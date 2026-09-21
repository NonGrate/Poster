// commonMain · theme/AppTheme.kt
package com.example.poster.theme

import com.example.poster.config.Features
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.poster.config.BrandPalette

/**
 * The colour schemes, role by role, from poster.properties (via the generated
 * [BrandPalette]). Change a colour there, not here.
 *
 * How the roles are used, so a new palette can be chosen deliberately:
 *  - primary / onPrimary: filled buttons, the selected filter chip.
 *  - primaryContainer / onPrimaryContainer: the selected tab pill, segmented
 *    pickers (visibility, theme), the FAB.
 *  - secondary family: the Like control and the who-liked roster — the app's
 *    one accent for "somebody cared about this".
 *  - tertiary family: tag chips and the Resolved badge.
 *  - error: Delete, Close group, and anything destructive.
 *  - background / surface: the paper. surfaceContainerLowest is a post card,
 *    surfaceContainer the nav bar, surfaceContainerHigh dialogs and pills.
 *  - outlineVariant: hairlines and card borders.
 *
 * Keep text roles at 4.5:1 or better against what they sit on; the default
 * palette lands around 10:1 for body text on purpose.
 */
private val LightColors = lightColorScheme(
    primary = Color(BrandPalette.Light.primary),
    onPrimary = Color(BrandPalette.Light.onPrimary),
    primaryContainer = Color(BrandPalette.Light.primaryContainer),
    onPrimaryContainer = Color(BrandPalette.Light.onPrimaryContainer),
    inversePrimary = Color(BrandPalette.Light.inversePrimary),
    secondary = Color(BrandPalette.Light.secondary),
    onSecondary = Color(BrandPalette.Light.onSecondary),
    secondaryContainer = Color(BrandPalette.Light.secondaryContainer),
    onSecondaryContainer = Color(BrandPalette.Light.onSecondaryContainer),
    tertiary = Color(BrandPalette.Light.tertiary),
    onTertiary = Color(BrandPalette.Light.onTertiary),
    tertiaryContainer = Color(BrandPalette.Light.tertiaryContainer),
    onTertiaryContainer = Color(BrandPalette.Light.onTertiaryContainer),
    error = Color(BrandPalette.Light.error),
    onError = Color(BrandPalette.Light.onError),
    errorContainer = Color(BrandPalette.Light.errorContainer),
    onErrorContainer = Color(BrandPalette.Light.onErrorContainer),
    background = Color(BrandPalette.Light.background),
    onBackground = Color(BrandPalette.Light.onBackground),
    surface = Color(BrandPalette.Light.surface),
    onSurface = Color(BrandPalette.Light.onSurface),
    surfaceVariant = Color(BrandPalette.Light.surfaceVariant),
    onSurfaceVariant = Color(BrandPalette.Light.onSurfaceVariant),
    surfaceTint = Color(BrandPalette.Light.surfaceTint),
    outline = Color(BrandPalette.Light.outline),
    outlineVariant = Color(BrandPalette.Light.outlineVariant),
    scrim = Color(BrandPalette.Light.scrim),
    inverseSurface = Color(BrandPalette.Light.inverseSurface),
    inverseOnSurface = Color(BrandPalette.Light.inverseOnSurface),
    surfaceBright = Color(BrandPalette.Light.surfaceBright),
    surfaceDim = Color(BrandPalette.Light.surfaceDim),
    surfaceContainerLowest = Color(BrandPalette.Light.surfaceContainerLowest),
    surfaceContainerLow = Color(BrandPalette.Light.surfaceContainerLow),
    surfaceContainer = Color(BrandPalette.Light.surfaceContainer),
    surfaceContainerHigh = Color(BrandPalette.Light.surfaceContainerHigh),
    surfaceContainerHighest = Color(BrandPalette.Light.surfaceContainerHighest),
)

