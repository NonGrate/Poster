package com.example.poster

import com.example.poster.auth.AttemptThrottle
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * How many guesses one address gets.
 *
 * The window matters as much as the count: a limit that never expires is a
 * permanent lockout somebody else can trigger, and one that resets on every
 * attempt never triggers at all.
 */
class AttemptThrottleTest {

    private class MovableClock(private var now: Instant = Instant.parse("2026-08-25T12:00:00Z")) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
        fun advance(by: Duration) { now = now.plus(by) }
    }

    @Test
    fun anAddressWithNoHistoryIsNotThrottled() {
        assertNull(throttle().retryAfter("someone@example.com"))
    }

    @Test
    fun failuresUpToTheLimitAreStillAllowed() {
        val throttle = throttle(limit = 3)

        repeat(2) { throttle.recordFailure("someone@example.com") }

        assertNull(throttle.retryAfter("someone@example.com"), "throttled before the limit was reached")
    }

    @Test
    fun theLimitStopsFurtherAttempts() {
        val throttle = throttle(limit = 3)

        repeat(3) { throttle.recordFailure("someone@example.com") }

        assertNotNull(throttle.retryAfter("someone@example.com"), "the limit did not stop anything")
    }

    /** Guessing at one address must not lock anybody else out. */
    @Test
    fun oneAddressBeingThrottledDoesNotAffectAnother() {
        val throttle = throttle(limit = 3)

        repeat(5) { throttle.recordFailure("victim@example.com") }

        assertNotNull(throttle.retryAfter("victim@example.com"))
        assertNull(throttle.retryAfter("bystander@example.com"))
    }

    @Test
    fun theWindowExpires() {
        val clock = MovableClock()
        val throttle = throttle(limit = 3, clock = clock)
        repeat(3) { throttle.recordFailure("someone@example.com") }
        assertNotNull(throttle.retryAfter("someone@example.com"))

        clock.advance(Duration.ofMinutes(16))

        assertNull(throttle.retryAfter("someone@example.com"), "the lockout outlived its window")
    }

    /**
     * The countdown starts at the first failure and does not restart on the
     * next one. Otherwise an attacker who keeps trying keeps the window open
     * for ever and never becomes able to try again — which sounds severe until
     * you notice it locks the real person out permanently too.
     */
    @Test
    fun theWindowIsNotExtendedByFurtherFailures() {
        val clock = MovableClock()
        val throttle = throttle(limit = 3, clock = clock)
        repeat(3) { throttle.recordFailure("someone@example.com") }

        clock.advance(Duration.ofMinutes(14))
        throttle.recordFailure("someone@example.com")
        clock.advance(Duration.ofMinutes(2))

        assertNull(throttle.retryAfter("someone@example.com"), "a late failure extended the lockout")
    }

    /** Signing in worked, so whatever came before it is no longer interesting. */
    @Test
    fun successClearsTheRecord() {
        val throttle = throttle(limit = 3)
        repeat(3) { throttle.recordFailure("someone@example.com") }

        throttle.clear("someone@example.com")

        assertNull(throttle.retryAfter("someone@example.com"))
    }

    @Test
    fun theSameAddressInAnotherCaseIsTheSameAddress() {
        val throttle = throttle(limit = 3)

        repeat(3) { throttle.recordFailure("Someone@Example.com") }

        assertNotNull(
            throttle.retryAfter("  someone@example.com  "),
            "case or a stray space bought a fresh allowance",
        )
    }

    @Test
    fun theRemainingWaitShrinksAsTimePasses() {
        val clock = MovableClock()
        val throttle = throttle(limit = 1, clock = clock)
        throttle.recordFailure("someone@example.com")
        val first = throttle.retryAfter("someone@example.com")!!

        clock.advance(Duration.ofMinutes(5))
        val later = throttle.retryAfter("someone@example.com")!!

        assertEquals(5, first.minus(later).toMinutes(), "the wait did not count down")
    }

    private fun throttle(limit: Int = 5, clock: Clock = MovableClock()) =
        AttemptThrottle(limit = limit, window = Duration.ofMinutes(15), clock = clock)
}
