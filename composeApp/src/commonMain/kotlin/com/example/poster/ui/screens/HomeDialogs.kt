package com.example.poster.ui.screens

import com.example.poster.ui.components.FollowDialog
import com.example.poster.viewmodel.FollowsViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.model.Post
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.viewmodel.PostsViewModel
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.report_title
import poster.composeapp.generated.resources.report_body
import poster.composeapp.generated.resources.report_confirm
import poster.composeapp.generated.resources.report_reason_hint
import poster.composeapp.generated.resources.report_sent
import poster.composeapp.generated.resources.post_completed_toast
import poster.composeapp.generated.resources.cancel
import kotlinx.coroutines.launch
import com.example.poster.viewmodel.TagViewModel

/**
 * Everything the feed opens over itself: reporting a post, marking one done,
 * deleting one, and following its author. One place, so the four cannot
 * disagree about which is up.
 */
@Composable
internal fun HomeDialogs(
    reporting: Post?,
    onReportingChange: (Post?) -> Unit,
    postToComplete: Post?,
    onPostToCompleteChange: (Post?) -> Unit,
    postToDelete: Post?,
    onPostToDeleteChange: (Post?) -> Unit,
    followAuthor: Post?,
    onFollowAuthorChange: (Post?) -> Unit,
    following: Set<String>,
    snackbarHostState: SnackbarHostState,
    postsViewModel: PostsViewModel,
    tagViewModel: TagViewModel,
    followsViewModel: FollowsViewModel,
) {
    val scope = rememberCoroutineScope()
    val reportSent = stringResource(Res.string.report_sent)
    val resolvedMessage = stringResource(Res.string.post_completed_toast)

reporting?.let { post ->
    ReportPostDialog(
        onConfirm = { reason ->
            onReportingChange(null)
            postsViewModel.reportPost(post.guid, reason)
            // Said regardless of what the network did. Somebody who has
            // just seen something upsetting should not also be handed a
            // failure to think about; the report is retried by them
            // pressing it again, and the server takes it either way.
            scope.launch { snackbarHostState.showSnackbar(reportSent) }
        },
        onDismiss = { onReportingChange(null) },
    )
}
postToComplete?.let { post ->
    CompletePostDialog(
        onDismiss = { onPostToCompleteChange(null) },
        onConfirm = { message ->
            postsViewModel.completePost(post, message)
            onPostToCompleteChange(null)
            scope.launch { snackbarHostState.showSnackbar(resolvedMessage) }
        },
    )
}
postToDelete?.let { post ->
    DeleteConfirmationDialog(
        post = post,
        onDismiss = { onPostToDeleteChange(null) },
        onConfirm = {
            scope.launch {
                postsViewModel.deletePost(post).join()
                tagViewModel.refreshTagSuggestions().join()
                onPostToDeleteChange(null)
            }
        },
    )
}
followAuthor?.let { post ->
    FollowDialog(
        authorName = post.authorName.orEmpty(),
        following = post.author in following,
        onToggle = { followsViewModel.toggle(post.author) },
        onDismiss = { onFollowAuthorChange(null) },
    )
}
}

/**
 * Report, with room to say what is wrong.
 *
 * The reason is optional — the report is counted either way — but when it is
 * given it is the difference between "somebody objected" and something a
 * moderator can act on, so there has to be somewhere to type it. The confirm is
 * tinted with the error colour to keep the weight the old confirm dialog had.
 */
@Composable
fun ReportPostDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.report_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.report_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                AdaptiveTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = stringResource(Res.string.report_reason_hint),
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("report_reason_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim().takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_report_button"),
            ) {
                Text(stringResource(Res.string.report_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
