package com.example.poster.ui.liquid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.poster.ui.components.ReadableWidth
import com.example.poster.ui.platform.NavDestination

/** Bar height plus the gap under it; what screens add to their bottom padding. */
val LiquidNavBarInset = 64.dp + 10.dp + 12.dp

/**
 * The floating tab bar (feature.liquidNavBar): a capsule of glass over the
 * content, the chosen tab under a soft lens that slides to it. Same
 * destinations, same test tags as the docked bar, so nothing else knows which
 * one is on.
 */
@Composable
fun LiquidNavBar(
    destinations: List<NavDestination>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalGlassBackdrop.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(50)
    ReadableWidth(modifier = modifier.navigationBarsPadding().padding(bottom = 10.dp), maxWidth = 520.dp) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = scheme.onSurface.copy(alpha = 0.25f),
                    spotColor = scheme.onSurface.copy(alpha = 0.25f),
                )
                .liquidGlass(
                    backdrop = backdrop,
                    shape = shape,
                    tint = scheme.surface,
                    tintAlpha = if (dark) 0.55f else 0.62f,
                    highlight = if (dark) scheme.onSurface.copy(alpha = 0.16f) else scheme.surface.copy(alpha = 0.9f),
                )
                .testTag("liquid_nav_bar"),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val count = destinations.size.coerceAtLeast(1)
                val itemWidth = maxWidth / count
                val index = destinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                val lensX by animateDpAsState(
                    targetValue = itemWidth * index,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                    label = "liquid-lens",
                )
                Box(
                    modifier = Modifier
                        .offset(x = lensX)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .background(scheme.primary.copy(alpha = if (dark) 0.28f else 0.16f), shape),
                )
                Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    destinations.forEach { destination ->
                        val selected = destination.route == selectedRoute
                        val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(itemWidth)
                                .fillMaxHeight()
                                // One selectable node per tab, not a clickable
                                // column of two unrelated labels: a screen
                                // reader announces "Home, tab, selected" rather
                                // than reading the icon's description and then
                                // the same word again.
                                .selectable(
                                    selected = selected,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { onSelect(destination.route) },
                                )
                                .testTag(destination.testTag),
                        ) {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.icon,
                                // The label below says it; the selectable above merges both.
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
