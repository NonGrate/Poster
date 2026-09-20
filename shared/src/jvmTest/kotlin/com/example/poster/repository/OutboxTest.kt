package com.example.poster.repository

import com.example.poster.cache.PostCache
import com.example.poster.config.Features
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

/** feature.offlineOutbox: a post written offline is kept, shown as yours, and sent when the server is back. */
class OutboxTest {
    private lateinit var store: PostLocalStore
    private val server = FlakyPostApi()

    private val post = Post(
        guid = "offline-1", title = "Written on a train", message = "m", author = USER,
        group = null, likes = 0, date = LocalDateTime.parse("2026-08-19T10:00"),
    )

    /** A database of its own per test, put back afterwards. */
    private fun withStore(block: suspend () -> Unit) = withTempDatabase {
        store = PostLocalStore(DatabaseManager(DatabaseDriverFactory()), dispatchers)
        runBlocking { block() }
    }

    @Test
    fun anOfflinePostWaitsInTheOutboxAndGoesOutOnTheNextRefresh() = withStore {
        if (!Features.OFFLINE_OUTBOX) return@withStore
        val repository = PostRepository(server, PostCache(), dispatchers, store)

        server.online = false
        assertTrue(repository.addPost(post).isSuccess, "offline, the add still succeeds locally")
        assertEquals(setOf(post.guid), store.unsent().first())
        assertEquals(listOf(post.guid), store.mine(USER).first().map { it.guid }, "it shows under My Posts")
        assertEquals(emptyList(), server.received)

        // An edit while still offline keeps it a single add with the newer words.
        assertTrue(repository.updatePost(post.copy(title = "Written on a train, edited")).isSuccess)
        assertEquals(1, store.queued().size)
        assertEquals(PostLocalStore.Outbox.ADD, store.queued().single().first)

        server.online = true
        repository.refreshPosts(USER)
        assertEquals(listOf("add:Written on a train, edited"), server.received)
        assertEquals(emptySet(), store.unsent().first())
    }

    /** The other half of the queue: an edit to a post the server already has. */
    @Test
    fun anOfflineEditOfASentPostGoesOutAsAnUpdate() = withStore {
        if (!Features.OFFLINE_OUTBOX) return@withStore
        val repository = PostRepository(server, PostCache(), dispatchers, store)

        assertTrue(repository.addPost(post).isSuccess)
        assertEquals(listOf("add:Written on a train"), server.received)

        // The connection goes while editing something already sent.
        server.online = false
        assertTrue(repository.updatePost(post.copy(title = "Edited on the way back")).isSuccess)
        assertEquals(PostLocalStore.Outbox.UPDATE, store.queued().single().first)

        server.online = true
        repository.refreshPosts(USER)
        assertEquals(
            listOf("add:Written on a train", "update:Edited on the way back"),
            server.received,
            "the edit went out as a second post rather than as an edit",
        )
        assertEquals(emptySet(), store.unsent().first())
    }

    @Test
    fun aRefusalThatIsNotTheConnectionIsNotQueued() = withStore {
        if (!Features.OFFLINE_OUTBOX) return@withStore
        val repository = PostRepository(server, PostCache(), dispatchers, store)
        server.refuse = true
        assertTrue(repository.addPost(post).isFailure)
        assertEquals(emptySet(), store.unsent().first())
    }

    /** Offline throws IOException; refusing throws anything else; online records what arrived. */
    private class FlakyPostApi : NoopPostApi() {
        var online = true
        var refuse = false
        val received = mutableListOf<String>()
        private fun gate() { if (refuse) error("refused"); if (!online) throw IOException("no connection") }
        override suspend fun getAllPosts(): List<Post> { gate(); return emptyList() }
        override suspend fun addPost(post: Post) { gate(); received += "add:${post.title}" }
        override suspend fun updatePost(post: Post) { gate(); received += "update:${post.title}" }
        override suspend fun removePost(post: Post) = gate()
        override suspend fun getFavoritePosts(): List<Post> { gate(); return emptyList() }
        override suspend fun completePost(postId: String, message: String?) = gate()
        override suspend fun reopenPost(postId: String) = gate()
        override suspend fun addFavorite(userId: String, postId: String) = gate()
        override suspend fun removeFavorite(userId: String, postId: String) = gate()
    }


    private companion object {
        const val USER = "AAA-AAA"
        val dispatchers = DispatcherProvider(main = Dispatchers.Unconfined, io = Dispatchers.Unconfined)
    }
}
