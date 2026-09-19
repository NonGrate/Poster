package com.example.poster.ui.images

/** What the form did to the post's picture: nothing, took it off, or picked a new one (already downscaled JPEG bytes). */
sealed interface PostImageChange {
    data object Keep : PostImageChange
    data object Remove : PostImageChange
    class New(val bytes: ByteArray) : PostImageChange
}
