package com.example.poster.auth

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * How many times a password may be guessed for one address before the guessing
 * has to stop.
 *
 * Keyed on the address rather than on who is asking. The obvious key is the
 * caller's IP, and it is the wrong one here: this app sits behind a reverse
 * proxy with no forwarded-header handling, so every request arrives from the
 * proxy and a per-IP limit would put the whole internet in one bucket — the
 * first attacker would lock out every real person at once. The address needs no
 * such trust, and it is what an attacker is actually working against.
 *
 * Only failures count, so somebody who signs in normally is never affected by
 * their own successful history, and a success clears the record.
 *
 * The cost of keying on the address is that somebody can lock a person out of
 * signing in by guessing at their address on purpose. That is a real trade and
 * it is the reason the window is short: fifteen minutes of nuisance, against a
 * password that would otherwise be guessable at any rate the network allows.
 * Limiting by real client IP as well would need XForwardedHeaders installed
 * first, and that is worth doing.
 *
 * In memory, so it is per process: two instances would each keep their own
 * count and the effective limit would double. That is the right shape for one
 * container and the wrong one for several, which is worth remembering before
 * this is ever scaled out.
 */
class AttemptThrottle(
    private val limit: Int = 5,
    private val window: Duration = Duration.ofMinutes(15),
    private val clock: Clock = Clock.systemUTC(),
) {
    private class Record(var failures: Int, var firstAt: Long)

    private val records = ConcurrentHashMap<String, Record>()

    /** Whether this address has spent its attempts, and how long until it has not. */
    fun retryAfter(key: String): Duration? {
        val record = records[normalise(key)] ?: return null
        synchronized(record) {
            val elapsed = Duration.ofMillis(clock.millis() - record.firstAt)
            if (elapsed >= window) return null
            if (record.failures < limit) return null
            return window.minus(elapsed)
        }
    }

    fun recordFailure(key: String) {
        val id = normalise(key)
        // Swept here rather than on a timer: the map only grows when somebody
        // is failing, and this is the moment it grows.
        evictExpired()
        val record = records.computeIfAbsent(id) { Record(0, clock.millis()) }
        synchronized(record) {
            if (Duration.ofMillis(clock.millis() - record.firstAt) >= window) {
                record.failures = 0
                record.firstAt = clock.millis()
            }
            record.failures++
        }
    }

    /** Signing in worked. Whatever came before it is no longer interesting. */
    fun clear(key: String) {
        records.remove(normalise(key))
    }

    private fun evictExpired() {
        val now = clock.millis()
        records.entries.removeIf { (_, record) ->
            Duration.ofMillis(now - record.firstAt) >= window
        }
    }

    // The same address in a different case is the same address, and the same
    // one with a stray space is a typo rather than a fresh allowance.
    private fun normalise(key: String) = key.trim().lowercase()
}
