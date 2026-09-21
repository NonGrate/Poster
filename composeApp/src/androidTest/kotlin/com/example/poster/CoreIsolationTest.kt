package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.util.TestUtils
import org.junit.Rule
import kotlin.test.Test

class CoreIsolationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            after = { TestUtils.performLogout(it) },
            action = action
        )
    }

    @Test
    fun testIsolation_loginFieldsAlwaysVisible() {
        wrapped { composeTestRule ->
            // Verify clean state - login screen should always be visible
            composeTestRule.onNodeWithTag("login_field").assertExists()
            composeTestRule.onNodeWithTag("password_field").assertExists()
            composeTestRule.onNodeWithTag("login_button").assertExists()
        }
    }

    @Test
    fun testIsolation_loginAndLogoutCycle() {
        wrapped { composeTestRule ->
            // Test full login/logout cycle
            TestUtils.performLogin(composeTestRule)

            // Verify we're logged in
            composeTestRule.onNodeWithTag("feed_screen").assertExists()

            // This test will auto-logout via the wrapper
        }
    }

    @Test
    fun testIsolation_secondTestAlsoHasCleanState() {
        wrapped { composeTestRule ->
            // This test should start clean regardless of previous test
            composeTestRule.onNodeWithTag("login_field").assertExists()
            composeTestRule.onNodeWithTag("password_field").assertExists()
        }
    }
}
