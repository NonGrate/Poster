package com.example.poster.ktor

import com.example.poster.network.BookmarkApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.isSuccess

class KtorBookmarkApi(private val httpClient: HttpClient) : BookmarkApi {
    override suspend fun saved(): List<String> {
        val response = httpClient.get("bookmarks")
        return if (response.status.isSuccess()) response.body() else emptyList()
    }

    override suspend fun save(postId: String) {
        val response = httpClient.post("bookmarks/$postId")
        check(response.status.isSuccess()) { "Save failed: ${response.status.value}" }
    }

    override suspend fun unsave(postId: String) {
        val response = httpClient.delete("bookmarks/$postId")
        check(response.status.isSuccess()) { "Unsave failed: ${response.status.value}" }
    }
}
