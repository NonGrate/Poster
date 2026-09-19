package com.example.poster.repository

import com.example.poster.db.DatabaseDriverFactory
import com.example.poster.db.DatabaseManager
import com.example.poster.model.Post
import com.example.poster.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The count on the row is this device's copy of how many people have liked.
 *
 * Nothing else moves it between refreshes, so a tap that leaves it alone puts
 * the table one behind the screen — and the favorites flow, which reads its
 * counts straight from these rows, then hands that stale number back to the
 * feed. Liking a second post made the first one's count drop by one.
 */
class PostLocalStoreFavoritesTest {

    private lateinit var store: PostLocalStore
    private var previousPath: String? = null

    private val post = Post(
        guid = "post-1",
        title = "A post",
        message = "message",
        author = "BBB-BBB",
        group = null,
        likes = 5,
        date = LocalDateTime.parse("2026-08-19T10:00"),
    )

    @BeforeTest
    fun setUp() {
        previousPath = System.getProperty("poster.database")
        val file = Files.createTempFile("poster-favorites", ".db").toFile()
        file.delete()
        file.deleteOnExit()
        System.setProperty("poster.database", file.path)
        store = PostLocalStore(
            databaseManager = DatabaseManager(DatabaseDriverFactory()),
            dispatchers = DispatcherProvider(main = Dispatchers.Unconfined, io = Dispatchers.Unconfined),
        )
    }

    @AfterTest
    fun tearDown() {
        previousPath?.let { System.setProperty("poster.database", it) }
            ?: System.clearProperty("poster.database")
    }

    @Test
    fun likingForSomethingCountsThisDeviceToo() = runBlocking {
        store.replaceAll(listOf(post))

        store.addFavorite(USER, post.guid)

        assertEquals(6, store.snapshot().single().likes)
        assertEquals(6, store.favorites(USER).first().single().likes)
    }

    @Test
    fun givingUpOnSomethingTakesThisDeviceBackOff() = runBlocking {
        store.replaceAll(listOf(post))
        store.addFavorite(USER, post.guid)

        store.removeFavorite(USER, post.guid)

        assertEquals(5, store.snapshot().single().likes)
    }

    /** A second tap on something already liked must not count twice. */
    @Test
    fun likingTwiceCountsOnce() = runBlocking {
        store.replaceAll(listOf(post))

        store.addFavorite(USER, post.guid)
        store.addFavorite(USER, post.guid)

        assertEquals(6, store.snapshot().single().likes)
    }

    /** Nor may giving up twice take two off. */
    @Test
    fun givingUpTwiceTakesOffOnce() = runBlocking {
        store.replaceAll(listOf(post))
        store.addFavorite(USER, post.guid)

        store.removeFavorite(USER, post.guid)
        store.removeFavorite(USER, post.guid)

        assertEquals(5, store.snapshot().single().likes)
    }

    /** Two people on one device is not a thing, but two ids in one table is. */
    @Test
    fun anotherPersonLikedCountsSeparately() = runBlocking {
        store.replaceAll(listOf(post))

        store.addFavorite(USER, post.guid)
        store.addFavorite("CCC-CCC", post.guid)

        assertEquals(7, store.snapshot().single().likes)
    }

    /** What the server says is the truth, and it overwrites whatever was counted here. */
    @Test
    fun aRefreshOverwritesTheLocalCount() = runBlocking {
        store.replaceAll(listOf(post))
        store.addFavorite(USER, post.guid)

        store.replaceAll(listOf(post.copy(likes = 9)))

        assertEquals(9, store.snapshot().single().likes)
    }

    /**
     * The feed is a page, so a post of yours can fall outside it. It must not
     * be swept off the device with the rest of the old feed: My Posts reads
     * these rows, and one missing from there reads as the app having lost it.
     */
    @Test
    fun aRefreshKeepsYourOwnPostsEvenWhenTheFeedDropsThem() = runBlocking {
        val mine = post.copy(guid = "mine-1", author = USER)
        store.replaceAll(listOf(post, mine), viewer = USER)

        store.replaceAll(listOf(post), viewer = USER)

        // Gone from the feed, which is right — it is not on the page any more —
        // and still on the device, which is what My Posts reads.
        assertEquals(listOf(post.guid), store.snapshot().map { it.guid })
        assertEquals(listOf("mine-1"), store.mine(USER).first().map { it.guid })
    }

    /** Somebody else's old post is still swept: that is what the page is for. */
    @Test
    fun aRefreshStillDropsOtherPeoplesOldPosts() = runBlocking {
        val theirs = post.copy(guid = "theirs-1")
        store.replaceAll(listOf(post, theirs), viewer = USER)

        store.replaceAll(listOf(post), viewer = USER)

        assertEquals(listOf(post.guid), store.snapshot().map { it.guid })
    }

    /** Nobody signed in exempts nobody, or signing out would never clear anything. */
    @Test
    fun withNobodySignedInEverythingIsStillSwept() = runBlocking {
        val mine = post.copy(guid = "mine-1", author = USER)
        store.replaceAll(listOf(post, mine))

        store.replaceAll(listOf(post))

        assertEquals(emptyList(), store.mine(USER).first(), "nobody signed in exempts nobody")
    }

    @Test
    fun yourOwnPostsAreReplacedByWhatTheServerSays() = runBlocking {
        val kept = post.copy(guid = "mine-1", author = USER)
        val goneElsewhere = post.copy(guid = "mine-2", author = USER)
        store.replaceMine(USER, listOf(kept, goneElsewhere))

        store.replaceMine(USER, listOf(kept))

        assertEquals(listOf("mine-1"), store.mine(USER).first().map { it.guid })
    }

    /** Replacing yours must not touch anybody else's. */
    @Test
    fun replacingYourOwnLeavesTheRestOfTheFeedAlone() = runBlocking {
        val theirs = post.copy(guid = "theirs-1")
        store.replaceAll(listOf(theirs), viewer = USER)

        store.replaceMine(USER, listOf(post.copy(guid = "mine-1", author = USER)))

        assertEquals(listOf("theirs-1"), store.snapshot().map { it.guid })
        assertEquals(listOf("mine-1"), store.mine(USER).first().map { it.guid })
    }

    private companion object {
        const val USER = "AAA-AAA"
    }
}
