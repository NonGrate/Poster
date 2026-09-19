package com.example.poster.network

import com.example.poster.model.Comment

interface CommentApi {
    /** The visible comments on a post, oldest first. Empty when the post cannot be seen. */
    suspend fun forPost(postGuid: String): List<Comment>

    /** Adds a comment and returns it as stored, or throws (the caller shows the error). */
    suspend fun add(postGuid: String, text: String): Comment

    /** Removes a comment; the author or the post's author may. */
    suspend fun delete(postGuid: String, commentGuid: String)
}
