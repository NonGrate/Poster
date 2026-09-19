package com.example.poster

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.poster.auth.AppleVerifier
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What makes an Apple token acceptable.
 *
 * The same rules as Google's, because the same attacks apply, plus the three
 * places Apple behaves differently — a string where a boolean is expected, a
 * relay address instead of a real one, and no name at any point.
 *
 * Keys are generated here, so a token can be minted that is wrong in exactly
 * one way and right in every other.
 */
class AppleSignInTest {

    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = keys.public as RSAPublicKey
    private val privateKey = keys.private as RSAPrivateKey
    private val otherKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    // Anchored to real time: java-jwt checks expiry against the system clock
    // whatever clock is held here, so what matters is the offsets.
    private val now: Instant = Instant.now()
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun verifier(audience: String = BUNDLE_ID) =
        AppleVerifier(audience = audience, keyFor = { publicKey }, clock = clock)

    private fun token(
        audience: String = BUNDLE_ID,
        issuer: String = "https://appleid.apple.com",
        email: String? = "somebody@icloud.com",
        emailVerified: Any? = true,
        subject: String? = "apple-subject-1",
        expiresAt: Instant = now.plusSeconds(600),
        signWith: RSAPrivateKey = privateKey,
        keyId: String? = "key-1",
    ): String = JWT.create()
        .withKeyId(keyId)
        .withAudience(audience)
        .withIssuer(issuer)
        .apply {
            subject?.let { withSubject(it) }
            email?.let { withClaim("email", it) }
            when (emailVerified) {
                is Boolean -> withClaim("email_verified", emailVerified)
                is String -> withClaim("email_verified", emailVerified)
                null -> Unit
                else -> error("email_verified is a boolean or a string")
            }
        }
        .withExpiresAt(Date.from(expiresAt))
        .sign(Algorithm.RSA256(null, signWith))

    @Test
    fun aGoodTokenIdentifiesSomebody() {
        val account = verifier().verify(token())

        assertEquals("somebody@icloud.com", account.email)
        assertEquals("apple-subject-1", account.subject)
        assertEquals("apple", account.provider)
    }

    /**
     * Apple sends the name once, beside the first token, and never in a token.
     * Anything reading it from here would find nothing and quietly write an
     * empty name onto somebody's account.
     */
    @Test
    fun thereIsNoNameInAnAppleToken() {
        val account = verifier().verify(token())

        assertNull(account.name)
        assertNull(account.surname)
    }

    /**
     * Apple sends this claim as a string as often as a boolean. Reading only
     * the boolean refuses genuine sign-ins; reading neither accepts anybody.
     */
    @Test
    fun emailVerifiedIsAcceptedAsAStringToo() {
        val account = verifier().verify(token(emailVerified = "true"))

        assertEquals("somebody@icloud.com", account.email)
    }

    @Test
    fun anUnverifiedAddressIsRefusedWhicheverWayItIsSpelt() {
        assertFailsWith<SocialSignInException> { verifier().verify(token(emailVerified = false)) }
        assertFailsWith<SocialSignInException> { verifier().verify(token(emailVerified = "false")) }
        assertFailsWith<SocialSignInException> { verifier().verify(token(emailVerified = null)) }
    }

    /** The check that stops a token minted for a different app being accepted here. */
    @Test
    fun aTokenForAnotherAppIsRefused() {
        val forSomebodyElse = token(audience = "com.example.someone-else")

        assertFailsWith<SocialSignInException> { verifier().verify(forSomebodyElse) }
    }

    @Test
    fun aTokenFromAnotherIssuerIsRefused() {
        assertFailsWith<SocialSignInException> {
            verifier().verify(token(issuer = "https://appleid.apple.com.evil.example"))
        }
    }

    /** Anybody can mint a token; only Apple can sign one. */
    @Test
    fun aTokenSignedByAnybodyElseIsRefused() {
        val forged = token(signWith = otherKeys.private as RSAPrivateKey)

        assertFailsWith<SocialSignInException> { verifier().verify(forged) }
    }

    @Test
    fun anExpiredTokenIsRefused() {
        assertFailsWith<SocialSignInException> { verifier().verify(token(expiresAt = now.minusSeconds(1))) }
    }

    @Test
    fun aTokenWithoutAnEmailIsRefused() {
        assertFailsWith<SocialSignInException> { verifier().verify(token(email = null)) }
    }

    @Test
    fun aTokenWithoutASubjectIsRefused() {
        assertFailsWith<SocialSignInException> { verifier().verify(token(subject = null)) }
    }

    @Test
    fun somethingThatIsNotATokenIsRefused() {
        assertFailsWith<SocialSignInException> { verifier().verify("not-a-token") }
    }

    /** Without a bundle id this build cannot check the audience, so it refuses everything. */
    @Test
    fun anUnconfiguredVerifierAcceptsNothing() {
        val unconfigured = AppleVerifier(audience = "", keyFor = { publicKey }, clock = clock)

        assertFalse(unconfigured.enabled)
        // The reason matters: "not configured" is a deployment that never
        // offered Apple, and reading it as a bad token sends somebody hunting
        // for a problem in a token that was fine.
        val refusal = assertFailsWith<SocialSignInException> { unconfigured.verify(token()) }
        assertEquals("Apple sign-in is not configured", refusal.message)
    }

    /**
     * The second expiry check, which java-jwt's own does not make redundant.
     *
     * java-jwt reads the system clock and nothing else. This verifier is handed
     * a clock so that anything reasoning about time — a test, or a deployment
     * whose clock has drifted — gets an answer from the clock it provided. With
     * only java-jwt's check, a token this app considers expired is accepted.
     */
    @Test
    fun aTokenExpiredByOurClockIsRefusedEvenWhenTheSystemClockDisagrees() {
        val later = Clock.fixed(now.plusSeconds(1_200), ZoneOffset.UTC)
        val strict = AppleVerifier(audience = BUNDLE_ID, keyFor = { publicKey }, clock = later)

        // Ten minutes of life by the system clock, twenty minutes ago by ours.
        assertFailsWith<SocialSignInException> { strict.verify(token(expiresAt = now.plusSeconds(600))) }
    }

    /**
     * The address somebody gets when they choose Hide My Email. It is real and
     * deliverable, and it is not the address they use anywhere else — so an app
     * that links accounts by email has to know the difference.
     */
    @Test
    fun aRelayAddressIsRecognised() {
        assertTrue(AppleVerifier.isPrivateRelay("abc123@privaterelay.appleid.com"))
        assertTrue(AppleVerifier.isPrivateRelay("ABC123@PrivateRelay.AppleID.com"), "matched case-sensitively")
        assertFalse(AppleVerifier.isPrivateRelay("somebody@icloud.com"))
        assertFalse(
            AppleVerifier.isPrivateRelay("somebody@privaterelay.appleid.com.example.com"),
            "matched the domain anywhere in the address rather than at the end",
        )
    }

    /** A relay address still signs somebody in; it is only linking that must be careful. */
    @Test
    fun someoneHidingTheirEmailCanStillSignIn() {
        val account = verifier().verify(token(email = "abc123@privaterelay.appleid.com"))

        assertEquals("abc123@privaterelay.appleid.com", account.email)
    }

    private companion object {
        const val BUNDLE_ID = "com.example.poster"
    }
}
