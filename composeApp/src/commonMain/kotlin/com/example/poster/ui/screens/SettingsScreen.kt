package com.example.poster.ui.screens

import poster.composeapp.generated.resources.settings_notifications_body
import poster.composeapp.generated.resources.settings_notifications_title
import poster.composeapp.generated.resources.settings_notifications
import androidx.compose.material.icons.outlined.Notifications
import com.example.poster.ui.liquid.LocalBottomBarInset
import com.example.poster.config.Features
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.poster.theme.Spacing
import com.example.poster.theme.isApplePlatform
import com.example.poster.ui.components.LargePageTitle
import com.example.poster.ui.components.SettingsDivider
import com.example.poster.ui.components.ScreenTopBar
import com.example.poster.ui.components.rememberCollapseFraction
import com.example.poster.ui.components.SettingsRow
import com.example.poster.ui.components.MergeAccountDialog
import androidx.compose.material.icons.filled.SwapHoriz
import poster.composeapp.generated.resources.merge_settings_button
import poster.composeapp.generated.resources.merge_settings_hint
import poster.composeapp.generated.resources.merge_cancel
import poster.composeapp.generated.resources.merge_done
import com.example.poster.ui.platform.AdaptiveSettingsSection
import com.example.poster.ui.platform.AdaptiveConfirmDialog
import com.example.poster.ui.platform.AdaptiveSwitch
import com.example.poster.ui.platform.AdaptiveTimePicker
import com.example.poster.ui.platform.rememberUses24HourClock
import com.example.poster.model.PostVisibility
import com.example.poster.notification.DailyReminders
import com.example.poster.notification.rememberNotificationPermissionRequest
import com.example.poster.util.AppPreferences
import com.example.poster.util.appVersionLabel
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import com.example.poster.viewmodel.AccountViewModel
import com.example.poster.viewmodel.GroupViewModel
import com.example.poster.viewmodel.NotificationsViewModel
import com.example.poster.viewmodel.PostsViewModel
import com.example.poster.viewmodel.SupportViewModel
import com.example.poster.viewmodel.ThemeViewModel
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.settings
import poster.composeapp.generated.resources.settings_account
import poster.composeapp.generated.resources.settings_appearance
import poster.composeapp.generated.resources.settings_daily_reminder
import poster.composeapp.generated.resources.settings_follow_system_theme
import poster.composeapp.generated.resources.theme_light
import poster.composeapp.generated.resources.theme_dark
import poster.composeapp.generated.resources.settings_edit_profile
import poster.composeapp.generated.resources.settings_no_groups
import poster.composeapp.generated.resources.reminder_permission_denied
import poster.composeapp.generated.resources.settings_reminder_summary
import poster.composeapp.generated.resources.settings_reminder_time
import poster.composeapp.generated.resources.time_picker_confirm
import poster.composeapp.generated.resources.cancel
import poster.composeapp.generated.resources.settings_manage_groups
import poster.composeapp.generated.resources.settings_default_visibility
import poster.composeapp.generated.resources.settings_posts
import poster.composeapp.generated.resources.settings_show_name
import poster.composeapp.generated.resources.settings_show_name_body
import poster.composeapp.generated.resources.visibility_group
import poster.composeapp.generated.resources.visibility_private
import poster.composeapp.generated.resources.visibility_public
import poster.composeapp.generated.resources.settings_support_action
import poster.composeapp.generated.resources.settings_support_body
import poster.composeapp.generated.resources.settings_support_manage
import poster.composeapp.generated.resources.settings_support_restore
import poster.composeapp.generated.resources.settings_support_thanks
import poster.composeapp.generated.resources.settings_support_title
import poster.composeapp.generated.resources.settings_delete_account
import poster.composeapp.generated.resources.delete_account_title
import poster.composeapp.generated.resources.delete_account_body
import poster.composeapp.generated.resources.delete_account_confirm
import poster.composeapp.generated.resources.delete_account_failed
import poster.composeapp.generated.resources.settings_sign_out
import poster.composeapp.generated.resources.settings_feedback_title
import poster.composeapp.generated.resources.settings_feedback
import poster.composeapp.generated.resources.settings_feedback_body
import poster.composeapp.generated.resources.sign_out_body
import poster.composeapp.generated.resources.sign_out_title
import poster.composeapp.generated.resources.settings_your_groups
import com.example.poster.preview.rememberPreviewGraph
import kotlinx.coroutines.launch
import com.example.poster.ui.components.SettingsSectionHeader
import com.example.poster.ui.components.SupportPaywall
import com.example.poster.ui.components.SupportCustomerCenter
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit = {},
    onManageGroups: () -> Unit = {},
    onFeedback: () -> Unit = {},
    onNotifications: () -> Unit = {},
    userViewModel: AccountViewModel = koinInject(),
    themeViewModel: ThemeViewModel = koinInject(),
    postsViewModel: PostsViewModel = koinInject(),
    appPreferences: AppPreferences = koinInject(),
    supportViewModel: SupportViewModel = koinInject(),
    groupViewModel: GroupViewModel = koinInject(),
) {
    // Only bound when the feature is on (see ViewModelModule), so only asked
    // for then — the sign-out path has to empty it.
    val notificationsViewModel = if (Features.PUSH_NOTIFICATIONS) koinInject<NotificationsViewModel>() else null
    val defaultVisibility by appPreferences.defaultVisibility.collectAsState()
    var showVisibilityPicker by remember { mutableStateOf(false) }
    val reminderEnabled by appPreferences.reminderEnabled.collectAsState()
    val reminderMinutes by appPreferences.reminderMinutes.collectAsState()
    val reminders = koinInject<DailyReminders>()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val uses24Hour = rememberUses24HourClock()
    var showTimePicker by remember { mutableStateOf(false) }
    val selectedUser by userViewModel.userState.collectAsState()
    // The app's one list (GroupViewModel follows the session), not a second
    // fetch of the same thing that could disagree with the cards and the filter.
    val userGroups by groupViewModel.groups.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Read here, not at the press: stringResource is composable and the press
    // happens in a coroutine.
    val deleteFailed = stringResource(Res.string.delete_account_failed)
    val reminderDenied = stringResource(Res.string.reminder_permission_denied)

    /**
     * The single place the reminder is turned on, off or moved.
     *
     * The stored setting follows what the platform actually agreed to, not what
     * was tapped: if permission is refused the switch goes back to off, because
     * a switch reading "on" with no notification behind it is worse than one
     * that plainly did not work.
     */
    fun applyReminder(enabled: Boolean, minutes: Int) {
        if (!enabled) {
            reminders.disable()
            appPreferences.setReminder(false, minutes)
            return
        }
        requestNotificationPermission { granted ->
            coroutineScope.launch {
                val scheduled = granted && reminders.enable(minutes / 60, minutes % 60)
                appPreferences.setReminder(scheduled, minutes)
                if (!scheduled) snackbarHostState.showSnackbar(reminderDenied)
            }
        }
    }
    var showPaywall by remember { mutableStateOf(false) }
    var showCustomerCenter by remember { mutableStateOf(false) }
    val isSupporter by supportViewModel.isSupporter.collectAsState()
    val hasSubscription by supportViewModel.hasSubscription.collectAsState()
    val activeSubscriptions by supportViewModel.activeSubscriptions.collectAsState()
    val renewalDates by supportViewModel.renewalDates.collectAsState()
    val subscriptionWillRenew by supportViewModel.subscriptionWillRenew.collectAsState()
    val offering by supportViewModel.offering.collectAsState()
    val purchasing by supportViewModel.purchasing.collectAsState()
    val supportError by supportViewModel.error.collectAsState()
    val justSupported by supportViewModel.justSupported.collectAsState()

    // Said once, and the sheet closes on it: a purchase that leaves the tiers
    // on screen looks like it did not happen.
    LaunchedEffect(justSupported) {
        if (justSupported) {
            showPaywall = false
            supportViewModel.acknowledgeThanks()
        }
    }

    // What is on offer can change without an app release, so it is asked for
    // when this screen opens rather than once at launch.
    LaunchedEffect(Unit) { if (Features.SUPPORT) supportViewModel.refresh() }

    if (showPaywall) {
        SupportPaywall(
            offering = offering,
            purchasing = purchasing,
            error = supportError?.message,
            onDismiss = { showPaywall = false },
            onBuy = { pkg -> supportViewModel.buy(pkg) },
            onRestore = { supportViewModel.restore() },
            demoTiers = supportViewModel.demoTiers,
            activeSubscriptionIds = activeSubscriptions,
            renewalDates = renewalDates,
            subscriptionWillRenew = subscriptionWillRenew,
        )
    }
    if (showCustomerCenter) {
        // Re-ask the store on the way out: a cancel done in there is exactly the
        // change the paywall and this screen need to stop showing "renews".
        SupportCustomerCenter(onDismiss = { showCustomerCenter = false; supportViewModel.refresh() })
    }

    val scrollState = rememberScrollState()
    val collapseFraction = rememberCollapseFraction(scrollState)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
    // The feed-screen pattern: a fixed bar above a scrolling inner column, no
    // Scaffold topBar (which broke the paywall sheet opened from this screen).
    Column(modifier = Modifier.fillMaxSize()) {
        // On iOS the compact title fades in as the large title below scrolls
        // away; Android keeps its single compact title.
        ScreenTopBar(title = stringResource(Res.string.settings), titleAlpha = collapseFraction)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = LocalBottomBarInset.current)
                .testTag("settings_screen")
        ) {
        if (isApplePlatform) {
            LargePageTitle(
                title = stringResource(Res.string.settings),
                modifier = Modifier.padding(horizontal = Spacing.md),
                collapseFraction = collapseFraction,
            )
        }

        AdaptiveSettingsSection(
            title = stringResource(Res.string.settings_appearance),
            modifier = Modifier.testTag("theme_section"),
            // The first section sits close under the top bar; the default gap is
            // for the space between sections and left a void at the top here.
            topPadding = Spacing.sm,
        ) {
        SettingsRow(
            label = stringResource(Res.string.settings_follow_system_theme),
            icon = Icons.Default.Info,
            trailing = {
                AdaptiveSwitch(
                    checked = themeViewModel.followSystemTheme,
                    onCheckedChange = { themeViewModel.setFollowSystem(it) },
                    modifier = Modifier.testTag("follow_system_theme_toggle")
                )
            }
        )

        // Only when not following the device: a light/dark selector in the same
        // segmented style as the post form's "Who can see this".
        if (!themeViewModel.followSystemTheme) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .testTag("theme_mode_selector"),
            ) {
                val options = listOf(
                    false to stringResource(Res.string.theme_light),
                    true to stringResource(Res.string.theme_dark),
                )
                options.forEachIndexed { index, (dark, label) ->
                    SegmentedButton(
                        selected = themeViewModel.darkThemeEnabled == dark,
                        onClick = { themeViewModel.setDarkTheme(dark) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        // Brown, not the default pink — pink is reserved for
                        // liking. Ordinary selection wears the primary family,
                        // like the visibility and language pickers.
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        icon = {},
                        modifier = Modifier.testTag(if (dark) "theme_dark_option" else "theme_light_option"),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        }

        AdaptiveSettingsSection(
            title = stringResource(Res.string.settings_account, selectedUser?.fullName() ?: "\u2014"),
        ) {
        SettingsRow(
            label = stringResource(Res.string.settings_edit_profile),
            icon = Icons.Default.AccountCircle,
            supporting = selectedUser?.email,
            onClick = onEditProfile,
            modifier = Modifier.testTag("edit_profile_row"),
            chevron = true,
        )

        // Only on an account signed in with a hidden Apple email: the way to
        // fold it into an account they already had. Gone once merged, because
        // the email is then the normal one.
        if (selectedUser?.email?.endsWith("@privaterelay.appleid.com", ignoreCase = true) == true) {
            SettingsRow(
                label = stringResource(Res.string.merge_settings_button),
                icon = Icons.Default.SwapHoriz,
                supporting = stringResource(Res.string.merge_settings_hint),
                onClick = { showMergeDialog = true },
                modifier = Modifier.testTag("merge_account_row"),
            )
        }

        }

        // Each row here belongs to a feature, so the rows are gathered first
        // and the section is drawn only when at least one survives.
        val postRows = buildList<@Composable () -> Unit> {
        if (Features.POST_VISIBILITY) add {
        SettingsRow(
            label = stringResource(Res.string.settings_default_visibility),
            icon = Icons.Default.Info,
            supporting = visibilityLabel(defaultVisibility),
            onClick = { showVisibilityPicker = true },
            modifier = Modifier.testTag("default_visibility_row"),
            chevron = true,
        )
        }
        // Opt-in: off unless they turn it on, so a name never appears on a
        // post's liked-by list by default. Saved on toggle via a one-field
        // profile update.
        if (Features.LIKES && !Features.AUTHORS) add {
        SettingsRow(
            label = stringResource(Res.string.settings_show_name),
            icon = Icons.Default.Person,
            supporting = stringResource(Res.string.settings_show_name_body),
            modifier = Modifier.testTag("show_name_row"),
            trailing = {
                AdaptiveSwitch(
                    checked = selectedUser?.showName == true,
                    onCheckedChange = { show -> coroutineScope.launch { userViewModel.setShowName(show) } },
                    modifier = Modifier.testTag("show_name_switch"),
                )
            }
        )
        }
        // Here rather than under a Notifications heading of its own: this is
        // the only notification the app sends. Being added to a group is
        // announced by email, because it happens whether or not the app is
        // installed.
        if (Features.DAILY_REMINDER) add {
        SettingsRow(
            label = stringResource(Res.string.settings_daily_reminder),
            icon = Icons.Default.Notifications,
            supporting = stringResource(Res.string.settings_reminder_summary),
            modifier = Modifier.testTag("daily_reminder_row"),
            trailing = {
                AdaptiveSwitch(
                    checked = reminderEnabled,
                    onCheckedChange = { applyReminder(it, reminderMinutes) },
                    modifier = Modifier.testTag("daily_reminder_switch"),
                )
            }
        )
        // Only when it is on: a time for a reminder nobody asked for is a
        // control with nothing behind it.
        if (reminderEnabled) {
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.settings_reminder_time),
                icon = Icons.Default.Notifications,
                supporting = formatTimeOfDay(reminderMinutes, uses24Hour),
                onClick = { showTimePicker = true },
                modifier = Modifier.testTag("reminder_time_row"),
                chevron = true,
            )
        }
        }
        }
        if (postRows.isNotEmpty()) {
            AdaptiveSettingsSection(
                title = stringResource(Res.string.settings_posts),
                modifier = Modifier.testTag("posts_section"),
            ) {
                postRows.forEachIndexed { index, row ->
                    if (index > 0) SettingsDivider()
                    row()
                }
            }
        }

        if (Features.GROUPS) AdaptiveSettingsSection(
            title = stringResource(Res.string.settings_your_groups),
            modifier = Modifier.testTag("group_management_section"),
        ) {
        SettingsRow(
            label = stringResource(Res.string.settings_manage_groups),
            icon = Icons.Default.Person,
            supporting = userGroups.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
                ?: stringResource(Res.string.settings_no_groups),
            onClick = onManageGroups,
            modifier = Modifier.testTag("manage_groups_button"),
            chevron = true,
        )

        }

        if (Features.PUSH_NOTIFICATIONS) AdaptiveSettingsSection(
            title = stringResource(Res.string.settings_notifications_title),
            modifier = Modifier.testTag("notifications_section"),
        ) {
            SettingsRow(
                label = stringResource(Res.string.settings_notifications),
                icon = Icons.Outlined.Notifications,
                supporting = stringResource(Res.string.settings_notifications_body),
                onClick = onNotifications,
                modifier = Modifier.testTag("notifications_button"),
                chevron = true,
            )
        }
        if (Features.FEEDBACK) AdaptiveSettingsSection(
            title = stringResource(Res.string.settings_feedback_title),
            modifier = Modifier.testTag("feedback_section"),
        ) {
            SettingsRow(
                label = stringResource(Res.string.settings_feedback),
                icon = Icons.AutoMirrored.Filled.Chat,
                supporting = stringResource(Res.string.settings_feedback_body),
                onClick = onFeedback,
                modifier = Modifier.testTag("feedback_button"),
                chevron = true,
            )
        }

        // Supporting the app, above signing out and below everything that is
        // actually about using it. Absent entirely in builds with no API key,
        // rather than present and dead.
        if (Features.SUPPORT && supportViewModel.available) {
            AdaptiveSettingsSection(
                title = stringResource(Res.string.settings_support_title),
                modifier = Modifier.testTag("support_section"),
            ) {
                SettingsRow(
                    label = if (isSupporter) {
                        stringResource(Res.string.settings_support_thanks)
                    } else {
                        stringResource(Res.string.settings_support_action)
                    },
                    icon = Icons.Default.Favorite,
                    supporting = stringResource(Res.string.settings_support_body),
                    onClick = { showPaywall = true },
                    modifier = Modifier.testTag("support_button"),
                    chevron = true,
                )
                // Only when there is a subscription to manage. A coffee has
                // nothing to cancel, and a row that opens a screen about
                // nothing is worse than no row.
                if (hasSubscription) {
                    SettingsRow(
                        label = stringResource(Res.string.settings_support_manage),
                        icon = Icons.Default.Person,
                        onClick = { showCustomerCenter = true },
                        modifier = Modifier.testTag("support_manage_button"),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))
        SettingsDivider()
        // Quiet destructive row, not a red brick: signing out is reversible.
        SettingsRow(
            label = stringResource(Res.string.settings_sign_out),
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            onClick = { showSignOutConfirmation = true },
            modifier = Modifier.testTag("logout_button")
        )
        // This one is not reversible, so it is coloured like it: below signing
        // out, because reaching for the wrong one here costs everything a
        // person has written.
        SettingsRow(
            label = stringResource(Res.string.settings_delete_account),
            icon = Icons.Filled.Delete,
            tint = MaterialTheme.colorScheme.error,
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier.testTag("delete_account_button"),
        )

        // The build, at the very bottom, so a tester can say which one they saw.
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = appVersionLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("app_version"),
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }

    }

    if (showDeleteConfirmation) {
        AdaptiveConfirmDialog(
            title = stringResource(Res.string.delete_account_title),
            body = stringResource(Res.string.delete_account_body),
            confirmLabel = stringResource(Res.string.delete_account_confirm),
            cancelLabel = stringResource(Res.string.cancel),
            destructive = true,
            confirmTestTag = "confirm_delete_account_button",
            onConfirm = {
                showDeleteConfirmation = false
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
            onDismiss = { showDeleteConfirmation = false },
        )
    }

    if (showSignOutConfirmation) {
        AdaptiveConfirmDialog(
            title = stringResource(Res.string.sign_out_title),
            body = stringResource(Res.string.sign_out_body),
            confirmLabel = stringResource(Res.string.settings_sign_out),
            cancelLabel = stringResource(Res.string.cancel),
            destructive = true,
            confirmTestTag = "confirm_sign_out_button",
            onConfirm = {
                showSignOutConfirmation = false
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
            onDismiss = { showSignOutConfirmation = false },
        )
    }

    if (showMergeDialog) {
        val mergedMessage = stringResource(Res.string.merge_done)
        MergeAccountDialog(
            accountViewModel = userViewModel,
            dismissLabel = stringResource(Res.string.merge_cancel),
            onDismiss = { showMergeDialog = false },
            onMerged = {
                showMergeDialog = false
                // The session now points at the surviving account; the screen
                // follows it, and the merge row disappears with the relay email.
                coroutineScope.launch { snackbarHostState.showSnackbar(mergedMessage) }
            },
        )
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            minutesSinceMidnight = reminderMinutes,
            onDismiss = { showTimePicker = false },
            onConfirm = { minutes ->
                showTimePicker = false
                // Re-schedules as well as stores: the alarm already out there
                // is for the old time, and leaving it would move the label
                // without moving the reminder.
                applyReminder(true, minutes)
            },
        )
    }

    if (showVisibilityPicker) {
        AlertDialog(
            onDismissRequest = { showVisibilityPicker = false },
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
                                    showVisibilityPicker = false
                                }
                                .padding(vertical = Spacing.sm)
                                .testTag("default_visibility_option_$option"),
                        ) {
                            RadioButton(
                                selected = defaultVisibility == option,
                                onClick = {
                                    appPreferences.setDefaultVisibility(option)
                                    showVisibilityPicker = false
                                },
                            )
                            Text(visibilityLabel(option))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVisibilityPicker = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun visibilityLabel(visibility: String): String = when (visibility) {
    PostVisibility.GROUP -> stringResource(Res.string.visibility_group)
    PostVisibility.PRIVATE -> stringResource(Res.string.visibility_private)
    else -> stringResource(Res.string.visibility_public)
}

@Preview
@Composable
fun SettingsScreenPreview() {
    val graph = rememberPreviewGraph()
    SettingsScreen(
        userViewModel = graph.accountViewModel,
        themeViewModel = graph.themeViewModel,
    )
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
private fun ReminderTimeDialog(
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
