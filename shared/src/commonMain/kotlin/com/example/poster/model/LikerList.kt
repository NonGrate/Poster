package com.example.poster.model

import kotlinx.serialization.Serializable

/**
 * Who liked a post, as the detail screen shows it.
 *
 * [named] are the people who opted in to being seen ([User.showName]), newest
 * first, each with when they started ([Liker.date]). [total] counts
 * everyone liking, named or not — so the screen can add "and N more people",
 * or say "by N people" when nobody chose to be named.
 */
@Serializable
data class LikerList(
    val named: List<Liker> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class Liker(
    val name: String,
    /** When they started liking, ISO-8601, or null when the device wrote the row. */
    val date: String? = null,
)
