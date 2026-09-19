package com.example.poster.notification

import androidx.compose.runtime.Composable

/** No scheduler on the desktop yet; the switch reports that it could not be turned on. */
object NoDailyReminders : DailyReminders {
    override suspend fun enable(hour: Int, minute: Int): Boolean = false
    override fun disable() = Unit
}

@Composable
actual fun rememberNotificationPermissionRequest(): ((granted: Boolean) -> Unit) -> Unit = { onResult -> onResult(false) }

actual val pushPlatform: String = "desktop"

actual suspend fun requestPushToken(): String? = null
