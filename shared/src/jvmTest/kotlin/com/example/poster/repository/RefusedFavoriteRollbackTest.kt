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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A write the server refuses must leave the count where it started.
 *
 * The device counts itself the moment somebody taps, because waiting for the
 * server to answer before showing it makes the tap feel broken. The price is
 * that a refused write has to be taken back off — otherwise the number stays a
 * person too high until the next refresh, which is exactly the sort of quiet
 * wrongness nobody reports.
 */
class RefusedFavoriteRollbackTest {

    private lateinit var store: PostLocalStore

    private val post = Post(
        guid = "post-1",
        title = "A post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = 5,
        date = LocalDateTime.parse("2026-08-19T10:00"),
    )

    /** A database of its own per test, put back afterwards. */
    private fun withStore(block: suspend () -> Unit) = withTempDatabase {
        store = PostLocalStore(
            databaseManager = DatabaseManager(DatabaseDriverFactory()),
            dispatchers = dispatchers,
        )
        runBlocking { block() }
    }

    @Test
    fun aRefusedPostLeavesTheCountAlone() = withStore {
        store.replaceAll(listOf(post))

        val result = repository().addFavorite(USER, post.guid)

        assertTrue(result.isFailure)
        assertEquals(5, store.snapshot().single().likes)
    }

    @Test
    fun aRefusedWithdrawalLeavesTheCountAlone() = withStore {
        store.replaceAll(listOf(post))
        store.addFavorite(USER, post.guid)

        val result = repository().removeFavorite(USER, post.guid)

        assertTrue(result.isFailure)
        assertEquals(6, store.snapshot().single().likes)
    }

    /**
     * The rollback used to rewrite the whole table from the in-memory cache. On
     * a device that reads from disk that cache can be empty, and then one
     * refused tap took away everything the person was liking.
     */
    @Test
    fun aRefusedPostKeepsEverythingElseBeingLikedFor() = withStore {
        val other = post.copy(guid = "post-2")
        store.replaceAll(listOf(post, other))
        store.addFavorite(USER, other.guid)

        repository().addFavorite(USER, post.guid)

        assertEquals(listOf(other.guid), store.favorites(USER).first().map { it.guid })
    }

    private fun repository() = PostRepository(
        postApi = RefusingPostApi(),
        cache = PostCache(),
        dispatchers = dispatchers,
        localStore = store,
    )

    private class RefusingPostApi : NoopPostApi() {
        override suspend fun addFavorite(userId: String, postId: String): Unit =
            throw IllegalStateException("server said no")

        override suspend fun removeFavorite(userId: String, postId: String): Unit =
            throw IllegalStateException("server said no")
    }

    private companion object {
        const val USER = "AAA-AAA"
        val dispatchers = DispatcherProvider(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        )
    }
}
