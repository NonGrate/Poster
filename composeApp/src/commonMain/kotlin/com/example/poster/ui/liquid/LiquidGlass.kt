package com.example.poster.ui.liquid

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * The "liquid" look (feature.liquidDesign / feature.liquidNavBar): a surface
 * that shows what is behind it, blurred and tinted, with a hairline highlight.
 *
 * Compose has no backdrop filter, so the content is recorded into a
 * [GraphicsLayer] as it draws ([glassBackdropSource]) and every glass surface
 * re-draws that layer, shifted to its own position and blurred
 * ([liquidGlass]). Any platform that renders `RenderEffect` gets the blur — iOS,
 * Android 12+; older Android silently keeps the tint, which still reads as a
 * translucent bar. No library: this is a hundred lines, and the one it would
 * replace does the same recording underneath.
 *
 * Written as modifier nodes rather than `drawWithContent`/`drawBehind`
 * lambdas on purpose: the source tells its glass surfaces to redraw by calling
 * `invalidateDraw()` on them, which touches no snapshot state. A state write
 * per frame (the obvious alternative) kept Compose permanently "busy" — the
 * instrumented tests never saw an idle frame and timed out.
 */
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where the recorded content sits in the window, so a surface can line the layer up with itself. */
    internal var origin: Offset = Offset.Zero

    /** Glass surfaces currently on screen; redrawn whenever the content is. */
    internal val dependents = mutableSetOf<DrawModifierNode>()
}

/** The backdrop the screen content is recorded into, or null when nothing needs it. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * Extra room screens leave at the bottom of their scrolling content so the
 * last card can scroll out from under a floating bar. Zero with the docked bar.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Records this node's content into [backdrop] on every draw and draws it as usual. */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this.then(GlassSourceElement(backdrop))

/**
 * Glass over [backdrop], clipped to [shape]: the content behind, blurred by
 * [blur], under a [tint] at [tintAlpha], with a one-pixel [highlight] edge.
 * With a null backdrop it is a plain translucent surface.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop?,
    shape: Shape,
    tint: Color,
    tintAlpha: Float = 0.62f,
    highlight: Color = Color.White.copy(alpha = 0.35f),
    blur: Dp = 20.dp,
): Modifier {
    val blurLayer = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { blur.toPx() }
    // Set once per radius, not per draw: changing a layer's effect invalidates
    // its parent, and doing that from inside a draw is a redraw loop. An effect
    // rather than a remember, because writing to the layer is a side effect and
    // a remember may be thrown away before it is ever committed.
    DisposableEffect(blurLayer, radius) {
        blurLayer.renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Clamp) else null
        onDispose { }
    }
    return this
        .clip(shape)
        .then(GlassElement(backdrop, blurLayer, tint.copy(alpha = tintAlpha)))
        .border(1.dp, highlight, shape)
}

// --- nodes -----------------------------------------------------------------

private data class GlassSourceElement(val backdrop: GlassBackdrop) : ModifierNodeElement<GlassSourceNode>() {
    override fun create() = GlassSourceNode(backdrop)
    override fun update(node: GlassSourceNode) { node.backdrop = backdrop }
    override fun InspectorInfo.inspectableProperties() { name = "glassBackdropSource" }
}

private class GlassSourceNode(var backdrop: GlassBackdrop) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.origin = coordinates.positionInWindow()
    }

    override fun ContentDrawScope.draw() {
        backdrop.layer.record { this@draw.drawContent() }
        drawLayer(backdrop.layer)
        // What is behind the glass just changed; the glass has not, so it
        // would not redraw on its own.
        // Over a copy: invalidating can detach a surface, and a set mutated
        // while it is being walked throws.
        backdrop.dependents.toList().forEach { it.invalidateDraw() }
    }
}

private data class GlassElement(
    val backdrop: GlassBackdrop?,
    val blurLayer: GraphicsLayer,
    val tint: Color,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(backdrop, blurLayer, tint)
    override fun update(node: GlassNode) {
        node.rebind(backdrop)
        node.blurLayer = blurLayer
        node.tint = tint
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "liquidGlass" }
}

private class GlassNode(
    private var backdrop: GlassBackdrop?,
    var blurLayer: GraphicsLayer,
    var tint: Color,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var origin = Offset.Zero

    override fun onAttach() { backdrop?.dependents?.add(this) }
    override fun onDetach() { backdrop?.dependents?.remove(this) }

    fun rebind(next: GlassBackdrop?) {
        if (next === backdrop) return
        if (isAttached) backdrop?.dependents?.remove(this)
        backdrop = next
        if (isAttached) next?.dependents?.add(this)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val next = coordinates.positionInWindow()
        if (next != origin) {
            origin = next
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val source = backdrop
        if (source != null) {
            val shift = source.origin - origin
            blurLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                translate(shift.x, shift.y) { drawLayer(source.layer) }
            }
            drawLayer(blurLayer)
        }
        drawRect(tint)
        drawContent()
    }
}
