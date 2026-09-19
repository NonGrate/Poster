package com.example.poster.viewmodel

import com.example.poster.network.FollowApi
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The ids this person follows (feature.follows). Optimistic: the set flips first, the server is told after. */
class FollowsViewModel(
    private val api: FollowApi,
    dispatchers: DispatcherProvider,
) : ScopedViewModel(dispatchers) {
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following: StateFlow<Set<String>> = _following.asStateFlow()

    fun refresh() {
        scope.launch {
            runCatching { withContext(dispatchers.io) { api.following() } }
                .onSuccess { _following.value = it.toSet() }
        }
    }

    fun toggle(userId: String) {
        val wasFollowing = userId in _following.value
        _following.value = if (wasFollowing) _following.value - userId else _following.value + userId
        scope.launch {
            runCatching { withContext(dispatchers.io) { if (wasFollowing) api.unfollow(userId) else api.follow(userId) } }
                .onFailure { _following.value = if (wasFollowing) _following.value + userId else _following.value - userId }
        }
    }
}
