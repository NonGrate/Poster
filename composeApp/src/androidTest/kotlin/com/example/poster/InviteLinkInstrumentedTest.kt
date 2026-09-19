package com.example.poster

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poster.invite.InviteLink
import com.example.poster.util.TestUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An invite arrives from a chat app, so whoever taps it is usually not signed in
 * yet. The code has to survive the sign-in that follows.
 */
@RunWith(AndroidJUnit4::class)
class InviteLinkInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun anInviteReceivedWhileSignedOutIsHonouredAfterSigningIn() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                // What MainActivity does with the Intent's data, minus the Intent.
                InviteLink.offer("poster://join/BOOK_CLUB_INVITE")
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                TestUtils.awaitTag(rule, "invite_result_dialog")
                rule.onNodeWithTag("invite_result_message").assertIsDisplayed()

                // Dismiss, then confirm the membership is real, not just a message.
                rule.onNodeWithText("OK").performClick()
                TestUtils.navigateToSettings(rule)
                rule.onNodeWithTag("manage_groups_button").performScrollTo().performClick()
                TestUtils.awaitTag(rule, "group_management_screen")
                // The group's row, with its overflow menu, is the membership made visible.
                TestUtils.awaitTag(rule, "group_overflow_book-club")
            },
        )
    }
}
