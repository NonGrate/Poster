package com.example.poster.comments

import com.example.poster.PostDatabase
import com.example.poster.model.Comment
import java.time.Instant
import java.util.UUID

/** A comment as the admin panel sees it: with who wrote it and on what. */
data class CommentEntry(
    val comment: Comment,
    val authorEmail: String,
    val postTitle: String,
    val hidden: Boolean,
)

// No default database, for the reason given on PostsLocalRepository.
class CommentsRepository(
    database: PostDatabase,
) {
    private val queries = database.commentQueries

    fun forPost(postGuid: String): List<Comment> =
        queries.commentsForPost(postGuid).executeAsList().map { it.toModel() }

    fun byId(guid: String): Comment? = queries.commentById(guid).executeAsOneOrNull()?.toModel()

    fun add(postGuid: String, author: String, text: String): Comment {
        val comment = Comment(
            guid = UUID.randomUUID().toString(),
            postGuid = postGuid,
            author = author,
            text = text,
            createdAt = Instant.now().toString(),
        )
        queries.insertComment(comment.guid, comment.postGuid, comment.author, comment.text, comment.createdAt)
        return comment
    }

    /** The author's or the post author's own removal: gone for good, not hidden. */
    fun remove(guid: String) = queries.deleteComment(guid)

    fun hide(guid: String) = queries.hideComment(Instant.now().toString(), guid)
    fun restore(guid: String) = queries.restoreComment(guid)

    fun countFor(postGuid: String): Int = queries.countCommentsForPost(postGuid).executeAsOne().toInt()

    fun allIncludingHidden(limit: Long = 500): List<CommentEntry> =
        queries.allCommentsIncludingDeleted(limit) { guid, postGuid, author, text, createdAt, deletedAt, email, title ->
            CommentEntry(Comment(guid, postGuid, author, text, createdAt), email, title, deletedAt != null)
        }.executeAsList()

    private fun com.example.poster.db.Comment.toModel() = Comment(
        guid = guid, postGuid = post_guid, author = author, text = text, createdAt = created_at,
    )
}
