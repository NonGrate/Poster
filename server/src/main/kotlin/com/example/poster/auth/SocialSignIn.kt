package com.example.poster.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Somebody a provider vouches for.
 *
 * [subject] is the provider's own id for them, stable across email changes;
 * [email] is what this app links accounts by. Both are only ever produced by a
 * token that has already been verified.
 */
data class SocialAccount(
    val provider: String,
    val subject: String,
    val email: String,
    val name: String?,
    val surname: String?,
)

/** Why a token was refused. Never shown to a caller: they get "no". */
class SocialSignInException(message: String) : Exception(message)

/**
 * Checking that an ID token really came from who it claims.
 *
 * The whole security of signing in this way is in this file: a token is a
 * bearer of somebody's identity, and a server that does not check the signature,
 * the issuer, the audience and the expiry will hand out accounts to anybody who
 * can type JSON.
 *
 * Written provider-shaped rather than Google-shaped. Apple issues the same kind
 * of token, signed the same way, with different issuers and a different key
 * endpoint — so adding it is another entry here, not another design.
 */
interface SocialVerifier {
    val provider: String
    val enabled: Boolean
    /**
     * [nonce] is the raw value the app generated before asking the provider,
     * of which only the SHA-256 was ever sent out. The token carries that
     * hash, so presenting the raw value proves the caller is the app that
     * started this sign-in and not somebody replaying a token they obtained.
     */
    fun verify(idToken: String, nonce: String): SocialAccount
}

/** Base64url of the SHA-256, which is what a provider is given and echoes back. */
internal fun nonceHash(nonce: String): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
        java.security.MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()),
    )

/**
 * Refuses a token whose nonce is missing or is not the one this sign-in asked
 * for.
 *
 * Without it an identity token is a bearer credential for as long as it lives:
 * anything that obtains one minted for this app's client id can present it
 * here and be signed in as its subject. The nonce binds the token to the
 * attempt, and the binding is one-way — the token carries the hash, the caller
 * must produce the value behind it.
 */
internal fun requireNonce(claim: String?, nonce: String) {
    if (nonce.isBlank()) throw SocialSignInException("This sign-in carried no nonce")
    if (claim.isNullOrBlank()) throw SocialSignInException("Token carries no nonce")
    if (!java.security.MessageDigest.isEqual(claim.toByteArray(), nonceHash(nonce).toByteArray())) {
        throw SocialSignInException("Token was not minted for this sign-in")
    }
}

/**
 * Google's ID tokens.
 *
 * [audience] is the OAuth client id(s) this app expects tokens to be minted for —
 * without checking it, a token issued for somebody else's app would be accepted
 * here, which is the classic way these integrations are broken into. Each app
 * platform has its own client id and mints tokens for it — the Android/web token
 * carries the Web client id, a native iOS one carries the iOS client id — so this
 * accepts a comma-separated list and matches any of them.
 *
 * [keyFor] resolves the signing key by its id. In production that fetches
 * Google's published keys; a test hands over a key it generated, which is what
 * lets every rule below be checked without the network.
 */
