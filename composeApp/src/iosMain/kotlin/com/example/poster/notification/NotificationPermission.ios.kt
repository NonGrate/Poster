package com.example.poster.notification

import androidx.compose.runtime.Composable

/**
 * Reports success without asking: on iOS the permission prompt is part of
 * scheduling, so [IosDailyReminders.enable] is what actually gets the answer.
 */
@Composable
actual fun rememberNotificationPermissionRequest(): ((granted: Boolean) -> Unit) -> Unit =
    { onResult -> onResult(true) }
