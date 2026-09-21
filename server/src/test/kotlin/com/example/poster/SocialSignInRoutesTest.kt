package com.example.poster

import com.example.poster.config.Features
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.auth.SocialAccount
import com.example.poster.auth.SocialSignInException
import com.example.poster.auth.SocialVerifier
import com.example.poster.model.AuthResponse
import com.example.poster.model.RegisterRequest
import com.example.poster.model.SocialSignInRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Signing in with Google, once the token has been believed.
 *
 * The verifier is stubbed here on purpose — whether a token is genuine is
 * settled in GoogleSignInTest, and repeating it would test the same thing
 * twice while making these harder to read. What is settled here is what
 * happens to the account afterwards, which is the part somebody notices:
 * landing on the account they already had, rather than a second one beside it.
 */
class SocialSignInRoutesTest {

    @Test
    fun signingInWithGoogleLandsOnTheAccountYouAlreadyHad() = withGoogle {
        // Registered the ordinary way first.
        val registered = register("somebody@example.com")

        val throughGoogle = social("google", "any-token")

        assertEquals(HttpStatusCode.OK, throughGoogle.first)
        assertEquals(
            registered.user.guid,
            throughGoogle.second?.user?.guid,
            "google sign-in created a second account instead of finding the first",
        )
    }

    @Test
    fun somebodyNewGetsAnAccount() = withGoogle {
        val (status, response) = social("google", "any-token")

        assertEquals(HttpStatusCode.OK, status)
        assertEquals("somebody@example.com", response?.user?.email)
        assertNotNull(response?.tokens?.accessToken)
    }

    /**
     * And that account is confirmed, because Google already did the confirming.
     * Otherwise somebody who signed in with Google would be told to go and check
     * an email they never asked for before they could write anything.
     */
    @Test
    fun anAccountReachedThroughGoogleCanWriteImmediately() = withGoogle {
        val session = social("google", "any-token").second
        assertNotNull(session)

        val posted = postPost(session, "social-1", title = "A post", message = "words", date = "2026-08-21T10:00", expect = null)

        assertEquals(HttpStatusCode.NoContent, posted.status, "a google account was asked to verify its email")
    }

    /**
     * Registered, never opened the email, then signed in with Google.
     *
     * The address is confirmed by that, and no link needs opening: reaching the
     * inbox is what our own email proves, and Google has already proved it. The
     * alternative is telling somebody Google has just vouched for to go and
     * find an email they ignored, before they can write anything.
     */
    @Test
    fun signingInWithGoogleConfirmsAnAccountThatNeverOpenedItsEmail() = withGoogle {
        if (!Features.EMAIL_VERIFICATION_REQUIRED) return@withGoogle
        val registered = register("somebody@example.com")
        // Unverified: registering does not confirm, and this proves it rather
        // than assuming it — otherwise the rest of this test proves nothing.
        assertEquals(
            HttpStatusCode.Forbidden,
            postPost(registered, "social-1", title = "A post", message = "words", date = "2026-08-21T10:00", expect = null).status,
            "an unverified account was allowed to write, so this test cannot show anything",
        )

        val throughGoogle = social("google", "any-token").second
        assertNotNull(throughGoogle)

        assertEquals(registered.user.guid, throughGoogle.user.guid)
        assertEquals(
            HttpStatusCode.NoContent,
            postPost(throughGoogle, "social-1", title = "A post", message = "words", date = "2026-08-21T10:00", expect = null).status,
            "google vouched for the address and the account is still unconfirmed",
        )
    }

    @Test
    fun aRefusedTokenSignsNobodyIn() = withGoogle {
        val (status, _) = social("google", REJECT)

        assertEquals(HttpStatusCode.Unauthorized, status)
    }

    /** A provider this build knows nothing about is not a bad request — it is unbuilt. */
    @Test
    fun aProviderThisBuildDoesNotHaveSaysSo() = withGoogle {
        val (status, _) = social("apple", "any-token")

        assertEquals(HttpStatusCode.NotImplemented, status)
    }

    @Test
    fun rubbishIsRefusedRatherThanCrashing() = withGoogle {
        val response = client.post("/auth/social") {
            contentType(ContentType.Application.Json)
            setBody("{\"nonsense\":true}")
        }

        assertTrue(
            response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.NotImplemented,
            "expected a refusal, got ${response.status}",
        )
    }

    /**
     * The Android web-flow callback bounces Apple's identity token back into the
     * app as a poster://auth/apple deep link, carrying the state untouched so
     * the app can match it to the request it started.
     */
    @Test
    fun theAppleCallbackBouncesTheTokenBackToTheApp() = withGoogle {
        val response = client.post("/auth/apple/callback") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("id_token=header.payload.signature&state=state-123")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("poster://auth/apple"), "no deep link in the callback page")
        assertTrue(body.contains("state=state-123"), "state was not carried back")
        assertTrue(body.contains("id_token=header.payload.signature"), "id_token was not carried back")
    }

    /** An Apple error is relayed, not swallowed, and no token is invented. */
    @Test
    fun theAppleCallbackRelaysAnErrorWithoutAToken() = withGoogle {
        val response = client.post("/auth/apple/callback") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=state-123&error=user_cancelled_authorize")
        }

        val body = response.bodyAsText()
        assertTrue(body.contains("error=user_cancelled_authorize"), "the error was not relayed")
        assertTrue(!body.contains("id_token="), "an error response should carry no token")
    }

    private suspend fun ApplicationTestBuilder.social(
        provider: String,
        idToken: String,
    ): Pair<HttpStatusCode, AuthResponse?> {
        val response = client.post("/auth/social") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SocialSignInRequest(provider, idToken)))
        }
        val body = if (response.status == HttpStatusCode.OK) {
            Json.decodeFromString<AuthResponse>(response.bodyAsText())
        } else {
            null
        }
        return response.status to body
    }

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    /** Believes anything except [REJECT], and always vouches for the same person. */
    private class StubGoogle : SocialVerifier {
        override val provider = "google"
        override val enabled = true
        override fun verify(idToken: String): SocialAccount {
            if (idToken == REJECT) throw SocialSignInException("refused for the test")
            return SocialAccount(
                provider = "google",
                subject = "google-subject-1",
                email = "somebody@example.com",
                name = "Some",
                surname = "Body",
            )
        }
    }

    private companion object {
        const val REJECT = "reject-me"
    }

    /** The shared server, with the stub Google this suite's tokens come from. */
    private fun withGoogle(block: suspend ApplicationTestBuilder.(TestServer) -> Unit) =
        withServer(verifiers = listOf(StubGoogle()), block = block)
}
