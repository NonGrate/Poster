package com.example.poster.notification

import com.example.poster.config.Features
import com.example.poster.repository.NotificationRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the server's idea of "this device belongs to this account" in step
 * with reality: registers a token after sign-in, re-registers when the
 * platform rotates it, withdraws it on sign-out. Started once, from the main
 * screen; does nothing with the feature off.
 */
class PushRegistrar(
    private val session: SessionRepository,
    private val notifications: NotificationRepository,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private var started: Job? = null

    fun start() {
        if (!Features.PUSH_NOTIFICATIONS || started != null) return
        started = scope.launch {
            launch {
                session.user.map { it?.guid }.distinctUntilChanged().collectLatest { userId ->
                    if (userId == null) {
                        notifications.unregisterThisDevice()
                    } else {
                        requestPushToken()?.let { notifications.registerDevice(it, pushPlatform) }
                    }
                }
            }
            launch {
                PushTokenRefresh.tokens.collect { token ->
                    if (session.user.value != null) notifications.registerDevice(token, pushPlatform)
                }
            }
        }
    }
}
