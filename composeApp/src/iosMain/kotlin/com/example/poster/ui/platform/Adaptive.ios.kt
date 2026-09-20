package com.example.poster.ui.platform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import com.example.poster.ui.components.BackChevron
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animate
import kotlinx.coroutines.launch
import kotlin.math.abs
import poster.composeapp.generated.resources.password_show
import poster.composeapp.generated.resources.password_hide
import com.example.poster.ui.components.EyeVisible
import com.example.poster.ui.components.EyeHidden
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDateComponents
import platform.UIKit.UIAction
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIColor
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import org.jetbrains.compose.resources.stringResource
import poster.composeapp.generated.resources.Res
import poster.composeapp.generated.resources.action_back
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import com.example.poster.theme.Spacing

/**
 * The iOS toggle is a plain pill: no track outline in the off state, white thumb
 * throughout. Compose draws it — the real UIKit control is not reachable from here.
 */
@Composable
actual fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Chevron plus the name of the screen you came from. */
@Composable
actual fun AdaptiveBackButton(onClick: () -> Unit, modifier: Modifier, parentLabel: String?) {
    TextButton(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = BackChevron,
                contentDescription = stringResource(Res.string.action_back),
            )
            if (parentLabel != null) {
                Text(
                    text = parentLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

/** Cards never carry a shadow on iOS; the hairline separates them. */
actual val adaptiveCardElevation: Dp = 0.dp

/** Flat tab bar: the tint swaps, with no pill behind the icon. */
@Composable
actual fun AdaptiveNavBar(
    destinations: List<NavDestination>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
) {
    Surface(color = navBarContainerColor(), modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                destinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    val tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            // No ripple: a square ripple across the icon-and-label
                            // column looked wrong, and iOS tabs give no ripple
                            // anyway — the tint change is the feedback.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(destination.route) }
                            .padding(horizontal = 8.dp)
                            .testTag(destination.testTag),
                    ) {
                        Icon(
                            if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = destination.label,
                            tint = tint,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}

/** iOS puts create in the header, so there is no floating button. */
@Composable
actual fun AdaptiveCreateFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier) = Unit

@Composable
actual fun AdaptiveCreateHeaderAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.Add, contentDescription)
    }
}

/** Destructive confirmations rise from the bottom as an action sheet. */
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
    if (!destructive) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    modifier = if (confirmTestTag != null) Modifier.testTag(confirmTestTag) else Modifier,
                ) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("confirm_dialog_cancel")) {
                    Text(cancelLabel)
                }
            },
        )
        return
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp),
                    )
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (confirmTestTag != null) Modifier.testTag(confirmTestTag) else Modifier),
                    ) {
                        Text(confirmLabel, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_dialog_cancel"),
                ) {
                    Text(cancelLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** No snackbar idiom on iOS: a capsule floats above the tab bar instead. */
@Composable
actual fun AdaptiveUndoBar(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier.padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            TextButton(onClick = onAction, modifier = Modifier.testTag("undo_button")) {
                Text(actionLabel, color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

/** Inset grouped row: the label sits above the field, and the field has no outline. */
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
    // The caller's modifier goes on the inner BasicTextField (below), not here:
    // it carries the testTag, and the automation types into the node that holds
    // it — which must be the editable field, not this wrapper. Callers that want
    // horizontal margins wrap the whole field in padding instead, which insets
    // it on both platforms. This Column just fills width.
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        var revealed by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            // surfaceVariant, not surfaceContainerLowest: the latter is not set
            // in this theme and fell back to a Material default that read too
            // dark against the page. surfaceVariant is explicitly themed and
            // gives the field clear definition in both light and dark.
            color = MaterialTheme.colorScheme.surfaceVariant,
            // A 1dp outline, like the Android field: the fill alone gives a clear
            // edge in light, but in dark it sits close to the page, so a border
            // is what makes the field read as defined there too.
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        // Dimmed when disabled, so a value shown but not editable
                        // reads as off rather than as ordinary text.
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    // A password somebody cannot read is one they mistype and
                    // only find out about at a failed sign-in — or later, on an
                    // account whose password is not what they think it is.
                    visualTransformation = if (isPassword && !revealed) {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    } else {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
            ),
                    modifier = modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                )
                }
                if (isPassword) {
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
            }
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = (if (supportingTextTag != null) Modifier.testTag(supportingTextTag) else Modifier)
                    .padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

/** Inset grouped section: uppercase header, rows inside one rounded container. */
@Composable
actual fun AdaptiveSettingsSection(
    title: String,
    modifier: Modifier,
    topPadding: androidx.compose.ui.unit.Dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = topPadding, bottom = 6.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(content = content)
        }
    }
}

/** #13 — iOS has no system Back button; navigation carries its back affordances. */
@Composable
actual fun AdaptiveBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun Modifier.adaptiveEdgeSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            // The page follows the finger: a drag that begins in the left edge and
            // goes clearly rightward slides the whole element across. Let go past a
            // third of the width and it finishes and closes; short of that it snaps
            // back. Only claimed once it is plainly a horizontal edge drag, so a
            // vertical scroll starting near the edge is left alone.
            var dragX by remember { mutableStateOf(0f) }
            val scope = rememberCoroutineScope()
            // If it closes some other way while a drag is mid-flight, do not keep
            // the element shoved to one side.
            LaunchedEffect(enabled) { if (!enabled) dragX = 0f }
            this
                .graphicsLayer { translationX = dragX }
                .pointerInput(Unit) {
                    val edge = 24.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > edge) return@awaitEachGesture
                        val threshold = size.width / 3f
                        var totalX = 0f
                        var totalY = 0f
                        var claimed = false
                        var abandoned = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val moved = change.positionChange()
                            totalX += moved.x
                            totalY += moved.y
                            if (!claimed && !abandoned) {
                                if (totalX > 12f && totalX > abs(totalY)) claimed = true
                                else if (abs(totalY) > 12f) abandoned = true
                            }
                            if (claimed) {
                                change.consume()
                                dragX = totalX.coerceAtLeast(0f)
                            }
                            if (!change.pressed) break
                        }
                        if (claimed) {
                            val settled = dragX
                            val target = if (settled > threshold) size.width.toFloat() else 0f
                            scope.launch {
                                animate(settled, target) { value, _ -> dragX = value }
                                if (target != 0f) {
                                    onBack()
                                    dragX = 0f
                                }
                            }
                        }
                    }
                }
        }
    }

