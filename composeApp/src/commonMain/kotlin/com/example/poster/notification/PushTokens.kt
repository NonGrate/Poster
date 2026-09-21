package com.example.poster.notification

import kotlinx.coroutines.flow.MutableSharedFlow

/** `android` or `ios`: what the server files the device under. */
expect val pushPlatform: String

/**
 * Asks the platform for this device's push token, prompting for permission
 * where the platform wants one (iOS). Null when refused, unavailable (no
 * Firebase configured, simulator without APNs) or slow to arrive.
 */
expect suspend fun requestPushToken(): String?

/** Tokens the platform hands out later (FCM rotation, a late APNs callback); the registrar re-registers each. */
object PushTokenRefresh {
    val tokens = MutableSharedFlow<String>(extraBufferCapacity = 4)
    fun offer(token: String) { tokens.tryEmit(token) }
}
