package com.example.poster.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.poster.PostDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // NativeSqliteDriver automatically creates the database if it doesn't exist
        return NativeSqliteDriver(
            schema = PostDatabase.Schema,
            name = DATABASE_NAME
        )
    }
}
