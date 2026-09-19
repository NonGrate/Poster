package com.example.poster.viewmodel

import com.example.poster.cache.PostCache
import com.example.poster.model.Post
import com.example.poster.network.PostApi
import com.example.poster.preview.FakeUserApi
import com.example.poster.repository.PostRepository
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A post from the second page has liked-for-able like any other.
 *
 * The feed used to arrive whole, so everything on screen was also in the cache
 * that answers "how many people have liked this". Paging broke that
 * assumption quietly: the older page went to the database, the screen showed
 * it, and the cache had never heard of it — so liking one of those left
 * the count on the old number.
 */
class OlderPageFavoritesTest {

    private val newest = post(guid = "post-new", likes = 2, date = "2026-08-19T10:00")
    private val older = post(guid = "post-old", likes = 5, date = "2026-08-01T10:00")

    @Test
    fun likingForAPostFromAnOlderPageCountsIt() = runBlocking {
        val repository = repository(PagedPostApi(first = listOf(newest), second = listOf(older)))
        repository.refreshPosts().getOrThrow()
        repository.loadOlderPosts(newest.date.toString(), newest.guid).getOrThrow()

        val snapshot = repository.addFavorite("AAA-AAA", older.guid).getOrThrow()

        assertEquals(6, snapshot.favoriteCounts[older.guid])
        assertTrue(snapshot.favoritePosts.any { it.guid == older.guid })
    }

    /** And the first page keeps working, so the fix is not a swap of one bug for another. */
    @Test
    fun likingForAPostFromTheFirstPageStillCountsIt() = runBlocking {
        val repository = repository(PagedPostApi(first = listOf(newest), second = listOf(older)))
        repository.refreshPosts().getOrThrow()
        repository.loadOlderPosts(newest.date.toString(), newest.guid).getOrThrow()

        val snapshot = repository.addFavorite("AAA-AAA", newest.guid).getOrThrow()

        assertEquals(3, snapshot.favoriteCounts[newest.guid])
    }

    /** Paging must not lose the top of the feed from the cache either. */
    @Test
    fun anOlderPageIsAddedToTheCacheRatherThanReplacingIt() = runBlocking {
        val cache = PostCache()
        val repository = repository(PagedPostApi(listOf(newest), listOf(older)), cache)
        repository.refreshPosts().getOrThrow()

        repository.loadOlderPosts(newest.date.toString(), newest.guid).getOrThrow()

        assertEquals(
            listOf(newest.guid, older.guid).sorted(),
            cache.getAllPosts().map { it.guid }.sorted(),
        )
    }

    /** Asking twice must not double the page, or the counts double with it. */
    @Test
    fun theSamePageArrivingTwiceIsStillOnePost() = runBlocking {
        val cache = PostCache()
        val repository = repository(PagedPostApi(listOf(newest), listOf(older)), cache)
        repository.refreshPosts().getOrThrow()

        repository.loadOlderPosts(newest.date.toString(), newest.guid).getOrThrow()
        repository.loadOlderPosts(newest.date.toString(), newest.guid).getOrThrow()

        assertEquals(1, cache.getAllPosts().count { it.guid == older.guid })
    }

    private fun repository(api: PostApi, cache: PostCache = PostCache()) = PostRepository(
        postApi = api,
        userApi = FakeUserApi(),
        cache = cache,
        dispatchers = DispatcherProvider(main = Dispatchers.Unconfined, io = Dispatchers.Unconfined),
    )

    private fun post(guid: String, likes: Int, date: String) = Post(
        guid = guid,
        title = "A post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = likes,
        date = LocalDateTime.parse(date),
    )

    /** Serves the first page with no cursor and the second page with one. */
    private class PagedPostApi(
        private val first: List<Post>,
        private val second: List<Post>,
    ) : PostApi {
        override suspend fun getPostPage(
            limit: Int,
            beforeDate: String?,
            beforeGuid: String?,
            tags: List<String>,
            groups: List<String>,
        query: String,
        following: Boolean,
        ): List<Post> = if (beforeDate == null) first else second

        override suspend fun getAllPosts(): List<Post> = first + second
        override suspend fun getFavoritePosts(): List<Post> = emptyList()
        override suspend fun removePost(post: Post) = Unit
        override suspend fun updatePost(post: Post) = Unit
        override suspend fun addPost(post: Post) = Unit
        override suspend fun completePost(postId: String, message: String?) = Unit
        override suspend fun reopenPost(postId: String) = Unit
        override suspend fun isFavorite(userId: String, postId: String) = false
        override suspend fun addFavorite(userId: String, postId: String) = Unit
        override suspend fun removeFavorite(userId: String, postId: String) = Unit
    }
}
