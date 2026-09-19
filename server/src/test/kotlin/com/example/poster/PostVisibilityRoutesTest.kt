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
import com.example.poster.model.Group
import com.example.poster.model.GroupLocalRepository
import com.example.poster.model.UserGroupLocalRepository
import com.example.poster.model.Post
import com.example.poster.model.RegisterRequest
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
 * The feed decides who reads whose post, so the rule is pinned here rather
 * than left to the screens that display it.
 */
class PostVisibilityRoutesTest {
    @Test
    fun feedShowsOnlyWhatTheViewerIsAllowedToSee() {
        val databasePath = Files.createTempDirectory("poster-visibility").resolve("test.db")
        val oldDevelopment = System.getProperty("io.ktor.development")
        val oldDatabase = System.getProperty("poster.database")
        System.setProperty("io.ktor.development", "true")
        System.setProperty("poster.database", databasePath.toString())

        try {
            testApplication {
                application { module() }

                val author = register("Author", "author@example.com")
                val member = register("Member", "member@example.com")
                val outsider = register("Outsider", "outsider@example.com")

                // Seeded directly: creating a group is the admin panel's,
                // and the endpoint that used to let any account do it is gone.
                val group = Group(id = "home-group", name = "Home", inviteCode = "HOME")
                GroupLocalRepository().addOrUpdateGroup(group)
                // An invite each: they are good once, so two people joining
                // takes two of them.
                val memberships = UserGroupLocalRepository()
                for ((index, joiner) in listOf(author, member).withIndex()) {
                    val code = "INVITE$index"
                    memberships.createInvite(group.id, "seed", code, "2026-08-28T10:00:00Z")
                    assertEquals(
                        HttpStatusCode.NoContent,
                        client.post("/groups/join") {
                            bearerAuth(joiner.tokens.accessToken)
                            contentType(ContentType.Application.Json)
                            setBody(Json.encodeToString(mapOf("inviteCode" to code)))
                        }.status,
                    )
                }

                post(author, "open", "public", null)
                post(author, "ours", "group", group.id)
                post(author, "mine", "private", null)

                assertEquals(setOf("open", "ours", "mine"), titles(author))
                assertEquals(setOf("open", "ours"), titles(member))
                assertEquals(setOf("open"), titles(outsider))

                // Knowing the id must not be a way around the feed.
                val ids = feed(author).associate { it.title to it.guid }
                assertEquals(
                    HttpStatusCode.NotFound,
                    client.get("/posts/byId/${ids.getValue("mine")}") {
                        bearerAuth(outsider.tokens.accessToken)
                    }.status,
                )
                assertEquals(
                    HttpStatusCode.NotFound,
                    client.get("/posts/byId/${ids.getValue("ours")}") {
                        bearerAuth(outsider.tokens.accessToken)
                    }.status,
                )
                assertEquals(
                    HttpStatusCode.OK,
                    client.get("/posts/byId/${ids.getValue("open")}") {
                        bearerAuth(outsider.tokens.accessToken)
                    }.status,
                )
                assertTrue(client.get("/posts").status == HttpStatusCode.Unauthorized)
            }
        } finally {
            if (oldDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", oldDevelopment)
            if (oldDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", oldDatabase)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.post(
        author: AuthResponse,
        title: String,
        visibility: String,
        group: String?,
    ) {
        val post = Post(
            guid = title,
            title = title,
            message = "message",
            author = author.user.guid,
            group = group,
            date = Clock.System.now().toLocalDateTime(TimeZone.UTC),
            visibility = visibility,
        )
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/posts") {
                bearerAuth(author.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(post))
            }.status,
        )
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.feed(
        viewer: AuthResponse,
    ): List<Post> {
        val response = client.get("/posts") { bearerAuth(viewer.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.titles(
        viewer: AuthResponse,
    ): Set<String> = feed(viewer).map { it.title }.toSet()

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.register(
        name: String,
        email: String,
    ): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest(name, "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        // Posting needs a confirmed address; this suite is not about that.
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }
}
