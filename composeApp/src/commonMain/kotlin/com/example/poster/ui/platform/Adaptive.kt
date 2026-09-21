package com.example.poster.ui.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The platform-adaptive chrome, per DESIGN_BRIEF: layouts and hierarchy are
 * shared, and only the controls users would notice as foreign differ.
 *
 * The list is closed. A thirteenth divergence should be a decision, not a
 * convenience — each one is a fork maintained forever.
 *
 * Already handled by the theme rather than by a composable:
 *  #1  type scale  -> platformTypography()
 *  #12 corner radii -> platformShapes()
 */

/** #2 — Material switch vs the iOS pill toggle. */
@Composable
expect fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)

/**
 * #5 — Material's arrow, versus iOS's chevron followed by the name of the screen
 * you came from. [parentLabel] is ignored on Android.
 */
@Composable
expect fun AdaptiveBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    parentLabel: String? = null,
)

/** #11 — cards lift off the paper on Android; on iOS a hairline does the work. */
expect val adaptiveCardElevation: Dp

/**
 * #13 — the hardware/gesture Back. Android pops what is on screen before leaving
 * the app; iOS has no system Back button (its back is the nav bar's own control
 * and the edge swipe), so this is a no-op there.
 *
 * A divergence rather than the common `androidx.compose.ui.backhandler`: that API
 * is not on the common metadata compilation's classpath here, so using it in
 * commonMain breaks `compileCommonMainKotlinMetadata`. Back is genuinely a
 * platform concept, so it earns its place on the list.
 */
@Composable
expect fun AdaptiveBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * The iOS interactive-pop gesture: a swipe from the left edge to the right closes
 * what is on top. Android already has the system back gesture and button, so
 * there it is a no-op. v1 fires [onBack] once the swipe clears a threshold; it
 * does not yet follow the finger.
 */
expect fun Modifier.adaptiveEdgeSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier

/** One destination in the bottom navigation. */
data class NavDestination(
    val route: String,
    val label: String,
    /** The outline glyph, shown when the tab is not the current one. */
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    /** The filled glyph, shown when the tab is selected. */
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String,
)

/**
 * The tab bar's fill.
 *
 * Light keeps a clear step below the cream page. Dark drops to just above the
 * page rather than the near-card surface it used to wear — that made the bar the
 * brightest region of a dark screen and pulled the eye off the posts, the one
 * dark-mode elevation the brief singled out.
 */
@Composable
fun navBarContainerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) {
        scheme.surfaceContainerLow
    } else {
        scheme.surfaceContainerHighest
    }
}

/** #4 — Material's pill indicator vs a flat iOS tab bar that only swaps tint. */
@Composable
expect fun AdaptiveNavBar(
    destinations: List<NavDestination>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
)

/**
 * #8 — the primary create action. Android puts a FAB over the content; iOS puts a
 * button in the header. Each platform renders one and ignores the other, so the
 * screen offers both slots and does not care which is used.
 */
@Composable
expect fun AdaptiveCreateFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier)

@Composable
expect fun AdaptiveCreateHeaderAction(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
)

/**
 * #3 — a Material dialog on Android. On iOS a destructive confirmation becomes an
 * action sheet rising from the bottom, and everything else stays an alert.
 */
@Composable
expect fun AdaptiveConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmTestTag: String? = null,
)

/**
 * #7 — undo. Android has the snackbar idiom; iOS has none, so the same anatomy
 * (message plus a single action) becomes a capsule floating above the tab bar.
 */
@Composable
expect fun AdaptiveUndoBar(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * #9 — an outlined field with a floating label on Android; an inset grouped row
 * with the label sitting above it on iOS.
 */
@Composable
expect fun AdaptiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    /**
     * Caps the field's height: past this many lines it scrolls its own content
     * instead of growing. Keeps a long message from pushing the form's header
     * and Save off the top — worse on iOS, which pans the view for the keyboard.
     */
    maxLines: Int = Int.MAX_VALUE,
    isError: Boolean = false,
    /** False greys the field and blocks focus — for a value shown but not editable. */
    enabled: Boolean = true,
    supportingText: String? = null,
    supportingTextTag: String? = null,
    isPassword: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text,
    /**
     * Sentences by default: nearly every field here is something a person
     * writes, and starting a post with a lowercase letter reads like a
     * mistake they made rather than one the app made.
     *
     * An email or a password must never be capitalised, and a name wants each
     * word, so those say so where they are used.
     */
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization =
        androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
)

/**
 * #10 — settings sections: full-bleed rows under a clay header on Android,
 * inset rounded groups under an uppercase header on iOS.
 */
@Composable
expect fun AdaptiveSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    // Space above the section header. The default is the gap between sections;
    // the first section on a screen passes a smaller value so it sits close to
    // the top bar rather than opening with a void.
    topPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
)

/**
 * #14 — the time picker for the daily reminder.
 *
 * A native picker on each platform: Android keeps the Material clock, iOS gets
 * the wheel UIDatePicker people expect there. Both follow the system's 12- vs
 * 24-hour choice — iOS automatically, Android via DateFormat.is24HourFormat.
 *
 * Reports the chosen time as minutes since midnight through [onTimeChange] as it
 * changes; the caller holds it and commits on confirm.
 */
@Composable
expect fun AdaptiveTimePicker(minutesSinceMidnight: Int, onTimeChange: (Int) -> Unit)

/**
 * Whether the device is set to a 24-hour clock, so the reminder time reads the
 * same way in the settings row as in the picker. Android reads the system
 * setting; iOS reads it from the current locale's preferred hour format.
 */
@Composable
expect fun rememberUses24HourClock(): Boolean

/**
 * Match the system chrome (the iOS status bar, keyboards, sheets) to the app's
 * own theme rather than the device's.
 *
 * The app has its own light/dark switch, independent of the device. On iOS the
 * status bar content colour follows the *device* appearance, so in-app dark mode
 * over a light device left the clock and battery black on a dark screen. This
 * forces the window's interface style to the app's, so the status bar follows.
 * Android draws its bars from the Compose theme already, so there it is a no-op.
 */
@Composable
expect fun SyncSystemAppearance(darkTheme: Boolean)
