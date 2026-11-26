package com.neb.ians.ui.screens.notes

import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.neb.ians.data.local.entity.NoteEntity

/**
 * What a note holds besides words, read once from its blocks so the list can show it: pictures, recordings, a drawing,
 * a table and a code snippet. Reading the JSON is off the main thread and cached by note id and edit time.
 */
@Immutable
internal class NoteMedia(
    val pics: List<String>,
    val audio: List<Int>,
    val drawing: List<Stroke>,
    val drawAspect: Float,
    val table: List<List<String>>,
    val code: String,
    val hasDivider: Boolean = false,
    val nextDue: Long = 0L,
    val todoDone: Int = 0,
    val todoTotal: Int = 0,
) {
    val hasAny: Boolean get() = pics.isNotEmpty() || audio.isNotEmpty() || drawing.isNotEmpty() || table.isNotEmpty() || code.isNotEmpty() || hasDivider

    companion object {
        val None = NoteMedia(emptyList(), emptyList(), emptyList(), 1.6f, emptyList(), "", false, 0L, 0, 0)
    }
}

internal object NoteMediaCache {
    private val cache = LruCache<String, NoteMedia>(128)

    fun key(n: NoteEntity): String = n.id + ":" + n.updatedAt

    fun peek(key: String): NoteMedia? = cache.get(key)

    fun of(n: NoteEntity): NoteMedia {
        val key = key(n)
        cache.get(key)?.let { return it }
        val d = n.doc
        val (done, total) = NoteFormatting.checkProgress(n.body)
        val has = d.isNotEmpty() && (
            d.contains("\"Image\"") || d.contains("\"Audio\"") || d.contains("\"Drawing\"") ||
                d.contains("\"Table\"") || d.contains("\"Code\"") || d.contains("\"Divider\"") || (d.contains("\"Todo\"") && d.contains("\"d\""))
            )
        val base = if (!has) NoteMedia.None else runCatching { read(d) }.getOrDefault(NoteMedia.None)
        val out = if (done == 0 && total == 0 && base === NoteMedia.None) NoteMedia.None
        else NoteMedia(base.pics, base.audio, base.drawing, base.drawAspect, base.table, base.code, base.hasDivider, base.nextDue, done, total)
        cache.put(key, out)
        return out
    }

    private fun read(json: String): NoteMedia {
        val blocks = NoteDoc.fromJson(json)
        val pics = blocks.filter { it.type == BlockType.Image && it.path.isNotBlank() }.map { it.path }
        val audio = blocks.filter { it.type == BlockType.Audio && it.path.isNotBlank() }.map { it.seconds }
        val draw = blocks.firstOrNull { it.type == BlockType.Drawing && it.strokes.isNotEmpty() }
        val strokes = draw?.strokes?.take(120)?.map { s ->
            val step = if (s.points.size > 160) 4 else if (s.points.size > 80) 2 else 1
            if (step == 1) s else Stroke(s.color, s.width, s.points.chunked(2).filterIndexed { i, _ -> i % step == 0 || i == s.points.size / 2 - 1 }.flatten())
        } ?: emptyList()
        val aspect = (340f / (draw?.height ?: 260f).coerceAtLeast(120f)).coerceIn(1.2f, 2.6f)
        val table = blocks.firstOrNull { it.type == BlockType.Table && !it.isBlank }?.rows
            ?.take(2)?.map { r -> r.take(3).map { it.trim() } } ?: emptyList()
        val code = blocks.firstOrNull { it.type == BlockType.Code && it.text.isNotBlank() }
            ?.text?.lineSequence()?.filter { it.isNotBlank() }?.take(2)?.joinToString("\n") ?: ""
        val hasDivider = blocks.any { it.type == BlockType.Divider } && pics.isEmpty() && strokes.isEmpty() && table.isEmpty()
        val nextDue = blocks.filter { it.type == BlockType.Todo && !it.checked && it.due > 0L }.minOfOrNull { it.due } ?: 0L
        return NoteMedia(pics, audio, strokes, aspect, table, code, hasDivider, nextDue)
    }
}
