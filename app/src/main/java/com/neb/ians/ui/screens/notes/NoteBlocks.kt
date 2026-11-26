package com.neb.ians.ui.screens.notes

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/*
 * A note is a list of blocks, the way Notion keeps it: text in several shapes, to-dos, quotes, code, dividers, pictures,
 * voice recordings, tables and sketches. Text inside a block is inline Markdown that the editor draws live (see
 * LiveMarkdown), so nobody types syntax.
 *
 * Stored twice: `doc` holds the blocks as JSON (lossless), `body` holds a Markdown export, so the list, search, sharing
 * and read-aloud keep working on plain text. Notes written before blocks existed have no `doc` and open from `body`.
 */

enum class BlockType(val label: String, val keywords: String) {
    Text("Text", "text paragraph plain"),
    H1("Heading 1", "heading title h1 big"),
    H2("Heading 2", "heading h2 subtitle"),
    H3("Heading 3", "heading h3 small"),
    Todo("To-do", "todo checklist check task"),
    Toggle("Toggle list", "toggle collapse fold expand outline dropdown"),
    Bullet("Bulleted list", "bullet list unordered"),
    Number("Numbered list", "number list ordered"),
    Quote("Quote", "quote cite"),
    Callout("Callout", "callout note tip highlight"),
    Code("Code", "code snippet pre"),
    Divider("Divider", "divider line rule"),
    Table("Table", "table grid rows columns"),
    Image("Photo", "image photo picture gallery"),
    Audio("Audio", "audio voice record sound"),
    Drawing("Drawing", "drawing sketch draw pen");

    /** One line of what the block is, shown beside it in the "/" list. */
    val hint: String
        get() = when (this) {
            Text -> "Plain writing"
            H1 -> "Big section title"
            H2 -> "Medium section title"
            H3 -> "Small section title"
            Todo -> "Track tasks with checkboxes"
            Toggle -> "Hide and show what is under it"
            Bullet -> "A simple list"
            Number -> "A list that counts"
            Quote -> "Set a line apart"
            Callout -> "Make a point stand out"
            Code -> "Monospace snippet"
            Divider -> "Split the page"
            Table -> "Rows and columns"
            Image -> "A photo from your gallery or camera"
            Audio -> "Record or add a sound"
            Drawing -> "Sketch with a pen"
        }

    val hasText: Boolean get() = this != Divider && this != Table && this != Image && this != Audio && this != Drawing
    val isList: Boolean get() = this == Todo || this == Bullet || this == Number || this == Toggle
}

/** One pen stroke. [points] is x0, y0, x1, y1... with x as a fraction of the canvas width and y of its height. */
data class Stroke(val color: Int, val width: Float, val points: List<Float>)

data class NoteBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType = BlockType.Text,
    val text: String = "",
    val checked: Boolean = false,
    /** Image or audio file. */
    val path: String = "",
    /** Image width as a fraction of the page. */
    val size: Float = 1f,
    /** Audio length. */
    val seconds: Int = 0,
    val rows: List<List<String>> = emptyList(),
    val strokes: List<Stroke> = emptyList(),
    /** Height of a drawing, in dp. */
    val height: Float = 260f,
    /** How far in the block sits (0 to [NoteDoc.MAX_INDENT]); blocks deeper than a toggle belong to it. */
    val indent: Int = 0,
    /** Toggle blocks: false hides everything nested under it. */
    val open: Boolean = true,
    /** Image placement: 0 left, 1 centre, 2 right. */
    val align: Int = 0,
    /** Image: the text block after it is written beside it instead of below. */
    val wrap: Boolean = false,
    /** Image caption. */
    val caption: String = "",
    /** Image crop: 0 as taken, 1 square, 2 wide, 3 tall. */
    val ratio: Int = 0,
    /** To-do due date as a day count since 1970 (UTC calendar day); 0 for none. */
    val due: Long = 0L,
    /** Text colour from `NoteInk` (0 is the page ink). */
    val tint: Int = 0,
    /** Highlight behind the block's text from `NoteInk` (0 for none). */
    val mark: Int = 0,
) {
    val isBlank: Boolean
        get() = when (type) {
            BlockType.Image, BlockType.Audio -> path.isBlank()
            BlockType.Table -> rows.all { r -> r.all { it.isBlank() } }
            BlockType.Drawing -> strokes.isEmpty()
            BlockType.Divider -> false
            else -> text.isBlank()
        }
}

