package com.neb.ians.ui.components

/**
 * Markdown at the edges of the editor. [parse] turns stored text (a draft, a post being edited, anything the web wrote)
 * into a [RichDoc]; [write] turns the document back into the Markdown the server and every renderer already understand.
 * Writing is the only place markers are made, and it makes them from the formatting, so a pair is always well formed.
 */
object RichMarkdown {
    private const val ESCAPABLE = "\\`*_~|[]()#>-+.!$"
    private const val ZWSP = '\u200B'
    private val headingRe = Regex("^(#{1,3}) ")
    private val todoRe = Regex("^[-*+] \\[([ xX])] ?")
    private val bulletRe = Regex("^[-*+] ")
    private val numberRe = Regex("^\\d+[.)] ")
    private val ruleRe = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val startsLikeBlock = Regex("^(#{1,3} |> |[-+*] |\\d+[.)] )")

    // ------------------------------------------------------------------ parse

    private class Builder {
        val text = StringBuilder()
        var masks = IntArray(64)
        var links = IntArray(64)
        val urls = ArrayList<String>()
        val keys = ArrayList<String>()
        var map: IntArray? = null
        var base = 0
        fun add(c: Char, mask: Int, link: Int) {
            if (text.length == masks.size) { masks = masks.copyOf(masks.size * 2); links = links.copyOf(links.size * 2) }
            masks[text.length] = if (c == '\n' || c == IMG) 0 else mask
            links[text.length] = if (c == '\n' || c == IMG) 0 else link
            text.append(c)
        }
        fun url(u: String): Int {
            val at = urls.indexOf(u)
            if (at >= 0) return at + 1
            urls.add(u)
            return urls.size
        }
    }

    fun parse(markdown: String): RichDoc = parseWithCaret(markdown, 0).first

    /** Plain text with no formatting at all (code blocks): one kind-less line per newline. */
    fun plainDoc(text: String): RichDoc {
        val lines = text.count { it == '\n' } + 1
        return RichDoc(text, IntArray(text.length), IntArray(text.length), emptyList(), IntArray(lines), emptyList())
    }

    /** The document and where Markdown offset [caret] lands in its plain text. */
    fun parseWithCaret(markdown: String, caret: Int, blocks: Boolean = false): Pair<RichDoc, Int> {
        val src = markdown.replace("\r\n", "\n").replace("\r", "\n").replace(ZWSP.toString(), "")
        if (src.isEmpty()) return RichDoc.Empty to 0
        val b = Builder()
        val exact = src.length == markdown.length
        val map = IntArray(src.length + 1) { -1 }
        var base = 0
        val kinds = ArrayList<Int>()
        var inFence = false
        var emitted = false
        for (line in src.split('\n')) {
            val lineBase = base
            base += line.length + 1
            if (!blocks && line.startsWith("```")) { inFence = !inFence; continue }
            if (emitted) b.add('\n', 0, 0)
            emitted = true
            map[lineBase] = b.text.length
            if (inFence) { kinds.add(RLine.CODE); for ((ci, c) in line.withIndex()) { map[lineBase + ci] = b.text.length; b.add(c, 0, 0) }; map[lineBase + line.length] = b.text.length; continue }
            var kind = RLine.NONE
            var from = 0
            val heading = headingRe.find(line)
            val todo = todoRe.find(line)
            if (!blocks) when {
                heading != null -> { kind = heading.groupValues[1].length; from = heading.value.length }
                line.startsWith("> ") -> { kind = RLine.QUOTE; from = 2 }
                todo != null -> { kind = if (todo.groupValues[1] == " ") RLine.CHECK else RLine.CHECKED; from = todo.value.length }
                bulletRe.containsMatchIn(line) && !ruleRe.matches(line) -> { kind = RLine.BULLET; from = 2 }
                numberRe.containsMatchIn(line) -> { kind = RLine.NUMBER; from = numberRe.find(line)!!.value.length }
                ruleRe.matches(line) -> { kind = RLine.RULE; from = line.length }
            }
            kinds.add(kind)
            b.map = map; b.base = lineBase
            inline(b, line, from, line.length, 0, 0)
            map[lineBase + line.length] = b.text.length
        }
        if (kinds.isEmpty()) return RichDoc.Empty to 0
        val len = b.text.length
        for (k in map.size - 1 downTo 0) if (map[k] < 0) map[k] = if (k + 1 < map.size && map[k + 1] >= 0) map[k + 1] else len
        val at = if (exact) map[caret.coerceIn(0, src.length)] else len
        return RichDoc(b.text.toString(), b.masks.copyOf(len), b.links.copyOf(len), b.urls, kinds.toIntArray(), b.keys) to at.coerceIn(0, len)
    }

