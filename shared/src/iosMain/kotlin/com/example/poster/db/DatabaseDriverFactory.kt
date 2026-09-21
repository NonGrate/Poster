package com.example.poster.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.poster.PostDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSUserDomainMask

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // NativeSqliteDriver automatically creates the database if it doesn't exist
        val driver = NativeSqliteDriver(
            schema = PostDatabase.Schema,
            name = DATABASE_NAME,
        )
        protect()
        return driver
    }

    /**
     * Asks iOS to keep the database encrypted until the phone is first
     * unlocked after a boot.
     *
     * The file holds posts the person can see, including ones shared only
     * with a group, and anything they wrote and have not sent. The default
     * class leaves it readable whenever the device is running, which includes
     * a device somebody has taken and not unlocked. "Until first user
     * authentication" is the strongest class this can use: the app reads its
     * own database from the background, and complete protection would deny it
     * while the screen is locked.
     *
     * Best effort by design. A failure here means the file keeps the default
     * class, which is what it had before, and is not a reason to refuse to
     * start.
     */
    @kotlinx.cinterop.ExperimentalForeignApi
    private fun protect() {
        val manager = NSFileManager.defaultManager
        val documents = manager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? platform.Foundation.NSURL ?: return
        // The journal and write-ahead files hold the same rows on their way in.
        listOf(DATABASE_NAME, "$DATABASE_NAME-journal", "$DATABASE_NAME-wal", "$DATABASE_NAME-shm")
            .forEach { name ->
                val path = documents.URLByAppendingPathComponent(name)?.path ?: return@forEach
                if (!manager.fileExistsAtPath(path)) return@forEach
                manager.setAttributes(
                    mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
                    ofItemAtPath = path,
                    error = null,
                )
            }
    }
}
