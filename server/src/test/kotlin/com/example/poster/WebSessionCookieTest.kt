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
 * The browser's session, kept where a script cannot read it.
 *
 * The web build used to hold both tokens in localStorage, so one injected
 * script took a thirty-day session. The refresh token is now a cookie the
 * server sets HttpOnly, and the body carries nothing to store.
 */
class WebSessionCookieTest {

    @Test
    fun askingForACookieSessionKeepsTheRefreshTokenOutOfTheBody() = withServer {
        val response = client.post("/auth/register") {
            header(SessionCookie.REQUEST_HEADER, "cookie")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Web","surname":"Person","email":"web@example.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        val body = Json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals("", body.tokens.refreshToken, "the refresh token was still in the body")
        assertTrue(body.tokens.accessToken.isNotBlank(), "no access token to work with")

        val cookie = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .single { it.startsWith(SessionCookie.NAME) }
        assertTrue("HttpOnly" in cookie, "a script could read the session: $cookie")
        assertTrue("SameSite=Strict" in cookie, "the cookie was not same-site: $cookie")
        assertTrue("/auth" in cookie, "the cookie went further than the auth routes: $cookie")
    }

    /** Without the header nothing changes, which is what the apps rely on. */
    @Test
    fun anOrdinaryCallerStillGetsItsTokenInTheBody() = withServer {
        val user = confirmed("app@example.com")
        assertTrue(user.tokens.refreshToken.isNotBlank(), "the apps lost their refresh token")
    }

    /** Signing out takes the cookie with it, whoever is next on this browser. */
    @Test
    fun signingOutClearsTheCookie() = withServer {
        val response = client.post("/auth/logout") {
            header(SessionCookie.REQUEST_HEADER, "cookie")
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":""}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val cookie = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .single { it.startsWith(SessionCookie.NAME) }
        assertTrue("Max-Age=0" in cookie || "max-age=0" in cookie, "the cookie outlived the session: $cookie")
    }
}
