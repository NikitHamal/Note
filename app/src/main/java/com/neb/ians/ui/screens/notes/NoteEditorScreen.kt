@file:OptIn(ExperimentalLayoutApi::class)

package com.neb.ians.ui.screens.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neb.ians.speech.DictationState
import com.neb.ians.speech.ModelState
import com.neb.ians.speech.ReadState
import com.neb.ians.ui.components.NebToastKind
import com.neb.ians.ui.components.nebToast
import com.neb.ians.ui.components.shareText
import com.neb.ians.util.TactileType
import com.neb.ians.util.rememberTactileFeedback
import kotlin.math.PI
import kotlin.math.sin

private enum class Setup { Dictate, Listen }

@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenVoice: () -> Unit,
    vm: NoteEditorViewModel = hiltViewModel(),
    speech: SpeechViewModel = hiltViewModel(),
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()

    val ready by vm.ready.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val registry = remember { BlockFieldRegistry() }
    val toneIndex by vm.tone.collectAsStateWithLifecycle()
    val iconKey by vm.icon.collectAsStateWithLifecycle()
    val coverIndex by vm.cover.collectAsStateWithLifecycle()
    val coverPhoto by vm.coverPhoto.collectAsStateWithLifecycle()
    val paperKind by vm.paper.collectAsStateWithLifecycle()
    val bgName by vm.bgPhoto.collectAsStateWithLifecycle()
    val backdrop by rememberBackdrop(if (bgName.isEmpty()) null else vm.coverFile(bgName))
    val bgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.setBgPhoto(uri) }
    val tags by vm.tags.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val dictation by vm.dictation.collectAsStateWithLifecycle()
    val readState by vm.readState.collectAsStateWithLifecycle()
    val sttState by speech.stt.collectAsStateWithLifecycle()
    val ttsState by speech.tts.collectAsStateWithLifecycle()

    val tone = noteTone(toneIndex)
    val bg = if (toneIndex == 0) c.surface else tone.container
    val fg = readableOn(if (toneIndex == 0) c.onSurface else tone.content, bg, 7f)

    var setup by remember { mutableStateOf<Setup?>(null) }
    var menu by remember { mutableStateOf(false) }
    var toneSheet by remember { mutableStateOf(false) }
    var barOpen by rememberSaveable { mutableStateOf(false) }
    var iconSheet by remember { mutableStateOf(false) }
    var coverSheet by remember { mutableStateOf(false) }
    var tagSheet by remember { mutableStateOf(false) }
    var folderSheet by remember { mutableStateOf(false) }
    var exportSheet by remember { mutableStateOf(false) }
    var insertSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    val listening = dictation is DictationState.Listening || dictation is DictationState.Preparing
    val reading = readState is ReadState.Speaking || readState is ReadState.Paused || readState is ReadState.Loading

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startDictation()
    }

    fun beginDictation() {
        when {
            sttState !is ModelState.Installed -> setup = Setup.Dictate
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                vm.startDictation()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun beginListening() {
        if (ttsState !is ModelState.Installed) setup = Setup.Listen else vm.listen()
    }

    fun leave() {
        vm.flush()
        onNavigateBack()
    }

    BackHandler { leave() }
    DisposableEffect(Unit) { onDispose { vm.flush() } }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        if (vm.startDictating) beginDictation()
        else if (vm.isNew) runCatching { titleFocus.requestFocus() }
    }

    val bodyScroll = rememberScrollState()
    val density = LocalDensity.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val stripAfter = with(density) { (if (coverIndex != 0) 112.dp else 0.dp).toPx() }
    val inserter = rememberBlockInserter(vm, registry)
    val anySheetOpen = menu || folderSheet || exportSheet || insertSheet || iconSheet || coverSheet || toneSheet || setup != null || tagSheet || confirmDelete
    val rawImeOpen = WindowInsets.isImeVisible
    // Keep sheetImeActive true while any sheet is open AND until a sheet-opened keyboard finishes closing
    var sheetImeActive by remember { mutableStateOf(false) }
    if (anySheetOpen) {
        sheetImeActive = true
    } else if (!rawImeOpen) {
        sheetImeActive = false
    }
    val editorImeOpen = rawImeOpen && !sheetImeActive
    var collapsedFromFormatBar by remember { mutableStateOf(false) }
    val surfaceTile = if (toneIndex == 0) noteSurfaceTileColor() else fg.copy(alpha = 0.10f)
    val raisedTile = if (toneIndex == 0) noteRaisedTileColor() else fg.copy(alpha = 0.16f)
    val reorder = remember { BlockReorder() }
    reorder.scroller = bodyScroll

    // statusBarsPadding() on the root Box guarantees the cover banner and scrolling note never overlap the status bar.
    // imePadding() is applied only to the scrollable note body (when no modal sheet is active) so the bottom dock stays anchored.
    Box(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .then(if (sheetImeActive) Modifier else Modifier.imePadding())
                    .onGloballyPositioned {
                        val at = it.positionInWindow().y
                        reorder.viewTop = at
                        reorder.viewBottom = at + it.size.height
                    }
                    .noteBackdrop({ backdrop }, bg.copy(alpha = 0.78f))
                    .verticalScroll(bodyScroll)
                    .notePaper(paperKind, fg),
            ) {
                NotePageHeader(
                    icon = iconKey, cover = coverIndex, photo = if (coverPhoto.isEmpty()) null else vm.coverFile(coverPhoto),
                    bg = bg, fg = fg, surfaceTile = surfaceTile, raisedTile = raisedTile,
                    editable = !trashed, tags = tags,
                    onIcon = { focusManager.clearFocus(); iconSheet = true },
                    onCover = { focusManager.clearFocus(); coverSheet = true },
                    onClearCover = { vm.setCover(0) },
                    onAddTag = { focusManager.clearFocus(); tagSheet = true },
                    onRemoveTag = { vm.removeTag(it) },
                ) { titleModifier ->
                    NoteEditorTitleSlot(vm = vm, readOnly = trashed, fg = fg, bg = bg, cursorColor = c.primary, titleFocus = titleFocus, modifier = titleModifier)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    NoteEditorBlocksSlot(vm = vm, registry = registry, readOnly = trashed, fg = fg, reorder = reorder)
                    Spacer(Modifier.height(40.dp))
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(reading, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { ReadBar(vm) }
                (readState as? ReadState.Error)?.let { ErrorBar(it.message, vm::clearReadError) }
                AnimatedVisibility(
                    listening || dictation is DictationState.Finishing || dictation is DictationState.Error,
                    enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
                ) { DictationStrip(vm) }
                notice?.let { ErrorBar(it, vm::clearNotice) }
                // Hide the bottom toolbar when the editor software keyboard is open, and fade it in-place when a sheet is open
                if (!trashed && !editorImeOpen) {
                    val onMic: () -> Unit = {
                        if (!anySheetOpen) {
                            tactile.perform(TactileType.LightTap)
                            if (listening) vm.stopDictation() else beginDictation()
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .padding(start = if (barOpen) 12.dp else 16.dp, end = if (barOpen) 12.dp else 16.dp, top = 8.dp, bottom = 16.dp)
                            .then(
                                if (anySheetOpen) Modifier.drawBehind { } else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (!anySheetOpen) {
                                if (barOpen) {
                                    NoteEditorFormatBarSlot(
                                        vm = vm, registry = registry, inserter = inserter,
                                        fg = fg, surfaceTile = surfaceTile, raisedTile = raisedTile,
                                        onCollapse = {
                                            tactile.perform(TactileType.LightTap)
                                            collapsedFromFormatBar = true
                                            barOpen = false
                                        },
                                        onInsert = { focusManager.clearFocus(); insertSheet = true },
                                    )
                                } else {
                                    NoteDockLeft(
                                        onFormat = {
                                            tactile.perform(TactileType.LightTap)
                                            collapsedFromFormatBar = false
                                            barOpen = true
                                        },
                                        onInsert = { focusManager.clearFocus(); insertSheet = true },
                                        container = raisedTile, content = fg,
                                        animateEntrance = collapsedFromFormatBar,
                                    )
                                }
                            }
                        }
                        if (!anySheetOpen) {
                            Spacer(Modifier.width(6.dp))
                            MicPill(
                                listening = listening,
                                level = vm.level,
                                onClick = onMic,
                                width = if (barOpen) 44.dp else 72.dp,
                                height = if (barOpen) 44.dp else 56.dp,
                                restRadius = if (barOpen) 16.dp else 20.dp,
                            )
                        }
                    }
                }
            }
        }
        val topBarTile = if (coverIndex != 0) bg.copy(alpha = 0.72f) else surfaceTile
        NoteTopBar(
            container = topBarTile, content = fg, canUndo = canUndo, canRedo = canRedo, pinned = pinned, trashed = trashed,
            onBack = { leave() }, onUndo = vm::undo, onRedo = vm::redo, onPin = vm::togglePin, onMore = { menu = true },
            modifier = Modifier.align(Alignment.TopCenter)
                .drawBehind { drawRect(bg, alpha = ((bodyScroll.value - stripAfter) / 24f).coerceIn(0f, 1f)) },
        )
    }

    if (menu) {
        val share = { vm.buildShareText { text -> shareText(context, text) } }
        val read = NoteMenuAction(if (reading) Icons.Outlined.Stop else Icons.Outlined.VolumeUp, if (reading) "Stop" else "Read aloud") {
            if (reading) vm.readStop() else beginListening()
        }
        val send = NoteMenuAction(Icons.Outlined.Share, "Share") { share() }
        if (trashed) {
            NoteMenuSheet(
                quick = listOf(read, send),
                rows = listOf(NoteMenuAction(Icons.Outlined.RestoreFromTrash, "Restore") { vm.restoreFromBin() }),
                danger = NoteMenuAction(Icons.Outlined.DeleteForever, "Delete forever") { confirmDelete = true },
                onDismiss = { menu = false },
            )
        } else {
            NoteMenuSheet(
                quick = listOf(read, NoteMenuAction(Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin") { vm.togglePin() }, send),
                rows = listOf(
                    NoteMenuAction(Icons.Outlined.Folder, "Move to folder") { folderSheet = true },
                    NoteMenuAction(Icons.Outlined.Download, "Export") { exportSheet = true },
                    NoteMenuAction(Icons.Outlined.Palette, "Appearance") { toneSheet = true },
                    NoteMenuAction(Icons.Outlined.FileCopy, "Duplicate") { vm.duplicate(onOpenNote) },
                    NoteMenuAction(Icons.Outlined.GraphicEq, "Voice settings") { onOpenVoice() },
                ),
                danger = NoteMenuAction(Icons.Outlined.Delete, "Delete note") { vm.moveToBin(onNavigateBack) },
                onDismiss = { menu = false },
            )
        }
    }
    if (folderSheet) {
        NoteFolderSheet(
            currentTags = tags,
            archived = archived,
            onMoveToFolder = { folder ->
                vm.moveToFolder(folder)
                context.nebToast("Moved to $folder", kind = NebToastKind.Success)
            },
            onToggleArchive = {
                val willArchive = !archived
                vm.toggleArchive()
                if (willArchive) {
                    context.nebToast("Moved to Archive", kind = NebToastKind.Info)
                    leave()
                }
            },
            onDismiss = { folderSheet = false },
        )
    }
    if (exportSheet) {
        val currentTitle by vm.title.collectAsStateWithLifecycle()
        NoteExportSheet(
            onSaveMarkdown = {
                vm.buildShareText { text -> exportNoteToDownloads(context, currentTitle, text, "md", "text/markdown") }
            },
            onSaveText = {
                vm.buildShareText { text -> exportNoteToDownloads(context, currentTitle, text, "txt", "text/plain") }
            },
            onCopyText = {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                vm.buildShareText { text ->
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Note", text))
                    context.nebToast("Copied to clipboard", kind = NebToastKind.Success)
                }
            },
            onShare = { vm.buildShareText { text -> shareText(context, text) } },
            onDismiss = { exportSheet = false },
        )
    }
    if (insertSheet) NoteInsertSheet(inserter) { insertSheet = false }

    if (iconSheet) {
        NoteIconSheet(current = iconKey, onPick = { vm.setIcon(it); iconSheet = false }, onDismiss = { iconSheet = false })
    }
    if (coverSheet) {
        NoteCoverSheet(
            current = coverIndex, onPick = { vm.setCover(it); coverSheet = false },
            onPhoto = { vm.setCoverPhoto(it); coverSheet = false }, onDismiss = { coverSheet = false },
        )
    }
    if (toneSheet) {
        NoteSheet(onDismiss = { toneSheet = false }, title = "Appearance", inset = 16.dp) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Themes", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                NoteThemeRow(toneIndex) { th -> vm.setTone(th.tone); vm.setCover(th.cover); vm.setIcon(th.icon); vm.setPaper(th.paper) }
                Text("Colour", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                ToneSwatches(current = toneIndex, onPick = { vm.setTone(it) })
                Text("Paper", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                PaperPicker(paperKind) { vm.setPaper(it) }
                Text("Page picture", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostChip(if (bgName.isEmpty()) "Add picture" else "Change picture", Icons.Outlined.Image, Modifier.weight(1f)) {
                        bgPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (bgName.isNotEmpty()) GhostChip("Remove", Icons.Outlined.Delete, Modifier.weight(1f)) { vm.setBgPhoto(null) }
                }
            }
        }
    }
    val pendingTranscribe by vm.pendingSetupTranscribe.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTranscribe) {
        if (pendingTranscribe != null) {
            setup = Setup.Dictate
        }
    }
    if (setup != null) {
        VoiceSetupSheet(
            forDictation = setup == Setup.Dictate,
            onReady = {
                val kind = setup
                setup = null
                val pending = vm.consumePendingSetupTranscribe()
                when {
                    pending != null -> vm.transcribeAudioBlock(pending.first, pending.second)
                    kind == Setup.Dictate -> beginDictation()
                    else -> vm.listen()
                }
            },
            onDismiss = {
                vm.consumePendingSetupTranscribe()
                setup = null
            },
            vm = speech,
        )
    }
    if (tagSheet) NoteTagSheet(existing = tags, onAdd = { vm.addTag(it); tagSheet = false }, onDismiss = { tagSheet = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp), containerColor = com.neb.ians.ui.theme.nebFloatColor(),
            tonalElevation = 0.dp,
            title = { Text("Delete forever?") },
            text = { Text("This note will be gone for good.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteForever(onNavigateBack) }) { Text("Delete", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun NoteEditorTitleSlot(
    vm: NoteEditorViewModel,
    readOnly: Boolean,
    fg: Color,
    bg: Color,
    cursorColor: Color,
    titleFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val title by vm.title.collectAsStateWithLifecycle()
    BasicTextField(
        value = title, onValueChange = vm::setTitle, readOnly = readOnly,
        textStyle = MaterialTheme.typography.headlineLarge.copy(color = fg, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(cursorColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { vm.focusFirst() }),
        decorationBox = { inner ->
            Box {
                if (title.isEmpty()) Text("Untitled", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = softReadable(fg, bg, 0.5f))
                inner()
            }
        },
        modifier = modifier.fillMaxWidth().focusRequester(titleFocus),
    )
}

private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun DictationStrip(vm: NoteEditorViewModel) {
    val c = MaterialTheme.colorScheme
    val state by vm.dictation.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val seconds by vm.seconds.collectAsStateWithLifecycle()
    if (state is DictationState.Error) {
        ErrorBar((state as DictationState.Error).message, vm::clearDictationError)
        return
    }
    Surface(
        shape = RoundedCornerShape(30.dp), color = noteSurfaceTileColor(), contentColor = c.onSurface,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state is DictationState.Listening) LevelBars(level, c.primary) else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = c.primary)
                Text(
                    when (state) {
                        is DictationState.Preparing -> "Loading model"
                        is DictationState.Finishing -> "Finishing"
                        else -> "Listening"
                    },
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                if (state is DictationState.Listening) {
                    Text(clock(seconds), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = c.primary, maxLines = 1)
                }
            }
            if (pending.isNotBlank()) {
                Text(pending, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LevelBars(level: Float, color: Color) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val smooth by animateFloatAsState(level, tween(120), label = "level")
    Canvas(Modifier.size(width = 48.dp, height = 26.dp)) {
        val n = 7
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val wave = 0.5f + 0.5f * sin(phase + i * 0.9f)
            val h = size.height * (0.22f + 0.78f * (smooth.coerceIn(0.18f, 1f) * wave))
            drawRoundRect(
                color, topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                size = Size(w, h), cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}

@Composable
private fun ReadBar(vm: NoteEditorViewModel) {
    val c = MaterialTheme.colorScheme
    val state by vm.readState.collectAsStateWithLifecycle()
    val progress by vm.readProgress.collectAsStateWithLifecycle()
    Surface(
        shape = RoundedCornerShape(30.dp), color = noteSurfaceTileColor(), contentColor = c.onSurface,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            val p = progress
            if (p != null) {
                LinearProgressIndicator(
                    progress = { (p.index + 1f) / p.total },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = c.primary, trackColor = noteRaisedTileColor(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state is ReadState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    if (state is ReadState.Loading) "Loading voice" else p?.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                IconButton(onClick = vm::readPrevious) { Icon(Icons.Outlined.SkipPrevious, "Previous") }
                Surface(
                    onClick = vm::readPause, shape = RoundedCornerShape(22.dp), color = c.primary, contentColor = c.onPrimary,
                    tonalElevation = 0.dp, shadowElevation = 0.dp,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (state is ReadState.Paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (state is ReadState.Paused) "Resume" else "Pause")
                    }
                }
                IconButton(onClick = vm::readNext) { Icon(Icons.Outlined.SkipNext, "Next") }
                IconButton(onClick = vm::readStop) { Icon(Icons.Outlined.Close, "Stop") }
            }
        }
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp), color = c.errorContainer, contentColor = c.onErrorContainer,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss") }
        }
    }
}

@Composable
private fun NoteEditorBlocksSlot(
    vm: NoteEditorViewModel,
    registry: BlockFieldRegistry,
    readOnly: Boolean,
    fg: Color,
    reorder: BlockReorder,
) {
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val (todoTotal, todoDone) = remember(blocks) {
        blocks.count { it.type == BlockType.Todo } to blocks.count { it.type == BlockType.Todo && it.checked }
    }
    if (todoTotal > 0) {
        TodoProgress(todoDone, todoTotal, fg)
    }
    Spacer(Modifier.height(6.dp))
    NoteBlockEditor(blocks, vm, registry, readOnly = readOnly, fg = fg, modifier = Modifier.padding(top = 4.dp), reorder = reorder)
}

@Composable
private fun NoteEditorFormatBarSlot(
    vm: NoteEditorViewModel,
    registry: BlockFieldRegistry,
    inserter: BlockInserter,
    fg: Color,
    surfaceTile: Color,
    raisedTile: Color,
    onCollapse: () -> Unit,
    onInsert: () -> Unit,
) {
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    BlockFormatBar(
        blocks = blocks, vm = vm, registry = registry, inserter = inserter,
        fg = fg, surfaceTile = surfaceTile, raisedTile = raisedTile,
        onCollapse = onCollapse, onInsert = onInsert,
    )
}

@Composable
private fun GhostChip(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(noteRaisedTileColor()).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.onSurface, maxLines = 1)
    }
}

/** A bar and a count above the to-dos; the bar fills with a spring as items are ticked. */
@Composable
private fun TodoProgress(done: Int, total: Int, fg: Color) {
    val c = MaterialTheme.colorScheme
    val p by animateFloatAsState(
        if (total == 0) 0f else done.toFloat() / total,
        spring(dampingRatio = 0.7f, stiffness = 300f), label = "todo_progress",
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(fg.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.04f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.primary))
        }
        Text("$done of $total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.primary, maxLines = 1)
    }
}
