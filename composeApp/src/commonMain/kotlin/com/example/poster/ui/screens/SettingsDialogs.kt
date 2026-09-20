package com.example.poster.ui.screens

import com.example.poster.config.Features
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.theme.Spacing
import com.example.poster.ui.components.MergeAccountDialog
import poster.composeapp.generated.resources.merge_cancel
import poster.composeapp.generated.resources.merge_done
import com.example.poster.ui.platform.AdaptiveConfirmDialog
import com.example.poster.ui.platform.AdaptiveTimePicker
import com.example.poster.model.PostVisibility
import com.example.poster.notification.DailyReminders
import com.example.poster.util.AppPreferences
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.NotificationsViewModel
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.settings
import poster.composeapp.generated.resources.settings_reminder_time
import poster.composeapp.generated.resources.time_picker_confirm
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.settings_default_visibility
import poster.composeapp.generated.resources.visibility_group
import poster.composeapp.generated.resources.visibility_private
import poster.composeapp.generated.resources.visibility_public
import poster.composeapp.generated.resources.delete_account_title
import poster.composeapp.generated.resources.delete_account_body
import poster.composeapp.generated.resources.delete_account_confirm
import poster.composeapp.generated.resources.delete_account_failed
import poster.composeapp.generated.resources.settings_sign_out
import poster.composeapp.generated.resources.sign_out_body
import poster.composeapp.generated.resources.sign_out_title
import kotlinx.coroutines.launch

/** Deleting the account, and the one thing that is not the server's to undo. */
@Composable
internal fun DeleteAccountDialog(
    userViewModel: AccountViewModel,
    reminders: DailyReminders,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    // Read here, not at the press: stringResource is composable and the press
    // happens in a coroutine.
    val deleteFailed = stringResource(Res.string.delete_account_failed)
    AdaptiveConfirmDialog(
        title = stringResource(Res.string.delete_account_title),
        body = stringResource(Res.string.delete_account_body),
        confirmLabel = stringResource(Res.string.delete_account_confirm),
        cancelLabel = stringResource(Res.string.cancel),
        destructive = true,
        confirmTestTag = "confirm_delete_account_button",
        onConfirm = {
            onDismiss()
            coroutineScope.launch {
                // Says so when it fails. A person told their posts are
                // gone when they are not is the worst way to be wrong here,
                // and the session is deliberately left alone on failure.
                if (userViewModel.deleteAccount()) {
                    // Same reason as sign-out: the alarm is not part of the
                    // account and nothing on the server can cancel it.
                    reminders.disable()
                } else {
                    snackbarHostState.showSnackbar(deleteFailed)
                }
            }
        },
        onDismiss = { onDismiss() },
    )
}

/** Signing out, and everything on this device that outlives the session. */
@Composable
internal fun SignOutDialog(
    userViewModel: AccountViewModel,
    reminders: DailyReminders,
    notificationsViewModel: NotificationsViewModel?,
    onDismiss: () -> Unit,
) {
    AdaptiveConfirmDialog(
        title = stringResource(Res.string.sign_out_title),
        body = stringResource(Res.string.sign_out_body),
        confirmLabel = stringResource(Res.string.settings_sign_out),
        cancelLabel = stringResource(Res.string.cancel),
        destructive = true,
        confirmTestTag = "confirm_sign_out_button",
        onConfirm = {
            onDismiss()
            // The alarm outlives the session unless it is cancelled here.
            // AppPreferences.clear() forgets the setting, but forgetting a
            // scheduled alarm does not unschedule it — whoever signs in
            // next would keep being reminded at a time they never chose.
            reminders.disable()
            // Same reason: the list is this account's, and the next person
            // to sign in on this device must not find it waiting.
            notificationsViewModel?.clearForSignOut()
            // Signing out updates the session; the screen follows it.
            userViewModel.logOut()
        },
        onDismiss = { onDismiss() },
    )
}

/** Folding a hidden-Apple-email account into one they already had. */
@Composable
internal fun SettingsMergeDialog(
    userViewModel: AccountViewModel,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val mergedMessage = stringResource(Res.string.merge_done)
    MergeAccountDialog(
        accountViewModel = userViewModel,
        dismissLabel = stringResource(Res.string.merge_cancel),
        onDismiss = { onDismiss() },
        onMerged = {
            onDismiss()
            // The session now points at the surviving account; the screen
            // follows it, and the merge row disappears with the relay email.
            coroutineScope.launch { snackbarHostState.showSnackbar(mergedMessage) }
        },
    )
}

/** Which visibility a new post starts on. */
@Composable
internal fun DefaultVisibilityDialog(
    current: String,
    appPreferences: AppPreferences,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(stringResource(Res.string.settings_default_visibility)) },
        text = {
            Column {
                listOfNotNull(
                    PostVisibility.PUBLIC,
                    PostVisibility.GROUP.takeIf { Features.GROUPS },
                    PostVisibility.PRIVATE,
                ).forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appPreferences.setDefaultVisibility(option)
                                onDismiss()
                            }
                            .padding(vertical = Spacing.sm)
                            .testTag("default_visibility_option_$option"),
                    ) {
                        RadioButton(
                            selected = current == option,
                            onClick = {
                                appPreferences.setDefaultVisibility(option)
                                onDismiss()
                            },
                        )
                        Text(visibilityLabel(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}


@Composable
internal fun visibilityLabel(visibility: String): String = when (visibility) {
    PostVisibility.GROUP -> stringResource(Res.string.visibility_group)
    PostVisibility.PRIVATE -> stringResource(Res.string.visibility_private)
    else -> stringResource(Res.string.visibility_public)
}

/**
 * Minutes since midnight as a clock reading, in the device's 12- or 24-hour
 * convention so the settings row matches the picker.
 */
internal fun formatTimeOfDay(minutesSinceMidnight: Int, uses24Hour: Boolean): String {
    val total = minutesSinceMidnight.coerceIn(0, 1439)
    val hour = total / 60
    val minute = (total % 60).toString().padStart(2, '0')
    if (uses24Hour) return "${hour.toString().padStart(2, '0')}:$minute"
    val hour12 = ((hour + 11) % 12) + 1
    val period = if (hour < 12) "AM" else "PM"
    return "$hour12:$minute $period"
}

/**
 * The reminder time dialog. The picker inside it is native per platform (see
 * [AdaptiveTimePicker]); this keeps the surrounding dialog and its confirm/cancel
 * shared.
 */
@Composable
internal fun ReminderTimeDialog(
    minutesSinceMidnight: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by remember { mutableStateOf(minutesSinceMidnight) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_reminder_time)) },
        text = { AdaptiveTimePicker(minutesSinceMidnight) { minutes = it } },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(minutes) },
                modifier = Modifier.testTag("reminder_time_confirm"),
            ) {
                Text(stringResource(Res.string.time_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
