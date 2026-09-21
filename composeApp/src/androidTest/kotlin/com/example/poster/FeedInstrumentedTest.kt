package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.util.TestUtils
import com.example.poster.model.Post
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import kotlin.test.Test

class FeedInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    val testPost = Post(
        "feed-test-post",
        "Feed Test Post",
        "This is a test post for feed testing",
        "user2@example.com", // Different author so it shows up in feed
        null,
        5,
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        listOf("test", "feed"),
    )

    fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(testPost)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            seeded = listOf(testPost),
            action = action
        )
    }

    @Test
    fun viewAllPostsInFeed() {
        wrapped { composeTestRule ->
            // TC-101: View All Posts in Feed - ✅ IMPLEMENTED
            TestUtils.awaitTag(composeTestRule, "feed_screen")
            TestUtils.awaitTag(composeTestRule, "posts_list")

            // Wait for posts to load and verify post cards are displayed
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("post_card").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithTag("post_card").assertCountEquals(1)
        }
    }

    @Test
    fun favoriteAPost() {
        wrapped { composeTestRule ->
            // TC-107: Favorite a Post - ✅ IMPLEMENTED
            // Wait for post to appear
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("post_card").fetchSemanticsNodes().isNotEmpty()
            }

            // The card can render before the favorites state lands, and this tag
            // flips to favorite_button_filled when it does.
            TestUtils.awaitTag(composeTestRule, "favorite_button")
            composeTestRule.onAllNodesWithTag("favorite_button").onFirst().performScrollTo().performClick()

            // Verify favorite icon is filled
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithTag("favorite_button_filled").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithTag("favorite_button_filled").onFirst().assertExists()

            // Find and click the favorite button on the first post
            composeTestRule.onAllNodesWithTag("favorite_button_filled").onFirst().performClick()
        }
    }

    @Test
    fun removeFavoriteFromFeed() {
        wrapped { composeTestRule ->
            // TC-108: Remove From Favorites in Feed - ✅ IMPLEMENTED
            // Wait for post to appear
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("post_card").fetchSemanticsNodes().isNotEmpty()
            }

            // First favorite a post
            TestUtils.awaitTag(composeTestRule, "favorite_button")
            composeTestRule.onAllNodesWithTag("favorite_button").onFirst().performScrollTo().performClick()

            // Wait for it to become favorited — a round trip, so the same
            // allowance the rest of the file gives one.
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("favorite_button_filled").fetchSemanticsNodes().isNotEmpty()
            }

            // Then unfavorite it
            composeTestRule.onAllNodesWithTag("favorite_button_filled").onFirst().performClick()

            // Verify undo snackbar appears
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithTag("undo_snackbar").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("undo_snackbar").assertExists()
        }
    }

    @Test
    fun postCardInfoDisplay() {
        wrapped { composeTestRule ->
            // TC-109: Post Card Info Display - ✅ IMPLEMENTED
            // Wait for post to appear
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("post_card").fetchSemanticsNodes().isNotEmpty()
            }

            // Verify all required elements are displayed
            composeTestRule.onAllNodesWithTag("post_title", useUnmergedTree = true).onFirst().assertExists()
            composeTestRule.onAllNodesWithTag("post_message_snippet", useUnmergedTree = true).onFirst().assertExists()
            composeTestRule.onAllNodesWithTag("favorite_count", useUnmergedTree = true).onFirst().assertExists()
            // Either state of the like pill counts: this test is about the card showing
            // its parts, not about whether the post happens to be liked.
            val likeControls = composeTestRule.onAllNodesWithTag("favorite_button").fetchSemanticsNodes().size +
                composeTestRule.onAllNodesWithTag("favorite_button_filled").fetchSemanticsNodes().size
            assert(likeControls > 0) { "expected a like control on the card" }
        }
    }

    @Test
    fun feedEmptyState() {
        // Test empty state without adding any posts
        TestUtils.runWrapped(
            composeTestRule,
            before = { TestUtils.performLogin(it) },
            after = { TestUtils.performLogout(it) },
            action = { composeTestRule ->
                // TC-601: Feed Empty State - ✅ IMPLEMENTED
                TestUtils.awaitTag(composeTestRule, "empty_feed_state")
                composeTestRule.onNodeWithText("Nothing here yet").assertExists()
            }
        )
    }

}
