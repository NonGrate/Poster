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
import com.example.poster.model.PostVisibility
import com.example.poster.model.RegisterRequest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sharing a post: an opaque public link, a read-only web page, and an in-app
 * open — for public posts only, and never leaking the id.
 */
class PostShareRoutesTest {

    @Test
    fun aPublicPostGetsAStableOpaqueLinkAndAPublicPage() = withServer {
        val author = register("Author", "author@example.com")
        createPost(author, "share-me", "That the house sells", visibility = PostVisibility.PUBLIC)

        val token = share(author, "share-me")
        assertNotNull(token, "a public post should get a token")
        // Opaque and unrelated to the guid — the whole point of not leaking the id.
        assertTrue("share-me" !in token, "the token must not contain the guid")

        // Sharing again reuses the one token rather than minting a second.
        assertEquals(token, share(author, "share-me"), "the token should be stable")

        // The public web page renders the post and offers the deep link.
        val page = client.get("/p/$token")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue("That the house sells" in html, "the page should show the title")
        assertTrue("poster://post/$token" in html, "the page should offer the deep link")

        // The in-app resolve returns the post.
        val resolved = client.get("/shared/$token")
        assertEquals(HttpStatusCode.OK, resolved.status)
        val post: Post = Json.decodeFromString(resolved.bodyAsText())
        assertEquals("share-me", post.guid)
    }

    @Test
    fun aPrivatePostCannotBeShared() = withServer {
        val author = register("Author", "author@example.com")
        createPost(author, "secret", "Only me", visibility = PostVisibility.PRIVATE)

        val response = client.post("/posts/secret/share") { bearerAuth(author.tokens.accessToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun aGroupPostCannotBeShared() = withServer {
        val author = register("Author", "author@example.com")
        createPost(author, "in-group", "For the group", visibility = PostVisibility.GROUP)

        val response = client.post("/posts/in-group/share") { bearerAuth(author.tokens.accessToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun sharingNeedsAnAccount() = withServer {
        val author = register("Author", "author@example.com")
        createPost(author, "share-me", "Public", visibility = PostVisibility.PUBLIC)

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/posts/share-me/share").status,
        )
    }

    @Test
    fun anUnknownTokenIsNotFound() = withServer {
        register("Author", "author@example.com")
        assertEquals(HttpStatusCode.NotFound, client.get("/p/nope123nope").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/shared/nope123nope").status)
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.share(who: AuthResponse, guid: String): String? {
        val response = client.post("/posts/$guid/share") { bearerAuth(who.tokens.accessToken) }
        if (response.status != HttpStatusCode.OK) return null
        return Json.decodeFromString<Map<String, String>>(response.bodyAsText())["token"]
    }

    private suspend fun ApplicationTestBuilder.createPost(
        author: AuthResponse,
        guid: String,
        title: String,
        visibility: String,
    ) {
        val post = Post(
            guid = guid,
            title = title,
            message = "message",
            author = author.user.guid,
            group = null,
            date = LocalDateTime(2026, 8, 31, 12, 0),
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

    private suspend fun ApplicationTestBuilder.register(name: String, email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest(name, "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-share").resolve("test.db")
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
