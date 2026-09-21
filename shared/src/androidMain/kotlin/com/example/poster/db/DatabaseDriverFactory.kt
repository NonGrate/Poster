package com.example.poster.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.poster.PostDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        // Check if the database file exists
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val dbExists = dbFile.exists()

        // Create the driver
        val driver = AndroidSqliteDriver(
            schema = PostDatabase.Schema,
            context = context,
            name = DATABASE_NAME
        )

        // If the database didn't exist, it was just created, so we don't need to do anything else
        // AndroidSqliteDriver automatically creates the database if it doesn't exist
        return driver
    }
}
