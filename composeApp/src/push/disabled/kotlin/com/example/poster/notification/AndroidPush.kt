package com.example.poster.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.poster.di.AppConfig

// feature.pushNotifications=false: no Firebase on the classpath. The same
// names exist so PosterApplication and the manifest compile unchanged.

actual val pushPlatform: String = "android"

fun initializeFirebaseIfConfigured(context: Context, config: AppConfig) = Unit

actual suspend fun requestPushToken(): String? = null

/** Never started: nothing sends the messaging intent without Firebase. */
class PushMessagingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
