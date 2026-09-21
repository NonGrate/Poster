package com.example.poster.db

import com.example.poster.PostDatabase

/**
 * Manages database initialization and provides access to the database.
 * This class is designed to be injectable through dependency injection.
 */
class DatabaseManager(private val databaseDriverFactory: DatabaseDriverFactory) {
    // Lazy initialization of the database
    private val _database by lazy {
        // Create the database driver
        val driver = databaseDriverFactory.createDriver()

        // Create the database instance
        PostDatabase(driver)
    }

    fun getDatabase(): PostDatabase {
        return _database
    }
}
