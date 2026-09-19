package com.example.poster.domain.validation

/**
 * How long a piece of feedback may be, and what counts as one worth sending.
 *
 * Here, in shared code, so the compose field and the server agree — a message
 * the form accepts and the server rejects is one somebody types out and loses.
 *
 * Trimmed before checking: trailing whitespace is not content, and a field of
 * only spaces is empty. Long enough to describe an idea, capped so it cannot be
 * a payload.
 */
object FeedbackRules {
    const val MESSAGE_LIMIT = 1000

    fun messageValid(message: String): Boolean = message.trim().length in 1..MESSAGE_LIMIT

    fun messageTooLong(message: String): Boolean = message.trim().length > MESSAGE_LIMIT
}
