package com.example.poster.repository

import com.example.poster.model.Tag
import com.example.poster.network.TagApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Repository layer for tags.
 * Provides methods for managing tags and their relationships with posts.
 */
class TagRepository(
    private val tagApi: TagApi
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    
    // In-memory cache for tags
    private var tagsCache: List<Tag> = emptyList()
    private val postTagsCache = mutableMapOf<String, List<Tag>>()
    
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
    
    suspend fun getTagById(guid: String): Tag? {
        val cachedTag = tagsCache.find { it.guid == guid }
        if (cachedTag != null) return cachedTag
        return tagApi.getTagById(guid)
    }
    
    suspend fun searchTagsByName(query: String): List<Tag> {
        // We don't cache search results
        return tagApi.searchTagsByName(query)
    }
    
    suspend fun addOrUpdateTag(tag: Tag) {
        // Update the cache immediately for UI responsiveness
        tagsCache = tagsCache.filter { it.guid != tag.guid } + tag
        
        coroutineScope.launch {
            try {
                tagApi.addOrUpdateTag(tag)
                val tags = tagApi.getAllTags()
                tagsCache = tags
            } catch (e: Exception) {
                println("Error adding/updating tag: ${e.message}")
            }
        }
    }
    
    suspend fun getTagsForPost(postId: String): List<Tag> {
        val cachedTags = postTagsCache[postId]
        if (cachedTags != null) return cachedTags
        val tags = tagApi.getTagsForPost(postId)
        postTagsCache[postId] = tags
        return tags
    }
    
    
    
    
    fun clearCache() {
        tagsCache = emptyList()
        postTagsCache.clear()
    }
}
