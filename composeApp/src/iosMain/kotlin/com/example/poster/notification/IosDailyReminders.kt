package com.example.poster.notification

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.reminder_body
import poster.composeapp.generated.resources.reminder_title
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * The daily reminder as a repeating calendar notification.
 *
 * iOS schedules this itself once asked, so it survives reboots, force quits and
 * the app never being opened again — there is no alarm to re-arm and no
 * background work to keep alive.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDailyReminders : DailyReminders {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun enable(hour: Int, minute: Int): Boolean {
        // Asking twice is harmless: after the first answer iOS stops showing the
        // prompt and replies with what the person already chose.
        if (!requestAuthorization()) return false

        // Replace rather than add. Without this, changing the time leaves the
        // old request in place and the reminder arrives twice a day.
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))

        // Read here rather than taken as a constructor argument: enable() is
        // suspend, which is exactly what a Compose resource lookup outside
        // composition needs, and Koin builds this long before there is a
        // composition to read from.
        val content = UNMutableNotificationContent().apply {
            setTitle(getString(Res.string.reminder_title))
            setBody(getString(Res.string.reminder_body))
        }
        // Hour and minute only, so iOS reads it as "every day at this time"
        // in whatever timezone the phone is in — which is what somebody who
        // travels expects of an evening reminder.
        val components = NSDateComponents().apply {
            setHour(hour.toLong())
            setMinute(minute.toLong())
        }
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                identifier = IDENTIFIER,
                content = content,
                trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                    dateComponents = components,
                    repeats = true,
                ),
            ),
            withCompletionHandler = null,
        )
        return true
    }

    override fun disable() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
    }

    private suspend fun requestAuthorization(): Boolean = suspendCancellableCoroutine { continuation ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            continuation.resume(granted)
        }
    }

    private companion object {
        const val IDENTIFIER = "daily_reminder"
    }
}
