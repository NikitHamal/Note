package com.neb.ians.ui.components

import android.content.Context
import android.provider.Settings
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.neb.ians.ui.components.m3x.M3xLayouts
import com.neb.ians.ui.components.m3x.M3xPalette
import com.neb.ians.ui.components.m3x.M3xPiece
import com.neb.ians.ui.theme.subjectFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The M3X art library drawn by code, like the note banners: no pictures in the APK, no network, sharp at any size, the same
 * (layout, palette, seed) as the site's js/m3x-art.js. The APK carries only `assets/m3x/covers.json` (about 9 KB): the 175 v7 covers as
 * [id, layout, palette, seed] and six v1 thumbnail recipes per subject family. A drawing is built once (an LRU keeps the last ones),
 * off the main thread, and a pick or first show settles once on a spring (no looping motion).
 */
object M3xArt {
    class Cover(val id: String, val layout: String, val palette: String, val seed: Int)
    class Recipe(val layout: String, val palette: String, val key: String)

    @Volatile private var cached: List<Cover>? = null
    @Volatile private var notes: List<Recipe>? = null

    private fun load(context: Context) = synchronized(this) {
        if (cached != null) return@synchronized
        runCatching {
            val text = context.applicationContext.assets.open("m3x/covers.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val rows = root.getJSONArray("covers")
            cached = List(rows.length()) { i -> rows.getJSONArray(i).let { Cover(it.getString(0), it.getString(1), it.getString(2), it.getInt(3)) } }
        }.onFailure { cached = emptyList() }
    }

    val isLoaded: Boolean get() = cached != null
    val isNotesLoaded: Boolean get() = notes != null
    fun covers(context: Context): List<Cover> { cached ?: load(context); return cached ?: emptyList() }
    fun cover(context: Context, id: String?): Cover? = id?.takeIf { it.isNotBlank() }?.let { want -> covers(context).firstOrNull { it.id == want } }
    fun resolveCover(context: Context, coverId: String?, username: String): Cover? =
        cover(context, coverId) ?: cover(context, defaultCover(context, username)) ?: covers(context).firstOrNull()

    /** FNV-1a over the UTF-8 bytes, unsigned 32 bit. Same as api/cover_art.py `default_cover`. */
    private fun fnv(text: String): Long {
        var h = 2166136261L
        for (b in text.toByteArray(Charsets.UTF_8)) h = ((h xor (b.toLong() and 0xFF)) * 16777619L) and 0xFFFFFFFFL
        return h
    }

    /** The cover a member who never chose one has: a stable pick from the username. */
    fun defaultCover(context: Context, username: String): String {
        val list = covers(context)
        return if (list.isEmpty()) "cover-001" else list[(fnv("cover|$username") % list.size).toInt()].id
    }

    /** The 72 note covers (`assets/m3x/notes.json`, about 2 KB) as recipes; position 0 is note cover 41. */
    fun noteRecipe(context: Context, position: Int): Recipe? {
        val list = notes ?: synchronized(this) {
            notes ?: runCatching {
                val rows = org.json.JSONArray(context.applicationContext.assets.open("m3x/notes.json").bufferedReader().use { it.readText() })
                List(rows.length()) { i -> rows.getJSONArray(i).let { Recipe(it.getString(0), it.getString(1), "note|${it.getInt(2)}||${it.getString(0)}|") } }
            }.getOrDefault(emptyList()).also { notes = it }
        }
        return list.getOrNull(position)
    }

    fun coverKey(c: Cover) = "cover|${c.seed}||${c.layout}|"
    fun trimMemory() = M3xPieces.trim()
}

private object M3xPieces {
    private val lru = LruCache<String, M3xPiece>(96)
    private val bySeed = LruCache<String, M3xPiece>(96)
    fun peek(key: String): M3xPiece? = synchronized(this) { lru.get(key) }
    fun peekBySeed(seedKey: String): M3xPiece? = synchronized(this) { bySeed.get(seedKey) }
    fun get(layout: String, cacheKey: String, seedKey: String, w: Double, h: Double): M3xPiece {
        peek(cacheKey)?.let { return it }
        val built = M3xLayouts.build(layout, seedKey, w, h)
        synchronized(this) {
            lru.put(cacheKey, built)
            bySeed.put(seedKey, built)
        }
        return built
    }
    fun trim() = synchronized(this) {
        lru.evictAll()
        bySeed.evictAll()
    }
}

@Composable
private fun isDarkSurface() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * One drawing. [settle] runs 0 to 1 once, when the cover is picked or first appears (the same spring the first 40 note covers use):
 * the picture grows into its frame and fades in. There is no looping motion, so nothing redraws while the screen is idle.
 */
private fun DrawScope.drawM3x(piece: M3xPiece, tones: Array<Color>, settle: Float) {
    val fit = max(size.width / piece.w, size.height / piece.h)
    val grow = fit * (0.88f + 0.12f * settle)
    val fade = settle.coerceIn(0f, 1f)
    clipRect {
        withTransform({
            translate(size.width / 2f, size.height / 2f)
            scale(grow, grow, Offset.Zero)
            translate(-piece.w / 2f, -piece.h / 2f)
        }) {
            for (i in piece.ops.indices) {
                val op = piece.ops[i]
                val a = op.alpha * fade
                val color = if (a < 1f) tones[op.tone].copy(alpha = a) else tones[op.tone]
                drawPath(op.path, color, style = op.style)
            }
        }
    }
}

/** The aspect the drawing is built for: the box's own, in steps of 0.05, so a cover is never cropped and the cache stays small. */
internal fun m3xFit(widthPx: Int, heightPx: Int): Float =
    (Math.round(widthPx.toFloat() / heightPx.toFloat() * 20f) / 20f).coerceIn(0.6f, 4f)

@Composable
private fun M3xArtView(layout: String, palette: String, key: String, h: Double, modifier: Modifier, live: Boolean) {
    val dark = isDarkSurface()
    val tones = remember(palette, dark) { M3xPalette.tones(palette, dark) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val aspect = if (box.width > 0 && box.height > 0) m3xFit(box.width, box.height) else 0f
    val initialPiece = remember(key, aspect) {
        if (aspect > 0f) {
            val w = Math.round(h * aspect).toInt()
            M3xPieces.peek("$key@$w") ?: M3xPieces.peekBySeed(key)
        } else {
            M3xPieces.peekBySeed(key)
        }
    }
    val piece by produceState<M3xPiece?>(initialPiece, key, aspect) {
        if (aspect <= 0f) return@produceState
        val w = Math.round(h * aspect).toDouble()
        val cacheKey = "$key@${w.toInt()}"
        value = M3xPieces.peek(cacheKey) ?: withContext(Dispatchers.Default) { M3xPieces.get(layout, cacheKey, key, w, h) }
    }
    // [live] = play the settle once for this drawing (a pick, or the first time it shows). Off when animations are off system-wide.
    val motion = live && !com.neb.ians.ui.theme.nebAnimationsReduced()
    val settle = remember(key, motion) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(piece, settle) {
        if (piece != null && settle.value < 1f) settle.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    // the page tone shows at once; the drawing lands as soon as it is built for this box
    Canvas(modifier.onSizeChanged { if (box != it) box = it }.background(tones[0])) {
        val p = piece ?: return@Canvas
        drawM3x(p, tones, settle.value)
    }
}

/** A profile cover from the library. [coverId] blank or unknown falls back to the username's stable pick. [live] settles it once when picked. */
@Composable
fun M3xCover(coverId: String?, username: String, modifier: Modifier = Modifier, live: Boolean = false) {
    val context = LocalContext.current
    val initial = remember(coverId, username) {
        if (M3xArt.isLoaded) M3xArt.resolveCover(context, coverId, username) else null
    }
    val cover by produceState(initial, coverId, username) {
        if (value == null) value = withContext(Dispatchers.IO) { M3xArt.resolveCover(context, coverId, username) }
    }
    val c = cover
    if (c != null) M3xArtView(c.layout, c.palette, M3xArt.coverKey(c), 400.0, modifier, live)
    else androidx.compose.foundation.layout.Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

/** A resource's thumbnail: the art library v3 `thumb` piece (study object on a calm backdrop), built by code for its box (v3art/V3Thumb.kt). */
@Composable
fun M3xThumb(seed: String, subject: String, modifier: Modifier = Modifier) {
    com.neb.ians.ui.components.v3art.V3Thumb(seed = seed, subject = subject, modifier = modifier)
}

/** One cover tile in the picker, drawn at its own size. */
@Composable
fun M3xCoverTile(id: String, modifier: Modifier = Modifier) = M3xCover(id, id, modifier)

/** A note cover from the library (cover index 41 and up), 720 x 320 like the site's `note` kind. Drawn by code; [live] settles it once when it appears or changes. */
@Composable
fun M3xNoteCover(position: Int, modifier: Modifier = Modifier, live: Boolean = false) {
    val context = LocalContext.current
    val initial = remember(position) {
        if (M3xArt.isNotesLoaded) M3xArt.noteRecipe(context, position) else null
    }
    val recipe by produceState(initial, position) {
        if (value == null) value = withContext(Dispatchers.IO) { M3xArt.noteRecipe(context, position) }
    }
    val r = recipe
    if (r != null) M3xArtView(r.layout, r.palette, r.key, 320.0, modifier, live)
    else androidx.compose.foundation.layout.Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
}
