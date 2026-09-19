package com.example.poster.preview

import com.example.poster.model.Feedback
import com.example.poster.model.FeedbackStatus
import com.example.poster.network.FeedbackApi

class FakeFeedbackApi : FeedbackApi {
    private val items = mutableListOf(
        Feedback(
            id = "1",
            message = "Could the feed remember where I scrolled to?",
            createdAt = "2026-09-05T10:00:00",
            response = "It does now — thank you for asking.",
            respondedAt = "2026-09-06T09:00:00",
            status = FeedbackStatus.ANSWERED,
        ),
        Feedback(
            id = "2",
            message = "A dark mode that follows the phone would be lovely.",
            createdAt = "2026-09-07T20:30:00",
        ),
    )

    override suspend fun submit(message: String): Boolean {
        items.add(0, Feedback(id = (items.size + 1).toString(), message = message, createdAt = "now"))
        return true
    }

    override suspend fun myFeedback(): List<Feedback> = items
}
