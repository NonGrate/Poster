package com.example.poster.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.poster.theme.DialogWindowEdgeToEdge
import com.example.poster.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.action_close

/**
 * The form pattern: a full-screen sheet with the commit action in the header
 * rather than at the bottom of a card, where a small screen can push it out of
 * reach. Shared so the post form and registration cannot drift apart.
 *
 * A [Dialog] around [FormSheet]. Used where a dialog is the right thing — over the
 * login screen, say. The post form is shown a step up in [MainScreen] with
 * [FormSheet] directly, because on iOS a Dialog stops short of the bottom safe
 * area and left the app's nav bar showing under the form; a plain sheet rendered
 * above the Scaffold covers the whole screen on both platforms.
 */
@Composable
fun FormDialog(
    title: String,
    saveLabel: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
    saveTestTag: String = "form_save_button",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FormSheet(
            title = title,
            saveLabel = saveLabel,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = modifier,
            saveEnabled = saveEnabled,
            saveTestTag = saveTestTag,
            content = content,
        )
    }
}

/**
 * Picks the presentation: a [Dialog] (the default) or a bare [FormSheet]. The
 * sheet is for when the form is already rendered above everything — see the
 * post form in [MainScreen], hoisted there so it covers the iOS nav bar.
 */
@Composable
fun FormContainer(
    asSheet: Boolean,
    title: String,
    saveLabel: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
    saveTestTag: String = "form_save_button",
    content: @Composable ColumnScope.() -> Unit,
) {
    if (asSheet) {
        FormSheet(title, saveLabel, onDismiss, onSave, modifier, saveEnabled, saveTestTag, content)
    } else {
        FormDialog(title, saveLabel, onDismiss, onSave, modifier, saveEnabled, saveTestTag, content)
    }
}

/** The form's full-screen surface, without a [Dialog] around it. */
@Composable
fun FormSheet(
    title: String,
    saveLabel: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
    saveTestTag: String = "form_save_button",
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // When this sheet is shown inside a Dialog (registration), draw the Dialog
    // window edge-to-edge so it matches every other screen. No-op otherwise.
    DialogWindowEdgeToEdge()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
            // A full-screen Dialog draws over the Scaffold, so it does not inherit
            // the insets the tabs get. Padded here so the title and Save clear the
            // status bar at the top, and the form's last field clears the bottom
            // bar / home indicator (the Surface background still fills to the edge
            // behind these, so nothing shows through). No-ops on Android where the
            // Dialog already covers the whole screen.
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                // Centre the form on a wide screen; the Surface background still
                // fills the gutters. A no-op on a phone.
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().height(64.dp).padding(end = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_form_button"),
                    ) {
                        Icon(Icons.Default.Close, stringResource(Res.string.action_close))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = Spacing.xs),
                    )
                    PrimaryButton(
                        onClick = onSave,
                        enabled = saveEnabled,
                        modifier = Modifier.height(40.dp).testTag(saveTestTag),
                    ) {
                        Text(saveLabel)
                    }
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth()
                        // Tap off a field to dismiss the keyboard. detectTapGestures
                        // only claims taps, so scrolling and tapping fields still
                        // work; a tap on empty form space clears focus.
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            })
                        }
                        .verticalScroll(rememberScrollState())
                        // So the focused field can scroll above the keyboard
                        // instead of sitting under it — the reason people had to
                        // dismiss the keyboard to reach the rest of the form. A
                        // no-op on iOS, which pans the view instead of reporting
                        // an inset.
                        .imePadding()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = content,
                )

            }
        }
}
