package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.poster.config.Features
import com.example.poster.model.Post
import com.example.poster.util.TestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import kotlin.test.Test

/**
 * The Share action on a public post. The OS share sheet itself is outside the
 * app, so this checks the wiring up to it: a public post's overflow menu
 * carries Share.
 */
class ShareInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun onlyWithSharing() = assumeTrue(Features.SHARING)

    private val post = Post(
        guid = "shareable-post",
        title = "A shareable post",
        message = "For anyone to see and pass on.",
        author = "user2@example.com",
        group = null,
        likes = 0,
        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        tags = emptyList(),
    )

    @Test
    fun aPublicPostOffersShareInItsMenu() {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                runBlocking { TestUtils.seedPostAsSecondUser(post) }
                TestUtils.performLogin(it)
            },
            after = { TestUtils.performLogout(it) },
            seeded = listOf(post),
        ) { rule ->
            TestUtils.navigateToHome(rule)
            TestUtils.awaitAnyTag(rule, "post_card")
            rule.onNodeWithTag("posts_list").performScrollToNode(hasText("A shareable post"))
            rule.onNodeWithText("A shareable post").performClick()

            TestUtils.awaitTag(rule, "details_screen")
            rule.onNodeWithTag("post_overflow_button").performClick()

            TestUtils.awaitTag(rule, "share_post_button")
            rule.onNodeWithTag("share_post_button").assertExists()
        }
    }
}
