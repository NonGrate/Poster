package com.example.poster.preview

import com.example.poster.model.Tag
import com.example.poster.network.TagApi

class FakeTagApi : TagApi {
    // In-memory storage for tags
    private val tags = mutableListOf(Tag("1", "General"), Tag("2", "Urgent"), Tag("3", "Family"))

    // In-memory storage for post-tag relationships
    private val postTags = tags.take(2)

    override suspend fun getAllTags(): List<Tag> = tags

    override suspend fun getTagById(guid: String): Tag? = tags.find { it.guid == guid }

    override suspend fun searchTagsByName(query: String): List<Tag> = tags

    override suspend fun addOrUpdateTag(tag: Tag) {}


    override suspend fun getTagsForPost(postId: String): List<Tag> = postTags



}
