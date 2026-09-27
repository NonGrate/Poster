package com.example.poster.repository

import com.example.poster.model.Tag
import com.example.poster.network.TagApi
import com.example.poster.util.AppPreferences

/**
 * Repository layer for tags.
 * Provides methods for managing tags and their relationships with posts.
 */
class TagRepository(
    private val tagApi: TagApi,
    private val appPreferences: AppPreferences,
) {
    // In-memory cache for tags
    private var tagsCache: List<Tag> = emptyList()

    /**
     * The last catalog saved on this device, without touching the network — so
     * the feed can label its chips on a cold start instead of showing raw ids
     * until the fetch below answers.
     */
    suspend fun cachedTags(): List<Tag> {
        if (tagsCache.isEmpty()) tagsCache = appPreferences.cachedTags()
        return tagsCache
    }

    suspend fun getAllTags(): List<Tag> {
        if (tagsCache.isNotEmpty()) return tagsCache
        val tags = tagApi.getAllTags()
        tagsCache = tags
        appPreferences.setCachedTags(tags)
        return tags
    }

    suspend fun refreshTags(): List<Tag> {
        val tags = tagApi.getAllTags()
        tagsCache = tags
        appPreferences.setCachedTags(tags)
        return tags
    }

    fun clearCache() {
        tagsCache = emptyList()
    }
}
