package com.example.poster.network

/** Who the signed-in person follows (feature.follows). */
interface FollowApi {
    /** User ids this person follows. */
    suspend fun following(): List<String>
    suspend fun follow(userId: String)
    suspend fun unfollow(userId: String)
}
