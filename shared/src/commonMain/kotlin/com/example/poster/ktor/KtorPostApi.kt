package com.example.poster.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import com.example.poster.model.UploadResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.example.poster.model.CompletePostRequest
import com.example.poster.model.ApiError
import com.example.poster.model.Post
import com.example.poster.model.LikerList
import com.example.poster.model.ReportRequest
import com.example.poster.network.EmailNotVerified
import com.example.poster.invite.AppLink
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import com.example.poster.network.PostApi

open class KtorPostApi(private val httpClient: HttpClient) : PostApi {
    override suspend fun getPostPage(
        limit: Int,
        beforeDate: String?,
        beforeGuid: String?,
        tags: List<String>,
        groups: List<String>,
        query: String,
        following: Boolean,
    ): List<Post> = httpClient.get("posts") {
        parameter("limit", limit)
        if (following) parameter("following", true)
        if (query.isNotBlank()) parameter("q", query.trim())
        // Both halves or neither: the server treats half a cursor as none, and
        // sending one half would quietly restart the feed from the top.
        if (beforeDate != null && beforeGuid != null) {
            parameter("beforeDate", beforeDate)
            parameter("beforeGuid", beforeGuid)
        }
        // Absent rather than blank when there is no filter, so the query string
        // says what it means. Comma separated, which is what the server splits
        // on and what keeps one filter to one parameter.
        if (tags.isNotEmpty()) parameter("tags", tags.joinToString(","))
        // The other half of the filter, same shape. The server ANDs the two:
        // a room and a tag both have to hold.
        if (groups.isNotEmpty()) parameter("groups", groups.joinToString(","))
    }.body()

    override suspend fun getAllPosts(): List<Post> {
        val posts = httpClient.get("posts").body<List<Post>>()
        val favoriteIds = getFavoritePosts().map { it.guid }.toSet()
        return posts.map { post ->
            post.isFavorite = favoriteIds.contains(post.guid)
            post
        }
    }

    override suspend fun getMyPosts(): List<Post> = httpClient.get("posts/mine").body()

    override suspend fun reportPost(postId: String, reason: String?) {
        httpClient.post("posts/$postId/report") {
            contentType(ContentType.Application.Json)
            setBody(ReportRequest(reason))
        }
    }

    override suspend fun removePost(post: Post) {
        httpClient.delete("posts/${post.guid}")
    }

    override suspend fun updatePost(post: Post) {
        val response = httpClient.post("posts") {
            contentType(ContentType.Application.Json)
            setBody(post)
        }
        response.failIfUnverified()
        response.failIfNotSuccess()
    }

    override suspend fun uploadImage(bytes: ByteArray, extension: String): String {
        val response = httpClient.submitFormWithBinaryData(
            url = "uploads",
            formData = formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/$extension")
                        append(HttpHeaders.ContentDisposition, "filename=\"image.$extension\"")
                    },
                )
            },
        )
        response.failIfUnverified()
        response.failIfNotSuccess()
        return response.body<UploadResponse>().id
    }

    override suspend fun fetchImage(id: String): ByteArray? {
        val response = httpClient.get("uploads/$id")
        return if (response.status.isSuccess()) response.body<ByteArray>() else null
    }

    override suspend fun addPost(post: Post) {
        val response = httpClient.post("posts") {
            contentType(ContentType.Application.Json)
            setBody(post)
        }
        response.failIfUnverified()
        response.failIfNotSuccess()
    }

    /**
     * Any non-2xx that is not the one refusal handled above. The client sets no
     * `expectSuccess`, so without this a 400/500 returned normally and a write
     * that never happened looked like a success — the post silently gone.
     */
    private fun HttpResponse.failIfNotSuccess() {
        if (!status.isSuccess()) error("Post request failed: ${status.value}")
    }

    /**
     * The one refusal the app can do something about.
     *
     * Read from the code rather than the message: the message is written for a
     * person and may be reworded or translated, and matching on it would break
     * quietly the first time somebody improved the wording.
     */
    private suspend fun HttpResponse.failIfUnverified() {
        if (status != HttpStatusCode.Forbidden) return
        val body = runCatching { bodyAsText() }.getOrDefault("")
        if (body.contains(ApiError.EMAIL_NOT_VERIFIED)) throw EmailNotVerified()
    }

    override suspend fun completePost(postId: String, message: String?) {
        httpClient.post("posts/$postId/complete") {
            contentType(ContentType.Application.Json)
            setBody(CompletePostRequest(message))
        }
    }

    override suspend fun reopenPost(postId: String) {
        httpClient.post("posts/$postId/reopen")
    }

    override suspend fun shareLink(postId: String): String? {
        val response = httpClient.post("posts/$postId/share")
        // Non-2xx is a real answer here — the post is not public, or is gone —
        // so it comes back as null (no link) rather than throwing.
        if (!response.status.isSuccess()) return null
        val token = response.body<Map<String, String>>()["token"] ?: return null
        return AppLink.postWebUrl(token)
    }

    override suspend fun postByShareToken(token: String): Post? {
        val response = httpClient.get("shared/$token")
        return if (response.status.isSuccess()) response.body() else null
    }

    override suspend fun getFavoritePosts(): List<Post> {
        return httpClient.get("favorites/me").body()
    }

    override suspend fun addFavorite(userId: String, postId: String) {
        httpClient.post("favorites/$userId/$postId")
    }

    override suspend fun removeFavorite(userId: String, postId: String) {
        httpClient.delete("favorites/$userId/$postId")
    }

    override suspend fun isFavorite(userId: String, postId: String): Boolean {
        return httpClient.get("favorites/check/$userId/$postId").body()
    }

    override suspend fun likers(postId: String): LikerList {
        val response = httpClient.get("favorites/post/$postId/people")
        return if (response.status.isSuccess()) response.body() else LikerList()
    }
}
