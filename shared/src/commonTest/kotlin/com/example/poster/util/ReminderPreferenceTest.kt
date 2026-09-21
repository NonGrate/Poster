package com.example.poster.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stored reminder.
 *
 * Worth its own test because two readers disagree if this is wrong: the switch
 * on the settings screen reads the flow, and the boot receiver reads the disk
 * directly, having no composition to read a flow from.
 */
class ReminderPreferenceTest {

    @Test
    fun offUntilSomebodyAsks() = runBlocking {
        val preferences = AppPreferences(Store())

        assertFalse(preferences.reminderEnabled.value)
        assertEquals(AppPreferences.DEFAULT_REMINDER_MINUTES, preferences.reminderMinutes.value)
    }

    @Test
    fun whatWasSetIsWhatIsRead() = runBlocking {
        val store = Store()
        AppPreferences(store).setReminder(enabled = true, minutesSinceMidnight = 7 * 60 + 30)

        assertEquals(true to (7 * 60 + 30), store.settled())
    }

    /**
     * The disk read and the flow have to agree. They are filled by different
     * code — one on demand, one asynchronously at construction — and a reminder
     * the switch shows as 7:30 while the alarm fires at 21:00 is the failure
     * this guards.
     */
    @Test
    fun theDiskAndTheFlowAgree() = runBlocking {
        val store = Store()
        AppPreferences(store).setReminder(enabled = true, minutesSinceMidnight = 6 * 60)
        store.settled()

        val restored = AppPreferences(store)
        val (enabled, minutes) = restored.storedReminder()

        assertTrue(enabled)
        assertEquals(6 * 60, minutes)
    }

    /** A time outside a day is a bug upstream; it must not become a bad alarm. */
    @Test
    fun aTimeOutsideTheDayIsPulledBackIntoIt() = runBlocking {
        val store = Store()
        val preferences = AppPreferences(store)

        preferences.setReminder(enabled = true, minutesSinceMidnight = 5000)
        assertEquals(1439, preferences.reminderMinutes.value)

        preferences.setReminder(enabled = true, minutesSinceMidnight = -60)
        assertEquals(0, preferences.reminderMinutes.value)
    }

    /** Stored nonsense is not an alarm at 03:47 either — it falls back. */
    @Test
    fun anUnreadableStoredTimeFallsBackToTheDefault() = runBlocking {
        val store = Store()
        store.putBoolean("daily_reminder_enabled", true)
        store.putString("daily_reminder_minutes", "not a number")

        assertEquals(
            true to AppPreferences.DEFAULT_REMINDER_MINUTES,
            AppPreferences(store).storedReminder(),
        )
    }

    /**
     * Signing out drops it. The reminder is a standing instruction to interrupt
     * somebody's evening, and whoever signs in next never gave it.
     */
    @Test
    fun signingOutForgetsIt() = runBlocking {
        val store = Store()
        val preferences = AppPreferences(store)
        preferences.setReminder(enabled = true, minutesSinceMidnight = 6 * 60)

        store.settled()
        preferences.clear()
        withTimeout(2000) {
            while (store.getString("daily_reminder_minutes", null) != null) delay(5)
        }

        assertFalse(preferences.reminderEnabled.value)
        assertEquals(false to AppPreferences.DEFAULT_REMINDER_MINUTES, AppPreferences(store).storedReminder())
    }

    /**
     * The writes are fire-and-forget — [AppPreferences.setReminder] launches
     * them and returns — so a test that reads straight afterwards is racing
     * them. This waits for the write rather than hoping it won.
     */
    private suspend fun Store.settled(): Pair<Boolean, Int> = withTimeout(2000) {
        while (getString("daily_reminder_minutes", null) == null) delay(5)
        getBoolean("daily_reminder_enabled", false) to
            getString("daily_reminder_minutes", null)!!.toInt()
    }

    private class Store : PlatformDataStore {
        private val bools = mutableMapOf<String, Boolean>()
        private val strings = mutableMapOf<String, String>()
        override suspend fun putBoolean(key: String, value: Boolean) { bools[key] = value }
        override suspend fun getBoolean(key: String, default: Boolean): Boolean = bools[key] ?: default
        override suspend fun putString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }
        override suspend fun getString(key: String, default: String?): String? = strings[key] ?: default
        override suspend fun clear() { bools.clear(); strings.clear() }
    }
}
