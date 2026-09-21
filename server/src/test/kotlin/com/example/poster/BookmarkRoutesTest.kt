package com.example.poster

import com.example.poster.config.Features
import com.example.poster.model.AuthResponse
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** feature.bookmarks: `/bookmarks` and `GET /posts?saved=true`. */
class BookmarkRoutesTest {
    @Test
    fun savedPostsComeBackUnderTheSavedFilter_andOnlyVisibleOnesCanBeSaved() = withServer {
        if (!Features.BOOKMARKS) return@withServer
        val reader = confirmed("reader@example.com")
        val writer = confirmed("writer@example.com")
        postPost(writer, "w-public", "public")
        postPost(writer, "w-private", "private")
        postPost(writer, "w-other", "public")

        assertEquals(HttpStatusCode.NoContent, client.post("/bookmarks/w-public") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/bookmarks/w-private") { bearerAuth(reader.tokens.accessToken) }.status, "a private post is not-found to a stranger")
        assertEquals(HttpStatusCode.NotFound, client.post("/bookmarks/no-such-post") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(listOf("w-public"), Json.decodeFromString<List<String>>(client.get("/bookmarks") { bearerAuth(reader.tokens.accessToken) }.bodyAsText()))
        assertEquals(listOf("w-public"), feed(reader, saved = true))
        assertEquals(listOf("w-other", "w-public"), feed(reader, saved = false).sorted(), "the plain feed is unchanged")

        assertEquals(HttpStatusCode.NoContent, client.delete("/bookmarks/w-public") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(emptyList(), feed(reader, saved = true))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/bookmarks").status)
    }

    /** The feed as [user] sees it, optionally through the saved filter. */
    private suspend fun ApplicationTestBuilder.feed(user: AuthResponse, saved: Boolean): List<String> {
        val response = client.get("/posts") { bearerAuth(user.tokens.accessToken); if (saved) parameter("saved", "true") }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.guids()
    }
}
