package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.model.Post
import com.example.poster.util.TestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import kotlin.test.Test

/**
 * Pulling works below the last post, which is most of a short screen.
 *
 * The list was only as tall as the posts in it, so with one post the rest
 * of the screen belonged to nothing and a pull there did nothing at all. The
 * refresh works anywhere on the list, so the fix is for the list to be the
 * screen.
 *
 * Nobody would report this. They would pull, see nothing happen, and decide
 * the app was slow.
 */
class PullToRefreshInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun post(guid: String, title: String) = Post(
        guid = guid,
        title = title,
        message = "words",
        author = "user2@example.com",
        group = null,
        likes = 0,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    )

    private fun wrapped(
        before: suspend () -> Unit = {},
        action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit,
    ) {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                before()
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = action,
        )
    }

    /**
     * Dragged from the middle of the screen, where there is nothing to grab.
     *
     * The gesture is aimed at the whole screen rather than at the empty
     * illustration: a person pulls the screen, and the illustration is only a
     * few hundred pixels of it — a drag that size never reaches the distance a
     * pull-to-refresh asks for, which would make this test pass or fail on the
     * size of a drawing.
     */
    private fun pullFromTheMiddle(
        rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        tag: String,
    ) {
        rule.onNodeWithTag(tag).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + (height / 2), durationMillis = 500)
        }
    }

    /**
     * A feed with one post can be pulled from below it, which is most of the
     * screen.
     *
     * Only this case: an empty screen was never broken, because the empty state
     * fills the height and the refresh reads the drag itself rather than
     * needing something scrollable underneath. A test for it passed with the
     * bug in place and failed one run in three on timing, which is a test that
     * costs attention and buys nothing.
     */
    @Test
    fun aShortFeedCanBePulledFromBelowTheLastPost() {
        wrapped(
            before = { TestUtils.seedPostAsSecondUser(post("pull-short-1", "The only post")) },
        ) { rule ->
            TestUtils.awaitTag(rule, "posts_list")
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 1
            }

            runBlocking { TestUtils.seedPostAsSecondUser(post("pull-short-2", "Arrived from below")) }
            // The list now fills the screen, so its centre is under the single
            // card rather than off the end of it.
            pullFromTheMiddle(rule, "feed_screen")

            rule.waitUntil(timeoutMillis = 15_000) {
                rule.onAllNodesWithText("Arrived from below").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

}
