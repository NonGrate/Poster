package com.example.poster.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.poster.theme.Spacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * An empty screen is still a screen: art, a heading, one line explaining what
 * will appear here, and one thing to do about it — instead of a grey sentence
 * floating in the middle.
 */
@Composable
fun EmptyState(
    art: DrawableResource,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionFilled: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            modifier = Modifier.width(264.dp).height(198.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            if (actionFilled) {
                PrimaryButton(onClick = onAction, modifier = Modifier.height(40.dp)) { Text(actionLabel) }
            } else {
                FilledTonalButton(onClick = onAction, modifier = Modifier.height(40.dp)) { Text(actionLabel) }
            }
        }
    }
}

/**
 * What a signed-in-only list says to a signed-out reader: "Sign in" and the
 * name of the thing they would see. My Posts and Liked each carried their own
 * copy of this Box-in-a-Column.
 */
@Composable
fun SignedOutPlaceholder(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
