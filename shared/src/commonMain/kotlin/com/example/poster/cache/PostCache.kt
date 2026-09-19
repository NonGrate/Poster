package com.example.poster.cache

import com.example.poster.model.Post

/**
 * Cache for posts and favorites.
 * This class provides in-memory storage for posts and favorites to allow immediate UI updates
 * without waiting for database operations to complete.
 */
class PostCache {
    // Cache for all posts
    private var allPosts: List<Post> = emptyList()
    
    // Cache for favorite posts
    private var favoritePosts: List<Post> = emptyList()
    
    // Cache for favorite post IDs
    private var favoritePostIds: Set<String> = emptySet()
    
    // Cache for post like counts
    private var postLikeCounts: Map<String, Int> = emptyMap()
    
    /**
     * Gets all posts from the cache.
     */
    suspend fun getAllPosts(): List<Post> {
        return allPosts
    }
    
    /**
     * Sets all posts in the cache.
     */
    suspend fun setAllPosts(posts: List<Post>) {
        allPosts = posts
        // Update like counts
        postLikeCounts = posts.associate { it.guid to it.likes }
    }
    
    /**
     * Gets favorite posts from the cache.
     */
    suspend fun getFavoritePosts(): List<Post> {
        return favoritePosts
    }
    
    /**
     * Sets favorite posts in the cache.
     */
    suspend fun setFavoritePosts(posts: List<Post>) {
        favoritePosts = posts
        favoritePostIds = posts.map { it.guid }.toSet()
    }
    
    /**
     * Gets favorite post IDs from the cache.
     */
    suspend fun getFavoritePostIds(): Set<String> {
        return favoritePostIds
    }
    
    /**
     * Sets favorite post IDs in the cache.
     */
    suspend fun setFavoritePostIds(ids: Set<String>) {
        favoritePostIds = ids
    }
    
    /**
     * Gets post like counts from the cache.
     */
    suspend fun getPostLikeCounts(): Map<String, Int> {
        return postLikeCounts
    }
    
    /**
     * Sets post like counts in the cache.
     */
    suspend fun setPostLikeCounts(counts: Map<String, Int>) {
        postLikeCounts = counts
    }
    
    /**
     * Updates a post in the cache.
     */
    suspend fun updatePost(post: Post) {
        // Update in allPosts
        allPosts = allPosts.map { 
            if (it.guid == post.guid) post else it 
        }
        
        // Update in favoritePosts if it exists
        if (favoritePostIds.contains(post.guid)) {
            favoritePosts = favoritePosts.map { 
                if (it.guid == post.guid) post else it 
            }
        }
        
        // Update like count
        postLikeCounts = postLikeCounts + (post.guid to post.likes)
    }
    
    /**
     * Adds a post to favorites in the cache.
     */
    suspend fun addFavorite(postId: String) {
        favoritePostIds = favoritePostIds + postId
        
        // Update like count
        val post = allPosts.find { it.guid == postId }
        if (post != null) {
            val updatedPost = post.copy(likes = post.likes + 1)
            updatePost(updatedPost)
            
            // Add to favorite posts if not already there
            if (!favoritePosts.any { it.guid == postId }) {
                favoritePosts = favoritePosts + updatedPost
            }
        }
    }
    
    /**
     * Removes a post from favorites in the cache.
     */
    suspend fun removeFavorite(postId: String) {
        favoritePostIds = favoritePostIds - postId
        
        // Update like count
        val post = allPosts.find { it.guid == postId }
        if (post != null) {
            // Never below zero. The cached count can be a refresh behind, and
            // "-1 liking" is a worse answer than a count that is merely stale.
            // The database floors it the same way — see adjustPostLikes.
            val updatedPost = post.copy(likes = (post.likes - 1).coerceAtLeast(0))
            updatePost(updatedPost)
        }
        
        // Remove from favorite posts
        favoritePosts = favoritePosts.filter { it.guid != postId }
    }
    
    /**
     * Checks if a post is a favorite in the cache.
     */
    suspend fun isFavorite(postId: String): Boolean {
        return favoritePostIds.contains(postId)
    }
    
    /**
     * Gets the like count for a post from the cache.
     */
    suspend fun getLikeCount(postId: String): Int {
        return postLikeCounts[postId] ?: 0
    }
    
    /**
     * Clears the cache.
     */
    suspend fun clear() {
        allPosts = emptyList()
        favoritePosts = emptyList()
        favoritePostIds = emptySet()
        postLikeCounts = emptyMap()
    }
}