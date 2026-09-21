package com.example.poster.auth

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * How fast one account may write, as a bucket rather than a gap.
 *
 * A flat minimum gap was the obvious shape and the wrong one: people write in
 * bursts. Somebody catching up on a thread posts three comments in ten
 * seconds and means all three, and a test posts several in a row because that
 * is the situation it is describing. What is worth stopping is the sustained
 * rate — a script looping — and that is what a bucket distinguishes.
 *
 * [burst] writes are free at any moment; after that one becomes available
 * every [refill]. So a handful in a row is fine and a thousand is not.
 *
 * Keyed on the account, which is the thing being limited and, unlike an
 * address or an IP, cannot be varied: making another account is itself
 * rate-limited.
 *
 * In memory and per process, like [AttemptThrottle]: two instances would each
 * keep their own buckets and the effective rate would double.
 */
class WriteRate(
    private val burst: Int = 10,
    private val refill: Duration = Duration.ofSeconds(3),
    private val clock: Clock = Clock.systemUTC(),
) {
    private class Bucket(var tokens: Double, var lastAt: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /** True when there was a token for this write, which also spends it. */
    @Synchronized
    fun take(userId: String): Boolean {
        val now = clock.millis()
        // Swept here because this is the only place the map grows. A bucket
        // that has had time to refill completely is the same as a new one.
        val full = refill.toMillis() * burst
        buckets.entries.removeIf { now - it.value.lastAt >= full }

        val bucket = buckets.getOrPut(userId) { Bucket(burst.toDouble(), now) }
        val gained = (now - bucket.lastAt).toDouble() / refill.toMillis()
        bucket.tokens = minOf(burst.toDouble(), bucket.tokens + gained)
        bucket.lastAt = now
        if (bucket.tokens < 1.0) return false
        bucket.tokens -= 1.0
        return true
    }
}
