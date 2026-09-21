package com.example.poster.diagnostics

import java.time.Clock
import java.time.Duration

/**
 * How much unauthenticated telemetry this server will take in a window.
 *
 * `/crashes` and `/events` are open to the internet by necessity — an app that
 * crashes before anybody signs in has no token to send — which makes them the
 * two routes a stranger can call in a loop. Three things went wrong when they
 * did: the operator's alert channel filled up, the tables prune to their newest
 * rows so genuine crashes were pushed out by the flood, and each request was
 * work the server did for free.
 *
 * Counted globally rather than per caller. The obvious key is the caller's IP
 * and it is not available here: the server sits behind a reverse proxy with no
 * forwarded-header handling, so every request arrives from the proxy and a
 * per-IP bucket would hold the whole internet. The cost of counting globally is
 * that a flood also stops real reports for the rest of the window — which is
 * the same thing the flood did anyway, and this way the alerts stop too.
 *
 * The limit is per process and in memory, like [com.example.poster.auth.AttemptThrottle]:
 * two instances would each keep their own count.
 */
class IntakeLimit(
    private val limit: Int = 120,
    private val window: Duration = Duration.ofMinutes(15),
    private val clock: Clock = Clock.systemUTC(),
) {
    private var windowStartedAt = clock.millis()
    private var taken = 0

    /** True when there was room for this one, which also spends it. */
    @Synchronized
    fun take(): Boolean {
        val now = clock.millis()
        if (Duration.ofMillis(now - windowStartedAt) >= window) {
            windowStartedAt = now
            taken = 0
        }
        if (taken >= limit) return false
        taken++
        return true
    }
}
