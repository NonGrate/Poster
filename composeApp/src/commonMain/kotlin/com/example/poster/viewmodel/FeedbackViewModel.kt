package com.example.poster.viewmodel

import com.example.poster.model.Feedback
import com.example.poster.repository.FeedbackRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The feedback screen's state: what has been sent (with any reply) and whether a
 * load or a send is in flight.
 *
 * A send re-reads the list rather than appending locally — the server stamps the
 * time and id, and re-reading is also how a reply left since last time appears.
 */
class FeedbackViewModel(
    private val feedback: FeedbackRepository,
    dispatchers: DispatcherProvider,
) : ScopedViewModel(dispatchers) {

    private val _items = MutableStateFlow<List<Feedback>>(emptyList())
    val items: StateFlow<List<Feedback>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error.asStateFlow()

    /** Loads the list. Called when the screen opens. */
    suspend fun load() {
        _loading.value = true
        feedback.myFeedback().fold(
            onSuccess = { _items.value = it },
            onFailure = { _error.value = UiError("Unable to load feedback", it) },
        )
        _loading.value = false
    }

    /** Sends a message, then reloads so the new item (and any reply) shows. */
    suspend fun submit(message: String): Boolean =
        feedback.submit(message).fold(
            onSuccess = { sent ->
                if (sent) load()
                sent
            },
            onFailure = { _error.value = UiError("Unable to send feedback", it); false },
        )

    fun acknowledgeError() { _error.value = null }
}
