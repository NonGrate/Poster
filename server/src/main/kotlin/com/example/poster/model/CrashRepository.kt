package com.example.poster.model

import app.cash.sqldelight.db.QueryResult
import com.example.poster.db.DatabaseDriverFactory
import kotlinx.datetime.Clock

/**
 * Crashes reported by the apps.
 *
 * Written through raw SQL rather than SQLDelight because the table is the
 * server's alone — no device has it, so it is not in the shared schema, and a
 * generated query for it would have to be.
 */
class CrashRepository(
    private val driver: app.cash.sqldelight.db.SqlDriver = DatabaseDriverFactory().createDriver(),
    private val keep: Int = KEEP,
) {
    init {
        // Server-only, like RefreshToken: no device holds this, so it stays out
        // of the shared schema and out of the client migrations. Created here
        // rather than in the driver factory so that anything holding a driver
        // — a test, for one — gets a usable repository.
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS CrashReport (
                    guid TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    message TEXT,
                    stack TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    os_version TEXT NOT NULL,
                    device TEXT NOT NULL,
                    app_version TEXT NOT NULL,
                    occurred_at TEXT NOT NULL,
                    received_at TEXT NOT NULL
                )
            """.trimIndent(),
            parameters = 0,
        )
    }

    fun record(report: CrashReport) {
        driver.execute(
            identifier = null,
            sql = """
                INSERT OR REPLACE INTO CrashReport(
                    guid, type, message, stack, platform, os_version, device,
                    app_version, occurred_at, received_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            parameters = 10,
        ) {
            bindString(0, report.guid)
            bindString(1, report.type.take(CrashReport.MAX_FIELD))
            bindString(2, report.message?.take(CrashReport.MAX_FIELD))
            bindString(3, report.stack.take(CrashReport.MAX_STACK))
            bindString(4, report.platform.take(CrashReport.MAX_FIELD))
            bindString(5, report.osVersion.take(CrashReport.MAX_FIELD))
            bindString(6, report.device.take(CrashReport.MAX_FIELD))
            bindString(7, report.appVersion.take(CrashReport.MAX_FIELD))
            bindString(8, report.occurredAt.take(CrashReport.MAX_FIELD))
            bindString(9, Clock.System.now().toString())
        }
        prune()
    }

    fun recent(limit: Int = 200): List<CrashReport> = driver.executeQuery(
        identifier = null,
        sql = """
            SELECT guid, type, message, stack, platform, os_version, device,
                   app_version, occurred_at, received_at
            FROM CrashReport ORDER BY received_at DESC LIMIT $limit
        """.trimIndent(),
        parameters = 0,
        mapper = { cursor ->
            val rows = mutableListOf<CrashReport>()
            while (cursor.next().value) {
                rows += CrashReport(
                    guid = cursor.getString(0).orEmpty(),
                    type = cursor.getString(1).orEmpty(),
                    message = cursor.getString(2),
                    stack = cursor.getString(3).orEmpty(),
                    platform = cursor.getString(4).orEmpty(),
                    osVersion = cursor.getString(5).orEmpty(),
                    device = cursor.getString(6).orEmpty(),
                    appVersion = cursor.getString(7).orEmpty(),
                    occurredAt = cursor.getString(8).orEmpty(),
                    receivedAt = cursor.getString(9),
                )
            }
            QueryResult.Value(rows.toList())
        },
    ).value

    /**
     * Anybody can post here — a crashing app has nobody signed in — so the
     * table is capped rather than trusted. The newest are the ones worth
     * having.
     */
    private fun prune() {
        driver.execute(
            identifier = null,
            sql = """
                DELETE FROM CrashReport WHERE guid NOT IN (
                    SELECT guid FROM CrashReport ORDER BY received_at DESC LIMIT $keep
                )
            """.trimIndent(),
            parameters = 0,
        )
    }

    private companion object {
        const val KEEP = 500
    }
}
