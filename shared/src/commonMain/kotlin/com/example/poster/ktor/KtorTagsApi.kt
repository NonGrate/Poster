package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import com.example.poster.model.Tag
import com.example.poster.network.TagApi

/**
 * Implementation of TagApi that uses Ktor HTTP client to make requests to the server.
 */
class KtorTagsApi(private val httpClient: HttpClient) : TagApi {
    override suspend fun getAllTags(): List<Tag> = httpClient.get("tags").body()
}