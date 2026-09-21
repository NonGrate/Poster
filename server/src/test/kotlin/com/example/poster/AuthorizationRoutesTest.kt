package com.example.poster

import com.example.poster.config.Features
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationRoutesTest {
    @Test
    fun authenticatedUserCannotActAsAnotherUser() = withServer {
        if (!Features.GROUPS || !Features.LIKES || !Features.TAGS) return@withServer

        val first = register("First", "first@example.com")
        val second = register("Second", "second@example.com")

        val forgedPost = """
            {
              "guid":"forged-post",
              "title":"Forged",
              "message":"Not allowed",
              "author":"${second.user.guid}",
              "group":null,
              "likes":0,
              "date":"${Clock.System.now().toLocalDateTime(TimeZone.UTC)}",
              "tags":[]
            }
        """.trimIndent()
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/posts") {
                bearerAuth(first.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(forgedPost)
            }.status,
        )
        // Asking for somebody's likes by id is not refused any more, it is
        // simply not a route: the only favourites anybody can name are their own.
        assertEquals(
            HttpStatusCode.NotFound,
            client.get("/favorites/user/${second.user.guid}") {
                bearerAuth(first.tokens.accessToken)
            }.status,
            "somebody else's likes were still addressable",
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/favorites/me") {
                bearerAuth(first.tokens.accessToken)
            }.status,
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/groups/user/${second.user.guid}") {
                bearerAuth(first.tokens.accessToken)
            }.status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/tags").status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.get("/tags") {
                bearerAuth(first.tokens.accessToken)
            }.status,
        )
    }

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
