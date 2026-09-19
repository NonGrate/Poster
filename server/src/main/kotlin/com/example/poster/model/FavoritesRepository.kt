package com.example.poster.model

interface FavoritesRepository {
    fun getUserFavoritePosts(userId: String): List<Post>
    fun isPostFavorite(userId: String, postId: String): Boolean
    fun addFavoritePost(userId: String, postId: String)
    fun removeFavoritePost(userId: String, postId: String): Boolean
    fun getUsersWhoFavoritedPost(postId: String): List<String>
    fun countPostFavorites(postId: String): Long

    /** Who liked a post: opted-in names (newest first) plus the total. */
    fun likers(postId: String): LikerList
}