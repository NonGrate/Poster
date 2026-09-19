package com.example.poster

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.poster.network.GroupApi
import com.example.poster.util.TestUtils
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A moderator adds someone to a group on the server, with no channel to tell
 * the phone. The app notices by comparing its membership against what this
 * device was last told, and says so the next time that person signs in.
 */
@RunWith(AndroidJUnit4::class)
class GroupAddedInstrumentedTest : KoinComponent {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val groupApi: GroupApi by inject()

    @Test
    fun beingAddedToAGroupIsAnnouncedOnTheNextSignIn() {
        TestUtils.runWrapped(
            composeTestRule,
            before = { TestUtils.performLogin(it) },
            after = { TestUtils.performLogout(it) },
            action = { rule ->
                // The first sign-in records the existing membership in silence —
                // announcing it would mean announcing everything on a new device.
                TestUtils.awaitTag(rule, "feed_screen")
                rule.onAllNodesWithTag("invite_result_dialog").fetchSemanticsNodes().let {
                    assert(it.isEmpty()) { "nothing should be announced on a first sign-in" }
                }

                runBlocking {
                    groupApi.addUserToGroup("test@example.com", "book-club")
                }

                TestUtils.performLogout(rule)
                TestUtils.performLogin(rule)

                TestUtils.awaitTag(rule, "invite_result_dialog")
                rule.onNodeWithTag("invite_result_message").assertIsDisplayed()
            },
        )
    }
}
