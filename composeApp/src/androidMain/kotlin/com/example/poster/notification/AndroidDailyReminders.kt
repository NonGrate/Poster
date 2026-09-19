package com.example.poster.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.poster.R
import java.util.Calendar

/**
 * The daily reminder, as an alarm that wakes a receiver which posts a
 * notification.
 *
 * Inexact on purpose. setInexactRepeating lets the system fold this into a
 * wakeup it was going to make anyway, which costs the battery nothing; an exact
 * alarm needs SCHEDULE_EXACT_ALARM, which Android grants grudgingly and means
 * for alarm clocks and calendar events. A reminder to like that arrives a few
 * minutes late is still the reminder.
 */
class AndroidDailyReminders(private val context: Context) : DailyReminders {

    override suspend fun enable(hour: Int, minute: Int): Boolean {
        // Asking is the caller's job — it needs an Activity, and this has a
        // Context. What is checked here is the answer: on Android 13 and later
        // a fresh install has notifications denied until somebody says
        // otherwise, and an alarm whose notification is dropped on arrival
        // leaves the switch on with nothing behind it.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        createChannel()
        alarmManager().setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextOccurrence(hour, minute),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
        return true
    }

    override fun disable() {
        alarmManager().cancel(pendingIntent(context))
    }

    private fun alarmManager() =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                // Default rather than high: this is an appointment somebody
                // made with themselves, not something urgent enough to cut in.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reminder_channel_description) },
        )
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 7301

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * The next time the clock reads [hour]:[minute] — today if that is
         * still ahead, tomorrow otherwise.
         *
         * Without the rollover, turning on a 9pm reminder at 10pm schedules the
         * first alarm an hour in the past, and AlarmManager fires those at
         * once: flipping the switch would notify you on the spot.
         */
        fun nextOccurrence(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.timeInMillis
        }
    }
}
