package com.example.poster.repository

import com.example.poster.cache.PostCache
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
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
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
    private var previousPath: String? = null

    private val post = Post(
        guid = "post-1",
        title = "A post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = 5,
        date = LocalDateTime.parse("2026-08-19T10:00"),
    )

    @BeforeTest
    fun setUp() {
        previousPath = System.getProperty("poster.database")
        val file = Files.createTempFile("poster-rollback", ".db").toFile()
        file.delete()
        file.deleteOnExit()
        System.setProperty("poster.database", file.path)
        store = PostLocalStore(
            databaseManager = DatabaseManager(DatabaseDriverFactory()),
            dispatchers = dispatchers,
        )
    }

    @AfterTest
    fun tearDown() {
        previousPath?.let { System.setProperty("poster.database", it) }
            ?: System.clearProperty("poster.database")
    }

    @Test
    fun aRefusedPostLeavesTheCountAlone() = runBlocking {
        store.replaceAll(listOf(post))

        val result = repository().addFavorite(USER, post.guid)

        assertTrue(result.isFailure)
        assertEquals(5, store.snapshot().single().likes)
    }

    @Test
    fun aRefusedWithdrawalLeavesTheCountAlone() = runBlocking {
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
    fun aRefusedPostKeepsEverythingElseBeingLikedFor() = runBlocking {
        val other = post.copy(guid = "post-2")
        store.replaceAll(listOf(post, other))
        store.addFavorite(USER, other.guid)

        repository().addFavorite(USER, post.guid)

        assertEquals(listOf(other.guid), store.favorites(USER).first().map { it.guid })
    }

    private fun repository() = PostRepository(
        postApi = RefusingPostApi(),
        userApi = NoUserApi(),
        cache = PostCache(),
        dispatchers = dispatchers,
        localStore = store,
    )

    private class RefusingPostApi : PostApi {
        override suspend fun getAllPosts(): List<Post> = emptyList()
        override suspend fun getFavoritePosts(): List<Post> = emptyList()
        override suspend fun removePost(post: Post) = Unit
        override suspend fun updatePost(post: Post) = Unit
        override suspend fun addPost(post: Post) = Unit
        override suspend fun completePost(postId: String, message: String?) = Unit
        override suspend fun reopenPost(postId: String) = Unit
        override suspend fun isFavorite(userId: String, postId: String) = false
        override suspend fun addFavorite(userId: String, postId: String): Unit =
            throw IllegalStateException("server said no")

        override suspend fun removeFavorite(userId: String, postId: String): Unit =
            throw IllegalStateException("server said no")
    }

    /** Never asked anything: this test is about posts, not people. */
    private class NoUserApi : UserApi {
        override suspend fun getUserById(id: String): User? = null
        override suspend fun updateUser(user: User) = Unit
        override suspend fun logIn(user: String): User? = null
        override suspend fun authenticateUser(email: String, password: String): User? = null
        override suspend fun createUser(
            name: String,
            surname: String,
            email: String,
            password: String,
            groupCode: String?,
            languages: List<String>,
            defaultLanguage: String?,
            showName: Boolean,
        ): User = error("not used")

        override suspend fun verifyEmail(token: String): Boolean = false
        override suspend fun resendVerification() = Unit
        override suspend fun requestPasswordReset(email: String) = Unit
        override suspend fun resetPassword(token: String, newPassword: String): Boolean = false
        override suspend fun logout() = Unit
        override suspend fun currentUser(): User? = null
    }

    private companion object {
        const val USER = "AAA-AAA"
        val dispatchers = DispatcherProvider(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        )
    }
}
