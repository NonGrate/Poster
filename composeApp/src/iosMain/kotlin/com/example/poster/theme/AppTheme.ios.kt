// iosMain · theme/AppTheme.ios.kt
package com.example.poster.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// FontFamily.Default resolves to San Francisco. SF metrics: smaller, tighter, heavier titles.
//
// A step below Apple's standard ramp — body is 16 where the system uses 17. The
// standard sizes read large in this app because almost every screen is a list of
// short rows, where 17pt body against 13pt secondary text leaves little air.
//
// Every role the app uses is set here. Three were left out and fell back to
// Material's defaults, which are drawn for Roboto: headlineLarge came out at
// 32sp, and the login title towered over everything near it.
actual fun platformTypography() = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp),
    titleLarge   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
    titleMedium  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.3).sp),
    // Grouped-list section headers: 13pt is what iOS Settings uses.
    titleSmall   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge    = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.4).sp),
    bodyMedium   = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.23).sp),
    bodySmall    = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.4).sp),
    labelMedium  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp),
    labelSmall   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp),
)

actual fun platformShapes() = Shapes(
    extraSmall = RoundedCornerShape(10.dp),  // inset field rows
    small = RoundedCornerShape(50),          // pill chips
    medium = RoundedCornerShape(16.dp),      // cards
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(14.dp),  // alerts
)

// iOS renders its status bar from the view controller; nothing to do here.
@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) = Unit

actual val isApplePlatform: Boolean = true

// iOS has no Dialog window to adjust — the form is a full-screen sheet already.
@Composable
actual fun DialogWindowEdgeToEdge() = Unit
