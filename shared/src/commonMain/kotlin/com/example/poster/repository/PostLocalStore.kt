package com.example.poster.repository

import kotlinx.serialization.json.Json
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.poster.config.Features
import com.example.poster.db.DatabaseManager
import com.example.poster.model.Post
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.withContext

/**
 * The posts this device has on disk.
 *
 * The point is what happens with no network: a cold start shows the last feed
 * immediately instead of an empty screen waiting on a request that may never
 * come back. Everything the UI reads comes from here; the network only writes.
 *
 * The single writer, so a sync and an optimistic edit cannot interleave into a
 * half-updated list.
 */
class PostLocalStore(
    databaseManager: DatabaseManager,
    private val dispatchers: DispatcherProvider,
) {
    private val queries = databaseManager.getDatabase().postQueries
    private val tagQueries = databaseManager.getDatabase().postTagQueries
    private val tagsTable = databaseManager.getDatabase().tagQueries
    private val favoriteQueries = databaseManager.getDatabase().userPostFavoriteQueries
    private val userQueries = databaseManager.getDatabase().userQueries
    private val outboxQueries = databaseManager.getDatabase().outboxQueries

    /** Re-emits whenever the table changes, which is what makes the UI follow the DB. */
    fun posts(): Flow<List<Post>> =
        queries.getAllPosts()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { row -> row.toPost(tagsOf(row.guid)) } }

    /**
     * Everything this person wrote, from disk.
     *
     * Its own query rather than a filter over [posts], because that one asks
     * for the feed and `in_feed` is exactly what a post of yours loses when
     * it falls off the end of the page. It is still yours, and My Posts is
     * not the feed.
     */
    fun mine(userId: String): Flow<List<Post>> =
        queries.getMyPosts(userId)
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { row -> row.toPost(tagsOf(row.guid)) } }

    private fun tagsOf(postGuid: String): List<String> =
        tagQueries.getTagsForPost(postGuid).executeAsList().map { it.name }

    suspend fun replaceAll(posts: List<Post>, viewer: String = "") = withContext(dispatchers.io) {
        queries.transaction {
            // A post deleted or hidden on the server has to disappear here
            // too, so the feed is replaced rather than merged — except for the
            // ones being liked, which are kept even when the feed drops
            // them (an resolved post leaves the feed after a day).
            // The viewer's own posts survive this, like favorites do: the
            // feed is a page and theirs may sit outside it. See deleteFeedPosts.
            queries.deleteFeedPosts(viewer)
            // Whatever survived that survived because somebody has liked
            // it. Marking it out of the feed before writing the new one is what
            // stops it showing there: it stays on the device for the Liked
            // list, and the feed does not carry a post the server no longer
            // sends — from a group you have left, for instance.
            queries.markEverythingOutOfFeed()
            posts.forEach { insert(it) }
        }
    }

    /**
     * Everything one person wrote, as the server has it.
     *
     * Its own fetch and its own replace, rather than a filter over the feed:
     * the feed is a page, and somebody's own posts are a finite list they
     * expect all of. Whichever of the two writes a post last, it is the same
     * post and the same row.
     */
    suspend fun replaceMine(viewer: String, posts: List<Post>) = withContext(dispatchers.io) {
        queries.transaction {
            queries.deleteMyPosts(viewer)
            posts.forEach { insert(it, inFeed = false) }
        }
    }

    /**
     * Adds an older page underneath what is already here.
     *
     * Deliberately not [replaceAll]: that clears the feed first, which is right
     * for "here is the world again" and wrong for "here is more of it". A page
     * of older posts is added to what the reader already has, and nothing
     * they were looking at moves.
     */
    suspend fun appendPage(posts: List<Post>) = withContext(dispatchers.io) {
        queries.transaction {
            posts.forEach { insert(it) }
        }
    }

    suspend fun upsert(post: Post) = withContext(dispatchers.io) {
        queries.transaction {
            queries.deletePost(post.guid)
            insert(post)
        }
    }

    suspend fun delete(guid: String) = withContext(dispatchers.io) {
        queries.deletePost(guid)
    }

    /** One of your own, kept off the feed until the server has it. */
    suspend fun upsertMine(post: Post) = withContext(dispatchers.io) {
        queries.transaction {
            queries.deletePost(post.guid)
            insert(post, inFeed = false)
        }
    }

    // --- outbox (feature.offlineOutbox) --------------------------------------

    /** What is waiting to be sent, by post id. Re-emits as the queue changes. */
    fun unsent(): Flow<Set<String>> =
        outboxQueries.queuedGuids().asFlow().mapToList(dispatchers.io).map { it.toSet() }

    suspend fun enqueue(post: Post, kind: String) = withContext(dispatchers.io) {
        // Editing something the server has never seen is still an add.
        val existing = outboxQueries.queued().executeAsList().firstOrNull { it.guid == post.guid }
        val effective = if (existing?.kind == Outbox.ADD) Outbox.ADD else kind
        outboxQueries.enqueue(post.guid, effective, Json.encodeToString(Post.serializer(), post), post.date.toString())
    }

    suspend fun queued(): List<Pair<String, Post>> = withContext(dispatchers.io) {
        outboxQueries.queued().executeAsList().mapNotNull { row ->
            runCatching { Json.decodeFromString(Post.serializer(), row.payload) }.getOrNull()?.let { row.kind to it }
        }
    }

    suspend fun dequeue(guid: String) = withContext(dispatchers.io) { outboxQueries.dequeue(guid) }

    object Outbox {
        const val ADD = "add"
        const val UPDATE = "update"
    }

    /**
     * What this person has liked, from disk. The posts themselves are the
     * rows the feed uses, so a post answered or edited anywhere shows here too.
     */
    fun favorites(userId: String): Flow<List<Post>> =
        favoriteQueries.getUserFavoritePosts(userId)
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { row -> row.toPost(tagsOf(row.guid)) } }

    /**
     * Replaces this person's favorites with what the server says, keeping the
     * posts themselves — the feed may not contain them all, since you can like
     * for something and then it leaves the feed.
     */
    suspend fun replaceFavorites(userId: String, posts: List<Post>) =
        withContext(dispatchers.io) {
            queries.transaction {
                favoriteQueries.deleteFavoritesForUser(userId)
                posts.forEach { post ->
                    insert(post)
                    favoriteQueries.addFavoritePost(userId, post.guid, null)
                }
            }
        }

    /**
     * Liking something, and counting this device among those doing it.
     *
     * The count moves with the join row because nothing else moves it until the
     * next refresh — see adjustPostLikes. Guarded by whether the row is
     * already there so a second tap counts once: the insert ignores duplicates
     * silently, and an unguarded increment beside it would not.
     */
    suspend fun addFavorite(userId: String, postId: String) = withContext(dispatchers.io) {
        queries.transaction {
            if (!favoriteQueries.isPostFavorite(userId, postId).executeAsOne()) {
                favoriteQueries.addFavoritePost(userId, postId, null)
                queries.adjustPostLikes(1, postId)
            }
        }
    }

    suspend fun removeFavorite(userId: String, postId: String) = withContext(dispatchers.io) {
        queries.transaction {
            if (favoriteQueries.isPostFavorite(userId, postId).executeAsOne()) {
                favoriteQueries.removeFavoritePost(userId, postId)
                queries.adjustPostLikes(-1, postId)
            }
        }
    }

    /**
     * Signing out has to empty this. What is cached here was fetched as one
     * person, and a feed obeys visibility rules — the next person to sign in on
     * this device must not be shown the previous one's private posts while a
     * refresh is in flight.
     */
    suspend fun clear() = withContext(dispatchers.io) {
        queries.transaction {
            favoriteQueries.deleteAllFavorites()
            tagQueries.deleteAllPostTags()
            queries.deleteAllPosts()
            // The posts went and these stayed: the cached author rows are
            // other people's names and photos, and the outbox holds text the
            // person signing out wrote and never sent. Neither belongs to
            // whoever signs in next on this phone.
            userQueries.deleteCachedAuthors()
            outboxQueries.deleteAllOutbox()
        }
    }

    /** A one-off read, for callers that want the rows rather than a subscription. */
    suspend fun snapshot(): List<Post> = withContext(dispatchers.io) {
        queries.getAllPosts().executeAsList().map { row -> row.toPost(tagsOf(row.guid)) }
    }

    private fun insert(post: Post, inFeed: Boolean = true) {
        val write = if (inFeed) queries::insertPost else queries::insertMyPost
        write(
            post.guid,
            post.title,
            post.message,
            post.author,
            post.group,
            post.likes.toLong(),
            post.date.toString(),
            post.visibility,
            post.language,
            // Persisted like everything else the server sent. Left out before, so a
            // post's answered state was dropped to null the moment it was cached —
            // the feed then read it from disk as unanswered and offered it as new.
            post.completedAt?.toString(),
            post.completionMessage,
            post.image,
        )
        // What the server derived on the way out, kept so the cached row reads
        // the same as the fresh one: the comment count on the row, the author's
        // name and photo as a stub User row. Both were dropped before, so the
        // feed showed no author line and no comment badge once it came from disk.
        if (Features.COMMENTS) queries.cacheComments(post.comments.toLong(), post.guid)
        if (Features.AUTHORS) post.authorName?.let { name ->
            userQueries.cacheAuthor(post.author, name, post.author)
            userQueries.updateAuthor(name, post.authorPhoto, post.author)
        }
        // Tags live in their own table, so the join is rebuilt with the row.
        // The name doubles as the id here: this table is a cache of what the
        // server said, and a tag is identified by its name everywhere else.
        tagQueries.removeAllTagsFromPost(post.guid)
        post.tags.distinct().forEach { name ->
            tagsTable.insertTag(name, name)
            tagQueries.addTagToPost(post.guid, name)
        }
    }

    private fun com.example.poster.db.Post.toPost(tags: List<String>): Post {
        // One read, not two: the getter is a query, and the name and the photo
        // both come off the same row.
        val cached = cachedAuthor
        return Post(
            guid = guid,
            title = title,
            message = message,
            author = author,
            group = groupId,
            image = image,
            likes = likes.toInt(),
            date = LocalDateTime.parse(date),
            completedAt = completed_at?.let { LocalDateTime.parse(it) },
            completionMessage = completion_message,
            visibility = visibility,
            language = language,
            tags = tags,
            comments = comments.toInt(),
            authorName = cached?.let { "${it.name} ${it.surname}".trim().ifBlank { null } },
            authorPhoto = cached?.photo,
        )
    }

    private val com.example.poster.db.Post.cachedAuthor
        get() = userQueries.getUserById(author).executeAsOneOrNull()
}