object NoteDoc {
    const val MAX_INDENT = 3

    /** Positions of the blocks hidden because a closed toggle above them is their parent. */
    fun hiddenIndexes(blocks: List<NoteBlock>): Set<Int> {
        val hidden = HashSet<Int>()
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b.type == BlockType.Toggle && !b.open) {
                var j = i + 1
                while (j < blocks.size && blocks[j].indent > b.indent) { hidden.add(j); j++ }
                i = j
            } else i++
        }
        return hidden
    }

    /** How many blocks sit under [index] (the ones deeper than it, straight after it). */
    fun childCount(blocks: List<NoteBlock>, index: Int): Int {
        val base = blocks.getOrNull(index)?.indent ?: return 0
        var j = index + 1
        while (j < blocks.size && blocks[j].indent > base) j++
        return j - index - 1
    }

    fun starter(): List<NoteBlock> = listOf(NoteBlock())

    fun isBlank(blocks: List<NoteBlock>): Boolean = blocks.all { it.isBlank }

    // ---------------------------------------------------------------- JSON

    fun toJson(blocks: List<NoteBlock>): String {
        val arr = JSONArray()
        blocks.forEach { b ->
            val o = JSONObject()
            o.put("id", b.id)
            o.put("t", b.type.name)
            if (b.text.isNotEmpty()) o.put("x", b.text)
            if (b.checked) o.put("c", true)
            if (b.path.isNotEmpty()) o.put("p", b.path)
            if (b.size != 1f) o.put("s", b.size.toDouble())
            if (b.seconds > 0) o.put("d", b.seconds)
            if (b.rows.isNotEmpty()) {
                val rs = JSONArray()
                b.rows.forEach { r -> rs.put(JSONArray(r)) }
                o.put("r", rs)
            }
            if (b.strokes.isNotEmpty()) {
                val ss = JSONArray()
                b.strokes.forEach { st ->
                    val so = JSONObject()
                    so.put("k", st.color)
                    so.put("w", st.width.toDouble())
                    val pts = JSONArray()
                    st.points.forEach { pts.put(Math.round(it * 10000f) / 10000.0) }
                    so.put("p", pts)
                    ss.put(so)
                }
                o.put("k", ss)
            }
            if (b.type == BlockType.Drawing) o.put("h", b.height.toDouble())
            if (b.indent > 0) o.put("i", b.indent)
            if (!b.open) o.put("o", false)
            if (b.align != 0) o.put("a", b.align)
            if (b.wrap) o.put("w", true)
            if (b.caption.isNotEmpty()) o.put("q", b.caption)
            if (b.ratio != 0) o.put("ra", b.ratio)
            if (b.due > 0L) o.put("d", b.due)
            if (b.tint != 0) o.put("tc", b.tint)
            if (b.mark != 0) o.put("tf", b.mark)
            arr.put(o)
        }
        return arr.toString()
    }

    private val parsedJsonCache = android.util.LruCache<Int, Pair<String, List<NoteBlock>>>(96)

    fun fromJson(json: String): List<NoteBlock> {
        if (json.isBlank()) return starter()
        val key = json.hashCode()
        synchronized(parsedJsonCache) {
            parsedJsonCache.get(key)?.let { if (it.first == json) return it.second }
        }
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<NoteBlock>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = runCatching { BlockType.valueOf(o.optString("t", "Text")) }.getOrDefault(BlockType.Text)
                val rows = ArrayList<List<String>>()
                o.optJSONArray("r")?.let { rs ->
                    for (r in 0 until rs.length()) {
                        val cells = rs.getJSONArray(r)
                        rows.add(List(cells.length()) { c -> cells.optString(c, "") })
                    }
                }
                val strokes = ArrayList<Stroke>()
                o.optJSONArray("k")?.let { ss ->
                    for (k in 0 until ss.length()) {
                        val so = ss.getJSONObject(k)
                        val pts = so.optJSONArray("p")
                        strokes.add(
                            Stroke(
                                color = so.optInt("k", 0xFF1B1B1F.toInt()),
                                width = so.optDouble("w", 4.0).toFloat(),
                                points = if (pts == null) emptyList() else List(pts.length()) { j -> pts.optDouble(j, 0.0).toFloat() }
                            )
                        )
                    }
                }
                out.add(
                    NoteBlock(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = type,
                        text = o.optString("x", ""),
                        checked = o.optBoolean("c", false),
                        path = o.optString("p", ""),
                        size = o.optDouble("s", 1.0).toFloat(),
                        seconds = if (type == BlockType.Audio) o.optInt("d", 0) else 0,
                        rows = rows,
                        strokes = strokes,
                        height = o.optDouble("h", 260.0).toFloat(),
                        indent = o.optInt("i", 0).coerceIn(0, MAX_INDENT),
                        open = o.optBoolean("o", true),
                        align = o.optInt("a", 0).coerceIn(0, 2),
                        wrap = o.optBoolean("w", false),
                        caption = o.optString("q", ""),
                        ratio = o.optInt("ra", 0).coerceIn(0, 3),
                        due = if (type == BlockType.Todo) o.optLong("d", 0L).coerceAtLeast(0L) else 0L,
                        tint = o.optInt("tc", 0).coerceIn(0, NoteInk.count),
                        mark = o.optInt("tf", 0).coerceIn(0, NoteInk.count),
                    )
                )
            }
            val parsed = if (out.isEmpty()) starter() else coalesceTextBlocks(out)
            synchronized(parsedJsonCache) { parsedJsonCache.put(key, json to parsed) }
            parsed
        } catch (_: Exception) {
            starter()
        }
    }

    /**
     * Merges consecutive plain [BlockType.Text] blocks that share the same indent, alignment, ink tint,
     * and highlight mark into a single multi-line [BlockType.Text] block so paragraphs can be selected
     * and edited together across newlines. Preserves side-by-side image partner blocks untouched.
     */
    fun coalesceTextBlocks(blocks: List<NoteBlock>): List<NoteBlock> {
        if (blocks.size <= 1) return blocks
        val out = ArrayList<NoteBlock>(blocks.size)
        for (b in blocks) {
            val prev = out.lastOrNull()
            val prevPrev = if (out.size >= 2) out[out.size - 2] else null
            val prevIsImagePartner = prevPrev != null && prevPrev.type == BlockType.Image && prevPrev.wrap && prevPrev.path.isNotBlank()
            if (
                prev != null &&
                !prevIsImagePartner &&
                prev.type == BlockType.Text &&
                b.type == BlockType.Text &&
                prev.indent == b.indent &&
                prev.align == b.align &&
                prev.tint == b.tint &&
                prev.mark == b.mark
            ) {
                val mergedText = when {
                    prev.text.isEmpty() -> b.text
                    b.text.isEmpty() -> prev.text
                    else -> prev.text + "\n" + b.text
                }
                out[out.lastIndex] = prev.copy(text = mergedText)
            } else {
                out.add(b)
            }
        }
        return if (out.isEmpty()) starter() else out
    }

    /**
     * Merges consecutive text-bearing blocks (including headings, lists, quotes, and paragraphs)
     * into unified multi-line [BlockType.Text] blocks so the user can select and copy across the entire note.
     */
    fun coalesceAllTextForSelection(blocks: List<NoteBlock>): List<NoteBlock> {
        if (blocks.size <= 1) return blocks
        val out = ArrayList<NoteBlock>(blocks.size)
        var numCounter = 0
        for (b in blocks) {
            val canMerge = b.type.hasText && b.type != BlockType.Code
            if (!canMerge) {
                numCounter = 0
                out.add(b)
                continue
            }
            if (b.type == BlockType.Number) numCounter++ else numCounter = 0
            val lineText = when (b.type) {
                BlockType.Bullet -> if (b.text.isBlank()) "" else "\u2022 ${b.text}"
                BlockType.Number -> if (b.text.isBlank()) "" else "$numCounter. ${b.text}"
                BlockType.Todo -> if (b.text.isBlank()) "" else "${if (b.checked) "[\u2713]" else "[ ]"} ${b.text}"
                else -> b.text
            }
            val prev = out.lastOrNull()
            val prevPrev = if (out.size >= 2) out[out.size - 2] else null
            val prevIsImagePartner = prevPrev != null && prevPrev.type == BlockType.Image && prevPrev.wrap && prevPrev.path.isNotBlank()
            if (prev != null && !prevIsImagePartner && prev.type == BlockType.Text) {
                val mergedText = when {
                    prev.text.isEmpty() -> lineText
                    lineText.isEmpty() -> prev.text
                    else -> prev.text + "\n" + lineText
                }
                out[out.lastIndex] = prev.copy(text = mergedText)
            } else {
                out.add(b.copy(type = BlockType.Text, text = lineText, checked = false, indent = 0))
            }
        }
        return if (out.isEmpty()) starter() else out
    }

    // ---------------------------------------------------------------- Markdown

    fun toMarkdown(blocks: List<NoteBlock>): String {
        val sb = StringBuilder()
        var number = 0
        var prev: BlockType? = null
        val counters = HashMap<Int, Int>()
        blocks.forEach { b ->
            if (b.isBlank && b.type != BlockType.Divider) return@forEach
            if (b.type == BlockType.Number) {
                number = (counters[b.indent] ?: 0) + 1
                counters[b.indent] = number
                counters.keys.filter { it > b.indent }.forEach { counters[it] = 0 }
            } else {
                number = 0
                counters.keys.filter { it >= b.indent }.forEach { counters[it] = 0 }
            }
            val piece = when (b.type) {
                BlockType.Text -> b.text
                BlockType.H1 -> "# ${b.text}"
                BlockType.H2 -> "## ${b.text}"
                BlockType.H3 -> "### ${b.text}"
                BlockType.Bullet, BlockType.Toggle -> "${pad(b)}- ${b.text}"
                BlockType.Number -> "${pad(b)}$number. ${b.text}"
                BlockType.Todo -> "${pad(b)}- [${if (b.checked) "x" else " "}] ${b.text}"
                BlockType.Quote, BlockType.Callout -> b.text.lines().joinToString("\n") { "> $it" }
                BlockType.Code -> "```\n${b.text}\n```"
                BlockType.Divider -> "---"
                BlockType.Image -> "[image]"
                BlockType.Audio -> "[audio recording]"
                BlockType.Drawing -> "[drawing]"
                BlockType.Table -> tableMarkdown(b.rows)
            }
            if (sb.isNotEmpty()) sb.append(if (prev?.isList == true && b.type.isList) "\n" else "\n\n")
            sb.append(piece)
            prev = b.type
        }
        return sb.toString()
    }

    private fun pad(b: NoteBlock): String = "  ".repeat(b.indent)

    private fun tableMarkdown(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val cols = rows.maxOf { it.size }.coerceAtLeast(1)
        fun line(r: List<String>) = "| " + List(cols) { c -> r.getOrElse(c) { "" }.replace("|", "\\|").replace("\n", " ").ifBlank { " " } }.joinToString(" | ") + " |"
        val sep = "|" + " --- |".repeat(cols)
        return (listOf(line(rows[0]), sep) + rows.drop(1).map(::line)).joinToString("\n")
    }

    private val numberRe = Regex("^\\s*\\d+[.)] ")
    private val todoRe = Regex("^\\s*[-*+] \\[([ xX])] ")
    private val bulletRe = Regex("^\\s*[-*+] ")
    private val headRe = Regex("^(#{1,3}) ")
    private val hrRe = Regex("^(-{3,}|\\*{3,}|_{3,})$")

    private fun depth(line: String): Int = (line.takeWhile { it == ' ' }.length / 2).coerceIn(0, MAX_INDENT)

    /** Notes saved as Markdown (older ones, or text pasted in) become blocks. */
    fun fromMarkdown(md: String): List<NoteBlock> {
        val out = ArrayList<NoteBlock>()
        val lines = md.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("```") -> {
                    val body = ArrayList<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) { body.add(lines[i]); i++ }
                    out.add(NoteBlock(type = BlockType.Code, text = body.joinToString("\n")))
                }
                line.isBlank() -> {}
                hrRe.matches(line.trim()) -> out.add(NoteBlock(type = BlockType.Divider))
                headRe.containsMatchIn(line) -> {
                    val m = headRe.find(line)!!
                    val type = when (m.groupValues[1].length) { 1 -> BlockType.H1; 2 -> BlockType.H2; else -> BlockType.H3 }
                    out.add(NoteBlock(type = type, text = line.removePrefix(m.value)))
                }
                todoRe.containsMatchIn(line) -> {
                    val m = todoRe.find(line)!!
                    out.add(NoteBlock(type = BlockType.Todo, checked = m.groupValues[1] != " ", text = line.removePrefix(m.value).trimStart(), indent = depth(line)))
                }
                bulletRe.containsMatchIn(line) -> out.add(NoteBlock(type = BlockType.Bullet, text = line.replaceFirst(bulletRe, ""), indent = depth(line)))
                numberRe.containsMatchIn(line) -> out.add(NoteBlock(type = BlockType.Number, text = line.replaceFirst(numberRe, ""), indent = depth(line)))
                line.startsWith("> ") -> out.add(NoteBlock(type = BlockType.Quote, text = line.removePrefix("> ")))
                line.trimStart().startsWith("|") -> {
                    val rows = ArrayList<List<String>>()
                    while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split('|').map { it.trim().replace("\\|", "|") }
                        if (!cells.all { c -> c.isNotEmpty() && c.all { ch -> ch == '-' || ch == ':' } }) rows.add(cells)
                        i++
                    }
                    i--
                    if (rows.isNotEmpty()) out.add(NoteBlock(type = BlockType.Table, rows = rows))
                }
                else -> out.add(NoteBlock(text = line))
            }
            i++
        }
        return if (out.isEmpty()) starter() else coalesceTextBlocks(out)
    }

    fun wordCount(blocks: List<NoteBlock>): Int =
        NoteFormatting.wordCount(toMarkdown(blocks.filter { it.type != BlockType.Code }))
}


