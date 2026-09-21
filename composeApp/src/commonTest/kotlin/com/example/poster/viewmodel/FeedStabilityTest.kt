package com.example.poster.viewmodel

import com.example.poster.cache.PostCache
import com.example.poster.model.Post
import com.example.poster.network.PostApi
import com.example.poster.preview.FakePlatformDataStore
import com.example.poster.preview.FakePostApi
import com.example.poster.preview.FakeUserApi
import com.example.poster.repository.SessionRepository
import com.example.poster.util.AppPreferences
import kotlinx.coroutines.CoroutineScope
import com.example.poster.repository.PostRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The feed must not rearrange itself under somebody who is reading it. New
 * content is offered; it lands when it is asked for.
 */
class FeedStabilityTest {

    private fun post(guid: String) = Post(
        guid = guid,
        title = guid,
        message = "message",
        author = "someone",
        group = null,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

    /** Serves a feed; everything else is the preview backend's do-nothing. */
    private class ServerStub(var posts: List<Post>) : PostApi by FakePostApi() {
        override suspend fun getAllPosts(): List<Post> = posts
    }

    /** Signed in, because a feed with nobody signed in is empty by design. */
    private fun signedIn(api: PostApi): PostsViewModel {
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
        val model = PostsViewModel(
            repository = PostRepository(
                postApi = api,
                cache = PostCache(),
                dispatchers = dispatchers,
            ),
            session = session,
            dispatchers = dispatchers,
        )
        runBlocking { session.logIn("AAA-AAA") }
        return model
    }

    @Test
    fun aPostYouAddAppearsWithoutBeingAskedFor() = runBlocking {
        val server = ServerStub(listOf(post("one")))
        val model = signedIn(server)
        withTimeout(5_000) { model.posts.first { it.isNotEmpty() } }

        server.posts = listOf(post("one"), post("mine"))
        model.addPost(post("mine"))

        // Your own change needs no offer: you already know the list changed.
        val shown = withTimeout(5_000) { model.posts.first { it.size == 2 } }
        assertTrue(shown.any { it.guid == "mine" })
        assertEquals(0, model.pendingCount.value, "your own post should not wait behind a button")
    }

    @Test
    fun aFailedAddIsReported() = runBlocking {
        val refusing = object : PostApi by ServerStub(emptyList()) {
            override suspend fun addPost(post: Post) = throw IllegalStateException("no")
        }
        val model = signedIn(refusing)

        model.addPost(post("doomed"))

        val error = withTimeout(5_000) { model.error.first { it != null } }
        assertEquals("Unable to add post", error?.message)
    }
}
