package com.example.poster.notification

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * When the first alarm lands.
 *
 * AlarmManager fires an alarm scheduled in the past straight away, so getting
 * this wrong does not fail quietly — turning the switch on notifies you on the
 * spot, at the wrong time, every time.
 */
class DailyReminderScheduleTest {

    @Test
    fun aTimeLaterTodayIsToday() {
        val now = at(2026, 8, 25, hour = 14, minute = 0)

        val next = AndroidDailyReminders.nextOccurrence(hour = 21, minute = 0, now = now)

        assertEquals(at(2026, 8, 25, hour = 21, minute = 0), next)
    }

    @Test
    fun aTimeAlreadyGoneIsTomorrow() {
        val now = at(2026, 8, 25, hour = 22, minute = 30)

        val next = AndroidDailyReminders.nextOccurrence(hour = 21, minute = 0, now = now)

        assertEquals(at(2026, 8, 26, hour = 21, minute = 0), next)
    }

    /**
     * The boundary, which is the one that bites: at exactly the chosen minute
     * "today" is now, and scheduling now means firing now and then again in
     * twenty-four hours — two notifications for one switch.
     */
    @Test
    fun theExactMinuteCountsAsGone() {
        val now = at(2026, 8, 25, hour = 21, minute = 0)

        val next = AndroidDailyReminders.nextOccurrence(hour = 21, minute = 0, now = now)

        assertEquals(at(2026, 8, 26, hour = 21, minute = 0), next)
    }

    @Test
    fun theResultIsNeverInThePast() {
        val now = at(2026, 8, 25, hour = 12, minute = 0)

        for (hour in 0..23) {
            for (minute in listOf(0, 30, 59)) {
                assertTrue(
                    AndroidDailyReminders.nextOccurrence(hour, minute, now) > now,
                    "$hour:$minute was scheduled in the past",
                )
            }
        }
    }

    /** Seconds are dropped, so the alarm lands on the minute rather than after it. */
    @Test
    fun secondsAreCleared() {
        val now = at(2026, 8, 25, hour = 8, minute = 0, second = 37)

        val next = AndroidDailyReminders.nextOccurrence(hour = 21, minute = 0, now = now)

        val calendar = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(0, calendar.get(Calendar.SECOND))
        assertEquals(0, calendar.get(Calendar.MILLISECOND))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis
}
