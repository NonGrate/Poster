package com.example.poster

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.model.Post
import com.example.poster.repository.PostRepository
import com.example.poster.util.TestUtils
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.test.Test

class MyPostsInstrumentedTest : KoinComponent {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // Inject repository through Koin instead of manual instantiation
    private val postRepository: PostRepository by inject()

    val testPost = Post(
        "aaa-aaa",
        "Test post",
        "Test post message",
        "test-author",
        null,
        1,
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        emptyList(),
        isFavorite = true
    )

    fun wrapped(action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit) {
        TestUtils.runWrapped(
            composeTestRule,
            before = {
                TestUtils.performLogin(it)
                TestUtils.navigateToMyPosts(it)
            },
            after = { TestUtils.performLogout(it) },
            action = action
        )
    }

    private fun addPost() {
        runBlocking {
            postRepository.addPost(testPost)
        }
    }

    @Test
    fun viewMyPosts() {
        wrapped { composeTestRule ->
            // TC-201: View My Posts - ✅ IMPLEMENTED
            composeTestRule.onNodeWithTag("my_posts_screen").assertExists()
            composeTestRule.onNodeWithTag("empty_my_posts_state").assertExists()

            // Verify only user-created posts are shown
            composeTestRule.onAllNodesWithTag("my_post_card").assertCountEquals(0) // Initially empty
        }
    }

    @Test
    fun createNewPostWithNewTag() {
        wrapped { composeTestRule ->
            // TC-203: Create New Post with New Tag - ✅ IMPLEMENTED
            openCreatePostDialog(composeTestRule)
            addPostTag(composeTestRule)
            addPostGroup(composeTestRule)
            submitPost(composeTestRule)
            assertPostCreated(composeTestRule)

            // Verify new tag is created and linked
            composeTestRule.onAllNodesWithText("Wellbeing").onFirst().assertExists()
        }
    }

    @Test
    fun createNewPostWithTwoTags() {
        wrapped { composeTestRule ->
            // TC-253: Create New Post with Two Tags - ✅ IMPLEMENTED
            openCreatePostDialog(composeTestRule)
            addPostTag(composeTestRule)
            addSecondPostTag(composeTestRule)
            addPostGroup(composeTestRule)
            submitPost(composeTestRule)
            assertPostCreated(composeTestRule)

            // Verify new tag is created and linked. The list is lazy, so a card below
            // the fold is not composed at all — scroll to it before asserting.
            composeTestRule.onNodeWithTag("my_posts_list")
                .performScrollToNode(hasText("Family"))
            composeTestRule.onAllNodesWithText("Family").onFirst().assertExists()
            composeTestRule.onAllNodesWithText("Wellbeing").onFirst().assertExists()
        }
    }

    @Test
    fun editExistingPost() {
        addPost()
        wrapped { composeTestRule ->
            // TC-204: Edit Existing Post - ✅ IMPLEMENTED
            // First create a post
            openCreatePostDialog(composeTestRule)
            addPostTag(composeTestRule)
            addPostGroup(composeTestRule)
            submitPost(composeTestRule)
            assertPostCreated(composeTestRule)

            // Click edit on the post
            composeTestRule.onAllNodesWithTag("post_overflow_button").onFirst().performClick()
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithTag("edit_post_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithTag("edit_post_button").onFirst().performClick()

            // Edit the post
            composeTestRule.onNodeWithTag("post_title_field").performTextClearance()
            composeTestRule.onNodeWithTag("post_title_field").performTextInput("Updated post title")

            composeTestRule.onNodeWithTag("submit_post_button").performClick()

            // Verify updated data is reflected
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Updated post title").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Updated post title").assertExists()
        }
    }

    @Test
    fun deleteAPost() {
        wrapped { composeTestRule ->
            // TC-205: Delete a Post - ✅ IMPLEMENTED
            // First create a post
            openCreatePostDialog(composeTestRule)
            addPostTag(composeTestRule)
            addPostGroup(composeTestRule)
            submitPost(composeTestRule)
            assertPostCreated(composeTestRule)

            // Click delete on the post
            composeTestRule.onAllNodesWithTag("post_overflow_button").onFirst().performClick()
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithTag("delete_post_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodesWithTag("delete_post_button").onFirst().performClick()

            // Confirm deletion
            composeTestRule.onNodeWithTag("confirm_delete_button").performClick()

            // Verify post is removed
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Please like wellbeing").fetchSemanticsNodes().isEmpty()
            }
            composeTestRule.onNodeWithText("Please like wellbeing").assertDoesNotExist()
        }
    }

    @Test
    fun myPostsEmptyState() {
        wrapped { composeTestRule ->
            // TC-602: My Posts Empty - ✅ IMPLEMENTED
            composeTestRule.onNodeWithTag("empty_my_posts_state").assertExists()
            composeTestRule.onNodeWithText("Write your first post").assertExists()
            composeTestRule.onNodeWithTag("create_post_fab").assertExists()
        }
    }

    /**
     * An empty form cannot be saved, and says what it is waiting for.
     *
     * This used to press Save and read an error off the title field. Reporting
     * after the press is an invitation to press: the button is dead until the
     * post is one, and the line under it names the three things missing so
     * the deadness is explained rather than mysterious.
     */
    @Test
    fun anEmptyPostCannotBeSaved() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(composeTestRule, "post_title_field")

