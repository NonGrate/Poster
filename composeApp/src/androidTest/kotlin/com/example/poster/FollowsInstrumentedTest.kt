package com.example.poster

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poster.model.Post
import com.example.poster.util.TestUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Follow the author of a post, narrow the feed to people you follow, then unfollow (feature.follows). */
@RunWith(AndroidJUnit4::class)
class FollowsInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val post = Post(
        guid = "followed-post",
        title = "A post from someone worth following",
        message = "Read me",
        author = "user2@example.com",
        group = null,
        likes = 0,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tags = listOf("health"),
    )

    @Test
    fun followingAnAuthorPutsTheirPostUnderTheFollowingFilter() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(post)
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
        ) { rule ->
            TestUtils.navigateToHome(rule)
            TestUtils.awaitAnyTag(rule, "post_card")
            rule.onNodeWithTag("posts_list").performScrollToNode(hasText(post.title))

            // Nobody followed yet: the Following filter shows nothing.
            rule.onNodeWithTag("feed_filter_toggle").performClick()
            TestUtils.awaitTag(rule, "filter_following")
            rule.onNodeWithTag("filter_following").performClick()
            rule.onNodeWithTag("filter_show_button").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag("feed_filter_sheet").fetchSemanticsNodes().isEmpty() }
            TestUtils.awaitTag(rule, "feed_filter_active_following")
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText(post.title).fetchSemanticsNodes().isEmpty() }

            // Back to everybody, follow the author from the card.
            rule.onNodeWithTag("feed_filter_active_following").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText(post.title).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("posts_list").performScrollToNode(hasText(post.title))
            rule.onNodeWithText("User Two", useUnmergedTree = true).performScrollTo().performClick()
            TestUtils.awaitTag(rule, "follow_dialog")
            rule.onNodeWithText("Follow").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag("follow_dialog").fetchSemanticsNodes().isEmpty() }

            // Now the Following filter keeps it.
            rule.onNodeWithTag("feed_filter_toggle").performClick()
            TestUtils.awaitTag(rule, "filter_following")
            rule.onNodeWithTag("filter_following").performClick()
            rule.onNodeWithTag("filter_show_button").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag("feed_filter_sheet").fetchSemanticsNodes().isEmpty() }
            TestUtils.awaitTag(rule, "feed_filter_active_following")
            rule.onNodeWithTag("posts_list").performScrollToNode(hasText(post.title))

            // Unfollow, and the filtered feed empties again.
            rule.onNodeWithText("User Two", useUnmergedTree = true).performScrollTo().performClick()
            TestUtils.awaitTag(rule, "follow_dialog")
            rule.onNodeWithText("Unfollow").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText(post.title).fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag("feed_filter_active_following").performClick()
        }
        kotlinx.coroutines.runBlocking { TestUtils.deletePostAsSecondUser(post) }
    }
}
