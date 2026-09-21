package com.example.poster.ktor

import com.example.poster.network.FollowApi
import io.ktor.client.HttpClient

class KtorFollowApi(httpClient: HttpClient) : KtorIdSetApi(httpClient, "follows", "Follow"), FollowApi {
    override suspend fun following(): List<String> = list()
    override suspend fun follow(userId: String) = add(userId)
    override suspend fun unfollow(userId: String) = remove(userId)
}
