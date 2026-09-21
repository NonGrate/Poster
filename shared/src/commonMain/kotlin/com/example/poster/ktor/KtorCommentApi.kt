package com.example.poster.ktor

import com.example.poster.model.Comment
import com.example.poster.model.CommentRequest
import com.example.poster.network.CommentApi
import com.example.poster.network.EmailNotVerified
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class KtorCommentApi(private val httpClient: HttpClient) : CommentApi {
    override suspend fun forPost(postGuid: String): List<Comment> =
        httpClient.get("posts/$postGuid/comments").bodyOr(emptyList())

    override suspend fun add(postGuid: String, text: String): Comment {
        val response = httpClient.post("posts/$postGuid/comments") {
            contentType(ContentType.Application.Json)
            setBody(CommentRequest(text))
        }
        if (response.status == HttpStatusCode.Forbidden) throw EmailNotVerified()
        response.failIfNotSuccess("Comment request")
        return response.body()
    }

    override suspend fun delete(postGuid: String, commentGuid: String) =
        httpClient.delete("posts/$postGuid/comments/$commentGuid").failIfNotSuccess("Comment delete")
}
