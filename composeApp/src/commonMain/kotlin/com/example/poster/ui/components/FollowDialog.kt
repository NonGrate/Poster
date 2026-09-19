package com.example.poster.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.follow_action
import poster.composeapp.generated.resources.follow_body
import poster.composeapp.generated.resources.unfollow_action

/** Follow or unfollow the author of a post (feature.follows). */
@Composable
fun FollowDialog(authorName: String, following: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(authorName) },
        text = { Text(stringResource(Res.string.follow_body)) },
        confirmButton = {
            TextButton(onClick = { onToggle(); onDismiss() }, modifier = Modifier.testTag("follow_toggle")) {
                Text(stringResource(if (following) Res.string.unfollow_action else Res.string.follow_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        modifier = Modifier.testTag("follow_dialog"),
    )
}
