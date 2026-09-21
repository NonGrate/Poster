package com.example.poster.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A thin left chevron, drawn rather than taken from Material.
 *
 * iOS back is a hairline `chevron.backward`, not the filled `KeyboardArrowLeft`
 * Material draws — that heavier arrow is one of the plainest "this is Android"
 * tells on every pushed screen. A chevron is three points and a stroke, so it
 * costs less to draw here than to reach for an icon pack, and [Icon] tints it
 * from the content colour like any other vector.
 */
val BackChevron: ImageVector by lazy {
    ImageVector.Builder(
        name = "BackChevron",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }.build()
}
