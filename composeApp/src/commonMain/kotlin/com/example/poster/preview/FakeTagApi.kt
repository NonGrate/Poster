package com.example.poster.preview

import com.example.poster.model.Tag
import com.example.poster.network.TagApi

class FakeTagApi : TagApi {
    // In-memory storage for tags
    private val tags = mutableListOf(Tag("1", "General"), Tag("2", "Urgent"), Tag("3", "Family"))

    override suspend fun getAllTags(): List<Tag> = tags
}
