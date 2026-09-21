package com.example.poster

import com.example.poster.auth.SocialVerifier
import com.example.poster.auth.nonceHash
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.poster.auth.GoogleVerifier
import com.example.poster.auth.SocialSignInException
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What makes a Google token acceptable.
 *
 * All of it is here rather than at the route, because this is the security of
 * signing in this way: an ID token is a bearer of somebody's identity, and each
 * of the checks below is one an attacker would otherwise walk through. The keys
 * are generated in the test, so a token can be minted that is wrong in exactly
 * one way and right in every other — which is the only way to know a check does
 * anything.
 */
class GoogleSignInTest {

    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keys.public as RSAPublicKey
    private val privateKey = keys.private as RSAPrivateKey
    private val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    // Anchored to real time rather than a fixed date: java-jwt checks expiry
    // against the system clock whatever clock we hold, so a token minted around
    // a date in the past is expired before any of the other rules are reached.
    // What matters here is the offsets, not the absolute instant.
    private val now: Instant = Instant.now()
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun verifier(audience: String = CLIENT_ID) =
        GoogleVerifier(audience = audience, keyFor = { publicKey }, clock = clock)

    private fun token(
        audience: String = CLIENT_ID,
        issuer: String = "https://accounts.google.com",
        email: String? = "somebody@gmail.com",
        emailVerified: Boolean? = true,
        subject: String? = "google-subject-1",
        expiresAt: Instant = now.plusSeconds(600),
        signWith: RSAPrivateKey = privateKey,
        keyId: String? = "key-1",
        // The claim carries the hash; the verifier is handed the value behind
        // it, which is what a caller replaying a token would not have.
        nonce: String? = nonceHash(NONCE),
    ): String = JWT.create()
        .withKeyId(keyId)
        .withAudience(audience)
        .withIssuer(issuer)
        .apply {
            subject?.let { withSubject(it) }
            nonce?.let { withClaim("nonce", it) }
            email?.let { withClaim("email", it) }
            emailVerified?.let { withClaim("email_verified", it) }
            withClaim("given_name", "Somebody")
            withClaim("family_name", "Else")
        }
        .withExpiresAt(Date.from(expiresAt))
        .sign(Algorithm.RSA256(null, signWith))

    @Test
    fun aGoodTokenIdentifiesSomebody() {
        val account = verifier().check(token())

        assertEquals("somebody@gmail.com", account.email)
        assertEquals("google-subject-1", account.subject)
        assertEquals("Somebody", account.name)
        assertEquals("Else", account.surname)
    }

    /** The check that stops a token minted for a different app being accepted here. */
    @Test
    fun aTokenForAnotherAppIsRefused() {
        val forSomebodyElse = token(audience = "some-other-app.apps.googleusercontent.com")

        assertFailsWith<SocialSignInException> { verifier().check(forSomebodyElse) }
    }

    /**
     * With more than one client id configured — the Web one and the iOS one — a
     * token minted for either is accepted, and a token for a third app still is
     * not. This is what lets a native iOS sign-in, whose token carries the iOS
     * client id, verify against the same route Android uses.
     */
    @Test
    fun aTokenForAnyConfiguredClientIsAccepted() {
        val ios = "poster-ios.apps.googleusercontent.com"
        val multi = verifier(audience = "$CLIENT_ID, $ios")

        assertEquals("google-subject-1", multi.check(token(audience = CLIENT_ID)).subject)
        assertEquals("google-subject-1", multi.check(token(audience = ios)).subject)
        assertFailsWith<SocialSignInException> {
            multi.check(token(audience = "some-other-app.apps.googleusercontent.com"))
        }
    }

    /** And the one that stops anybody who can generate a key pair signing their own. */
    @Test
    fun aTokenSignedBySomebodyElseIsRefused() {
        val forged = token(signWith = otherKeys.private as RSAPrivateKey)

        assertFailsWith<SocialSignInException> { verifier().check(forged) }
    }

    @Test
    fun anExpiredTokenIsRefused() {
        val stale = token(expiresAt = now.minusSeconds(1))

        assertFailsWith<SocialSignInException> { verifier().check(stale) }
    }

    @Test
    fun aTokenFromTheWrongIssuerIsRefused() {
        val elsewhere = token(issuer = "https://accounts.example.com")

        assertFailsWith<SocialSignInException> { verifier().check(elsewhere) }
    }

    /**
     * Accounts are linked by email, so an address Google has not checked would
     * be a way to walk into somebody else's account by claiming their address.
     */
    @Test
    fun anUnverifiedAddressIsRefused() {
        assertFailsWith<SocialSignInException> { verifier().check(token(emailVerified = false)) }
        assertFailsWith<SocialSignInException> { verifier().check(token(emailVerified = null)) }
    }

    @Test
    fun aTokenWithNoEmailIsRefused() {
        assertFailsWith<SocialSignInException> { verifier().check(token(email = null)) }
    }

    @Test
    fun nonsenseIsRefusedRatherThanCrashing() {
        assertFailsWith<SocialSignInException> { verifier().check("not-a-token") }
        assertFailsWith<SocialSignInException> { verifier().check("") }
    }

    /** Addresses differ in case; accounts do not. */
    @Test
    fun theAddressIsNormalised() {
        val account = verifier().check(token(email = "  SomeBody@GMail.com "))

        assertEquals("somebody@gmail.com", account.email)
    }

    @Test
    fun withoutAClientIdNothingIsAccepted() {
        val unconfigured = GoogleVerifier(audience = "", keyFor = { publicKey }, clock = clock)

        assertTrue(!unconfigured.enabled)
        assertFailsWith<SocialSignInException> { unconfigured.check(token()) }
    }

    private companion object {
        const val CLIENT_ID = "poster.apps.googleusercontent.com"
    }

    /**
     * The replay case, which is the whole reason the nonce is there.
     *
     * A token is a bearer credential otherwise: anything that obtains one
     * minted for this app's client id could present it and be signed in as
     * its subject. The token carries only the hash, so presenting it without
     * the value behind it proves nothing and is refused.
     */
    @Test
    fun aTokenPresentedWithoutTheValueBehindItsNonceIsRefused() {
        val stolen = token()

        assertFailsWith<SocialSignInException> {
            verifier().check(stolen, nonce = "a-guess")
        }
        assertFailsWith<SocialSignInException> {
            verifier().check(stolen, nonce = "")
        }
        // A token minted without a nonce at all is refused too, so a caller
        // cannot simply ask for the older shape.
        assertFailsWith<SocialSignInException> {
            verifier().check(token(nonce = null))
        }
        // And the honest case still works.
        assertEquals("google-subject-1", verifier().check(stolen).subject)
    }

    /** The value behind the nonce in the tokens these tests mint. */
    private val NONCE = "a-value-only-this-sign-in-knows"

    /** Verifying with the nonce this suite's tokens were minted for. */
    private fun SocialVerifier.check(idToken: String, nonce: String = NONCE) = verify(idToken, nonce)
}
