package com.example.poster.model

import kotlinx.serialization.Serializable

/**
 * One thing a person told us, and our answer if we have given one.
 *
 * [response] and [respondedAt] are null until the developer replies from the
 * admin panel; [status] says which state this is in without the client having to
 * infer it from a null. There is no push — the reply is simply here the next
 * time the person opens the feedback screen.
 */
@Serializable
data class Feedback(
    val id: String,
    val message: String,
    val createdAt: String,
    val response: String? = null,
    val respondedAt: String? = null,
    val status: String = FeedbackStatus.OPEN,
)

/** Whether a piece of feedback is still waiting or has been answered. */
object FeedbackStatus {
    const val OPEN = "open"
    const val ANSWERED = "answered"
}

/** Sending feedback: just the message. Everything else is the server's to set. */
@Serializable
data class FeedbackRequest(val message: String)
