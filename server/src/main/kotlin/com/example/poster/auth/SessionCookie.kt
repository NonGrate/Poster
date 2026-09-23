package com.example.poster.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.plugins.origin

/**
 * Holding a browser's refresh token where script cannot reach it.
 *
 * The web build kept both tokens in `localStorage`, which any script on the
 * origin can read — so one injected script walked away with a thirty-day
 * session rather than the fifteen minutes an access token is worth. A cookie
 * marked `HttpOnly` is not readable by script at all, which is the only
 * defence that survives an injection.
 *
 * Opt-in per request, by a header the page's own fetch sets and a cross-site
 * form cannot: a browser sends cookies on any request it makes to this host,
 * so the header is what says "this caller wants cookie sessions" and doubles
 * as the CSRF defence. Setting a header on a cross-origin request triggers a
 * preflight, and the CORS allowlist answers that for configured origins only.
 *
 * `SameSite=Strict` because the supported arrangement is the web app served
 * by this same server (`POSTER_WEB_DIR`, at `/app`). A web app on another
 * origin does not get the cookie and keeps the old behaviour, which is why
 * the client only asks for cookie mode when its API is same-origin.
 */
object SessionCookie {
    const val NAME = "poster_refresh"

    /** The header a caller sets to say it wants the refresh token as a cookie. */
    const val REQUEST_HEADER = "X-Poster-Session"
    private const val REQUEST_VALUE = "cookie"

    fun wanted(call: ApplicationCall): Boolean =
        call.request.header(REQUEST_HEADER)?.equals(REQUEST_VALUE, ignoreCase = true) == true

    fun read(call: ApplicationCall): String? =
        call.request.cookies[NAME]?.takeIf { it.isNotBlank() }

    fun set(call: ApplicationCall, refreshToken: String, ttlSeconds: Long) {
        call.response.header(HttpHeaders.SetCookie, header(refreshToken, ttlSeconds, secure(call)))
    }

    fun clear(call: ApplicationCall) {
        call.response.header(HttpHeaders.SetCookie, header("", 0, secure(call)))
    }

    /**
     * Written out rather than built with `response.cookies.append`.
     *
     * Ktor appends a `$x-enc=…` marker to the header so it can decode its own
     * cookie later, whatever encoding is asked for. Nothing else needs that
     * marker, browsers and proxies have to ignore it, and this is a header
     * worth being able to read at a glance. The value is base64url, so there
     * is nothing here to encode.
     */
    private fun header(value: String, maxAge: Long, secure: Boolean): String = buildString {
        append(NAME).append('=').append(value)
        append("; Max-Age=").append(maxAge)
        append("; Path=/auth")
        append("; HttpOnly")
        append("; SameSite=Strict")
        if (secure) append("; Secure")
    }

    /**
     * Secure everywhere except a plain-HTTP local run, where a Secure cookie
     * would simply never be stored and the web app would look broken for a
     * reason nothing says out loud.
     */
    private fun secure(call: ApplicationCall): Boolean =
        !(call.request.header(HttpHeaders.Origin).orEmpty().startsWith("http://") ||
            call.request.origin.scheme == "http")
}
