package com.example.poster

import com.example.poster.config.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import com.example.poster.mail.LoggingMailer
import com.example.poster.mail.ResendMailer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sending, and — more importantly — failing to send.
 *
 * Every caller is in the middle of something that matters more than the email:
 * registering somebody, or answering a reset request. None of them may fall over
 * because a third party is having a bad afternoon.
 */
class MailerTest {

    @Test
    fun aMessageIsPostedToResendWithTheKeyInTheHeader() {
        var authorization: String? = null
        var body: String? = null
        val mailer = mailerRespondingWith(HttpStatusCode.OK, """{"id":"abc"}""") { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            body = (request.body as io.ktor.http.content.TextContent).text
        }

        val sent = runBlocking { mailer.send("them@example.com", "Subject", "Body") }

        assertTrue(sent)
        assertEquals("Bearer test-key", authorization)
        assertTrue(body!!.contains("them@example.com"), "the recipient did not reach Resend")
        assertTrue(body!!.contains("Subject"))
    }

    /** A refusal is reported and swallowed: registration must still succeed. */
    @Test
    fun aRefusalIsNotAnException() {
        val complaints = mutableListOf<String>()
        val mailer = mailerRespondingWith(
            HttpStatusCode.UnprocessableEntity,
            """{"message":"domain not verified"}""",
            log = complaints,
        )

        val sent = runBlocking { mailer.send("them@example.com", "Subject", "Body") }

        assertFalse(sent, "a refused message was reported as sent")
        assertTrue(
            complaints.any { it.contains("domain not verified") },
            "the reason was not written down: $complaints",
        )
    }

    @Test
    fun aProviderThatCannotBeReachedIsNotAnException() {
        val complaints = mutableListOf<String>()
        val engine = MockEngine { throw java.io.IOException("no route to host") }
        val mailer = ResendMailer(
            apiKey = "test-key",
            from = "${AppInfo.NAME} <no-reply@poster.example.com>",
            client = HttpClient(engine) { install(ContentNegotiation) { json(Json) } },
            log = { complaints += it },
        )

        val sent = runBlocking { mailer.send("them@example.com", "Subject", "Body") }

        assertFalse(sent)
        assertTrue(complaints.any { it.contains("could not reach") }, "silence about an unreachable provider")
    }

    /** The key is a secret; the things around it are not. */
    @Test
    fun theApiKeyIsNeverWrittenToTheLog() {
        val complaints = mutableListOf<String>()
        val mailer = mailerRespondingWith(
            HttpStatusCode.Unauthorized,
            """{"message":"invalid key"}""",
            log = complaints,
        )

        runBlocking { mailer.send("them@example.com", "Subject", "Body") }

        assertTrue(
            complaints.none { it.contains("test-key") },
            "the API key was written to the log: $complaints",
        )
    }

    /**
     * No key is the normal state in development and in every test. It must
     * produce a mailer that prints, not a server that will not start.
     */
    @Test
    fun withoutAKeyMailIsPrintedRatherThanSent() {
        val notes = mutableListOf<String>()
        val mailer = ResendMailer.fromEnvironment(apiKey = null, from = null, log = { notes += it })

        assertTrue(mailer is LoggingMailer)
        assertTrue(runBlocking { mailer.send("them@example.com", "Subject", "Body") })
        assertTrue(notes.any { it.contains("printed rather than sent") })
    }

    @Test
    fun aBlankKeyCountsAsNoKey() {
        assertTrue(ResendMailer.fromEnvironment(apiKey = "   ", from = null, log = {}) is LoggingMailer)
    }

    @Test
    fun theSenderFallsBackToTheAppsOwnAddress() {
        val mailer = ResendMailer.fromEnvironment(apiKey = "key", from = null, log = {})

        assertFalse(mailer is LoggingMailer, "a key was given, so it should send")
        assertTrue(ResendMailer.DEFAULT_FROM.contains("poster.example.com"))
    }

    private fun mailerRespondingWith(
        status: HttpStatusCode,
        body: String,
        log: MutableList<String>? = null,
        onRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ): ResendMailer {
        val engine = MockEngine { request ->
            onRequest(request)
            if (status.isSuccess()) {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respondError(status, body)
            }
        }
        return ResendMailer(
            apiKey = "test-key",
            from = "${AppInfo.NAME} <no-reply@poster.example.com>",
            client = HttpClient(engine) { install(ContentNegotiation) { json(Json) } },
            log = { line -> log?.add(line) },
        )
    }
}
