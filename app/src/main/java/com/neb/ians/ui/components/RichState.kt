package com.neb.ians.ui.components

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The editor behind every composer. [value] is the plain text the keyboard edits, untouched by formatting, so there is no
 * frame in which a marker shows or a style reverts. [onChange] keeps the formatting attached to the characters that move
 * (typing, deleting, pasting, autocorrect); the toolbar calls the toggle functions. Markdown is made only by [markdown].
 */
@Stable
class RichState(
    private val maxChars: Int = Int.MAX_VALUE,
    /** One block of a note: line marks (lists, headings) belong to the block type, so none are made or read here. */
    private val blockMode: Boolean = false,
    /** Text with no formatting at all (a code block): the Markdown is the text itself. */
    private val raw: Boolean = false
) {
    var value by mutableStateOf(TextFieldValue(""))
        private set
    var doc by mutableStateOf(RichDoc.Empty)
        private set

    /** Style chosen with nothing selected (bold tapped, next letters are bold); -1 when the caret decides. */
    var armed by mutableIntStateOf(-1)
        private set
    private var armedAt = -1

    /** Pictures picked but not uploaded yet, by their key. They go up when the post is sent. */
    val uris = mutableStateMapOf<String, Uri>()

    var onEdited: ((RichState) -> Unit)? = null

    /** The Markdown last written or loaded; hosts compare it to what they hold to know an outside change from an echo. */
    var lastMarkdown: String = ""
        private set

    /** Where the caret sits in [lastMarkdown]. */
    var lastCaret: Int = 0
        private set

    fun markdown(): String = lastMarkdown

    val isBlank: Boolean get() = doc.text.isBlank()

    /** Selects the entire plain text in this block. */
    fun selectAll() {
        val len = doc.text.length
        if (len <= 0) return
        value = value.copy(selection = TextRange(0, len))
    }

    /** Load Markdown from outside (a draft, an edit, a mention picked); [caret] is an offset in [md]. */
    fun setMarkdown(md: String, caret: Int = md.length) {
        val (d, at) = if (raw) RichMarkdown.plainDoc(md) to caret.coerceIn(0, md.length) else RichMarkdown.parseWithCaret(md, caret, blockMode)
        doc = d
        value = TextFieldValue(d.text, TextRange(at))
        armed = -1
        lastMarkdown = md
        lastCaret = caret.coerceIn(0, md.length)
    }

    private val recent = ArrayList<Pair<String, Long>>()

    /** Take Markdown handed in from outside unless it is just an echo of what this editor sent a moment ago. */
    fun adopt(md: String, caret: Int) {
        if (md == lastMarkdown) return
        val now = android.os.SystemClock.uptimeMillis()
        if (recent.any { it.first == md && now - it.second < 250L }) return
        setMarkdown(md, caret)
    }

    private fun publish() {
        if (raw) {
            lastMarkdown = doc.text
            lastCaret = value.selection.end.coerceIn(0, doc.text.length)
            return
        }
        val (md, c) = RichMarkdown.writeWithCaret(doc, value.selection.end.coerceIn(0, doc.text.length))
        lastMarkdown = md
        lastCaret = c
        recent.add(md to android.os.SystemClock.uptimeMillis())
        if (recent.size > 8) recent.removeAt(0)
    }

    private fun commit(d: RichDoc, v: TextFieldValue) {
        doc = d
        value = v
        publish()
        onEdited?.invoke(this)
    }

    // ------------------------------------------------------------------ typing

    fun onChange(nv: TextFieldValue) {
        val o = doc.text
        val n = nv.text
        if (o == n) {
            val moved = nv.selection != value.selection
            if (moved && !(armed >= 0 && nv.selection.collapsed && nv.selection.start == armedAt)) armed = -1
            value = nv
            if (moved) { publish(); onEdited?.invoke(this) }
            return
        }
        if (n.length > maxChars && n.length > o.length) return
        val min = minOf(o.length, n.length)
        var p = 0
        while (p < min && o[p] == n[p]) p++
        var sfx = 0
        while (sfx < min - p && o[o.length - 1 - sfx] == n[n.length - 1 - sfx]) sfx++
        val oEnd = o.length - sfx
        val nEnd = n.length - sfx
        val ins = n.substring(p, nEnd).replace(IMG.toString(), "")
        val d = doc

        if (blockMode || raw) {
            // no line marks to make or unmake
        } else if (oEnd == p) {
            if (ins == "\n") {
                // Enter on an empty list item ends the list
                val li = d.lineOf(p)
                if (RLine.isList(d.kinds[li]) && d.lineStart(li) == d.lineEnd(li)) {
                    val k = d.kinds.copyOf(); k[li] = RLine.NONE
                    commit(d.withKinds(k), TextFieldValue(o, TextRange(p)))
                    return
                }
            }
            if (ins == " ") {
                val li = d.lineOf(p)
                val ls = d.lineStart(li)
                if (d.kinds[li] == RLine.NONE && p - ls in 1..4) {
                    val kind = shortcut(o.substring(ls, p))
                    if (kind != RLine.NONE) {
                        val cut = d.splice(ls, p, "", 0, 0)
                        val k = cut.kinds.copyOf(); k[li] = kind
                        commit(cut.withKinds(k), TextFieldValue(cut.text, TextRange(ls)))
                        return
                    }
                }
            }
        } else if (ins.isEmpty() && oEnd == p + 1 && o[p] == '\n') {
            // Backspace at the start of a list, quote or heading line removes the mark first, then joins the lines
            val li = d.lineOf(p) + 1
            if (li < d.kinds.size && d.kinds[li] != RLine.NONE) {
                val k = d.kinds.copyOf(); k[li] = RLine.NONE
                commit(d.withKinds(k), TextFieldValue(o, TextRange(p + 1)))
                return
            }
        }

        val mask = if (raw) 0 else when {
            oEnd > p -> if (o[p] != '\n' && o[p] != IMG) d.masks[p] else d.inheritedMask(p)
            armed >= 0 && p == armedAt -> armed
            else -> d.inheritedMask(p)
        }
        val link = if (oEnd > p) 0 else d.inheritedLink(p)
        val next = d.splice(p, oEnd, ins, mask, link)
        armed = -1
        val sameAsKeyboard = next.text == n
        commit(next, if (sameAsKeyboard) nv else TextFieldValue(next.text, TextRange((p + ins.length).coerceIn(0, next.text.length))))
    }

    /** Apply an edit without telling the host (used when the host is about to rewrite the text itself). */
    fun onChangeQuiet(nv: TextFieldValue) {
        val cb = onEdited
        onEdited = null
        try { onChange(nv) } finally { onEdited = cb }
    }

    private fun shortcut(prefix: String): Int = when {
        prefix == "-" || prefix == "*" || prefix == "+" || prefix == "\u2022" -> RLine.BULLET
        prefix == "#" -> RLine.H1
        prefix == "##" -> RLine.H2
        prefix == "###" -> RLine.H3
        prefix == ">" -> RLine.QUOTE
        prefix == "[]" || prefix == "[ ]" -> RLine.CHECK
        prefix == "[x]" || prefix == "[X]" -> RLine.CHECKED
        prefix.length >= 2 && (prefix.last() == '.' || prefix.last() == ')') && prefix.dropLast(1).all { it.isDigit() } -> RLine.NUMBER
        else -> RLine.NONE
    }

    /** Backspace with the caret at the start of a formatted line (the keyboard sends nothing at the very start of the text). */
    fun backspaceAtLineStart(): Boolean {
        val sel = value.selection
        if (!sel.collapsed) return false
        val d = doc
        val li = d.lineOf(sel.start)
        if (sel.start != d.lineStart(li) || d.kinds[li] == RLine.NONE) return false
        val k = d.kinds.copyOf(); k[li] = RLine.NONE
        commit(d.withKinds(k), value)
        return true
    }

    // ------------------------------------------------------------------ toolbar

    fun isActive(flag: Int): Boolean {
        val d = doc
        val t = d.text
        val sel = value.selection
        val s = sel.min.coerceIn(0, t.length)
        val e = sel.max.coerceIn(0, t.length)
        if (s == e) return (if (armed >= 0) armed else d.inheritedMask(s)) and flag != 0
        var any = false
        for (i in s until e) {
            val c = t[i]
            if (c == '\n' || c == IMG) continue
            any = true
            if (d.masks[i] and flag == 0) return false
        }
        return any
    }

    fun toggleInline(flag: Int) {
        val d = doc
        val t = d.text
        val sel = value.selection
        var s = sel.min.coerceIn(0, t.length)
        var e = sel.max.coerceIn(0, t.length)
        if (s == e) {
            if (s > 0 && s < t.length && t[s - 1].isLetterOrDigit() && t[s].isLetterOrDigit()) {
                while (s > 0 && t[s - 1].isLetterOrDigit()) s--
                while (e < t.length && t[e].isLetterOrDigit()) e++
                applyMask(s, e, flag)
                return
            }
            armed = (if (armed >= 0) armed else d.inheritedMask(s)) xor flag
            armedAt = s
            return
        }
        applyMask(s, e, flag)
    }

    private fun applyMask(s: Int, e: Int, flag: Int) {
        val d = doc
        val t = d.text
        var all = true
        var any = false
        for (i in s until e) {
            val c = t[i]
            if (c == '\n' || c == IMG) continue
            any = true
            if (d.masks[i] and flag == 0) { all = false; break }
        }
        if (!any) return
        val m = d.masks.copyOf()
        for (i in s until e) {
            val c = t[i]
            if (c == '\n' || c == IMG) continue
            m[i] = if (all) m[i] and flag.inv() else m[i] or flag
        }
        commit(d.withMasks(m), value)
    }

    private fun same(a: Int, kind: Int) = a == kind || (kind == RLine.CHECK && a == RLine.CHECKED) || (kind == RLine.H2 && RLine.isHeading(a))

    fun isLineActive(kind: Int): Boolean {
        val d = doc
        return same(d.kinds[d.lineOf(value.selection.min)], kind)
    }

    fun toggleLine(kind: Int) {
        val d = doc
        val sel = value.selection
        val l0 = d.lineOf(sel.min)
        val l1 = d.lineOf(sel.max)
        var all = true
        for (i in l0..l1) if (!same(d.kinds[i], kind)) all = false
        val k = d.kinds.copyOf()
        for (i in l0..l1) k[i] = if (all) RLine.NONE else kind
        commit(d.withKinds(k), value)
    }

    fun insertDivider() {
        val sel = value.selection
        val s = sel.min.coerceIn(0, doc.text.length)
        val e = sel.max.coerceIn(0, doc.text.length)
        val li = doc.lineOf(s)
        val d = doc.splice(s, e, "\n\n", 0, 0)
        val k = d.kinds.copyOf()
        k[li + 1] = RLine.RULE
        k[li + 2] = RLine.NONE
        commit(d.withKinds(k), TextFieldValue(d.text, TextRange(d.lineStart(li + 2))))
    }

    fun insertLink(url: String) {
        val clean = url.trim().let { if (it.isNotEmpty() && !it.contains("://") && !it.startsWith("mailto:")) "https://$it" else it }
        if (clean.isEmpty()) return
        val sel = value.selection
        var s = sel.min.coerceIn(0, doc.text.length)
        var e = sel.max.coerceIn(0, doc.text.length)
        var d = doc
        if (s == e) {
            val label = clean.removePrefix("https://").removePrefix("http://")
            d = d.splice(s, e, label, d.inheritedMask(s), 0)
            e = s + label.length
        }
        val urls = d.urls + clean
        val id = urls.size
        val nl = d.links.copyOf()
        for (i in s until e) if (d.text[i] != '\n' && d.text[i] != IMG) nl[i] = id
        commit(RichDoc(d.text, d.masks, nl, urls, d.kinds, d.keys), TextFieldValue(d.text, TextRange(e)))
    }

    fun insertSymbol(symbol: String) {
        val t = value.text
        val s = value.selection.min.coerceIn(0, t.length)
        val e = value.selection.max.coerceIn(0, t.length)
        onChange(TextFieldValue(t.replaceRange(s, e, symbol), TextRange(s + symbol.length)))
    }

    // ------------------------------------------------------------------ pictures

    fun imagePositions(): IntArray {
        val t = doc.text
        val out = IntArray(doc.keys.size)
        var n = 0
        for (i in t.indices) if (t[i] == IMG && n < out.size) out[n++] = i
        return out
    }

    fun insertImage(key: String, uri: Uri?) {
        if (uri != null) uris[key] = uri
        val t = doc.text
        val s = value.selection.min.coerceIn(0, t.length)
        val e = value.selection.max.coerceIn(0, t.length)
        val d = doc.splice(s, e, IMG.toString(), 0, 0, listOf(key))
        armed = -1
        commit(d, TextFieldValue(d.text, TextRange(s + 1)))
    }

    fun removeImage(index: Int) {
        val pos = imagePositions().getOrNull(index) ?: return
        val key = doc.keys[index]
        val d = doc.splice(pos, pos + 1, "", 0, 0)
        uris.remove(key)
        commit(d, TextFieldValue(d.text, TextRange(pos)))
    }

    /** Moves picture [index] so it sits at text offset [target] (an offset in the current text). */
    fun moveImage(index: Int, target: Int) {
        val from = imagePositions().getOrNull(index) ?: return
        if (target == from || target == from + 1) { moveCaret(from + 1); return }
        val key = doc.keys[index]
        val cut = doc.splice(from, from + 1, "", 0, 0)
        val at = (if (target > from) target - 1 else target).coerceIn(0, cut.text.length)
        val d = cut.splice(at, at, IMG.toString(), 0, 0, listOf(key))
        commit(d, TextFieldValue(d.text, TextRange(at + 1)))
    }

    /** Put the caret (nothing selected) at [at]; used while a picture is dragged so the caret shows where it lands. */
    fun moveCaret(at: Int) {
        val a = at.coerceIn(0, doc.text.length)
        if (value.selection.collapsed && value.selection.start == a) return
        value = value.copy(selection = TextRange(a))
        armed = -1
    }

    fun restoreSelection(start: Int, end: Int) {
        val len = doc.text.length
        value = value.copy(selection = TextRange(start.coerceIn(0, len), end.coerceIn(0, len)))
    }

    fun pendingKeys(): List<String> = doc.keys.filter { it.startsWith("u") }

    /** Address of every picture in the text, in order: a local file until it is uploaded, the server copy after. */
    fun imageUrls(): List<String> = doc.keys.map { k -> uris[k]?.toString() ?: (resolveMediaUrl("/media/content_images/$k.webp") ?: "") }
}
