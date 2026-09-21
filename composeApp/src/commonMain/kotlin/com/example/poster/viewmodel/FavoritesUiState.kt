package com.example.poster.viewmodel

import com.example.poster.model.Post

/**
 * Everything the favorites screens read, in one value.
 *
 * It used to be five flows updated one after another, so between two of those
 * writes the ids could disagree with the list and the counts with both. [ids] is
 * derived here instead of stored: it cannot drift from the list it describes,
 * because it is that list.
 *
 * [counts] stays separate — it holds a count for posts that are not favorites,
 * which is what Home and Details display.
 *
 * The undo prompt is deliberately *not* here. It is an event with a four-second
 * timer, and folding it in made every feed row recompose when that timer fired.
 * Stage 8 turns it into a proper event; until then it stays its own flow.
 */
data class FavoritesUiState(
    val posts: List<Post> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val error: UiError? = null,
) {
    val ids: Set<String> = posts.mapTo(LinkedHashSet()) { it.guid }

    fun countFor(post: Post): Int = counts[post.guid] ?: post.likes

    fun isFavorite(postId: String): Boolean = postId in ids

    /** The optimistic half of a toggle: list, ids and count move together. */
    fun withFavorite(post: Post, added: Boolean): FavoritesUiState {
        val newCount = ((counts[post.guid] ?: post.likes) + if (added) 1 else -1)
            .coerceAtLeast(0)
        val newPosts = if (added) {
            val updated = post.copy(likes = newCount)
            if (posts.any { it.guid == post.guid }) {
                posts.map { if (it.guid == post.guid) updated else it }
            } else {
                posts + updated
            }
        } else {
            posts.filterNot { it.guid == post.guid }
        }
        return copy(posts = newPosts, counts = counts + (post.guid to newCount))
    }
}
