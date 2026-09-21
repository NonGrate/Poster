package com.example.poster.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The Android look: Material 3 as the design system draws it.
actual fun platformTypography() = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
)

actual fun platformShapes() = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) = Unit

actual val isApplePlatform: Boolean = false

@Composable
actual fun DialogWindowEdgeToEdge() = Unit
