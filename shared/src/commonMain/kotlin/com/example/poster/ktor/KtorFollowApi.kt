package com.example.poster.ktor

import com.example.poster.network.FollowApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.isSuccess

class KtorFollowApi(private val httpClient: HttpClient) : FollowApi {
    override suspend fun following(): List<String> {
        val response = httpClient.get("follows")
        return if (response.status.isSuccess()) response.body() else emptyList()
    }

    override suspend fun follow(userId: String) {
        val response = httpClient.post("follows/$userId")
        check(response.status.isSuccess()) { "Follow failed: ${response.status.value}" }
    }

    override suspend fun unfollow(userId: String) {
        val response = httpClient.delete("follows/$userId")
        check(response.status.isSuccess()) { "Unfollow failed: ${response.status.value}" }
    }
}
