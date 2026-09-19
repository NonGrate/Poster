package com.example.poster.util

import androidx.compose.ui.test.*
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.poster.MainActivity
import com.example.poster.auth.InMemoryAuthTokenStorage
import com.example.poster.ktor.KtorPostApi
import com.example.poster.ktor.KtorUserApi
import com.example.poster.ktor.createHttpClient
import com.example.poster.model.Post
import com.example.poster.repository.PostRepository
import com.example.poster.repository.TagRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.example.poster.ktor.KtorGroupApi

object TestUtils : KoinComponent {

    // Inject repositories through Koin
    private val postRepository: PostRepository by inject()
    private val tagRepository: TagRepository by inject()
    private val appPreferences: AppPreferences by inject()

    suspend fun seedPostAsSecondUser(post: Post) {
        val tokenStorage = InMemoryAuthTokenStorage()
        createHttpClient(TestServer.host, TestServer.port, authTokenStorage = tokenStorage).use { httpClient ->
            val userApi = KtorUserApi(httpClient, tokenStorage)
            check(userApi.authenticateUser("user2@example.com", "password123") != null)
            KtorPostApi(httpClient).addPost(post)
            userApi.logout()
        }
    }

    /** Removing a seeded post, as the author — the only person allowed to. */
    suspend fun deletePostAsSecondUser(post: Post) {
        val tokenStorage = InMemoryAuthTokenStorage()
        createHttpClient(TestServer.host, TestServer.port, authTokenStorage = tokenStorage).use { httpClient ->
            val userApi = KtorUserApi(httpClient, tokenStorage)
            check(userApi.authenticateUser("user2@example.com", "password123") != null)
            KtorPostApi(httpClient).removePost(post)
            userApi.logout()
        }
    }

    /** The author saying how it went, which only the author may do. */
    suspend fun completePostAsSecondUser(postId: String, message: String?) {
        val tokenStorage = InMemoryAuthTokenStorage()
        createHttpClient(TestServer.host, TestServer.port, authTokenStorage = tokenStorage).use { httpClient ->
            val userApi = KtorUserApi(httpClient, tokenStorage)
            check(userApi.authenticateUser("user2@example.com", "password123") != null)
            KtorPostApi(httpClient).completePost(postId, message)
            userApi.logout()
        }
    }

    suspend fun joinGroupAsTestUser(inviteCode: String) {
        val tokenStorage = InMemoryAuthTokenStorage()
        createHttpClient(TestServer.host, TestServer.port, authTokenStorage = tokenStorage).use { httpClient ->
            val userApi = KtorUserApi(httpClient, tokenStorage)
            val user = userApi.authenticateUser("test@example.com", "password123")
            check(user != null)
            val group = KtorGroupApi(httpClient).getGroupByInviteCode(inviteCode)
            check(group != null) { "no group for invite code $inviteCode" }
            KtorGroupApi(httpClient).addUserToGroup(user!!.guid, group!!.id)
            userApi.logout()
        }
    }

    /**
     * A group the test user owns, with the second user brought in as a
     * member — the shape the promote-to-admin controls need. Returns its id.
     * The fixture's group-a has no owner, so it grows no Manage panel; this one
     * does.
     */
    fun ownedGroupWithSecondMember(name: String): String = runBlocking {
        lateinit var groupId: String
        lateinit var code: String
        val ownerStorage = InMemoryAuthTokenStorage()
        createHttpClient(TestServer.host, TestServer.port, authTokenStorage = ownerStorage).use { httpClient ->
            val userApi = KtorUserApi(httpClient, ownerStorage)
            check(userApi.authenticateUser("test@example.com", "password123") != null)
            val group = checkNotNull(KtorGroupApi(httpClient).createGroup(name)) {
                "could not create the group"
            }
            groupId = group.id
            code = checkNotNull(KtorGroupApi(httpClient).createInvite(group.id)) {
                "could not create an invite"
            }
            userApi.logout()
        }
        val memberStorage = InMemoryAuthTokenStorage()
        createHttpClient(TestServer.host, TestServer.port, authTokenStorage = memberStorage).use { httpClient ->
            val userApi = KtorUserApi(httpClient, memberStorage)
            val member = checkNotNull(userApi.authenticateUser("user2@example.com", "password123"))
            check(
                KtorGroupApi(httpClient).joinWithInvite(member.guid, code) ==
                    com.example.poster.network.JoinResult.JOINED
            ) {
                "the second user could not join"
            }
            userApi.logout()
        }
        groupId
    }

