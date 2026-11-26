@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.neb.ians.ui.screens.notes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.neb.ians.ui.components.nebHorizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Task
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.neb.ians.ui.components.NebGlyphShapes
import com.neb.ians.ui.components.NebModalSheet
import com.neb.ians.ui.components.NebTone
import com.neb.ians.ui.components.drawGlyph
import com.neb.ians.ui.components.rememberGlyphPath

/**
 * A note's page icon: a glyph from this fixed set, stored by key. Vectors only, so the icon
 * follows the theme and there is never an emoji in the data.
 */
object NoteIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "page" to Icons.Outlined.Description,
        "book" to Icons.Outlined.MenuBook,
        "school" to Icons.Outlined.School,
        "science" to Icons.Outlined.Science,
        "bio" to Icons.Outlined.Biotech,
        "math" to Icons.Outlined.Calculate,
        "idea" to Icons.Outlined.Lightbulb,
        "mind" to Icons.Outlined.Psychology,
        "star" to Icons.Outlined.Star,
        "heart" to Icons.Outlined.Favorite,
        "flag" to Icons.Outlined.Flag,
        "bolt" to Icons.Outlined.Bolt,
        "draw" to Icons.Outlined.Draw,
        "brush" to Icons.Outlined.Brush,
        "palette" to Icons.Outlined.Palette,
        "code" to Icons.Outlined.Code,
        "terminal" to Icons.Outlined.Terminal,
        "task" to Icons.Outlined.Task,
        "assignment" to Icons.Outlined.Assignment,
        "event" to Icons.Outlined.Event,
        "timer" to Icons.Outlined.Timer,
        "work" to Icons.Outlined.Work,
        "group" to Icons.Outlined.Group,
        "home" to Icons.Outlined.Home,
        "world" to Icons.Outlined.Public,
        "language" to Icons.Outlined.Language,
        "translate" to Icons.Outlined.Translate,
        "map" to Icons.Outlined.Map,
        "music" to Icons.Outlined.MusicNote,
        "lock" to Icons.Outlined.Lock,
        "bookmark" to Icons.Outlined.Bookmark,
        "pets" to Icons.Outlined.Pets,
        "eco" to Icons.Outlined.Eco,
        "sun" to Icons.Outlined.WbSunny,
        "moon" to Icons.Outlined.NightsStay,
        "cloud" to Icons.Outlined.Cloud,
        "fire" to Icons.Outlined.LocalFireDepartment,
        "rocket" to Icons.Outlined.RocketLaunch,
        "trophy" to Icons.Outlined.EmojiEvents,
        "game" to Icons.Outlined.SportsEsports,
        "headphones" to Icons.Outlined.Headphones,
        "camera" to Icons.Outlined.CameraAlt,
        "movie" to Icons.Outlined.Movie,
        "food" to Icons.Outlined.Restaurant,
        "fitness" to Icons.Outlined.FitnessCenter,
        "cart" to Icons.Outlined.ShoppingCart,
        "savings" to Icons.Outlined.Savings,
        "flight" to Icons.Outlined.Flight,
        "car" to Icons.Outlined.DirectionsCar,
        "build" to Icons.Outlined.Build,
        "puzzle" to Icons.Outlined.Extension,
        "gavel" to Icons.Outlined.Gavel,
        "quiz" to Icons.Outlined.Quiz,
        "health" to Icons.Outlined.LocalHospital,
        "diamond" to Icons.Outlined.Diamond,
        "soccer" to Icons.Outlined.SportsSoccer,
        "coffee" to Icons.Outlined.LocalCafe,
        "bike" to Icons.Outlined.DirectionsBike,
        "beach" to Icons.Outlined.BeachAccess,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
        "chart" to Icons.Outlined.BarChart,
        "shield" to Icons.Outlined.Shield,
        "gift" to Icons.Outlined.CardGiftcard,
        "article" to Icons.Outlined.Article,
        "folder" to Icons.Outlined.Folder,
        "inbox" to Icons.Outlined.Inbox,
        "chip" to Icons.Outlined.Memory,
        "podcast" to Icons.Outlined.Podcasts,
        "park" to Icons.Outlined.Park,
        "cafe" to Icons.Outlined.LocalCafe,
        "verified" to Icons.Outlined.Verified,
    )

    private val byKey: Map<String, ImageVector> = all.toMap()

    fun find(key: String): ImageVector? = if (key.isEmpty()) null else byKey[key]
}

/** The icon tile that sits on a note's page, in the page's tone. */
@Composable
fun NoteIconTile(key: String, size: Dp, container: Color, content: Color, modifier: Modifier = Modifier) {
    val vector = NoteIcons.find(key) ?: return
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(vector, null, tint = content, modifier = Modifier.size(size * 0.54f))
    }
}

