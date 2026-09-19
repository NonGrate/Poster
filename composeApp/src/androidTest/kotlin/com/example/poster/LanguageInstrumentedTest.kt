package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.util.TestUtils
import org.junit.Rule
import kotlin.test.Test

/**
 * Choosing what you read, and what you write.
 *
 * The rule worth protecting is the absence: somebody who reads one language is
 * never asked which language a post is in, because there is only one answer
 * and asking makes the form longer for nothing.
 */
class LanguageInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = { TestUtils.performLogin(it) },
            after = { TestUtils.performLogout(it) },
            action = action,
        )
    }

    /** The fixture account reads English only, so the form must not ask. */
    @Test
    fun readingOneLanguageMeansTheFormDoesNotAsk() {
        wrapped { rule ->
            rule.onNodeWithTag("my_posts_tab").performClick()
            rule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(rule, "post_form")

            rule.onAllNodesWithTag("post_language_field").assertCountEquals(0)
        }
    }

    /** Add a second language, and the question becomes worth asking. */
    @Test
    fun readingTwoLanguagesBringsThePickerBack() {
        wrapped { rule ->
            addRussian(rule)

            rule.onNodeWithTag("my_posts_tab").performClick()
            rule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(rule, "post_form")

            TestUtils.awaitTag(rule, "post_language_field")
            // Defaulted to what this person writes in, which is still English.
            rule.onNodeWithTag("post_language_en_selected").assertExists()
            rule.onNodeWithTag("post_language_ru").assertExists()
        }
    }

    /** What is chosen has to survive the round trip to the server and back. */
    @Test
    fun theLanguagesYouChooseAreRemembered() {
        wrapped { rule ->
            addRussian(rule)

            // Leave the screen and come back: this reads what the server stored,
            // not what the form was holding.
            rule.onNodeWithTag("feed_tab").performClick()
            TestUtils.awaitTag(rule, "feed_screen")
            rule.onNodeWithTag("settings_tab").performClick()
            TestUtils.awaitTag(rule, "settings_screen")
            rule.onNodeWithTag("edit_profile_row").performClick()
            TestUtils.awaitTag(rule, "profile_screen")

            rule.onNodeWithTag("language_ru_selected").assertExists()
            rule.onNodeWithTag("language_en_selected").assertExists()
        }
    }

    /** Turning off the last one would empty the feed with no way to see why. */
    @Test
    fun theLastLanguageCannotBeTurnedOff() {
        wrapped { rule ->
            openProfile(rule)

            rule.onNodeWithTag("language_en_selected").performClick()

            rule.onNodeWithTag("language_en_selected").assertExists()
        }
    }

    private fun addRussian(rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        openProfile(rule)
        rule.onNodeWithTag("language_ru").performClick()
        TestUtils.awaitTag(rule, "language_ru_selected")
        rule.onNodeWithTag("profile_save").performClick()
        TestUtils.awaitTag(rule, "profile_back")
        rule.onNodeWithTag("profile_back").performClick()
        TestUtils.awaitTag(rule, "settings_screen")
    }

    private fun openProfile(rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        rule.onNodeWithTag("settings_tab").performClick()
        TestUtils.awaitTag(rule, "settings_screen")
        rule.onNodeWithTag("edit_profile_row").performClick()
        TestUtils.awaitTag(rule, "profile_screen")
    }
}
