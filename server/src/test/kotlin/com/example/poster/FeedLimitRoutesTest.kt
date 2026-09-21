package com.example.poster

import com.example.poster.config.Features
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import com.example.poster.model.AuthResponse
import com.example.poster.model.DEFAULT_FEED_LIMIT
import com.example.poster.model.MAX_FEED_LIMIT
import com.example.poster.model.Post
import com.example.poster.model.RegisterRequest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of the feed one request may carry.
 *
 * It used to be all of it. That is right for a family and wrong for a
 * neighbourhood: a phone would download years of posts to show the top of a
 * list. This is a cap, not paging — see Stage 17 for the difference and why the
 * rest is not built yet.
 */
class FeedLimitRoutesTest {

    /**
     * Page by page, until there are none left.
     *
     * A cap alone would have meant the oldest posts were unreachable, which
     * is not a smaller version of the feature — it is a different one, where
     * somebody's post from last month simply cannot be found.
     */
    @Test
    fun everyPostCanBeReachedOnePageAtATime() = withServer {
        val author = register("author@example.com")
        val written = 25
        repeat(written) { post(author, "Post $it", minute = it) }

        val seen = mutableListOf<Post>()
        var cursor: Pair<String, String>? = null
        repeat(10) {
            val page = feed(author, limit = 10, before = cursor)
            if (page.isEmpty()) return@repeat
            seen += page
            cursor = page.last().date.toString() to page.last().guid
        }

        assertEquals(written, seen.size, "paging did not reach every post")
        assertEquals(written, seen.map { it.guid }.distinct().size, "a post came back on two pages")
    }

    /** The end of the feed is an empty page, not a repeat of the last one. */
    @Test
    fun pagingPastTheEndReturnsNothing() = withServer {
        val author = register("author@example.com")
        repeat(3) { post(author, "Post $it", minute = it) }

        val page = feed(author, limit = 10)
        val past = feed(author, limit = 10, before = page.last().date.toString() to page.last().guid)

        assertTrue(past.isEmpty(), "asking past the end returned ${past.size} posts")
    }

    /** A cursor without both halves cannot resume, so it starts again. */
    @Test
    fun halfACursorIsNoCursor() = withServer {
        val author = register("author@example.com")
        repeat(5) { post(author, "Post $it", minute = it) }

        val response = client.get("/posts?beforeDate=2026-01-01T00:04") {
            bearerAuth(author.tokens.accessToken)
        }

        assertEquals(5, Json.decodeFromString<List<Post>>(response.bodyAsText()).size)
    }

    @Test
    fun theFeedIsCappedRatherThanUnbounded() = withServer {
        val author = register("author@example.com")
        repeat(DEFAULT_FEED_LIMIT + 20) { post(author, "Post $it", minute = it) }

        assertEquals(DEFAULT_FEED_LIMIT, feed(author).size)
    }

    /** Newest first, or a cap would keep the least useful ones. */
    @Test
    fun whatComesBackIsTheNewest() = withServer {
        val author = register("author@example.com")
        repeat(DEFAULT_FEED_LIMIT + 5) { post(author, "Post $it", minute = it) }

        val titles = feed(author).map { it.title }

        assertEquals("Post ${DEFAULT_FEED_LIMIT + 4}", titles.first(), "not the newest")
        assertTrue("Post 0" !in titles, "the oldest survived a cap of the newest")
    }

    @Test
    fun aCallerMayAskForFewer() = withServer {
        val author = register("author@example.com")
        repeat(10) { post(author, "Post $it", minute = it) }

        assertEquals(3, feed(author, limit = 3).size)
    }

    /** A limit anybody can raise is not a limit. */
    @Test
    fun askingForMoreThanTheCapGetsTheCap() = withServer {
        val author = register("author@example.com")
        repeat(MAX_FEED_LIMIT + 10) { post(author, "Post $it", minute = it) }

        assertEquals(MAX_FEED_LIMIT, feed(author, limit = 100_000).size)
    }

