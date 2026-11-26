@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.data.local.entity.NoteEntity
import com.neb.ians.ui.components.InlineImageTokens
import com.neb.ians.ui.components.LiveColors
import com.neb.ians.ui.components.LiveMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private sealed interface PreviewLine {
    data class Plain(val text: AnnotatedString, val heading: Boolean = false) : PreviewLine
    data class Check(val text: AnnotatedString, val done: Boolean) : PreviewLine
    data class Quote(val text: AnnotatedString) : PreviewLine
    data class Callout(val text: AnnotatedString) : PreviewLine
    data class Toggle(val text: AnnotatedString, val open: Boolean) : PreviewLine
}

private val previewCheck = Regex("^\\s*[-*+]\\s\\[([ xX])]\\s?(.*)$")
private val previewSkip = setOf("[image]", "[audio recording]", "[drawing]")
private val previewRule = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")
private val previewLinesCache = android.util.LruCache<String, List<PreviewLine>>(128)

private fun peekPreviewLines(noteId: String, updatedAt: Long, max: Int, inkKey: ULong): List<PreviewLine>? {
    val cacheKey = "$noteId|$updatedAt|$max|$inkKey"
    return synchronized(previewLinesCache) { previewLinesCache.get(cacheKey) }
}

private fun cachedPreviewLines(noteId: String, updatedAt: Long, doc: String, body: String, max: Int, inkKey: ULong, colors: LiveColors): List<PreviewLine> {
    if (body.isBlank() && doc.isBlank()) return emptyList()
    val cacheKey = "$noteId|$updatedAt|$max|$inkKey"
    synchronized(previewLinesCache) { previewLinesCache.get(cacheKey) }?.let { return it }
    return previewLines(doc, body, max, colors).also {
        synchronized(previewLinesCache) { previewLinesCache.put(cacheKey, it) }
    }
}

/** Extracts compact, rich preview lines for every block type in the note. */
private fun previewLines(doc: String, body: String, max: Int, colors: LiveColors): List<PreviewLine> {
    val out = ArrayList<PreviewLine>(max)
    if (doc.isNotBlank()) {
        val blocks = runCatching { NoteDoc.fromJson(doc) }.getOrDefault(emptyList())
        var num = 0
        for (b in blocks) {
            if (out.size >= max) break
            if (b.type == BlockType.Number) num++ else num = 0
            val rawText = InlineImageTokens.plainText(b.text, "").replace(LiveMarkdown.ZWSP, "").trim()
            if (rawText.isBlank()) continue
            val firstLine = rawText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (firstLine.isEmpty()) continue
            when (b.type) {
                BlockType.Todo -> {
                    val styled = LiveMarkdown.transform(firstLine, colors).styled
                    if (styled.isNotEmpty()) out += PreviewLine.Check(styled, b.checked)
                }
                BlockType.Toggle -> {
                    val styled = LiveMarkdown.transform(firstLine, colors).styled
                    if (styled.isNotEmpty()) out += PreviewLine.Toggle(styled, b.open)
                }
                BlockType.Quote -> {
                    val styled = LiveMarkdown.transform(firstLine, colors).styled
                    if (styled.isNotEmpty()) out += PreviewLine.Quote(styled)
                }
                BlockType.Callout -> {
                    val styled = LiveMarkdown.transform(firstLine, colors).styled
                    if (styled.isNotEmpty()) out += PreviewLine.Callout(styled)
                }
                BlockType.H1, BlockType.H2, BlockType.H3 -> {
                    val styled = LiveMarkdown.transform(firstLine, colors).styled
                    if (styled.isNotEmpty()) out += PreviewLine.Plain(styled, heading = true)
                }
                BlockType.Bullet -> {
                    val styled = LiveMarkdown.transform("\u2022 $firstLine", colors).styled
                    if (styled.isNotEmpty()) out += PreviewLine.Plain(styled)
                }
                BlockType.Number -> {
                    val styled = LiveMarkdown.transform("$num. $firstLine", colors).styled
                    if (styled.isNotEmpty()) out += PreviewLine.Plain(styled)
                }
                BlockType.Text -> {
                    for (ln in rawText.lineSequence()) {
                        if (out.size >= max) break
                        val trimmed = ln.trim()
                        if (trimmed.isEmpty() || previewRule.matches(trimmed) || trimmed in previewSkip) continue
                        val styled = LiveMarkdown.transform(trimmed, colors).styled
                        if (styled.isNotEmpty()) out += PreviewLine.Plain(styled)
                    }
                }
                else -> {}
            }
        }
        if (out.isNotEmpty()) return out
    }
    var inCode = false
    for (line in body.lineSequence().take(30)) {
        if (line.trimStart().startsWith("```")) { inCode = !inCode; continue }
        val raw = InlineImageTokens.plainText(line, "").replace(LiveMarkdown.ZWSP, "")
        if (inCode || raw.isBlank() || previewRule.matches(raw) || raw.trim() in previewSkip) continue
        val chk = previewCheck.find(raw)
        if (chk != null) {
            val text = LiveMarkdown.transform(chk.groupValues[2].trim(), colors).styled
            if (text.isNotEmpty()) out += PreviewLine.Check(text, chk.groupValues[1].lowercase() == "x")
        } else if (raw.trimStart().startsWith("> ")) {
            val text = LiveMarkdown.transform(raw.trimStart().removePrefix("> ").trim(), colors).styled
            if (text.isNotEmpty()) out += PreviewLine.Quote(text)
        } else {
            val isHeading = raw.trimStart().startsWith("#")
            val clean = raw.trim().replaceFirst(Regex("^#{1,3}\\s+"), "")
            val text = LiveMarkdown.transform(clean, colors).styled
            if (text.isNotEmpty()) out += PreviewLine.Plain(text, heading = isHeading)
        }
        if (out.size >= max) break
    }
    return out
}