    suspend fun leaveGroupAsTestUser(groupId: String) {
        val tokenStorage = InMemoryAuthTokenStorage()
        createHttpClient(TestServer.host, TestServer.port, authTokenStorage = tokenStorage).use { httpClient ->
            val userApi = KtorUserApi(httpClient, tokenStorage)
            val user = userApi.authenticateUser("test@example.com", "password123")
            check(user != null)
            KtorGroupApi(httpClient).removeUserFromGroup(user!!.guid, groupId)
            userApi.logout()
        }
    }

    fun runWrapped(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        before: suspend (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit = {},
        after: suspend (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit = {},
        action: (AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) -> Unit
    ) {
        try {
            applyServerFixtures()
            runBlocking {
                appPreferences.clear()
                // The database too, not just the caches. This used to be done
                // for us: signing out cleared it, and clearing preferences
                // looked like signing out. It no longer does — an app that
                // cannot reach the server must keep what it has — so a test
                // that wants a clean device now has to say so.
                postRepository.clearLocalPosts()
                tagRepository.clearCache()
            }
            runBlocking { before(composeTestRule) }
            action(composeTestRule)
        } catch (failure: Throwable) {
            // A failed test usually leaves the app somewhere `after` cannot
            // sign out from (a dialog, a sheet). Its own timeout must not
            // replace the failure that put us there.
            try {
                runBlocking { after(composeTestRule) }
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            } finally {
                clearAppState()
            }
            throw failure
        }
        try {
            runBlocking { after(composeTestRule) }
        } finally {
            // Always clean up, even if after() fails
            clearAppState()
        }
    }

    /**
     * Clears all app state using repositories instead of direct database access
     */
    private fun clearAppState() {
        runBlocking {
            try {
                val currentUserId = appPreferences.getUserId()

                // Clear preferences (logout user)
                appPreferences.clear()

                // Clear repository caches and underlying data
                postRepository.clearCache()
                postRepository.getAllPosts()
                    .onSuccess { posts ->
                        posts.forEach { postRepository.deletePost(it) }
                    }
                currentUserId?.let { userId ->
                    postRepository.getFavoritePosts()
                        .onSuccess { snapshot ->
                            snapshot.favoritePosts.forEach { post ->
                                postRepository.removeFavorite(userId, post.guid)
                            }
                        }
                }

                println("TestUtils: App state cleared successfully using repositories")
            } catch (e: Exception) {
                println("TestUtils: Error clearing app state: ${e.message}")
                // Don't throw - we want tests to continue even if cleanup partially fails
            }
        }
    }

    /**
     * Performs logout via UI navigation with fallback to programmatic logout
     */
    fun performLogout(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        try {
            // Check if we're already on login screen
            if (composeTestRule.onAllNodesWithTag("login_field").fetchSemanticsNodes().isNotEmpty()) {
                return // Already logged out
            }

            // Try UI logout first
            performUILogout(composeTestRule)

        } catch (e: Throwable) { // ComposeTimeoutException extends Throwable, not Exception
            println("TestUtils: UI logout failed, falling back to programmatic logout: ${e.message}")
            // Fallback: Clear preferences programmatically
            performProgrammaticLogout(composeTestRule)
        }
    }

    /**
     * Performs logout via UI navigation
     */
    private fun performUILogout(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        // Navigate to settings
        composeTestRule.onNodeWithTag("settings_tab").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Click logout button, then confirm: signing out asks first now
        composeTestRule.onNodeWithTag("logout_button").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithTag("confirm_sign_out_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("confirm_sign_out_button").performClick()

        // Wait for login screen to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("login_field").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Performs programmatic logout by clearing preferences through repository
     */
    private fun performProgrammaticLogout(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        runBlocking { appPreferences.clear() }

        // Give the app a moment to react to the preferences change
        composeTestRule.waitForIdle()

        // Wait for login screen to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("login_field").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Performs login with test credentials
     */
    fun performLogin(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        email: String = "test@example.com",
        password: String = "password123"
    ) {
        // Wait for login screen to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("login_field").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("login_field").performTextInput(email)

        // Enter password (though it might not be used in the current implementation)
        composeTestRule.onNodeWithTag("password_field").performTextInput(password)

        // Click login button
        composeTestRule.onNodeWithTag("login_button").performClick()

        // Wait for login to complete and navigate to feed.
        //
        // Reported with what is on screen instead of a bare timeout: "the feed
        // did not appear" is the same message whether the password was wrong,
        // the backend was down, or a dialog is covering everything, and the
        // difference is exactly what somebody debugging needs.
        try {
            composeTestRule.waitUntil(timeoutMillis = 10000) {
                composeTestRule.onAllNodesWithTag("feed_screen").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: Exception) {
            val loginError = composeTestRule.onAllNodesWithTag("login_error_message").fetchSemanticsNodes()
            error(
                "Signing in as $email never reached the feed. " +
                    (if (loginError.isNotEmpty()) "The screen shows a login error. " else "") +
                    "On screen:\n" + composeTestRule.onRoot().printToString(maxDepth = 12),
            )
        }
    }

    /** Performs registration through the real registration UI. */
    fun performRegistration(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        name: String = "Test",
        surname: String = "User",
        email: String = "newuser@example.com",
        password: String = "password123",
        groupCode: String? = null
    ) {
        // Click register button
        composeTestRule.onNodeWithTag("register_button").performClick()
        composeTestRule.onNodeWithTag("register_name_field").performTextInput(name)
        composeTestRule.onNodeWithTag("register_surname_field").performTextInput(surname)
        composeTestRule.onNodeWithTag("register_email_field").performTextInput(email)
        composeTestRule.onNodeWithTag("register_password_field").performTextInput(password)
        composeTestRule.onNodeWithTag("register_password_repeat_field").performTextInput(password)
        groupCode?.let {
            composeTestRule.onNodeWithTag("register_group_code_field").performTextInput(it)
        }
        composeTestRule.onNodeWithTag("registration_submit_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag("feed_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Navigates to settings screen
     */
    fun navigateToSettings(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        composeTestRule.onNodeWithTag("settings_tab").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Navigates to favourites screen
     */
    /**
     * Waits for a tag to exist, then asserts it.
     *
     * Screens here arrive after a network round trip, so asserting the instant
     * after navigating samples a tree that is still being built. That produced
     * failures that always passed in isolation — including the confusing
     * "found it in the unmerged tree" ones, which is what a half-composed
     * subtree looks like.
     */
    fun awaitTag(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        tag: String,
        timeoutMillis: Long = 5000,
    ) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(tag).assertExists()
    }

    fun navigateToFavorites(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        composeTestRule.onNodeWithTag("favourites_tab").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("favourites_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Navigates to favourites screen
     */
    /**
     * Waits for at least one node with the tag. [awaitTag] insists on exactly
     * one, which is right for a screen and wrong for a list of cards.
     */
    fun awaitAnyTag(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        tag: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeTestRule.waitUntil(timeoutMillis) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun navigateToHome(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        composeTestRule.onNodeWithTag("feed_tab").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("feed_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    fun navigateToMyPosts(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        composeTestRule.onNodeWithTag("my_posts_tab").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag("my_posts_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
