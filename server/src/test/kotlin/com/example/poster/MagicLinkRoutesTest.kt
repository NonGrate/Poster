package com.example.poster

import com.example.poster.config.Features
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import com.example.poster.model.User
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Passwordless sign-in: the emailed link, once, for anybody with the inbox. */
class MagicLinkRoutesTest {
    @Test
    fun anExistingAccountGetsALinkThatSignsInOnce_andConfirmsTheAddress() = withServer { (mail) ->
        if (!Features.MAGIC_LINK) return@withServer
        val member = register("member@example.com")
        assertNull(me(member).verifiedAt)

        assertEquals(HttpStatusCode.NoContent, request("member@example.com"))
        val body = mail.sent.last().third
        assertTrue("https://poster.example.com/magic?token=" in body, "no magic link in:\n$body")
        // By subject: registering has already sent one, and "the second one"
        // is only right until something else sends mail.
        val token = mail.linkFor("member@example.com", subject = "Sign in")!!

        val response = client.post("/auth/magic") { contentType(ContentType.Application.Json); setBody("""{"token":"$token"}""") }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val session = Json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals(member.user.guid, session.user.guid)
        assertNotNull(me(session).verifiedAt, "following the link did not confirm the address")

        val again = client.post("/auth/magic") { contentType(ContentType.Application.Json); setBody("""{"token":"$token"}""") }
        assertEquals(HttpStatusCode.BadRequest, again.status, "the link worked twice")
    }

    @Test
    fun anUnknownAddressGetsNothing_andTheAnswerLooksTheSame() = withServer { (mail) ->
        if (!Features.MAGIC_LINK) return@withServer
        assertEquals(HttpStatusCode.NoContent, request("newcomer@example.com"))
        assertTrue(mail.sent.isEmpty(), "a sign-in link went to an address with no account")
        // And no account appeared behind the scenes.
        val register = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest("Some", "Body", "newcomer@example.com", "password123")))
        }
        assertEquals(HttpStatusCode.OK, register.status)
    }

    @Test
    fun aBadAddressOrAnInventedTokenSaysNothingUseful() = withServer { (mail) ->
        if (!Features.MAGIC_LINK) return@withServer
        assertEquals(HttpStatusCode.NoContent, request("not an address"))
        assertTrue(mail.sent.isEmpty())
        val response = client.post("/auth/magic") { contentType(ContentType.Application.Json); setBody("""{"token":"made-up"}""") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun theLandingPageHandsTheTokenToTheAppWithoutSpendingIt() = withServer { (mail) ->
        if (!Features.MAGIC_LINK) return@withServer
        register("member@example.com")
        request("member@example.com")
        val token = mail.linkFor("member@example.com", subject = "Sign in")!!
        val page = client.get("/magic?token=$token")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue("poster://magic?token=$token" in page.bodyAsText(), "the page did not offer the app link")
        val response = client.post("/auth/magic") { contentType(ContentType.Application.Json); setBody("""{"token":"$token"}""") }
        assertEquals(HttpStatusCode.OK, response.status, "loading the page spent the token")
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.request(email: String): HttpStatusCode =
        client.post("/auth/magic/request") { contentType(ContentType.Application.Json); setBody("""{"email":"$email"}""") }.status

    private suspend fun ApplicationTestBuilder.me(session: AuthResponse): User =
        Json.decodeFromString(client.get("/auth/me") { bearerAuth(session.tokens.accessToken) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

}
