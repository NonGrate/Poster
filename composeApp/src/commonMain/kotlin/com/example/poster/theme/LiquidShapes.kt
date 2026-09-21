package com.example.poster.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shapes for feature.liquidDesign, on both platforms: everything a step
 * rounder than Material or iOS defaults, controls as full capsules. The roles
 * are the same as [platformShapes]; only the radii differ.
 */
fun liquidShapes() = Shapes(
    extraSmall = RoundedCornerShape(14.dp), // fields
    small = RoundedCornerShape(50),         // chips, buttons: capsules
    medium = RoundedCornerShape(24.dp),     // cards, images
    large = RoundedCornerShape(28.dp),      // sheets
    extraLarge = RoundedCornerShape(32.dp), // dialogs
)
