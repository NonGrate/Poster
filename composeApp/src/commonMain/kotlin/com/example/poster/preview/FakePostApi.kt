package com.example.poster.preview

import com.example.poster.model.Post
import com.example.poster.network.PostApi

class FakePostApi : PostApi {
    // Current user ID - in a real app, this would come from a user session
    private var currentUserId: String? = null

    // In-memory storage for favorites
    private val favorites = mutableMapOf<String, MutableSet<String>>()

    /**
     * Sets the current user ID for this API instance.
     * This should be called when a user logs in.
     */
    fun setCurrentUser(userId: String?) {
        currentUserId = userId
    }

    override suspend fun getAllPosts(): List<Post> = emptyList()

    override suspend fun removePost(post: Post) {}

    override suspend fun updatePost(post: Post) {}

    override suspend fun addPost(post: Post) {}

    override suspend fun completePost(postId: String, message: String?) = Unit

    override suspend fun reopenPost(postId: String) = Unit

    override suspend fun getFavoritePosts(): List<Post> = emptyList()

    override suspend fun addFavorite(userId: String, postId: String) {
        favorites.getOrPut(userId) { mutableSetOf() }.add(postId)
    }

    override suspend fun removeFavorite(userId: String, postId: String) {
        favorites[userId]?.remove(postId)
    }
}
