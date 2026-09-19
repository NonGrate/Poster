// androidMain · theme/AppTheme.android.kt
package com.example.poster.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Stock M3/Roboto metrics, with one correction: screen titles get Medium weight.
actual fun platformTypography() = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
)

actual fun platformShapes() = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // fields
    small = RoundedCornerShape(8.dp),        // chips
    medium = RoundedCornerShape(12.dp),      // cards
    large = RoundedCornerShape(16.dp),       // FAB
    extraLarge = RoundedCornerShape(28.dp),  // dialogs
)

// Status/navigation bar icons must contrast with the app background, which the
// theme now owns. Light theme -> dark icons, dark theme -> light icons.
@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

actual val isApplePlatform: Boolean = false

@Composable
actual fun DialogWindowEdgeToEdge() {
    val view = LocalView.current
    if (view.isInEditMode) return
    // Null unless the caller is inside a Dialog — the post form is shown as a
    // bare sheet and needs nothing here.
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Drop the window's opaque default background so the form's own surface
        // fills to the edges behind the (now transparent) bars, instead of a
        // grey slab top and bottom. The form already pads its content clear of
        // the bars with statusBarsPadding()/navigationBarsPadding().
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
    }
}
