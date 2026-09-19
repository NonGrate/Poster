// Desktop: the Android (Material 3) look, minus what only Android has. Kept in
// step with Adaptive.android.kt by hand; the two differ only where marked.
package com.example.poster.ui.platform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import poster.composeapp.generated.resources.password_show
import poster.composeapp.generated.resources.password_hide
import com.example.poster.ui.components.EyeVisible
import com.example.poster.ui.components.EyeHidden
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.action_back

@Composable
actual fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = modifier)
}

/** The originating screen's name is an iOS convention; Android just points back. */
@Composable
actual fun AdaptiveBackButton(onClick: () -> Unit, modifier: Modifier, parentLabel: String?) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
    }
}

actual val adaptiveCardElevation: Dp = 1.dp

@Composable
actual fun AdaptiveNavBar(
    destinations: List<NavDestination>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
) {
    // navBarContainerColor keeps the light bar a clear step off the cream page
    // and drops the dark bar to just above the page, so it stops being the
    // brightest region of a dark screen.
    NavigationBar(containerColor = navBarContainerColor()) {
        destinations.forEach { destination ->
            val selected = selectedRoute == destination.route
            NavigationBarItem(
                // Filled glyph for the current tab, outline for the rest, so the
                // selected state survives for anyone who can't tell the pill from
                // the bar.
                icon = {
                    Icon(
                        if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                selected = selected,
                onClick = { onSelect(destination.route) },
                // The selected tab glows in the primary (peach/clay) family, not
                // rose — rose now means "liking", and the active tab belongs with
                // the app's primary accent, matching the FAB and buttons.
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}

@Composable
actual fun AdaptiveCreateFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier) {
    // The primary action wears the primary colour — the same clay as Save, Add
    // Post and the invite buttons, so "create a post" is one colour wherever
    // it is offered. (It was rose before, which put it in the "liking" family
    // and made the FAB the odd one out among the action buttons.)
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Icon(Icons.Default.Add, contentDescription)
    }
}

/** Android has the FAB; nothing goes in the header. */
@Composable
actual fun AdaptiveCreateHeaderAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier,
) = Unit

@Composable
actual fun AdaptiveDestructiveTextButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text = text, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
actual fun AdaptiveConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean,
    confirmTestTag: String?,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = if (confirmTestTag != null) Modifier.testTag(confirmTestTag) else Modifier,
            ) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("confirm_dialog_cancel")) {
                Text(cancelLabel)
            }
        },
    )
}

@Composable
actual fun AdaptiveUndoBar(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    androidx.compose.material3.Snackbar(
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.extraSmall,
        action = {
            TextButton(onClick = onAction, modifier = Modifier.testTag("undo_button")) {
                Text(actionLabel, color = MaterialTheme.colorScheme.inversePrimary)
            }
        },
        modifier = modifier,
    ) {
        Text(message)
    }
}

@Composable
actual fun AdaptiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    isError: Boolean,
    enabled: Boolean,
    supportingText: String?,
    supportingTextTag: String?,
    isPassword: Boolean,
    keyboardType: androidx.compose.ui.text.input.KeyboardType,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization,
) {
    var revealed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText?.let {
            {
                Text(
                    text = it,
                    modifier = if (supportingTextTag != null) Modifier.testTag(supportingTextTag) else Modifier,
                )
            }
        },
        // A password somebody cannot read is a password they mistype and only
        // find out about later — at the point of a failed sign-in, or worse, an
        // account whose password is not what they think it is.
        visualTransformation = if (isPassword && !revealed) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                androidx.compose.material3.IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.testTag("password_reveal"),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (revealed) EyeHidden else EyeVisible,
                        contentDescription = stringResource(
                            if (revealed) Res.string.password_hide else Res.string.password_show
                        ),
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
        ),
        // A filled container so the field reads as a defined input rather than a
        // faint outline on the page — the transparent default blended into the
        // background (worst in dark mode) and read as "too dark, no contrast".
        // surfaceVariant is lighter than the page in dark and a warm tint in
        // light, so the field stands out either way.
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        modifier = modifier,
    )
}

@Composable
actual fun AdaptiveSettingsSection(
    title: String,
    modifier: Modifier,
    topPadding: androidx.compose.ui.unit.Dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            // Secondary label colour, not the primary accent: a heading is
            // structure, not an action. In dark the peach primary made the
            // section title the brightest thing on the screen; the accent
            // belongs to buttons and switches, not to the words above them.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 8.dp),
        )
        content()
    }
}

/** #13 — the Android system Back button/gesture. */
/** No system back gesture on the desktop; screens offer their own back button. */
@Composable
actual fun AdaptiveBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** Android has the system back gesture and button already; nothing to add. */
actual fun Modifier.adaptiveEdgeSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier = this

/** Android colours its bars from the Compose theme already. */
@Composable
actual fun SyncSystemAppearance(darkTheme: Boolean) {}

/** The Material clock, in the system's 12- or 24-hour convention. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AdaptiveTimePicker(minutesSinceMidnight: Int, onTimeChange: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = minutesSinceMidnight / 60,
        initialMinute = minutesSinceMidnight % 60,
        is24Hour = rememberUses24HourClock(),
    )
    LaunchedEffect(state.hour, state.minute) {
        onTimeChange(state.hour * 60 + state.minute)
    }
    TimePicker(state = state, modifier = Modifier.testTag("reminder_time_picker"))
}

/** The device's clock preference, from the system setting. */
@Composable
actual fun rememberUses24HourClock(): Boolean =
    (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) as? java.text.SimpleDateFormat)?.toPattern()?.contains('a') == false
