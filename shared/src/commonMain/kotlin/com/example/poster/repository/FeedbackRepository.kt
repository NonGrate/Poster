package com.example.poster.repository

import com.example.poster.model.Feedback
import com.example.poster.network.FeedbackApi
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Feedback, off the main thread and wrapped in [Result] so the view model can
 * fold success and failure the same way it does everywhere else.
 *
 * Nothing is cached: feedback is read fresh each time the screen opens, which is
 * also how a reply left since last time turns up.
 */
class FeedbackRepository(
    private val feedbackApi: FeedbackApi,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun submit(message: String): Result<Boolean> =
        withContext(dispatchers.io) { runCatching { feedbackApi.submit(message) } }

    suspend fun myFeedback(): Result<List<Feedback>> =
        withContext(dispatchers.io) { runCatching { feedbackApi.myFeedback() } }
}
