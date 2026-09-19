package com.example.poster.network

import com.example.poster.model.Feedback

/**
 * Sending feedback and reading back what has been said, replies included.
 *
 * There is no push: [myFeedback] is how a reply reaches somebody — they see it
 * the next time they open the screen.
 */
interface FeedbackApi {
    /** Sends a message. True when the server took it. */
    suspend fun submit(message: String): Boolean

    /** The caller's own feedback, newest first, with any reply. */
    suspend fun myFeedback(): List<Feedback>
}
