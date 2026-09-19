package com.example.poster

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.poster.notification.AndroidDailyReminders
import com.example.poster.notification.DailyReminderReceiver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the reminder actually reaches the tray.
 *
 * The receiver cannot be tested from `adb shell am broadcast`: it is
 * exported="false", so a broadcast from outside the app is dropped before it
 * arrives. Calling onReceive here runs it as the app, which is how the alarm
 * calls it.
 */
@RunWith(AndroidJUnit4::class)
class DailyReminderNotificationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun clearTray() {
        // Granted here rather than assumed. Installing the test APK revokes it,
        // so an assumeTrue on "are notifications enabled" reports two green
        // tests that never ran — which is exactly what it did.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        assertTrue(
            manager.areNotificationsEnabled(),
            "notifications are still off after granting — the rest of this test would be vacuous",
        )
        manager.cancelAll()
        // Channels outlive an install, so a leftover one from an earlier run
        // would let "enabling creates the channel" pass without creating it.
        manager.deleteNotificationChannel(AndroidDailyReminders.CHANNEL_ID)
    }

    @After
    fun tidy() {
        manager.cancelAll()
    }

    @Test
    fun theReceiverPostsTheReminder() {
        // The channel is made when the reminder is enabled, not when it fires.
        // Without it, Android 8 and later drop the notification silently.
        runBlockingEnable()

        DailyReminderReceiver().onReceive(context, Intent())

        val posted = awaitReminder()
        assertNotNull(posted, "the reminder did not reach the tray")
        assertEquals(AndroidDailyReminders.CHANNEL_ID, posted.notification.channelId)
        // A small icon that fails to load is how a notification gets dropped
        // without an error anybody sees.
        assertTrue(posted.notification.smallIcon != null, "no small icon")
        assertTrue(
            posted.notification.extras.getCharSequence("android.title").isNullOrBlank().not(),
            "the reminder arrived with no title",
        )
    }

    /** Turning it on creates the channel the notification needs. */
    @Test
    fun enablingCreatesTheChannel() {
        runBlockingEnable()

        assertNotNull(
            manager.getNotificationChannel(AndroidDailyReminders.CHANNEL_ID),
            "the reminder channel was not created",
        )
    }

    /**
     * NotificationManager.notify hands off to another process and returns, so
     * reading activeNotifications on the next line is a race — one this test
     * lost about half the time before it waited.
     */
    private fun awaitReminder(): android.service.notification.StatusBarNotification? {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            manager.activeNotifications
                .firstOrNull { it.id == AndroidDailyReminders.NOTIFICATION_ID }
                ?.let { return it }
            Thread.sleep(50)
        }
        return null
    }

    private fun runBlockingEnable() = kotlinx.coroutines.runBlocking {
        assertTrue(
            AndroidDailyReminders(context).enable(hour = 21, minute = 0),
            "enable() refused despite notifications being allowed",
        )
    }
}
