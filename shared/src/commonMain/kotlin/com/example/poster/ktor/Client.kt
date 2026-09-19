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
import io.ktor.http.URLProtocol
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import com.example.poster.auth.AuthTokenStorage
import com.example.poster.model.AuthResponse
import com.example.poster.model.RefreshTokenRequest

fun createHttpClient(
    serverHost: String,
    serverPort: Int,
    serverScheme: String = "http",
    authTokenStorage: AuthTokenStorage? = null,
) = HttpClient {
    configureClient(
        serverHost = serverHost,
        serverPort = serverPort,
        serverScheme = serverScheme,
        authTokenStorage = authTokenStorage,
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
                    val refreshToken = oldTokens?.refreshToken ?: return@refreshTokens null
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
    }
}

private val PUBLIC_AUTH_PATHS = setOf(
    "/auth/register",
    "/auth/login",
    "/auth/refresh",
    "/auth/logout",
)
