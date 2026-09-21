package com.example.poster.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val surname: String,
    val email: String,
    val password: String,
    val groupCode: String? = null,
    /** Empty means the default: an older client that does not send them. */
    val languages: List<String> = emptyList(),
    val defaultLanguage: String? = null,
    /** Opt-in name visibility, chosen at sign-up. Defaults off for older clients. */
    val showName: Boolean = false,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

/**
 * Signing in with a token from Google or, later, Apple.
 *
 * The provider is named rather than assumed, so one route serves both and the
 * server decides which verifier — if any — this deployment was built with.
 */
@Serializable
data class SocialSignInRequest(
    val provider: String,
    val idToken: String,
)

/**
 * Merge the account you are signed in to (typically a fresh Apple/private-relay
 * one) into an account you already have, proving you own that other account by
 * either its email + password or a provider token. One pair is set, not both.
 */
@Serializable
data class MergeRequest(
    val email: String? = null,
    val password: String? = null,
    val provider: String? = null,
    val idToken: String? = null,
)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

@Serializable
data class AuthResponse(
    val user: User,
    val tokens: AuthTokens,
)

@Serializable
data class ApiError(
    val message: String,
    /**
     * A stable name for what went wrong, for the app to branch on.
     *
     * The message is written for a person and may be reworded or translated at
     * any time; this may not. [ApiError.EMAIL_NOT_VERIFIED] is the one the app
     * turns into a screen rather than a toast.
     */
    val code: String? = null,
) {
    companion object {
        const val EMAIL_NOT_VERIFIED = "email_not_verified"
    }
}

/** Following a link from an email: verification, or the first half of a reset. */
@Serializable
data class TokenRequest(val token: String)

/** Asking for a reset. Answered identically whether or not the address is ours. */
@Serializable
data class EmailRequest(val email: String)

/** Finishing a reset: the token proves the inbox, the password is the new one. */
@Serializable
data class PasswordResetRequest(val token: String, val password: String)

/** Why somebody reported a post. Optional: the act of reporting is the signal. */
@Serializable
data class ReportRequest(val reason: String? = null)
