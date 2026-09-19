package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.util.TestUtils
import org.junit.Rule
import kotlin.test.Test

class LoginScreenInstrumentedTest {
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
    fun loginAndPasswordFieldsAreShown() {
        wrapped { composeTestRule ->
            // Verify login screen elements are displayed
            composeTestRule.onNodeWithTag("login_field").assertExists()
            composeTestRule.onNodeWithTag("password_field").assertExists()
            composeTestRule.onNodeWithTag("login_button").assertExists()
            composeTestRule.onNodeWithTag("register_button").assertExists()
        }
    }

    @Test
    fun launchAppAndCheckLoginScreen() {
        wrapped { composeTestRule ->
            // Test that launches the app on the android emulator and checks that login and password fields are shown
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodesWithTag("login_field").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithTag("login_field").assertExists()
            composeTestRule.onNodeWithTag("password_field").assertExists()
            composeTestRule.onNodeWithTag("login_button").assertExists()
        }
    }
}
