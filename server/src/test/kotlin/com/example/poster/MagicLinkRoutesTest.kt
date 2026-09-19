package com.example.poster

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
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Passwordless sign-in: the emailed link, once, for anybody with the inbox. */
class MagicLinkRoutesTest {
    private class RecordedMail : com.example.poster.mail.Mailer {
        val sent = mutableListOf<Triple<String, String, String>>()
        override suspend fun send(to: String, subject: String, body: String): Boolean { sent += Triple(to, subject, body); return true }
        fun linkFor(email: String, index: Int = 0): String? =
            sent.filter { it.first == email }.getOrNull(index)?.third?.let { Regex("token=([A-Za-z0-9_-]+)").find(it)?.groupValues?.get(1) }
    }

    @Test
    fun anExistingAccountGetsALinkThatSignsInOnce_andConfirmsTheAddress() = withServer { mail ->
        val member = register("member@example.com")
        assertNull(me(member).verifiedAt)

        assertEquals(HttpStatusCode.NoContent, request("member@example.com"))
        val body = mail.sent.last().third
        assertTrue("https://poster.example.com/magic?token=" in body, "no magic link in:\n$body")
        val token = mail.linkFor("member@example.com", index = 1) ?: mail.linkFor("member@example.com")!!

        val response = client.post("/auth/magic") { contentType(ContentType.Application.Json); setBody("""{"token":"$token"}""") }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val session = Json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals(member.user.guid, session.user.guid)
        assertNotNull(me(session).verifiedAt, "following the link did not confirm the address")

        val again = client.post("/auth/magic") { contentType(ContentType.Application.Json); setBody("""{"token":"$token"}""") }
        assertEquals(HttpStatusCode.BadRequest, again.status, "the link worked twice")
    }

    @Test
    fun anUnknownAddressGetsNothing_andTheAnswerLooksTheSame() = withServer { mail ->
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
    fun aBadAddressOrAnInventedTokenSaysNothingUseful() = withServer { mail ->
        assertEquals(HttpStatusCode.NoContent, request("not an address"))
        assertTrue(mail.sent.isEmpty())
        val response = client.post("/auth/magic") { contentType(ContentType.Application.Json); setBody("""{"token":"made-up"}""") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun theLandingPageHandsTheTokenToTheAppWithoutSpendingIt() = withServer { mail ->
        register("member@example.com")
        request("member@example.com")
        val token = mail.linkFor("member@example.com", index = 1)!!
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

    private fun withServer(block: suspend ApplicationTestBuilder.(RecordedMail) -> Unit) {
        val root = Files.createTempDirectory("poster-magic-test")
        val previous = mapOf("poster.database" to System.getProperty("poster.database"), "io.ktor.development" to System.getProperty("io.ktor.development"))
        System.setProperty("poster.database", root.resolve("test.db").toString())
        System.setProperty("io.ktor.development", "true")
        val mail = RecordedMail()
        try {
            testApplication {
                application { module(mail) }
                block(mail)
            }
        } finally {
            previous.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            root.toFile().deleteRecursively()
        }
    }
}
