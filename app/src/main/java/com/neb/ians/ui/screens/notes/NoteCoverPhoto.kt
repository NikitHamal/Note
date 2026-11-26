package com.neb.ians.ui.screens.notes

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val noteCoverBitmapCache = android.util.LruCache<String, ImageBitmap>(24)

internal fun trimNoteCoverBitmapCache() {
    synchronized(noteCoverBitmapCache) { noteCoverBitmapCache.evictAll() }
}

/** A gallery cover. Decoded off the main thread with bounded dimensions and LRU caching. */
@Composable
fun NoteCoverPhoto(file: File, modifier: Modifier = Modifier, light: Boolean = false) {
    val cacheKey = "${file.path}:$light"
    val cached = androidx.compose.runtime.remember(cacheKey) { synchronized(noteCoverBitmapCache) { noteCoverBitmapCache.get(cacheKey) } }
    val bmp by produceState<ImageBitmap?>(cached, cacheKey) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val maxDim = if (light) 640 else 1280
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()?.also {
                    synchronized(noteCoverBitmapCache) { noteCoverBitmapCache.put(cacheKey, it) }
                }
            }.getOrNull()
        }
    }
    Box(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bmp?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}

/** Built-in banner or gallery picture, whichever the note uses. */
@Composable
fun NoteCover(cover: Int, photo: File?, modifier: Modifier = Modifier, animate: Boolean = true, light: Boolean = false) {
    if (cover < 0 && photo != null) NoteCoverPhoto(photo, modifier, light) else NoteCoverBanner(cover, modifier, animate)
}
