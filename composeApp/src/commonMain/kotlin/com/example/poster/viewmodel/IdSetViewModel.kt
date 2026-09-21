package com.example.poster.viewmodel

import com.example.poster.network.BookmarkApi
import com.example.poster.network.FollowApi
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A set of ids the server keeps for this person, toggled one at a time.
 * Optimistic: the set flips first and is put back if the server refuses.
 */
abstract class IdSetViewModel(dispatchers: DispatcherProvider) : ScopedViewModel(dispatchers) {
    protected abstract suspend fun load(): List<String>
    protected abstract suspend fun add(id: String)
    protected abstract suspend fun remove(id: String)

    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun refresh() {
        scope.launch {
            runCatching { withContext(dispatchers.io) { load() } }.onSuccess { _ids.value = it.toSet() }
        }
    }

    /**
     * Signing out: the next person must not inherit this one's set. Not
     * `clear()` — that is [ScopedViewModel]'s, and it cancels the scope this
     * singleton still needs.
     */
    fun clearIds() { _ids.value = emptySet() }

    fun toggle(id: String) {
        val had = id in _ids.value
        _ids.value = if (had) _ids.value - id else _ids.value + id
        scope.launch {
            runCatching { withContext(dispatchers.io) { if (had) remove(id) else add(id) } }
                .onFailure { _ids.value = if (had) _ids.value + id else _ids.value - id }
        }
    }
}

/** Who this person follows (feature.follows). */
class FollowsViewModel(private val api: FollowApi, dispatchers: DispatcherProvider) : IdSetViewModel(dispatchers) {
    override suspend fun load() = api.following()
    override suspend fun add(id: String) = api.follow(id)
    override suspend fun remove(id: String) = api.unfollow(id)
}

/** Posts this person saved for later (feature.bookmarks). */
class BookmarksViewModel(private val api: BookmarkApi, dispatchers: DispatcherProvider) : IdSetViewModel(dispatchers) {
    override suspend fun load() = api.saved()
    override suspend fun add(id: String) = api.save(id)
    override suspend fun remove(id: String) = api.unsave(id)
}