    private fun opens(s: String, i: Int, mark: Int, from: Int, to: Int): Boolean {
        if (i + mark >= to || s[i + mark].isWhitespace()) return false
        if (i > from) {
            val p = s[i - 1]
            if (p == s[i] || (s[i] == '_' && p.isLetterOrDigit())) return false
        }
        return true
    }

    private fun closer(s: String, c: Char, mark: Int, start: Int, to: Int): Int {
        var j = start
        while (j + mark <= to) {
            if (j < to && s[j] == c && (mark == 1 || (j + 1 < to && s[j + 1] == c)) && j > 0 && s[j - 1] != '\\') {
                val before = s[j - 1]
                val after = if (j + mark < to) s[j + mark] else ' '
                val single = mark == 1 && (before == c || (j + 1 < to && s[j + 1] == c))
                val wordEdge = c == '_' && after.isLetterOrDigit()
                if (!before.isWhitespace() && !wordEdge && !single) return j
            }
            j++
        }
        return -1
    }

    private fun inline(b: Builder, s: String, from: Int, to: Int, mask: Int, link: Int) {
        var i = from
        while (i < to) {
            val c = s[i]
            b.map?.let { m -> val at = b.base + i; if (at < m.size && m[at] < 0) m[at] = b.text.length }
            if (c == '\\' && i + 1 < to && ESCAPABLE.indexOf(s[i + 1]) >= 0) { b.add(s[i + 1], mask, link); i += 2; continue }
            if (c == '[' && s.startsWith("[[img:", i)) {
                val m = InlineImageTokens.REGEX.matchAt(s, i)
                if (m != null) {
                    b.add(IMG, 0, 0); b.keys.add(m.groupValues[1])
                    i = m.range.last + 1; continue
                }
            }
            if (c == '<' && s.startsWith("<u>", i)) {
                val j = s.indexOf("</u>", i + 3)
                if (j >= i + 3 && j + 4 <= to) { inline(b, s, i + 3, j, mask or Fmt.UNDERLINE, link); i = j + 4; continue }
            }
            if (c == '`') {
                val j = s.indexOf('`', i + 1)
                if (j > i + 1 && j < to) { for (k in i + 1 until j) b.add(s[k], mask or Fmt.CODE, link); i = j + 1; continue }
            }
            if (c == '$') {
                val j = s.indexOf('$', i + 1)
                if (j > i + 1 && j < to) { for (k in i..j) b.add(s[k], mask, link); i = j + 1; continue }
            }
            if (c == '*' && i + 2 < to && s[i + 1] == '*' && s[i + 2] == '*' && opens(s, i, 3, from, to)) {
                val j = s.indexOf("***", i + 3)
                if (j >= i + 3 && j + 3 <= to && !s[j - 1].isWhitespace()) {
                    inline(b, s, i + 3, j, mask or Fmt.BOLD or Fmt.ITALIC, link); i = j + 3; continue
                }
            }
            if (c == '*' || c == '~' || c == '_') {
                val dbl = i + 1 < to && s[i + 1] == c
                val m = if (dbl) 2 else 1
                if (c == '~' && !dbl) { b.add(c, mask, link); i++; continue }
                if (opens(s, i, m, from, to)) {
                    val j = closer(s, c, m, i + m, to)
                    if (j > 0) {
                        val f = if (c == '~') Fmt.STRIKE else if (m == 2) Fmt.BOLD else Fmt.ITALIC
                        inline(b, s, i + m, j, mask or f, link); i = j + m; continue
                    }
                }
                for (k in 0 until m) b.add(c, mask, link)
                i += m; continue
            }
            if (c == '[') {
                val mid = s.indexOf("](", i + 1)
                if (mid > i + 1 && mid < to) {
                    val close = s.indexOf(')', mid + 2)
                    val label = s.substring(i + 1, mid)
                    if (close in (mid + 2) until to && label.indexOf('[') < 0 && label.indexOf(']') < 0) {
                        inline(b, s, i + 1, mid, mask, b.url(s.substring(mid + 2, close))); i = close + 1; continue
                    }
                }
            }
            b.add(c, mask, link); i++
        }
    }

