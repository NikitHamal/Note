@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.neb.ians.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * The landing page's four tonal accents (azure, sky, aqua, periwinkle), as flat fills.
 * [container] is the soft tile, [deep] the ink that reads on it, [vivid] the saturated
 * fill. The values are the web tokens, so a shape on the phone is the same blue family
 * as the same shape on the site.
 */
enum class NebTone(
    private val containerLight: Long,
    private val containerDark: Long,
    private val deepLight: Long,
    private val deepDark: Long,
    val vivid: Color
) {
    Azure(0xFFD7E3FF, 0xFF1C3470, 0xFF0047B8, 0xFF9DB8FF, Color(0xFF7AA2FF)),
    Sky(0xFFCDEEFF, 0xFF0B3A52, 0xFF00598A, 0xFF8FDCFF, Color(0xFF5CC8FF)),
    Aqua(0xFFC6F3EB, 0xFF0B3B37, 0xFF0A6A62, 0xFF8AE9DA, Color(0xFF55D9C6)),
    Peri(0xFFE3E9FF, 0xFF25306A, 0xFF2A47A3, 0xFFC1CCFF, Color(0xFFA9B9FF));

    fun container(dark: Boolean): Color = Color(if (dark) containerDark else containerLight)
    fun deep(dark: Boolean): Color = Color(if (dark) deepDark else deepLight)
}

/** The silhouettes the landing page uses for its glyph tiles, by the names it gives them. */
object NebGlyphShapes {
    val Cookie: RoundedPolygon get() = MaterialShapes.Cookie9Sided
    val Sunny: RoundedPolygon get() = MaterialShapes.Sunny
    val Flower: RoundedPolygon get() = MaterialShapes.Flower
    val Clover: RoundedPolygon get() = MaterialShapes.Clover4Leaf
    val Gem: RoundedPolygon get() = MaterialShapes.Gem
    val Burst: RoundedPolygon get() = MaterialShapes.SoftBurst
    val Pill: RoundedPolygon get() = MaterialShapes.Pill
    val Pentagon: RoundedPolygon get() = MaterialShapes.Pentagon
    val Square: RoundedPolygon get() = MaterialShapes.Square
}

/**
 * Draws [polygon] into a square of [side] centred on [center]. Polygons from
 * [MaterialShapes] live in a unit box, so a scale is all the fitting they need.
 */
fun DrawScope.drawGlyph(
    path: Path,
    center: Offset,
    side: Float,
    color: Color,
    rotation: Float = 0f,
    alpha: Float = 1f
) {
    if (side <= 0f || alpha <= 0f) return
    withTransform({
        translate(center.x - side / 2f, center.y - side / 2f)
        rotate(rotation, pivot = Offset(side / 2f, side / 2f))
        scale(side, side, pivot = Offset.Zero)
    }) {
        drawPath(path, color, alpha = alpha)
    }
}

/** A polygon as a compose [Path] in the unit box, built once. */
@Composable
fun rememberGlyphPath(polygon: RoundedPolygon): Path = remember(polygon) {
    Morph(polygon, polygon).toPath(0f)
}

/**
 * One blob that morphs through [shapes] in order. [position] runs 0..shapes.lastIndex and is
 * read in the draw phase only, so animating it never recomposes.
 */
@Composable
fun NebMorphBlob(
    shapes: List<RoundedPolygon>,
    color: Color,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
    position: () -> Float
) {
    val morphs = remember(shapes) { shapes.zipWithNext { a, b -> Morph(a, b) } }
    val path = remember(morphs) { Path() }
    val lastPos = remember(morphs) { floatArrayOf(Float.NaN) }
    Canvas(modifier) {
        if (morphs.isEmpty()) return@Canvas
        val p = position().coerceIn(0f, morphs.size.toFloat())
        if (p != lastPos[0]) {
            lastPos[0] = p
            val index = p.toInt().coerceAtMost(morphs.size - 1)
            path.rewind()
            morphs[index].toPath(progress = p - index, path = path)
        }
        val side = size.minDimension
        drawGlyph(path, Offset(size.width / 2f, size.height / 2f), side, color, rotation())
    }
}
