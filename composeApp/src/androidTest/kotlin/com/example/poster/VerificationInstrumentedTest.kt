package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.config.Features
import com.example.poster.util.TestUtils
import org.junit.Assume.assumeTrue
import org.junit.Rule
import kotlin.test.Test

/**
 * What somebody who has not confirmed their address actually experiences.
 *
 * The server refuses the write, which is the part that holds — a modified app
 * could ignore any flag. This is the other half: that the refusal reads as
 * something to do rather than as a failure.
 */
class VerificationInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Registering makes an unconfirmed account, and the fixtures do not confirm
     * this one — unlike the seeded pair, which exist so other suites can post.
     */
    private fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = { },
            after = { },
            action = action,
        )
    }

    @Test
    fun anUnconfirmedAccountIsToldWhatToDoRatherThanThatItFailed() {
        // The other test here is about the way back in, which has nothing to
        // do with confirming an address.
        assumeTrue(Features.EMAIL_VERIFICATION_REQUIRED)
        wrapped { rule ->
            TestUtils.awaitTag(rule, "login_screen")
            rule.onNodeWithTag("register_button").performClick()
            TestUtils.awaitTag(rule, "registration_dialog")
            rule.onNodeWithTag("register_name_field").performTextInput("New")
            rule.onNodeWithTag("register_surname_field").performTextInput("Member")
            rule.onNodeWithTag("register_email_field").performTextInput("unconfirmed@example.com")
            rule.onNodeWithTag("register_password_field").performTextInput("password123")
            // Typed twice now, or the button stays dead.
            rule.onNodeWithTag("register_password_repeat_field").performTextInput("password123")
            rule.onNodeWithTag("registration_submit_button").performClick()
            TestUtils.awaitTag(rule, "feed_screen")

            // Reading works: nobody is locked out of an app they were invited to.
            rule.onNodeWithTag("my_posts_tab").performClick()
            TestUtils.awaitTag(rule, "my_posts_screen")

            rule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(rule, "post_form")
            rule.onNodeWithTag("post_title_field").performTextInput("A post")
            rule.onNodeWithTag("post_message_field").performTextInput("Please like")
            // A tag too: a post without one cannot be saved, and this test is
            // about what the server says to an unverified address, not about
            // the form.
            rule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(rule, "tag_chip_wellbeing")
            rule.onNodeWithTag("tag_chip_wellbeing").performScrollTo().performClick()
            rule.onNodeWithTag("submit_post_button").performClick()

            // Not "unable to add post": a way forward, with the email offered
            // again, because the usual reason for being here is that it never
            // arrived.
            TestUtils.awaitTag(rule, "verify_needed_dialog")
            rule.onNodeWithTag("verify_needed_resend").assertExists()
            rule.onNodeWithTag("verify_needed_resend").performClick()
            TestUtils.awaitTag(rule, "verify_needed_sent")
        }
    }

    /** The way back in, for somebody who cannot remember their password. */
    @Test
    fun theLoginScreenOffersAWayBackIn() {
        wrapped { rule ->
            TestUtils.awaitTag(rule, "login_screen")

            rule.onNodeWithTag("forgot_password_button").performClick()
            TestUtils.awaitTag(rule, "forgot_password_dialog")
            rule.onNodeWithTag("forgot_password_field").performTextInput("test@example.com")
            rule.onNodeWithTag("forgot_password_submit").performClick()

            // The same words whatever the address: telling somebody it is not
            // registered would answer, for anybody who asks, which addresses are.
            TestUtils.awaitTag(rule, "forgot_password_sent")
        }
    }
}
