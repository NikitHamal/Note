@file:OptIn(ExperimentalLayoutApi::class)

package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.ui.components.nebPressable
import com.neb.ians.ui.theme.NebShapes

/** Every control of the notes screens is a tonal tile with finite radii; the inner edge of a joined group is 8dp, never square. */
internal val TileRadius = 24.dp
internal val JoinEdge = NebShapes.InnerEdge
internal val TightJoinEdge = NebShapes.TightEdge

internal fun joinStart(outer: Dp = 28.dp, inner: Dp = JoinEdge): Shape = NebShapes.joinStart(outer, inner)
internal fun joinEnd(outer: Dp = 28.dp, inner: Dp = JoinEdge): Shape = NebShapes.joinEnd(outer, inner)
internal fun joinTop(outer: Dp = TileRadius, inner: Dp = JoinEdge): Shape = NebShapes.joinTop(outer, inner)
internal fun joinBottom(outer: Dp = TileRadius, inner: Dp = JoinEdge): Shape = NebShapes.joinBottom(outer, inner)
internal val JoinMid: Shape = RoundedCornerShape(JoinEdge)
internal val TightJoinMid: Shape = RoundedCornerShape(TightJoinEdge)

internal fun gridCellShape(row: Int, col: Int, rows: Int, cols: Int, outer: Dp = TileRadius, inner: Dp = JoinEdge): Shape =
    NebShapes.gridCell(row, col, rows, cols, outer, inner)

internal fun stackItemShape(index: Int, count: Int, outer: Dp = TileRadius, inner: Dp = JoinEdge): Shape = when {
    count <= 1 -> RoundedCornerShape(outer)
    index == 0 -> joinTop(outer, inner)
    index == count - 1 -> joinBottom(outer, inner)
    else -> RoundedCornerShape(inner)
}

internal fun rowItemShape(index: Int, count: Int, outer: Dp = TileRadius, inner: Dp = JoinEdge): Shape = when {
    count <= 1 -> RoundedCornerShape(outer)
    index == 0 -> joinStart(outer, inner)
    index == count - 1 -> joinEnd(outer, inner)
    else -> RoundedCornerShape(inner)
}

/**
 * Resting surface tile on the page (`#202128` in dark, `#F7F7F8` in light):
 * used for top-bar buttons, icon tile, `+ Tag` / `Cover` chips, Audio card, Voice cards, and Dictation strip.
 */
@Composable
@ReadOnlyComposable
internal fun noteSurfaceTileColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() < 0.5f) c.surfaceContainerLowest else c.surfaceContainerLow
}

/**
 * Elevated tonal tile (`#2B2C33` in dark, `#EBECEF` in light):
 * used for tiles inside sheets/cards, segmented controls, active tag chips, and the bottom dock (`Aa | +`).
 */
@Composable
@ReadOnlyComposable
internal fun noteRaisedTileColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() < 0.5f) c.surfaceContainer else c.surfaceContainerHigh
}

/** A pressable tonal surface. Press scales it on the render thread; the shape stays rounded. */
@Composable
internal fun NoteTile(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(TileRadius),
    container: Color = noteRaisedTileColor(),
    content: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    body: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .then(if (onClick != null) Modifier.nebPressable(enabled = enabled, onClick = onClick) else Modifier)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides content) { body() } }
}

/** An icon button of the top bar and dock. */
@Composable
internal fun NoteButton(
    icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    container: Color = noteSurfaceTileColor(),
    content: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    width: Dp = 44.dp, height: Dp = 44.dp,
) {
    NoteTile(onClick, modifier.size(width, height), shape, container, content, enabled) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (enabled) content else content.copy(alpha = 0.38f))
    }
}

/** A label tile: used for segments, suggestions and sheet actions. */
@Composable
internal fun NoteLabelTile(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, selected: Boolean = false,
    shape: Shape = RoundedCornerShape(TileRadius),
    height: Dp = 48.dp, size: Int = 13,
    container: Color = noteRaisedTileColor(),
) {
    val c = MaterialTheme.colorScheme
    NoteTile(
        onClick, modifier.height(height), shape,
        container = if (selected) c.primary else container,
        content = if (selected) c.onPrimary else c.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp))
            Text(
                label, fontSize = size.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Connected M3X segmented/scattered row: outer ends of the group use `height / 2` (`outer`),
 * and all inner adjacent corners stay at `8dp` (`JoinEdge`), whether selected or unselected.
 */
@Composable
internal fun <T> NoteSegments(
    options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, height: Dp = 48.dp,
    container: Color = noteRaisedTileColor(),
    icons: List<ImageVector?>? = null,
) {
    val outer = height / 2
    if (options.size > 4) {
        val rows = options.chunked(4)
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.padding(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEachIndexed { cIdx, (v, label) ->
                        val on = v == selected
                        val shape = gridCellShape(r, cIdx, rows.size, row.size, outer, JoinEdge)
                        NoteLabelTile(label, { onSelect(v) }, Modifier.weight(1f), selected = on, shape = shape, height = height, container = container)
                    }
                }
            }
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, (v, label) ->
            val on = v == selected
            val shape = rowItemShape(i, options.size, outer = outer, inner = JoinEdge)
            NoteLabelTile(
                label, { onSelect(v) }, Modifier.weight(1f),
                icon = icons?.getOrNull(i), selected = on, shape = shape, height = height, container = container,
            )
        }
    }
}
