package com.neb.ians.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/** What the field paints for a [RichDoc]: styled text, list marks drawn in front of lines, pictures as blank slots. */
internal class RichBuilt(
    val text: AnnotatedString,
    private val o2t: IntArray,
    private val t2o: IntArray,
    /** Transformed index of the first blank character of each picture. */
    val slots: IntArray,
    private val rawLength: Int
) {
    val transformed = TransformedText(text, object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = o2t[offset.coerceIn(0, rawLength)]
        override fun transformedToOriginal(offset: Int): Int = t2o[offset.coerceIn(0, text.length)]
    })

    fun toOriginal(offset: Int): Int = t2o[offset.coerceIn(0, text.length)]

    val transformation = object : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText =
            if (text.text.length == rawLength) transformed else TransformedText(text, OffsetMapping.Identity)
    }
}

internal object RichVisual {
    private const val ZW = '\u200B'

    /** [imageLine] is the paragraph style of a line that holds a picture: only those lines grow, the rest keep the text's own height. */
    fun build(doc: RichDoc, c: LiveColors, blankSpacing: TextUnit, imageLine: ParagraphStyle? = null): RichBuilt {
        val t = doc.text
        val n = t.length
        val lines = doc.kinds.size
        val prefixes = arrayOfNulls<String>(lines)
        var num = 0
        var prefixTotal = 0
        for (li in 0 until lines) {
            val kind = doc.kinds[li]
            num = if (kind == RLine.NUMBER) num + 1 else 0
            val p = when (kind) {
                RLine.BULLET -> "\u2022 "
                RLine.NUMBER -> "$num. "
                RLine.CHECK -> "\u2610 "
                RLine.CHECKED -> "\u2611 "
                RLine.QUOTE -> "\u2502 "
                RLine.RULE -> "\u2500".repeat(18)
                else -> ""
            }
            prefixes[li] = p
            prefixTotal += p.length
        }
        val ph = InlineImageTokens.PLACEHOLDER_LEN
        val imgs = t.count { it == IMG }
        val cap = n + (ph - 1) * imgs + prefixTotal + 4
        val o2t = IntArray(n + 1)
        val t2o = IntArray(cap + 1)
        val sb = StringBuilder(cap)
        val spans = ArrayList<AnnotatedString.Range<SpanStyle>>()
        val slots = ArrayList<Int>()
        val paras = ArrayList<AnnotatedString.Range<ParagraphStyle>>()
        val code = SpanStyle(fontFamily = FontFamily.Monospace, background = c.code, color = c.codeText)
        val blank = SpanStyle(color = Color.Transparent, letterSpacing = blankSpacing)
        // A line break next to a picture line becomes a zero width mark: a Compose paragraph that ends in a real line
        // break gets one more empty line from the platform layout, which was the gap under every picture line.
        val hasImg = BooleanArray(lines)
        for (li in 0 until lines) {
            val a = doc.lineStart(li)
            val b = doc.lineEnd(li)
            var k = a
            while (k < b) { if (t[k] == IMG) { hasImg[li] = true; break }; k++ }
        }
        var tailMark = false

        for (li in 0 until lines) {
            val ls = doc.lineStart(li)
            val le = doc.lineEnd(li)
            val kind = doc.kinds[li]
            val pre = prefixes[li]!!
            val tStart = sb.length
            for (ch in pre) { t2o[sb.length] = ls; sb.append(ch) }
            val contentStart = sb.length
            if (pre.isNotEmpty()) spans.add(AnnotatedString.Range(SpanStyle(color = if (kind == RLine.CHECKED) c.link else c.muted), tStart, contentStart))
            for (i in ls until le) {
                o2t[i] = sb.length
                if (t[i] == IMG) {
                    slots.add(sb.length)
                    val s0 = sb.length
                    for (k in 0 until ph) { t2o[sb.length] = i; sb.append(InlineImageTokens.PLACEHOLDER_CHAR) }
                    spans.add(AnnotatedString.Range(blank, s0, sb.length))
                } else {
                    t2o[sb.length] = i
                    sb.append(t[i])
                }
            }
            o2t[le] = if (le == ls) contentStart else o2t[le]
            val contentEnd = sb.length
            // an empty last line right after a picture line has no characters, so it would not get a line at all
            if (li == lines - 1 && li > 0 && hasImg[li - 1] && le == ls && pre.isEmpty()) {
                o2t[le] = sb.length
                t2o[sb.length] = le
                sb.append(ZW)
                tailMark = true
            }
            if (contentEnd > contentStart) {
                when (kind) {
                    RLine.H1 -> spans.add(AnnotatedString.Range(SpanStyle(fontSize = 1.45f.em, fontWeight = FontWeight.Bold), contentStart, contentEnd))
                    RLine.H2 -> spans.add(AnnotatedString.Range(SpanStyle(fontSize = 1.25f.em, fontWeight = FontWeight.Bold), contentStart, contentEnd))
                    RLine.H3 -> spans.add(AnnotatedString.Range(SpanStyle(fontSize = 1.1f.em, fontWeight = FontWeight.Bold), contentStart, contentEnd))
                    RLine.QUOTE -> spans.add(AnnotatedString.Range(SpanStyle(fontStyle = FontStyle.Italic, color = c.quote), contentStart, contentEnd))
                    RLine.CODE -> spans.add(AnnotatedString.Range(code, contentStart, contentEnd))
                    RLine.CHECKED -> spans.add(AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.LineThrough, color = c.muted), contentStart, contentEnd))
                }
            }
            if (li < lines - 1) {
                o2t[le] = sb.length
                t2o[sb.length] = le
                sb.append(if (hasImg[li] || hasImg[li + 1]) ZW else '\n')
            }
            if (imageLine != null && hasImg[li]) {
                // the paragraph ends with the zero width mark (never a real line break), so it adds no empty line
                paras.add(AnnotatedString.Range(imageLine, tStart, sb.length))
            }
        }
        o2t[n] = if (tailMark) sb.length - 1 else sb.length
        t2o[sb.length] = n

        // inline styles: one span per run of equal formatting
        val cache = HashMap<Int, SpanStyle>()
        var i = 0
        while (i < n) {
            val ch = t[i]
            val mask = doc.masks[i]
            val link = doc.links[i]
            if (ch == '\n' || ch == IMG || (mask == 0 && link == 0)) { i++; continue }
            var j = i + 1
            while (j < n && t[j] != '\n' && t[j] != IMG && doc.masks[j] == mask && doc.links[j] == link) j++
            val key = mask or (if (link != 0) 256 else 0)
            val style = cache.getOrPut(key) {
                var deco: TextDecoration? = null
                if (mask and Fmt.STRIKE != 0) deco = TextDecoration.LineThrough
                if (mask and Fmt.UNDERLINE != 0 && link == 0) deco = if (deco == null) TextDecoration.Underline else TextDecoration.combine(listOf(deco, TextDecoration.Underline))
                if (link != 0) deco = if (deco == null) TextDecoration.Underline else TextDecoration.combine(listOf(deco, TextDecoration.Underline))
                SpanStyle(
                    fontWeight = if (mask and Fmt.BOLD != 0) FontWeight.Bold else null,
                    fontStyle = if (mask and Fmt.ITALIC != 0) FontStyle.Italic else null,
                    textDecoration = deco,
                    color = if (link != 0) c.link else Color.Unspecified,
                    fontFamily = if (mask and Fmt.CODE != 0) FontFamily.Monospace else null,
                    background = if (mask and Fmt.CODE != 0) c.code else Color.Unspecified
                )
            }
            val a = o2t[i]
            val b = o2t[j - 1] + 1
            if (b > a) spans.add(AnnotatedString.Range(style, a, b))
            i = j
        }
        val text = AnnotatedString(sb.toString(), spans, paras)
        return RichBuilt(text, o2t, t2o, slots.toIntArray(), n)
    }
}
