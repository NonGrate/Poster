package com.example.poster.auth

/**
 * Asking the platform who is signed in, and getting back a token to prove it.
 *
 * Only the token crosses this boundary. Everything about whether it is genuine
 * — the signature, the audience, the issuer, whether Google verified the
 * address — is settled on the server, because a client can be modified and a
 * server cannot.
 */
interface GoogleCredentials {
    /** False where this is not built or not supported, and then no button appears. */
    val available: Boolean

    /**
     * Returns an ID token, or null when the person changed their mind.
     *
     * Cancelling is not a failure and must not be reported as one: somebody who
     * opens the account sheet and closes it again has done nothing wrong.
     * Anything genuinely broken throws.
     */
    suspend fun requestIdToken(): String?
}

/**
 * Something went wrong that somebody could act on.
 *
 * Cancelling is not this: that returns null. This is a client id that does not
 * match the app's signing certificate, Play Services too old, a device with no
 * Google on it — all things worth saying out loud, and all things that used to
 * arrive as a tap that did nothing.
 */
class GoogleCredentialsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** No client id, no sign-in — the ordinary state of a build without one. */
object NoGoogleCredentials : GoogleCredentials {
    override val available = false
    override suspend fun requestIdToken(): String? = null
}
