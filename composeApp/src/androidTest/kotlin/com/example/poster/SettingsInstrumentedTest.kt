package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.util.TestUtils
import org.junit.Rule
import kotlin.test.Test

class SettingsInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.performLogin(it)
                TestUtils.navigateToSettings(it)
            },
            after = { TestUtils.performLogout(it) },
            action = action
        )
    }

    @Test
    fun switchToDarkMode() {
        wrapped { composeTestRule ->
            // TC-401: Switch to Dark Mode. The theme follows the device until the
            // switch is turned off; only then does the light/dark selector appear.
            composeTestRule.onNodeWithTag("settings_screen").assertExists()
            composeTestRule.onNodeWithTag("theme_section").assertExists()
            composeTestRule.onNodeWithTag("follow_system_theme_toggle").performClick()
            TestUtils.awaitTag(composeTestRule, "theme_mode_selector")
            composeTestRule.onNodeWithTag("theme_dark_option").performClick()
            composeTestRule.onNodeWithTag("theme_dark_option").assertIsSelected()
        }
    }

    @Test
    fun switchToLightMode() {
        wrapped { composeTestRule ->
            // TC-402: Switch to Light Mode, after going dark first.
            composeTestRule.onNodeWithTag("follow_system_theme_toggle").performClick()
            TestUtils.awaitTag(composeTestRule, "theme_mode_selector")
            composeTestRule.onNodeWithTag("theme_dark_option").performClick()
            composeTestRule.onNodeWithTag("theme_light_option").performClick()
            composeTestRule.onNodeWithTag("theme_light_option").assertIsSelected()
        }
    }

    @Test
    fun viewGroupManagement() {
        wrapped { composeTestRule ->
            // TC-403: View Group Management - ✅ IMPLEMENTED
            composeTestRule.onNodeWithTag("group_management_section").assertExists()
            composeTestRule.onNodeWithTag("manage_groups_button").performClick()

            // Verify group management screen opens
            composeTestRule.onNodeWithTag("group_management_screen").assertExists()
            TestUtils.awaitTag(composeTestRule, "groups_list")
        }
    }

    @Test
    fun joinGroupViaCodeTest() {
        wrapped { composeTestRule ->
            joinGroupViaCode()
        }
    }

    @Test
    fun leaveGroup() {
        wrapped { composeTestRule ->
            // TC-405: Leave a Group - ✅ IMPLEMENTED
            // First join a group
            joinGroupViaCode()

            // Leave the group, from the row's overflow menu
            composeTestRule.onNodeWithTag("group_overflow_book-club").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "leave_group_book-club")
            composeTestRule.onNodeWithTag("leave_group_book-club").performClick()
            composeTestRule.onNodeWithTag("confirm_leave_group_button").performClick()

            // Verify group is removed
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithText("Book Club").fetchSemanticsNodes().isEmpty()
            }
            composeTestRule.onNodeWithText("Book Club").assertDoesNotExist()
        }
    }

    @Test
    fun postsHiddenAfterLeavingGroup() {
        wrapped { composeTestRule ->
            // TC-405A: Posts Hidden After Leaving a Group - ✅ IMPLEMENTED
            // This test would require existing posts from a group
            // Join a group, create a post, then leave the group
            joinGroupViaCode()

            // Go to My Posts and create a post for this group
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.onNodeWithTag("create_post_fab").performClick()

            composeTestRule.onNodeWithTag("post_title_field").performTextInput("Group Post")
            composeTestRule.onNodeWithTag("post_message_field").performTextInput("Post for new group")
            composeTestRule.onNodeWithTag("visibility_option_group").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("group_selector").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithTag("group_option_book-club").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("group_option_book-club").performClick()
            composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("tag_chip_wellbeing").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("submit_post_button").performClick()

            // Verify post exists
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Group Post").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Group Post").assertExists()

            // Leave the group
            TestUtils.navigateToSettings(composeTestRule)
            composeTestRule.onNodeWithTag("manage_groups_button").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "group_overflow_book-club")
            composeTestRule.onNodeWithTag("group_overflow_book-club").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "leave_group_book-club")
            composeTestRule.onNodeWithTag("leave_group_book-club").performClick()
            composeTestRule.onNodeWithTag("confirm_leave_group_button").performClick()

            // Go back to My Posts and verify post is hidden
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Group Post").fetchSemanticsNodes().isEmpty()
            }
            composeTestRule.onNodeWithText("Group Post").assertDoesNotExist()
        }
    }

    private fun joinGroupViaCode() {
        // TC-404: Join Group via Code - ✅ IMPLEMENTED
        composeTestRule.onNodeWithTag("manage_groups_button").performScrollTo().performClick()
        TestUtils.awaitTag(composeTestRule, "group_management_screen")

        // Joining lives in the "Join or create" sheet.
        composeTestRule.onNodeWithTag("add_group_button").performClick()
        TestUtils.awaitTag(composeTestRule, "group_code_input")
        composeTestRule.onNodeWithTag("group_code_input").performScrollTo().performTextInput("BOOK_CLUB_INVITE")
        composeTestRule.onNodeWithTag("join_group_submit_button").performScrollTo().performClick()

        // Verify group is added: its row (with the overflow menu) appears in the
        // list. The invite field clears once the join succeeds, so the code
        // itself is gone; the row is the evidence. "Leave" lives in that menu.
        TestUtils.awaitTag(composeTestRule, "group_overflow_book-club")
        composeTestRule.onNodeWithTag("group_overflow_book-club").assertExists()
    }
}
