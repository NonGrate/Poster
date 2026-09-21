package com.example.poster.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The native Sign in with Apple, done in Swift and handed in at startup.
 *
 * ASAuthorizationController is an AppKit/UIKit flow with an Objective-C delegate,
 * far cleaner to drive from Swift than from Kotlin/Native interop, so Swift
 * conforms to this protocol (see AppleSignInBridge.swift) and does the work.
 * Callback-shaped so Swift can implement it: [signIn] calls back exactly once,
 * with an identity token on success, null on cancel, or an error message.
 */
interface AppleSignInLauncher {
    fun signIn(completion: (idToken: String?, error: String?) -> Unit)
}

/**
 * Sign in with Apple on iOS, native, beside [IosGoogleCredentials].
 *
 * [launcher] is the Swift bridge. Null means the build has no Sign in with Apple
 * capability wired and, as everywhere else, no button appears.
 */
class IosAppleCredentials(
    private val launcher: AppleSignInLauncher?,
) : AppleCredentials {

    override val available: Boolean get() = launcher != null

    override suspend fun requestIdToken(): String? {
        val launcher = launcher ?: return null
        return suspendCancellableCoroutine { continuation ->
            launcher.signIn { idToken, error ->
                when {
                    error != null -> continuation.resumeWithException(AppleCredentialsException(error))
                    // A null token with no error is a cancel: nothing happened.
                    else -> continuation.resume(idToken)
                }
            }
        }
    }
}
