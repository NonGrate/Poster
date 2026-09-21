package com.example.poster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a single column of content is allowed to get. Below this a phone
 * never reaches the cap, so the clamp is invisible there; above it — a tablet in
 * particular — it keeps line length readable and the content centred instead of
 * stretched edge to edge.
 */
val ContentMaxWidth: Dp = 640.dp

/**
 * Centres [content] and caps its width at [maxWidth]. A no-op on a phone, whose
 * screen is narrower than the cap, so it is safe to wrap a whole screen in one.
 * The caller keeps painting any full-bleed background behind it; only the content
 * inside is clamped.
 */
@Composable
fun ReadableWidth(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ContentMaxWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // widthIn before fillMaxSize: the cap has to narrow the incoming max
        // constraint first, or fillMaxSize fixes the width to fill and the cap
        // does nothing.
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
    }
}
