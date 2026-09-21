package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import com.example.poster.model.AppleExchangeResponse
import com.example.poster.model.AppleExchangeRequest
import io.ktor.http.contentType
import com.example.poster.model.User
import com.example.poster.model.AuthResponse
import com.example.poster.model.LoginRequest
import com.example.poster.model.MergeRequest
import com.example.poster.model.SocialSignInRequest
import com.example.poster.model.LogoutRequest
import com.example.poster.model.TokenRequest
import com.example.poster.model.EmailRequest
import com.example.poster.model.PasswordResetRequest
import com.example.poster.model.RegisterRequest
import com.example.poster.network.UserApi
import com.example.poster.auth.AuthTokenStorage
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import com.example.poster.network.SessionCheck

/**
 * Ktor implementation aligned with current server module routes.
 * Server routes for users are under /accounts (see server Application.kt).
 */
open class KtorUserApi(
    private val httpClient: HttpClient,
    private val authTokenStorage: AuthTokenStorage,
) : UserApi {

    /**
     * The one call that must tell "no" apart from "no answer".
     *
     * A 401 or 403 means the token is not accepted; a 404 means the account is
     * gone. Both are answers, and both mean this session is over. Anything else
     * — a timeout, a refused connection, a 500 — is the server not answering,
     * and says nothing about whether the session is valid.
     */
    override suspend fun verifySession(userId: String): SessionCheck {
        return try {
            val response = httpClient.get("accounts/byId/$userId") {
                contentType(ContentType.Application.Json)
            }
            when {
                response.status.isSuccess() -> SessionCheck.Valid(response.body())
                response.status == HttpStatusCode.Unauthorized ||
                    response.status == HttpStatusCode.Forbidden ||
                    response.status == HttpStatusCode.NotFound -> SessionCheck.Rejected
                else -> SessionCheck.Unreachable(null)
            }
        } catch (e: ResponseException) {
            // Ktor throws for error statuses when expectSuccess is on.
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                HttpStatusCode.NotFound -> SessionCheck.Rejected
                else -> SessionCheck.Unreachable(e)
            }
        } catch (e: Exception) {
            SessionCheck.Unreachable(e)
        }
    }

    override suspend fun getUserById(id: String): User? {
        return try {
            httpClient.get("accounts/byId/$id") {
                contentType(ContentType.Application.Json)
            }.body()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateUser(user: User) {
        // Server uses POST /accounts as upsert (no PUT route)
        httpClient.post("accounts") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }
    }

    override suspend fun logIn(user: String): User? {
        // Minimal login: fetch the user by ID
        return getUserById(user)
    }

    override suspend fun currentUser(): User? {
        return runCatching { httpClient.get("auth/me").body<User>() }.getOrNull()
    }

    override suspend fun authenticateUser(email: String, password: String): User? {
        val response = httpClient.post("auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = password))
        }.body<AuthResponse>()
        authTokenStorage.save(response.tokens)
        clearCachedBearerToken()
        return response.user
    }

    override suspend fun signInWithProvider(provider: String, idToken: String, nonce: String): User? {
        val response = httpClient.post("auth/social") {
            contentType(ContentType.Application.Json)
            setBody(SocialSignInRequest(provider = provider, idToken = idToken, nonce = nonce))
        }.body<AuthResponse>()
        authTokenStorage.save(response.tokens)
        clearCachedBearerToken()
        return response.user
    }

    override suspend fun exchangeAppleCode(code: String, verifier: String): String? {
        val response = httpClient.post("auth/apple/exchange") {
            contentType(ContentType.Application.Json)
            setBody(AppleExchangeRequest(code = code, verifier = verifier))
        }
        if (!response.status.isSuccess()) return null
        return response.body<AppleExchangeResponse>().idToken
    }

    override suspend fun mergeInto(request: MergeRequest): User? {
        // Authenticated as the account being given up; the client attaches its
        // token automatically. The reply is the survivor's session, so saving it
        // switches this app onto the account that was kept.
        val response = httpClient.post("auth/social/merge") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<AuthResponse>()
        authTokenStorage.save(response.tokens)
        clearCachedBearerToken()
        return response.user
    }

    override suspend fun createUser(
        name: String,
        surname: String,
        email: String,
        password: String,
        groupCode: String?,
        languages: List<String>,
        defaultLanguage: String?,
        showName: Boolean,
    ): User {
        val response = httpClient.post("auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    name = name,
                    surname = surname,
                    email = email,
                    password = password,
                    groupCode = groupCode,
                    languages = languages,
                    defaultLanguage = defaultLanguage,
                    showName = showName,
                ),
            )
        }.body<AuthResponse>()
        authTokenStorage.save(response.tokens)
        clearCachedBearerToken()
        return response.user
    }

    override suspend fun verifyEmail(token: String): Boolean =
        httpClient.post("auth/verify") {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(token))
        }.status.isSuccess()

    override suspend fun resendVerification() {
        runCatching { httpClient.post("auth/verify/resend") }
    }

    override suspend fun requestPasswordReset(email: String) {
        // Deliberately ignores the answer: the server says the same thing
        // whether or not the address is one of ours, and the app must not
        // appear to know more than it does.
        runCatching {
            httpClient.post("auth/password/forgot") {
                contentType(ContentType.Application.Json)
                setBody(EmailRequest(email))
            }
        }
    }

    override suspend fun requestMagicLink(email: String) {
        runCatching {
            httpClient.post("auth/magic/request") {
                contentType(ContentType.Application.Json)
                setBody(EmailRequest(email))
            }
        }
    }

    override suspend fun signInWithMagicLink(token: String): User? {
        val response = httpClient.post("auth/magic") {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(token))
        }
        if (!response.status.isSuccess()) return null
        val session = response.body<AuthResponse>()
        authTokenStorage.save(session.tokens)
        clearCachedBearerToken()
        return session.user
    }

    override suspend fun resetPassword(token: String, newPassword: String): Boolean =
        httpClient.post("auth/password/reset") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetRequest(token, newPassword))
        }.status.isSuccess()

    override suspend fun deleteAccount(userId: String): Boolean {
        val response = runCatching { httpClient.delete("accounts/$userId") }.getOrNull() ?: return false
        if (!response.status.isSuccess()) return false
        // The session died with the account on the server; drop it here too, or
        // the app keeps a token for somebody who no longer exists.
        authTokenStorage.clear()
        clearCachedBearerToken()
        return true
    }

    override suspend fun logout() {
        val refreshToken = authTokenStorage.load()?.refreshToken
        authTokenStorage.clear()
        clearCachedBearerToken()
        if (refreshToken != null) {
            runCatching {
                httpClient.post("auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(LogoutRequest(refreshToken))
                }
            }
        }
    }

    private fun clearCachedBearerToken() {
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }
}
