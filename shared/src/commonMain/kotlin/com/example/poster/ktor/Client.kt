package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.URLProtocol
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import com.example.poster.auth.AuthTokenStorage
import com.example.poster.model.AuthResponse
import com.example.poster.model.RefreshTokenRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/**
 * The body, or [fallback] when the server answered with anything but a 2xx.
 *
 * For the reads where "no answer" is an answer — an empty list is the right
 * thing to show, and throwing would only turn a bad connection into a crash.
 */
internal suspend inline fun <reified T> HttpResponse.bodyOr(fallback: T): T =
    if (status.isSuccess()) body() else fallback

/**
 * For the writes, where it is not. The client sets no `expectSuccess`, so
 * without this a 400 or 500 returns normally and a write that never happened
 * looks like one that did.
 */
internal fun HttpResponse.failIfNotSuccess(what: String) {
    if (!status.isSuccess()) error("$what failed: ${status.value}")
}

fun createHttpClient(
    serverHost: String,
    serverPort: Int,
    serverScheme: String = "http",
    authTokenStorage: AuthTokenStorage? = null,
    cookieSession: Boolean = false,
) = HttpClient {
    configureClient(
        serverHost = serverHost,
        serverPort = serverPort,
        serverScheme = serverScheme,
        authTokenStorage = authTokenStorage,
        cookieSession = cookieSession,
    )
}

internal fun createHttpClient(
    engine: HttpClientEngine,
    serverHost: String,
    serverPort: Int,
    serverScheme: String,
    authTokenStorage: AuthTokenStorage? = null,
) = HttpClient(engine) {
    configureClient(
        serverHost = serverHost,
        serverPort = serverPort,
        serverScheme = serverScheme,
        authTokenStorage = authTokenStorage,
    )
}

private fun HttpClientConfig<*>.configureClient(
    serverHost: String,
    serverPort: Int,
    serverScheme: String,
    authTokenStorage: AuthTokenStorage?,
    /**
     * Ask the server to keep the refresh token in a cookie script cannot read.
     *
     * Only the web build sets it, and only when its API is the origin the page
     * came from — the cookie is SameSite=Strict, so a web app hosted somewhere
     * else would simply never receive it and could not stay signed in.
     */
    cookieSession: Boolean = false,
) {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            isLenient = true
            coerceInputValues = true
            ignoreUnknownKeys = true
        })
    }

    if (authTokenStorage != null) {
        install(Auth) {
            bearer {
                loadTokens {
                    authTokenStorage.load()?.let {
                        BearerTokens(it.accessToken, it.refreshToken)
                    }
                }
                refreshTokens {
                    // With a cookie session there is no token to send: the
                    // browser attaches it and the server reads it there.
                    val refreshToken = oldTokens?.refreshToken.orEmpty()
                    if (refreshToken.isEmpty() && !cookieSession) return@refreshTokens null
                    val response = runCatching {
                        client.post("auth/refresh") {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshTokenRequest(refreshToken))
                        }.body<AuthResponse>()
                    }.getOrElse {
                        // A refresh that fails used to throw out of here, and
                        // the caller saw an exception rather than the 401 the
                        // server was trying to give it — so a session that had
                        // genuinely expired was indistinguishable from a bad
                        // connection. Forgetting the tokens lets the next
                        // request go unauthenticated and be refused plainly.
                        authTokenStorage.clear()
                        return@refreshTokens null
                    }
                    authTokenStorage.save(response.tokens)
                    BearerTokens(response.tokens.accessToken, response.tokens.refreshToken)
                }
                sendWithoutRequest { request ->
                    request.url.build().encodedPath !in PUBLIC_AUTH_PATHS
                }
            }
        }
    }

    defaultRequest {
        url.protocol = when (serverScheme.lowercase()) {
            "http" -> URLProtocol.HTTP
            "https" -> URLProtocol.HTTPS
            else -> error("Unsupported server scheme: $serverScheme")
        }
        host = serverHost
        port = serverPort
        // Says "keep my refresh token in a cookie". A header rather than a
        // cookie flag because a browser sends cookies on any request to this
        // host, including one a third-party page caused — so the header is
        // what distinguishes this app's own fetch, and setting it
        // cross-origin needs a preflight the CORS allowlist has to approve.
        if (cookieSession) header("X-Poster-Session", "cookie")
    }
}

private val PUBLIC_AUTH_PATHS = setOf(
    "/auth/register",
    "/auth/login",
    "/auth/refresh",
    "/auth/logout",
)
