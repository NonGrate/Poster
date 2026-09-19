package com.example.poster.ui.screens

import com.example.poster.model.Post
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** How long an resolved post stays in the feed after it is resolved. */
val RESOLVED_VISIBLE_FOR: Duration = 24.hours

/**
 * What the feed shows.
 *
 * A resolved post needs no more posts, so leaving it there crowds out the
 * ones that do — but removing it the instant it is resolved means nobody ever
 * sees the good news. It stays for [RESOLVED_VISIBLE_FOR] and then goes.
 *
 * This is the *feed's* rule and nowhere else's. Your own resolved posts stay
 * in My Posts, and anything you have liked stays in Liked however long
 * ago it was resolved — that is the list of things you asked to care about.
 */
fun List<Post>.withoutStaleResolutions(
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    window: Duration = RESOLVED_VISIBLE_FOR,
): List<Post> = filter { post ->
    val resolvedAt = post.completedAt ?: return@filter true
    now - resolvedAt.toInstant(zone) < window
}

/**
 * The tags worth offering as a filter: the ones actually on these posts.
 *
 * There are sixty curated tags and a family feed will use a handful, so
 * offering all of them would be a wall of chips, most of which lead to an empty
 * list. Read from the *unfiltered* feed — offering only the tags that survive
 * the current filter would leave one chip on screen and no way to switch.
 */
fun List<Post>.tagsOffered(): List<String> =
    flatMap { it.tags }.distinct()

/**
 * What the reader has narrowed the feed to: who it is from, and what it is about.
 *
 * Two independent questions. Groups are an OR-set and tags are an OR-set,
 * and the two are ANDed — "anything my reading group is carrying about pets
 * or money". Empty on either side means that question is not being asked.
 *
 * A group on its own means every post in that group. Ticking tags inside it
 * narrows further — so the group is the question and the tags are the
 * refinement, which is why [effectiveTags] falls back to the whole group rather
 * than to nothing.
 *
 * [groupTags] rides along so that fallback is a pure function of the filter.
 * The alternative was giving the ViewModel the tag repository so it could look
 * the group up, which is a dependency earned by one expression.
 */
data class PostFilter(
    val groups: Set<String> = emptySet(),
    val group: String? = null,
    val groupTags: List<String> = emptyList(),
    val tags: Set<String> = emptySet(),
    /** Free text over title and message. */
    val query: String = "",
    /** Only people the reader follows (feature.follows). */
    val following: Boolean = false,
) {
    val isEmpty: Boolean get() = groups.isEmpty() && group == null && tags.isEmpty() && query.isBlank() && !following

    /** The tag ids the feed is actually narrowed to. Empty means no tag filter. */
    val effectiveTags: Set<String>
        get() = if (tags.isNotEmpty()) tags else groupTags.toSet()

    /** How many separate things the reader has chosen, for the "Clear" affordances. */
    val activeCount: Int get() = groups.size + tags.size + (if (group != null) 1 else 0) + (if (query.isBlank()) 0 else 1) + (if (following) 1 else 0)
}

/**
 * The feed narrowed to any of these tags. An empty set is no filter at all.
 *
 * Any rather than all: picking Illness and Debt asks for posts about either.
 * Requiring both would answer almost nothing, since a post carries at most
 * five tags and rarely two from the same question.
 */
fun List<Post>.withTags(tagIds: Set<String>): List<Post> =
    if (tagIds.isEmpty()) this else filter { post -> post.tags.any { it in tagIds } }

/**
 * The feed narrowed to posts shared with any of these groups.
 *
 * Naming a room also drops public posts, the same rule the server applies:
 * "show me what my reading group is carrying" is not answered by a feed that
 * still holds everything posted to everybody. An empty set is no filter.
 */
/** The same match the server makes: case-insensitive, anywhere in the title or the message. */
fun List<Post>.matching(query: String): List<Post> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return this
    return filter { it.title.lowercase().contains(needle) || it.message.lowercase().contains(needle) }
}

/** Only these authors; null is no filter (the same match the server makes with `following=true`). */
fun List<Post>.fromAuthors(authors: Set<String>?): List<Post> =
    if (authors == null) this else filter { it.author in authors }

fun List<Post>.fromGroups(groupIds: Set<String>): List<Post> =
    if (groupIds.isEmpty()) this else filter { it.group in groupIds }

/**
 * Everything the feed hides, in one place.
 *
 * Your own posts are not in it — you know what you wrote, and My Posts is
 * where they live. Answers older than a day are not in it. Under a filter, only
 * posts carrying one of the chosen tags are.
 *
 * One function because two things need the answer and they must agree: the
 * screen that draws the list, and the count behind the "new posts" button.
 * They disagreed. The button counted arrivals in the whole feed — which the
 * server deliberately includes your own posts in — so writing four posts
 * yourself made it offer four, and tapping it then showed none of them, because
 * this is where they were removed. Anything that needs to know what the reader
 * will see asks here.
 */
fun List<Post>.asFeedShows(
    viewer: String?,
    now: Instant,
    filter: PostFilter = PostFilter(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<Post> =
    // Nobody signed in means nothing is anybody's own: no author equals null.
    filter { it.author != viewer }
        .withoutStaleResolutions(now, zone)
        .fromGroups(filter.groups)
        .withTags(filter.effectiveTags)
        .matching(filter.query)
