package com.example.poster.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.poster.domain.validation.FeedbackRules
import com.example.poster.model.Feedback
import com.example.poster.model.FeedbackStatus
import com.example.poster.theme.Spacing
import com.example.poster.ui.components.PrimaryButton
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.platform.AdaptiveBackButton
import com.example.poster.ui.components.relativeTime
import com.example.poster.ui.platform.AdaptiveTextField
import com.example.poster.viewmodel.FeedbackViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import com.example.poster.preview.rememberPreviewGraph
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.feedback_title
import poster.composeapp.generated.resources.feedback_field_label
import poster.composeapp.generated.resources.feedback_send
import poster.composeapp.generated.resources.feedback_sent
import poster.composeapp.generated.resources.feedback_empty
import poster.composeapp.generated.resources.feedback_reply_label
import poster.composeapp.generated.resources.post_message_count
import poster.composeapp.generated.resources.settings

@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    feedbackViewModel: FeedbackViewModel = koinInject(),
) {
    val items by feedbackViewModel.items.collectAsState()
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sentMessage = stringResource(Res.string.feedback_sent)
    val scope = rememberCoroutineScope()

    // Read on open, which is also how a reply left since last time turns up.
    LaunchedEffect(Unit) { feedbackViewModel.load() }

    val overLimit = message.length > FeedbackRules.MESSAGE_LIMIT
    val canSend = message.isNotBlank() && !overLimit && !sending

    // No window insets of its own — pushed inside MainScreen's Scaffold. See
    // ProfileScreen for the full reasoning. The Scaffold stays for the snackbar.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.testTag("feedback_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    })
                },
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ScreenTopBar(
                title = stringResource(Res.string.feedback_title),
                navigation = {
                    AdaptiveBackButton(
                        onClick = onBack,
                        parentLabel = stringResource(Res.string.settings),
                        modifier = Modifier.testTag("feedback_back"),
                    )
                },
            )

            // Margins live on this wrapper, not the field's own modifier: that
            // modifier carries the testTag and must land on the editable field
            // (iOS wraps it in a Column, so padding there would not inset it).
            Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                AdaptiveTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = stringResource(Res.string.feedback_field_label),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 8,
                    isError = overLimit,
                    supportingText = if (message.isEmpty()) null
                        else stringResource(Res.string.post_message_count, message.length, FeedbackRules.MESSAGE_LIMIT),
                    supportingTextTag = "feedback_counter",
                    modifier = Modifier.fillMaxWidth().testTag("feedback_field"),
                )
            }

            PrimaryButton(
                onClick = {
                    sending = true
                    scope.launch {
                        val ok = feedbackViewModel.submit(message.trim())
                        sending = false
                        if (ok) {
                            message = ""
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            snackbarHostState.showSnackbar(sentMessage)
                        }
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = Spacing.md)
                    .testTag("feedback_send"),
            ) {
                Text(stringResource(Res.string.feedback_send))
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))

            if (items.isEmpty()) {
                Text(
                    text = stringResource(Res.string.feedback_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xl)
                        .testTag("feedback_empty"),
                )
            } else {
                items.forEach { item ->
                    FeedbackItemCard(item)
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun FeedbackItemCard(item: Feedback) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .testTag("feedback_item"),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(item.message, style = MaterialTheme.typography.bodyLarge)
            // "Today" / "3 days ago", same words the post cards use. The
            // server stores an ISO instant; if it ever fails to parse, fall
            // back to the bare date rather than crash the log.
            val sentAt = remember(item.createdAt) {
                runCatching {
                    Instant.parse(item.createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
                }.getOrNull()
            }
            Text(
                text = sentAt?.let { relativeTime(it) } ?: item.createdAt.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val reply = item.response
            if (item.status == FeedbackStatus.ANSWERED && reply != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = stringResource(Res.string.feedback_reply_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = reply,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun FeedbackScreenPreview() {
    val graph = rememberPreviewGraph()
    FeedbackScreen(onBack = {}, feedbackViewModel = graph.feedbackViewModel)
}
