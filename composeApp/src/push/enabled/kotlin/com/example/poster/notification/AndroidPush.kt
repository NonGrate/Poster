package com.example.poster.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.poster.R
import com.example.poster.di.AppConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual val pushPlatform: String = "android"

/**
 * Firebase from four public values in `gradle.properties` rather than a
 * `google-services.json`, so a fork drops its own numbers in and nothing else
 * changes. All blank = Firebase is never initialised and [requestPushToken]
 * answers null.
 */
fun initializeFirebaseIfConfigured(context: Context, config: AppConfig) {
    if (config.firebaseAppId.isBlank() || config.firebaseApiKey.isBlank() || config.firebaseProjectId.isBlank()) return
    if (FirebaseApp.getApps(context).isNotEmpty()) return
    FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
            .setProjectId(config.firebaseProjectId)
            .setApplicationId(config.firebaseAppId)
            .setApiKey(config.firebaseApiKey)
            .setGcmSenderId(config.firebaseSenderId.ifBlank { null })
            .build(),
    )
}

actual suspend fun requestPushToken(): String? {
    if (FirebaseApp.getApps(org.koin.mp.KoinPlatform.getKoin().get<Context>()).isEmpty()) return null
    return suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
}

/** Receives pushes and token rotations; the manifest points here. */
class PushMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PushTokenRefresh.offer(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.social_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.social_channel_description) },
        )
    }

    private companion object {
        /** Matches `android.notification.channel_id` the server sets on FCM messages. */
        const val CHANNEL_ID = "social"
    }
}