@Composable
actual fun SyncSystemAppearance(darkTheme: Boolean) {
    LaunchedEffect(darkTheme) {
        // The window's own style, not the device's, decides the status bar
        // content colour. Force it to the app's theme so the clock and battery
        // stay legible when in-app dark mode runs over a light device.
        val style = if (darkTheme) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        UIApplication.sharedApplication.windows.forEach { window ->
            (window as? UIWindow)?.overrideUserInterfaceStyle = style
        }
    }
}

/**
 * The native wheel picker. UIDatePicker in time mode follows the device's 12- vs
 * 24-hour setting on its own, so nothing here has to ask which it is.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AdaptiveTimePicker(minutesSinceMidnight: Int, onTimeChange: (Int) -> Unit) {
    val latest by rememberUpdatedState(onTimeChange)
    // The interop view is transparent by default, so the settings behind the
    // dialog showed through the wheels. Paint it the dialog's own colour.
    val background = MaterialTheme.colorScheme.background
    UIKitView(
        factory = {
            val picker = UIDatePicker()
            picker.datePickerMode = UIDatePickerMode.UIDatePickerModeTime
            picker.preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
            picker.backgroundColor = background.toUIColor()
            picker.minuteInterval = 1L
            val components = NSDateComponents()
            components.hour = (minutesSinceMidnight / 60).toLong()
            components.minute = (minutesSinceMidnight % 60).toLong()
            NSCalendar.currentCalendar.dateFromComponents(components)?.let {
                picker.setDate(it, animated = false)
            }
            picker.addAction(
                UIAction.actionWithHandler {
                    val comps = NSCalendar.currentCalendar.components(
                        NSCalendarUnitHour or NSCalendarUnitMinute,
                        fromDate = picker.date,
                    )
                    latest(comps.hour.toInt() * 60 + comps.minute.toInt())
                },
                forControlEvents = UIControlEventValueChanged,
            )
            picker
        },
        modifier = Modifier.fillMaxWidth().height(216.dp),
        update = { it.backgroundColor = background.toUIColor() },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

/**
 * The device's clock preference, read from the locale. The "j" template resolves
 * to the locale's preferred hour form; a 12-hour locale carries the am/pm symbol
 * ("a"), a 24-hour one does not.
 */
@Composable
actual fun rememberUses24HourClock(): Boolean {
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0uL, NSLocale.currentLocale)
    return pattern?.contains("a") != true
}
