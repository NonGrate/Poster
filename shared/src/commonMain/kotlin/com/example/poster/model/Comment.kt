package com.example.poster.model

import kotlinx.serialization.Serializable

/**
 * A comment under a post. [author] is the account id; whether a name is shown
 * is the app's business (posts are anonymous by default, and so are these).
 * [createdAt] is an ISO instant from the server.
 */
@Serializable
data class Comment(
    val guid: String,
    val postGuid: String,
    val author: String,
    val text: String,
    val createdAt: String,
    /** Filled in by the server when feature.authors is on. */
    val authorName: String? = null,
    val authorPhoto: String? = null,
)

@Serializable
data class CommentRequest(val text: String)
