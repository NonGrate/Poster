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

/** feature.follows: `/follows` and `GET /posts?following=true`. */
class FollowRoutesTest {
    @Test
    fun theFollowingFeedHoldsOnlyFollowedAuthors_andNeverWidensVisibility() = withServer {
        if (!Features.FOLLOWS) return@withServer
        val reader = confirmed("reader@example.com")
        val followed = confirmed("followed@example.com")
        val stranger = confirmed("stranger@example.com")
        postPost(followed, "f-public", "public")
        postPost(followed, "f-private", "private")
        postPost(stranger, "s-public", "public")

        assertEquals(HttpStatusCode.NoContent, client.post("/follows/${followed.user.guid}") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(listOf(followed.user.guid), Json.decodeFromString<List<String>>(client.get("/follows") { bearerAuth(reader.tokens.accessToken) }.bodyAsText()))
        assertEquals(listOf("f-public"), feed(reader, following = true), "only the followed author's visible posts")
        assertEquals(listOf("f-public", "s-public"), feed(reader, following = false).sorted(), "without the filter the feed is unchanged")

        assertEquals(HttpStatusCode.NoContent, client.delete("/follows/${followed.user.guid}") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(emptyList(), feed(reader, following = true))
    }

    @Test
    fun youCannotFollowYourselfOrNobody() = withServer {
        if (!Features.FOLLOWS) return@withServer
        val reader = confirmed("reader@example.com")
        assertEquals(HttpStatusCode.BadRequest, client.post("/follows/${reader.user.guid}") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/follows/no-such-user") { bearerAuth(reader.tokens.accessToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/follows").status)
    }

    /** The feed as [user] sees it, optionally through the following filter. */
    private suspend fun ApplicationTestBuilder.feed(user: AuthResponse, following: Boolean): List<String> {
        val response = client.get("/posts") { bearerAuth(user.tokens.accessToken); if (following) parameter("following", "true") }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.guids()
    }
}
