package com.example.poster.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The native Google Sign-In, done in Swift and handed in at startup.
 *
 * Kotlin cannot call the GoogleSignIn SDK directly — it is a Swift package — so
 * Swift conforms to this protocol (see GoogleSignInBridge.swift) and does the
 * work. Callback-shaped rather than suspend precisely so Swift can implement it:
 * [signIn] calls back exactly once, with a token on success, null on cancel, or
 * an error message.
 */
interface GoogleSignInLauncher {
    fun signIn(nonce: String, completion: (idToken: String?, error: String?) -> Unit)
}

/**
 * Google sign-in on iOS through Google's native SDK, for parity with Android's
 * Credential Manager: the SDK keeps its own session, so after the first grant
 * re-auth is silent — no consent screen and no "access granted" email each time,
 * which is what the earlier browser flow could not avoid.
 *
 * [launcher] is the Swift bridge. Null means the build was given no client id
 * and, as everywhere else, no button appears.
 */
class IosGoogleCredentials(
    private val launcher: GoogleSignInLauncher?,
) : GoogleCredentials {

    override val available: Boolean get() = launcher != null

    override suspend fun requestCredential(): SocialCredential? {
        val launcher = launcher ?: return null
        // Only the hash reaches the provider; the value is what the server
        // hashes and compares, and it never leaves this device otherwise.
        val nonce = IosNonce.random()
        return suspendCancellableCoroutine { continuation ->
            launcher.signIn(IosNonce.hash(nonce)) { idToken, error ->
                when {
                    error != null -> continuation.resumeWithException(GoogleCredentialsException(error))
                    // A null token with no error is a cancel: nothing happened,
                    // so nothing is said.
                    else -> continuation.resume(idToken?.let { SocialCredential(it, nonce) })
                }
            }
        }
    }
}
