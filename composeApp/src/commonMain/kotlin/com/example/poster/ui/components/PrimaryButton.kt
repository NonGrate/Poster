package com.example.poster.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's one primary (filled) button.
 *
 * A thin wrapper over [Button] whose only job is a defined disabled state:
 * Material's default disables a button by dropping the fill to onSurface at 12%,
 * which on the cream page is a faint grey wash under a 38%-ink label — about
 * 2.4:1, below the 4.5:1 floor. It reads as broken rather than waiting, and it is
 * the first control a new user meets (Login, Register, Save, Create).
 *
 * Here the disabled fill is a real warm surface and the label stays at the
 * standard 38% ink, so an inactive button looks inactive, not broken. Enabled
 * colours are untouched — the primary brown, everywhere it was before.
 *
 * One place, so the disabled look cannot drift between the screens that use it.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        modifier = modifier,
        content = content,
    )
}
