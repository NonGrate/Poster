package com.example.poster.db

import app.cash.sqldelight.db.SqlDriver

const val DATABASE_NAME = "post.db"

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}