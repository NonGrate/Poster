package com.example.poster.notification

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat

@Composable
actual fun rememberNotificationPermissionRequest(): ((granted: Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    // The launcher hands its answer to a callback registered before it was
    // launched, so the caller's lambda has to be parked somewhere both can see.
    val pending = remember { arrayOfNulls<((Boolean) -> Unit)>(1) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    return { onResult ->
        when {
            // Before Android 13 there is no permission to ask for; notifications
            // are on unless the person turned them off, which is what this reads.
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                onResult(NotificationManagerCompat.from(context).areNotificationsEnabled())
            // Already answered yes — asking again shows nothing and returns the
            // same answer, but this saves the round trip.
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> onResult(true)
            else -> {
                pending[0] = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
