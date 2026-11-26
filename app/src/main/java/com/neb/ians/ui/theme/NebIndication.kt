package com.neb.ians.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The press feedback of the whole app. A state layer in the content colour, drawn inside a rounded shape of its own
 * (a circle on small square controls, 16dp on everything else), never a square. A control that is already clipped to
 * a larger radius keeps its own shape because the clip wins. Replaces the platform ripple as `LocalIndication`, so a
 * control that forgot to clip can no longer flash a rectangle. No layout, no recomposition: one animated alpha read in
 * the draw phase.
 */
object NebIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = StateLayerNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === NebIndication
    override fun hashCode(): Int = 7919
}

private class StateLayerNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val alpha = Animatable(0f)
    private val presses = ArrayList<PressInteraction.Press>(2)
    private var hovered = 0
    private var focused = 0
    private var dragged = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i -> track(i); settle() }
        }
    }

    private fun track(i: Interaction) {
        when (i) {
            is PressInteraction.Press -> presses.add(i)
            is PressInteraction.Release -> presses.remove(i.press)
            is PressInteraction.Cancel -> presses.remove(i.press)
            is HoverInteraction.Enter -> hovered++
            is HoverInteraction.Exit -> hovered = (hovered - 1).coerceAtLeast(0)
            is FocusInteraction.Focus -> focused++
            is FocusInteraction.Unfocus -> focused = (focused - 1).coerceAtLeast(0)
            is DragInteraction.Start -> dragged++
            is DragInteraction.Stop, is DragInteraction.Cancel -> dragged = (dragged - 1).coerceAtLeast(0)
        }
    }

    private fun settle() {
        val target = when {
            presses.isNotEmpty() || dragged > 0 -> 0.12f
            focused > 0 -> 0.10f
            hovered > 0 -> 0.08f
            else -> 0f
        }
        coroutineScope.launch {
            // a tap shorter than a frame or two still shows the layer
            if (target == 0f && alpha.value > 0f && alpha.value < 0.05f) delay(50)
            alpha.animateTo(target, tween(if (target > alpha.value) 70 else 240))
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = alpha.value
        if (a <= 0.001f) return
        val minSide = minOf(size.width, size.height)
        val smallSquare = minSide <= 56.dp.toPx() && size.width <= size.height * 1.4f && size.height <= size.width * 1.4f
        val radius = if (smallSquare) minSide / 2f else minOf(16.dp.toPx(), minSide / 2f)
        drawRoundRect(
            color = currentValueOf(LocalContentColor).copy(alpha = a),
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}
