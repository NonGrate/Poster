package com.example.poster.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Post(
    val guid: String,
    val title: String,
    val message: String,
    val author: String,
    val group: String?,
    val likes: Int = 0,
    val date: LocalDateTime,
    val tags: List<String> = emptyList(),
    val completedAt: LocalDateTime? = null,
    val completionMessage: String? = null,
    val visibility: String = PostVisibility.PUBLIC,
    /** What it is written in. Everything written before this existed is English. */
    val language: String = Language.DEFAULT,
    /**
     * Id of the post's image in the server's upload store (`GET /uploads/{id}`),
     * or null. Defaulted so clients from before images still parse and send.
     */
    val image: String? = null,
    /** Visible comments, counted by the server on the way out; the device caches the number with the row. */
    val comments: Int = 0,
    /** Who wrote it, filled in by the server when feature.authors is on; null otherwise. */
    val authorName: String? = null,
    val authorPhoto: String? = null,
) {
    @OptIn(ExperimentalUuidApi::class)
    constructor(title: String, message: String, author: String, group: String?, tags: List<String>) : this(
        Uuid.random().toString(),
        title,
        message,
        author,
        group,
        0,
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tags
    )
}

/**
 * Who can read a post. Public is the majority; group means only that
 * group's members; private is the author's own reminder list.
 */
object PostVisibility {
    const val PUBLIC = "public"
    const val GROUP = "group"
    const val PRIVATE = "private"
}

@Serializable
data class CompletePostRequest(val message: String? = null)

/** `POST /uploads` answers with the id to put on [Post.image]. */
@Serializable
data class UploadResponse(val id: String)
