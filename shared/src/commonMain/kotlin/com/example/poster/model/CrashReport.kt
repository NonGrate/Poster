package com.example.poster.model

import kotlinx.serialization.Serializable

/**
 * What is known about a crash, and deliberately nothing else.
 *
 * No account, no screen, no post. A fault report says what broke, not who was
 * holding the phone: this is an app people write the most private things they
 * have into, and a crash is exactly the moment when whatever they were doing is
 * closest to hand. Everything here would be in a stack trace anyway.
 *
 * [occurredAt] is when it happened and [receivedAt] when the server heard about
 * it. They differ because a crashed app cannot send anything — the report waits
 * on the device until the next launch, which may be days later or never.
 */
@Serializable
data class CrashReport(
    val guid: String,
    /** The exception's class, which is what groups one crash with another. */
    val type: String,
    val message: String?,
    val stack: String,
    /** "android" or "ios". */
    val platform: String,
    val osVersion: String,
    val device: String,
    val appVersion: String,
    val occurredAt: String,
    val receivedAt: String? = null,
) {
    companion object {
        /**
         * A stack trace is unbounded and this endpoint takes anything the
         * internet sends it, so both ends cut it to something a page can show
         * and a database can hold. The top of a trace is the part that says
         * what broke.
         */
        const val MAX_STACK = 8_000
        const val MAX_FIELD = 500
    }
}
