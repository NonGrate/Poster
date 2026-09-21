package com.example.poster.model

import com.example.poster.PostDatabase
import java.time.Instant
import java.util.UUID

/** Feedback people send, and the developer's replies. */
// No default database, for the reason given on PostsLocalRepository.
class FeedbackRepository(
    private val database: PostDatabase,
) {
    private val queries = database.feedbackQueries

    /** Stores a new message. It starts open; a reply is a separate act. */
    fun submit(userId: String, message: String) {
        queries.insertFeedback(
            id = UUID.randomUUID().toString(),
            user_id = userId,
            message = message,
            created_at = Instant.now().toString(),
        )
    }

    /** One person's own feedback, newest first — what their feedback screen shows. */
    fun forUser(userId: String): List<Feedback> =
        queries.feedbackForUser(userId) { id, _, message, createdAt, response, respondedAt, status ->
            Feedback(
                id = id,
                message = message,
                createdAt = createdAt,
                response = response,
                respondedAt = respondedAt,
                status = status,
            )
        }.executeAsList()

    /** Everything, for the admin panel: who sent it, when, and whether it is resolved. */
    fun all(): List<FeedbackEntry> =
        queries.allFeedback { id, userId, message, createdAt, response, respondedAt, status, name, surname, email ->
            FeedbackEntry(
                id = id,
                userId = userId,
                senderName = "$name $surname".trim(),
                senderEmail = email,
                message = message,
                createdAt = createdAt,
                response = response,
                respondedAt = respondedAt,
                status = status,
            )
        }.executeAsList()

    /** Records the developer's reply, which is what flips it to answered. */
    fun respond(id: String, response: String) {
        queries.respondToFeedback(
            response = response,
            responded_at = Instant.now().toString(),
            id = id,
        )
    }
}

/** One piece of feedback with its sender, for the admin panel. */
data class FeedbackEntry(
    val id: String,
    val userId: String,
    val senderName: String,
    val senderEmail: String,
    val message: String,
    val createdAt: String,
    val response: String?,
    val respondedAt: String?,
    val status: String,
) {
    val answered: Boolean get() = status == FeedbackStatus.ANSWERED
}
