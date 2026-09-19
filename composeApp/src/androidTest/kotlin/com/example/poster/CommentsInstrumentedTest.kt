package com.example.poster

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poster.model.Post
import com.example.poster.util.TestUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The thread under a post: write one, see it, take it back (feature.comments). */
@RunWith(AndroidJUnit4::class)
class CommentsInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val post = Post(
        guid = "commented-post",
        title = "A post worth a comment",
        message = "Say something",
        author = "user2@example.com",
        group = null,
        likes = 0,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tags = listOf("health"),
    )

    @Test
    fun aCommentAppearsUnderThePostAndCanBeRemovedByItsWriter() {
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
            rule.onNodeWithTag("posts_list").performScrollToNode(hasText("A post worth a comment"))
            rule.onNodeWithText("A post worth a comment").performClick()
            TestUtils.awaitTag(rule, "details_screen")
            TestUtils.awaitTag(rule, "comments_section")

            // Empty to start; the send button stays off until there are words.
            rule.onNodeWithTag("comments_empty").performScrollTo()
            rule.onNodeWithTag("comment_send").assertIsNotEnabled()

            rule.onNodeWithTag("comment_input").performScrollTo().performTextInput("Well said")
            rule.onNodeWithTag("comment_send").performClick()
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithTag("comment_text", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Well said", useUnmergedTree = true).performScrollTo()
            // Mine, so I can take it back.
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("You", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            // The remove button's tag carries the comment's id, which the test does
            // not know; there is exactly one remove button on screen, so match by prefix.
            rule.onNode(hasTagStartingWith("comment_delete_"), useUnmergedTree = true).performScrollTo().performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("comment_text", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
            TestUtils.awaitTag(rule, "comments_empty")
        }
    }
}

private fun hasTagStartingWith(prefix: String) = SemanticsMatcher("TestTag starts with '$prefix'") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}
