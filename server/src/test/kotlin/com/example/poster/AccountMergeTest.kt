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
import com.example.poster.config.Features
import com.example.poster.auth.SocialAccount
import com.example.poster.auth.SocialVerifier
import com.example.poster.model.AuthResponse
import com.example.poster.model.MergeRequest
import com.example.poster.model.RegisterRequest
import com.example.poster.model.SocialSignInRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Folding a fresh Apple/private-relay account into the one somebody already had.
 *
 * This is the operation `/auth/social/link` refuses: the Apple identity is
 * already bound to the new account, so it is a merge of two accounts, not a
 * link. Signed in as the account being given up, the person proves they own the
 * other, everything moves, and they end up on the account they kept.
 */
class AccountMergeTest {

    @Test
    fun mergingMovesTheContentAndLandsOnTheAccountYouKept() = withApple {
        // A: the account they already had, email + password.
        val kept = register("main@example.com")
        // B: a first Apple sign-in with Hide My Email → a separate account.
        val apple = social("apple").second!!
        assertNotEqualGuids(kept, apple)

        // Something written while on the Apple account, which must survive.
        postPost(apple, "merge-1", title = "Carry my mother", message = "words", date = "2026-08-21T10:00")
        // And the rest of what hangs off an account. Every one of these is a
        // User row with ON DELETE CASCADE behind it, so a merge that only
        // moved the posts threw the lot away when it deleted the merged id.
        if (Features.COMMENTS) {
            assertEquals(
                HttpStatusCode.OK,
                client.post("/posts/merge-1/comments") {
                    bearerAuth(apple.tokens.accessToken)
                    contentType(ContentType.Application.Json)
                    setBody("""{"text":"She would have liked this"}""")
                }.status,
            )
        }
        if (Features.BOOKMARKS) {
            assertEquals(
                HttpStatusCode.NoContent,
                client.post("/bookmarks/merge-1") { bearerAuth(apple.tokens.accessToken) }.status,
            )
        }

        val merged = merge(apple.tokens.accessToken, MergeRequest(email = "main@example.com", password = "password123"))
        assertEquals(HttpStatusCode.OK, merged.first)
        val survivor = merged.second!!
        assertEquals(kept.user.guid, survivor.user.guid, "the merge landed on a different account")
        assertEquals("main@example.com", survivor.user.email, "the survivor did not keep its normal email")

        // The Apple post is now the kept account's.
        val mine = client.get("/posts/mine") { bearerAuth(survivor.tokens.accessToken) }.bodyAsText()
        assertTrue(mine.contains("Carry my mother"), "the merged account's post did not move to the survivor")

        if (Features.COMMENTS) {
            val comments = client.get("/posts/merge-1/comments") {
                bearerAuth(survivor.tokens.accessToken)
            }.bodyAsText()
            assertTrue(comments.contains("She would have liked this"), "the comment went with the merged account")
            assertTrue(comments.contains(survivor.user.guid), "the comment still names an account that no longer exists")
        }
        if (Features.BOOKMARKS) {
            assertEquals(
                listOf("merge-1"),
                Json.decodeFromString<List<String>>(
                    client.get("/bookmarks") { bearerAuth(survivor.tokens.accessToken) }.bodyAsText(),
                ),
                "what the merged account had saved was thrown away",
            )
        }

        // Signing in with Apple now lands on the kept account, not a new one.
        assertEquals(
            kept.user.guid,
            social("apple").second?.user?.guid,
            "the Apple identity did not follow the merge",
        )
    }

    @Test
    fun mergingIntoTheAccountYouAreAlreadyOnIsRefused() = withApple {
        val kept = register("main@example.com")
        val (status, _) = merge(kept.tokens.accessToken, MergeRequest(email = "main@example.com", password = "password123"))
        assertEquals(HttpStatusCode.BadRequest, status)
    }

    @Test
    fun mergingWithTheWrongPasswordIsRefused() = withApple {
        register("main@example.com")
        val apple = social("apple").second!!
        val (status, _) = merge(apple.tokens.accessToken, MergeRequest(email = "main@example.com", password = "wrong-password"))
        assertEquals(HttpStatusCode.Unauthorized, status)
        // And the Apple account is untouched — it still signs in as itself.
        assertEquals(apple.user.guid, social("apple").second?.user?.guid)
    }

    @Test
    fun mergingWithoutBeingSignedInIsRefused() = withApple {
        val response = client.post("/auth/social/merge") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(MergeRequest(email = "main@example.com", password = "password123")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // — helpers —

    private fun assertNotEqualGuids(a: AuthResponse, b: AuthResponse) =
        assertTrue(a.user.guid != b.user.guid, "test setup produced one account, not two")

    /** Apple relay by default; the merge target is the email/password account. */
    private class AppleStub : SocialVerifier {
        override val provider = "apple"
        override val enabled = true
        override fun verify(idToken: String, nonce: String) = SocialAccount(
            provider = "apple",
            subject = "apple-subject-1",
            email = "a1b2c3d4@privaterelay.appleid.com",
            name = null,
            surname = null,
        )
    }

    private suspend fun ApplicationTestBuilder.social(provider: String): Pair<HttpStatusCode, AuthResponse?> {
        val response = client.post("/auth/social") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(SocialSignInRequest(provider, "any-token")))
        }
        val body = if (response.status == HttpStatusCode.OK) Json.decodeFromString<AuthResponse>(response.bodyAsText()) else null
        return response.status to body
    }

    private suspend fun ApplicationTestBuilder.merge(accessToken: String, request: MergeRequest): Pair<HttpStatusCode, AuthResponse?> {
        val response = client.post("/auth/social/merge") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }
        val body = if (response.status == HttpStatusCode.OK) Json.decodeFromString<AuthResponse>(response.bodyAsText()) else null
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

    /** The shared server, with the stub Apple this suite's tokens come from. */
    private fun withApple(block: suspend ApplicationTestBuilder.(TestServer) -> Unit) =
        withServer(verifiers = listOf(AppleStub()), block = block)
}
