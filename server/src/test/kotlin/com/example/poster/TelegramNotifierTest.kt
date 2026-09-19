package com.example.poster

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import com.example.poster.notify.LoggingNotifier
import com.example.poster.notify.TelegramNotifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class TelegramNotifierTest {

    @Test
    fun disabledWithoutSecrets() = runBlocking {
        val notifier = TelegramNotifier.fromEnvironment(botToken = null, chatId = "1") {}
        assertTrue(notifier is LoggingNotifier, "no token means it must not try to send")
        notifier.notify("anything")  // must not throw
    }

    @Test
    fun postsToTheBotWithChatAndText() = runBlocking {
        var seen: HttpRequestData? = null
        val engine = MockEngine { request ->
            seen = request
            respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val notifier = TelegramNotifier(
            botToken = "123:ABC",
            chatId = "42",
            client = HttpClient(engine) { install(ContentNegotiation) { json(Json) } },
        )
        notifier.notify("hello")

        val request = requireNotNull(seen)
        assertContains(request.url.toString(), "/bot123:ABC/sendMessage")
        val body = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertContains(body, "\"chat_id\":\"42\"")
        assertContains(body, "hello")
    }

    @Test
    fun swallowsDeliveryFailure() = runBlocking {
        val engine = MockEngine { respond("nope", HttpStatusCode.Unauthorized) }
        val log = mutableListOf<String>()
        val notifier = TelegramNotifier(
            botToken = "123:ABC",
            chatId = "42",
            client = HttpClient(engine) { install(ContentNegotiation) { json(Json) } },
            log = { log.add(it) },
        )
        notifier.notify("hello")  // a refusal must not throw
        assertTrue(log.any { it.contains("refused") }, "a failure should be logged, not raised")
    }
}
