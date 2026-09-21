package com.example.poster.network

/** Posts the signed-in person saved for later (feature.bookmarks). */
interface BookmarkApi {
    suspend fun saved(): List<String>
    suspend fun save(postId: String)
    suspend fun unsave(postId: String)
}
