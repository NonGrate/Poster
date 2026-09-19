package com.example.poster

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import com.example.poster.model.AuthResponse
import com.example.poster.model.ModerationRepository
import com.example.poster.model.Post
import com.example.poster.model.RegisterRequest
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What moderation is *for*: the content actually goes away.
 *
 * The queries filter on `deleted_at` and on banned authors, and nothing checked
 * that a moderator's action reaches the feed a reader sees.
 */
class ModerationEffectRoutesTest {

    @Test
    fun aSoftDeletedPostDisappearsAndComesBackWhenRestored() = withServer { client, moderation ->
        val author = client.register("Author", "mod-author@example.com")
        val reader = client.register("Reader", "mod-reader@example.com")
        client.post(author, "visible-then-not")

        assertTrue(client.feed(reader).any { it.guid == "visible-then-not" })

        moderation.softDeletePost("admin", "visible-then-not", "not appropriate")
        assertTrue(
            client.feed(reader).none { it.guid == "visible-then-not" },
            "a deleted post was still in the feed",
        )
        assertTrue(
            client.feed(author).none { it.guid == "visible-then-not" },
            "a deleted post was still visible to its own author",
        )

        moderation.restorePost("admin", "visible-then-not")
        assertTrue(
            client.feed(reader).any { it.guid == "visible-then-not" },
            "restoring did not bring the post back",
        )
    }

    @Test
    fun aBannedAuthorsPostsLeaveTheFeed() = withServer { client, moderation ->
        val author = client.register("Author", "banned-author@example.com")
        val reader = client.register("Reader", "ban-reader@example.com")
        client.post(author, "by-someone-banned")

        assertTrue(client.feed(reader).any { it.guid == "by-someone-banned" })

        moderation.ban("admin", author.user.guid, "spam")

        assertTrue(
            client.feed(reader).none { it.guid == "by-someone-banned" },
            "a banned account's posts were still in the feed",
        )

        moderation.unban("admin", author.user.guid)
        assertTrue(
            client.feed(reader).any { it.guid == "by-someone-banned" },
            "unbanning did not bring the posts back",
        )
    }

    private suspend fun io.ktor.client.HttpClient.post(author: AuthResponse, guid: String) {
        val post = Post(
            guid = guid,
            title = guid,
            message = "message",
            author = author.user.guid,
            group = null,
            date = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        )
        assertEquals(
            HttpStatusCode.NoContent,
            post("/posts") {
                bearerAuth(author.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(post))
            }.status,
        )
    }

    private suspend fun io.ktor.client.HttpClient.feed(viewer: AuthResponse): List<Post> {
        val response = get("/posts") { bearerAuth(viewer.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun io.ktor.client.HttpClient.register(
        name: String,
        email: String,
    ): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest(name, "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        // Posting needs a confirmed address; this suite is not about that.
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun withServer(
        block: suspend (io.ktor.client.HttpClient, ModerationRepository) -> Unit,
    ) {
        val databasePath = Files.createTempDirectory("poster-moderation").resolve("test.db")
        val oldDevelopment = System.getProperty("io.ktor.development")
        val oldDatabase = System.getProperty("poster.database")
        System.setProperty("io.ktor.development", "true")
        System.setProperty("poster.database", databasePath.toString())
        try {
            testApplication {
                application { module() }
                // The same database the server is using, so a moderator action
                // here is the one the routes see.
                val moderation = ModerationRepository(
                    DatabaseManager(DatabaseDriverFactory()).getDatabase()
                )
                block(client, moderation)
            }
        } finally {
            if (oldDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", oldDevelopment)
            if (oldDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", oldDatabase)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }
}
