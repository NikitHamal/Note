package com.neb.ians.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.neb.ians.ui.theme.LocalNebAuthPalette
import com.neb.ians.ui.theme.NebAuthTokens
import com.neb.ians.ui.theme.nebEffectsSpec

// ---------------------------------------------------------------------------
// The Material 3 Expressive layer.
//
// Expressive ships three things worth having and nothing worth theming twice: a
// button group whose members widen under the thumb, a toggle button that morphs
// its own corners between resting, pressed and checked, and a set of named
// shapes that are polygons rather than rounded rectangles. Everything here is
// those primitives wearing the journey's graphite palette, so a call site picks
// a control and never a colour.
// ---------------------------------------------------------------------------

/** How far the chosen pill sits inside the track that carries it. */
private val TrackInset = 4.dp

/** One member of a segmented bank: the stored value, the word on it, an optional glyph. */
@Immutable
data class NebSegment(
    val value: String,
    val label: String,
    val icon: ImageVector? = null
)

/**
 * Two to four one-word answers in a single connected bank — gender, a unit, a
 * yes/no that deserves more presence than a switch.
 *
 * It reads as one continuous control because it is one: a track in the field
 * colour carries the segments, and the segments themselves are transparent
 * until chosen, so there is no seam and no gap between the answers. The pill
 * that marks the choice slides inside the track rather than replacing a tile.
 *
 * Underneath it is still Expressive's [ButtonGroup] and [ToggleButton], so the
 * segment under the thumb widens while its neighbours give way and its corners
 * morph from round at rest to square while pressed. None of that is drawn here;
 * it is the components' own behaviour, which is the point of using them instead
 * of hand-rolling a row of boxes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NebSegmentedChoice(
    segments: List<NebSegment>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 56.dp
) {
    val palette = LocalNebAuthPalette.current
    val colors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = Color.Transparent,
        contentColor = palette.inkMuted,
        checkedContainerColor = palette.accent,
        checkedContentColor = palette.onAccent
    )
    val interactions = remember(segments) { segments.map { MutableInteractionSource() } }

    ButtonGroup(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NebAuthTokens.PillRadius))
            .background(palette.field)
            .padding(TrackInset),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        segments.forEachIndexed { index, segment ->
            val checked = segment.value == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(segment.value) },
                modifier = Modifier
                    .weight(1f)
                    .animateWidth(interactions[index])
                    .heightIn(min = minHeight - TrackInset * 2),
                shapes = ToggleButtonDefaults.shapes(),
                colors = colors,
                interactionSource = interactions[index],
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                if (segment.icon != null) {
                    Icon(
                        imageVector = segment.icon,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(
                    text = segment.label,
                    style = NebAuthType.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The same bank where the answers carry no glyph — an institution type, a unit. */
@Composable
fun NebSegmentedChoice(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(options) { options.map { NebSegment(it, it) } }
    NebSegmentedChoice(segments, selected, onSelect, modifier)
}

/**
 * A named Expressive polygon as a [Shape]. Every tile, avatar frame and badge in
 * the redesigned surfaces is cut with one of these instead of a rounded
 * rectangle — it is the cheapest way to look like nothing else on the platform.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun nebShape(polygon: RoundedPolygon, startAngle: Int = 0): Shape = polygon.toShape(startAngle)

/** The shapes the app reaches for by name, so a screen never indexes into [MaterialShapes]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object NebShapes {
    val Tile: RoundedPolygon get() = MaterialShapes.Square
    val Accent: RoundedPolygon get() = MaterialShapes.Clover4Leaf

    /**
     * The bottom bar's create button.
     *
     * A four-lobed clover, chosen over the cookies for a reason that only shows
     * up in motion: opening the menu turns the button 40 degrees, and the clover
     * has ninety-degree rotational symmetry, so at that angle the silhouette is
     * genuinely different rather than landing back on itself. A nine-sided
     * cookie turned by 40 degrees is pixel-identical to where it started.
     */
    val Create: RoundedPolygon get() = MaterialShapes.Clover4Leaf

    /**
     * One silhouette per option in a bank of roles. A column of four identical
     * rounded squares is a form; four different silhouettes is a choice, and the
     * user can tell which one they picked from across the room.
     */
    val Option: List<RoundedPolygon>
        get() = listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Gem,
            MaterialShapes.Slanted
        )

    fun option(index: Int): RoundedPolygon = Option[index.coerceAtLeast(0) % Option.size]
}

/**
 * A container cut to one of [NebShapes]. Used for avatars and glyph tiles, where
 * the silhouette does the decorating so nothing else has to.
 */
@Composable
fun NebShapedBox(
    polygon: RoundedPolygon,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    fill: Color? = null,
    startAngle: Int = 0,
    content: @Composable () -> Unit = {}
) {
    val palette = LocalNebAuthPalette.current
    val background by animateColorAsState(
        targetValue = fill ?: palette.accentSoft,
        animationSpec = nebEffectsSpec(),
        label = "neb_shaped_fill"
    )
    Box(
        modifier = modifier
            .size(diameter)
            .clip(nebShape(polygon, startAngle))
            .background(background),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

/**
 * Expressive's own loading indicator: a sequence of [MaterialShapes] morphing
 * into one another. It replaces the spinner wherever the journey waits, because
 * a morphing polygon reads as the same design language as everything around it
 * and a circular arc does not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NebLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalNebAuthPalette.current.accent
) {
    LoadingIndicator(modifier = modifier, color = color)
}
