package com.example.poster.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.poster.MainActivity
import com.example.poster.R
import com.example.poster.util.AppPreferences
import com.example.poster.util.PlatformDataStoreImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Posts the reminder, and puts it back after a reboot.
 *
 * Alarms do not survive a restart — Android drops every one of them — so
 * without the boot half of this, a reminder somebody set weeks ago stops the
 * first time their phone runs out of battery, silently and for good.
 */
class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> rescheduleFromSettings(context)
            else -> notify(context)
        }
    }

    private fun notify(context: Context) {
        // Between scheduling the alarm and it going off, the person may have
        // turned notifications off in system settings. Posting anyway throws on
        // Android 13+.
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AndroidDailyReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission") // guarded by areNotificationsEnabled above
        manager.notify(AndroidDailyReminders.NOTIFICATION_ID, notification)
    }

    /**
     * Reads the stored setting and re-arms, on the same DataStore the app
     * writes — one copy of when the reminder is, rather than a second one kept
     * here that could disagree with the switch the person can see.
     */
    private fun rescheduleFromSettings(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences = AppPreferences(PlatformDataStoreImpl(context.applicationContext))
                val (enabled, minutes) = preferences.storedReminder()
                if (!enabled) return@launch
                val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarms.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    AndroidDailyReminders.nextOccurrence(minutes / 60, minutes % 60),
                    AlarmManager.INTERVAL_DAY,
                    AndroidDailyReminders.pendingIntent(context),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
