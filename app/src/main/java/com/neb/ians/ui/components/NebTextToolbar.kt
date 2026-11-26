package com.neb.ians.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.neb.ians.ui.theme.nebFloatColor
import com.neb.ians.ui.theme.nebFloatLine
import kotlin.math.roundToInt

/** An extra button in the selection menu (for example Bold in a writing field). */
class NebToolAction(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val keepMenuOpen: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The app's own menu for selected text: a floating pill above the selection (below it when the top is too close to the
 * screen edge) in the floating surface colour with a hairline, no shadow. It never takes focus, so the keyboard stays.
 */
@Stable
class NebTextToolbarState : TextToolbar {
    class Menu(
        val rect: Rect,
        val copy: (() -> Unit)?,
        val paste: (() -> Unit)?,
        val cut: (() -> Unit)?,
        val selectAll: (() -> Unit)?,
    )

    var menu by mutableStateOf<Menu?>(null)
    var extras: List<NebToolAction> = emptyList()

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        menu = Menu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() {
        if (menu != null) menu = null
    }

    override val status: TextToolbarStatus
        get() = if (menu != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
}

/** Gives every text field under [content] the app's selection menu, with [extras] added after the standard actions. */
@Composable
fun NebTextToolbarProvider(extras: List<NebToolAction> = emptyList(), content: @Composable () -> Unit) {
    val state = remember { NebTextToolbarState() }
    SideEffect { state.extras = extras }
    CompositionLocalProvider(LocalTextToolbar provides state) { content() }
    state.menu?.let { NebToolbarPopup(state, it) }
}

private class ToolbarPosition(
    private val rect: Rect,
    private val viewLeft: Int,
    private val viewTop: Int,
    private val gap: Int,
    private val below: Int,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val left = rect.left + viewLeft
        val right = rect.right + viewLeft
        val top = rect.top + viewTop
        val bottom = rect.bottom + viewTop
        val x = (((left + right) / 2f) - popupContentSize.width / 2f).roundToInt()
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val above = (top - popupContentSize.height - gap).roundToInt()
        val y = if (above >= margin * 3) above else (bottom + below).roundToInt()
        return IntOffset(x, y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)))
    }
}

@Composable
private fun NebToolbarPopup(state: NebTextToolbarState, m: NebTextToolbarState.Menu) {
    val view = LocalView.current
    val density = LocalDensity.current
    val loc = IntArray(2).also { view.getLocationInWindow(it) }
    val provider = remember(m.rect, loc[0], loc[1]) {
        with(density) { ToolbarPosition(m.rect, loc[0], loc[1], 10.dp.toPx().roundToInt(), 34.dp.toPx().roundToInt(), 8.dp.toPx().roundToInt()) }
    }
    val c = MaterialTheme.colorScheme
    val extras = state.extras
    val hasSelection = m.copy != null || m.cut != null
    val twoRow = extras.isNotEmpty() && hasSelection
    val shape = RoundedCornerShape(if (twoRow) 24.dp else 26.dp)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 700f)) }
    fun run(block: (() -> Unit)?, keepOpen: Boolean = false) = {
        block?.invoke()
        if (!keepOpen) state.hide()
    }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .widthIn(max = 336.dp)
                .graphicsLayer {
                    val k = enter.value
                    alpha = k.coerceIn(0f, 1f)
                    val s = 0.88f + 0.12f * k
                    scaleX = s; scaleY = s
                }
                .clip(shape)
                .background(nebFloatColor())
                .border(1.dp, nebFloatLine(), shape)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (twoRow) {
                Row(
                    Modifier
                        .nebHorizontalScroll( androidx.compose.foundation.rememberScrollState() )
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                ) {
                    extras.forEach { a ->
                        ToolIcon(a.icon, a.label, a.active, run(a.onClick, a.keepMenuOpen))
                    }
                }
                Box(
                    Modifier
                        .padding(vertical = 3.dp, horizontal = 6.dp)
                        .width(280.dp)
                        .height(1.dp)
                        .background(c.outlineVariant.copy(alpha = 0.55f))
                )
                Row(
                    Modifier.nebHorizontalScroll( androidx.compose.foundation.rememberScrollState() ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    m.cut?.let { ToolText("Cut", run(it)) }
                    m.copy?.let { ToolText("Copy", run(it)) }
                    m.paste?.let { ToolText("Paste", run(it)) }
                    m.selectAll?.let { ToolText("Select all", run(it)) }
                }
            } else {
                Row(
                    Modifier.nebHorizontalScroll( androidx.compose.foundation.rememberScrollState() ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    m.cut?.let { ToolText("Cut", run(it)) }
                    m.copy?.let { ToolText("Copy", run(it)) }
                    m.paste?.let { ToolText("Paste", run(it)) }
                    m.selectAll?.let { ToolText("Select all", run(it)) }
                    if (extras.isNotEmpty()) {
                        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(c.outlineVariant))
                        extras.forEach { a -> ToolIcon(a.icon, a.label, a.active, run(a.onClick, a.keepMenuOpen)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolText(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            tint = if (active) c.onPrimaryContainer else c.onSurface,
            modifier = Modifier.size(19.dp)
        )
    }
}
