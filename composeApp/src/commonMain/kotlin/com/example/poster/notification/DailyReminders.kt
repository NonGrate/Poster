package com.example.poster.notification

/**
 * The one notification this app sends: a reminder, at an hour the person chose,
 * to come back and like.
 *
 * Scheduled on the device rather than pushed from the server. A daily reminder
 * is not news — it does not depend on anything the server knows, it has to work
 * with no connection, and routing it through a push service would mean telling
 * that service what time every user likes.
 */
interface DailyReminders {

    /**
     * Asks for the reminder at [hour]:[minute], local time, every day.
     *
     * Returns false when the platform refused. On both platforms a person can
     * decline notifications, and a switch that turns itself on regardless is a
     * switch that lies — the caller is expected to leave the setting off.
     */
    suspend fun enable(hour: Int, minute: Int): Boolean

    /** Cancels it. Safe to call when nothing is scheduled. */
    fun disable()
}
