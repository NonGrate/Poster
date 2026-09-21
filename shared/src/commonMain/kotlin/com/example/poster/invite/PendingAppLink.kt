package com.example.poster.invite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A link that arrived and has not been acted on yet.
 *
 * Global state for the same reason [InviteLink] is: the platform entry points
 * that receive a URL — an Android Intent, an iOS callback — run outside the
 * composition with no graph to write into.
 */
object PendingAppLink {
    private val _link = MutableStateFlow<AppLink?>(null)
    val link: StateFlow<AppLink?> = _link.asStateFlow()

    fun offer(url: String?) {
        AppLink.parse(url)?.let { _link.value = it }
    }

    /** Spent once acted on, so returning to the screen does not repeat it. */
    fun consume() {
        _link.value = null
    }
}
