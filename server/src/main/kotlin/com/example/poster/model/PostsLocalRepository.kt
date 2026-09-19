package com.example.poster.model

import com.example.poster.db.DatabaseManager
import com.example.poster.db.DatabaseDriverFactory
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime

class PostsLocalRepository(
    private val tagRepository: TagLocalRepository = TagLocalRepository(),
) : PostsRepository {
    private val databaseManager = DatabaseManager(DatabaseDriverFactory())
    private val database = databaseManager.getDatabase()
    private val postQueries = database.postQueries

    fun completePost(guid: String, message: String?) {
        postQueries.completePost(kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).toString(), message, guid)
    }

    fun reopenPost(guid: String) {
        postQueries.reopenPost(guid)
    }

    /**
     * The feed as one viewer sees it. A null viewer is not logged in and gets
     * only public posts.
     */
    override fun visiblePosts(
        viewer: String?,
        languages: List<String>,
        limit: Int,
        before: FeedCursor?,
        tags: List<String>,
        groups: List<String>,
        query: String,
        following: Boolean,
        saved: Boolean,
    ): List<Post> {
        val needle = query.trim().lowercase()
        val capped = limit.coerceIn(1, MAX_FEED_LIMIT).toLong()
        // The empty string means "from the newest", which keeps the query one
        // statement instead of two that could drift apart.
        val cursorDate = before?.date.orEmpty()
        val cursorGuid = before?.guid.orEmpty()
        // Same convention as the cursor and the languages: no tag is the empty
        // string, and several are one comma-separated string.
        val wanted = if (tags.isEmpty()) "" else tags.joinToString(",")
        val rooms = if (groups.isEmpty()) "" else groups.joinToString(",")
        val rows = if (viewer == null) {
            // Nobody signed in, so there is no preference to honour and no room
            // to be in: a group filter from a signed-out caller can only
            // name rooms they cannot read, and the public query has no clause
            // for it because there is nothing it could return.
            postQueries.getPublicPosts(tags = wanted, query = needle, before = cursorDate, beforeGuid = cursorGuid, limit = capped).executeAsList()
        } else {
            postQueries.getVisiblePosts(
                viewer = viewer,
                languages = Language.store(languages),
                tags = wanted,
                groups = rooms,
                query = needle,
                following = if (following) 1L else 0L,
                saved = if (saved) 1L else 0L,
                before = cursorDate,
                beforeGuid = cursorGuid,
                limit = capped,
            ).executeAsList()
        }
        return rows.map { it.toPost() }
    }

    override fun visiblePostById(viewer: String?, guid: String): Post? {
        val row = if (viewer == null) {
            postQueries.getPublicPostById(guid).executeAsOneOrNull()
        } else {
            postQueries.getVisiblePostById(guid, viewer).executeAsOneOrNull()
        }
        return row?.toPost()
    }

    override fun postsByAuthor(author: String): List<Post> =
        postQueries.getMyPosts(author).executeAsList().map { it.toPost() }

    /** A public post by its share token, or null — the web page's read path. */
    override fun postByShareToken(token: String): Post? =
        postQueries.getPostByShareToken(token).executeAsOneOrNull()?.toPost()

    /**
     * The post's share token, minting and storing one the first time. One
     * token per post, reused on every later share. [newToken] supplies a fresh
     * candidate (the caller checks it for collisions).
     */
    override fun ensureShareToken(guid: String, newToken: () -> String): String {
        val existing = postQueries.shareTokenOf(guid).executeAsOneOrNull()?.shareToken
        if (existing != null) return existing
        val token = newToken()
        postQueries.setShareToken(token, guid)
        return token
    }

    override fun allPosts(): List<Post> {
        return postQueries.getAllPosts().executeAsList().map { it.toPost() }
    }

    override fun postById(guid: String): Post? {
        val posts = postQueries.getAllPosts().executeAsList().filter {
            it.guid.equals(guid, ignoreCase = true)
        }

        return posts.firstOrNull()?.toPost()
    }

    override fun addOrUpdatePost(post: Post) {
        val existingPost = postById(post.guid)

        if (existingPost != null) {
            postQueries.updatePost(
                title = post.title,
                message = post.message,
                groupId = post.group,
                likes = post.likes.toLong(),
                date = post.date.toString(),
                visibility = post.visibility,
                language = post.language,
                image = post.image,
                guid = post.guid,
            )
        } else {
            postQueries.insertPost(
                guid = post.guid,
                title = post.title,
                message = post.message,
                author = post.author,
                groupId = post.group,
                likes = post.likes.toLong(),
                date = post.date.toString(),
                visibility = post.visibility,
                language = post.language,
                completed_at = post.completedAt?.toString(),
                completion_message = post.completionMessage,
                image = post.image,
            )
        }

        tagRepository.removeAllTagsFromPost(post.guid)
        // Only tags that already exist. A name nobody curated used to create a
        // tag here, which is what made the set unfilterable in the first place
        // — and now that tags are never tidied away, anything invented would
        // stay in the picker for good.
        post.tags.distinct().forEach { tagName ->
            val tag = tagRepository.tagsByName(tagName).firstOrNull { it.name == tagName }
            if (tag != null) tagRepository.addTagToPost(post.guid, tag.guid)
        }
    }

    /**
     * One row, one post. It was written out by hand at each call site, and one
     * of those copies had quietly lost `language` — a row read back through it
     * claimed to be English whatever it was written in. Nothing displayed that
     * copy, so nothing ever said so.
     */
    override fun imagesInUse(): List<String> = postQueries.imagesInUse().executeAsList().filterNotNull()

    override fun postByImage(id: String): Post? = postQueries.postByImage(id).executeAsOneOrNull()?.toPost()

    private fun com.example.poster.db.Post.toPost() = Post(
        guid = guid,
        title = title,
        message = message,
        author = author,
        group = groupId,
        image = image,
        likes = likes.toInt(),
        date = LocalDateTime.parse(date),
        tags = tagRepository.getTagsForPost(guid).map { tag -> tag.name },
        completedAt = completed_at?.let { at -> LocalDateTime.parse(at) },
        completionMessage = completion_message,
        visibility = visibility,
        language = language,
        isFavorite = false,
    )

    override fun removePost(guid: String): Boolean {
        val existingPost = postById(guid)
        if (existingPost != null) {
            tagRepository.removeAllTagsFromPost(guid)
            postQueries.deletePost(guid)
            return true
        }
        return false
    }
}
