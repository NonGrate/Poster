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
}