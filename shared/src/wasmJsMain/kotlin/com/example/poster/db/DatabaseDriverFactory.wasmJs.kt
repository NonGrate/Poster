package com.example.poster.db

import app.cash.sqldelight.db.SqlDriver

/**
 * No database in the browser (docs/Web.md): the web app is online only, and
 * `PostRepository` is built without a `PostLocalStore` there. The class exists
 * because `shared` declares it for every platform; calling it is a bug.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver = error("the web app has no local database; see docs/Web.md")
}