@Immutable
private class Palette(val bgL: Long, val bgD: Long, val deepL: Long, val deepD: Long, val vivid: Color) {
    fun bg(dark: Boolean) = Color(if (dark) bgD else bgL)
    fun deep(dark: Boolean) = Color(if (dark) deepD else deepL)
}

@Immutable
private class CoverSpec(
    val pal: Palette?, val tone: NebTone?,
    val a: RoundedPolygon, val ax: Float, val ay: Float, val asz: Float, val ar: Float,
    val b: RoundedPolygon, val bx: Float, val by: Float, val bsz: Float, val br: Float,
    val pattern: Int = 0,
) {
    fun bg(dark: Boolean) = tone?.container(dark) ?: pal!!.bg(dark)
    fun ink(dark: Boolean) = tone?.deep(dark) ?: pal!!.deep(dark)
    val vivid: Color get() = tone?.vivid ?: pal!!.vivid
}

private val extraPalettes = listOf(
    Palette(0xFFF5EBDD, 0xFF3A3126, 0xFF7A5C2E, 0xFFEBDDC5, Color(0xFFE2B66B)),
    Palette(0xFFDDF3E4, 0xFF17382A, 0xFF1F7A4D, 0xFFBFEBD0, Color(0xFF6FD39A)),
    Palette(0xFFFFE3D3, 0xFF442618, 0xFFB5521C, 0xFFFFD0B5, Color(0xFFFFA173)),
    Palette(0xFFFBF1C4, 0xFF3B3512, 0xFF8A7500, 0xFFF3E8A3, Color(0xFFF2D04B)),
    Palette(0xFFBFE6F7, 0xFF0C3550, 0xFF0B6DA0, 0xFFB4E2FA, Color(0xFF4FB6E8)),
    Palette(0xFF2B2E36, 0xFF1A1C22, 0xFFC9CDD8, 0xFFC9CDD8, Color(0xFF59607A)),
    Palette(0xFF0F1A33, 0xFF0A1226, 0xFFB9CCFF, 0xFFB9CCFF, Color(0xFF2E56C9)),
    Palette(0xFF163B2A, 0xFF0F2A1E, 0xFFA7E3C0, 0xFFA7E3C0, Color(0xFF2E9A66)),
    Palette(0xFFFFDCE0, 0xFF45202A, 0xFFB3263E, 0xFFFFC2CB, Color(0xFFFF8CA0)),
    Palette(0xFFD2F3EF, 0xFF0F3A36, 0xFF0B7A70, 0xFFB2EAE3, Color(0xFF3CC4B5)),
    Palette(0xFFE4E8EF, 0xFF262B36, 0xFF4A5670, 0xFFCDD4E3, Color(0xFF8C9AB8)),
    Palette(0xFFFFE0DB, 0xFF4A2420, 0xFFC4412F, 0xFFFFC7BE, Color(0xFFFF8C7A)),
    Palette(0xFFD9E6FF, 0xFF14264F, 0xFF0B4AC2, 0xFFB5CCFF, Color(0xFF5B8DEF)),
    Palette(0xFF2E2A27, 0xFF1E1B19, 0xFFE0D6CC, 0xFFE0D6CC, Color(0xFF7A6A5A)),
)

/** Cover 0 is none. 1..[count] are flat tonal banners with two big shapes, positions fixed per cover. */
object NoteCovers {
    /** Drawn banners, 1 to 40. */
    const val drawn = 40
    /** 41 to 112 are the v7 covers drawn by code (`NoteArtCovers`). */
    const val count = drawn + NoteArtCovers.count
}

private fun coverSpec(index: Int): CoverSpec? = if (index > 8) generatedCover(index) else when (index) {
    1 -> CoverSpec(null, NebTone.Azure, NebGlyphShapes.Cookie, 0.82f, 0.30f, 1.9f, 12f, NebGlyphShapes.Pill, 0.18f, 0.95f, 1.0f, -20f)
    2 -> CoverSpec(null, NebTone.Sky, NebGlyphShapes.Flower, 0.16f, 0.35f, 1.7f, 0f, NebGlyphShapes.Gem, 0.78f, 0.85f, 1.1f, 15f)
    3 -> CoverSpec(null, NebTone.Aqua, NebGlyphShapes.Sunny, 0.70f, 0.40f, 2.0f, 25f, NebGlyphShapes.Clover, 0.10f, 0.90f, 1.0f, 0f)
    4 -> CoverSpec(null, NebTone.Peri, NebGlyphShapes.Clover, 0.88f, 0.55f, 1.8f, 0f, NebGlyphShapes.Burst, 0.30f, 0.10f, 1.1f, 30f)
    5 -> CoverSpec(null, NebTone.Azure, NebGlyphShapes.Burst, 0.25f, 0.45f, 2.0f, 8f, NebGlyphShapes.Flower, 0.90f, 0.10f, 1.0f, 0f)
    6 -> CoverSpec(null, NebTone.Sky, NebGlyphShapes.Pentagon, 0.60f, 0.50f, 1.9f, -10f, NebGlyphShapes.Cookie, 0.05f, 0.15f, 1.1f, 20f)
    7 -> CoverSpec(null, NebTone.Aqua, NebGlyphShapes.Gem, 0.85f, 0.45f, 1.8f, 0f, NebGlyphShapes.Sunny, 0.22f, 0.80f, 1.2f, 0f)
    8 -> CoverSpec(null, NebTone.Peri, NebGlyphShapes.Cookie, 0.12f, 0.50f, 1.8f, 0f, NebGlyphShapes.Pill, 0.80f, 0.20f, 1.3f, 40f)
    else -> null
}

