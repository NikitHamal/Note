package com.neb.ians.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em

/** Colours the live preview needs; the rest comes from the text style of the card. */
data class LiveColors(val code: Color, val codeText: Color, val link: Color, val quote: Color, val muted: Color)

class LiveText(
    val raw: String,
    val visible: String,
    val styled: AnnotatedString
)

object LiveMarkdown {
    internal const val ZWSP = "\u200B"
    private const val ESCAPABLE = "\\`*_~|[]()#>-+.!$"
    private val headingRe = Regex("^(#{1,3}) ")
    private val bulletRe = Regex("^(\\s*)([-*+]) (?!\\[[ xX]] )")
    private val todoRe = Regex("^(\\s*)[-*+] \\[([ xX])] ")
    private val hrRe = Regex("^(-{3,}|\\*{3,}|_{3,})$")

    private val plain = LiveColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)

    private val lru = arrayOfNulls<LiveText>(8)
    private val lruColors = arrayOfNulls<LiveColors>(8)
    private var lruNext = 0

    fun transform(raw: String, colors: LiveColors = plain): LiveText {
        synchronized(lru) {
            for (i in lru.indices) {
                val hit = lru[i]
                if (hit != null && lruColors[i] == colors && hit.raw == raw) return hit
            }
        }
        val made = parse(raw, colors)
        synchronized(lru) {
            lru[lruNext] = made; lruColors[lruNext] = colors
            lruNext = (lruNext + 1) % lru.size
        }
        return made
    }

    private fun parse(raw: String, colors: LiveColors): LiveText {
        val n = raw.length
        val hidden = BooleanArray(n)
        val replace = CharArray(n)
        val runs = ArrayList<Triple<Int, Int, SpanStyle>>()

        val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.code, color = colors.codeText)
        val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
        val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
        val strikeStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
        val linkStyle = SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)

        fun hide(from: Int, to: Int) { for (i in from until to) if (i in 0 until n) hidden[i] = true }

        fun scanInline(from: Int, to: Int) {
            fun opens(i: Int, mark: Int): Boolean {
                if (i + mark >= to || raw[i + mark].isWhitespace()) return false
                if (i > from) {
                    val p = raw[i - 1]
                    if (p == raw[i] || (raw[i] == '_' && p.isLetterOrDigit())) return false
                }
                return true
            }
            fun closer(c: Char, mark: Int, start: Int): Int {
                var j = start
                while (j + mark <= to) {
                    if (raw[j] == c && (mark == 1 || (j + 1 < to && raw[j + 1] == c)) && raw[j - 1] != '\\' && !hidden[j]) {
                        val before = raw[j - 1]
                        val after = if (j + mark < to) raw[j + mark] else ' '
                        val single = mark == 1 && (before == c || (j + 1 < to && raw[j + 1] == c))
                        val wordEdge = c == '_' && after.isLetterOrDigit()
                        if (!before.isWhitespace() && !wordEdge && !single) return j
                    }
                    j++
                }
                return -1
            }
            var i = from
            while (i < to) {
                if (hidden[i]) { i++; continue }
                val c = raw[i]
                if (c == '\\' && i + 1 < to && ESCAPABLE.indexOf(raw[i + 1]) >= 0) { hidden[i] = true; i += 2; continue }
                if (c == '`') {
                    val j = raw.indexOf('`', i + 1)
                    if (j > i + 1 && j < to) {
                        hide(i, i + 1); hide(j, j + 1)
                        runs.add(Triple(i + 1, j, codeStyle))
                        i = j + 1; continue
                    }
                }
                if (c == '$') {
                    val j = raw.indexOf('$', i + 1)
                    if (j > i + 1 && j < to) { i = j + 1; continue }
                }
                if (c == '*' && i + 2 < to && raw[i + 1] == '*' && raw[i + 2] == '*' && opens(i, 3)) {
                    val j3 = raw.indexOf("***", i + 3)
                    if (j3 >= i + 3 && j3 + 3 <= to && !raw[j3 - 1].isWhitespace() && !hidden[j3]) {
                        hide(i, i + 3); hide(j3, j3 + 3)
                        runs.add(Triple(i + 3, j3, boldStyle)); runs.add(Triple(i + 3, j3, italicStyle))
                        i = j3 + 3; continue
                    }
                }
                if (c == '*' || c == '~' || c == '_') {
                    val dbl = i + 1 < to && raw[i + 1] == c
                    val mark = if (dbl) 2 else 1
                    if (c == '~' && !dbl) { i++; continue }
                    if (opens(i, mark)) {
                        val j = closer(c, mark, i + mark)
                        if (j > 0) {
                            hide(i, i + mark); hide(j, j + mark)
                            val style = if (c == '~') strikeStyle else if (mark == 2) boldStyle else italicStyle
                            runs.add(Triple(i + mark, j, style))
                            i += mark; continue
                        }
                    }
                    i += mark; continue
                }
                if (c == '[') {
                    val mid = raw.indexOf("](", i + 1)
                    if (mid > i + 1 && mid < to) {
                        val text = raw.substring(i + 1, mid)
                        val close = raw.indexOf(')', mid + 2)
                        if (close in (mid + 2) until to && text.indexOf('[') < 0 && text.indexOf(']') < 0) {
                            hide(i, i + 1); hide(mid, close + 1)
                            runs.add(Triple(i + 1, mid, linkStyle))
                            i = close + 1; continue
                        }
                    }
                }
                i++
            }
        }

        var inFence = false
        var ls = 0
        while (ls <= n) {
            var le = raw.indexOf('\n', ls)
            if (le < 0) le = n
            val line = raw.substring(ls, le)
            if (line.startsWith("```")) {
                inFence = !inFence
                hide(ls, le)
            } else if (inFence) {
                runs.add(Triple(ls, le, codeStyle))
            } else {
                var from = ls
                val heading = headingRe.find(line)
                val todo = todoRe.find(line)
                val bullet = bulletRe.find(line)
                when {
                    heading != null -> {
                        val level = heading.groupValues[1].length
                        hide(ls, ls + heading.value.length)
                        from = ls + heading.value.length
                        val size = if (level == 1) 1.4f else if (level == 2) 1.22f else 1.1f
                        runs.add(Triple(from, le, SpanStyle(fontSize = size.em, fontWeight = FontWeight.Bold)))
                    }
                    line.startsWith("> ") -> {
                        hide(ls, ls + 2); from = ls + 2
                        runs.add(Triple(from, le, SpanStyle(color = colors.quote, fontStyle = FontStyle.Italic)))
                    }
                    todo != null -> {
                        val ind = todo.groupValues[1].length
                        val done = todo.groupValues[2] != " "
                        hide(ls + ind, ls + ind + 3); hide(ls + ind + 4, ls + ind + 5)
                        replace[ls + ind + 3] = if (done) '\u2611' else '\u2610'
                        from = ls + ind + 6
                        if (done) runs.add(Triple(from, le, SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.muted)))
                    }
                    bullet != null -> {
                        replace[ls + bullet.groupValues[1].length] = '\u2022'
                        from = ls + bullet.value.length
                    }
                    hrRe.matches(line) -> {
                        for (i in ls until le) replace[i] = '\u2500'
                        from = le
                    }
                }
                scanInline(from, le)
            }
            if (le >= n) break
            ls = le + 1
        }

        val o2t = IntArray(n + 1)
        val sb = StringBuilder(n)
        var t = 0
        for (i in 0 until n) {
            o2t[i] = t
            if (!hidden[i]) {
                sb.append(if (replace[i] != '\u0000') replace[i] else raw[i])
                t++
            }
        }
        o2t[n] = t
        val spans = ArrayList<AnnotatedString.Range<SpanStyle>>(runs.size)
        for ((s, e, style) in runs) {
            val a = o2t[s.coerceIn(0, n)]
            val b = o2t[e.coerceIn(0, n)]
            if (b > a) spans.add(AnnotatedString.Range(style, a, b))
        }
        val visible = sb.toString()
        return LiveText(raw, visible, AnnotatedString(visible, spans))
    }
}
