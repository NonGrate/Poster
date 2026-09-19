package com.example.poster.viewmodel

import com.example.poster.model.Post
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedUpdateTest {

    private fun post(guid: String, likes: Int = 0, author: String = "someone") = Post(
        guid = guid,
        title = guid,
        message = "message",
        author = author,
        group = null,
        likes = likes,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

    @Test
    fun someoneElseLikedLandsWithoutAsking() {
        val onScreen = listOf(post("one", likes = 1), post("two"))
        val fresh = listOf(post("one", likes = 2), post("two"))

        val update = decideFeedUpdate(onScreen, fresh)

        assertTrue(update is FeedUpdate.Apply, "a count change moves nothing and should just land")
        assertEquals(2, (update as FeedUpdate.Apply).posts.first().likes)
    }

    @Test
    fun aResolvedPostLandsWithoutAsking() {
        val onScreen = listOf(post("one"))
        val answered = post("one").copy(
            completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            completionMessage = "She is home",
        )

        assertTrue(decideFeedUpdate(onScreen, listOf(answered)) is FeedUpdate.Apply)
    }

    @Test
    fun aNewPostWaitsAndIsCounted() {
        val onScreen = listOf(post("one"))
        val fresh = listOf(post("new"), post("one"))

        val update = decideFeedUpdate(onScreen, fresh)

        assertTrue(update is FeedUpdate.Hold)
        assertEquals(1, (update as FeedUpdate.Hold).arrived)
    }

    /**
     * A departure lands rather than waiting.
     *
     * This used to wait, on the reasoning that a list must not shrink mid-read.
     * The trap was that the button counts posts that *arrived*, a departure
     * brings none, so the button never appeared and the held update could never
     * be asked for. A post deleted by its author — or one from a group you
     * had just left — stayed on the feed for as long as it was open, and opening
     * it led to something that was no longer there.
     */
    @Test
    fun aDepartedPostLandsBecauseNothingWouldEverAnnounceIt() {
        val onScreen = listOf(post("one"), post("two"))
        val fresh = listOf(post("one"))

        val update = decideFeedUpdate(onScreen, fresh)

        assertTrue(update is FeedUpdate.Apply, "a departure had no way of ever being applied")
        assertEquals(fresh, (update as FeedUpdate.Apply).posts)
    }

    /** Protection is for movement, and a departure that also reorders is movement. */
    @Test
    fun aDepartureThatAlsoReordersStillWaits() {
        val onScreen = listOf(post("one"), post("two"), post("three"))
        val fresh = listOf(post("three"), post("one"))

        assertTrue(decideFeedUpdate(onScreen, fresh) is FeedUpdate.Hold)
    }

    /** New content still waits, even when something left in the same refresh. */
    @Test
    fun aDepartureAlongsideAnArrivalStillWaits() {
        val onScreen = listOf(post("one"), post("two"))
        val fresh = listOf(post("one"), post("three"))

        val update = decideFeedUpdate(onScreen, fresh)

        assertTrue(update is FeedUpdate.Hold)
        assertEquals(1, (update as FeedUpdate.Hold).arrived)
    }

    @Test
    fun reorderingWaits() {
        val onScreen = listOf(post("one"), post("two"))
        val fresh = listOf(post("two"), post("one"))

        assertTrue(decideFeedUpdate(onScreen, fresh) is FeedUpdate.Hold)
    }

    /**
     * The reported bug: "4 new posts", tapped, nothing happens.
     *
     * The four were the reader's own. The server puts them in the feed page on
     * purpose; Home takes them out. Counting them made the button offer
     * posts it was never going to show, so tapping it did exactly what it
     * said and the screen looked identical.
     */
    @Test
    fun yourOwnArrivalsAreNotAnnounced() {
        val onScreen = listOf(post("one"))
        val fresh = listOf(post("mine", author = "me"), post("one"))

        val update = decideFeedUpdate(onScreen, fresh, shown = { it.filter { p -> p.author != "me" } })

        assertTrue(update is FeedUpdate.Apply, "nothing the reader can see moved, so nothing should be offered")
        assertEquals(fresh, (update as FeedUpdate.Apply).posts, "the feed itself still keeps everything")
    }

    /** Only what will be shown is counted, not the whole page. */
    @Test
    fun onlyTheArrivalsThatWillBeShownAreCounted() {
        val onScreen = listOf(post("one"))
        val fresh = listOf(post("mine", author = "me"), post("new"), post("one"))

        val update = decideFeedUpdate(onScreen, fresh, shown = { it.filter { p -> p.author != "me" } })

        assertTrue(update is FeedUpdate.Hold)
        assertEquals(1, (update as FeedUpdate.Hold).arrived, "one post is new to this reader, not two")
        assertEquals(fresh, update.posts, "what is held back is the whole feed, not the visible part")
    }

    /** Hidden posts leaving is not movement the reader can see either. */
    @Test
    fun aDepartureNobodyCanSeeLandsQuietly() {
        val onScreen = listOf(post("mine", author = "me"), post("one"))
        val fresh = listOf(post("one"))

        val update = decideFeedUpdate(onScreen, fresh, shown = { it.filter { p -> p.author != "me" } })

        assertTrue(update is FeedUpdate.Apply)
    }

    /** Without a projection it behaves as it always did. */
    @Test
    fun theWholeFeedIsTheDefault() {
        val onScreen = listOf(post("one"))
        val fresh = listOf(post("mine", author = "me"), post("one"))

        val update = decideFeedUpdate(onScreen, fresh)

        assertTrue(update is FeedUpdate.Hold)
        assertEquals(1, (update as FeedUpdate.Hold).arrived)
    }
}