private fun generatedCover(i: Int): CoverSpec? {
    if (i !in 9..NoteCovers.drawn) return null
    val k = i - 9
    val bag = listOf(NebGlyphShapes.Cookie, NebGlyphShapes.Sunny, NebGlyphShapes.Flower, NebGlyphShapes.Clover, NebGlyphShapes.Gem, NebGlyphShapes.Burst, NebGlyphShapes.Pentagon, NebGlyphShapes.Pill)
    val pattern = when (k % 5) { 0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; else -> 0 }
    return CoverSpec(
        extraPalettes[k % extraPalettes.size], null,
        bag[(k * 3 + 1) % 8], 0.15f + ((k * 37) % 70) / 100f, 0.30f + ((k * 23) % 40) / 100f, 1.6f + (k % 4) * 0.15f, ((k * 17) % 50 - 20).toFloat(),
        bag[(k * 5 + 4) % 8], 0.10f + ((k * 29) % 80) / 100f, 0.15f + ((k * 41) % 70) / 100f, 0.9f + (k % 3) * 0.15f, ((k * 31) % 90 - 40).toFloat(),
        pattern,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPattern(kind: Int, color: Color) {
    val step = 18.dp.toPx()
    when (kind) {
        1 -> {
            var y = step / 2
            while (y < size.height) {
                var x = step / 2 + (if (((y / step).toInt() % 2) == 0) 0f else step / 2)
                while (x < size.width) { drawCircle(color, 2.2.dp.toPx(), Offset(x, y)); x += step }
                y += step
            }
        }
        2 -> {
            var x = -size.height
            while (x < size.width) {
                drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                x += step * 1.2f
            }
        }
        3 -> {
            var x = 0f
            while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step * 1.6f }
            var y = 0f
            while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step * 1.6f }
        }
        4 -> {
            val path = androidx.compose.ui.graphics.Path()
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx())
            var y = step
            while (y < size.height + step) {
                path.rewind()
                path.moveTo(0f, y)
                var x = 0f
                while (x < size.width) {
                    path.relativeQuadraticBezierTo(step / 2, -step / 2, step, 0f)
                    path.relativeQuadraticBezierTo(step / 2, step / 2, step, 0f)
                    x += step * 2
                }
                drawPath(path, color, style = stroke)
                y += step * 1.3f
            }
        }
    }
}

/** Width over height of the note editor's cover frame (a little shorter than before), the frame the 40 drawn banners are composed on. */
const val NoteCoverFrameAspect = 2.5f

/** Shape size at the reference frame, as a share of its height (the size the banners were drawn with). */
private const val NoteCoverUnit = 0.9f

/**
 * Zoom of the drawn banners: 1 is the fully cropped-in look, 0.62 was too far out and 0.80 still a little far. Size and the distance of every centre
 * from the middle of the frame scale by the same factor, so the picture keeps its width and height proportion and only the
 * view moves a little closer (0.80).
 */
internal const val NoteCoverZoom = 0.90f

/** A centre's place across (or down) the frame, [frac] of the frame, pulled toward the middle by [NoteCoverZoom]. */
internal fun noteCoverPos(frac: Float): Float = 0.5f + (frac - 0.5f) * NoteCoverZoom

/**
 * The 40 drawn banners, one composition, any frame. The reference frame is the editor's (2.5 : 1). A frame of that shape shows
 * it exactly; a wider one (a card, a strip) keeps the shapes the size they have at the same height and spreads their centres
 * across the width; a taller one keeps them the size they have at the same width. So the size follows the frame's limiting side
 * and a circle stays a circle, a cookie stays a cookie, at every size. Nothing is stretched, nothing is cropped away.
 */
internal fun noteCoverUnit(w: Float, h: Float): Float = NoteCoverUnit * NoteCoverZoom * minOf(w / NoteCoverFrameAspect, h)

