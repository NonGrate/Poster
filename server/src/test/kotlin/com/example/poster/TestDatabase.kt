package com.example.poster

import app.cash.sqldelight.db.SqlDriver
import com.example.poster.db.DatabaseDriverFactory

/**
 * The database `poster.database` points at.
 *
 * No repository opens one for itself any more — the server builds one and hands
 * it round — so a test that reaches past the routes to check a row builds its
 * own handle onto the same file here.
 */
internal fun testDriver(): SqlDriver = DatabaseDriverFactory().createDriver()

internal fun testDatabase(): PostDatabase = PostDatabase(testDriver())
