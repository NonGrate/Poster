package com.example.poster.repository

import com.example.poster.cache.PostCache
import com.example.poster.config.Features
import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
import com.example.poster.model.Post
import com.example.poster.model.User
import com.example.poster.network.PostApi
import com.example.poster.network.UserApi
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** feature.offlineOutbox: a post written offline is kept, shown as yours, and sent when the server is back. */
class OutboxTest {
    private lateinit var store: PostLocalStore
    private var previousPath: String? = null
    private val server = FlakyPostApi()

    private val post = Post(
        guid = "offline-1", title = "Written on a train", message = "m", author = USER,
        group = null, likes = 0, date = LocalDateTime.parse("2026-08-19T10:00"),
    )

    @BeforeTest
    fun setUp() {
        previousPath = System.getProperty("poster.database")
        val file = Files.createTempFile("poster-outbox", ".db").toFile()
        file.delete(); file.deleteOnExit()
        System.setProperty("poster.database", file.path)
        store = PostLocalStore(DatabaseManager(DatabaseDriverFactory()), dispatchers)
    }

    @AfterTest
    fun tearDown() {
        previousPath?.let { System.setProperty("poster.database", it) } ?: System.clearProperty("poster.database")
    }

    @Test
    fun anOfflinePostWaitsInTheOutboxAndGoesOutOnTheNextRefresh() = runBlocking {
        if (!Features.OFFLINE_OUTBOX) return@runBlocking
        val repository = PostRepository(server, NoUserApi(), PostCache(), dispatchers, store)

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

    @Test
    fun aRefusalThatIsNotTheConnectionIsNotQueued() = runBlocking {
        if (!Features.OFFLINE_OUTBOX) return@runBlocking
        val repository = PostRepository(server, NoUserApi(), PostCache(), dispatchers, store)
        server.refuse = true
        assertTrue(repository.addPost(post).isFailure)
        assertEquals(emptySet(), store.unsent().first())
    }

    /** Offline throws IOException; refusing throws anything else; online records what arrived. */
    private class FlakyPostApi : PostApi {
        var online = true
        var refuse = false
        val received = mutableListOf<String>()
        private fun gate() { if (refuse) error("refused"); if (!online) throw IOException("no connection") }
        override suspend fun getAllPosts(): List<Post> { gate(); return emptyList() }
        override suspend fun getPostPage(limit: Int, beforeDate: String?, beforeGuid: String?, tags: List<String>, groups: List<String>, query: String, following: Boolean, saved: Boolean): List<Post> { gate(); return emptyList() }
        override suspend fun addPost(post: Post) { gate(); received += "add:${post.title}" }
        override suspend fun updatePost(post: Post) { gate(); received += "update:${post.title}" }
        override suspend fun removePost(post: Post) = gate()
        override suspend fun getFavoritePosts(): List<Post> { gate(); return emptyList() }
        override suspend fun completePost(postId: String, message: String?) = gate()
        override suspend fun reopenPost(postId: String) = gate()
        override suspend fun isFavorite(userId: String, postId: String): Boolean { gate(); return false }
        override suspend fun addFavorite(userId: String, postId: String) = gate()
        override suspend fun removeFavorite(userId: String, postId: String) = gate()
    }

    private class NoUserApi : UserApi {
        override suspend fun getUserById(id: String): User? = null
        override suspend fun updateUser(user: User) = Unit
        override suspend fun logIn(user: String): User? = null
        override suspend fun currentUser(): User? = null
        override suspend fun authenticateUser(email: String, password: String): User? = null
        override suspend fun createUser(name: String, surname: String, email: String, password: String, groupCode: String?, languages: List<String>, defaultLanguage: String?, showName: Boolean): User = error("not used")
        override suspend fun verifyEmail(token: String): Boolean = false
        override suspend fun resendVerification() = Unit
        override suspend fun requestPasswordReset(email: String) = Unit
        override suspend fun resetPassword(token: String, newPassword: String): Boolean = false
        override suspend fun logout() = Unit
    }

    private companion object {
        const val USER = "AAA-AAA"
        val dispatchers = DispatcherProvider(main = Dispatchers.Unconfined, io = Dispatchers.Unconfined)
    }
}
