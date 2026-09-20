package com.example.poster.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.example.poster.config.Features
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.domain.validation.CommentRules
import com.example.poster.model.Comment
import com.example.poster.theme.Spacing
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.CommentsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.comment_count
import poster.composeapp.generated.resources.comment_delete
import poster.composeapp.generated.resources.comment_failed
import poster.composeapp.generated.resources.comment_hint
import poster.composeapp.generated.resources.comment_send
import poster.composeapp.generated.resources.comment_too_long
import poster.composeapp.generated.resources.comment_you
import poster.composeapp.generated.resources.comments_empty
import poster.composeapp.generated.resources.comments_title

/**
 * The thread under a post on the details screen: the comments oldest first,
 * then a composer. Comments carry no name — posts are anonymous here by
 * default, so are these; the reader's own are marked "You". The author of a
 * comment and the author of the post can remove it.
 */
@Composable
fun CommentsSection(
    postGuid: String,
    currentUserId: String?,
    isPostAuthor: Boolean,
    modifier: Modifier = Modifier,
    viewModel: CommentsViewModel = koinInject(),
    accountViewModel: AccountViewModel = koinInject(),
) {
    val items by viewModel.items.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember(postGuid) { mutableStateOf("") }
    var failed by remember(postGuid) { mutableStateOf(false) }
    LaunchedEffect(postGuid) { viewModel.load(postGuid) }

    // The comment was declined for want of a confirmed address. The same dialog
    // the post form gets, at the same moment: the refusal is when the sentence
    // "confirm your email" answers a question somebody is actually asking.
    val needsVerifiedEmail by viewModel.needsVerifiedEmail.collectAsState()
    if (needsVerifiedEmail) {
        VerifyEmailDialog(
            onDismiss = { viewModel.acknowledgeVerification() },
            accountViewModel = accountViewModel,
        )
    }

    val tooLong = CommentRules.tooLong(draft)
    val canSend = CommentRules.textValid(draft) && !sending

    Column(modifier = modifier.testTag("comments_section")) {
        Text(
            text = if (items.isEmpty()) {
                stringResource(Res.string.comments_title)
            } else {
                stringResource(Res.string.comment_count, items.size)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm).testTag("comments_header"),
        )
        if (items.isEmpty()) {
            Text(
                text = stringResource(Res.string.comments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("comments_empty"),
            )
        }
        items.forEach { comment ->
            CommentRow(
                comment = comment,
                mine = comment.author == currentUserId,
                onDelete = if (comment.author == currentUserId || isPostAuthor) {
                    { scope.launch { viewModel.delete(postGuid, comment) } }
                } else {
                    null
                },
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            AdaptiveTextField(
                value = draft,
                onValueChange = { draft = it; failed = false },
                label = stringResource(Res.string.comment_hint),
                singleLine = false,
                minLines = 1,
                maxLines = 4,
                isError = tooLong || failed,
                supportingText = when {
                    tooLong -> stringResource(Res.string.comment_too_long, CommentRules.TEXT_LIMIT)
                    failed -> stringResource(Res.string.comment_failed)
                    else -> null
                },
                supportingTextTag = "comment_error",
                modifier = Modifier.weight(1f).testTag("comment_input"),
            )
            IconButton(
                onClick = {
                    val text = draft
                    scope.launch {
                        if (viewModel.add(postGuid, text)) draft = "" else failed = true
                    }
                },
                enabled = canSend,
                modifier = Modifier.testTag("comment_send"),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.comment_send))
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, mine: Boolean, onDelete: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("comment_row_${comment.guid}"),
    ) {
        if (Features.AUTHORS) {
            Avatar(photo = comment.authorPhoto, name = comment.authorName ?: "", size = 28.dp)
            Spacer(modifier = Modifier.width(Spacing.xs))
        }
        Column(modifier = Modifier.weight(1f)) {
            val writer = comment.authorName
            if (Features.AUTHORS && !writer.isNullOrBlank()) {
                Text(
                    text = writer,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("comment_author"),
                )
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("comment_text"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (mine) {
                    Text(
                        text = stringResource(Res.string.comment_you),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val at = runCatching { Instant.parse(comment.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
                if (at != null) {
                    Text(
                        text = relativeTime(at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.testTag("comment_delete_${comment.guid}")) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.comment_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
