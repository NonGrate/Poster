package com.example.poster.model

import com.example.poster.PostDatabase
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
import java.time.Instant
import java.util.UUID

/** One person's report of one post, and the pile for whoever reads them. */
class ReportsRepository(
    private val database: PostDatabase = DatabaseManager(DatabaseDriverFactory()).getDatabase(),
) {
    private val queries = database.postReportQueries

    /**
     * Records that somebody thinks this post should not be here.
     *
     * Pressing it twice is not two problems: the row is keyed on the pair, so
     * a second report from the same person replaces the first rather than
     * making one upset reader look like a crowd.
     */
    fun record(postId: String, reporterId: String, reason: String?) {
        queries.recordReport(
            id = UUID.randomUUID().toString(),
            post_id = postId,
            reporter_id = reporterId,
            reason = reason,
            created_at = Instant.now().toString(),
        )
    }

    fun count(): Long = queries.countReports().executeAsOne()

    /** How many posts are waiting on a decision, which is the number worth showing. */
    fun pendingPosts(): Int = reported().count { it.deletedAt == null }

    /**
     * Grouped by post: five reports of one post are one decision.
     *
     * Hidden ones sort last rather than disappearing — somebody has to be able
     * to check that what they hid was what was reported.
     */
    fun reported(): List<ReportedPost> =
        queries.reportedPosts { postId, count, lastReported, reasons, title, message, author, deletedAt, visibility ->
            ReportedPost(
                postId = postId,
                reportCount = count,
                lastReported = lastReported ?: "",
                reasons = reasons.orEmpty().split(" | ").map(String::trim).filter(String::isNotEmpty),
                title = title,
                message = message,
                author = author,
                deletedAt = deletedAt,
                visibility = visibility,
            )
        }.executeAsList()

    /** Dealt with, one way or the other. A pile that never shrinks stops being read. */
    fun dismissFor(postId: String) = queries.dismissReportsFor(postId)

}

/** One post somebody objected to, with everything said about it. */
data class ReportedPost(
    val postId: String,
    val reportCount: Long,
    val lastReported: String,
    val reasons: List<String>,
    val title: String,
    val message: String,
    val author: String,
    val deletedAt: String?,
    val visibility: String,
) {
    val hidden: Boolean get() = deletedAt != null
}
