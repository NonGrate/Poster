package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.util.TestUtils
import com.example.poster.model.Post
import com.example.poster.repository.PostRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.test.Test

class FavouritesInstrumentedTest : KoinComponent {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // Inject repository through Koin instead of manual instantiation
    private val postRepository: PostRepository by inject()

    val testPost = Post(
        "aaa-aaa",
        "Test post",
        "Test post message",
        "user2@example.com",
        null,
        1,
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        emptyList(),
        isFavorite = true
    )

    private suspend fun preparePost(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        favorite: Boolean,
    ) {
        TestUtils.seedPostAsSecondUser(testPost)
        TestUtils.performLogin(composeTestRule)
        if (favorite) {
            postRepository.addFavorite("test@example.com", testPost.guid).getOrThrow()
        }
    }

    private fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.performLogin(it)
                TestUtils.navigateToFavorites(it)
            },
            after = { TestUtils.performLogout(it) },
            action = action
        )
    }


    @Test
    fun viewFavourites() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                preparePost(it, favorite = true)
                TestUtils.navigateToFavorites(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { composeTestRule ->
            // TC-301: View Favourites - ✅ IMPLEMENTED
            composeTestRule.onNodeWithTag("favourites_screen").assertExists()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithTag("favourites_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("favourites_list").assertExists()

            // Initially empty state
            composeTestRule.onAllNodesWithTag("favourite_post_card").assertCountEquals(1)

            }
        )
    }

    @Test
    fun removeFromFavouritesWithUndo() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                preparePost(it, favorite = true)
                TestUtils.navigateToFavorites(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { composeTestRule ->
            // TC-302: Remove With Undo - ✅ IMPLEMENTED
            // Remove from favourites
            composeTestRule.onAllNodesWithTag("remove_favourite_button").onFirst().performClick()

            // Verify undo snackbar appears
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag(
                    "undo_snackbar",
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithTag("undo_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("undo_snackbar", useUnmergedTree = true).assertExists()
            composeTestRule.onNodeWithTag("undo_button").assertExists()

            // Click undo
            composeTestRule.onNodeWithTag("undo_button").performClick()

            // Verify post remains in favourites
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("favourite_post_card").fetchSemanticsNodes().size == 1
            }
            composeTestRule.onAllNodesWithTag("favourite_post_card").assertCountEquals(1)
            }
        )
    }

    @Test
    fun removeFromFavouritesWithoutUndo() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                preparePost(it, favorite = true)
                TestUtils.navigateToFavorites(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { composeTestRule ->
            // TC-303: Remove Without Undo - ✅ IMPLEMENTED
            // Remove from favourites
            TestUtils.awaitTag(composeTestRule, "remove_favourite_button")
            composeTestRule.onAllNodesWithTag("remove_favourite_button").onFirst().performClick()

            // Wait for undo timeout without clicking undo
            composeTestRule.waitUntil(timeoutMillis = 6000) {
                composeTestRule.onAllNodesWithTag("undo_snackbar").fetchSemanticsNodes().isEmpty()
            }

            // Verify post is removed from list. The snackbar going away is
            // the moment the removal is *sent*, not the moment the list has
            // caught up, so give it that beat — asserting on the instant the
            // snackbar vanished failed on a card that was already on its way out.
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("favourite_post_card").fetchSemanticsNodes().isEmpty()
            }
            composeTestRule.onAllNodesWithTag("favourite_post_card").assertCountEquals(0)
            }
        )
    }

    @Test
    fun undoWorksIndependentlyInFeedAndFavourites() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                preparePost(it, favorite = false)
                TestUtils.navigateToFavorites(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { composeTestRule ->
            // TC-303A: Undo Works Independently in Feed and Favourites - ✅ IMPLEMENTED
            // Add a post to favourites
            composeTestRule.onNodeWithTag("feed_tab").performClick()
            TestUtils.awaitAnyTag(composeTestRule, "favorite_button")
            composeTestRule.onAllNodesWithTag("favorite_button").onFirst().performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithText("Likes", substring = true).fetchSemanticsNodes().isNotEmpty()
            }

            // Remove from favourites in feed
            composeTestRule.onAllNodesWithTag("favorite_button_filled").onFirst().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithText("Unliked", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("undo_button").performClick() // Undo in feed
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("favorite_button_filled").fetchSemanticsNodes().isNotEmpty()
            }

            // Go to favourites and remove there
            composeTestRule.onNodeWithTag("favourites_tab").performClick()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("remove_favourite_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithTag("remove_favourite_button").onFirst().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithText("Unliked", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("undo_button").performClick() // Undo in favourites

            // Verify both undo actions worked independently
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("favourite_post_card").fetchSemanticsNodes().size == 1
            }
            composeTestRule.onAllNodesWithTag("favourite_post_card").assertCountEquals(1)
            }
        )
    }

    @Test
    fun favouritesEmptyState() {
        wrapped { composeTestRule ->
            // TC-603: Favourites Empty - ✅ IMPLEMENTED
            composeTestRule.onNodeWithTag("empty_favourites_state").assertExists()
            composeTestRule.onNodeWithText("Nothing liked yet").assertExists()
            composeTestRule.onNodeWithText("Tap Like on a post to keep it here.").assertExists()
        }
    }
}
