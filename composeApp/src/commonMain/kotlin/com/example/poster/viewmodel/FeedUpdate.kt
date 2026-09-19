package com.example.poster.viewmodel

import com.example.poster.model.Post

/** What to do with a fresher list than the one on screen. */
sealed interface FeedUpdate {
    /** Nothing moves, so it lands without asking. */
    data class Apply(val posts: List<Post>) : FeedUpdate

    /**
     * Something moved. It waits for the reader, and [arrived] is how many
     * posts are genuinely new — the number the button announces.
     */
    data class Hold(val posts: List<Post>, val arrived: Int) : FeedUpdate
}

/**
 * The rule behind the "new posts" button.
 *
 * The button exists so a list does not move under someone reading it. A change
 * that moves nothing does not need it: when someone else starts liking, a
 * number changes on a card that stays exactly where it is.
 *
 * That distinction matters more than it looks. Holding count changes back meant
 * they were never shown at all, because the button counts new posts and a
 * count change adds none — so the number simply froze.
 *
 * [shown] is what the reader will actually see of these lists — see
 * `asFeedShows`. Deciding from the raw feed instead is how the button came to
 * offer four posts and deliver nothing: they were the reader's own, counted
 * here and removed by the screen. The lists themselves stay whole; only the
 * decision is made on what survives to the screen.
 */
fun decideFeedUpdate(
    onScreen: List<Post>,
    fresh: List<Post>,
    shown: (List<Post>) -> List<Post> = { it },
): FeedUpdate {
    val onScreenIds = shown(onScreen).map { it.guid }
    val freshIds = shown(fresh).map { it.guid }
    if (freshIds == onScreenIds) {
        return FeedUpdate.Apply(fresh)
    }

    val known = onScreenIds.toSet()
    val arrived = freshIds.count { it !in known }

    // Posts have only left, and the ones remaining are in the order they were
    // already in. Holding this was a trap: the button counts posts that
    // arrived, none did, so it never appeared — and the held update had no way
    // of ever being asked for. A post deleted by its author, or one from a
    // group you left, stayed on the feed for as long as it was open, and
    // opening it led to something that was not there.
    if (arrived == 0 && freshIds == onScreenIds.filter { it in freshIds.toSet() }) {
        return FeedUpdate.Apply(fresh)
    }
    return FeedUpdate.Hold(fresh, arrived)
}
