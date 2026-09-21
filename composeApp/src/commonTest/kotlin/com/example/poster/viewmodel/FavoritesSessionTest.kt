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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Favorites belong to whoever is signed in, so signing out has to empty them —
 * the next person to open the app must not see the last one's list. This used to
 * work through a direct reference to AccountViewModel, which no test could
 * stand in for.
 */
class FavoritesSessionTest {

    private val favorite = Post(
        guid = "post-1",
        title = "A post",
        message = "message",
        author = "AAA-AAA",
        group = null,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

    /** Counts the one call this test is about; the rest is the preview backend. */
    private class StubPostApi(private val favorites: List<Post>) : PostApi by FakePostApi() {
        var favoriteLoads = 0
            private set

        override suspend fun getFavoritePosts(): List<Post> {
            favoriteLoads++
            return favorites
        }
    }

    private fun fixture(): Triple<SessionRepository, FavoritesViewModel, StubPostApi> {
        val dispatchers = DispatcherProvider(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        )
        val postApi = StubPostApi(listOf(favorite))
        val repository = PostRepository(
            postApi = postApi,
            cache = PostCache(),
            dispatchers = dispatchers,
        )
        val session = SessionRepository(
            userApi = FakeUserApi(),
            appPreferences = AppPreferences(FakePlatformDataStore()),
            dispatchers = dispatchers,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        return Triple(session, FavoritesViewModel(repository, session, dispatchers), postApi)
    }

    /**
     * The load is a reaction to the session changing, and AppPreferences does
     * its first read on its own dispatcher, so the state is awaited rather than
     * sampled — sampling passed on iOS and raced on a device.
     */
    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T =
        withTimeout(5_000) { first(predicate) }

    @Test
    fun signingInLoadsFavorites() = runBlocking {
        val (session, favorites, postApi) = fixture()
        assertEquals(0, postApi.favoriteLoads, "nothing should load before anyone signs in")

        session.logIn("AAA-AAA")

        assertEquals(setOf("post-1"), favorites.state.await { it.ids.isNotEmpty() }.ids)
        assertTrue(postApi.favoriteLoads > 0, "signing in should load favorites")
    }

    @Test
    fun signingOutClearsFavorites() = runBlocking {
        val (session, favorites, _) = fixture()
        session.logIn("AAA-AAA")
        favorites.state.await { it.ids.isNotEmpty() }

        session.logOut()

        val cleared = favorites.state.await { it.posts.isEmpty() }
        assertEquals(emptySet(), cleared.ids)
        assertEquals(emptyList(), cleared.posts)
        assertEquals(emptyMap(), cleared.counts)
    }
}
