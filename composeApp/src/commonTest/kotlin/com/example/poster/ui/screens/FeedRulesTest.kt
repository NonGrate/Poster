package com.example.poster.ui.screens

import com.example.poster.model.Post
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class FeedRulesTest {

    private val now = Instant.parse("2026-08-14T12:00:00Z")
    private val utc = TimeZone.UTC

    private fun post(
        guid: String,
        resolvedAt: Instant? = null,
        tags: List<String> = emptyList(),
        author: String = "someone",
        group: String? = null,
    ) = Post(
        guid = guid,
        title = guid,
        message = "message",
        author = author,
        group = group,
        date = now.toLocalDateTime(utc),
        completedAt = resolvedAt?.toLocalDateTime(utc),
        tags = tags,
    )

    @Test
    fun unresolvedPostsAlwaysStay() {
        val posts = listOf(post("open"))

        assertEquals(posts, posts.withoutStaleResolutions(now, utc))
    }

    @Test
    fun aPostResolvedRecentlyIsStillGoodNews() {
        val posts = listOf(post("just resolved", resolvedAt = now - 1.hours))

        assertEquals(posts, posts.withoutStaleResolutions(now, utc))
    }

    @Test
    fun aPostResolvedYesterdayHasHadItsMoment() {
        val posts = listOf(
            post("open"),
            post("resolved last week", resolvedAt = now - (24 * 7).hours),
        )

        val feed = posts.withoutStaleResolutions(now, utc)

        assertEquals(listOf("open"), feed.map { it.guid })
    }

    /** The boundary itself: at exactly the window it has expired. */
    @Test
    fun theWindowEndsWhenItEnds() {
        val onTheLine = listOf(post("borderline", resolvedAt = now - RESOLVED_VISIBLE_FOR))

        assertEquals(emptyList(), onTheLine.withoutStaleResolutions(now, utc))
    }

    @Test
    fun onlyTheTagsActuallyOnThesePostsAreOffered() {
        val posts = listOf(
            post("a", tags = listOf("health", "family")),
            post("b", tags = listOf("health")),
            post("c"),
        )

        // Ninety-eight tags exist; a chip for one nothing carries leads to an
        // empty list, and a chip per curated tag is a wall.
        assertEquals(listOf("health", "family"), posts.tagsOffered())
    }

    @Test
    fun aTagIsOfferedOnceHoweverManyPostsCarryIt() {
        val posts = List(3) { post("p$it", tags = listOf("health")) }

        assertEquals(listOf("health"), posts.tagsOffered())
    }

    @Test
    fun filteringKeepsOnlyThePostsCarryingThatTag() {
        val tagged = post("a", tags = listOf("health", "family"))
        val posts = listOf(tagged, post("b", tags = listOf("work")), post("c"))

        assertEquals(listOf(tagged), posts.withTags(setOf("health")))
    }

    /**
     * Any of them, not all of them.
     *
     * A post carries at most five tags and rarely two from one group, so
     * requiring every chosen tag would answer nothing — which is a filter that
     * looks broken rather than one that is narrow.
     */
    @Test
    fun severalTagsMeansAnyOfThem() {
        val health = post("a", tags = listOf("health"))
        val debt = post("b", tags = listOf("debt"))
        val neither = post("c", tags = listOf("work"))

        val feed = listOf(health, debt, neither).withTags(setOf("health", "debt"))

        assertEquals(listOf(health, debt), feed)
    }

    /** Carrying two of the chosen tags is still one post, not two. */
    @Test
    fun aPostCarryingTwoChosenTagsAppearsOnce() {
        val both = post("a", tags = listOf("health", "debt"))

        assertEquals(listOf(both), listOf(both).withTags(setOf("health", "debt")))
    }

    @Test
    fun noFilterIsNotAFilter() {
        val posts = listOf(post("a", tags = listOf("health")), post("b"))

        assertEquals(posts, posts.withTags(emptySet()))
    }

    /**
     * A group with nothing ticked inside it means the whole group.
     *
     * The empty case is the one that had to be decided deliberately: falling
     * back to "no filter" would silently widen to the entire feed the moment
     * somebody unticked their last tag, which looks like the filter broke.
     */
    @Test
    fun aGroupWithNoTagsTickedMeansTheWholeGroup() {
        val filter = PostFilter(
            group = "health_wellbeing",
            groupTags = listOf("health", "wellbeing", "fitness"),
        )

        assertEquals(setOf("health", "wellbeing", "fitness"), filter.effectiveTags)
    }

    /** Ticking tags inside a group narrows to those, not to the group. */
    @Test
    fun tickedTagsWinOverTheGroup() {
        val filter = PostFilter(
            group = "health_wellbeing",
            groupTags = listOf("health", "wellbeing", "fitness"),
            tags = setOf("wellbeing"),
        )

        assertEquals(setOf("wellbeing"), filter.effectiveTags)
    }

    @Test
    fun noGroupAndNoTagsIsNoFilter() {
        assertEquals(emptySet(), PostFilter().effectiveTags)
        assertEquals(true, PostFilter().isEmpty)
    }

    // — the From half —

    @Test
    fun namingAGroupShowsOnlyItsPosts() {
        val family = post("family", group = "c-family")
        val club = post("club", group = "c-club")
        val open = post("open", group = null)

        assertEquals(
            listOf(family),
            listOf(family, club, open).fromGroups(setOf("c-family")),
        )
    }

    /** Several rooms are an "or", the way several tags are. */
    @Test
    fun severalGroupsAreAnyOfThem() {
        val family = post("family", group = "c-family")
        val club = post("club", group = "c-club")
        val open = post("open", group = null)

        assertEquals(
            listOf(family, club),
            listOf(family, club, open).fromGroups(setOf("c-family", "c-club")),
        )
    }

    @Test
    fun noGroupIsNoFilter() {
        val posts = listOf(post("a", group = "c"), post("b", group = null))
        assertEquals(posts, posts.fromGroups(emptySet()))
    }

    /**
     * The two halves narrow together. An implementation that ORed them would
     * return everything either question matched, which on a real feed looks
     * like a filter that is merely generous rather than one that is wrong.
     */
    @Test
    fun aGroupAndATagBothHaveToHold() {
        val wanted = post("wanted", group = "c-family", tags = listOf("health"))
        val wrongTag = post("wrong-tag", group = "c-family", tags = listOf("work"))
        val wrongRoom = post("wrong-room", group = "c-club", tags = listOf("health"))

        val feed = listOf(wanted, wrongTag, wrongRoom).asFeedShows(
            viewer = "me",
            now = now,
            filter = PostFilter(groups = setOf("c-family"), tags = setOf("health")),
            zone = utc,
        )

        assertEquals(listOf(wanted), feed)
    }

    /** A group with nothing ticked still means the whole group, alongside a room. */
    @Test
    fun aGroupNarrowsAGroupRatherThanReplacingIt() {
        val inGroup = post("in-group", group = "c-family", tags = listOf("fitness"))
        val outOfGroup = post("out", group = "c-family", tags = listOf("work"))

        val feed = listOf(inGroup, outOfGroup).asFeedShows(
            viewer = "me",
            now = now,
            filter = PostFilter(
                groups = setOf("c-family"),
                group = "health_wellbeing",
                groupTags = listOf("health", "wellbeing", "fitness"),
            ),
            zone = utc,
        )

        assertEquals(listOf(inGroup), feed)
    }

    @Test
    fun anEmptyFilterIsEmptyWhicheverHalfIsAsked() {
        assertEquals(true, PostFilter().isEmpty)
        assertEquals(false, PostFilter(groups = setOf("c")).isEmpty)
        assertEquals(false, PostFilter(tags = setOf("health")).isEmpty)
    }

    /** A resolved post that has had its moment is gone whatever its tag. */
    @Test
    fun filteringDoesNotResurrectAStaleAnswer() {
        val stale = post("old", resolvedAt = now - 25.hours, tags = listOf("health"))
        val open = post("open", tags = listOf("health"))

        val feed = listOf(stale, open).withoutStaleResolutions(now, utc).withTags(setOf("health"))

        assertEquals(listOf(open), feed)
    }

    /**
     * The bug the button had: four posts offered, none shown.
     *
     * The reader's own posts come down in the feed page on purpose — the
     * server sends them — and the screen takes them out again. Anything
     * counting arrivals has to take them out first or it counts posts it will
     * never show.
     */
    @Test
    fun yourOwnPostsAreNotInTheFeed() {
        val mine = post("mine", author = "me")
        val theirs = post("theirs")

        assertEquals(listOf(theirs), listOf(mine, theirs).asFeedShows(viewer = "me", now = now, zone = utc))
    }

    /** Signed out, nothing is anybody's own, and hiding a random author's posts would empty the feed. */
    @Test
    fun withNobodySignedInNothingIsHidden() {
        val posts = listOf(post("a"), post("b"))

        assertEquals(posts, posts.asFeedShows(viewer = null, now = now, zone = utc))
    }

    /** All three rules at once, which is the only way it is ever called. */
    @Test
    fun theFeedHidesYoursYourStaleAnswersAndOtherTags() {
        val mine = post("mine", author = "me", tags = listOf("health"))
        val stale = post("stale", resolvedAt = now - 25.hours, tags = listOf("health"))
        val otherTag = post("other", tags = listOf("work"))
        val wanted = post("wanted", tags = listOf("health"))

        val feed = listOf(mine, stale, otherTag, wanted)
            .asFeedShows(viewer = "me", now = now, filter = PostFilter(tags = setOf("health")), zone = utc)

        assertEquals(listOf(wanted), feed)
    }

    /** A tag is a question about the feed, not a way to see your own posts again. */
    @Test
    fun noTagFilterStillHidesYourOwn() {
        val mine = post("mine", author = "me")
        val theirs = post("theirs")

        val feed = listOf(mine, theirs).asFeedShows(viewer = "me", now = now, filter = PostFilter(tags = emptySet()), zone = utc)

        assertEquals(listOf(theirs), feed)
    }
}
