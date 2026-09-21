package com.example.poster.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The spinner that sits inside a button while it is working.
 *
 * Sized to the text beside it rather than to the default, which is large enough
 * to change the button's height and make the whole screen jump the moment
 * somebody presses it.
 *
 * It takes the button's own content colour, so it stays legible on a filled
 * button and on an outlined one without either being told about the other.
 */
@Composable
fun BusySpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = LocalContentColor.current,
    )
    Spacer(modifier = Modifier.width(8.dp))
}
