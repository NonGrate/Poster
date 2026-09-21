package com.example.poster.notification

import androidx.compose.runtime.Composable

/** No scheduler in the browser tab; the switch reports that it could not be turned on. */
object NoDailyReminders : DailyReminders {
    override suspend fun enable(hour: Int, minute: Int): Boolean = false
    override fun disable() = Unit
}

@Composable
actual fun rememberNotificationPermissionRequest(): ((granted: Boolean) -> Unit) -> Unit = { onResult -> onResult(false) }

actual val pushPlatform: String = "web"

actual suspend fun requestPushToken(): String? = null