    @Test
    fun nonsenseIsTreatedAsNoAnswer() = withServer {
        val author = register("author@example.com")
        repeat(5) { post(author, "Post $it", minute = it) }

        assertEquals(5, feed(author, limitRaw = "banana").size, "a bad limit should fall back to the default")
        assertEquals(5, feed(author, limit = 0).size, "zero is not a request for nothing")
    }

    /**
     * A tag narrows the feed, and it does so page by page like the rest of it.
     *
     * Filtering on the device could only ever narrow what the device had
     * fetched, so a post older than the page carried its tag invisibly — the
     * chip was not even offered for it.
     */
    @Test
    fun aTagNarrowsTheFeedAcrossEveryPage() = withServer {
        val author = register("author@example.com")
        // Every third post is tagged, spread well past one page.
        repeat(60) { post(author, "Post $it", minute = it, tags = if (it % 3 == 0) listOf(TAG) else emptyList()) }

        val seen = mutableListOf<Post>()
        var cursor: Pair<String, String>? = null
        repeat(10) {
            val page = feed(author, limit = 5, before = cursor, tag = TAG)
            if (page.isEmpty()) return@repeat
            seen += page
            cursor = page.last().date.toString() to page.last().guid
        }

        assertEquals(20, seen.size, "paging a tag did not reach every tagged post")
        assertTrue(seen.all { TAG in it.tags }, "an untagged post came back from a tagged feed")
        assertEquals(seen.size, seen.map { it.guid }.distinct().size, "a post came back twice")
    }

    @Test
    fun noTagIsNoFilter() = withServer {
        val author = register("author@example.com")
        repeat(4) { post(author, "Post $it", minute = it, tags = if (it == 0) listOf(TAG) else emptyList()) }

        assertEquals(4, feed(author).size)
        assertEquals(4, feed(author, tag = "").size, "a blank tag should read as no filter")
    }

    @Test
    fun aTagNothingCarriesReturnsNothing() = withServer {
        val author = register("author@example.com")
        repeat(3) { post(author, "Post $it", minute = it, tags = listOf(TAG)) }

        assertEquals(emptyList(), feed(author, tag = "idea"))
    }

    /** The tag filter must not become a way around who may see what. */
    @Test
    fun aTaggedFeedStillObeysVisibility() = withServer {
        if (!Features.POST_VISIBILITY) return@withServer
        val author = register("author@example.com")
        val stranger = register("stranger@example.com")
        post(author, "Private one", minute = 0, tags = listOf(TAG), visibility = "private")
        post(author, "Public one", minute = 1, tags = listOf(TAG))

        assertEquals(listOf("Public one"), feed(stranger, tag = TAG).map { it.title })
    }

    private suspend fun ApplicationTestBuilder.feed(
        viewer: AuthResponse,
        limit: Int? = null,
        limitRaw: String? = null,
        before: Pair<String, String>? = null,
        tag: String? = null,
    ): List<Post> {
        val parts = buildList {
            if (limitRaw != null) add("limit=$limitRaw") else if (limit != null) add("limit=$limit")
            before?.let { add("beforeDate=${it.first}"); add("beforeGuid=${it.second}") }
            tag?.let { add("tag=$it") }
        }
        val query = if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
        val response = client.get("/posts$query") { bearerAuth(viewer.tokens.accessToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.post(
        author: AuthResponse,
        title: String,
        minute: Int,
        tags: List<String> = emptyList(),
        visibility: String = "public",
    ) {
        val post = Post(
            guid = title.replace(' ', '-'),
            title = title,
            message = "words",
            author = author.user.guid,
            group = null,
            // Spread across time so "newest" means something.
            date = LocalDateTime(2026, 1, 1, minute / 60, minute % 60),
            tags = tags,
            visibility = visibility,
        )
        assertEquals(
            HttpStatusCode.NoContent,
            client.post("/posts") {
                bearerAuth(author.tokens.accessToken)
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(post))
            }.status,
        )
    }

    private suspend fun ApplicationTestBuilder.register(email: String): AuthResponse {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(RegisterRequest("Author", "User", email, "password123")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        confirmAddress(email)
        return Json.decodeFromString(response.bodyAsText())
    }

    private companion object {
        /** One of the curated tags, so the server will actually link it. */
        const val TAG = "wellbeing"
    }

}
