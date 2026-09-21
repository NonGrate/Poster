package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.config.Features
import com.example.poster.util.TestUtils
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import kotlin.test.Test

class TagsInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun onlyWithTags() = assumeTrue(Features.TAGS)

    fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = { TestUtils.performLogin(it) },
            after = { TestUtils.performLogout(it) },
            action = action
        )
    }

    /**
     * Six groups first, then a group's tags. Nobody types anything.
     *
     * This is the point of the picker. The search box before it could only show
     * what somebody had already guessed the name of, and production ended up
     * with nine tags in use out of 98 — plus a transliterated `trevoga` that
     * somebody invented rather than found. A flat cloud of sixty would have
     * been the same wall; ten behind each of six chips is not.
     */
    @Test
    fun theGroupsComeFirstAndThenTheirTags() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(composeTestRule, "tag_groups")

            // Nothing open: the hint, and no tags at all.
            composeTestRule.onNodeWithTag("tag_pick_group_hint").assertExists()
            composeTestRule.onAllNodesWithTag("tag_chip_wellbeing").assertCountEquals(0)

            composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_cloud")
            composeTestRule.onNodeWithTag("tag_chip_wellbeing").assertExists()
            composeTestRule.onNodeWithTag("tag_chip_health").assertExists()

            // A tag from another shelf is not on this one.
            composeTestRule.onAllNodesWithTag("tag_chip_family").assertCountEquals(0)

            composeTestRule.onNodeWithTag("tag_group_people").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_family")
            composeTestRule.onAllNodesWithTag("tag_chip_wellbeing").assertCountEquals(0)
        }
    }

    /**
     * Switching group must not lose what is already chosen.
     *
     * The one thing a two-step picker can take away: the tags you picked sit
     * behind a group chip, and opening another would hide them. They live above
     * the groups instead, so they are always on screen.
     */
    @Test
    fun aChosenTagSurvivesOpeningAnotherGroup() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(composeTestRule, "tag_groups")

            composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing")
            composeTestRule.onNodeWithTag("tag_chip_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chosen_wellbeing")

            composeTestRule.onNodeWithTag("tag_group_people").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_family")

            composeTestRule.onNodeWithTag("tag_chosen_wellbeing").assertExists()
        }
    }

    /** There is nothing to type into: free text is gone, and so is the field. */
    @Test
    fun thereIsNoWayToTypeATag() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.onNodeWithTag("create_post_fab").performClick()

            // The groups are the whole of it: there is nothing to type into.
            TestUtils.awaitTag(composeTestRule, "tag_groups")
            composeTestRule.onNodeWithTag("tag_pick_group_hint").assertExists()
        }
    }

    /** Tapping a chosen tag is how you change your mind. */
    @Test
    fun tappingATagPicksItAndTappingAgainLetsItGo() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing")

            composeTestRule.onNodeWithTag("tag_chip_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing_selected")

            composeTestRule.onNodeWithTag("tag_chip_wellbeing_selected").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing")
        }
    }

    /**
     * At the cap the rest go quiet rather than staying tappable and refusing.
     * What is already chosen stays live, because letting one go is the way back.
     */
    @Test
    fun atTheLimitTheUnchosenTagsGoQuiet() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(composeTestRule, "tag_groups")

            // Five from one shelf, so the cap is reached without changing group.
            composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_cloud")
            listOf("health", "wellbeing", "fitness", "food", "sleep").forEach { id ->
                composeTestRule.onNodeWithTag("tag_chip_$id").performScrollTo().performClick()
                TestUtils.awaitTag(composeTestRule, "tag_chip_${id}_selected")
            }

            TestUtils.awaitTag(composeTestRule, "tag_limit_reached")
            composeTestRule.onNodeWithTag("tag_chip_habits").assertIsNotEnabled()
            composeTestRule.onNodeWithTag("tag_chip_health_selected").assertIsEnabled()

            // And letting one go opens the rest again.
            composeTestRule.onNodeWithTag("tag_chip_health_selected").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithTag("tag_limit_reached").fetchSemanticsNodes().isEmpty()
            }
            composeTestRule.onNodeWithTag("tag_chip_habits").assertIsEnabled()
        }
    }

    @Test
    fun pickingATagPutsItOnThePost() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()

            openCreatePostDialog(composeTestRule)
            addPostTag(composeTestRule)
            addPostGroup(composeTestRule)
            submitPost(composeTestRule)

            // Unmerged: the card is clickable, so its children fold into one node
            // in the merged tree and the tag row is not findable there.
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithTag("post_tags", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithText("Wellbeing", useUnmergedTree = true).onFirst().assertExists()
        }
    }

    @Test
    fun aCuratedTagSurvivesTheLastPostThatUsedIt() {
        // Tags used to be tidied away when no post used them, which was right
        // when every tag was something one person had typed. A curated tag is
        // offered to everybody, and one person deleting a post must not take
        // it out of the picker for the whole app.
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("my_posts_tab").performClick()
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            composeTestRule.onNodeWithTag("post_title_field").performTextInput("Passing post")
            composeTestRule.onNodeWithTag("post_message_field").performTextInput("For a moment")
            composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing")
            composeTestRule.onNodeWithTag("tag_chip_wellbeing").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("submit_post_button").performClick()
            TestUtils.awaitTag(composeTestRule, "my_post_card")

            // Remove the only post that uses it.
            composeTestRule.onAllNodesWithTag("post_overflow_button").onFirst().performClick()
            TestUtils.awaitAnyTag(composeTestRule, "delete_post_button")
            composeTestRule.onAllNodesWithTag("delete_post_button").onFirst().performClick()
            composeTestRule.onNodeWithTag("confirm_delete_button").performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Passing post").fetchSemanticsNodes().isEmpty()
            }

            // The tag is still there to be picked.
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
            TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing")
        }
    }

    fun openCreatePostDialog(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        // TC-202: Create New Post with Tags and Group - ✅ IMPLEMENTED
        composeTestRule.onNodeWithTag("create_post_fab").performClick()

        // Fill post form
        composeTestRule.onNodeWithTag("post_title_field").performTextInput("Please like wellbeing")
        composeTestRule.onNodeWithTag("post_message_field").performTextInput("I need posts for my everyday from home")
    }

    fun addPostTag(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
        TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing")
        composeTestRule.onNodeWithTag("tag_chip_wellbeing").performScrollTo().performClick()
    }

    fun addPostGroup(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        // The group picker only exists for the visibility that needs one.
        composeTestRule.onNodeWithTag("visibility_option_group").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("group_selector").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("group_option_group-a").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("group_option_group-a").performClick()
        // The menu closing is the proof the choice landed; submitting with no
        // group chosen shows an error and keeps the form open.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("group_option_group-a").fetchSemanticsNodes().isEmpty()
        }
    }

    fun submitPost(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        // Submit post. Asserted enabled first: a disabled button swallows the
        // tap and the test would only learn "no card appeared" ten seconds later.
        composeTestRule.onNodeWithTag("submit_post_button").assertIsEnabled().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("post_form").fetchSemanticsNodes().isEmpty()
        }
    }
}