private val noteDateCal = object : ThreadLocal< Pair<Calendar, Calendar> >() {
    override fun initialValue(): Pair<Calendar, Calendar> = Calendar.getInstance() to Calendar.getInstance()
}

private fun dateLabel(ms: Long): String {
    val (now, then) = noteDateCal.get()!!
    val nowMs = System.currentTimeMillis()
    now.timeInMillis = nowMs
    then.timeInMillis = ms
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val days = (nowMs - ms) / 86_400_000L
    val pattern = when {
        sameDay -> "h:mm a"
        days < 7 -> "EEE"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> "MMM d"
        else -> "MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(ms)
}

/**
 * One note in the list. Short on purpose: a title, a few lines, one small preview of what the note holds (pictures,
 * recording, drawing, table, code) and one footer line, so the staggered columns stay dense and differ in height only
 * by what the notes really contain.
 */
@Composable
internal fun NoteCard(
    note: NoteEntity,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val tone = noteTone(note.tone)
    val ink = tone.content
    val shape = RoundedCornerShape(22.dp)
    val live = remember(tone) { LiveColors(ink.copy(alpha = 0.12f), ink, ink, ink.copy(alpha = 0.75f), ink.copy(alpha = 0.6f)) }
    val key = remember(note.id, note.updatedAt) { NoteMediaCache.key(note) }
    val media by produceState(NoteMediaCache.peek(key), key) {
        value = NoteMediaCache.peek(key) ?: withContext(Dispatchers.Default) { NoteMediaCache.of(note) }
    }
    val m = media ?: NoteMedia.None
    val hasMedia = m.hasAny
    val maxEntries = if (hasMedia) 1 else 2
    val lines by produceState(
        initialValue = peekPreviewLines(note.id, note.updatedAt, maxEntries, ink.value) ?: emptyList(),
        key1 = note.id,
        key2 = note.updatedAt,
        key3 = maxEntries,
    ) {
        value = peekPreviewLines(note.id, note.updatedAt, maxEntries, ink.value)
            ?: withContext(Dispatchers.Default) {
                cachedPreviewLines(note.id, note.updatedAt, note.doc, note.body, maxEntries, ink.value, live)
            }
    }
    val done = m.todoDone
    val total = m.todoTotal
    val nextDue = m.nextDue
    val firstTag = remember(note.tags) { note.tagList.firstOrNull() }
    val showFooter = nextDue > 0L || firstTag != null || total > 0
    val cardBg = if (note.tone == 0) noteSurfaceTileColor() else tone.container
    val border = when {
        selected -> BorderStroke(2.dp, c.primary)
        tone.hairline -> BorderStroke(1.dp, c.outlineVariant.copy(alpha = 0.55f))
        else -> null
    }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(cardBg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (note.cover != 0) {
            val ctx = LocalContext.current
            val photo = remember(note.id, note.coverPhoto) {
                if (note.cover < 0 && note.coverPhoto.isNotEmpty()) File(ctx.filesDir, "notes/${note.id}/${note.coverPhoto}") else null
            }
            NoteCover(note.cover, photo, Modifier.fillMaxWidth().height(if (compact) 30.dp else 40.dp), animate = false, light = true)
        }
        val pad = if (compact) 10.dp else 12.dp
        Column(
            Modifier.fillMaxWidth().padding(horizontal = pad, vertical = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val hasTitle = note.title.isNotBlank()
            if (hasTitle || note.icon.isNotEmpty() || selected || note.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.icon.isNotEmpty()) NoteIconTile(note.icon, if (compact) 20.dp else 22.dp, container = ink.copy(alpha = 0.12f), content = ink)
                    if (hasTitle) Text(
                        note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    ) else Box(Modifier.weight(1f))
                    if (selected) Icon(Icons.Outlined.Check, null, tint = readableOn(c.primary, cardBg, 3f), modifier = Modifier.size(16.dp))
                    else if (note.pinned) Icon(Icons.Outlined.PushPin, "Pinned", tint = readableOn(ink, cardBg, 3f), modifier = Modifier.size(14.dp))
                }
            }
            lines.forEachIndexed { i, line ->
                val cap = if (i == 0 && !hasMedia) 2 else 1
                when (line) {
                    is PreviewLine.Plain -> Text(
                        line.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 16.sp,
                            fontWeight = if (line.heading) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = ink.copy(alpha = if (line.heading) 0.94f else 0.82f),
                        maxLines = cap, overflow = TextOverflow.Ellipsis,
                    )
                    is PreviewLine.Check -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.padding(top = 2.dp).size(12.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (line.done) c.primary else ink.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { if (line.done) Icon(Icons.Outlined.Check, null, tint = c.onPrimary, modifier = Modifier.size(9.dp)) }
                        Text(
                            line.text, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            color = ink.copy(alpha = if (line.done) 0.55f else 0.84f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    is PreviewLine.Toggle -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (line.open) "\u25BE" else "\u25B8", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f), maxLines = 1)
                        Text(
                            line.text, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            color = ink.copy(alpha = 0.84f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                    }
                    is PreviewLine.Quote -> Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(width = 2.5.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
                        Text(
                            line.text, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            color = ink.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                    }
                    is PreviewLine.Callout -> Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            line.text, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                            fontWeight = FontWeight.Medium, color = ink.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (hasMedia) NoteMediaPreview(m, compact, ink, cardBg)
            if (showFooter) {
                Row(
                    Modifier.padding(top = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (nextDue > 0L) {
                        val dueText = remember(nextDue) { NoteDue.label(nextDue) }
                        val isOverdue = remember(nextDue) { NoteDue.overdue(nextDue) }
                        NoteChipLabel(dueText, if (isOverdue) c.error else ink, ink)
                    } else if (firstTag != null) {
                        NoteChipLabel("#$firstTag", ink, ink)
                    }
                    Box(Modifier.weight(1f))
                    if (total > 0) Text("$done/$total", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.6f), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun NoteChipLabel(text: String, color: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.10f)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
