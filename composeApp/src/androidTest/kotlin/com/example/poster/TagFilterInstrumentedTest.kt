package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.config.Features
import com.example.poster.model.Post
import com.example.poster.util.TestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.example.poster.repository.PostRepository
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.test.Test

/**
 * Narrowing the feed to one tag.
 *
 * This is what the curated set was for: "health" and "здоровье" are one tag, so
 * a filter over them narrows the feed instead of splitting it.
 */
class TagFilterInstrumentedTest : KoinComponent {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun onlyWithTags() = assumeTrue(Features.TAGS)

    private val repository: PostRepository by inject()

    private fun post(guid: String, title: String, tags: List<String>) = Post(
        guid = guid,
        title = title,
        message = "Please like",
        // Somebody else's: the feed does not show you your own.
        author = "user2@example.com",
        group = null,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tags = tags,
    )

    private fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.seedPostAsSecondUser(post("filter-health", "For my mother", listOf("health")))
                TestUtils.seedPostAsSecondUser(post("filter-work", "A new job", listOf("work")))
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            action = action,
        )
    }

    /** Opens the filter, opens the tag's group, and ticks the tag. */
    private fun filterBy(
        rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        group: String,
        tag: String,
    ) {
        TestUtils.awaitTag(rule, "feed_filter_toggle")
        rule.onNodeWithTag("feed_filter_toggle").performClick()
        TestUtils.awaitTag(rule, "feed_filter_groups")
        rule.onNodeWithTag("feed_filter_group_$group").performScrollTo().performClick()
        TestUtils.awaitTag(rule, "feed_filter_tag_$tag")
        rule.onNodeWithTag("feed_filter_tag_$tag").performScrollTo().performClick()
        dismissSheet(rule)
    }

    /** Opens the filter and takes a whole group, ticking nothing inside it. */
    private fun filterByGroup(
        rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        group: String,
    ) {
        TestUtils.awaitTag(rule, "feed_filter_toggle")
        rule.onNodeWithTag("feed_filter_toggle").performClick()
        TestUtils.awaitTag(rule, "feed_filter_groups")
        rule.onNodeWithTag("feed_filter_group_$group").performScrollTo().performClick()
        TestUtils.awaitTag(rule, "feed_filter_tags")
        dismissSheet(rule)
    }

    /**
     * The filter is a bottom sheet now, so it sits over the feed while it is
     * open. Everything these tests check is on the feed behind it, and the
     * sheet's own button is what closes it — there is nothing to apply, because
     * the feed has been changing on every tap.
     */
    private fun dismissSheet(
        rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    ) {
        TestUtils.awaitTag(rule, "filter_show_button")
        rule.onNodeWithTag("filter_show_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("feed_filter_sheet").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun choosingATagNarrowsTheFeedToIt() {
        wrapped { rule ->
            TestUtils.awaitTag(rule, "posts_list")
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 2
            }

            filterBy(rule, "health_wellbeing", "health")

            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 1
            }
            rule.onNodeWithText("For my mother").assertExists()
            rule.onAllNodesWithText("A new job").assertCountEquals(0)
        }
    }

    /** The filter in force stays on screen, and clearing it brings everything back. */
    @Test
    fun clearingTheFilterShowsTheWholeFeed() {
        wrapped { rule ->
            filterBy(rule, "health_wellbeing", "health")
            TestUtils.awaitTag(rule, "feed_filter_active")

            // The chip that is in force lives on the feed now rather than
            // inside the filter: tapping it is how you undo one thing without
            // opening anything.
            rule.onNodeWithTag("feed_filter_active_health").performClick()

            // One thing at a time: the tag goes, the category it was ticked in
            // stays ("everything in this category"), so the feed is still narrowed
            // to the one post carrying a Health and wellbeing tag.
            TestUtils.awaitTag(rule, "feed_filter_active_group")
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 1
            }
            rule.onNodeWithTag("feed_filter_active_group").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 2
            }
        }
    }

    /**
     * A tag nothing on this page carries is still offered.
     *
     * The opposite of what this screen used to do, and deliberately: the feed is
     * a page now, so "no post here carries it" says nothing about whether the
     * server has one. Hiding the tag made those posts unreachable.
     */
    @Test
    fun aTagNoPostOnThisPageCarriesIsStillOffered() {
        wrapped { rule ->
            TestUtils.awaitTag(rule, "feed_filter_toggle")
            rule.onNodeWithTag("feed_filter_toggle").performClick()
            TestUtils.awaitTag(rule, "feed_filter_groups")

            rule.onNodeWithTag("feed_filter_group_hobbies").performScrollTo().performClick()

            TestUtils.awaitTag(rule, "feed_filter_tag_books")
        }
    }

    /**
     * A group with nothing ticked inside it is the whole group.
     *
     * The empty case had to be decided deliberately: falling back to "no
     * filter" would widen to the entire feed the moment somebody unticked their
     * last tag, which reads as the filter breaking rather than widening.
     */
    @Test
    fun aGroupOnItsOwnTakesEveryPostInIt() {
        wrapped { rule ->
            TestUtils.awaitTag(rule, "posts_list")
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 2
            }

            filterByGroup(rule, "health_wellbeing")

            // "For my mother" carries health; "A new job" does not.
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 1
            }
            rule.onNodeWithText("For my mother").assertExists()
        }
    }

    /** Ticking tags inside the open group narrows it further. */
    @Test
    fun tagsInsideAGroupNarrowItFurther() {
        wrapped { rule ->
            filterByGroup(rule, "health_wellbeing")
            TestUtils.awaitTag(rule, "feed_filter_active_group")

            // The tags of the open category are inside the sheet; open it again to tick one.
            rule.onNodeWithTag("feed_filter_toggle").performClick()
            TestUtils.awaitTag(rule, "feed_filter_tags")
            rule.onNodeWithTag("feed_filter_tag_sleep").performScrollTo().performClick()
            dismissSheet(rule)

            // Nothing carries addiction, so the group's one post goes too.
            TestUtils.awaitTag(rule, "empty_tag_filter_state")
        }
    }

    /**
     * The feed is not empty — the filter is. Saying "a quiet feed, for now" over
     * posts sitting behind a chip would read as them having gone missing.
     */
    @Test
    fun aFilterMatchingNothingSaysSoAndOffersTheWayBack() {
        wrapped { rule ->
            filterBy(rule, "health_wellbeing", "health")
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("post_card").fetchSemanticsNodes().size == 1
            }

            // The post carrying the chosen tag leaves the feed under us.
            // Driven rather than swiped: a pull gesture that does not quite
            // trigger looks exactly like a refresh that found nothing.
            runBlocking {
                TestUtils.deletePostAsSecondUser(post("filter-health", "For my mother", listOf("health")))
                repository.refreshPosts()
            }

            TestUtils.awaitTag(rule, "empty_tag_filter_state")
            rule.onAllNodesWithTag("empty_feed_state").assertCountEquals(0)
        }
    }
}
