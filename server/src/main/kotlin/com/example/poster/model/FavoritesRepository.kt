package com.example.poster.model

interface FavoritesRepository {
    fun getUserFavoritePosts(userId: String): List<Post>
    fun isPostFavorite(userId: String, postId: String): Boolean
    /** True when this was a new like; false when it was already there. */
    fun addFavoritePost(userId: String, postId: String): Boolean
    fun removeFavoritePost(userId: String, postId: String): Boolean
    fun countPostFavorites(postId: String): Long

    /** Who liked a post: opted-in names (newest first) plus the total. */
    fun likers(postId: String): LikerList
}