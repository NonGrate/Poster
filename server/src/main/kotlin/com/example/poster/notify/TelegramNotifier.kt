package com.example.poster.notify

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pushes short operator alerts — crashes, feedback, reports — somewhere a person
 * will actually see them.
 *
 * A failure is reported and swallowed: an alert that cannot be delivered must
 * never fail the request that triggered it. Callers fire it and forget it.
 */
interface AlertNotifier {
    suspend fun notify(text: String)
}

/**
 * Telegram, over its Bot API. The bot token is a secret and is read from the
 * environment, never logged; the chat id says who receives the message.
 */
class TelegramNotifier(
    private val botToken: String,
    private val chatId: String,
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
    private val log: (String) -> Unit = ::println,
) : AlertNotifier {

    @Serializable
    private data class Message(val chat_id: String, val text: String)

    override suspend fun notify(text: String) {
        try {
            // Telegram rejects a message over 4096 characters; a long stack trace
            // reaches that, so trim with room to spare.
            val body = if (text.length > 4000) text.take(4000) + "…" else text
            // Plain text on purpose: no parse_mode, so a crash message or a piece
            // of feedback with markup characters cannot break the send.
            val response = client.post("https://api.telegram.org/bot$botToken/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(Message(chat_id = chatId, text = body))
            }
            if (!response.status.isSuccess()) {
                // The body says why (a bad chat id, a bot the user never started);
                // the token is in the URL path, not here.
                log("telegram: sendMessage refused (${response.status}): ${response.bodyAsText()}")
            }
        } catch (cause: Exception) {
            log("telegram: could not reach Telegram (${cause::class.simpleName}: ${cause.message})")
        }
    }

    companion object {
        /**
         * Configured, or not sending. Missing secrets are normal — development, a
         * test, a deployment not given them — and produce a notifier that prints
         * rather than a server that will not start.
         */
        fun fromEnvironment(
            botToken: String? = System.getenv("POSTER_TELEGRAM_BOT_TOKEN"),
            chatId: String? = System.getenv("POSTER_TELEGRAM_CHAT_ID"),
            log: (String) -> Unit = ::println,
        ): AlertNotifier = if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
            log("telegram: no POSTER_TELEGRAM_* env, so alerts are printed rather than sent")
            LoggingNotifier(log)
        } else {
            TelegramNotifier(botToken = botToken, chatId = chatId, log = log)
        }
    }
}

/** The stand-in when Telegram is not configured: the alert goes to the log. */
class LoggingNotifier(private val log: (String) -> Unit = ::println) : AlertNotifier {
    override suspend fun notify(text: String) = log("alert (telegram disabled): $text")
}
