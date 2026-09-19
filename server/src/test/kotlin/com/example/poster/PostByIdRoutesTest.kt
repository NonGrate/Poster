package com.example.poster

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import com.example.poster.model.AuthResponse
import com.example.poster.model.Post
import com.example.poster.model.RegisterRequest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One post, by id.
 *
 * It used to be answered by searching the feed, which was every post the
 * viewer could see. Once the feed became a page that search became a search of
 * the newest hundred, and a post older than that read as not existing — to a
 * link, to the Liked list, to anything holding an id. Visibility still has to
 * be obeyed, so the tests below check both halves: old posts are found, and
 * posts that are not yours to see are still not found.
 */
class PostByIdRoutesTest {

    @Test
    fun aPostOlderThanAPageIsStillFoundById() = withServer {
        val author = register("author@example.com")
        // Comfortably past the default page, so the oldest is nowhere near it.
        repeat(120) { post(author, "Post $it", minute = it) }

        val response = byId(author, "Post-0")

        assertEquals(HttpStatusCode.OK, response.first, "the oldest post was not found by id")
        assertEquals("Post 0", response.second?.title)
    }

    @Test
    fun aPostInsideThePageIsStillFoundById() = withServer {
        val author = register("author@example.com")
        repeat(120) { post(author, "Post $it", minute = it) }

        assertEquals(HttpStatusCode.OK, byId(author, "Post-119").first)
    }

    /** Not visible reads as not found: whether it exists is itself private. */
    @Test
    fun somebodyElsesPrivatePostIsNotFound() = withServer {
        val author = register("author@example.com")
        val stranger = register("stranger@example.com")
        post(author, "Private one", minute = 0, visibility = "private")

        assertEquals(HttpStatusCode.NotFound, byId(stranger, "Private-one").first)
    }

    /** And the author still sees their own, however old and however private. */
    @Test
    fun yourOwnPrivatePostIsFoundHoweverOld() = withServer {
        val author = register("author@example.com")
        post(author, "Private one", minute = 0, visibility = "private")
        repeat(120) { post(author, "Post $it", minute = it + 1) }

        assertEquals(HttpStatusCode.OK, byId(author, "Private-one").first)
    }

    @Test
    fun anIdNobodyWroteIsNotFound() = withServer {
        val author = register("author@example.com")
        post(author, "Post 0", minute = 0)

        assertEquals(HttpStatusCode.NotFound, byId(author, "no-such-post").first)
    }

    /**
     * Your own posts are not a page. The feed is, and a post of yours that
     * falls outside it is not gone — My Posts must still show it.
     */
    @Test
    fun everyPostYouWroteComesBackHoweverMany() = withServer {
        val author = register("author@example.com")
        val written = 130
        repeat(written) { post(author, "Post $it", minute = it) }

        val mine = mine(author)

        assertEquals(written, mine.size, "my posts came back as a page")
        assertEquals("Post 129", mine.first().title, "newest first")
    }

    @Test
    fun myPostsAreOnlyMine() = withServer {
        val author = register("author@example.com")
        val stranger = register("stranger@example.com")
        post(author, "Mine", minute = 0)
        post(stranger, "Theirs", minute = 1)

        assertEquals(listOf("Mine"), mine(author).map { it.title })
    }

    /** Including the ones only you can see. */
    @Test
    fun myPostsIncludeMyPrivateOnes() = withServer {
        val author = register("author@example.com")
        post(author, "Private one", minute = 0, visibility = "private")

        assertEquals(listOf("Private one"), mine(author).map { it.title })
    }

    @Test
    fun myPostsNeedsSigningIn() = withServer {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/posts/mine").status)
    }

    private suspend fun ApplicationTestBuilder.mine(viewer: AuthResponse): List<Post> {
        val response = client.get("/posts/mine") { bearerAuth(viewer.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.byId(
        viewer: AuthResponse,
        guid: String,
    ): Pair<HttpStatusCode, Post?> {
        val response = client.get("/posts/byId/$guid") { bearerAuth(viewer.tokens.accessToken) }
        val body = if (response.status == HttpStatusCode.OK) {
            Json.decodeFromString<Post>(response.bodyAsText())
        } else {
            null
        }
        return response.status to body
    }

    private suspend fun ApplicationTestBuilder.post(
        author: AuthResponse,
        title: String,
        minute: Int,
        visibility: String = "public",
    ) {
        val post = Post(
            guid = title.replace(' ', '-'),
            title = title,
            message = "words",
            author = author.user.guid,
            group = null,
            date = LocalDateTime(2026, 1, 1, minute / 60, minute % 60),
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

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Author", "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-byid").resolve("test.db")
        val previousDatabase = System.getProperty("poster.database")
        val previousDevelopment = System.getProperty("io.ktor.development")
        System.setProperty("poster.database", databasePath.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module() }
                block()
            }
        } finally {
            if (previousDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previousDatabase)
            if (previousDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", previousDevelopment)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }
}