private val DarkColors = darkColorScheme(
    primary = Color(BrandPalette.Dark.primary),
    onPrimary = Color(BrandPalette.Dark.onPrimary),
    primaryContainer = Color(BrandPalette.Dark.primaryContainer),
    onPrimaryContainer = Color(BrandPalette.Dark.onPrimaryContainer),
    inversePrimary = Color(BrandPalette.Dark.inversePrimary),
    secondary = Color(BrandPalette.Dark.secondary),
    onSecondary = Color(BrandPalette.Dark.onSecondary),
    secondaryContainer = Color(BrandPalette.Dark.secondaryContainer),
    onSecondaryContainer = Color(BrandPalette.Dark.onSecondaryContainer),
    tertiary = Color(BrandPalette.Dark.tertiary),
    onTertiary = Color(BrandPalette.Dark.onTertiary),
    tertiaryContainer = Color(BrandPalette.Dark.tertiaryContainer),
    onTertiaryContainer = Color(BrandPalette.Dark.onTertiaryContainer),
    error = Color(BrandPalette.Dark.error),
    onError = Color(BrandPalette.Dark.onError),
    errorContainer = Color(BrandPalette.Dark.errorContainer),
    onErrorContainer = Color(BrandPalette.Dark.onErrorContainer),
    background = Color(BrandPalette.Dark.background),
    onBackground = Color(BrandPalette.Dark.onBackground),
    surface = Color(BrandPalette.Dark.surface),
    onSurface = Color(BrandPalette.Dark.onSurface),
    surfaceVariant = Color(BrandPalette.Dark.surfaceVariant),
    onSurfaceVariant = Color(BrandPalette.Dark.onSurfaceVariant),
    surfaceTint = Color(BrandPalette.Dark.surfaceTint),
    outline = Color(BrandPalette.Dark.outline),
    outlineVariant = Color(BrandPalette.Dark.outlineVariant),
    scrim = Color(BrandPalette.Dark.scrim),
    inverseSurface = Color(BrandPalette.Dark.inverseSurface),
    inverseOnSurface = Color(BrandPalette.Dark.inverseOnSurface),
    surfaceBright = Color(BrandPalette.Dark.surfaceBright),
    surfaceDim = Color(BrandPalette.Dark.surfaceDim),
    surfaceContainerLowest = Color(BrandPalette.Dark.surfaceContainerLowest),
    surfaceContainerLow = Color(BrandPalette.Dark.surfaceContainerLow),
    surfaceContainer = Color(BrandPalette.Dark.surfaceContainer),
    surfaceContainerHigh = Color(BrandPalette.Dark.surfaceContainerHigh),
    surfaceContainerHighest = Color(BrandPalette.Dark.surfaceContainerHighest),
)

// Shared 4dp spacing scale - replaces hardcoded 16.dp at call sites.
object Spacing {
    val xxs = 4.dp;  val xs = 8.dp;  val sm = 12.dp; val md = 16.dp
    val lg = 24.dp;  val xl = 32.dp; val xxl = 48.dp
}

// Divergence #1 (type) and #12 (radii): metrics differ, roles do not.
expect fun platformTypography(): Typography
expect fun platformShapes(): Shapes

// Part of divergence #3 (nav): iOS centres a pushed screen's title where Android
// leads with it. A plain flag rather than another Adaptive composable, since only
// one shared bar reads it.
expect val isApplePlatform: Boolean

// Not a design divergence — plumbing. The app draws behind the system bars, so the
// bar icons have to be told which way to contrast against the app background.
@Composable
expect fun SystemBarsAppearance(darkTheme: Boolean)

// Plumbing: an Android Dialog window does not draw behind the system bars by
// default, so a full-screen form shown in one (registration) had opaque bars top
// and bottom while every other screen ran edge to edge. Makes the current Dialog
// window edge-to-edge; a no-op on iOS and when the caller is not inside a Dialog.
@Composable
expect fun DialogWindowEdgeToEdge()

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    SystemBarsAppearance(darkTheme)
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = platformTypography(),
        shapes = if (Features.LIQUID_DESIGN) liquidShapes() else platformShapes(),
        content = content
    )
}