/**
 * Starting points for a new note. Each one is a few real blocks, so the page opens already shaped like the thing it is for
 * and the writer fills in the gaps.
 */
enum class NoteTemplate(val label: String) {
    Blank("Blank"),
    Class("Class notes"),
    Tasks("To-do list"),
    Exam("Exam revision"),
    Week("Weekly plan"),
    Reading("Reading log"),
    Journal("Daily review"),
    Outline("Chapter outline"),
    Meeting("Meeting notes"),
    Cornell("Cornell notes"),
    Project("Project plan"),
    Vocab("Vocabulary");

    fun blocks(): List<NoteBlock> {
        fun t(type: BlockType, text: String = "", indent: Int = 0, open: Boolean = true) = NoteBlock(type = type, text = text, indent = indent, open = open)
        return when (this) {
            Blank -> NoteDoc.starter()
            Class -> listOf(
                t(BlockType.H2, "Topic"), t(BlockType.Text),
                t(BlockType.H2, "Key points"), t(BlockType.Bullet), t(BlockType.Bullet), t(BlockType.Bullet),
                t(BlockType.H2, "Questions"), t(BlockType.Todo),
                t(BlockType.H2, "Summary"), t(BlockType.Text),
            )
            Tasks -> listOf(
                t(BlockType.H2, "Today"), t(BlockType.Todo), t(BlockType.Todo), t(BlockType.Todo),
                t(BlockType.H2, "Later"), t(BlockType.Todo), t(BlockType.Todo),
            )
            Exam -> listOf(
                t(BlockType.Callout, "Exam date and paper"),
                t(BlockType.H2, "Chapters"),
                NoteBlock(type = BlockType.Table, rows = listOf(listOf("Subject", "Chapter", "Status"), listOf("", "", ""), listOf("", "", ""), listOf("", "", ""))),
                t(BlockType.H2, "Before the exam"), t(BlockType.Todo, "Past papers"), t(BlockType.Todo, "Formula sheet"), t(BlockType.Todo, "Sleep early"),
                t(BlockType.H2, "Weak spots"), t(BlockType.Bullet),
            )
            Week -> listOf(
                NoteBlock(
                    type = BlockType.Table,
                    rows = listOf(listOf("Day", "Plan")) + listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").map { listOf(it, "") }
                ),
                t(BlockType.H2, "This week"), t(BlockType.Todo), t(BlockType.Todo),
            )
            Reading -> listOf(
                NoteBlock(type = BlockType.Table, rows = listOf(listOf("Book", "Pages", "Done"), listOf("", "", ""), listOf("", "", ""))),
                t(BlockType.H2, "Lines worth keeping"), t(BlockType.Quote),
            )
            Journal -> listOf(
                t(BlockType.H2, "Learned today"), t(BlockType.Text),
                t(BlockType.H2, "Still confusing"), t(BlockType.Bullet),
                t(BlockType.H2, "Tomorrow"), t(BlockType.Todo), t(BlockType.Todo),
            )
            Outline -> listOf(
                t(BlockType.Toggle, "Chapter 1", open = true),
                t(BlockType.Bullet, "Main idea", indent = 1), t(BlockType.Bullet, "Definitions", indent = 1), t(BlockType.Bullet, "Examples", indent = 1),
                t(BlockType.Toggle, "Chapter 2", open = true),
                t(BlockType.Bullet, "Main idea", indent = 1), t(BlockType.Bullet, "Definitions", indent = 1),
                t(BlockType.Toggle, "Chapter 3", open = false),
                t(BlockType.Bullet, "Main idea", indent = 1),
            )
            Meeting -> listOf(
                t(BlockType.Callout, "Date, time and who is there"),
                t(BlockType.H2, "Agenda"), t(BlockType.Number), t(BlockType.Number), t(BlockType.Number),
                t(BlockType.H2, "Decisions"), t(BlockType.Bullet),
                t(BlockType.H2, "Action items"), t(BlockType.Todo), t(BlockType.Todo), t(BlockType.Todo),
            )
            Cornell -> listOf(
                t(BlockType.H2, "Topic"), t(BlockType.Text),
                NoteBlock(type = BlockType.Table, rows = listOf(listOf("Cue", "Notes"), listOf("", ""), listOf("", ""), listOf("", ""), listOf("", ""))),
                t(BlockType.H2, "Summary"), t(BlockType.Quote),
            )
            Project -> listOf(
                t(BlockType.Callout, "Goal and deadline"),
                t(BlockType.H2, "Steps"), t(BlockType.Todo), t(BlockType.Todo), t(BlockType.Todo), t(BlockType.Todo),
                t(BlockType.H2, "Resources"), t(BlockType.Bullet),
                t(BlockType.H2, "Blocked by"), t(BlockType.Bullet),
            )
            Vocab -> listOf(
                NoteBlock(type = BlockType.Table, rows = listOf(listOf("Word", "Meaning", "Example"), listOf("", "", ""), listOf("", "", ""), listOf("", "", ""), listOf("", "", ""))),
                t(BlockType.H2, "Hard ones"), t(BlockType.Bullet),
            )
        }
    }

    companion object {
        /** The template the notes list picked, read once by the editor that opens next. */
        @Volatile var pending: NoteTemplate? = null

        fun take(): NoteTemplate? = pending.also { pending = null }
    }
}
