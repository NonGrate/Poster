package com.example.poster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.poster.theme.Spacing
import com.example.poster.theme.isApplePlatform

/**
 * One 64dp bar at the top of every screen, title in titleLarge.
 *
 * Replaces the 32sp headline each screen used to draw inline, which made the
 * title compete with the content beneath it and left every screen inventing its
 * own spacing.
 */
@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: @Composable (() -> Unit)? = null,
    /**
     * How opaque the inline title is. 1 by default. On an iOS root screen with a
     * large title below, this rides the scroll: 0 at rest (the large title is on
     * screen and the bar carries only the actions), fading to 1 once the large
     * title has scrolled away — the iOS large-title collapse.
     */
    titleAlpha: Float = 1f,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // A pushed iOS screen centres its title, chevron leading and actions
    // trailing — the HIG convention. Root screens (no back affordance) and
    // Android everywhere keep the leading title.
    if (isApplePlatform && navigation != null) {
        Box(
            modifier = modifier.fillMaxWidth().height(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                // Held clear of the leading/trailing controls so a long title
                // ellipsises rather than sliding under them.
                modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = Spacing.xxs, end = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigation()
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = if (navigation == null) Spacing.md else Spacing.xxs, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        navigation?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alpha(titleAlpha),
        )
        actions()
    }
}

/**
 * The big title that sits at the top of an iOS root screen's scroll and slides
 * away as it is scrolled — the other half of the large-title collapse, the inline
 * title in [ScreenTopBar] taking over once this is gone.
 *
 * Meant to be the first item of the screen's LazyColumn so it scrolls with the
 * content. iOS only; Android root screens keep their single compact title.
 */
@Composable
fun LargePageTitle(
    title: String,
    modifier: Modifier = Modifier,
    /** 0 at rest, 1 when fully collapsed — fades the title out as it scrolls. */
    collapseFraction: Float = 0f,
) {
    Text(
        text = title,
        // The iOS large-title weight and size, in SF (the app's iOS type family).
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Fades out as it scrolls up, crossfading with the inline title in the
        // bar rather than clipping under it and letting a small title pop in.
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 1f - collapseFraction }
            .padding(top = Spacing.xs, bottom = Spacing.sm),
    )
}
