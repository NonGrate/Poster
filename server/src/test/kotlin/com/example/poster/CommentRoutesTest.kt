package com.example.poster

import com.example.poster.config.Features
import com.example.poster.domain.validation.CommentRules
import com.example.poster.model.AuthResponse
import com.example.poster.model.Comment
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `/posts/{id}/comments`: who may read, write and remove. */
class CommentRoutesTest {
    @Test
    fun aCommentIsStoredAndComesBackOldestFirst_andThePostCountsIt() = withServer {
        if (!Features.COMMENTS) return@withServer
        val author = confirmed("author@example.com")
        val reader = confirmed("reader@example.com")
        postPost(author, "p1", "public")
        assertEquals(HttpStatusCode.OK, comment(reader, "p1", "First").status)
        assertEquals(HttpStatusCode.OK, comment(author, "p1", "Second").status)

        val thread = list(reader, "p1")
        assertEquals(listOf("First", "Second"), thread.map { it.text })
        val feed = Json.parseToJsonElement(client.get("/posts") { bearerAuth(reader.tokens.accessToken) }.bodyAsText()).jsonArray
        assertEquals(2, feed.first().jsonObject["comments"]!!.jsonPrimitive.content.toInt(), "the post did not carry its comment count")
    }

    @Test
    fun blankAndOverlongCommentsAreRefused() = withServer {
        if (!Features.COMMENTS) return@withServer
        val author = confirmed("author@example.com")
        postPost(author, "p1", "public")
        assertEquals(HttpStatusCode.BadRequest, comment(author, "p1", "   ").status)
        assertEquals(HttpStatusCode.BadRequest, comment(author, "p1", "x".repeat(CommentRules.TEXT_LIMIT + 1)).status)
        assertTrue(list(author, "p1").isEmpty())
    }

    @Test
    fun aPrivatePostsThreadIsNotThereForAnybodyElse() = withServer {
        if (!Features.COMMENTS) return@withServer
        val author = confirmed("author@example.com")
        val stranger = confirmed("stranger@example.com")
        postPost(author, "secret", "private")
        comment(author, "secret", "Note to self")
        assertEquals(HttpStatusCode.NotFound, client.get("/posts/secret/comments") { bearerAuth(stranger.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.NotFound, comment(stranger, "secret", "Hi").status)
        assertEquals(1, list(author, "secret").size)
    }

    @Test
    fun theCommentsAuthorAndThePostsAuthorMayRemoveIt_nobodyElse() = withServer {
        if (!Features.COMMENTS) return@withServer
        val author = confirmed("author@example.com")
        val commenter = confirmed("commenter@example.com")
        val stranger = confirmed("stranger@example.com")
        postPost(author, "p1", "public")
        val mine = Json.decodeFromString<Comment>(comment(commenter, "p1", "Mine").bodyAsText())
        val theirs = Json.decodeFromString<Comment>(comment(commenter, "p1", "Theirs").bodyAsText())

        assertEquals(HttpStatusCode.Forbidden, remove(stranger, "p1", mine.guid).status)
        assertEquals(HttpStatusCode.NoContent, remove(commenter, "p1", mine.guid).status, "the author could not remove their own comment")
        assertEquals(HttpStatusCode.NoContent, remove(author, "p1", theirs.guid).status, "the post's author could not remove a comment on it")
        assertTrue(list(author, "p1").isEmpty())
    }

    @Test
    fun deletingThePostTakesTheThreadWithIt() = withServer {
        if (!Features.COMMENTS) return@withServer
        val author = confirmed("author@example.com")
        postPost(author, "p1", "public")
        comment(author, "p1", "Gone with the post")
        client.delete("/posts/p1") { bearerAuth(author.tokens.accessToken) }
        assertEquals(HttpStatusCode.NotFound, client.get("/posts/p1/comments") { bearerAuth(author.tokens.accessToken) }.status)
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.comment(user: AuthResponse, post: String, text: String): HttpResponse =
        client.post("/posts/$post/comments") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}""")
        }

    private suspend fun ApplicationTestBuilder.list(user: AuthResponse, post: String): List<Comment> =
        Json.decodeFromString(client.get("/posts/$post/comments") { bearerAuth(user.tokens.accessToken) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.remove(user: AuthResponse, post: String, comment: String): HttpResponse =
        client.delete("/posts/$post/comments/$comment") { bearerAuth(user.tokens.accessToken) }

}
