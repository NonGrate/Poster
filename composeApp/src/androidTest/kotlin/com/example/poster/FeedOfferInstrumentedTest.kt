package com.example.poster

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poster.model.Post
import com.example.poster.util.TestUtils
import androidx.compose.ui.test.onFirst
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.example.poster.repository.PostLocalStore
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.repository.PostRepository
import com.example.poster.repository.SessionRepository
import com.example.poster.util.DispatcherProvider
import com.example.poster.ktor.KtorPostApi
import com.example.poster.ktor.createHttpClient
import com.example.poster.auth.InMemoryAuthTokenStorage
import com.example.poster.cache.PostCache
import com.example.poster.preview.FakeUserApi
import com.example.poster.viewmodel.FavoritesViewModel
import com.example.poster.model.PostVisibility

/**
 * Somebody else's post arriving must not move the list out from under whoever
 * is reading it. It waits behind a button, and lands when that button is tapped.
 */
@RunWith(AndroidJUnit4::class)
class FeedOfferInstrumentedTest : KoinComponent {

    private val localStore: PostLocalStore by inject()
    private val postsViewModel: PostsViewModel by inject()
    private val favoritesViewModel: FavoritesViewModel by inject()
    private val repository: PostRepository by inject()
    private val session: SessionRepository by inject()
    private val dispatchers: DispatcherProvider by inject()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val existing = Post(
        guid = "feed-offer-existing",
        title = "Already here",
        message = "Was in the feed before",
        author = "user2@example.com",
        group = null,
        likes = 0,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tags = emptyList(),
        isFavorite = false,
    )

    private val arriving = existing.copy(
        guid = "feed-offer-arriving",
        title = "Arrived while reading",
        message = "Should wait for the button",
    )

    @Test
    fun aPostArrivingWhileReadingWaitsForTheButton() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitTag(rule, "post_card")
                rule.onAllNodesWithTag("post_card").assertCountEquals(1)

                // Someone else posts, and the app is told to look again.
                runBlocking { TestUtils.seedPostAsSecondUser(arriving) }
                TestUtils.navigateToMyPosts(rule)
                TestUtils.navigateToHome(rule)

                // The offer appears; the list has not moved.
                TestUtils.awaitTag(rule, "new_posts_button")
                rule.onAllNodesWithTag("post_card").assertCountEquals(1)

                rule.onNodeWithTag("new_posts_button").performClick()

