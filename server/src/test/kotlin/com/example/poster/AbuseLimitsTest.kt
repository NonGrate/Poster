package com.example.poster

import com.example.poster.auth.AttemptThrottle
import com.example.poster.config.Features
import com.example.poster.model.AppNotification
import com.example.poster.model.ModerationRepository
import com.example.poster.model.RegisterRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What one account, or one stranger, can make this server do.
 *
 * The theme is that an allowance has to be counted against the thing being
 * protected — the inbox, the person, the channel — and not against a spelling
 * the caller chooses.
 */
class AbuseLimitsTest {

    /**
     * Gmail treats somebody+1@ and somebody@ as one inbox. Counting them
     * separately meant every allowance could be multiplied by inventing
     * suffixes, which turned registration into a way to mail a stranger from
     * this domain as often as you liked.
     */
    @Test
    fun oneInboxIsOneAllowanceWhateverTheSpelling() {
        val throttle = AttemptThrottle(limit = 2)
        throttle.recordFailure("victim@gmail.com")
        throttle.recordFailure("v.i.c.t.i.m+one@gmail.com")

        assertTrue(
            throttle.retryAfter("victim+two@gmail.com") != null,
            "a plus suffix bought a fresh allowance for the same inbox",
        )
        assertTrue(
            throttle.retryAfter("someone.else@gmail.com") == null,
            "a different inbox was caught up in it",
        )
        // Only Gmail folds dots and suffixes; elsewhere they are real addresses.
        assertTrue(throttle.retryAfter("victim+two@fastmail.com") == null)
    }

    /** A ban a plus suffix undoes is not a ban. */
    @Test
    fun aBannedInboxCannotRegisterAgainUnderAnotherSpelling() = withServer {
        val banned = confirmed("evader@gmail.com")
        ModerationRepository(testDatabase()).ban("admin", banned.user.guid, "spam")

        val again = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest("E", "V", "ev.ader+new@gmail.com", "password123")))
        }
        assertEquals(HttpStatusCode.Conflict, again.status, again.bodyAsText())

        // Somebody unrelated is not caught by it.
        val innocent = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest.serializer(), RegisterRequest("I", "N", "someone@gmail.com", "password123")))
        }
        assertEquals(HttpStatusCode.OK, innocent.status, innocent.bodyAsText())
    }

    /**
     * Liking twice changes nothing, so the second one must not tell the author
     * again. Otherwise a loop is unlimited notifications and pushes at them.
     */
    @Test
    fun likingTheSamePostTwiceNotifiesOnce() = withServer {
        if (!Features.LIKES || !Features.PUSH_NOTIFICATIONS) return@withServer
        val author = confirmed("liked-author@example.com")
        val reader = confirmed("repeat-liker@example.com")
        postPost(author, "liked-once")

        repeat(5) {
            client.post("/favorites/liked-once") { bearerAuth(reader.tokens.accessToken) }
        }

        val activity = Json.decodeFromString<List<AppNotification>>(
            client.get("/notifications") { bearerAuth(author.tokens.accessToken) }.bodyAsText(),
        )
        assertEquals(
            1,
            activity.count { it.postGuid == "liked-once" },
            "five likes of one post produced more than one notification",
        )
    }

    /** One account cannot make every push fan out to a thousand devices. */
    @Test
    fun theNumberOfDevicesOneAccountCanRegisterIsCapped() = withServer {
        if (!Features.PUSH_NOTIFICATIONS) return@withServer
        val user = confirmed("many-devices@example.com")

        repeat(15) { i ->
            val response = client.post("/devices") {
                bearerAuth(user.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody("""{"token":"token-$i","platform":"android"}""")
            }
            assertEquals(HttpStatusCode.NoContent, response.status)
        }

        val kept = testDatabase().deviceQueries.devicesForUser(user.user.guid).executeAsList()
        assertTrue(kept.size <= 10, "kept ${kept.size} devices for one account")
    }

    /**
     * A token goes into the path of a request to Apple, so it cannot contain
     * anything that would change which URL this server calls.
     */
    @Test
    fun aDeviceTokenThatIsNotATokenIsRefused() = withServer {
        if (!Features.PUSH_NOTIFICATIONS) return@withServer
        val user = confirmed("bad-token@example.com")

        val refused = client.post("/devices") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"token":"../../3/device/somebody-else?x=","platform":"ios"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, refused.status)
    }
}
