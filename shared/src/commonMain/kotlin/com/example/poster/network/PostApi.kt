package com.example.poster.network

import com.example.poster.domain.validation.ImageRules
import com.example.poster.model.Post
import com.example.poster.model.LikerList

interface PostApi {
    suspend fun getAllPosts(): List<Post>

    /**
     * The page after [beforeDate]/[beforeGuid], or the first when they are null.
     *
     * Defaulted so the offline backend and the previews, which have no paging
     * and no need of it, are unaffected.
     */
    suspend fun getPostPage(
        limit: Int,
        beforeDate: String? = null,
        beforeGuid: String? = null,
        tags: List<String> = emptyList(),
        groups: List<String> = emptyList(),
        /** Free text over title and message; blank = none. */
        query: String = "",
        /** Only authors the reader follows (feature.follows). */
        following: Boolean = false,
        /** Only posts the reader saved (feature.bookmarks). */
        saved: Boolean = false,
    ): List<Post> = getAllPosts()

    /**
     * Tells the server somebody thinks this post should not be here.
     *
     * Never fails loudly: a report that could not be sent is worth retrying,
     * not worth interrupting somebody who has just seen something upsetting.
     */
    suspend fun reportPost(postId: String, reason: String?) = Unit

    suspend fun removePost(post: Post)

    suspend fun updatePost(post: Post)

    suspend fun addPost(post: Post)

    /**
     * Uploads an image and returns its id, to be set as [Post.image] on the post
     * saved next. `extension` is what [ImageRules.extensionOf] found.
     */
    suspend fun uploadImage(bytes: ByteArray, extension: String): String = error("images are not supported by this client")

    /** The bytes of a post image this account may see, or null when it cannot be fetched. */
    suspend fun fetchImage(id: String): ByteArray? = null

    /**
     * Gets all posts that are favorites for the current user.
     * The current user is determined by the implementation.
     */
    suspend fun getFavoritePosts(): List<Post>

    /** Author-only: mark a post concluded, optionally saying how it went. */
    suspend fun completePost(postId: String, message: String?)

    /** Author-only: undo a completion. */
    suspend fun reopenPost(postId: String)

    /**
     * A public web link for a post, or null. The server mints/reuses an opaque
     * token; the returned URL opens the public web page. Public posts only —
     * the server refuses the rest. Defaulted to null so offline/preview backends
     * need not implement it.
     */
    suspend fun shareLink(postId: String): String? = null

    /**
     * A public post resolved from a share token, or null — the in-app open for
     * a `poster://post/{token}` deep link. Public-only. Defaulted to null.
     */
    suspend fun postByShareToken(token: String): Post? = null

    /**
     * Adds a post to a user's favorites.
     */
    suspend fun addFavorite(userId: String, postId: String)

    /**
     * Removes a post from a user's favorites.
     */
    suspend fun removeFavorite(userId: String, postId: String)

    /**
     * Checks if a post is a favorite for a user.
     */
    /**
     * Everything the signed-in person wrote, however old.
     *
     * Defaulted to the whole feed so the offline and preview backends, where
     * "everything" is a handful of posts in memory, need not implement it.
     */
    suspend fun getMyPosts(): List<Post> = getAllPosts()

    suspend fun isFavorite(userId: String, postId: String): Boolean

    /**
     * Who liked a post — the opted-in names (newest first) and the
     * total count — for the detail screen. Defaulted to empty so offline and
     * preview backends need not implement it.
     */
    suspend fun likers(postId: String): LikerList = LikerList()
}
