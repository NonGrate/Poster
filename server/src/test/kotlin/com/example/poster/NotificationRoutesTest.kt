package com.example.poster

import com.example.poster.model.AppNotification
import com.example.poster.model.AuthResponse
import com.example.poster.model.NotificationType
import com.example.poster.model.RegisterRequest
import com.example.poster.push.PushMessage
import com.example.poster.push.PushSender
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Likes and comments become activity rows and pushes; devices come and go. */
class NotificationRoutesTest {
    /** Records what would have been pushed; a token containing "dead" is reported gone. */
    private class RecordingSender : PushSender {
        val sent = mutableListOf<Pair<String, PushMessage>>()
        override suspend fun send(token: String, message: PushMessage): Boolean {
            sent += token to message
            return "dead" !in token
        }
    }

    private val android = RecordingSender()
    private val ios = RecordingSender()

    @Test
    fun aLikeTellsTheAuthorOnEveryDevice_notTheLiker() = withServer {
        val author = confirmed("author@example.com")
        val fan = confirmed("fan@example.com")
        postPost(author, "p1")
        registerDevice(author, "author-android-token", "android")
        registerDevice(author, "author-ios-token", "ios")
        registerDevice(fan, "fan-token", "android")

        like(fan, "p1")
        awaitPushes(2)

        assertEquals(listOf("author-android-token"), android.sent.map { it.first })
        assertEquals(listOf("author-ios-token"), ios.sent.map { it.first })
        assertTrue(android.sent.first().second.body.contains("liked your post"), android.sent.first().second.body)
        assertEquals("p1", android.sent.first().second.data["postGuid"])

        val mine = list(author)
        assertEquals(1, mine.size)
        assertEquals(NotificationType.LIKE, mine.first().type)
        assertEquals("Title", mine.first().postTitle)
        assertTrue(list(fan).isEmpty(), "the liker was told about their own like")
        assertEquals(1, unread(author))
    }

    @Test
    fun likingYourOwnPostIsNotNews() = withServer {
        val author = confirmed("author@example.com")
        postPost(author, "p1")
        registerDevice(author, "t", "android")
        like(author, "p1")
        delay(200)
        assertTrue(android.sent.isEmpty())
        assertTrue(list(author).isEmpty())
    }

    @Test
    fun aCommentTellsTheAuthorInTheirLanguage() = withServer {
        val author = confirmed("author@example.com")
        val other = confirmed("other@example.com")
        client.post("/accounts") {
            bearerAuth(author.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(com.example.poster.model.User.serializer(), author.user.copy(languages = listOf("en", "ru"), defaultLanguage = "ru")))
        }
        postPost(author, "p1")
        registerDevice(author, "t", "ios")
        client.post("/posts/p1/comments") {
            bearerAuth(other.tokens.accessToken); contentType(ContentType.Application.Json); setBody("""{"text":"Nice"}""")
        }
        awaitPushes(1)
        val body = ios.sent.single().second.body
        assertTrue("комментарий" in body || "comment" in body, body)
        assertEquals(NotificationType.COMMENT, list(author).single().type)
    }

    @Test
    fun readingClearsTheCount() = withServer {
        val author = confirmed("author@example.com")
        val fan = confirmed("fan@example.com")
        postPost(author, "p1")
        like(fan, "p1")
        assertEquals(1, unread(author))
        assertEquals(HttpStatusCode.NoContent, client.post("/notifications/read") { bearerAuth(author.tokens.accessToken) }.status)
        assertEquals(0, unread(author))
        assertTrue(list(author).single().readAt != null)
    }

    @Test
    fun aDeadTokenIsForgotten_andAWithdrawnOneIsNotPushedTo() = withServer {
        val author = confirmed("author@example.com")
        val fan = confirmed("fan@example.com")
        postPost(author, "p1")
        registerDevice(author, "dead-token", "android")
        registerDevice(author, "live-token", "android")
        like(fan, "p1")
        awaitPushes(2)
        android.sent.clear()

        // Withdrawal needs no session: it is what the app does after signing out.
        assertEquals(HttpStatusCode.NoContent, client.delete("/devices/live-token").status)
        client.delete("/favorites/${fan.user.guid}/p1") { bearerAuth(fan.tokens.accessToken) }
        like(fan, "p1")
        delay(300)
        assertTrue(android.sent.isEmpty(), "pushed to ${android.sent.map { it.first }}")
    }

    @Test
    fun aDeviceNeedsATokenAndAKnownPlatform() = withServer {
        val user = confirmed("someone@example.com")
        val response = client.post("/devices") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json); setBody("""{"token":"abc","platform":"web"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- helpers -----------------------------------------------------------

    private suspend fun ApplicationTestBuilder.awaitPushes(count: Int) {
        repeat(50) { if (android.sent.size + ios.sent.size >= count) return; delay(50) }
    }

    private suspend fun ApplicationTestBuilder.like(user: AuthResponse, post: String) {
        val response = client.post("/favorites/${user.user.guid}/$post") { bearerAuth(user.tokens.accessToken) }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private suspend fun ApplicationTestBuilder.registerDevice(user: AuthResponse, token: String, platform: String) {
        val response = client.post("/devices") {
            bearerAuth(user.tokens.accessToken); contentType(ContentType.Application.Json)
            setBody("""{"token":"$token","platform":"$platform"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.list(user: AuthResponse): List<AppNotification> =
        Json.decodeFromString(client.get("/notifications") { bearerAuth(user.tokens.accessToken) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.unread(user: AuthResponse): Int =
        Json.parseToJsonElement(client.get("/notifications/unread") { bearerAuth(user.tokens.accessToken) }.bodyAsText())
            .jsonObject["unread"]!!.jsonPrimitive.content.toInt()

    private suspend fun ApplicationTestBuilder.confirmed(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.postPost(user: AuthResponse, guid: String) {
        val response = client.post("/posts") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"guid":"$guid","title":"Title","message":"words","author":"${user.user.guid}",""" +
                    """"group":null,"likes":0,"date":"2026-08-23T10:00","visibility":"public","tags":[],"language":"en"}""",
            )
        }
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val root = Files.createTempDirectory("poster-notifications-test")
        val previous = mapOf("poster.database" to System.getProperty("poster.database"), "io.ktor.development" to System.getProperty("io.ktor.development"))
        System.setProperty("poster.database", root.resolve("test.db").toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module(pushSenders = mapOf("android" to android, "ios" to ios)) }
                block()
            }
        } finally {
            previous.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            root.toFile().deleteRecursively()
        }
    }
}
