package com.example.poster

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `scripts/backup-database.sh`, which is the whole backup story on staging.
 *
 * A backup nobody has ever restored is a guess, so these run the real script
 * against a real database and then open what comes out. The script also runs
 * against a *live* database — the server writes while the nightly task runs —
 * which is why it uses `.backup` rather than `cp`, and that is what the last
 * test here pins.
 */
class BackupScriptTest {

    private val script = File("../scripts/backup-database.sh").absoluteFile

    @Test
    fun aBackupCanBeRestoredAndHoldsWhatWasThere() = withTemporaryDatabase { db, backups ->
        sqlite(db, "CREATE TABLE Post (guid TEXT PRIMARY KEY, title TEXT)")
        sqlite(db, "INSERT INTO Post VALUES ('p-1', 'for my mother')")

        val result = runBackup(db, backups)
        assertEquals(0, result.exitCode, "the script failed:\n${result.output}")

        val restored = File(backups.parentFile, "restored.db")
        gunzip(backups.listFiles()!!.single(), restored)
        assertEquals("ok", sqlite(restored, "PRAGMA integrity_check;"))
        assertEquals(
            "for my mother",
            sqlite(restored, "SELECT title FROM Post WHERE guid = 'p-1';"),
            "the backup did not contain the data that was in the database",
        )
    }

    @Test
    fun oldBackupsArePrunedAndTheNewestAreKept() = withTemporaryDatabase { db, backups ->
        sqlite(db, "CREATE TABLE Post (guid TEXT PRIMARY KEY)")

        repeat(4) {
            assertEquals(0, runBackup(db, backups, keep = 2).exitCode)
            // The filename carries a whole-second timestamp, so without this the
            // four backups would collide into one name.
            Thread.sleep(1100)
        }

        val kept = backups.listFiles()!!.sortedBy { it.name }
        assertEquals(2, kept.size, "expected 2 backups, found ${kept.map { it.name }}")
    }

    @Test
    fun aMissingDatabaseFailsLoudlyRatherThanWritingAnEmptyBackup() {
        val directory = Files.createTempDirectory("poster-backup").toFile()
        try {
            val backups = File(directory, "backups")
            val result = runBackup(File(directory, "absent.db"), backups)

            assertTrue(result.exitCode != 0, "a missing database should fail the task")
            assertTrue(
                backups.listFiles().isNullOrEmpty(),
                "a backup file was written for a database that does not exist",
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    /**
     * The database is a live one, and a backup has to be taken through SQLite
     * rather than by copying the file.
     *
     * This is pinned in WAL mode, where the difference is unambiguous: recent
     * commits live in a separate `-wal` file, so copying `post.db` alone
     * produces a database that opens cleanly, passes an integrity check, and is
     * quietly missing rows — the worst way to lose data, because nothing
     * complains. `.backup` reads through SQLite and includes them.
     *
     * The server does not run in WAL mode today. That is exactly why the test
     * sets it here: switching to WAL is an ordinary performance change somebody
     * could make later, and it must not silently turn the backups into
     * something that loses whatever was written most recently.
     */
    @Test
    fun aBackupIsTakenThroughSqliteRatherThanByCopyingTheFile() = withTemporaryDatabase { db, backups ->
        // The connection has to stay open: SQLite folds the -wal back into the
        // database when the last one closes, and then a plain copy would have
        // everything after all.
        val writer = ProcessBuilder("sqlite3", db.path).redirectErrorStream(true).start()
        val statements = writer.outputStream.bufferedWriter()
        statements.write("PRAGMA journal_mode=WAL;\n")
        statements.write("CREATE TABLE Post (guid TEXT PRIMARY KEY, title TEXT);\n")
        statements.write("INSERT INTO Post VALUES ('p-1', 'for my mother');\n")
        statements.write("SELECT 'written';\n")
        statements.flush()

        // Wait until the row is committed and visible to a separate reader, and
        // is sitting in the -wal file rather than the main database. A non-empty
        // -wal is not enough on its own: on some SQLite builds PRAGMA
        // journal_mode=WAL writes a -wal header before any row is committed, so
        // backing up on that alone could catch a table-less database — which
        // restores cleanly and then has no Post table, so the SELECT below
        // fails with a non-zero exit rather than the missing-row this pins.
        val wal = File("${db.path}-wal")
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !(wal.length() > 0 && rowIsVisible(db))) {
            Thread.sleep(50)
        }
        check(wal.length() > 0) { "expected the row to be sitting in the -wal file" }
        check(rowIsVisible(db)) { "the committed row never became visible before the backup ran" }

        val result = runBackup(db, backups)

        statements.close()
        writer.waitFor(30, TimeUnit.SECONDS)
        assertEquals(0, result.exitCode, "the script failed on a WAL database:\n${result.output}")

        val restored = File(backups.parentFile, "restored.db")
        gunzip(backups.listFiles()!!.single(), restored)
        assertEquals("ok", sqlite(restored, "PRAGMA integrity_check;"))
        assertEquals(
            "for my mother",
            sqlite(restored, "SELECT title FROM Post WHERE guid = 'p-1';"),
            "the backup lost a committed row — it copied the database file " +
                "instead of going through SQLite",
        )
    }

    private class Result(val exitCode: Int, val output: String)

    private fun runBackup(database: File, backups: File, keep: Int = 14): Result {
        val process = ProcessBuilder("bash", script.path)
            .redirectErrorStream(true)
            .also {
                it.environment()["POSTER_DATABASE_PATH"] = database.path
                it.environment()["POSTER_BACKUP_DIR"] = backups.path
                it.environment()["POSTER_BACKUP_KEEP"] = keep.toString()
                // Never inherited from the machine running the tests.
                it.environment().remove("POSTER_BACKUP_UPLOAD")
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(60, TimeUnit.SECONDS)
        return Result(process.exitValue(), output)
    }

    /**
     * Whether the seeded row reads back from a separate connection.
     *
     * In WAL mode a reader sees committed rows without a checkpoint, and while
     * the writer connection is still open this read does not fold the -wal back
     * into the database — so the row stays where the backup has to reach through
     * SQLite to find it. Non-throwing on purpose: it is polled before the row
     * (or the table) exists.
     */
    private fun rowIsVisible(database: File): Boolean {
        val process = ProcessBuilder("sqlite3", database.path, "SELECT title FROM Post WHERE guid = 'p-1';")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(30, TimeUnit.SECONDS)
        return process.exitValue() == 0 && output == "for my mother"
    }

    private fun sqlite(database: File, sql: String): String {
        val process = ProcessBuilder("sqlite3", database.path, sql)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(60, TimeUnit.SECONDS)
        check(process.exitValue() == 0) { "sqlite3 failed: $output" }
        return output
    }

    private fun gunzip(source: File, target: File) {
        java.util.zip.GZIPInputStream(source.inputStream()).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    private fun withTemporaryDatabase(block: (database: File, backups: File) -> Unit) {
        // sqlite3 is a hard dependency of the backup path, not of this test: if
        // it is missing the backups do not work either, so this fails rather
        // than quietly skipping.
        check(ProcessBuilder("sqlite3", "-version").start().waitFor() == 0) {
            "sqlite3 is required — it is what takes the backup"
        }
        val directory = Files.createTempDirectory("poster-backup").toFile()
        try {
            block(File(directory, "post.db"), File(directory, "backups"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
