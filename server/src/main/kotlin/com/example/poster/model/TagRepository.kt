package com.example.poster.model

interface TagRepository {
    fun allTags(): List<Tag>
    fun tagById(guid: String): Tag?
    fun tagsByName(query: String): List<Tag>
    fun addOrUpdateTag(tag: Tag)
    fun getTagsForPost(postGuid: String): List<Tag>
    fun addTagToPost(postGuid: String, tagGuid: String)
    fun removeTagFromPost(postGuid: String, tagGuid: String)
    fun removeAllTagsFromPost(postGuid: String)
}