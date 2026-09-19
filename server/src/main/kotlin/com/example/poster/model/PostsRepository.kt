package com.example.poster.model

/**
 * Where a page ended: the date and guid of its last post.
 *
 * Both, because two posts written in the same minute are an ambiguous place
 * to resume from — the pair is a position, either alone is a guess.
 */
data class FeedCursor(val date: String, val guid: String)

/** How many posts a single request may return. */
const val DEFAULT_FEED_LIMIT = 100
const val MAX_FEED_LIMIT = 200

interface PostsRepository {
    fun allPosts(): List<Post>
    /**
     * One page of what this viewer may see, newest first.
     *
     * [before] is where the previous page ended — null for the first. A cursor
     * rather than an offset because posts arrive while somebody reads, and an
     * offset would then skip one or repeat one without saying so.
     *
     * [tags] narrows it to posts carrying any of them. Done here rather than
     * on the device because the device only holds the pages it has fetched: a tag
     * on anything older could not be found by filtering what is loaded.
     */
    fun visiblePosts(
        viewer: String?,
        languages: List<String> = Language.ALL,
        limit: Int = DEFAULT_FEED_LIMIT,
        before: FeedCursor? = null,
        tags: List<String> = emptyList(),
        groups: List<String> = emptyList(),
        /** Free text over title and message; blank = no search. */
        query: String = "",
    ): List<Post>
    fun postById(guid: String): Post?

    /**
     * One post, if this viewer may see it. Null covers both "not there" and
     * "not yours to see": which of the two it is, is itself private.
     *
     * Separate from [visiblePosts] because that returns a page, and a post
     * older than the page is not missing — it is just further down.
     */
    fun visiblePostById(viewer: String?, guid: String): Post?

    /**
     * Everything one person wrote, newest first, however old.
     *
     * Not a page. The feed is one because nobody reads all of it; a person's
     * own posts are a list they expect to see the whole of.
     */
    fun postsByAuthor(author: String): List<Post>
    fun addOrUpdatePost(post: Post)

    /** Every image id some post (hidden ones included) still points at. */
    fun imagesInUse(): List<String>

    /** The post carrying this image, hidden or not, or null for an image no post has yet. */
    fun postByImage(id: String): Post?
    fun removePost(guid: String): Boolean

    /**
     * A public post by its opaque share token, or null. The read path for the
     * web share page and the in-app "open shared link": the query enforces
     * public-only, so a token never surfaces a group or private post.
     */
    fun postByShareToken(token: String): Post?

    /**
     * The post's share token, minting and storing one on first use. One token
     * per post, reused thereafter. [newToken] supplies a collision-free
     * candidate when a new one is needed.
     */
    fun ensureShareToken(guid: String, newToken: () -> String): String
}