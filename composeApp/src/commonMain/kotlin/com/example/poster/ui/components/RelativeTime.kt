package com.example.poster.ui.components

import androidx.compose.runtime.Composable
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.time_days_ago
import poster.composeapp.generated.resources.time_months_ago
import poster.composeapp.generated.resources.time_today
import poster.composeapp.generated.resources.time_weeks_ago
import poster.composeapp.generated.resources.time_yesterday
import poster.composeapp.generated.resources.time_years_ago

/**
 * How long ago, in words: "Today", "Yesterday", "3 days ago".
 *
 * Coarse on purpose — a post is not a chat message, and "3 days ago" is what
 * somebody wants to know, not the minute. Localised with plurals so Russian gets
 * its one/few/many forms rather than an English "days" glued to a number.
 *
 * The clock is read on every recomposition, which is enough: nothing here needs
 * to tick, and the screen recomposes often enough that a day-old post never
 * reads a day stale.
 */
@Composable
fun relativeTime(date: LocalDateTime): String {
    val then = date.toInstant(TimeZone.currentSystemDefault())
    val days = (Clock.System.now().epochSeconds - then.epochSeconds) / 86_400L
    return when {
        days <= 0L -> stringResource(Res.string.time_today)
        days == 1L -> stringResource(Res.string.time_yesterday)
        days < 7L -> pluralStringResource(Res.plurals.time_days_ago, days.toInt(), days.toInt())
        days < 30L -> (days / 7L).toInt().let { pluralStringResource(Res.plurals.time_weeks_ago, it, it) }
        days < 365L -> (days / 30L).toInt().let { pluralStringResource(Res.plurals.time_months_ago, it, it) }
        else -> (days / 365L).toInt().let { pluralStringResource(Res.plurals.time_years_ago, it, it) }
    }
}
