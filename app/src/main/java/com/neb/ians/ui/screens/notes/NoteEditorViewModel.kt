package com.neb.ians.ui.screens.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neb.ians.data.local.entity.NoteEntity
import com.neb.ians.data.repository.NotesRepository
import com.neb.ians.speech.DictationState
import com.neb.ians.speech.NoteSpeechText
import com.neb.ians.speech.PhononEngine
import com.neb.ians.speech.ReadProgress
import com.neb.ians.speech.ReadState
import com.neb.ians.speech.WhistleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: NotesRepository,
    private val whistle: WhistleEngine,
    private val phonon: PhononEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val noteId: String = handle.get<String>("noteId") ?: "new"
    val startDictating: Boolean = handle.get<Boolean>("dictate") ?: false

    private var note: NoteEntity? = null
    private var existsInDb = false
    private var saveJob: Job? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()
    private val _blocks = MutableStateFlow(NoteDoc.starter())
    val blocks: StateFlow<List<NoteBlock>> = _blocks.asStateFlow()
    /** The block that should take the keyboard next; the editor clears it once it has. */
    private val _focusRequest = MutableStateFlow<String?>(null)
    val focusRequest: StateFlow<String?> = _focusRequest.asStateFlow()
    private val _tone = MutableStateFlow(0)
    val tone: StateFlow<Int> = _tone.asStateFlow()
    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()
    private val _icon = MutableStateFlow("")
    val icon: StateFlow<String> = _icon.asStateFlow()
    private val _cover = MutableStateFlow(0)
    val cover: StateFlow<Int> = _cover.asStateFlow()
    private val _bgPhoto = MutableStateFlow("")
    val bgPhoto: StateFlow<String> = _bgPhoto.asStateFlow()
    private val _paper = MutableStateFlow(0)
    val paper: StateFlow<Int> = _paper.asStateFlow()
    private val _coverPhoto = MutableStateFlow("")
    val coverPhoto: StateFlow<String> = _coverPhoto.asStateFlow()
    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()
    private val _archived = MutableStateFlow(false)
    val archived: StateFlow<Boolean> = _archived.asStateFlow()
    private val _trashed = MutableStateFlow(false)
    val trashed: StateFlow<Boolean> = _trashed.asStateFlow()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val dictation: StateFlow<DictationState> = whistle.state
    val level: StateFlow<Float> = whistle.level
    val pending: StateFlow<String> = whistle.pending
    val seconds: StateFlow<Int> = whistle.seconds
    val readState: StateFlow<ReadState> = phonon.state
    val readProgress: StateFlow<ReadProgress?> = phonon.progress

    private val undoStack = ArrayDeque<List<NoteBlock>>()
    private val redoStack = ArrayDeque<List<NoteBlock>>()
    private var lastPushAt = 0L
    /** The block the caret is in; where dictation and new media land. */
    var focusedId: String? = null
        private set

    init {
        viewModelScope.launch {
            val existing = if (noteId == "new") null else repo.get(noteId)
            if (existing != null) {
                note = existing
                existsInDb = true
                _title.value = existing.title
                _blocks.value = withContext(Dispatchers.Default) {
                    if (existing.doc.isNotBlank()) NoteDoc.fromJson(existing.doc) else NoteDoc.fromMarkdown(existing.body)
                }
                _tone.value = existing.tone
                _icon.value = existing.icon
                _cover.value = existing.cover
                _coverPhoto.value = existing.coverPhoto
                _paper.value = existing.paper
                _bgPhoto.value = existing.bgPhoto
                _tags.value = existing.tagList
                _pinned.value = existing.pinned
                _archived.value = existing.archived
                _trashed.value = existing.isTrashed
            } else if (noteId == "new") {
                NoteTemplate.take()?.takeIf { it != NoteTemplate.Blank }?.let { _blocks.value = it.blocks() }
            }
            _ready.value = true
        }
        viewModelScope.launch {
            whistle.committed.collect { insertDictated(it) }
        }
    }

    val isNew: Boolean get() = noteId == "new"
    val currentId: String get() = (note ?: repo.blank().also { note = it }).id

    // ---- title and focus

    fun setTitle(t: String) {
        _title.value = t
        scheduleSave()
    }

    fun setFocused(id: String) { focusedId = id }

    /** True when the block being focused came from splitting a line, so the caret belongs at its start. */
    var caretAtStart = false
        private set
    var focusCaretOffset = -1
        private set
    var selectAllRequested = false
        private set

    /** A tap on the empty page under the last block keeps writing there. */
    fun tapBelow() {
        val last = _blocks.value.last()
        caretAtStart = false
        focusCaretOffset = -1
        selectAllRequested = false
        if (last.type == BlockType.Text) _focusRequest.value = last.id else insert(BlockType.Text, last.id)
    }

    fun focusFirst() {
        _blocks.value.firstOrNull { it.type.hasText }?.let {
            caretAtStart = false
            focusCaretOffset = -1
            selectAllRequested = false
            _focusRequest.value = it.id
        }
    }
    fun consumeFocus() {
        _focusRequest.value = null
        focusCaretOffset = -1
        selectAllRequested = false
    }
    fun clearNotice() { _notice.value = null }

    // ---- blocks

    private fun commit(next: List<NoteBlock>, force: Boolean) {
        val old = _blocks.value
        if (next == old) return
        pushHistory(old, force)
        _blocks.value = next
        scheduleSave()
    }

    private fun indexOf(id: String) = _blocks.value.indexOfFirst { it.id == id }

    /** Typing in a block. */
    fun setText(id: String, text: String) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0 || list[i].text == text) return
        commit(list.toMutableList().also { it[i] = list[i].copy(text = text) }, force = false)
    }

    fun update(id: String, force: Boolean = true, change: (NoteBlock) -> NoteBlock) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        commit(list.toMutableList().also { it[i] = change(list[i]) }, force)
    }

    /** Enter in the middle of a block: the text after the caret moves to a new block of the same kind. */
    fun split(id: String, before: String, after: List<String>) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        val cur = list[i]
        // Enter on an empty list item, quote or callout steps out of it
        if (cur.type != BlockType.Text && cur.type != BlockType.Code && cur.text.isBlank() && before.isBlank() && after.all { it.isBlank() } &&
            (cur.type.isList || cur.type == BlockType.Quote || cur.type == BlockType.Callout)) {
            // an empty item first steps back out one level, then out of the list
            if (cur.indent > 0) indent(id, -1)
            else update(id) { it.copy(type = BlockType.Text, checked = false) }
            _focusRequest.value = id
            return
        }
        val nextType = when (cur.type) {
            BlockType.H1, BlockType.H2, BlockType.H3, BlockType.Callout -> BlockType.Text
            BlockType.Toggle -> if (cur.open && before.isNotBlank() && after.size == 1 && after[0].isEmpty()) BlockType.Text else BlockType.Toggle
            else -> cur.type
        }
        // Enter at the end of an open toggle writes inside it; anywhere else the new line sits level with this one
        val intoToggle = cur.type == BlockType.Toggle && cur.open && nextType == BlockType.Text
        val nextIndent = if (intoToggle) (cur.indent + 1).coerceAtMost(NoteDoc.MAX_INDENT) else cur.indent
        val created = after.map { NoteBlock(type = nextType, text = it, indent = nextIndent, align = cur.align, tint = cur.tint, mark = cur.mark) }
        val out = list.toMutableList()
        out[i] = cur.copy(text = before)
        out.addAll(i + 1, created)
        commit(out, force = true)
        caretAtStart = created.size == 1
        created.lastOrNull()?.let { _focusRequest.value = it.id }
    }

    /** Backspace at the very start: a list or heading becomes plain text first, then the block joins the one above. */
    fun backspaceAtStart(id: String) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        val cur = list[i]
        if (cur.indent > 0) { indent(id, -1); return }
        if (cur.type != BlockType.Text) { update(id) { it.copy(type = BlockType.Text, checked = false) }; return }
        if (i == 0) return
        val prev = list[i - 1]
        if (prev.type.hasText) {
            val out = list.toMutableList()
            val sep = if (prev.type == BlockType.Text && cur.type == BlockType.Text && prev.text.isNotEmpty() && cur.text.isNotEmpty()) "\n" else ""
            val joinAt = prev.text.length + sep.length
            out[i - 1] = prev.copy(text = prev.text + sep + cur.text)
            out.removeAt(i)
            commit(out, force = true)
            caretAtStart = false
            focusCaretOffset = joinAt
            _focusRequest.value = prev.id
        } else if (cur.text.isBlank()) {
            val out = list.toMutableList()
            out.removeAt(i)
            commit(out, force = true)
        } else {
            _focusRequest.value = prev.id
        }
    }

    fun convert(id: String, type: BlockType) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        val cur = list[i]
        val nextBlock = when {
            cur.type == type && type != BlockType.Text -> cur.copy(type = BlockType.Text, checked = false)
            !type.hasText -> cur
            else -> cur.copy(type = type, checked = if (type == BlockType.Todo) cur.checked else false)
        }
        val out = list.toMutableList().also { it[i] = nextBlock }
        val coalesced = if (nextBlock.type == BlockType.Text) NoteDoc.coalesceTextBlocks(out) else out
        commit(coalesced, force = true)
        val focusId = if (coalesced.any { it.id == id }) id else coalesced.getOrNull(i.coerceAtMost(coalesced.lastIndex))?.id
        if (focusId != null) _focusRequest.value = focusId
    }

    /**
     * Converts only the active caret line inside a multi-line [BlockType.Text] block into [type]
     * (keeping lines above and below as [BlockType.Text]), or converts the whole block when single-line.
     */
    fun convertLineOrBlock(id: String, state: com.neb.ians.ui.components.RichState?, type: BlockType) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        val cur = list[i]
        val isSingleLineTarget = type == BlockType.H1 || type == BlockType.H2 || type == BlockType.H3 || type.isList
        if (cur.type != BlockType.Text || !isSingleLineTarget || !cur.text.contains('\n') || state == null) {
            convert(id, type)
            return
        }
        val mdLines = cur.text.split('\n')
        if (mdLines.size <= 1) {
            convert(id, type)
            return
        }
        val plain = state.value.text
        val caret = state.value.selection.start.coerceIn(0, plain.length)
        val lineIdx = plain.substring(0, caret).count { it == '\n' }.coerceIn(0, mdLines.lastIndex)
        val beforeMd = mdLines.subList(0, lineIdx).joinToString("\n")
        val targetLineMd = mdLines[lineIdx]
        val afterMd = mdLines.subList(lineIdx + 1, mdLines.size).joinToString("\n")
        val out = list.toMutableList()
        val converted = NoteBlock(
            type = type,
            text = targetLineMd,
            indent = cur.indent,
            align = cur.align,
            tint = cur.tint,
            mark = cur.mark,
        )
        var insertAt = i
        if (beforeMd.isNotEmpty()) {
            out[i] = cur.copy(text = beforeMd)
            insertAt = i + 1
            out.add(insertAt, converted)
        } else {
            out[i] = converted
            insertAt = i
        }
        if (afterMd.isNotEmpty()) {
            val tail = NoteBlock(type = BlockType.Text, text = afterMd, indent = cur.indent, align = cur.align, tint = cur.tint, mark = cur.mark)
            out.add(insertAt + 1, tail)
        }
        commit(out, force = true)
        caretAtStart = false
        focusCaretOffset = targetLineMd.length
        _focusRequest.value = converted.id
    }

    /** Slash menu: the typed "/word" is cleared and the block turns into [type] (or a media block is added below). */
    fun applySlash(id: String, type: BlockType) {
        update(id) { it.copy(text = "") }
        if (type.hasText) convert(id, type) else insert(type, afterId = id, replaceBlankId = id)
    }

    /**
     * Slash menu triggered on a line inside a multi-line [BlockType.Text] block:
     * keeps [beforeMd] above, creates the new [type] block, and places [afterMd] below if non-empty.
     */
    fun applySlashOnLine(id: String, beforeMd: String, afterMd: String, type: BlockType) {
        if (beforeMd.isEmpty() && afterMd.isEmpty()) {
            applySlash(id, type)
            return
        }
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        val cur = list[i]
        val out = list.toMutableList()
        val created = NoteBlock(type = type, indent = cur.indent, align = cur.align, tint = cur.tint, mark = cur.mark)
        var insertAt = i
        if (beforeMd.isNotEmpty()) {
            out[i] = cur.copy(text = beforeMd)
            insertAt = i + 1
            out.add(insertAt, created)
        } else {
            out[i] = created
            insertAt = i
        }
        if (afterMd.isNotEmpty()) {
            val tail = NoteBlock(type = BlockType.Text, text = afterMd, indent = cur.indent, align = cur.align, tint = cur.tint, mark = cur.mark)
            out.add(insertAt + 1, tail)
        }
        commit(out, force = true)
        caretAtStart = true
        focusCaretOffset = 0
        if (created.type.hasText) _focusRequest.value = created.id
    }

    /**
     * Coalesces consecutive plain text blocks and selects all text in the focused (or primary) text block.
     * When [wholeNote] is true (or when the current block's text is already fully selected and other text blocks exist),
     * merges all consecutive text-bearing blocks (pushing an Undo snapshot) so the entire note text is selected at once.
     */
    fun selectAllText(registry: BlockFieldRegistry, wholeNote: Boolean = false) {
        var merged = NoteDoc.coalesceTextBlocks(_blocks.value)
        val initialId = focusedId?.takeIf { id -> merged.any { it.id == id && it.type.hasText } }
            ?: merged.maxByOrNull { if (it.type.hasText) it.text.length else -1 }?.takeIf { it.type.hasText }?.id
        val initialBlock = initialId?.let { id -> merged.firstOrNull { it.id == id } }
        val initialState = initialBlock?.let { registry.stateOf(this, it) }
        val alreadySelectedBlock = initialState != null &&
            initialState.doc.text.isNotEmpty() &&
            initialState.value.selection.start == 0 &&
            initialState.value.selection.end == initialState.doc.text.length
        val textBlockCount = merged.count { it.type.hasText && it.type != BlockType.Code && it.text.isNotBlank() }
        if ((wholeNote || alreadySelectedBlock) && textBlockCount > 1) {
            merged = NoteDoc.coalesceAllTextForSelection(merged)
            commit(merged, force = true)
        } else if (merged != _blocks.value) {
            commit(merged, force = false)
        }
        val targetId = focusedId?.takeIf { id -> merged.any { it.id == id && it.type.hasText } }
            ?: merged.maxByOrNull { if (it.type.hasText) it.text.length else -1 }?.takeIf { it.type.hasText }?.id
            ?: return
        val targetBlock = merged.firstOrNull { it.id == targetId } ?: return
        val st = registry.stateOf(this, targetBlock)
        st.adopt(targetBlock.text, targetBlock.text.length)
        st.selectAll()
        selectAllRequested = true
        _focusRequest.value = null
        _focusRequest.value = targetId
    }

    fun toggleTodo(id: String) = update(id) { it.copy(checked = !it.checked) }

    /** Sets or clears (0) the due day of a to-do. */
    fun setDue(id: String, day: Long) = update(id) { it.copy(due = day) }

    /** Text alignment (0 start, 1 centre, 2 end), text colour and highlight of a text block; -1 keeps a value. */
    fun setLook(id: String, align: Int = -1, tint: Int = -1, mark: Int = -1) = update(id) {
        it.copy(
            align = if (align >= 0) align.coerceIn(0, 2) else it.align,
            tint = if (tint >= 0) tint else it.tint,
            mark = if (mark >= 0) mark else it.mark,
        )
    }

    /** Folds or unfolds a toggle block; what sits under it hides with it. */
    /** Removes every ticked to-do. */
    fun clearDone() {
        val out = _blocks.value.filterNot { it.type == BlockType.Todo && it.checked }.ifEmpty { listOf(NoteBlock()) }
        commit(out, force = true)
    }

    fun toggleOpen(id: String) = update(id) { it.copy(open = !it.open) }

    /**
     * Moves a block one level in or out. A block can sit at most one level deeper than the one above it, and the blocks
     * nested under it travel with it so a toggle keeps its contents.
     */
    fun indent(id: String, delta: Int) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0 || !list[i].type.hasText) return
        val cur = list[i]
        val ceiling = if (i == 0) 0 else (list[i - 1].indent + 1).coerceAtMost(NoteDoc.MAX_INDENT)
        val target = (cur.indent + delta).coerceIn(0, ceiling)
        val shift = target - cur.indent
        if (shift == 0) return
        val kids = NoteDoc.childCount(list, i)
        val out = list.toMutableList()
        for (j in i..i + kids) out[j] = out[j].copy(indent = (out[j].indent + shift).coerceIn(0, NoteDoc.MAX_INDENT))
        commit(out, force = true)
    }

    fun delete(id: String) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        val out = list.toMutableList().also { it.removeAt(i) }
        if (out.isEmpty()) out.add(NoteBlock())
        commit(out, force = true)
        out.getOrNull((i - 1).coerceAtLeast(0))?.takeIf { it.type.hasText }?.let { _focusRequest.value = it.id }
    }

    fun move(id: String, delta: Int) {
        val list = _blocks.value
        val i = indexOf(id)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val out = list.toMutableList()
        out.add(j, out.removeAt(i))
        commit(out, force = true)
    }

    /**
     * Drag and drop: [id] and everything nested under it land where [overId] is. Dropped on a block further down it
     * goes after it, dropped on one further up it goes before it. A drop inside its own group changes nothing.
     */
    fun moveBlock(id: String, overId: String) {
        val list = _blocks.value
        val from = indexOf(id)
        val over = indexOf(overId)
        if (from < 0 || over < 0 || from == over) return
        val size = 1 + NoteDoc.childCount(list, from)
        if (over in from until from + size) return
        val group = list.subList(from, from + size).toList()
        val rest = list.toMutableList().also { for (k in 0 until size) it.removeAt(from) }
        val anchor = rest.indexOfFirst { it.id == overId }
        if (anchor < 0) return
        val at = if (over > from) anchor + 1 else anchor
        rest.addAll(at, group)
        commit(rest, force = true)
    }

    fun duplicate(id: String) {
        val list = _blocks.value
        val i = indexOf(id)
        if (i < 0) return
        val copy = list[i].copy(id = UUID.randomUUID().toString())
        commit(list.toMutableList().also { it.add(i + 1, copy) }, force = true)
    }

    /** Adds [block] after [afterId] (or after the focused block, or at the end). A blank text block it lands on is replaced. */
    fun insert(type: BlockType, afterId: String? = focusedId, block: NoteBlock = NoteBlock(type = type), replaceBlankId: String? = null) {
        val list = _blocks.value
        val at = afterId?.let { indexOf(it) } ?: -1
        val out = list.toMutableList()
        val target = replaceBlankId ?: afterId
        val ti = target?.let { indexOf(it) } ?: -1
        if (ti >= 0 && out[ti].type.hasText && out[ti].text.isBlank() && (replaceBlankId != null || out[ti].type == BlockType.Text)) {
            out[ti] = block
        } else {
            out.add(if (at >= 0) at + 1 else out.size, block)
        }
        // keep a text block under media so there is always somewhere to keep writing
        val bi = out.indexOfFirst { it.id == block.id }
        if (!block.type.hasText && (bi == out.lastIndex)) out.add(NoteBlock())
        commit(out, force = true)
        val focus = if (block.type.hasText) block.id else out.getOrNull(bi + 1)?.takeIf { it.type.hasText }?.id
        if (focus != null) _focusRequest.value = focus
    }

    fun setTableCell(id: String, row: Int, col: Int, text: String) {
        update(id, force = false) { b ->
            val rows = b.rows.map { it.toMutableList() }.toMutableList()
            while (rows.size <= row) rows.add(MutableList(rows.firstOrNull()?.size ?: 2) { "" })
            while (rows[row].size <= col) rows[row].add("")
            rows[row][col] = text
            b.copy(rows = rows)
        }
    }

    fun tableShape(id: String, addRow: Int = 0, addCol: Int = 0) {
        update(id) { b ->
            var rows = b.rows.map { it.toList() }
            val cols = rows.maxOfOrNull { it.size } ?: 2
            if (addRow > 0) rows = rows + listOf(List(cols) { "" })
            if (addRow < 0 && rows.size > 1) rows = rows.dropLast(1)
            if (addCol > 0) rows = rows.map { it + "" }
            if (addCol < 0 && cols > 1) rows = rows.map { it.dropLast(1) }
            b.copy(rows = rows)
        }
    }

    fun newTable(afterId: String? = focusedId) = insert(
        BlockType.Table, afterId,
        NoteBlock(type = BlockType.Table, rows = List(3) { r -> List(3) { if (r == 0) "" else "" } })
    )

    fun addStroke(id: String, stroke: Stroke) = update(id) { it.copy(strokes = it.strokes + stroke) }
    fun undoStroke(id: String) = update(id) { it.copy(strokes = it.strokes.dropLast(1)) }
    fun clearStrokes(id: String) = update(id) { it.copy(strokes = emptyList()) }
    fun eraseStrokes(id: String, strokes: List<Stroke>) = update(id) { it.copy(strokes = strokes) }

    // ---- media

    private fun mediaDir(): File = File(context.filesDir, "notes/$currentId").also { it.mkdirs() }

    fun reportNotice(message: String) { _notice.value = message }

    /** Copies a picked photo into the note (scaled down so a note stays light) and puts it in the empty photo block. */
    fun fillImage(id: String, uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { copyImage(uri) }.getOrNull() }
            if (file == null) _notice.value = "That photo could not be added"
            else update(id) { it.copy(path = file.absolutePath, size = 1f) }
        }
    }

    private fun copyImage(uri: Uri): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("unreadable")
        val out = File(mediaDir(), "img_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return out
    }

    fun newAudioFile(): File = File(mediaDir(), "audio_${System.currentTimeMillis()}.m4a")

    fun fillAudio(id: String, file: File, seconds: Int) {
        viewModelScope.launch {
            val valid = withContext(Dispatchers.IO) { file.exists() && file.length() > 0L }
            if (!valid) { _notice.value = "Nothing was recorded"; return@launch }
            update(id) { it.copy(path = file.absolutePath, seconds = seconds) }
        }
    }

    fun fillAudioFromUri(id: String, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val out = File(mediaDir(), "audio_${System.currentTimeMillis()}.m4a")
                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: error("unreadable")
                    val retriever = MediaMetadataRetriever()
                    val secs = try {
                        retriever.setDataSource(out.absolutePath)
                        ((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L).toInt()
                    } finally { runCatching { retriever.release() } }
                    out to secs
                }.getOrNull()
            }
            if (result == null) _notice.value = "That audio could not be added" else fillAudio(id, result.first, result.second)
        }
    }

    // ---- history

    private fun pushHistory(old: List<NoteBlock>, force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || undoStack.isEmpty() || now - lastPushAt > 700) {
            undoStack.addLast(old)
            if (undoStack.size > 120) undoStack.removeFirst()
            redoStack.clear()
            lastPushAt = now
        }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_blocks.value)
        _blocks.value = prev
        lastPushAt = 0
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
        scheduleSave()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_blocks.value)
        _blocks.value = next
        lastPushAt = 0
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
        scheduleSave()
    }

    // ---- meta

    fun setTone(t: Int) {
        _tone.value = t
        scheduleSave()
    }

    fun setPaper(kind: Int) {
        _paper.value = kind
        scheduleSave()
    }

    fun setIcon(key: String) {
        _icon.value = key
        scheduleSave()
    }

    fun setCover(index: Int) {
        if (index != -1) {
            dropCoverFile()
            _coverPhoto.value = ""
        }
        _cover.value = index
        scheduleSave()
    }

    /** A gallery picture as the cover: scaled down, kept inside the note's own folder. */
    fun setCoverPhoto(uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { copyCover(uri) }.getOrNull() }
            if (name == null) {
                _notice.value = "That photo could not be added"
                return@launch
            }
            dropCoverFile()
            _coverPhoto.value = name
            _cover.value = -1
            scheduleSave()
        }
    }

    /** A gallery picture behind the whole page; empty [uri] clears it. */
    fun setBgPhoto(uri: Uri?) {
        if (uri == null) {
            dropBgFile()
            _bgPhoto.value = ""
            scheduleSave()
            return
        }
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { runCatching { copyCover(uri, "bg_") }.getOrNull() }
            if (name == null) {
                _notice.value = "That photo could not be added"
                return@launch
            }
            dropBgFile()
            _bgPhoto.value = name
            scheduleSave()
        }
    }

    private fun dropBgFile() {
        val n = _bgPhoto.value
        val id = currentId
        if (n.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(context.filesDir, "notes/$id/$n").delete() }
        }
    }

    fun coverFile(name: String): File = File(context.filesDir, "notes/$currentId/$name")

    private fun dropCoverFile() {
        val n = _coverPhoto.value
        val id = currentId
        if (n.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(context.filesDir, "notes/$id/$n").delete() }
        }
    }

    private fun copyCover(uri: Uri, prefix: String = "cover_"): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("unreadable")
        val name = "${prefix}${System.currentTimeMillis()}.jpg"
        File(mediaDir(), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        bmp.recycle()
        return name
    }

    fun togglePin() {
        _pinned.value = !_pinned.value
        if (_pinned.value) _archived.value = false
        scheduleSave()
    }

    fun toggleArchive() {
        _archived.value = !_archived.value
        if (_archived.value) _pinned.value = false
        scheduleSave()
    }

    private val tagCleanRegex = Regex("[^\\p{L}\\p{N}-]+")

    fun addTag(raw: String) {
        val tag = raw.trim().removePrefix("#").lowercase().replace(tagCleanRegex, "-").trim('-').take(24)
        if (tag.isNotEmpty() && tag !in _tags.value && _tags.value.size < 8) {
            _tags.value = _tags.value + tag
            scheduleSave()
        }
    }

    fun removeTag(tag: String) {
        _tags.value = _tags.value - tag
        scheduleSave()
    }

    fun moveToFolder(folder: String) {
        val clean = folder.trim().removePrefix("#").replace(Regex("\\s+"), "-")
        if (clean.isEmpty()) return
        val existing = _tags.value
        if (existing.any { it.equals(clean, ignoreCase = true) }) {
            _tags.value = listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) }
        } else {
            _tags.value = (listOf(clean) + existing).take(8)
        }
        scheduleSave()
    }

    val transcriptions: kotlinx.coroutines.flow.StateFlow<Map<String, com.neb.ians.speech.FileTranscriptionState>> = whistle.transcriptions
    private val _pendingSetupTranscribe = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)
    val pendingSetupTranscribe: kotlinx.coroutines.flow.StateFlow<Pair<String, String>?> = _pendingSetupTranscribe.asStateFlow()

    fun consumePendingSetupTranscribe(): Pair<String, String>? {
        val cur = _pendingSetupTranscribe.value
        _pendingSetupTranscribe.value = null
        return cur
    }

    fun cancelAudioTranscription(blockId: String) {
        whistle.cancelFileTranscription(blockId)
    }

    fun transcribeAudioBlock(blockId: String, filePath: String, onFinished: () -> Unit = {}) {
        if (!whistle.hasInstalledModel) {
            _pendingSetupTranscribe.value = blockId to filePath
            onFinished()
            return
        }
        val currentNoteId = note?.id
        whistle.startFileTranscription(
            blockId = blockId,
            filePath = filePath,
            onPartial = { partial ->
                if (partial.isNotBlank()) {
                    update(blockId, force = false) { it.copy(caption = partial) }
                }
            },
            onFinished = { result ->
                result.onSuccess { text ->
                    if (text.isNotBlank()) {
                        update(blockId) { it.copy(caption = text) }
                        if (currentNoteId != null) {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                runCatching {
                                    repo.get(currentNoteId)?.let { saved ->
                                        val updatedBlocks = NoteDoc.fromJson(saved.doc).map { b ->
                                            if (b.id == blockId) b.copy(caption = text) else b
                                        }
                                        repo.save(
                                            saved.copy(
                                                doc = NoteDoc.toJson(updatedBlocks),
                                                body = NoteDoc.toMarkdown(updatedBlocks),
                                            ),
                                            touch = true,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        _notice.value = "No speech detected in this recording"
                    }
                }.onFailure { err ->
                    _notice.value = err.message ?: "Audio transcription failed"
                }
                onFinished()
            }
        )
    }

    // ---- persistence

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            persist()
        }
    }

    /** Called when the screen leaves composition; lets pending edits finish saving. */
    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch(NonCancellable) { persist() }
    }

    private suspend fun persist() {
        if (!_ready.value) return
        val title = _title.value.trim()
        val blocks = _blocks.value
        val base = note ?: repo.blank()
        if (title.isBlank() && NoteDoc.isBlank(blocks)) {
            if (existsInDb) {
                repo.deleteForever(listOf(base.id))
                existsInDb = false
                note = null
            }
            return
        }
        val (body, doc) = withContext(Dispatchers.Default) {
            NoteDoc.toMarkdown(blocks) to NoteDoc.toJson(blocks)
        }
        val updated = base.copy(
            title = title,
            body = body,
            doc = doc,
            tone = _tone.value,
            icon = _icon.value,
            cover = _cover.value,
            coverPhoto = _coverPhoto.value,
            paper = _paper.value,
            bgPhoto = _bgPhoto.value,
            tags = _tags.value.joinToString(" "),
            pinned = _pinned.value,
            archived = _archived.value,
        )
        val textChanged = existsInDb && (base.title != updated.title || base.doc != updated.doc || base.tags != updated.tags || base.icon != updated.icon || base.cover != updated.cover || base.coverPhoto != updated.coverPhoto || base.paper != updated.paper || base.bgPhoto != updated.bgPhoto)
        repo.save(updated, touch = !existsInDb || textChanged)
        note = updated.copy(updatedAt = if (!existsInDb || textChanged) System.currentTimeMillis() else base.updatedAt)
        existsInDb = true
    }

    fun moveToBin(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch(NonCancellable) {
            persist()
            note?.let { repo.trash(listOf(it.id)) }
            onDone()
        }
    }

    fun restoreFromBin() {
        viewModelScope.launch {
            note?.let { repo.restore(listOf(it.id)) }
            _trashed.value = false
        }
    }

    fun deleteForever(onDone: () -> Unit) {
        saveJob?.cancel()
        viewModelScope.launch(NonCancellable) {
            note?.let {
                repo.deleteForever(listOf(it.id))
                withContext(Dispatchers.IO) { File(context.filesDir, "notes/${it.id}").deleteRecursively() }
            }
            existsInDb = false
            note = null
            _title.value = ""
            _blocks.value = NoteDoc.starter()
            onDone()
        }
    }

    fun duplicate(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            persist()
            note?.let { n ->
                repo.duplicate(n.id)?.let { newId ->
                    withContext(Dispatchers.IO) { copyMedia(n.id, newId) }
                    onCreated(newId)
                }
            }
        }
    }

    /** A copy of a note gets its own copies of the pictures and recordings, so deleting one never breaks the other. */
    private suspend fun copyMedia(from: String, to: String) {
        val src = File(context.filesDir, "notes/$from")
        if (!src.exists()) return
        runCatching { src.copyRecursively(File(context.filesDir, "notes/$to"), overwrite = true) }
        repo.get(to)?.let { d ->
            val fixed = d.doc.replace("/notes/$from/", "/notes/$to/")
            if (fixed != d.doc) repo.save(d.copy(doc = fixed), touch = false)
        }
    }

    fun markdown(): String = NoteDoc.toMarkdown(_blocks.value)

    fun shareText(): String {
        val t = _title.value.trim()
        val b = markdown().trim()
        return if (t.isEmpty()) b else if (b.isEmpty()) t else "$t\n\n$b"
    }

    fun buildShareText(onReady: (String) -> Unit) {
        val t = _title.value.trim()
        val snap = _blocks.value
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) {
                val b = NoteDoc.toMarkdown(snap).trim()
                if (t.isEmpty()) b else if (b.isEmpty()) t else "$t\n\n$b"
            }
            onReady(text)
        }
    }

    // ---- voice

    fun startDictation() {
        phonon.stop()
        whistle.start()
    }

    fun stopDictation() = whistle.stop()
    fun clearDictationError() = whistle.clearError()

    /** Dictated words go to the end of the block the caret was last in (or the last text block). */
    private fun insertDictated(text: String) {
        val list = _blocks.value
        val i = focusedId?.let { indexOf(it) }?.takeIf { it >= 0 && list[it].type.hasText }
            ?: list.indexOfLast { it.type.hasText }
        if (i < 0) {
            insert(BlockType.Text, null, NoteBlock(text = text.trim()))
            return
        }
        val cur = list[i]
        val lead = if (cur.text.isNotEmpty() && !cur.text.last().isWhitespace()) " " else ""
        val out = list.toMutableList()
        out[i] = cur.copy(text = cur.text + lead + text)
        commit(out, force = false)
    }

    fun listen() {
        whistle.cancel()
        val currentTitle = _title.value
        val currentBlocks = _blocks.value
        viewModelScope.launch {
            val segments = withContext(Dispatchers.Default) {
                NoteSpeechText.segments(currentTitle, NoteDoc.toMarkdown(currentBlocks))
            }
            if (segments.isEmpty()) {
                _notice.value = "Write something in the note first"
            } else {
                phonon.play(segments)
            }
        }
    }

    fun readPause() = phonon.toggle()
    fun readNext() = phonon.next()
    fun readPrevious() = phonon.previous()
    fun readStop() = phonon.stop()
    fun clearReadError() = phonon.clearError()

    override fun onCleared() {
        whistle.cancel()
        phonon.stop()
        super.onCleared()
    }
}
