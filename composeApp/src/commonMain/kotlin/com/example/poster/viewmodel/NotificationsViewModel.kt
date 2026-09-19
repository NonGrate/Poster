package com.example.poster.viewmodel

import com.example.poster.model.AppNotification
import com.example.poster.repository.NotificationRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The activity list and its unread count (feature.pushNotifications). */
class NotificationsViewModel(
    private val notifications: NotificationRepository,
    dispatchers: DispatcherProvider,
) : ScopedViewModel(dispatchers) {
    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Cheap; Home calls it when it appears so the bell can show a dot. */
    suspend fun refreshUnread() { _unread.value = notifications.unreadCount() }

    /** Opening the list is reading it: everything shown is marked read, the dot goes. */
    suspend fun openList() {
        _loading.value = true
        notifications.list().onSuccess { _items.value = it }
        notifications.markAllRead().onSuccess { _unread.value = 0 }
        _loading.value = false
    }

    fun clearForSignOut() { _items.value = emptyList(); _unread.value = 0 }
}
