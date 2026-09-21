package com.example.poster.model

import app.cash.sqldelight.db.QueryResult
import kotlinx.datetime.Clock

/**
 * Diagnostic events reported by the apps.
 *
 * Server-only, like [CrashRepository]: no device holds this table, so it stays
 * out of the shared schema and the client migrations, and is created here in raw
 * SQL. The user id is set from the token by the route, never trusted from the
 * body. Capped and pruned — anybody can post, so the table is bounded rather
 * than trusted, newest kept.
 */
// The driver is handed in and has no default: the server shares one connection.
class EventRepository(
    private val driver: app.cash.sqldelight.db.SqlDriver,
    private val keep: Int = KEEP,
) {
    init {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS AppEvent (
                    guid TEXT NOT NULL PRIMARY KEY,
                    device_id TEXT NOT NULL,
                    user_id TEXT,
                    name TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    detail TEXT,
                    platform TEXT NOT NULL,
                    app_version TEXT NOT NULL,
                    occurred_at TEXT NOT NULL,
                    received_at TEXT NOT NULL
                )
            """.trimIndent(),
            parameters = 0,
        )
    }

    /** Stores an event. [userId] comes from the token — null for an anonymous one. */
    fun record(event: AppEvent, userId: String?) {
        driver.execute(
            identifier = null,
            sql = """
                -- The guid comes from the app, so a repeat is a resend rather than a new
                -- report: OR IGNORE keeps the first one instead of letting a
                -- caller overwrite anybody's row by guessing an id.
                INSERT OR IGNORE INTO AppEvent(
                    guid, device_id, user_id, name, severity, detail, platform,
                    app_version, occurred_at, received_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            parameters = 10,
        ) {
            bindString(0, event.guid)
            bindString(1, event.deviceId.take(AppEvent.MAX_FIELD))
            bindString(2, userId?.take(AppEvent.MAX_FIELD))
            bindString(3, event.name.take(AppEvent.MAX_FIELD))
            bindString(4, event.severity.take(AppEvent.MAX_FIELD))
            bindString(5, event.detail?.take(AppEvent.MAX_FIELD))
            bindString(6, event.platform.take(AppEvent.MAX_FIELD))
            bindString(7, event.appVersion.take(AppEvent.MAX_FIELD))
            bindString(8, event.occurredAt.take(AppEvent.MAX_FIELD))
            bindString(9, Clock.System.now().toString())
        }
        prune()
    }

    fun recent(limit: Int = 500): List<AppEvent> = driver.executeQuery(
        identifier = null,
        sql = """
            SELECT guid, device_id, user_id, name, severity, detail, platform,
                   app_version, occurred_at, received_at
            FROM AppEvent ORDER BY received_at DESC LIMIT $limit
        """.trimIndent(),
        parameters = 0,
        mapper = { cursor ->
            val rows = mutableListOf<AppEvent>()
            while (cursor.next().value) {
                rows += AppEvent(
                    guid = cursor.getString(0).orEmpty(),
                    deviceId = cursor.getString(1).orEmpty(),
                    userId = cursor.getString(2),
                    name = cursor.getString(3).orEmpty(),
                    severity = cursor.getString(4).orEmpty(),
                    detail = cursor.getString(5),
                    platform = cursor.getString(6).orEmpty(),
                    appVersion = cursor.getString(7).orEmpty(),
                    occurredAt = cursor.getString(8).orEmpty(),
                    receivedAt = cursor.getString(9),
                )
            }
            QueryResult.Value(rows.toList())
        },
    ).value

    /** How many misbehaviours are on record — the number worth flagging. */
    fun warnCount(): Int = recent().count { it.severity == AppEventSeverity.WARN }

    private fun prune() {
        driver.execute(
            identifier = null,
            sql = """
                DELETE FROM AppEvent WHERE guid NOT IN (
                    SELECT guid FROM AppEvent ORDER BY received_at DESC LIMIT $keep
                )
            """.trimIndent(),
            parameters = 0,
        )
    }

    private companion object {
        const val KEEP = 2_000
    }
}
