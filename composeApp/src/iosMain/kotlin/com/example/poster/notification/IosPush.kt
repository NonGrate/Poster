package com.example.poster.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

actual val pushPlatform: String = "ios"

/**
 * The APNs token arrives through the app delegate, which is Swift
 * (`iOSApp.swift` → `offerDeviceToken`). Kotlin asks permission, asks UIKit to
 * register, then waits for that callback.
 */
actual suspend fun requestPushToken(): String? {
    val granted = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { ok, _ -> continuation.resume(ok) }
    }
    if (!granted) return null
    IosPushTokens.latest.value = null
    // `registerForRemoteNotifications` is not in the Kotlin/Native UIKit bindings;
    // the Swift app delegate installs the call at launch.
    val register = IosPushTokens.registerWithSystem ?: return null
    dispatch_async(dispatch_get_main_queue()) { register() }
    return withTimeoutOrNull(10_000) { IosPushTokens.latest.filterNotNull().first() }
}

object IosPushTokens {
    internal val latest = MutableStateFlow<String?>(null)
    internal var registerWithSystem: (() -> Unit)? = null
}

/** Swift installs `UIApplication.shared.registerForRemoteNotifications` here at launch. */
fun setRemoteRegistration(block: () -> Unit) {
    IosPushTokens.registerWithSystem = block
}

/** Called from the Swift app delegate with the hex device token, or null when registration failed. */
fun offerDeviceToken(hex: String?) {
    IosPushTokens.latest.value = hex
    if (hex != null) PushTokenRefresh.offer(hex)
}
