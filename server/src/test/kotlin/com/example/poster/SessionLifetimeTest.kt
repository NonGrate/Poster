package com.example.poster

import com.example.poster.model.AuthResponse
import com.example.poster.model.RefreshTokenRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * How long a signed-in session lasts, and what happens when its token turns
 * up twice.
 *
 * A refresh token rotates: using it returns a new one and retires the old.
 * That is only half a defence. The other half is noticing when a retired one
 * comes back, because the only way that happens is if somebody kept a copy.
 */
class SessionLifetimeTest {

    @Test
    fun rotatingWorksAndHandsBackADifferentToken() = withServer {
        val user = confirmed("rotate@example.com")

        val rotated = refresh(user.tokens.refreshToken)
        assertEquals(HttpStatusCode.OK, rotated.first, rotated.second)
        val next = Json.decodeFromString<AuthResponse>(rotated.second)
        assertNotEquals(user.tokens.refreshToken, next.tokens.refreshToken, "the same token came back")

        // The new one works, which is the ordinary case.
        assertEquals(HttpStatusCode.OK, refresh(next.tokens.refreshToken).first)
    }

    /**
     * The copied-token case. One of the two holders is not the owner, and
     * there is no way to tell which from here — so both are signed out and
     * the real person signs in again with their password.
     */
    @Test
    fun replayingASpentTokenEndsEverySessionTheAccountHas() = withServer {
        val user = confirmed("replayed@example.com")

        val first = Json.decodeFromString<AuthResponse>(refresh(user.tokens.refreshToken).second)
        // Somebody kept the original and presents it after the owner rotated.
        assertEquals(
            HttpStatusCode.Unauthorized,
            refresh(user.tokens.refreshToken).first,
            "a spent token was accepted",
        )
        // And the session that came out of the honest rotation is gone too.
        assertEquals(
            HttpStatusCode.Unauthorized,
            refresh(first.tokens.refreshToken).first,
            "the account kept a live session after a token of its was replayed",
        )
    }

    private suspend fun ApplicationTestBuilder.refresh(token: String): Pair<HttpStatusCode, String> {
        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RefreshTokenRequest.serializer(), RefreshTokenRequest(token)))
        }
        return response.status to response.bodyAsText()
    }
}
