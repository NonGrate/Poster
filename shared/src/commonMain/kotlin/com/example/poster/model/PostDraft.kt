package com.example.poster.model

import kotlinx.serialization.Serializable

/** An unsent post kept on the device (feature.drafts). One at a time; the image is not kept. */
@Serializable
data class PostDraft(
    val title: String = "",
    val message: String = "",
    val tags: List<String> = emptyList(),
    val group: String? = null,
    val visibility: String = PostVisibility.PUBLIC,
    val language: String? = null,
) {
    val isBlank: Boolean get() = title.isBlank() && message.isBlank()
}
