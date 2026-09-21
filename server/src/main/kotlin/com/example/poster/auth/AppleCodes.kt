package com.example.poster.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * One-time codes standing in for an Apple identity token.
 *
 * Apple's answer to the Android browser round-trip arrives here as a POST and
 * has to reach the app, which means a redirect the app can receive. A custom
 * scheme is the only thing an unverified app can be sure of receiving, and it
 * is first-come on Android: any other installed app may declare `poster://`
 * and take the callback. Putting the identity token in that URL therefore
 * handed it to whoever asked — and an identity token is a sign-in.
 *
 * So the token stays here and the URL carries a code. The app proves the
 * request was its own by presenting the verifier whose SHA-256 it put in the
 * `state` before the browser opened; an app that merely received the redirect
 * has the code and not the verifier, and the code buys it nothing. This is
 * RFC 8252's proof key for code exchange, with the challenge riding in `state`
 * because Apple echoes that field untouched and offers no other.
 *
 * In memory and per process, like [AttemptThrottle]: the window is the seconds
 * between the browser closing and the app asking, a restart in that gap costs
 * one sign-in attempt, and two instances would each hold their own codes —
 * which is worth knowing before this is scaled out behind a load balancer.
 */
class AppleCodes(
    private val lifetime: Duration = Duration.ofMinutes(5),
    private val clock: Clock = Clock.systemUTC(),
) {
    private class Held(val idToken: String, val challenge: String, val expiresAt: Long)

    private val held = ConcurrentHashMap<String, Held>()
    private val random = SecureRandom()

    /** Keeps [idToken] against [challenge] and returns the code that stands for it. */
    fun mint(idToken: String, challenge: String): String {
        sweep()
        val code = randomToken()
        held[code] = Held(idToken, challenge, clock.millis() + lifetime.toMillis())
        return code
    }

    /**
     * The token, if [verifier] is the one whose hash was registered. Removed
     * whether or not the verifier matched: a code is one attempt, so a wrong
     * guess cannot be followed by a right one.
     */
    fun redeem(code: String, verifier: String): String? {
        val entry = held.remove(code) ?: return null
        if (clock.millis() > entry.expiresAt) return null
        if (!MessageDigest.isEqual(challengeFor(verifier).toByteArray(), entry.challenge.toByteArray())) {
            return null
        }
        return entry.idToken
    }

    private fun sweep() {
        val now = clock.millis()
        held.entries.removeIf { it.value.expiresAt < now }
    }

    private fun randomToken(): String =
        ByteArray(32).also(random::nextBytes).let(base64::encodeToString)

    companion object {
        private val base64: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        /**
         * The value an app puts in `state`, derived from the verifier it keeps.
         * Base64url of the SHA-256, the same shape OAuth's S256 method uses.
         */
        fun challengeFor(verifier: String): String =
            base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

        /**
         * The challenge an app appended to its `state`, or null.
         *
         * `state` is the app's to choose and Apple returns it untouched, so the
         * challenge travels as a suffix after a dot. A state without one is a
         * client from before this existed: the callback answers with an error
         * rather than falling back to sending the token, which would leave the
         * hole open for anybody who asked for it.
         */
        fun challengeIn(state: String): String? =
            state.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it != state }
    }
}
