package com.example.poster.model

import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Posts saved for later (feature.bookmarks). The feed reads the table itself. */
class BookmarksLocalRepository {
    private val queries = DatabaseManager(DatabaseDriverFactory()).getDatabase().bookmarkQueries

    fun add(userId: String, postId: String) =
        queries.addBookmark(userId, postId, Clock.System.now().toLocalDateTime(TimeZone.UTC).toString())

    fun remove(userId: String, postId: String) = queries.removeBookmark(userId, postId)

    fun of(userId: String): List<String> = queries.bookmarksOf(userId).executeAsList()
}
