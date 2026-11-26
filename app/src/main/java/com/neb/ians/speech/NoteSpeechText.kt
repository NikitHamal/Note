package com.neb.ians.speech

/** Turns a note into sentences a voice can read: Markdown noise out, long lines split. */
object NoteSpeechText {
    private val fence = Regex("^\\s*(```|~~~)")
    private val heading = Regex("^\\s{0,3}#{1,6}\\s+")
    private val bullet = Regex("^\\s*([-*+]|\\d+[.)])\\s+")
    private val check = Regex("^\\[( |x|X)]\\s*")
    private val rule = Regex("^\\s*([-*_]\\s*){3,}$")
    private val image = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val underscored = Regex("(?<!\\w)_([^_\\n]+)_(?!\\w)")
    private val tag = Regex("(?<![\\w&])#(\\w+)")
    private val quotePrefix = Regex("^\\s*>+\\s?")
    private val multiSpace = Regex("\\s+")
    private val sentenceEnd = Regex("(?<=[.!?;:])\\s+|(?<=[\\u0964\\u0965])\\s*")
    private const val MAX = 180

    fun segments(title: String, body: String): List<String> {
        val out = ArrayList<String>()
        if (title.isNotBlank()) out += finish(clean(title))
        var inCode = false
        for (raw in body.lineSequence()) {
            if (fence.containsMatchIn(raw)) { inCode = !inCode; continue }
            if (inCode || raw.isBlank() || rule.matches(raw)) continue
            var line = raw
            val isHeading = heading.containsMatchIn(line)
            line = heading.replaceFirst(line, "")
            line = line.replaceFirst(quotePrefix, "")
            line = bullet.replaceFirst(line, "")
            var done = false
            check.find(line)?.let { m -> done = m.groupValues[1].lowercase() == "x"; line = line.substring(m.range.last + 1) }
            line = clean(line)
            if (line.isBlank()) continue
            if (done) line = "Done: $line"
            if (isHeading) line = finish(line)
            out += split(line)
        }
        return out.filter { it.any(Char::isLetterOrDigit) }
    }

    /** Plain text, for previews and for counting words. */
    fun plain(body: String): String = segments("", body).joinToString(" ")

    private fun clean(s: String): String {
        var t = image.replace(s, "")
        t = link.replace(t, "$1")
        t = underscored.replace(t, "$1")
        t = tag.replace(t, "$1")
        t = t.replace("**", "").replace("__", "").replace("~~", "").replace("`", "").replace("*", "")
        return t.replace(multiSpace, " ").trim()
    }

    private fun finish(s: String) = if (s.isEmpty() || s.last() in ".!?\u0964\u0965") s else "$s."

    private fun split(line: String): List<String> {
        val sentences = line.split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.size <= 1 && line.length <= MAX) return listOf(line)
        val parts = ArrayList<String>()
        val cur = StringBuilder()
        for (sentence in sentences) {
            if (cur.isNotEmpty() && cur.length + sentence.length + 1 > MAX) { parts += cur.toString(); cur.clear() }
            if (sentence.length > MAX) {
                var rest = sentence
                while (rest.length > MAX) {
                    val cut = rest.lastIndexOf(' ', MAX).takeIf { it > 0 } ?: MAX
                    parts += rest.substring(0, cut).trim()
                    rest = rest.substring(cut).trim()
                }
                cur.append(rest)
            } else {
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(sentence)
            }
        }
        if (cur.isNotEmpty()) parts += cur.toString()
        return parts
    }
}