/** The banner itself. Shapes settle in with a spring when the cover appears or changes, once, never in a loop. */
@Composable
fun NoteCoverBanner(cover: Int, modifier: Modifier = Modifier, animate: Boolean = true) {
    if (NoteArtCovers.has(cover)) { NoteArtCover(cover, modifier, live = animate); return }
    val spec = remember(cover) { coverSpec(cover) } ?: return
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pa = rememberGlyphPath(spec.a)
    val pb = rememberGlyphPath(spec.b)
    val motion = animate && !com.neb.ians.ui.theme.nebAnimationsReduced()
    val settle = remember(cover, motion) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(cover, motion) {
        if (motion) settle.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    val bg = spec.bg(dark)
    val ink = spec.ink(dark)
    val vivid = spec.vivid
    Canvas(modifier.clipToBounds().background(bg)) {
        val t = settle.value
        if (spec.pattern != 0) drawPattern(spec.pattern, ink.copy(alpha = if (dark) 0.16f else 0.12f))
        val fade = t.coerceIn(0f, 1f)
        val h = size.height
        val w = size.width
        val u = noteCoverUnit(w, h)
        drawGlyph(
            pa, Offset(w * noteCoverPos(spec.ax), h * noteCoverPos(spec.ay)), u * spec.asz * (0.55f + 0.45f * t), vivid,
            rotation = spec.ar - 40f * (1f - t), alpha = (if (dark) 0.5f else 0.85f) * fade,
        )
        drawGlyph(
            pb, Offset(w * noteCoverPos(spec.bx), h * noteCoverPos(spec.by)), u * spec.bsz * (0.4f + 0.6f * t), ink,
            rotation = spec.br + 60f * (1f - t), alpha = 0.26f * fade,
        )
    }
}

/** A notes sheet: the same sheet as everywhere, with the content inset to the title's edge so nothing touches the screen sides. */
@Composable
fun NoteSheet(onDismiss: () -> Unit, title: String?, inset: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    NebModalSheet(onDismiss = onDismiss, title = title, horizontalPadding = inset, fullBleed = true) { // bleed: content pads itself or draws edge to edge rows
        Column(Modifier.fillMaxWidth().padding(horizontal = inset), content = content)
    }
}

@Composable
fun NoteIconSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    NoteSheet(onDismiss = onDismiss, title = "Icon") {
        FlowRow(
            Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(c.surfaceContainerHigh)
                    .clickable { onPick("") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "No icon", tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
            NoteIcons.all.forEach { (key, vector) ->
                val on = key == current
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(if (on) 27.dp else 18.dp))
                        .background(if (on) c.primary else c.surfaceContainerHigh)
                        .clickable { onPick(key) },
                    contentAlignment = Alignment.Center,
                ) { Icon(vector, key, tint = if (on) c.onPrimary else c.onSurface, modifier = Modifier.size(24.dp)) }
            }
        }
        Box(Modifier.height(16.dp))
    }
}

@Composable
fun NoteCoverSheet(current: Int, onPick: (Int) -> Unit, onPhoto: (android.net.Uri) -> Unit, onDismiss: () -> Unit) {
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPhoto(uri) }
    NoteSheet(onDismiss = onDismiss, title = "Cover", inset = 16.dp) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }, key = "cover_source") {
                NoteSegments(
                    options = listOf(0 to "No cover", -1 to "Gallery"),
                    selected = when {
                        current == 0 -> 0
                        current < 0 -> -1
                        else -> -99
                    },
                    onSelect = { v ->
                        if (v == 0) onPick(0)
                        else gallery.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                    icons = listOf(Icons.Outlined.Close, Icons.Outlined.Image),
                )
            }
            items(count = NoteCovers.count, key = { it + 1 }, contentType = { "cover_tile" }) { idx ->
                val i = idx + 1
                val tileShape = RoundedCornerShape(20.dp)
                NoteCoverBanner(
                    i, animate = false,
                    modifier = Modifier.fillMaxWidth().height(76.dp)
                        .clip(tileShape)
                        .then(if (i == current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, tileShape) else Modifier)
                        .clickable { onPick(i) },
                )
            }
        }
    }
}

/** One tap sets colour, cover and icon together. */
@Composable
fun NoteThemeRow(currentTone: Int, onPick: (NoteTheme) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().nebHorizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoteTones.themes.forEach { th ->
            val tone = noteTone(th.tone)
            Column(
                Modifier.width(112.dp).clip(RoundedCornerShape(22.dp)).background(tone.container)
                    .clickable { onPick(th) },
            ) {
                NoteCoverBanner(th.cover, animate = false, modifier = Modifier.fillMaxWidth().height(52.dp))
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NoteIcons.find(th.icon)?.let { Icon(it, null, tint = tone.content, modifier = Modifier.size(16.dp)) }
                    Text(th.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tone.content, maxLines = 1)
                }
            }
        }
    }
}
