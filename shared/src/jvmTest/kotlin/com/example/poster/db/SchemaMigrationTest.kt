package com.example.poster.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.poster.PostDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The database has to be able to move forward.
 *
 * SQLDelight records one `databases/<version>.db` per schema version so a
 * migration can be checked against the shape it will actually meet. The tests
 * below are data-driven from those files: with only `1.db` present (a fresh
 * template) the migration cases have nothing to run and pass trivially; the
 * moment `2.sqm` and `2.db` exist they start exercising the real path. See
 * docs/Database.md for how to add a migration.
 */
class SchemaMigrationTest {

    @Test
    fun aFreshDatabaseIsRecordedAtTheCurrentVersion() {
        withDatabaseFile { path ->
            System.setProperty("poster.database", path)
            DatabaseDriverFactory().createDriver().use { driver ->
                assertEquals(
                    PostDatabase.Schema.version,
                    driver.userVersion(),
                    "a new database did not record which schema it was created at",
                )
            }
        }
    }

    /**
     * Reopening must be a no-op. If `create` ran again it would fail on existing
     * tables, and if the version were not recorded every open would try to
     * migrate from scratch.
     */
    @Test
    fun reopeningAnUpToDateDatabaseChangesNothing() {
        withDatabaseFile { path ->
            System.setProperty("poster.database", path)
            DatabaseDriverFactory().createDriver().use { first ->
                PostDatabase(first).groupQueries.insertGroup("c-1", "Home", "HOME", owner = null)
            }
            DatabaseDriverFactory().createDriver().use { second ->
                val groups = PostDatabase(second).groupQueries.getAllGroups().executeAsList()
                assertEquals(1, groups.size, "reopening lost the data")
                assertEquals(PostDatabase.Schema.version, second.userVersion())
            }
        }
    }

    /**
     * Every recorded older schema is brought forward to the current one, and a
     * snapshot of the old file is kept beside it before anything is changed.
     */
    @Test
    fun everyRecordedOlderVersionMigratesForwardAndIsSnapshotted() {
        for (version in olderRecordedVersions()) {
            withDatabaseFile { path ->
                databaseAtVersion(version, path)
                System.setProperty("poster.database", path)
                DatabaseDriverFactory().createDriver().use { migrated ->
                    assertEquals(
                        PostDatabase.Schema.version,
                        migrated.userVersion(),
                        "a database at version $version was not brought forward",
                    )
                }
                val backups = File(File(path).absoluteFile.parentFile, "backups")
                    .listFiles { f -> f.name.startsWith("pre-migration-v$version-") }
                    .orEmpty()
                assertEquals(1, backups.size, "no snapshot was kept before migrating from $version")
                JdbcSqliteDriver("jdbc:sqlite:${backups.single().path}").use { snapshot ->
                    assertEquals("ok", snapshot.singleText("PRAGMA integrity_check"))
                    assertEquals(version, snapshot.userVersion(), "the snapshot is not the pre-migration file")
                }
            }
        }
    }

    /** Nothing to copy, and nothing to fail on. */
    @Test
    fun aFreshDatabaseIsNotSnapshotted() {
        withDatabaseFile { path ->
            System.setProperty("poster.database", path)
            DatabaseDriverFactory().createDriver().use { }

            val backups = File(File(path).absoluteFile.parentFile, "backups")
            assertTrue(
                backups.listFiles().isNullOrEmpty(),
                "a database created from scratch has no previous state worth keeping",
            )
        }
    }

    private fun olderRecordedVersions(): List<Long> =
        File("src/commonMain/sqldelight/databases").listFiles { f -> f.extension == "db" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            .filter { it < PostDatabase.Schema.version }
            .sorted()

    private fun databaseAtVersion(version: Long, path: String) {
        File("src/commonMain/sqldelight/databases/$version.db").copyTo(File(path), overwrite = true)
        JdbcSqliteDriver("jdbc:sqlite:$path").use { old ->
            old.execute(null, "PRAGMA user_version = $version", 0)
        }
    }

    private fun app.cash.sqldelight.db.SqlDriver.singleText(sql: String): String =
        executeQuery(
            identifier = null,
            sql = sql,
            parameters = 0,
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getString(0).orEmpty() else "")
            },
        ).value

    private fun app.cash.sqldelight.db.SqlDriver.userVersion(): Long =
        executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            parameters = 0,
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
        ).value

    private fun withDatabaseFile(block: (String) -> Unit) {
        val directory = Files.createTempDirectory("poster-migration")
        val previous = System.getProperty("poster.database")
        try {
            block(directory.resolve("test.db").toString())
        } finally {
            if (previous == null) System.clearProperty("poster.database")
            else System.setProperty("poster.database", previous)
            directory.toFile().deleteRecursively()
        }
    }
}
