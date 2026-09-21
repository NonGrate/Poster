package com.example.poster.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How often a screen left open asks the server whether anything changed. */
val REFRESH_INTERVAL: Duration = 5.minutes

/**
 * Fetches on arrival, then every [interval] for as long as the screen is there.
 *
 * The loop lives in the composition on purpose: leaving a screen cancels it, so
 * four screens cannot end up polling forever behind the one being looked at.
 *
 * What it fetches goes to the database, not to what is on screen. On the feed
 * that means a refresh surfaces as the "new posts" button rather than moving
 * the list under whoever is reading it.
 */
@Composable
fun PeriodicRefresh(
    key: Any? = Unit,
    interval: Duration = REFRESH_INTERVAL,
    onRefresh: () -> Unit,
) {
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(key, interval) {
        while (true) {
            refresh()
            delay(interval)
        }
    }
}
