package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

/** Everything a note holds besides words, drawn small: pictures, recordings, a drawing, a table, code. */
@Composable
internal fun NoteMediaPreview(media: NoteMedia, compact: Boolean, ink: Color, container: Color) {
    val tile = ink.copy(alpha = 0.08f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (media.pics.isNotEmpty()) PicturesPreview(media.pics, compact, tile)
        if (media.drawing.isNotEmpty()) DrawingPreview(media, compact, ink, container, tile)
        if (media.audio.isNotEmpty()) AudioPreview(media.audio, ink, tile)
        if (media.table.isNotEmpty()) TablePreview(media.table, ink)
        if (media.code.isNotEmpty()) Text(
            media.code, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 14.sp),
            color = ink.copy(alpha = 0.86f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(tile).padding(horizontal = 8.dp, vertical = 5.dp),
        )
        if (media.hasDivider) {
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).clip(RoundedCornerShape(1.dp)).background(ink.copy(alpha = 0.18f)))
        }
    }
}

@Composable
private fun Thumb(path: String, modifier: Modifier, tile: Color) {
    val ctx = LocalContext.current
    AsyncImage(
        model = remember(path) { ImageRequest.Builder(ctx).data(File(path)).size(320).crossfade(false).build() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(tile),
    )
}

@Composable
private fun PicturesPreview(pics: List<String>, compact: Boolean, tile: Color) {
    val h = if (compact) 64.dp else 88.dp
    when {
        pics.size == 1 -> Thumb(pics[0], Modifier.fillMaxWidth().height(h), tile)
        pics.size == 2 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Thumb(pics[0], Modifier.weight(1f).height(h * 0.85f), tile)
            Thumb(pics[1], Modifier.weight(1f).height(h * 0.85f), tile)
        }
        else -> Row(Modifier.height(h), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Thumb(pics[0], Modifier.weight(1.5f).height(h), tile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Thumb(pics[1], Modifier.weight(1f).fillMaxWidth(), tile)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Thumb(pics[2], Modifier.matchParentSize(), tile)
                    if (pics.size > 3) Box(
                        Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+${pics.size - 3}", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun AudioPreview(audio: List<Int>, ink: Color, tile: Color) {
    val seconds = audio[0]
    val bars = remember(seconds, audio.size) { FloatArray(22) { i -> (0.35f + 0.65f * (sin(i * 1.7f + seconds * 0.9f) * 0.5f + 0.5f)) } }
    Row(
        Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(12.dp)).background(tile).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PlayArrow, null, tint = ink, modifier = Modifier.size(14.dp))
        }
        Canvas(Modifier.weight(1f).height(14.dp)) {
            val gap = 2.dp.toPx()
            val w = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
            bars.forEachIndexed { i, v ->
                val bh = size.height * v
                drawRoundRect(ink.copy(alpha = 0.55f), Offset(i * (w + gap), (size.height - bh) / 2f), Size(w, bh), CornerRadius(w / 2f))
            }
        }
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60) + if (audio.size > 1) " +${audio.size - 1}" else "",
            style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.8f), maxLines = 1,
        )
    }
}

@Composable
private fun DrawingPreview(media: NoteMedia, compact: Boolean, ink: Color, container: Color, tile: Color) {
    val strokePath = remember { Path() }
    val containerLum = container.luminance()
    Canvas(Modifier.fillMaxWidth().height(if (compact) 52.dp else 68.dp).clip(RoundedCornerShape(12.dp)).background(tile)) {
        val pad = 8.dp.toPx()
        val aw = size.width - pad * 2
        val ah = size.height - pad * 2
        val a = media.drawAspect
        val dw = if (aw / ah > a) ah * a else aw
        val dh = dw / a
        val ox = (size.width - dw) / 2f
        val oy = (size.height - dh) / 2f
        val strokeStyle = DrawStroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        media.drawing.forEach { st ->
            val pts = st.points
            if (pts.size < 2) return@forEach
            strokePath.rewind()
            strokePath.moveTo(ox + pts[0] * dw, oy + pts[1] * dh)
            if (pts.size == 2) strokePath.lineTo(ox + pts[0] * dw + 0.1f, oy + pts[1] * dh)
            var i = 2
            while (i + 1 < pts.size) {
                strokePath.quadraticBezierTo(
                    ox + pts[i - 2] * dw, oy + pts[i - 1] * dh,
                    ox + (pts[i - 2] + pts[i]) / 2f * dw, oy + (pts[i - 1] + pts[i + 1]) / 2f * dh,
                )
                i += 2
            }
            val raw = Color(st.color)
            // a pen that would vanish on this note colour is drawn in the note's ink
            val col = if (abs(raw.luminance() - containerLum) < 0.28f) ink else raw
            drawPath(strokePath, col, style = strokeStyle)
        }
    }
}

@Composable
private fun TablePreview(rows: List<List<String>>, ink: Color) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, ink.copy(alpha = 0.18f), shape)) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().background(if (r == 0) ink.copy(alpha = 0.08f) else Color.Transparent)) {
                for (col in 0 until (rows.maxOfOrNull { it.size } ?: 0)) {
                    Text(
                        row.getOrElse(col) { "" }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = ink.copy(alpha = if (r == 0) 0.95f else 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).height(20.dp).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
