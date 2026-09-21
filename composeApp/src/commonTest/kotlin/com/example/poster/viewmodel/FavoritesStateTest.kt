package com.example.poster.viewmodel

import com.example.poster.cache.PostCache
import com.example.poster.model.Post
import com.example.poster.network.PostApi
import com.example.poster.preview.FakePlatformDataStore
import com.example.poster.preview.FakePostApi
import com.example.poster.preview.FakeUserApi
import com.example.poster.repository.PostRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.util.AppPreferences
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The list, the ids and the counts are three views of one thing. They used to be
 * three flows written one after another, which is how they came apart.
 */
class FavoritesStateTest {

    private val post = Post(
        guid = "post-1",
        title = "A post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = 2,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

    @Test
    fun addingAFavoriteMovesListIdsAndCountTogether() {
        val after = FavoritesUiState().withFavorite(post, added = true)

        assertTrue(after.isFavorite(post.guid))
        assertEquals(setOf(post.guid), after.ids)
        assertEquals(listOf(post.guid), after.posts.map { it.guid })
        assertEquals(3, after.countFor(post))
    }

    @Test
    fun removingAFavoriteMovesListIdsAndCountTogether() {
        val added = FavoritesUiState().withFavorite(post, added = true)

        val after = added.withFavorite(post, added = false)

        assertFalse(after.isFavorite(post.guid))
        assertEquals(emptySet(), after.ids)
        assertEquals(emptyList(), after.posts)
        assertEquals(2, after.countFor(post))
    }

    @Test
    fun aCountNeverGoesBelowZero() {
        val state = FavoritesUiState(counts = mapOf(post.guid to 0))

        assertEquals(0, state.withFavorite(post, added = false).countFor(post))
    }

    /** A rejected write must leave no trace, in any of the three. */
    @Test
    fun aFailedToggleRollsBackEverything() = runBlocking {
        val dispatchers = DispatcherProvider(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        )
        val session = SessionRepository(
            userApi = FakeUserApi(),
            appPreferences = AppPreferences(FakePlatformDataStore()),
            dispatchers = dispatchers,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        val favorites = FavoritesViewModel(
            repository = PostRepository(
                postApi = RefusingPostApi(),
                cache = PostCache(),
                dispatchers = dispatchers,
            ),
            session = session,
            dispatchers = dispatchers,
        )
        session.logIn("AAA-AAA")
        val before = favorites.state.value

        favorites.toggleFavorite(post)

        val after = withTimeout(5_000) { favorites.state.first { it.error != null } }
        assertEquals(before.posts, after.posts)
        assertEquals(before.ids, after.ids)
        assertEquals(before.counts, after.counts)
    }

    private class RefusingPostApi : PostApi by FakePostApi() {
        override suspend fun addFavorite(userId: String, postId: String) =
            throw IllegalStateException("server said no")

        override suspend fun removeFavorite(userId: String, postId: String) =
            throw IllegalStateException("server said no")
    }
}
