package com.example.poster

import com.example.poster.diagnostics.IntakeLimit
import com.example.poster.model.LoginRequest
import com.example.poster.model.MergeRequest
import com.example.poster.model.ModerationRepository
import com.example.poster.model.Post
import com.example.poster.model.RefreshTokenRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The holes a security review found, each with the request that used to work.
 *
 * One file rather than a test wedged into each feature's suite: these are not
 * about posts or accounts, they are about the boundary, and a reviewer coming
 * back to this code should be able to read the whole set in one place.
 */
class SecurityHardeningTest {

    /**
     * The merge endpoint verifies a password, so it is a place to guess one.
     * It was the only such place with no limit, and worth more than /login to
     * an attacker: a correct guess hands back a session for the account guessed.
     */
    @Test
    fun guessingAPasswordAtTheMergeEndpointIsThrottled() = withServer {
        confirmed("merge-victim@example.com")
        val attacker = confirmed("merge-attacker@example.com")

        repeat(5) {
            val refused = client.post("/auth/social/merge") {
                bearerAuth(attacker.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(MergeRequest.serializer(), MergeRequest(
                    email = "merge-victim@example.com", password = "wrong-$it",
                )))
            }
            assertEquals(HttpStatusCode.Unauthorized, refused.status, refused.bodyAsText())
        }

        val throttled = client.post("/auth/social/merge") {
            bearerAuth(attacker.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(MergeRequest.serializer(), MergeRequest(
                email = "merge-victim@example.com", password = "password123",
            )))
        }
        // The right password, and it still does not get in: the allowance was
        // already spent on the wrong ones.
        assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
    }

