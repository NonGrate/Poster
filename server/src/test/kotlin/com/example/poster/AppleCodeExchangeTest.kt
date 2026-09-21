package com.example.poster

import com.example.poster.auth.AppleCodes
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Apple browser round-trip on Android, and why the identity token is no
 * longer in the URL that comes back.
 *
 * A custom scheme is first-come on Android: another installed app may declare
 * `poster://` and receive the callback. The token stays on the server and the
 * URL carries a code that is worthless without the verifier the app kept.
 */
class AppleCodeExchangeTest {

    @Test
    fun theCallbackHandsBackACodeAndNotTheToken() = withServer {
        val verifier = "a-verifier-only-this-app-knows"
        val state = "nonce." + AppleCodes.challengeFor(verifier)

        val response = client.post("/auth/apple/callback") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=$state&id_token=the-identity-token")
        }
        val page = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("code=" in page, "the callback did not return a code")
        assertTrue(
            "the-identity-token" !in page,
            "the identity token was in the page an intercepting app can read",
        )
    }

    /** The code is one attempt, and only for whoever generated the verifier. */
    @Test
    fun aCodeIsWorthlessWithoutTheVerifierAndIsSpentOnce() {
        val codes = AppleCodes()
        val verifier = "the-right-verifier"
        val code = codes.mint("the-identity-token", AppleCodes.challengeFor(verifier))

        assertNull(
            codes.redeem(code, "a-guess"),
            "a wrong verifier redeemed the token",
        )
        assertNull(
            codes.redeem(code, verifier),
            "the code survived a wrong guess, so it could be brute-forced",
        )

        val fresh = codes.mint("the-identity-token", AppleCodes.challengeFor(verifier))
        assertEquals("the-identity-token", codes.redeem(fresh, verifier))
        assertNull(codes.redeem(fresh, verifier), "the code was spendable twice")
    }

    /**
     * An app from before the exchange existed gets an error, not the token:
     * answering the old shape would leave the hole open to anybody who asked
     * for it that way.
     */
    @Test
    fun aClientWithoutAChallengeIsToldToUpdate() = withServer {
        val response = client.post("/auth/apple/callback") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=nonce-with-no-challenge&id_token=the-identity-token")
        }
        val page = response.bodyAsText()

        assertTrue("error=update_required" in page, "no error for a client that cannot exchange")
        assertTrue("the-identity-token" !in page, "the token was sent to an old client anyway")
    }

    /** The route refuses a code it never minted. */
    @Test
    fun anUnknownCodeIsRefused() = withServer {
        val response = client.post("/auth/apple/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"invented","verifier":"anything"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
