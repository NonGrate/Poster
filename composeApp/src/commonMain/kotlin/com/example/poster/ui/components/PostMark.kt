package com.example.poster.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.poster_mark
import poster.composeapp.generated.resources.poster_mark_dark

/**
 * The app mark, lit for the current theme.
 *
 * The light mark's washes are radial glows; on a dark background they mound
 * around the centre dots and read as anatomy, so dark mode uses a flat-tinted
 * variant instead. Keyed off the actual theme in effect (the background's
 * luminance) rather than the system setting, because the app's theme is its own
 * switch and the two can differ.
 */
@Composable
fun postMarkPainter(): Painter = painterResource(
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Res.drawable.poster_mark_dark
    } else {
        Res.drawable.poster_mark
    },
)
