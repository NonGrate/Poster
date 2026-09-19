package com.example.poster

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poster.util.TestUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Closing the post form keeps the words for next time (feature.drafts). */
@RunWith(AndroidJUnit4::class)
class DraftsInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aClosedFormComesBackWithWhatWasTyped_andEmptyOnceCleared() {
        TestUtils.runWrapped(
            composeTestRule,
            before = { TestUtils.performLogin(it) },
            after = { TestUtils.performLogout(it) },
        ) { rule ->
            TestUtils.navigateToMyPosts(rule)
            TestUtils.awaitTag(rule, "create_post_fab")
            rule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(rule, "post_title_field")
            rule.onNodeWithTag("post_title_field").performTextInput("Half a thought")
            rule.onNodeWithTag("close_form_button").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag("post_form").fetchSemanticsNodes().isEmpty() }

            rule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(rule, "post_title_field")
            rule.onNodeWithTag("post_title_field").assertTextContains("Half a thought")

            // Emptied and closed: nothing to keep, so the next form is blank.
            rule.onNodeWithTag("post_title_field").performTextClearance()
            rule.onNodeWithTag("close_form_button").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag("post_form").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(rule, "post_title_field")
            rule.onNodeWithTag("post_title_field").assertTextContains("")
            rule.onNodeWithTag("close_form_button").performClick()
        }
    }
}
