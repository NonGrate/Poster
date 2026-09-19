package com.example.poster.notification

import androidx.compose.runtime.Composable

/**
 * Asks the platform for permission to post notifications, then reports back.
 *
 * Separate from [DailyReminders] because asking needs an Activity on Android
 * and a scheduler holds only a Context. The two platforms also ask at different
 * moments: Android has a permission dialog of its own that must be launched
 * from the UI, while iOS folds the request into scheduling — so its
 * implementation reports success without asking anything and lets
 * [DailyReminders.enable] get the real answer.
 */
@Composable
expect fun rememberNotificationPermissionRequest(): ((granted: Boolean) -> Unit) -> Unit
