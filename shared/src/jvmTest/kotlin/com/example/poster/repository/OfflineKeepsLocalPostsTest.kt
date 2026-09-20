package com.example.poster.repository

import com.example.poster.cache.PostCache
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
import com.example.poster.model.Post
import com.example.poster.testing.NoopPostApi
import com.example.poster.testing.withTempDatabase
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No connection is not the same as no posts.
 *
 * Everything the app shows comes off the device, and a refresh that cannot
 * reach the server has learned nothing — least of all that somebody's posts
 * are gone. Showing what was last known, however old, is the whole point of
 * keeping it. An empty Liked list on a train would read as the app having
 * lost what people asked to be liked.
 */
class OfflineKeepsLocalPostsTest {

    private lateinit var store: PostLocalStore

    private val theirs = Post(
        guid = "theirs-1",
        title = "Somebody else's post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = 5,
        date = LocalDateTime.parse("2026-08-19T10:00"),
    )
    private val mine = theirs.copy(guid = "mine-1", title = "My post", author = USER)

    /** A database of its own per test, put back afterwards. */
    private fun withStore(block: suspend () -> Unit) = withTempDatabase {
        store = PostLocalStore(DatabaseManager(DatabaseDriverFactory()), dispatchers)
        runBlocking { block() }
    }

    @Test
    fun aFailedFeedRefreshLeavesTheFeedAlone() = withStore {
        store.replaceAll(listOf(theirs), viewer = USER)

        val result = offlineRepository().refreshPosts(USER)

        assertTrue(result.isFailure)
        assertEquals(listOf(theirs.guid), store.snapshot().map { it.guid })
    }

    @Test
    fun aFailedMyPostsRefreshLeavesMyPostsAlone() = withStore {
        store.replaceMine(USER, listOf(mine))

        val result = offlineRepository().refreshMyPosts(USER)

        assertTrue(result.isFailure)
        assertEquals(listOf(mine.guid), store.mine(USER).first().map { it.guid })
    }

    @Test
    fun aFailedFavoritesRefreshLeavesTheLikerListAlone() = withStore {
        store.replaceAll(listOf(theirs), viewer = USER)
        store.addFavorite(USER, theirs.guid)

        val result = offlineRepository().refreshFavorites(USER)

        assertTrue(result.isFailure)
        assertEquals(listOf(theirs.guid), store.favorites(USER).first().map { it.guid })
    }

    /**
     * A *successful* but empty feed page is not a reason to blank the feed: the
     * feed carries everyone's posts, so an empty page means the request came
     * back wrong, not that the world went quiet.
     */
    @Test
    fun anEmptyFeedPageLeavesTheStoredFeedAlone() = withStore {
        store.replaceAll(listOf(theirs), viewer = USER)

        val result = PostRepository(
            postApi = OfflinePostApi(feedPage = emptyList()),
            cache = PostCache(),
            dispatchers = dispatchers,
            localStore = store,
        ).refreshPosts(USER)

        assertTrue(result.isSuccess)
        assertEquals(listOf(theirs.guid), store.snapshot().map { it.guid })
        assertEquals(listOf(theirs.guid), result.getOrThrow().map { it.guid })
    }

    /** Reaching for an older page offline must not disturb the pages already here. */
    @Test
    fun aFailedOlderPageLeavesWhatIsAlreadyHere() = withStore {
        store.replaceAll(listOf(theirs), viewer = USER)

        val result = offlineRepository().loadOlderPosts(theirs.date.toString(), theirs.guid)

        assertTrue(result.isFailure)
        assertEquals(listOf(theirs.guid), store.snapshot().map { it.guid })
    }

    private fun offlineRepository() = PostRepository(
        postApi = OfflinePostApi(),
        cache = PostCache(),
        dispatchers = dispatchers,
        localStore = store,
    )

    /**
     * Every read fails the way a dead connection fails — except the feed page,
     * which returns [feedPage] when one is given, so an empty-but-successful
     * response can be exercised too.
     */
    private class OfflinePostApi(private val feedPage: List<Post>? = null) : NoopPostApi() {
        private fun offline(): Nothing = throw IOException("no connection")
        override suspend fun getAllPosts(): List<Post> = offline()
        override suspend fun getMyPosts(): List<Post> = offline()
        override suspend fun getPostPage(
            limit: Int,
            beforeDate: String?,
            beforeGuid: String?,
            tags: List<String>,
            groups: List<String>,
            query: String,
            following: Boolean,
            saved: Boolean,
        ): List<Post> = feedPage ?: offline()

        override suspend fun getFavoritePosts(): List<Post> = offline()
        override suspend fun removePost(post: Post) = offline()
        override suspend fun updatePost(post: Post) = offline()
        override suspend fun addPost(post: Post) = offline()
    }


    private companion object {
        const val USER = "AAA-AAA"
        val dispatchers = DispatcherProvider(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        )
    }
}