class GoogleVerifier(
    private val audience: String,
    private val keyFor: (keyId: String) -> RSAPublicKey,
    private val clock: Clock = Clock.systemUTC(),
) : SocialVerifier {

    override val provider = PROVIDER

    private val audiences = audience.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** No client id configured means the app was built without Google sign-in. */
    override val enabled: Boolean get() = audiences.isNotEmpty()

    override fun verify(idToken: String, nonce: String): SocialAccount {
        if (!enabled) throw SocialSignInException("Google sign-in is not configured")

        val decoded = try {
            JWT.decode(idToken)
        } catch (invalid: Exception) {
            throw SocialSignInException("Not a token")
        }
        val keyId = decoded.keyId ?: throw SocialSignInException("Token names no signing key")

        val verified = try {
            JWT.require(Algorithm.RSA256(keyFor(keyId), null))
                // Any of the app's client ids: the token is minted for whichever
                // platform produced it (Web/Android or iOS).
                .withAnyOfAudience(*audiences.toTypedArray())
                // Google has issued both spellings for years and still does.
                .withIssuer(*ISSUERS)
                .build()
                .verify(idToken)
        } catch (refused: JWTVerificationException) {
            throw SocialSignInException("Token rejected: ${refused.message}")
        } catch (noKey: Exception) {
            throw SocialSignInException("Signing key unavailable")
        }

        // java-jwt checks expiry against the system clock; this checks it again
        // against ours, so a test can hold time still and still be honest.
        val expiry = verified.expiresAt?.toInstant()
            ?: throw SocialSignInException("Token never expires")
        if (!expiry.isAfter(clock.instant())) throw SocialSignInException("Token has expired")

        // The token has to belong to this sign-in, not merely to this app.
        requireNonce(verified.getClaim("nonce").asString(), nonce)
        val email = verified.getClaim("email").asString()?.trim()?.lowercase()
        if (email.isNullOrEmpty()) throw SocialSignInException("Token carries no email")

        // An unverified address is somebody's claim about themselves, and this
        // app links accounts by email: accepting it would let anyone who can
        // set an address on a Google account take over the account behind it.
        if (verified.getClaim("email_verified").asBoolean() != true) {
            throw SocialSignInException("Google has not verified that address")
        }

        return SocialAccount(
            provider = PROVIDER,
            subject = verified.subject ?: throw SocialSignInException("Token carries no subject"),
            email = email,
            name = verified.getClaim("given_name").asString()?.trim()?.takeIf(String::isNotEmpty),
            surname = verified.getClaim("family_name").asString()?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    companion object {
        const val PROVIDER = "google"
        private val ISSUERS = arrayOf("accounts.google.com", "https://accounts.google.com")

        /**
         * Google's published signing keys, cached.
         *
         * They rotate, so this cannot be pinned; the cache keeps the lookup off
         * the hot path and the rate limit stops a flood of unknown key ids from
         * turning into a flood of requests to Google.
         */
        fun googleKeys(): (String) -> RSAPublicKey {
            val provider = JwkProviderBuilder(URI("https://www.googleapis.com/oauth2/v3/certs").toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
            return { keyId -> provider.get(keyId).publicKey as RSAPublicKey }
        }
    }
}

/**
 * Apple's ID tokens.
 *
 * The same shape as Google's — RSA-signed, checked for signature, audience,
 * issuer and expiry — with three differences that are not cosmetic:
 *
 * 1. **`email_verified` arrives as a string as often as a boolean.** Apple has
 *    sent `"true"` and `true` from the same endpoint for years. Reading it as
 *    only one of those rejects half of all genuine sign-ins.
 * 2. **The address may be a relay.** Somebody who chose Hide My Email gets an
 *    `@privaterelay.appleid.com` address that is real, deliverable, and not the
 *    address they use anywhere else. [isPrivateRelay] says so, because linking
 *    an account by it is what silently splits somebody in two.
 * 3. **There is no name in the token, ever.** Apple sends the name once, beside
 *    the token, at the first authorization only — never in the token itself,
 *    and never again afterwards. So [SocialAccount.name] is always null here
 *    and the caller has to have kept what it was given the first time.
 *
 * [audience] is the OAuth client id(s) tokens are minted for: the app's bundle
 * id for the native iOS sign-in, and the Service ID for the Android/web flow.
 * Comma-separated, matched as any-of — the same reasoning as Google's, because
 * the two platforms produce tokens addressed differently.
 */
class AppleVerifier(
    private val audience: String,
    private val keyFor: (keyId: String) -> RSAPublicKey,
    private val clock: Clock = Clock.systemUTC(),
) : SocialVerifier {

    override val provider = PROVIDER

    private val audiences = audience.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** No bundle id or Service ID configured means this build was not set up for Apple. */
    override val enabled: Boolean get() = audiences.isNotEmpty()

    override fun verify(idToken: String, nonce: String): SocialAccount {
        if (!enabled) throw SocialSignInException("Apple sign-in is not configured")

        val decoded = try {
            JWT.decode(idToken)
        } catch (invalid: Exception) {
            throw SocialSignInException("Not a token")
        }
        val keyId = decoded.keyId ?: throw SocialSignInException("Token names no signing key")

        val verified = try {
            JWT.require(Algorithm.RSA256(keyFor(keyId), null))
                // Any of the app's audiences: the bundle id for native iOS, the
                // Service ID for the Android/web flow.
                .withAnyOfAudience(*audiences.toTypedArray())
                .withIssuer(ISSUER)
                .build()
                .verify(idToken)
        } catch (refused: JWTVerificationException) {
            throw SocialSignInException("Token rejected: ${refused.message}")
        } catch (noKey: Exception) {
            throw SocialSignInException("Signing key unavailable")
        }

        val expiry = verified.expiresAt?.toInstant()
            ?: throw SocialSignInException("Token never expires")
        if (!expiry.isAfter(clock.instant())) throw SocialSignInException("Token has expired")

        // The token has to belong to this sign-in, not merely to this app.
        requireNonce(verified.getClaim("nonce").asString(), nonce)
        val email = verified.getClaim("email").asString()?.trim()?.lowercase()
        if (email.isNullOrEmpty()) throw SocialSignInException("Token carries no email")

        // Same reasoning as Google's: an address this app links by has to have
        // been proved to belong to whoever is signing in. A relay address is
        // always Apple's own and always verified.
        val emailVerified = verified.getClaim("email_verified").let { claim ->
            claim.asBoolean() ?: claim.asString()?.equals("true", ignoreCase = true)
        }
        if (emailVerified != true) throw SocialSignInException("Apple has not verified that address")

        return SocialAccount(
            provider = PROVIDER,
            subject = verified.subject ?: throw SocialSignInException("Token carries no subject"),
            email = email,
            // Never in the token. See the note above.
            name = null,
            surname = null,
        )
    }

    companion object {
        const val PROVIDER = "apple"
        private const val ISSUER = "https://appleid.apple.com"

        /**
         * Whether this address is one Apple made up to hide somebody's real one.
         *
         * Worth asking before treating an address as the person: two accounts
         * for one human is the failure this exists to prevent.
         */
        fun isPrivateRelay(email: String): Boolean =
            email.trim().lowercase().endsWith("@privaterelay.appleid.com")

        /** Apple's published signing keys, cached and rate limited as Google's are. */
        fun appleKeys(): (String) -> RSAPublicKey {
            val provider = JwkProviderBuilder(URI("https://appleid.apple.com/auth/keys").toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
            return { keyId -> provider.get(keyId).publicKey as RSAPublicKey }
        }
    }
}
