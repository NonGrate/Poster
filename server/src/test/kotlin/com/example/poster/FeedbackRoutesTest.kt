package com.example.poster

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
import com.example.poster.domain.validation.FeedbackRules
import com.example.poster.model.AuthResponse
import com.example.poster.model.Feedback
import com.example.poster.model.FeedbackRepository
import com.example.poster.model.FeedbackStatus
import com.example.poster.model.RegisterRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sending feedback, reading it back, and the developer's reply.
 *
 * No confirmed address is required — feedback is not a post, and somebody who
 * cannot yet post one is exactly the person worth hearing from. What is checked:
 * a message is validated, feedback is private to its sender, and a reply left
 * from the admin side shows up on the sender's own list.
 */
class FeedbackRoutesTest {

    @Test
    fun sendingFeedbackStoresItAndItComesBack() = withServer {
        val user = register("someone@example.com")

        assertEquals(HttpStatusCode.NoContent, submit(user, "Please add a dark mode"))

        val mine = myFeedback(user)
        assertEquals(1, mine.size)
        assertEquals("Please add a dark mode", mine.first().message)
        assertEquals(FeedbackStatus.OPEN, mine.first().status)
    }

    @Test
    fun aBlankMessageIsRefused() = withServer {
        val user = register("someone@example.com")

        assertEquals(HttpStatusCode.BadRequest, submit(user, "   "))
        assertTrue(myFeedback(user).isEmpty())
    }

    @Test
    fun anOverlongMessageIsRefused() = withServer {
        val user = register("someone@example.com")

        val tooLong = "x".repeat(FeedbackRules.MESSAGE_LIMIT + 1)
        assertEquals(HttpStatusCode.BadRequest, submit(user, tooLong))
        assertTrue(myFeedback(user).isEmpty())
    }

    /** Feedback is one person's own: another account never sees it. */
    @Test
    fun feedbackIsPrivateToItsSender() = withServer {
        val one = register("one@example.com")
        val two = register("two@example.com")

        submit(one, "Only I should see this")

        assertEquals(1, myFeedback(one).size)
        assertTrue(myFeedback(two).isEmpty())
    }

    /** The developer's reply, left from the admin side, reaches the sender's list. */
    @Test
    fun aReplyShowsUpForTheSender() = withServer {
        val user = register("someone@example.com")
        submit(user, "A question")
        val id = myFeedback(user).first().id

        FeedbackRepository().respond(id, "Here is the answer")

        val answered = myFeedback(user).first()
        assertEquals(FeedbackStatus.ANSWERED, answered.status)
        assertEquals("Here is the answer", answered.response)
    }

    @Test
    fun sendingNeedsASession() = withServer {
        val response = client.post("/feedback") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"anonymous"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // — helpers —

    private suspend fun ApplicationTestBuilder.submit(user: AuthResponse, message: String) =
        client.post("/feedback") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(mapOf("message" to message)))
        }.status

    private suspend fun ApplicationTestBuilder.myFeedback(user: AuthResponse): List<Feedback> {
        val response = client.get("/feedback") { bearerAuth(user.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-feedback").resolve("test.db")
        val previousDatabase = System.getProperty("poster.database")
        val previousDevelopment = System.getProperty("io.ktor.development")
        System.setProperty("poster.database", databasePath.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module() }
                block()
            }
        } finally {
            if (previousDatabase == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previousDatabase)
            if (previousDevelopment == null) System.clearProperty("io.ktor.development")
            else System.setProperty("io.ktor.development", previousDevelopment)
            databasePath.toFile().parentFile.deleteRecursively()
        }
    }
}
