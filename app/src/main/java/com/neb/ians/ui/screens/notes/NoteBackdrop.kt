package com.neb.ians.ui.screens.notes

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal object NoteBackdropCache {
    private val cache = android.util.LruCache<String, ImageBitmap>(8)
    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, bmp: ImageBitmap) { synchronized(cache) { cache.put(key, bmp) } }
    fun trim() { synchronized(cache) { cache.evictAll() } }
}

/** The page picture, decoded once off the main thread (null while loading or when the note has none). */
@Composable
fun rememberBackdrop(file: File?): State<ImageBitmap?> {
    val cacheKey = file?.path
    val cached = if (cacheKey != null) NoteBackdropCache.get(cacheKey) else null
    return produceState<ImageBitmap?>(cached, cacheKey) {
        if (file == null) {
            value = null
            return@produceState
        }
        NoteBackdropCache.get(file.path)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                while (longest / sample > 2048) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()?.also {
                    NoteBackdropCache.put(file.path, it)
                }
            }.getOrNull()
        }
    }
}

/**
 * A picture fixed behind the scrolling page, cropped to fill, with a flat wash of the page colour on top so text stays
 * readable. Put it before `verticalScroll` so it does not move with the blocks. Drawn in the draw phase only.
 */
fun Modifier.noteBackdrop(image: () -> ImageBitmap?, wash: Color): Modifier = drawBehind {
    val bmp = image() ?: return@drawBehind
    val scale = max(size.width / bmp.width, size.height / bmp.height)
    val dw = bmp.width * scale
    val dh = bmp.height * scale
    clipRect {
        drawImage(
            bmp,
            srcSize = IntSize(bmp.width, bmp.height),
            dstOffset = IntOffset(((size.width - dw) / 2f).roundToInt(), ((size.height - dh) / 2f).roundToInt()),
            dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        )
        drawRect(wash)
    }
}
