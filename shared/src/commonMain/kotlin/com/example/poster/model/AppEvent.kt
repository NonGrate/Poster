package com.example.poster.model

import kotlinx.serialization.Serializable

/**
 * A diagnostic event the app noticed — a slow login, a failed load, a retry.
 *
 * Not a crash (that is [CrashReport]) and not product analytics: this is for
 * spotting when something misbehaved for a particular install or person, so it
 * can be looked at per-device and per-user in the admin panel.
 *
 * What it deliberately does NOT carry: post content, a message, an email, or
 * anything a person typed. [name] comes from a fixed vocabulary ([AppEventName])
 * so an event can never smuggle free text, and [detail] is for small structured
 * facts only — a duration, a count, a status code — never anything personal.
 *
 * [deviceId] is a random per-install id, always present, so anonymous events
 * (before anyone signs in, or a login that never completes) still group. [userId]
 * is filled by the server from the token when there is a session, and is null
 * otherwise — the client never sends it, so it cannot be spoofed.
 */
@Serializable
data class AppEvent(
    val guid: String,
    val deviceId: String,
    val name: String,
    val severity: String = AppEventSeverity.INFO,
    /** Small non-personal facts, e.g. "durationMs=8200". Never anything typed. */
    val detail: String? = null,
    /** "android" or "ios". */
    val platform: String,
    val appVersion: String,
    val occurredAt: String,
    /** Server-set from the token; the client always leaves it null. */
    val userId: String? = null,
    val receivedAt: String? = null,
) {
    companion object {
        const val MAX_FIELD = 500
    }
}

/** How much attention an event deserves. */
object AppEventSeverity {
    /** Something worth knowing happened — a slow but successful operation. */
    const val INFO = "info"

    /** A misbehaviour — a failure, a timeout, a retry that should not be routine. */
    const val WARN = "warn"
}

/**
 * The events the app may report. A fixed set, not free text: it keeps the admin
 * view legible and makes it impossible for an event to carry something personal
 * in its name. Adding one is a line here plus the call site that reports it.
 */
object AppEventName {
    /** A normal, successful sign-in. Info: the routine event, so there is always
     * something on record and the pipeline is visibly working. */
    const val LOGIN = "login"

    /** A login that eventually succeeded but took longer than it should. */
    const val LOGIN_SLOW = "login_slow"

    /** A login attempt that failed or timed out. */
    const val LOGIN_FAILED = "login_failed"

    /** The feed could not be loaded from the server. */
    const val FEED_LOAD_FAILED = "feed_load_failed"

    /** The whole set, so the server can reject anything not on it. */
    val ALL = setOf(LOGIN, LOGIN_SLOW, LOGIN_FAILED, FEED_LOAD_FAILED)
}
