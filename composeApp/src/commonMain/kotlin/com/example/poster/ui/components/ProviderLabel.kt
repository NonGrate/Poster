package com.example.poster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/** A provider mark beside its label — the inside of a "Sign in with X" button. */
@Composable
fun ProviderLabel(mark: Painter, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painter = mark, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label)
    }
}