    // ------------------------------------------------------------------ write

    private fun escapeAt(sb: StringBuilder, text: String, i: Int, strikeLine: Boolean, bracketLine: Boolean) {
        val c = text[i]
        val esc = when (c) {
            '*', '`' -> true
            '_' -> !(i > 0 && i + 1 < text.length && text[i - 1].isLetterOrDigit() && text[i + 1].isLetterOrDigit())
            '~' -> strikeLine
            '[', ']' -> bracketLine
            '\\' -> i + 1 < text.length && ESCAPABLE.indexOf(text[i + 1]) >= 0
            else -> false
        }
        if (esc) sb.append('\\')
        sb.append(c)
    }

    private fun opener(mask: Int, link: Int): String {
        val sb = StringBuilder()
        if (link != 0) sb.append('[')
        if (mask and Fmt.UNDERLINE != 0) sb.append("<u>")
        if (mask and Fmt.STRIKE != 0) sb.append("~~")
        sb.append(if (mask and Fmt.BOLD != 0 && mask and Fmt.ITALIC != 0) "***" else if (mask and Fmt.BOLD != 0) "**" else if (mask and Fmt.ITALIC != 0) "*" else "")
        if (mask and Fmt.CODE != 0) sb.append('`')
        return sb.toString()
    }

    private fun closerOf(mask: Int, link: Int, url: String): String {
        val sb = StringBuilder()
        if (mask and Fmt.CODE != 0) sb.append('`')
        sb.append(if (mask and Fmt.BOLD != 0 && mask and Fmt.ITALIC != 0) "***" else if (mask and Fmt.BOLD != 0) "**" else if (mask and Fmt.ITALIC != 0) "*" else "")
        if (mask and Fmt.STRIKE != 0) sb.append("~~")
        if (mask and Fmt.UNDERLINE != 0) sb.append("</u>")
        if (link != 0) sb.append("](").append(url.replace(" ", "%20").replace(")", "%29")).append(')')
        return sb.toString()
    }

    private class Ctx(val caret: Int) { var hit = -1 }

    private fun Ctx.mark(pos: Int, out: StringBuilder) { if (hit < 0 && pos == caret) hit = out.length }

    fun write(doc: RichDoc): String = writeWithCaret(doc, -1).first

    /** The Markdown and where [caret] (an offset in the plain text) lands in it. */
    fun writeWithCaret(doc: RichDoc, caret: Int): Pair<String, Int> {
        if (doc.text.isEmpty()) return "" to 0
        val ctx = Ctx(caret)
        val out = StringBuilder(doc.text.length + 32)
        var number = 0
        var imgIndex = 0
        var li = 0
        var fenced = false
        while (li < doc.kinds.size) {
            val kind = doc.kinds[li]
            val ls = doc.lineStart(li)
            val le = doc.lineEnd(li)
            if (kind == RLine.CODE && !fenced) { out.append("```\n"); fenced = true }
            if (kind != RLine.CODE && fenced) { out.append("```\n"); fenced = false }
            number = if (kind == RLine.NUMBER) number + 1 else 0
            when (kind) {
                RLine.H1 -> out.append("# ")
                RLine.H2 -> out.append("## ")
                RLine.H3 -> out.append("### ")
                RLine.QUOTE -> out.append("> ")
                RLine.BULLET -> out.append("- ")
                RLine.NUMBER -> out.append(number).append(". ")
                RLine.CHECK -> out.append("- [ ] ")
                RLine.CHECKED -> out.append("- [x] ")
                RLine.RULE -> out.append("---")
            }
            when (kind) {
                RLine.CODE -> { if (caret in ls..le && ctx.hit < 0) ctx.hit = out.length + (caret - ls); out.append(doc.text, ls, le) }
                RLine.RULE -> ctx.mark(ls, out)
                else -> imgIndex = writeInline(out, doc, ls, le, imgIndex, kind == RLine.NONE, ctx)
            }
            ctx.mark(le, out)
            if (kind == RLine.CODE) { for (k in ls until le) if (doc.text[k] == IMG) imgIndex++ }
            if (li < doc.kinds.size - 1) out.append('\n')
            li++
        }
        if (fenced) out.append("\n```")
        return out.toString() to (if (ctx.hit >= 0) ctx.hit else out.length)
    }