    /**
     * A ban used to set a status column and nothing else: the person kept their
     * refresh token, could sign in again with their password, and could go on
     * writing. Only their reading was curtailed.
     */
    @Test
    fun aBanEndsTheSessionAndRefusesEveryWayBackIn() = withServer {
        val user = confirmed("banned@example.com")
        postPost(user, "before-the-ban")

        ModerationRepository(testDatabase()).ban("admin", user.user.guid, "spam")

        val withOldToken = client.get("/posts/mine") { bearerAuth(user.tokens.accessToken) }
        assertEquals(HttpStatusCode.Unauthorized, withOldToken.status, "the access token outlived the ban")

        val refreshed = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RefreshTokenRequest.serializer(), RefreshTokenRequest(user.tokens.refreshToken)))
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshed.status, "the refresh token outlived the ban")

        val signedInAgain = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(LoginRequest.serializer(), LoginRequest("banned@example.com", "password123")))
        }
        assertEquals(HttpStatusCode.Unauthorized, signedInAgain.status, "a banned account signed back in")
    }

    /**
     * The feed orders by date, so a date the writer chooses is a position in
     * everybody else's feed. Anything ahead of now is brought back to now;
     * backdating is left alone, because the fixtures and the demo seed use it.
     */
    @Test
    fun aPostCannotBeDatedIntoTheFutureToPinItToTheFeed() = withServer {
        val user = confirmed("pinner@example.com")
        postPost(user, "pinned", date = "9999-01-01T00:00")
        postPost(user, "backdated", date = "2020-01-01T00:00")

        val mine = Json.decodeFromString<List<Post>>(
            client.get("/posts/mine") { bearerAuth(user.tokens.accessToken) }.bodyAsText(),
        )
        val pinned = mine.single { it.guid == "pinned" }
        assertTrue(
            pinned.date <= LocalDateTime.parse("2200-01-01T00:00"),
            "a post dated in the year 9999 was stored as sent: ${pinned.date}",
        )
        assertEquals(
            LocalDateTime.parse("2020-01-01T00:00"),
            mine.single { it.guid == "backdated" }.date,
            "backdating is what the demo seed does and must keep working",
        )
    }

    /**
     * Liking a post is not a permanent grant of access to it. The Liked tab was
     * the one read path that did not ask whether the viewer may still see the
     * row, so taking a post back to private left it readable in full.
     */
    @Test
    fun unpublishingAPostRemovesItFromEverybodyElsesLikedTab() = withServer {
        val author = confirmed("retractor@example.com")
        val reader = confirmed("liker@example.com")
        postPost(author, "retracted", title = "Public for now")

        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/favorites/retracted") { bearerAuth(reader.tokens.accessToken) }.status,
        )
        assertTrue(likedGuids(reader.tokens.accessToken).contains("retracted"), "the like did not register")

        // The author thinks better of it.
        postPost(author, "retracted", title = "Public for now", visibility = "private")

        assertFalse(
            likedGuids(reader.tokens.accessToken).contains("retracted"),
            "a post taken back to private was still readable on the liker's Liked tab",
        )
        // Still the author's own, on their own tab.
        assertEquals(
            HttpStatusCode.OK,
            client.get("/posts/byId/retracted") { bearerAuth(author.tokens.accessToken) }.status,
        )
    }

    /**
     * /crashes takes no token, because an app that crashes before anybody signs
     * in has none to send. That makes an unbounded body an anonymous way to
     * make the server allocate whatever it is sent.
     */
    @Test
    fun anOversizedBodyIsRefusedBeforeItIsRead() = withServer {
        val huge = "x".repeat(200_000)
        val refused = client.post("/crashes") {
            contentType(ContentType.Application.Json)
            setBody("""{"type":"T","message":"m","stack":"$huge","platform":"android","osVersion":"1","device":"d","appVersion":"1","occurredAt":"2026-09-21T10:00:00Z"}""")
        }
        assertTrue(
            refused.status == HttpStatusCode.PayloadTooLarge || refused.status == HttpStatusCode.BadRequest,
            "a 200 KB crash report was accepted: ${refused.status}",
        )
    }

    /**
     * The ceiling is server-wide, not something each route remembers to apply.
     * Checked on a route that takes no token, because that is the shape of the
     * request that costs nothing to send.
     */
    @Test
    fun aBodyOverTheCeilingIsRefusedOnAnyRoute() = withServer {
        val refused = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@example.com","password":"${"p".repeat(100_000)}"}""")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, refused.status)
    }

    /** The window reopens, so a flood costs a quarter of an hour and not the route. */
    @Test
    fun theIntakeLimitStopsAFloodAndThenRecovers() {
        var millis = 0L
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): Instant = Instant.ofEpochMilli(millis)
        }
        val limit = IntakeLimit(limit = 3, window = Duration.ofMinutes(15), clock = clock)

        assertTrue(limit.take() && limit.take() && limit.take(), "the first three were within the allowance")
        assertFalse(limit.take(), "the fourth was taken anyway")

        millis += Duration.ofMinutes(15).toMillis()
        assertTrue(limit.take(), "the window never reopened")
    }

    /**
     * A new address is an unconfirmed address. Carrying the old flag over let
     * somebody move to an inbox they do not own and still count as confirmed,
     * which is the gate on writing and what the admin bootstrap looks at.
     */
    @Test
    fun changingYourEmailLosesTheConfirmation() = withServer {
        val user = confirmed("before@example.com")

        val changed = client.post("/accounts") {
            bearerAuth(user.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(user.user.copy(email = "after@example.com")))
        }
        assertEquals(HttpStatusCode.NoContent, changed.status, changed.bodyAsText())

        // Writing is what a confirmed address buys, so writing is the check.
        postPost(user, "after-the-change", expect = null).let {
            assertEquals(HttpStatusCode.Forbidden, it.status, "an unconfirmed address could still post")
        }
    }

    /**
     * "Public" in the app means everyone signed in. A share token means
     * everyone at all, for good, with no way to take it back — so it is the
     * author's to mint.
     */
    @Test
    fun onlyTheAuthorCanMintAShareLinkForAPost() = withServer {
        if (!com.example.poster.config.Features.SHARING) return@withServer
        val author = confirmed("sharer@example.com")
        val stranger = confirmed("stranger@example.com")
        postPost(author, "shareable")

        val refused = client.post("/posts/shareable/share") { bearerAuth(stranger.tokens.accessToken) }
        assertEquals(HttpStatusCode.Forbidden, refused.status, "a stranger published somebody else's post")

        val mine = client.post("/posts/shareable/share") { bearerAuth(author.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, mine.status, mine.bodyAsText())
    }

    /**
     * Listing a group publicly lets any stranger join and read every group-only
     * post already in it, so it belongs with the other owner-only actions
     * rather than with what a promoted admin may do.
     */
    @Test
    fun onlyTheOwnerCanListAGroupPublicly() = withServer {
        if (!com.example.poster.config.Features.PUBLIC_GROUPS) return@withServer
        val owner = confirmed("group-owner@example.com")
        val admin = confirmed("group-admin@example.com")

        val created = client.post("/groups/create") {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Thursday Neighbours","visibility":"private"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val groupId = Json.parseToJsonElement(created.bodyAsText())
            .jsonObject["id"]!!.jsonPrimitive.content

        val refused = client.post("/groups/$groupId/visibility") {
            bearerAuth(admin.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"visibility":"public"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status, "somebody who does not own the group opened it")

        val allowed = client.post("/groups/$groupId/visibility") {
            bearerAuth(owner.tokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody("""{"visibility":"public"}""")
        }
        assertEquals(HttpStatusCode.NoContent, allowed.status)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.likedGuids(token: String): List<String> =
        Json.decodeFromString<List<Post>>(
            client.get("/favorites/me") { bearerAuth(token) }.bodyAsText(),
        ).map { it.guid }
}
