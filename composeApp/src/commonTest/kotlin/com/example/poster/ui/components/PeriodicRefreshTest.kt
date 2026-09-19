package com.example.poster.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The loop that keeps a screen current.
 *
 * The property worth protecting is not that it fires — it is that it *stops*.
 * A timer that outlives its screen leaves four of them polling behind the one
 * being looked at, on someone's phone, on their data.
 */
@OptIn(ExperimentalTestApi::class)
class PeriodicRefreshTest {

    @Test
    fun itFetchesOnArrivalAndKeepsGoing() = runComposeUiTest {
        var fetches = 0

        setContent {
            PeriodicRefresh(interval = 30.milliseconds) { fetches++ }
        }

        waitUntil(timeoutMillis = 5_000) { fetches >= 3 }
        assertTrue(fetches >= 3, "the loop stopped after $fetches fetches")
    }

    @Test
    fun leavingTheScreenStopsIt() = runComposeUiTest {
        var fetches = 0
        var onScreen by mutableStateOf(true)

        setContent {
            if (onScreen) {
                PeriodicRefresh(interval = 30.milliseconds) { fetches++ }
            }
        }

        waitUntil(timeoutMillis = 5_000) { fetches >= 2 }
        onScreen = false
        waitForIdle()

        val afterLeaving = fetches
        // Long enough that a loop still running would have fired several times.
        runCatching { waitUntil(timeoutMillis = 400) { fetches > afterLeaving } }
        assertEquals(
            afterLeaving,
            fetches,
            "the loop kept fetching after its screen was gone",
        )
    }
}
