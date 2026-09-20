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
import com.example.poster.config.Features
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
        saved: Boolean,
    ): List<Post> = httpClient.get("posts") {
        parameter("limit", limit)
        // Only ask for a narrowing the build actually has: the server route is
        // gone when the flag is off, and the parameter would be a lie.
        if (Features.FOLLOWS && following) parameter("following", true)
        if (Features.BOOKMARKS && saved) parameter("saved", true)
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

    // Just the feed. It used to fetch `favorites/me` as well and stamp each post
    // with it — a second round trip on every refresh for a flag nothing read:
    // the screens ask FavoritesViewModel, which holds the ids itself.
    override suspend fun getAllPosts(): List<Post> = httpClient.get("posts").body()

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
        response.failIfNotSuccess("Post request")
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
        response.failIfNotSuccess("Post request")
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
        response.failIfNotSuccess("Post request")
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

    // The server takes the liker from the bearer token, so [userId] never
    // reaches it: the path used to carry an id that had to equal the token's
    // subject, which is a parameter with one legal value. Kept on the interface
    // because the call sites pass the signed-in id and nothing else knows it.
    override suspend fun addFavorite(userId: String, postId: String) {
        httpClient.post("favorites/$postId")
    }

    override suspend fun removeFavorite(userId: String, postId: String) {
        httpClient.delete("favorites/$postId")
    }

    override suspend fun likers(postId: String): LikerList =
        httpClient.get("favorites/post/$postId/people").bodyOr(LikerList())
}
