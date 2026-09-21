package com.example.poster.repository

import com.example.poster.model.AppNotification
import com.example.poster.network.NotificationApi
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * The activity list and the device registration. The token the server was
 * last told about is kept in preferences so signing out can withdraw it (the
 * withdraw route needs no session — by then there is none) and signing in on
 * another account re-points it.
 */
class NotificationRepository(
    private val api: NotificationApi,
    private val preferences: AppPreferences,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun list(): Result<List<AppNotification>> = withContext(dispatchers.io) { runCatching { api.list() } }

    suspend fun unreadCount(): Int = withContext(dispatchers.io) { runCatching { api.unreadCount() }.getOrDefault(0) }

    suspend fun markAllRead(): Result<Unit> = withContext(dispatchers.io) { runCatching { api.markAllRead() } }

    suspend fun registerDevice(token: String, platform: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            api.registerDevice(token, platform)
            preferences.setPushToken(token)
        }
    }

    /** Withdraws whatever token this device last registered; safe to call with none. */
    suspend fun unregisterThisDevice(): Unit = withContext(dispatchers.io) {
        val token = preferences.pushToken() ?: return@withContext
        runCatching { api.unregisterDevice(token) }
        preferences.setPushToken(null)
    }
}
