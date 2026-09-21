package com.example.poster.domain.validation

/** What a comment may be; checked in the composer and again on the server. */
object CommentRules {
    const val TEXT_LIMIT = 1000

    fun textValid(text: String): Boolean = text.trim().length in 1..TEXT_LIMIT
    fun tooLong(text: String): Boolean = text.trim().length > TEXT_LIMIT
}
