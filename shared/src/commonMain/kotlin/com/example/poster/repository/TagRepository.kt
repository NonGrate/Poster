package com.example.poster.repository

import com.example.poster.model.Tag
import com.example.poster.network.TagApi

/**
 * Repository layer for tags.
 * Provides methods for managing tags and their relationships with posts.
 */
class TagRepository(
    private val tagApi: TagApi
) {
    // In-memory cache for tags
    private var tagsCache: List<Tag> = emptyList()
    
    suspend fun getAllTags(): List<Tag> {
        if (tagsCache.isNotEmpty()) return tagsCache
        val tags = tagApi.getAllTags()
        tagsCache = tags
        return tags
    }

    suspend fun refreshTags(): List<Tag> {
        val tags = tagApi.getAllTags()
        tagsCache = tags
        return tags
    }

    fun clearCache() {
        tagsCache = emptyList()
    }
}
