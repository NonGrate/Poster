package com.example.poster.ktor

import com.example.poster.network.BookmarkApi
import io.ktor.client.HttpClient

class KtorBookmarkApi(httpClient: HttpClient) : KtorIdSetApi(httpClient, "bookmarks", "Save"), BookmarkApi {
    override suspend fun saved(): List<String> = list()
    override suspend fun save(postId: String) = add(postId)
    override suspend fun unsave(postId: String) = remove(postId)
}
