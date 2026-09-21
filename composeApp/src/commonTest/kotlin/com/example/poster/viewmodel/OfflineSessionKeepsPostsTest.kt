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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What is on the device outlives a session the app cannot confirm.
 *
 * Emptying it is for signing out — a decision somebody made — and not for a
 * session that has not been restored yet, or one the server could not be
 * reached to confirm. Those look identical from here: no user. Treating them
 * the same is how opening the app on a train would have thrown away every
 * post somebody was liking, at exactly the moment the network could not
 * bring them back.
 */
class OfflineSessionKeepsPostsTest {

    private val post = Post(
        guid = "post-1",
        title = "A post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = 2,
        date = LocalDateTime.parse("2026-08-19T10:00"),
    )

    @Test
    fun aSessionThatWasNeverRestoredKeepsWhatIsOnTheDevice() = runBlocking {
        val cache = PostCache()
        cache.setAllPosts(listOf(post))
        val session = session()

        viewModel(cache, session)

        assertEquals(
            listOf(post.guid),
            cache.getAllPosts().map { it.guid },
            "an unconfirmed session emptied the device",
        )
    }

    /** Signing out still empties it: that is somebody's decision, not a failure. */
    @Test
    fun signingOutEmptiesTheDevice() = runBlocking {
        val cache = PostCache()
        cache.setAllPosts(listOf(post))
        val session = session()
        viewModel(cache, session)
        session.logIn(USER)
        cache.setAllPosts(listOf(post))

        session.logOut()

        assertEquals(emptyList(), cache.getAllPosts(), "signing out left the previous person's posts")
    }

    private fun viewModel(cache: PostCache, session: SessionRepository) = PostsViewModel(
        repository = PostRepository(
            postApi = OfflinePostApi(),
            cache = cache,
            dispatchers = dispatchers,
        ),
        session = session,
        dispatchers = dispatchers,
    )

    private fun session() = SessionRepository(
        userApi = FakeUserApi(),
        appPreferences = AppPreferences(FakePlatformDataStore()),
        dispatchers = dispatchers,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    /**
     * Nothing can be read, the way nothing can be read on a train. The writes
     * are left to the preview backend: this test never makes one, and a fake
     * that spells out every method buries the three lines that matter.
     */
    private class OfflinePostApi : PostApi by FakePostApi() {
        private fun offline(): Nothing = throw IOException("no connection")
        override suspend fun getAllPosts(): List<Post> = offline()
        override suspend fun getMyPosts(): List<Post> = offline()
        override suspend fun getFavoritePosts(): List<Post> = offline()
    }

    private companion object {
        const val USER = "AAA-AAA"
        val dispatchers = DispatcherProvider(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        )
    }
}
