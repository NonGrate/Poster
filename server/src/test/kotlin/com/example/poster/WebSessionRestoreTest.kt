package com.example.poster

import com.example.poster.auth.SessionCookie
import com.example.poster.model.AuthResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coming back to the web app after a reload.
 *
 * Nothing is kept on the page: the access token lives for the life of the tab
 * and the refresh token is a cookie no script can read. So a reload has only
 * the cookie to go on, and this is the path it takes.
 */
class WebSessionRestoreTest {

    /**
     * Signing in must set the cookie. It did not: only the routes that shared
     * one helper set it, and /login — the one a browser uses most — was not
     * one of them, so the web app came back to a login screen every time.
     */
    @Test
    fun signingInSetsTheCookieAndRefreshingUsesIt() = withServer {
        confirmed("reload@example.com")

        val login = client.post("/auth/login") {
            header(SessionCookie.REQUEST_HEADER, "cookie")
            contentType(ContentType.Application.Json)
            setBody("""{"email":"reload@example.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status, login.bodyAsText())

        val setCookie = login.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .single { it.startsWith(SessionCookie.NAME) }
        assertTrue("HttpOnly" in setCookie, "a script could read the session: $setCookie")
        // Ktor writes a $x-enc marker of its own when it builds the header;
        // nothing else needs it and this one is worth reading at a glance.
        assertTrue("\$x-enc" !in setCookie, "the header carried Ktor's encoding marker: $setCookie")
        assertEquals(
            "",
            Json.decodeFromString<AuthResponse>(login.bodyAsText()).tokens.refreshToken,
            "the refresh token was in the body as well as the cookie",
        )

        // The reload: no token to send, only the cookie the browser holds.
        val cookie = setCookie.substringBefore(';')
        val refreshed = client.post("/auth/refresh") {
            header(HttpHeaders.Cookie, cookie)
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":""}""")
        }
        assertEquals(HttpStatusCode.OK, refreshed.status, refreshed.bodyAsText())
        assertTrue(
            Json.decodeFromString<AuthResponse>(refreshed.bodyAsText()).tokens.accessToken.isNotBlank(),
            "the cookie did not buy a session back",
        )
    }
}
