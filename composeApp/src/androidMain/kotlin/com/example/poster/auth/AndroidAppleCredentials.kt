package com.example.poster.auth

import com.example.poster.config.AppInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Sign in with Apple on Android — a browser round-trip, because Apple ships no
 * native SDK here.
 *
 * The app opens Apple's sign-in in a browser (`client_id` = the Services ID,
 * `response_mode=form_post`). Apple POSTs the result to the server's
 * `/auth/apple/callback`, which bounces it back into the app as a
 * `poster://auth/apple` deep link carrying the identity token. [requestIdToken]
 * suspends until that deep link arrives (routed here by [AppleWebCallback]), then
 * hands the token up like any other provider — the server verifies it (audience
 * = the Services ID).
 *
 * [serviceId] blank means no Apple button on Android. [redirectUri] must match
 * the return URL registered on the Services ID exactly (the production URL), and
 * the server it points at must have POSTER_APPLE_SERVICE_ID set.
 */
class AndroidAppleCredentials(
    private val activityProvider: () -> Context?,
    private val serviceId: String,
    private val redirectUri: String,
    /**
     * Trades the callback's one-time code for the identity token, over HTTPS
     * to this app's own server. The last step of the proof-key exchange: the
     * code arrived on a scheme any app can claim, the verifier did not.
     */
    private val exchange: suspend (code: String, verifier: String) -> String?,
) : AppleCredentials {

    override val available: Boolean get() = serviceId.isNotBlank()

    override suspend fun requestCredential(): SocialCredential? {
        if (!available) return null
        val context = activityProvider()
            ?: throw AppleCredentialsException("No active screen to start Apple sign-in from")

        // The verifier never leaves this app except in the exchange below;
        // what goes out with the browser is its hash, riding in `state`
        // because Apple echoes that field back untouched and offers no other
        // place to put it. An app that hijacks the `poster://` callback gets
        // the code and not this, and the code alone redeems nothing.
        val verifier = randomVerifier()
        val state = UUID.randomUUID().toString() + "." + challengeFor(verifier)
        val pending = AppleWebCallback.begin(state)

        // Apple puts this in the token it mints. Only the hash goes out; the
        // value is what the server hashes and compares.
        val nonce = randomVerifier()
        val authorize = Uri.parse("https://appleid.apple.com/auth/authorize").buildUpon()
            .appendQueryParameter("client_id", serviceId)
            .appendQueryParameter("redirect_uri", redirectUri)
            // A hybrid response so Apple returns the identity token directly;
            // with an id_token in the response, form_post is mandatory.
            .appendQueryParameter("response_type", "code id_token")
            .appendQueryParameter("response_mode", "form_post")
            .appendQueryParameter("scope", "name email")
            .appendQueryParameter("state", state)
            .appendQueryParameter("nonce", challengeFor(nonce))
            .build()

        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, authorize).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (noBrowser: ActivityNotFoundException) {
            AppleWebCallback.clear()
            throw AppleCredentialsException("No browser to sign in with Apple", noBrowser)
        }

        val result = pending.await()
        // Apple's own cancel comes back as this error; treat it as a cancel, not
        // a failure — the same as closing the sheet, which resolves to null.
        if (result.error != null && result.error != "user_cancelled_authorize") {
            throw AppleCredentialsException("Apple sign-in failed: ${result.error}")
        }
        val code = result.code ?: return null
        val idToken = exchange(code, verifier)
            ?: throw AppleCredentialsException("Apple sign-in could not be completed")
        return SocialCredential(idToken, nonce)
    }

    private fun randomVerifier(): String =
        ByteArray(32).also(SecureRandom()::nextBytes).let(base64::encodeToString)

    private fun challengeFor(verifier: String): String =
        base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    private companion object {
        val base64: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

/**
 * The bridge between the browser redirect (arriving as a deep link on the
 * Activity) and the coroutine waiting in [AndroidAppleCredentials].
 *
 * One request at a time — the login screen's single busy flag guarantees that.
 * [begin] arms it, [offer] completes it when the matching deep link arrives, and
 * [cancelIfPending] completes it as a cancel when the Activity resumes without
 * one (the person closed the browser).
 */
object AppleWebCallback {
    data class Result(val code: String?, val error: String?)

    private var pending: CompletableDeferred<Result>? = null
    private var expectedState: String? = null

    @Synchronized
    fun begin(state: String): CompletableDeferred<Result> {
        pending?.complete(Result(null, null)) // abandon any stale request
        val deferred = CompletableDeferred<Result>()
        pending = deferred
        expectedState = state
        return deferred
    }

    /** True when [uri] was an Apple callback (consumed here, not an invite link). */
    @Synchronized
    fun offer(uri: Uri): Boolean {
        if (uri.scheme != AppInfo.SCHEME || uri.host != "auth" || uri.path?.startsWith("/apple") != true) {
            return false
        }
        val deferred = pending
        // A state that does not match the one we issued is not our request; still
        // consume the link so it does not fall through to the invite handler.
        if (deferred != null && uri.getQueryParameter("state") == expectedState) {
            deferred.complete(
                Result(
                    code = uri.getQueryParameter("code"),
                    error = uri.getQueryParameter("error"),
                ),
            )
            pending = null
            expectedState = null
        }
        return true
    }

    /** The person came back to the app without finishing — treat it as a cancel. */
    @Synchronized
    fun cancelIfPending() {
        pending?.complete(Result(null, null))
        pending = null
        expectedState = null
    }

    @Synchronized
    fun clear() {
        pending = null
        expectedState = null
    }
}
