package com.example.poster.auth

/**
 * Sign in with Apple, the same shape as [GoogleCredentials].
 *
 * Only the identity token crosses this boundary; the server settles whether it
 * is genuine (signature, issuer `appleid.apple.com`, audience, expiry). On iOS
 * this is the native ASAuthorizationController; on Android it is a browser
 * round-trip to Apple, because Apple ships no native SDK there.
 */
interface AppleCredentials {
    /** False where this is not built or not supported — then no button appears. */
    val available: Boolean

    /**
     * An Apple identity token, or null when the person cancelled. Cancelling is
     * not a failure and must not be reported as one; anything genuinely broken
     * throws [AppleCredentialsException].
     */
    suspend fun requestIdToken(): String?
}

/** Something went wrong that somebody could act on — not a cancellation. */
class AppleCredentialsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** No Apple sign-in configured — the ordinary state of a build without it. */
object NoAppleCredentials : AppleCredentials {
    override val available = false
    override suspend fun requestIdToken(): String? = null
}
