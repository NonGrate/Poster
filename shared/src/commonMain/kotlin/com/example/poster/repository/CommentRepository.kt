package com.example.poster.repository

import com.example.poster.model.Comment
import com.example.poster.network.CommentApi
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.withContext

/** Comments are read from the server each time a post is opened; nothing is cached on the device. */
class CommentRepository(
    private val api: CommentApi,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun forPost(postGuid: String): Result<List<Comment>> =
        withContext(dispatchers.io) { runCatching { api.forPost(postGuid) } }

    suspend fun add(postGuid: String, text: String): Result<Comment> =
        withContext(dispatchers.io) { runCatching { api.add(postGuid, text.trim()) } }

    suspend fun delete(postGuid: String, commentGuid: String): Result<Unit> =
        withContext(dispatchers.io) { runCatching { api.delete(postGuid, commentGuid) } }
}
