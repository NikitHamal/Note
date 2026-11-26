package com.neb.ians.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val NEBiansShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Global M3X finite shape tokens and connected-group corner helpers (see `docs/M3X_DESIGN_SYSTEM.md` section 5).
 * Joined tiles, segmented rows, vertical stacks, and 2D bento grids share an 8dp inner corner and a 24–28dp outer corner.
 */
object NebShapes {
    val InnerEdge = 8.dp
    val TightEdge = 6.dp
    val Card = 28.dp
    val Tile = 24.dp
    val Pill = 28.dp
    val SheetTop = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    fun joinStart(outer: Dp = Pill, inner: Dp = InnerEdge): Shape =
        RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)

    fun joinEnd(outer: Dp = Pill, inner: Dp = InnerEdge): Shape =
        RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)

    fun joinTop(outer: Dp = Tile, inner: Dp = InnerEdge): Shape =
        RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)

    fun joinBottom(outer: Dp = Tile, inner: Dp = InnerEdge): Shape =
        RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)

    fun rowItem(index: Int, count: Int, outer: Dp = Pill, inner: Dp = InnerEdge): Shape = when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> joinStart(outer, inner)
        index == count - 1 -> joinEnd(outer, inner)
        else -> RoundedCornerShape(inner)
    }

    fun stackItem(index: Int, count: Int, outer: Dp = Tile, inner: Dp = InnerEdge): Shape = when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> joinTop(outer, inner)
        index == count - 1 -> joinBottom(outer, inner)
        else -> RoundedCornerShape(inner)
    }

    fun gridCell(row: Int, col: Int, rows: Int, cols: Int, outer: Dp = Tile, inner: Dp = InnerEdge): Shape =
        RoundedCornerShape(
            topStart = if (row == 0 && col == 0) outer else inner,
            topEnd = if (row == 0 && col == cols - 1) outer else inner,
            bottomStart = if (row == rows - 1 && col == 0) outer else inner,
            bottomEnd = if (row == rows - 1 && col == cols - 1) outer else inner,
        )
}