                rule.waitUntil(timeoutMillis = 5000) {
                    rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 2
                }
                rule.onAllNodesWithTag("new_posts_button").assertCountEquals(0)
            },
        )
    }

    /**
     * The feed has to survive the process, which means it has to be on disk.
     * This is what makes a cold start show something before the network answers.
     */
    @Test
    fun whatTheFeedShowsIsWrittenToDisk() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitTag(rule, "post_card")

                // A one-off read, not a subscription: blocking on a flow here
                // starves the main dispatcher and Compose cannot build a dialog.
                val stored = runBlocking { localStore.snapshot() }
                assertTrue(stored.isNotEmpty(), "the feed was not written to disk")
                assertEquals(
                    existing.title,
                    stored.first { it.guid == existing.guid }.title,
                )
            },
        )
    }

    /**
     * Liked has to survive the process too. It used to come from an in-memory
     * cache, so the feed worked offline and this screen did not — the same app
     * behaving two ways.
     */
    @Test
    fun whatYouAreLikedForIsWrittenToDisk() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitTag(rule, "post_card")
                rule.onAllNodesWithTag("favorite_button").onFirst().performClick()
                TestUtils.navigateToFavorites(rule)
                TestUtils.awaitTag(rule, "favourite_post_card")

                val stored = runBlocking { localStore.favorites("test@example.com").first() }
                assertTrue(
                    stored.any { it.guid == existing.guid },
                    "what this person has liked was not written to disk",
                )
            },
        )
    }

    /**
     * The feed refresh keeps posts somebody has liked, so a refresh
     * rewrites rows that are already on disk. When the insert was not idempotent
     * that failed the whole transaction and nothing was written at all: once a
     * device had favorited anything, its feed never changed again.
     */
    @Test
    fun theFeedStillUpdatesAfterSomethingIsFavorited() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitTag(rule, "favorite_button")
                rule.onAllNodesWithTag("favorite_button").onFirst().performClick()
                TestUtils.awaitTag(rule, "favorite_button_filled")

                // A post arrives while one is favorited.
                runBlocking { TestUtils.seedPostAsSecondUser(arriving) }
                TestUtils.navigateToMyPosts(rule)
                TestUtils.navigateToHome(rule)

                // Waited for, not sampled: a refresh is a round trip.
                rule.waitUntil(timeoutMillis = 10_000) {
                    runBlocking { localStore.snapshot() }.any { it.guid == arriving.guid }
                }
                val stored = runBlocking { localStore.snapshot() }
                assertTrue(
                    stored.any { it.guid == existing.guid },
                    "the favorited post was lost by the refresh",
                )
            },
        )
    }

    /**
     * A pull is someone saying "show me now", so it applies what it finds
     * instead of leaving them to tap the button they just pulled past.
     *
     * This drives what the gesture calls rather than the gesture: simulating the
     * swipe was unreliable here. That the swipe reaches this at all was checked
     * by hand on a device, watching the indicator appear.
     */
    @Test
    fun refreshingShowsWhatArrivedWithoutAskingAgain() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitTag(rule, "post_card")
                runBlocking { TestUtils.seedPostAsSecondUser(arriving) }

                postsViewModel.refresh()

                rule.waitUntil(timeoutMillis = 15_000) {
                    rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 2
                }
                rule.onAllNodesWithTag("new_posts_button").assertCountEquals(0)
            },
        )
    }

    /**
     * A cold start must show the whole feed, not part of it with the rest behind
     * a button.
     *
     * The store fills in pieces — favorites are written before the feed — and a
     * ViewModel that treats the first thing it sees as "what the reader is
     * looking at" then treats the real feed as arrivals. A fresh launch showed
     * two posts with "5 new posts" over the rest, and nobody had read
     * anything yet.
     *
     * A new ViewModel is built here on purpose: the app's is a singleton that
     * outlives any screen, so switching tabs cannot reproduce a launch.
     */
    @Test
    fun aFreshStartShowsTheWholeFeedRatherThanOfferingIt() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.seedPostAsSecondUser(arriving)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitAnyTag(rule, "post_card")

                // The state a launch actually starts from: the favorites write
                // lands first, so the store holds a strict subset of the feed.
                runBlocking {
                    localStore.clear()
                    localStore.replaceFavorites("test@example.com", listOf(existing))
                }

                val fresh = PostsViewModel(repository, session, dispatchers)
                try {
                    rule.waitUntil(timeoutMillis = 15_000) { fresh.feed.value.size == 2 }
                    assertEquals(
                        0,
                        fresh.pendingCount.value,
                        "a launch should show the feed, not offer it",
                    )
                } finally {
                    fresh.clear()
                }
            },
        )
    }

    /**
     * A feed the server did not fill offers nothing below it.
     *
     * "Show older posts" used to be offered until somebody pressed it and got
     * an empty page back, so every feed shorter than a page carried a button
     * that fetched nothing. The size of the page that arrived already answers
     * the question — a server that had more would have sent more.
     */
    @Test
    fun aFeedShorterThanAPageOffersNothingOlder() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitAnyTag(rule, "post_card")

                val fresh = PostsViewModel(repository, session, dispatchers)
                try {
                    rule.waitUntil(timeoutMillis = 15_000) { fresh.feed.value.isNotEmpty() }
                    rule.waitUntil(timeoutMillis = 15_000) { !fresh.moreToLoad.value }
                    assertEquals(
                        false,
                        fresh.moreToLoad.value,
                        "a feed of ${fresh.feed.value.size} is not a full page and has nothing below it",
                    )
                } finally {
                    fresh.clear()
                }
            },
        )
    }

    /**
     * The point of keeping the feed on disk: with no server reachable, a launch
     * shows the last feed instead of an empty screen waiting on a request that
     * will never come back.
     *
     * The ViewModel is built against a repository whose API cannot connect, so
     * the only thing it can possibly show is what the database already holds.
     */
    @Test
    fun aFeedIsStillThereWithNothingToConnectTo() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitAnyTag(rule, "post_card")

                val offline = PostRepository(
                    postApi = KtorPostApi(
                        createHttpClient("127.0.0.1", 1, authTokenStorage = InMemoryAuthTokenStorage())
                    ),
                    userApi = FakeUserApi(),
                    cache = PostCache(),
                    dispatchers = dispatchers,
                    localStore = localStore,
                )
                val model = PostsViewModel(offline, session, dispatchers)
                try {
                    rule.waitUntil(timeoutMillis = 20_000) {
                        model.feed.value.any { it.guid == existing.guid }
                    }
                } finally {
                    model.clear()
                }
            },
        )
    }

    /**
     * A post somebody has liked survives a feed refresh that drops it —
     * and stops appearing in the feed.
     *
     * The refresh replaces the feed wholesale, so it deletes what the server did
     * not send. Leaving a group is the case that separates the two: the
     * post drops out of the feed while Liked still holds it, and deleting it
     * would empty that list until its own refresh happened to run.
     *
     * Keeping the row used to mean keeping it in the feed too, because the feed
     * read every row on the device — so a post from a group you had left
     * stayed on your Home screen. `in_feed` is what tells the two reasons for a
     * row being here apart.
     */
    @Test
    fun aPostYouAreLikedForSurvivesLeavingItsGroup() {
        val groupPost = existing.copy(
            guid = "group-post-kept",
            title = "From the book club",
            group = "book-club",
            visibility = PostVisibility.GROUP,
        )
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                // A public post has to stay behind: an empty page from the server
                // is treated as a failed request and leaves the feed untouched
                // (PostRepository.refreshPosts), which is not what this test is about.
                TestUtils.seedPostAsSecondUser(existing)
                TestUtils.joinGroupAsTestUser("BOOK_CLUB_INVITE")
                TestUtils.seedPostAsSecondUser(groupPost)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitAnyTag(rule, "favorite_button")
                rule.onAllNodesWithTag("favorite_button").onFirst().performClick()
                TestUtils.awaitAnyTag(rule, "favorite_button_filled")

                // Driven synchronously: waiting on the screen cannot tell "the
                // refresh has not run yet" apart from "it ran and kept the row",
                // which is exactly what this test is about.
                val (feed, liking) = runBlocking {
                    TestUtils.leaveGroupAsTestUser("book-club")
                    repository.refreshPosts()
                    val userId = session.user.value!!.guid
                    localStore.snapshot() to localStore.favorites(userId).first()
                }
                assertTrue(
                    liking.any { it.guid == groupPost.guid },
                    "the feed refresh deleted a post this person has liked",
                )
                assertTrue(
                    feed.none { it.guid == groupPost.guid },
                    "a post from a group this person has left is still in the feed",
                )
            },
        )
    }
}