            composeTestRule.onNodeWithTag("submit_post_button").assertIsNotEnabled()
            composeTestRule.onNodeWithTag("post_form_incomplete").assertExists()
        }
    }

    /** Each of the three on its own is not enough. */
    @Test
    fun aPostNeedsAllThreeBeforeItCanBeSaved() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            TestUtils.awaitTag(composeTestRule, "post_title_field")

            composeTestRule.onNodeWithTag("post_title_field").performTextInput("A title")
            composeTestRule.onNodeWithTag("submit_post_button").assertIsNotEnabled()

            composeTestRule.onNodeWithTag("post_message_field").performTextInput("Some words")
            composeTestRule.onNodeWithTag("submit_post_button")
                .assertIsNotEnabled()  // still no tag

            addPostTag(composeTestRule)
            composeTestRule.onNodeWithTag("submit_post_button").assertIsEnabled()
            composeTestRule.onAllNodesWithTag("post_form_incomplete").assertCountEquals(0)
        }
    }

    /**
     * The default: public, no group. Every other test here picks a group,
     * which is how a filter that required one hid these entirely — Home does not
     * show your own posts, so they appeared nowhere.
     */
    @Test
    fun aPublicPostAppearsInMyPosts() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            composeTestRule.onNodeWithTag("post_title_field").performTextInput("A public post")
            composeTestRule.onNodeWithTag("post_message_field").performTextInput("For anyone")
            addPostTag(composeTestRule)
            composeTestRule.onNodeWithTag("submit_post_button").performClick()

            TestUtils.awaitTag(composeTestRule, "my_post_card")
            composeTestRule.onNodeWithText("A public post").assertExists()
        }
    }

    /**
     * The feed holds new content back so the list does not move while it is
     * being read. That button is on Home, and only Home — when My Posts read
     * the same held list, a person's own posts sat behind a button they could
     * not reach, and this screen simply looked empty.
     */
    @Test
    fun myPostsAreNotHeldBackByTheFeedsButton() {
        wrapped { composeTestRule ->
            composeTestRule.onNodeWithTag("create_post_fab").performClick()
            composeTestRule.onNodeWithTag("post_title_field").performTextInput("Mine to see")
            composeTestRule.onNodeWithTag("post_message_field").performTextInput("Please like")
            addPostTag(composeTestRule)
            composeTestRule.onNodeWithTag("submit_post_button").performClick()
            TestUtils.awaitTag(composeTestRule, "my_post_card")

            // Someone else posts, which is what puts the feed behind its button.
            runBlocking {
                TestUtils.seedPostAsSecondUser(
                    Post(
                        guid = "held-back-feed-post",
                        title = "Arrived from elsewhere",
                        message = "…",
                        author = "user2@example.com",
                        group = null,
                        likes = 0,
                        date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        tags = emptyList(),
                        isFavorite = false,
                    )
                )
            }
            TestUtils.navigateToHome(composeTestRule)
            TestUtils.navigateToMyPosts(composeTestRule)

            composeTestRule.onNodeWithText("Mine to see").assertExists()
        }
    }

    @Test
    fun createPostWithoutSelectingGroup() {
        wrapped { composeTestRule ->
            // TC-606: Create Post Without Selecting Group (if in multiple) - ✅ IMPLEMENTED
            composeTestRule.onNodeWithTag("create_post_fab").performClick()

            composeTestRule.onNodeWithTag("post_title_field").performTextInput("Test post")
            composeTestRule.onNodeWithTag("post_message_field").performTextInput("Test message")
            // A tag as well, or the button is dead for a reason this test is
            // not about — it is about the group that was never named.
            addPostTag(composeTestRule)

            // Ask for a group post, then submit without naming one
            composeTestRule.onNodeWithTag("visibility_option_group").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("submit_post_button").performClick()

            // Verify validation prevents submission
            composeTestRule.onNodeWithTag("group_error_message", useUnmergedTree = true).assertExists()
        }
    }

    fun openCreatePostDialog(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        // TC-202: Create New Post with Tags and Group - ✅ IMPLEMENTED
        composeTestRule.onNodeWithTag("create_post_fab").performClick()

        // Fill post form
        composeTestRule.onNodeWithTag("post_title_field").performTextInput("Please like wellbeing")
        composeTestRule.onNodeWithTag("post_message_field").performTextInput("I need posts for my everyday from home")
    }

    /** Searches for a curated tag and takes the first match, as a person does. */
    fun addPostTag(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        // The picker is two steps now: the group, then the tag on that shelf.
        composeTestRule.onNodeWithTag("tag_group_health_wellbeing").performScrollTo().performClick()
        TestUtils.awaitTag(composeTestRule, "tag_chip_wellbeing")
        composeTestRule.onNodeWithTag("tag_chip_wellbeing").performScrollTo().performClick()
    }

    /** A second tag, since a post can carry more than one. */
    fun addSecondPostTag(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        composeTestRule.onNodeWithTag("tag_group_people").performScrollTo().performClick()
        TestUtils.awaitTag(composeTestRule, "tag_chip_family")
        composeTestRule.onNodeWithTag("tag_chip_family").performScrollTo().performClick()
    }

    fun addPostGroup(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        // The group picker only exists for the visibility that needs one.
        composeTestRule.onNodeWithTag("visibility_option_group").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("group_selector").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("group_option_group-a").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("group_option_group-a").performClick()
    }

    fun submitPost(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        // Submit post
        composeTestRule.onNodeWithTag("submit_post_button").performClick()
    }

    fun assertPostCreated(composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Please like wellbeing").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Please like wellbeing").assertExists()
    }
}