    private fun writeInline(out: StringBuilder, doc: RichDoc, ls: Int, le: Int, imgStart: Int, plainLine: Boolean, ctx: Ctx): Int {
        var imgIndex = imgStart
        val t = doc.text
        val line = t.substring(ls, le)
        val strikeLine = line.contains("~~")
        val bracketLine = line.contains("](")
        // $...$ spans stay literal, so formulas are not escaped
        val math = BooleanArray(line.length)
        var q = 0
        while (q < line.length) {
            if (line[q] == '$') {
                val j = line.indexOf('$', q + 1)
                if (j > q + 1) { for (k in q..j) math[k] = true; q = j + 1; continue }
            }
            q++
        }
        if (plainLine && startsLikeBlock.containsMatchIn(line) || plainLine && ruleRe.matches(line)) {
            val first = line[0]
            if (first.isDigit()) {
                var d = 0
                while (d < line.length && line[d].isDigit()) d++
                out.append(line, 0, d).append('\\')
                writeRange(out, doc, ls + d, le, ls, math, strikeLine, bracketLine, ctx) { imgIndex++ ; imgIndex - 1 }
                return imgIndex
            }
            if (first == '#' || first == '>' || first == '-' || first == '+') out.append('\\')
        }
        writeRange(out, doc, ls, le, ls, math, strikeLine, bracketLine, ctx) { imgIndex++; imgIndex - 1 }
        return imgIndex
    }

    private inline fun writeRange(
        out: StringBuilder, doc: RichDoc, from: Int, to: Int, lineStart: Int,
        math: BooleanArray, strikeLine: Boolean, bracketLine: Boolean, ctx: Ctx, nextImage: () -> Int
    ) {
        val t = doc.text
        var i = from
        var lastWasMarker = false
        while (i < to) {
            ctx.mark(i, out)
            if (t[i] == IMG) {
                val k = nextImage()
                out.append("[[img:").append(doc.keys.getOrElse(k) { "0" }).append("]]")
                lastWasMarker = false; i++; continue
            }
            val mask = doc.masks[i]
            val link = doc.links[i]
            var j = i + 1
            while (j < to && t[j] != IMG && doc.masks[j] == mask && doc.links[j] == link) j++
            if (mask == 0 && link == 0) {
                for (k in i until j) { ctx.mark(k, out); if (math[k - lineStart]) out.append(t[k]) else escapeAt(out, t, k, strikeLine, bracketLine) }
                lastWasMarker = false; i = j; continue
            }
            var a = i
            var b = j
            while (a < b && t[a].isWhitespace()) a++
            while (b > a && t[b - 1].isWhitespace()) b--
            for (k in i until a) { ctx.mark(k, out); out.append(t[k]) }
            if (a < b) {
                val open = opener(mask, link)
                if (lastWasMarker && a == i && open.isNotEmpty()) out.append(ZWSP)
                out.append(open)
                if (mask and Fmt.CODE != 0) { if (ctx.caret in a until b && ctx.hit < 0) ctx.hit = out.length + (ctx.caret - a); out.append(t, a, b) }
                else for (k in a until b) { ctx.mark(k, out); if (math[k - lineStart]) out.append(t[k]) else escapeAt(out, t, k, strikeLine, bracketLine) }
                out.append(closerOf(mask, link, if (link != 0) doc.urls[link - 1] else ""))
                lastWasMarker = b == j
            }
            for (k in b until j) { ctx.mark(k, out); out.append(t[k]) }
            if (b < j) lastWasMarker = false
            i = j
        }
    }

    /** Plain reading text: no markers, pictures as [image]. */
    fun plain(doc: RichDoc): String = doc.text.replace(IMG.toString(), "[image]")
}
