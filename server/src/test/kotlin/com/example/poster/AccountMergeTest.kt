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
import com.example.poster.auth.SocialAccount
import com.example.poster.auth.SocialVerifier
import com.example.poster.model.AuthResponse
import com.example.poster.model.MergeRequest
import com.example.poster.model.RegisterRequest
import com.example.poster.model.SocialSignInRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
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
    fun mergingMovesTheContentAndLandsOnTheAccountYouKept() = withServer {
        // A: the account they already had, email + password.
        val kept = register("main@example.com")
        // B: a first Apple sign-in with Hide My Email → a separate account.
        val apple = social("apple").second!!
        assertNotEqualGuids(kept, apple)

        // Something written while on the Apple account, which must survive.
        assertEquals(HttpStatusCode.NoContent, postPost(apple.tokens.accessToken, apple.user.guid, "Carry my mother"))

        val merged = merge(apple.tokens.accessToken, MergeRequest(email = "main@example.com", password = "password123"))
        assertEquals(HttpStatusCode.OK, merged.first)
        val survivor = merged.second!!
        assertEquals(kept.user.guid, survivor.user.guid, "the merge landed on a different account")
        assertEquals("main@example.com", survivor.user.email, "the survivor did not keep its normal email")

        // The Apple post is now the kept account's.
        val mine = client.get("/posts/mine") { bearerAuth(survivor.tokens.accessToken) }.bodyAsText()
        assertTrue(mine.contains("Carry my mother"), "the merged account's post did not move to the survivor")

        // Signing in with Apple now lands on the kept account, not a new one.
        assertEquals(
            kept.user.guid,
            social("apple").second?.user?.guid,
            "the Apple identity did not follow the merge",
        )
    }

    @Test
    fun mergingIntoTheAccountYouAreAlreadyOnIsRefused() = withServer {
        val kept = register("main@example.com")
        val (status, _) = merge(kept.tokens.accessToken, MergeRequest(email = "main@example.com", password = "password123"))
        assertEquals(HttpStatusCode.BadRequest, status)
    }

    @Test
    fun mergingWithTheWrongPasswordIsRefused() = withServer {
        register("main@example.com")
        val apple = social("apple").second!!
        val (status, _) = merge(apple.tokens.accessToken, MergeRequest(email = "main@example.com", password = "wrong-password"))
        assertEquals(HttpStatusCode.Unauthorized, status)
        // And the Apple account is untouched — it still signs in as itself.
        assertEquals(apple.user.guid, social("apple").second?.user?.guid)
    }

    @Test
    fun mergingWithoutBeingSignedInIsRefused() = withServer {
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
        override fun verify(idToken: String) = SocialAccount(
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

    private suspend fun ApplicationTestBuilder.postPost(accessToken: String, author: String, title: String): HttpStatusCode =
        client.post("/posts") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                """{"guid":"merge-1","title":"$title","message":"words","author":"$author",""" +
                    """"group":null,"likes":0,"date":"2026-08-21T10:00","visibility":"public","tags":[],"language":"en"}""",
            )
        }.status

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Some", "Body", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) {
        val databasePath = Files.createTempDirectory("poster-merge").resolve("test.db")
        val previousDatabase = System.getProperty("poster.database")
        val previousDevelopment = System.getProperty("io.ktor.development")
        System.setProperty("poster.database", databasePath.toString())
        System.setProperty("io.ktor.development", "true")
        try {
            testApplication {
                application { module(verifiers = listOf(AppleStub())) }
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
