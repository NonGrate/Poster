package com.example.poster.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.poster.PostDatabase
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The JVM driver: what the server and the JVM tests use.
 *
 * The database is a file whose path comes from `POSTER_DATABASE_PATH` (the
 * environment, for a deployment) or `poster.database` (a system property, for
 * scripts and tests), falling back to [DATABASE_NAME] in the working directory.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val databaseName = databasePath()
        val dbPath = "jdbc:sqlite:$databaseName"
        // Schema work happens on its own connection, with foreign keys off.
        //
        // Rebuilding a table means dropping and renaming it, and SQLite is
        // explicit that this must be done with enforcement off — otherwise the
        // rebuild trips over the references pointing at the table being
        // replaced.
        JdbcSqliteDriver(dbPath).use { migrator -> applySchema(migrator) }

        // SQLite has foreign keys off unless a connection asks, and it is a
        // per-connection setting: it has to be a connection property rather
        // than a statement, because this driver opens more than one connection.
        //
        // Only the server asks. The app keeps posts from the feed in the same
        // schema and never stores their authors, so a phone with this on could
        // not write its own cache at all — the constraint is inert there on
        // purpose. The phone is a cache; the server is the truth.
        // A busy timeout, for the same reason the foreign keys are here: this
        // driver opens a connection per thread, so the server's request
        // handlers are genuinely concurrent writers, and without a timeout the
        // second one fails instantly with SQLITE_BUSY rather than waiting the
        // moment out — which surfaced as a request failing part-way through a
        // multi-step write.
        //
        // Not journal_mode=WAL here, though it would help: switching the mode
        // needs exclusive access to the file and every new connection would
        // try, so the connections race each other to set it. It belongs in a
        // one-off migration step, not in a per-connection property.
        return if (enforcesForeignKeys()) {
            JdbcSqliteDriver(
                dbPath,
                java.util.Properties().apply {
                    setProperty("foreign_keys", "true")
                    setProperty("busy_timeout", "5000")
                },
            )
        } else {
            JdbcSqliteDriver(dbPath)
        }
    }

    private fun databasePath(): String =
        System.getenv("POSTER_DATABASE_PATH")
            ?: System.getProperty("poster.database")
            ?: DATABASE_NAME

    /**
     * Whether this process wants the cascades in the schema to actually fire.
     *
     * Off by default, including for the shared JVM tests, which write posts
     * into a local store with no users in it — exactly as a phone does.
     */
    private fun enforcesForeignKeys(): Boolean =
        System.getenv("POSTER_ENFORCE_FOREIGN_KEYS")?.equals("true", ignoreCase = true)
            ?: System.getProperty("poster.enforceForeignKeys")?.equals("true", ignoreCase = true)
            ?: false

    /**
     * Brings the database to the schema this build expects.
     *
     * `user_version` is SQLite's own slot for this, and it is what the Android
     * and native drivers use, so all three platforms agree on what version a
     * file is at.
     *
     * Migrations run one at a time, writing the version after each. SQLite
     * commits DDL as it goes, so "all or nothing" was never on offer; remembering
     * exactly how far it got is what lets a failed deploy restart cleanly.
     */
    private fun applySchema(driver: SqlDriver) {
        val current = driver.userVersion()
        val target = PostDatabase.Schema.version

        val effective = if (current == 0L) {
            // Either an empty file or one from before versioning existed.
            // create() succeeds on an empty file and throws when the tables are
            // already there, which is what tells the two apart.
            if (runCatching { PostDatabase.Schema.create(driver) }.isSuccess) target else 1L
        } else {
            current
        }
        if (effective < target) {
            snapshotBeforeMigrating(driver, from = effective)
            var at = effective
            while (at < target) {
                PostDatabase.Schema.migrate(driver, at, at + 1)
                at += 1
                driver.execute(identifier = null, sql = "PRAGMA user_version = $at", parameters = 0)
            }
        } else if (current != target) {
            driver.execute(identifier = null, sql = "PRAGMA user_version = $target", parameters = 0)
        }
    }

    private fun SqlDriver.userVersion(): Long = executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        parameters = 0,
        mapper = { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L,
            )
        },
    ).value

    /**
     * Keeps a copy of the database as it was before a migration touches it.
     *
     * A nightly backup can be most of a day old by the time a deploy runs, so a
     * migration that damages data would cost everything written since. This
     * costs one file, only on the deploy that actually migrates.
     *
     * `VACUUM INTO` rather than a file copy: it goes through SQLite, so the
     * result is a consistent database rather than whatever the bytes on disk
     * happened to be. A failure here stops the server rather than migrating
     * anyway — refusing to start is visible and recoverable; migrating without
     * the copy is neither.
     */
    private fun snapshotBeforeMigrating(driver: SqlDriver, from: Long) {
        val database = databasePath()
        // An in-memory database has nothing to keep.
        if (database.contains(":memory:") || database.isEmpty()) return

        val directory = File(database).absoluteFile.parentFile
        val backups = File(System.getenv("POSTER_BACKUP_DIR") ?: File(directory, "backups").path)
        backups.mkdirs()
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        // Nanoseconds as well as the second: two databases migrating in the same
        // second (the test suite does this) must not race for one file name.
        val snapshot = File(backups, "pre-migration-v$from-$stamp-${System.nanoTime() % 1_000_000}.db")

        // VACUUM INTO refuses to overwrite, which is why the name carries a
        // timestamp rather than being reused.
        driver.execute(
            identifier = null,
            sql = "VACUUM INTO '${snapshot.path.replace("'", "''")}'",
            parameters = 0,
        )
        println("database: snapshot before migrating from $from -> ${snapshot.path}")
    }
}
