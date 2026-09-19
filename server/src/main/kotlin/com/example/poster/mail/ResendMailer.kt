package com.example.poster.mail

import com.example.poster.config.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resend, over its HTTP API rather than SMTP.
 *
 * SMTP would mean either a mail server on the box or the app speaking it
 * directly, and many cloud hosts block outbound port 25. An HTTPS call
 * needs none of that: no daemon to run, nothing to patch, and no port that a
 * hosting provider has opinions about.
 *
 * The API key is read from the environment and never logged. A failure is
 * reported and swallowed — see [Mailer] for why the caller must not fall over.
 */
class ResendMailer(
    private val apiKey: String,
    private val from: String,
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
    private val log: (String) -> Unit = ::println,
) : Mailer {

    @Serializable
    private data class Message(
        val from: String,
        val to: List<String>,
        val subject: String,
        val text: String,
    )

    override suspend fun send(to: String, subject: String, body: String): Boolean = try {
        val response = client.post("https://api.resend.com/emails") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(Message(from = from, to = listOf(to), subject = subject, text = body))
        }
        if (!response.status.isSuccess()) {
            // The body says why — an unverified domain, a malformed address —
            // and none of it is a secret. The key is in a header, not here.
            log("mail: Resend refused a message (${response.status}): ${response.bodyAsText()}")
        }
        response.status.isSuccess()
    } catch (cause: Exception) {
        log("mail: could not reach Resend (${cause::class.simpleName}: ${cause.message})")
        false
    }

    companion object {
        /**
         * Configured, or not sending. A missing key is normal — development, a
         * test, a deployment that has not been given one — and produces a
         * mailer that prints rather than a server that will not start.
         */
        fun fromEnvironment(
            apiKey: String? = System.getenv("POSTER_RESEND_API_KEY"),
            from: String? = System.getenv("POSTER_MAIL_FROM"),
            log: (String) -> Unit = ::println,
        ): Mailer = if (apiKey.isNullOrBlank()) {
            log("mail: no POSTER_RESEND_API_KEY, so mail is printed rather than sent")
            LoggingMailer(log)
        } else {
            ResendMailer(apiKey = apiKey, from = from?.takeIf { it.isNotBlank() } ?: DEFAULT_FROM, log = log)
        }

        val DEFAULT_FROM = "${AppInfo.NAME} <no-reply@${AppInfo.WEB_ORIGIN.substringAfter("://")}>"
    }
}
