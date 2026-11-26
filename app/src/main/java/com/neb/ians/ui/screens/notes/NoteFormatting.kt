package com.neb.ians.ui.screens.notes

/** Pure Markdown counting helpers for the note body. */
object NoteFormatting {
    private val checkLine = Regex("^(\\s*[-*+]\\s)\\[([ xX])](\\s.*)?$")

    fun checkProgress(body: String): Pair<Int, Int> {
        var done = 0
        var total = 0
        for (line in body.lineSequence()) {
            val m = checkLine.find(line) ?: continue
            total++
            if (m.groupValues[2].lowercase() == "x") done++
        }
        return done to total
    }

    fun wordCount(body: String): Int {
        var count = 0
        var inWord = false
        var hasAlnum = false
        for (i in 0 until body.length) {
            val ch = body[i]
            if (ch.isWhitespace()) {
                if (inWord && hasAlnum) count++
                inWord = false
                hasAlnum = false
            } else {
                inWord = true
                if (!hasAlnum && ch.isLetterOrDigit()) hasAlnum = true
            }
        }
        if (inWord && hasAlnum) count++
        return count
    }
}
