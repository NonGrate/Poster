package com.example.poster.model

import com.example.poster.PostDatabase
import kotlinx.datetime.Clock
import java.util.UUID

/** What a moderator did, and to whom. Every action here writes an audit row. */
data class AuditEntry(
    val id: String,
    val actorId: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val reason: String?,
    val createdAt: String,
)

// No default database, for the reason given on PostsLocalRepository.
class ModerationRepository(
    private val database: PostDatabase,
) {
    private val users = database.userQueries
    private val posts = database.postQueries
    private val postTags = database.postTagQueries
    private val audit = database.moderationAuditQueries

    fun allUsersIncludingBanned(): List<User> = users.getAllUsers().executeAsList().map { it.toModel() }

    fun allPostsIncludingDeleted(): List<Pair<Post, Boolean>> {
        // One statement for the whole page rather than one per post, which is
        // what kept tags off it: this lists every post there has ever been.
        val tagsByPost = postTags.allPostTags().executeAsList()
            .groupBy({ it.post_guid }, { it.name })
        return posts.getAllPostsIncludingDeleted().executeAsList().map {
            Post(
                guid = it.guid,
                title = it.title,
                message = it.message,
                author = it.author,
                group = it.groupId,
                image = it.image,
                likes = it.likes.toInt(),
                date = kotlinx.datetime.LocalDateTime.parse(it.date),
                // Everything the panel displays has to come through here. It
                // used to stop at the date, so a post's language read as
                // English whatever was stored, and the "answered" line — which
                // is drawn from completedAt — could never appear at all.
                completedAt = it.completed_at?.let(kotlinx.datetime.LocalDateTime::parse),
                completionMessage = it.completion_message,
                visibility = it.visibility,
                language = it.language,
                tags = tagsByPost[it.guid].orEmpty(),
            ) to (it.deleted_at != null)
        }
    }

    fun ban(actorId: String, userId: String, reason: String) = database.transaction {
        users.setStatus(User.STATUS_BANNED, now(), reason, userId)
        // The status alone only hides them; their refresh token would keep
        // minting access tokens for as long as they cared to use it. A ban
        // that leaves the person signed in is not a ban.
        database.refreshTokenQueries.deleteRefreshTokensForUser(userId)
        record(actorId, "ban", "user", userId, reason)
    }

    fun unban(actorId: String, userId: String) = database.transaction {
        users.setStatus(User.STATUS_ACTIVE, null, null, userId)
        record(actorId, "unban", "user", userId, null)
    }

    fun setRole(actorId: String, userId: String, role: String) {
        users.setRole(role, userId)
        record(actorId, "role:$role", "user", userId, null)
    }

    fun softDeletePost(actorId: String, postId: String, reason: String?) {
        posts.softDeletePost(now(), postId)
        record(actorId, "delete", "post", postId, reason)
    }

    fun restorePost(actorId: String, postId: String) {
        posts.restorePost(postId)
        record(actorId, "restore", "post", postId, null)
    }

    fun recentAudit(limit: Long = 100): List<AuditEntry> =
        audit.recent(limit).executeAsList().map {
            AuditEntry(it.id, it.actor_id, it.action, it.target_type, it.target_id, it.reason, it.created_at)
        }

    fun recordGroupAction(actorId: String, action: String, groupId: String, detail: String?) {
        record(actorId, action, "group", groupId, detail)
    }

    /** Hiding or restoring a comment from the admin panel; the panel does the write, this keeps the trail. */
    fun recordCommentAction(actorId: String, action: String, commentId: String, detail: String?) {
        record(actorId, action, "comment", commentId, detail)
    }

    /** Correcting what a post is written in, which is a moderator's call. */
    fun setPostLanguage(actorId: String, postId: String, language: String) {
        if (!Language.isKnown(language)) return
        posts.setPostLanguage(language, postId)
        record(actorId, "post:language", "post", postId, language)
    }

    /**
     * Confirming an address by hand, for somebody whose email never arrived.
     *
     * The email is the usual proof; this is the way through when it does not
     * work — a spam folder nobody finds, an address typed with a letter wrong,
     * a provider refusing mail from a young domain. Audited, because it is
     * somebody vouching for somebody else rather than the person themselves.
     */
    fun markVerified(actorId: String, userId: String, at: String) {
        users.markVerified(at, userId)
        record(actorId, "user:verified", "user", userId, null)
    }

    /** The password itself is never written down — only that it was replaced. */
    fun recordPasswordReset(actorId: String, userId: String) {
        record(actorId, "user:password_reset", "user", userId, null)
    }

    private fun record(actorId: String, action: String, targetType: String, targetId: String, reason: String?) {
        audit.record(UUID.randomUUID().toString(), actorId, action, targetType, targetId, reason, now())
    }

    private fun now() = Clock.System.now().toString()
}
