package com.example.poster.network

import com.example.poster.model.Tag

/**
 * Interface for tag-related operations.
 * This is used by the application to interact with the backend.
 */
interface TagApi {
    /**
     * Gets all tags.
     */
    suspend fun getAllTags(): List<Tag>

    /**
     * Gets a tag by its ID.
     */
    suspend fun getTagById(guid: String): Tag?

    /**
     * Searches for tags by name.
     */
    suspend fun searchTagsByName(query: String): List<Tag>

    /**
     * Adds or updates a tag.
     */
    suspend fun addOrUpdateTag(tag: Tag)

    /**
     * Removes a tag.
     */

    /**
     * Gets all tags for a post.
     */
    suspend fun getTagsForPost(postId: String): List<Tag>

    /**
     * Adds a tag to a post.
     */

    /**
     * Removes a tag from a post.
     */

    /**
     * Removes all tags from a post.
     */
}