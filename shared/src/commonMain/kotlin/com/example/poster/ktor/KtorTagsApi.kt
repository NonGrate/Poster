package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.example.poster.model.Tag
import com.example.poster.network.TagApi

/**
 * Implementation of TagApi that uses Ktor HTTP client to make requests to the server.
 */
class KtorTagsApi(private val httpClient: HttpClient) : TagApi {
    
    override suspend fun getAllTags(): List<Tag> {
        return httpClient.get("tags").body()
    }
    
    override suspend fun getTagById(guid: String): Tag? {
        // There's no direct endpoint for this, so we'll get all tags and filter
        val tags = getAllTags()
        return tags.find { it.guid == guid }
    }
    
    override suspend fun searchTagsByName(query: String): List<Tag> {
        return httpClient.get("tags/byName/$query").body()
    }
    
    override suspend fun addOrUpdateTag(tag: Tag) {
        httpClient.post("tags") {
            contentType(ContentType.Application.Json)
            setBody(tag)
        }
    }
    
    override suspend fun getTagsForPost(postId: String): List<Tag> {
        return httpClient.get("tags/forPost/$postId").body()
    }
    
    
    
}