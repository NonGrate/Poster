package com.example.poster.cache

import com.example.poster.model.Post
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** The cached count is allowed to be stale. It is not allowed to be impossible. */
class PostCacheCountTest {

    private val post = Post(
        guid = "post-1",
        title = "A post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = 0,
        date = LocalDateTime.parse("2026-08-19T10:00"),
    )

    @Test
    fun aCountNeverGoesBelowZero() = runBlocking {
        val cache = PostCache()
        cache.setAllPosts(listOf(post))

        cache.removeFavorite(post.guid)

        assertEquals(0, cache.getPostLikeCounts()[post.guid])
    }

    @Test
    fun likingAndGivingUpLeavesTheCountWhereItStarted() = runBlocking {
        val cache = PostCache()
        cache.setAllPosts(listOf(post.copy(likes = 3)))

        cache.addFavorite(post.guid)
        cache.removeFavorite(post.guid)

        assertEquals(3, cache.getPostLikeCounts()[post.guid])
    }
}
