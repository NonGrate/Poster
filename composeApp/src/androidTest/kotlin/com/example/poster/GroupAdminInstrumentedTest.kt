package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.poster.util.TestUtils
import org.junit.Rule
import kotlin.test.Test

/**
 * The owner promoting a member to admin, through the real Manage panel. The
 * server rules are covered by GroupAdminRoutesTest; this is the UI wiring —
 * the "Make admin" control appears and, once used, the member reads as admin.
 */
class GroupAdminInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private var groupId: String = ""

    @Test
    fun ownerPromotesAMemberToAdmin() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                groupId = TestUtils.ownedGroupWithSecondMember("Test Group")
                TestUtils.performLogin(it)
                TestUtils.navigateToSettings(it)
            },
            after = { TestUtils.performLogout(it) },
        ) { rule ->
            rule.onNodeWithTag("manage_groups_button").performScrollTo().performClick()
            TestUtils.awaitTag(rule, "group_management_screen")

            // The owned group grows a Manage panel; open it.
            rule.onNodeWithTag("manage_group_$groupId").performScrollTo().performClick()
            TestUtils.awaitTag(rule, "group_manage_panel")

            // The second user starts as a plain member — promote them.
            TestUtils.awaitTag(rule, "set_role_user2@example.com")
            rule.onNodeWithTag("set_role_user2@example.com").performClick()

            // The control flips: they are an admin now, so it offers to remove it.
            rule.waitUntil(timeoutMillis = 5000) {
                rule.onAllNodesWithText("Remove admin").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Remove admin").assertExists()
        }
    }
}
