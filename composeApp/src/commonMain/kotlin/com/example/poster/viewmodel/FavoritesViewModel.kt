package com.example.poster.viewmodel

import com.example.poster.model.Post
import com.example.poster.repository.PostRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.repository.PostRepository.FavoritesSnapshot
import com.example.poster.ui.components.UndoState
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing post favorites.
 *
 * All of it is one [FavoritesUiState]: every transition below replaces the whole
 * value, so the screens never see a half-applied change.
 */
class FavoritesViewModel(
    private val repository: PostRepository,
    private val session: SessionRepository,
    dispatchers: DispatcherProvider
) : ScopedViewModel(dispatchers) {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    /** Separate on purpose — see the note on [FavoritesUiState]. */
    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private var undoJob: Job? = null

    /** The disk-flow collector for the current user; cancelled before re-subscribing. */
    private var favoritesJob: Job? = null

    /** True while a fetch a person asked for is in flight, for the pull indicator. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private companion object {
        /** Long enough to read the sentence and decide, short enough not to linger. */
        const val UNDO_TIMEOUT_MS = 4_000L
    }

    enum class UndoAction {
        REMOVED_FROM_FAVORITES,
        ADDED_TO_FAVORITES
    }

    init {
        scope.launch {
            // Keyed on identity, not the whole User: a profile edit (the
            // name-visibility toggle) emits a same-guid User, and re-running this
            // block flashed Liked empty and stacked another disk collector.
            // Only a real sign-in/out/switch should reload.
            session.user.distinctUntilChangedBy { it?.guid }.collectLatest { user ->
                if (user == null) {
                    favoritesJob?.cancel()
                    clearFavoritesState()
                } else {
                    // From disk first, so Liked has content before the network
                    // answers — the same way the feed does. One collector at a
                    // time: launched on the VM scope (it must outlive this
                    // collectLatest pass), so cancel the previous before starting.
                    favoritesJob?.cancel()
                    favoritesJob = repository.favorites(user.guid)?.let { stored ->
                        scope.launch {
                            stored.collect { fromDisk ->
                                _state.update {
                                    it.copy(
                                        posts = fromDisk,
                                        counts = fromDisk.associate { p -> p.guid to p.likes },
                                    )
                                }
                            }
                        }
                    }
                    // Awaited inside collectLatest so that signing out cancels a
                    // load already in flight. Launching it separately let the
                    // previous user's favorites land after the list was cleared.
                    refreshFavorites()
                }
            }
        }
    }

    /** A pull on the Liked list. */
    fun refresh() {
        scope.launch {
            _isRefreshing.value = true
            try {
                refreshFavorites()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadFavorites() {
        if (session.user.value == null) {
            clearFavoritesState()
            return
        }
        scope.launch { refreshFavorites() }
    }

    private suspend fun refreshFavorites() {
        val userId = session.user.value?.guid
        if (userId != null && repository.favorites(userId) != null) {
            // The list arrives through the flow above; this only fetches.
            repository.refreshFavorites(userId)
                .onFailure { if (_state.value.posts.isEmpty()) fail("Unable to load favorites", it) }
            return
        }
        repository.getFavoritePosts()
            .onSuccess {
                // Whoever asked for this may have signed out while it was away.
                if (session.user.value != null) applySnapshot(it)
            }
            .onFailure { fail("Unable to load favorites", it) }
    }

    fun toggleFavorite(post: Post): Boolean {
        val user = session.user.value
        if (user == null) {
            fail("You must be logged in to manage favorites")
            return false
        }

        val currentlyFavorite = _state.value.isFavorite(post.guid)
        // What to go back to if the write fails — one value, not three fields.
        val previous = _state.value
        _state.update { it.withFavorite(post, added = !currentlyFavorite) }
        val action =
            if (currentlyFavorite) UndoAction.REMOVED_FROM_FAVORITES else UndoAction.ADDED_TO_FAVORITES

        scope.launch {
            val result = if (currentlyFavorite) {
                repository.removeFavorite(user.guid, post.guid)
            } else {
                repository.addFavorite(user.guid, post.guid)
            }

            result.onSuccess { updated ->
                applySnapshot(updated)
                showUndoOption(post = post, action = action)
            }.onFailure { throwable ->
                _state.value = previous.copy(error = UiError("Unable to update favorite", throwable))
            }
        }

        return !currentlyFavorite
    }

    private fun showUndoOption(post: Post, action: UndoAction) {
        undoJob?.cancel()
        _undoState.value = UndoState(post, action)
        undoJob = scope.launch {
            delay(UNDO_TIMEOUT_MS)
            dismissUndo()
        }
    }

    fun performUndo() {
        val undo = _undoState.value ?: return
        val user = session.user.value ?: return
        val previous = _state.value

        scope.launch {
            val result = when (undo.action) {
                UndoAction.REMOVED_FROM_FAVORITES -> repository.addFavorite(user.guid, undo.post.guid)
                UndoAction.ADDED_TO_FAVORITES -> repository.removeFavorite(user.guid, undo.post.guid)
            }

            result.onSuccess { applySnapshot(it) }
                .onFailure { throwable ->
                    _state.value =
                        previous.copy(error = UiError("Unable to undo favorite action", throwable))
                }
        }

        dismissUndo()
    }

    fun dismissUndo() {
        undoJob?.cancel()
        undoJob = null
        _undoState.value = null
    }

    fun acknowledgeError() {
        _state.update { it.copy(error = null) }
    }

    private fun fail(message: String, cause: Throwable? = null) {
        _state.update { it.copy(error = UiError(message, cause)) }
    }

    private fun applySnapshot(snapshot: FavoritesSnapshot) {
        _state.update {
            it.copy(
                posts = snapshot.favoritePosts,
                counts = snapshot.favoriteCounts,
            )
        }
    }

    private fun clearFavoritesState() {
        dismissUndo()
        _state.value = FavoritesUiState()
    }
}
