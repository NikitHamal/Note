package com.neb.ians.ui.components.v3art

import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

internal object V3Cache {
    private val lru = LruCache<String, V3Art>(160)
    private val bySeed = LruCache<String, V3Art>(160)

    fun trim(empty: Boolean = false) {
        synchronized(this) {
            if (empty) {
                lru.evictAll()
                bySeed.evictAll()
            } else {
                lru.trimToSize(40)
                bySeed.trimToSize(40)
            }
        }
    }

    fun peek(seed: String, subject: String, fit: Float, dark: Boolean): V3Art? {
        val seedKey = "$seed|$subject|$dark"
        return synchronized(this) {
            if (fit > 0f) lru.get("$seed|$subject|$fit|$dark") ?: bySeed.get(seedKey)
            else bySeed.get(seedKey)
        }
    }

    fun get(seed: String, subject: String, fit: Float, dark: Boolean): V3Art {
        val key = "$seed|$subject|$fit|$dark"
        val seedKey = "$seed|$subject|$dark"
        synchronized(this) { lru.get(key) }?.let { return it }
        val spec = V3Still.pick(seed, subject)
        val (body, sky) = V3Still.make(spec, fit, dark)
        val art = V3Svg.parse(body, Math.round(256 * fit).toFloat(), 256f, sky)
        synchronized(this) {
            lru.put(key, art)
            bySeed.put(seedKey, art)
        }
        return art
    }

    
}

private fun DrawScope.drawV3(art: V3Art) {
    val sc = max(size.width / art.w, size.height / art.h)
    clipRect {
        drawRect(art.sky)
        withTransform({
            translate(size.width / 2f, size.height / 2f)
            scale(sc, sc, Offset.Zero)
            translate(-art.w / 2f, -art.h / 2f)
        }) {
            for (op in art.ops) {
                val m = op.matrix
                if (m == null) drawOp(op) else withTransform({ transform(m) }) { drawOp(op) }
            }
        }
    }
}

private fun DrawScope.drawOp(op: V3Op) {
    op.fill?.let { drawPath(op.path, it) }
    val strokeColor = op.stroke
    val strokeStyle = op.strokeStyle
    if (strokeColor != null && strokeStyle != null) {
        drawPath(op.path, strokeColor, style = strokeStyle)
    }
}

/**
 * A resource's thumbnail: the art library v3 `thumb` piece (a study object on a calm backdrop), built by code for the box it sits in.
 * Same pick and same drawing as js/v3-thumb.js on the site. [seed] is "resourceId|type"; the subject chooses the objects.
 * Nothing is bundled and nothing is downloaded; scenes (sun, moon, hills, trees, buildings) are never drawn.
 */
@Composable
fun V3Thumb(seed: String, subject: String, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    var box by remember { mutableStateOf(IntSize.Zero) }
    val fit = if (box.width > 0 && box.height > 0) V3Still.fit(box.width.toFloat() / box.height.toFloat()) else 0f
    val cached = remember(seed, subject, fit, dark) { V3Cache.peek(seed, subject, fit, dark) }
    val art by produceState(cached, seed, subject, fit, dark) {
        if (fit <= 0f || value != null) return@produceState
        value = withContext(Dispatchers.Default) { V3Cache.get(seed, subject, fit, dark) }
    }
    Canvas(modifier.onSizeChanged { if (box != it) box = it }.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        art?.let { drawV3(it) }
    }
}
