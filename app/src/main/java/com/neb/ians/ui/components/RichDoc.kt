package com.neb.ians.ui.components

/*
 * The document a composer edits.
 *
 * The text in the field is exactly what the keyboard sees: no markers, no hidden characters. Formatting lives next to it,
 * one bit mask per character (bold, italic, strike, code), one link id per character and one kind per line (heading,
 * quote, list, to-do, code block). Markdown exists only at the edges: [RichMarkdown.parse] reads it, [RichMarkdown.write]
 * produces it when a post is sent or saved. Nothing in the middle can leak a marker or revert a style.
 */

object Fmt {
    const val BOLD = 1
    const val ITALIC = 2
    const val STRIKE = 4
    const val CODE = 8
    const val UNDERLINE = 16
}

object RLine {
    const val NONE = 0
    const val H1 = 1
    const val H2 = 2
    const val H3 = 3
    const val QUOTE = 4
    const val BULLET = 5
    const val NUMBER = 6
    const val CHECK = 7
    const val CHECKED = 8
    const val CODE = 9
    const val RULE = 10

    fun isList(k: Int) = k == BULLET || k == NUMBER || k == CHECK || k == CHECKED || k == QUOTE
    fun isHeading(k: Int) = k in H1..H3

    /** The kind the next line gets when Enter is pressed on a line of kind [k]. */
    fun next(k: Int): Int = when (k) {
        BULLET -> BULLET
        NUMBER -> NUMBER
        CHECK, CHECKED -> CHECK
        QUOTE -> QUOTE
        CODE -> CODE
        else -> NONE
    }
}

/** One object-replacement character per inline picture; its key is the n-th entry of [RichDoc.keys]. */
const val IMG: Char = '\uFFFC'

class RichDoc(
    val text: String,
    val masks: IntArray,
    /** 0 = no link, k = urls[k - 1]. */
    val links: IntArray,
    val urls: List<String>,
    /** One per line (lines = newlines + 1). */
    val kinds: IntArray,
    /** One per [IMG] character, in order. */
    val keys: List<String>
) {
    val lineStarts: IntArray by lazy(LazyThreadSafetyMode.NONE) {
        val out = IntArray(kinds.size)
        var li = 1
        for (i in text.indices) if (text[i] == '\n' && li < out.size) out[li++] = i + 1
        out
    }

    fun lineOf(pos: Int): Int {
        val end = pos.coerceIn(0, text.length)
        var c = 0
        for (i in 0 until end) if (text[i] == '\n') c++
        return c.coerceAtMost(kinds.size - 1)
    }

    fun lineStart(line: Int): Int = lineStarts[line.coerceIn(0, kinds.size - 1)]

    fun lineEnd(line: Int): Int = if (line + 1 < kinds.size) lineStarts[line + 1] - 1 else text.length

    /** Mask a character typed at [p] with nothing selected would take: the one before it, never across a line or a picture. */
    fun inheritedMask(p: Int): Int =
        if (p > 0 && p <= text.length && text[p - 1] != '\n' && text[p - 1] != IMG) masks[p - 1] else 0

    fun inheritedLink(p: Int): Int =
        if (p > 0 && p < text.length && links[p - 1] != 0 && links[p - 1] == links[p]) links[p] else 0

    fun withKinds(k: IntArray) = RichDoc(text, masks, links, urls, k, keys)

    fun withMasks(m: IntArray) = RichDoc(text, m, links, urls, kinds, keys)

    /** Replace [p, oEnd) with [ins]; masks, links, kinds and picture keys follow the edit. */
    fun splice(p: Int, oEnd: Int, ins: String, insMask: Int, insLink: Int, insKeys: List<String> = emptyList()): RichDoc {
        val removed = text.substring(p, oEnd)
        var rn = 0
        var rImg = 0
        for (ch in removed) { if (ch == '\n') rn++ else if (ch == IMG) rImg++ }
        var inNl = 0
        for (ch in ins) if (ch == '\n') inNl++
        val li = lineOf(p)
        val firstKind = if (rn > 0 && p == lineStart(li) && inNl == 0) kinds[(li + rn).coerceAtMost(kinds.size - 1)] else kinds[li]
        val cont = RLine.next(firstKind)
        val nk = IntArray(kinds.size - rn + inNl)
        var w = 0
        for (i in 0 until li) nk[w++] = kinds[i]
        nk[w++] = firstKind
        repeat(inNl) { nk[w++] = cont }
        for (i in li + rn + 1 until kinds.size) nk[w++] = kinds[i]

        val nt = text.substring(0, p) + ins + text.substring(oEnd)
        val nm = IntArray(nt.length)
        val nl = IntArray(nt.length)
        System.arraycopy(masks, 0, nm, 0, p)
        System.arraycopy(links, 0, nl, 0, p)
        for (j in ins.indices) {
            val c = ins[j]
            if (c != '\n' && c != IMG) { nm[p + j] = insMask; nl[p + j] = insLink }
        }
        System.arraycopy(masks, oEnd, nm, p + ins.length, text.length - oEnd)
        System.arraycopy(links, oEnd, nl, p + ins.length, text.length - oEnd)

        var before = 0
        for (i in 0 until p) if (text[i] == IMG) before++
        val nKeys = ArrayList<String>(keys.size - rImg + insKeys.size)
        for (i in 0 until before.coerceAtMost(keys.size)) nKeys.add(keys[i])
        nKeys.addAll(insKeys)
        for (i in (before + rImg) until keys.size) nKeys.add(keys[i])
        return RichDoc(nt, nm, nl, urls, nk, nKeys)
    }

    companion object {
        val Empty = RichDoc("", IntArray(0), IntArray(0), emptyList(), intArrayOf(RLine.NONE), emptyList())
    }
}
