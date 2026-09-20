package com.example.poster

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.domain.validation.PostRules
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A post is one paragraph.
 *
 * The form counts to the same number, but a form is not a boundary: anything
 * can post here, and one person's essay in a feed everybody reads is the thing
 * being prevented.
 */
class PostLengthTest {

    @Test
    fun aMessageAtTheLimitIsAccepted() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")

        assertEquals(
            HttpStatusCode.NoContent,
            post(author, "p-1", "a".repeat(PostRules.MESSAGE_LIMIT)),
        )
    }

    @Test
    fun oneCharacterPastItIsRefused() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")

        val response = post(author, "p-1", "a".repeat(PostRules.MESSAGE_LIMIT + 1))

        assertEquals(HttpStatusCode.BadRequest, response)
    }

    @Test
    fun aTitleTooLongIsRefusedToo() = withServer {
        val author = register("author@example.com")
        confirmAddress("author@example.com")

        assertEquals(
            HttpStatusCode.BadRequest,
            post(author, "p-1", "words", title = "t".repeat(PostRules.TITLE_LIMIT + 1)),
        )
    }

    private suspend fun ApplicationTestBuilder.post(
        user: AuthResponse,
        guid: String,
        message: String,
        title: String = "A post",
    ) = client.post("/posts") {
        bearerAuth(user.tokens.accessToken)
        contentType(ContentType.Application.Json)
        setBody(
            """{"guid":"$guid","title":"$title","message":"$message","author":"${user.user.guid}",""" +
                """"group":null,"likes":0,"date":"2026-08-27T10:00","visibility":"public",""" +
                """"tags":[],"language":"en"}""",
        )
    }.status

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

}
