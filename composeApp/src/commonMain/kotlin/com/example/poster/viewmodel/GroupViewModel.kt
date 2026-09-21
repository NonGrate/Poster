package com.example.poster.viewmodel

import com.example.poster.model.Group
import com.example.poster.network.GroupApi
import com.example.poster.repository.SessionRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The groups this person is in, for everything that needs their names.
 *
 * A post stores its group as an id, and an id is not something to show
 * anybody — the card has to say "Family". Groups screen fetched its own
 * list, which was fine while it was the only place that needed one; a card in a
 * scrolling feed cannot fetch per row, and two fetches that can disagree is how
 * the same group ends up called two things on two screens.
 *
 * Empty is the honest answer offline, and a card whose group cannot be
 * named says nothing rather than showing a guid.
 */
class GroupViewModel(
    private val groupApi: GroupApi,
    private val session: SessionRepository,
    dispatchers: DispatcherProvider,
) : ScopedViewModel(dispatchers) {

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    init {
        scope.launch {
            // Following the session rather than fetching once: signing in is
            // when there is somebody to have groups, and signing out has
            // to empty this or the next person sees the last one's rooms.
            session.user.collect { user ->
                _groups.value = user?.guid
                    ?.let { runCatching { groupApi.getUserGroups(it) }.getOrNull() }
                    ?: emptyList()
            }
        }
    }

    /** After joining, leaving, creating or closing one. */
    fun refresh(): Job = scope.launch {
        val userId = session.user.value?.guid ?: return@launch
        runCatching { groupApi.getUserGroups(userId) }
            .onSuccess { _groups.value = it }
    }
}

/**
 * What to show for a stored group id, or null when it cannot be named.
 *
 * On the list rather than on the ViewModel so a screen can call it against the
 * state it collected — reading through the ViewModel would not recompose when
 * the groups arrive, and the card would keep showing nothing.
 */
fun List<Group>.nameOf(groupId: String?): String? {
    if (groupId == null) return null
    return firstOrNull { it.id == groupId }?.name
}
