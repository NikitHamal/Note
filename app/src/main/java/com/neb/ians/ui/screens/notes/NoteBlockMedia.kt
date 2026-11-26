@file:OptIn(ExperimentalMaterial3Api::class)

package com.neb.ians.ui.screens.notes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import coil.request.ImageRequest
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.sp
import com.neb.ians.ui.components.nebHorizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.neb.ians.speech.NebPlaybackNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** The block's own controls (move, duplicate, delete) are in the menu; these are the media bodies. */

@Composable
private fun EmptyMediaCard(icon: ImageVector, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.onSurface, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------------------------------------------------------------------------------------------------- photos

/** Crop presets, 0 is the picture as taken. */
internal val ImageRatios = listOf("Original" to 0f, "Square" to 1f, "Wide" to 16f / 9f, "Tall" to 0.8f)

private val noteImageAspectCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

@Composable
fun ImageBlockView(
    block: NoteBlock, vm: NoteEditorViewModel, readOnly: Boolean,
    selected: Boolean, onSelect: () -> Unit, inRow: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.fillImage(block.id, uri) }
    if (block.path.isBlank()) {
        if (!readOnly) EmptyMediaCard(
            icon = Icons.Outlined.Image,
            label = "Add a photo",
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            trailing = {
                IconButton(onClick = { vm.delete(block.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, "Delete block", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            },
        )
        return
    }
    val cachedAspect = remember(block.path) { noteImageAspectCache[block.path] }
    val natural by produceState(cachedAspect ?: 1.5f, block.path) {
        if (cachedAspect != null) return@produceState
        value = withContext(Dispatchers.IO) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(block.path, o)
            val ratio = if (o.outWidth > 0 && o.outHeight > 0) (o.outWidth.toFloat() / o.outHeight).coerceIn(0.4f, 3f) else 1.5f
            noteImageAspectCache[block.path] = ratio
            ratio
        }
    }
    val aspect = ImageRatios.getOrNull(block.ratio)?.second?.takeIf { it > 0f } ?: natural
    val edit = selected && !readOnly
    var live by remember(block.id) { mutableFloatStateOf(block.size) }
    LaunchedEffect(block.size) { live = block.size }
    var fullPx by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(18.dp)
    val request = remember(block.path) { ImageRequest.Builder(context).data(File(block.path)).size(1280).crossfade(true).build() }
    val frac = if (inRow) 1f else live.coerceIn(0.2f, 1f)
    Column(Modifier.fillMaxWidth().onSizeChanged { fullPx = it.width }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = when (block.align) { 1 -> Alignment.TopCenter; 2 -> Alignment.TopEnd; else -> Alignment.TopStart },
        ) {
            Box(
                Modifier.fillMaxWidth(frac).aspectRatio(aspect)
                    .pointerInput(edit, fullPx) {
                        if (!edit) return@pointerInput
                        // two fingers pinch the picture to size; one finger is left to the page so it still scrolls
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var changed = false
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.changes.size >= 2) {
                                    val z = ev.calculateZoom()
                                    if (z != 1f) {
                                        live = (live * z).coerceIn(0.2f, 1f)
                                        changed = true
                                        ev.changes.forEach { it.consume() }
                                    }
                                }
                            } while (ev.changes.any { it.pressed })
                            if (changed) vm.update(block.id) { it.copy(size = live) }
                        }
                    },
            ) {
                AsyncImage(
                    model = request, contentDescription = "Photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(shape).clickable(enabled = !readOnly, onClick = onSelect),
                )
                if (edit) {
                    Box(Modifier.matchParentSize().border(2.dp, c.primary, shape))
                    val left = block.align == 2 && !inRow
                    Box(
                        Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(10.dp).size(30.dp).clip(CircleShape).background(c.primary)
                            .pointerInput(fullPx, left) {
                                detectDragGestures(onDragEnd = { vm.update(block.id) { it.copy(size = live) } }) { change, drag ->
                                    change.consume()
                                    if (fullPx > 0 && !inRow) live = (live + (if (left) -drag.x else drag.x) / fullPx).coerceIn(0.2f, 1f)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.OpenInFull, "Resize", tint = c.onPrimary, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        if (block.caption.isNotEmpty() || edit) {
            var cap by remember(block.id) { mutableStateOf(block.caption) }
            androidx.compose.foundation.text.BasicTextField(
                value = cap, onValueChange = { cap = it.take(160); vm.update(block.id, force = false) { b -> b.copy(caption = cap) } },
                readOnly = readOnly, singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = c.onSurfaceVariant, textAlign = when (block.align) { 1 -> androidx.compose.ui.text.style.TextAlign.Center; 2 -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Start }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (cap.isEmpty()) Text("Add a caption", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant.copy(alpha = 0.6f))
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

/** What a selected picture offers: size, place, beside text, crop, replace. */
@Composable
internal fun ImageTools(block: NoteBlock, vm: NoteEditorViewModel) {
    val c = MaterialTheme.colorScheme
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.fillImage(block.id, uri) }
    // Emits its controls straight into the caller's row (MediaFrame owns the one horizontal scroll). A second
    // horizontalScroll here crashed with infinite max width constraints.
    val sizes = listOf("S" to 0.33f, "M" to 0.55f, "L" to 0.8f, "Full" to 1f)
    sizes.forEachIndexed { idx, (label, f) ->
        val on = abs(block.size - f) < 0.04f
        ToolChip(label = label, on = on, shape = rowItemShape(idx, sizes.size, outer = 18.dp, inner = TightJoinEdge)) { vm.update(block.id) { it.copy(size = f) } }
    }
    ToolDivider()
    val aligns = listOf(Icons.Outlined.FormatAlignLeft to "Left", Icons.Outlined.FormatAlignCenter to "Centre", Icons.Outlined.FormatAlignRight to "Right")
    aligns.forEachIndexed { i, (icon, name) ->
        ToolIcon(icon, name, block.align == i, shape = rowItemShape(i, aligns.size, outer = 18.dp, inner = TightJoinEdge)) { vm.update(block.id) { it.copy(align = i, wrap = if (i == 1) false else it.wrap) } }
    }
    ToolIcon(Icons.AutoMirrored.Outlined.WrapText, "Text beside", block.wrap) {
        vm.update(block.id) { it.copy(wrap = !it.wrap, align = if (!it.wrap && it.align == 1) 0 else it.align, size = if (!it.wrap) it.size.coerceAtMost(0.5f) else it.size) }
    }
    ToolDivider()
    ToolChip(label = ImageRatios[block.ratio].first, on = block.ratio != 0, icon = Icons.Outlined.Crop) { vm.update(block.id) { it.copy(ratio = (it.ratio + 1) % ImageRatios.size) } }
    ToolIcon(Icons.Outlined.PhotoLibrary, "Replace", false) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
internal fun ToolDivider() {
    Box(Modifier.padding(horizontal = 2.dp).size(width = 1.dp, height = 22.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
internal fun ToolChip(
    label: String,
    on: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.height(36.dp).clip(shape).background(if (on) c.primary else c.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (on) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onPrimary else c.onSurfaceVariant)
    }
}

@Composable
internal fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(36.dp).clip(shape).background(if (on) c.primary else c.surfaceContainerHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
}

// ---------------------------------------------------------------------------------------------------- audio

private class NoteAudioRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var started = 0L
    var file: File? = null
        private set

    fun start(target: File): Boolean = try {
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(target.absolutePath)
        r.prepare()
        r.start()
        rec = r
        file = target
        started = SystemClock.elapsedRealtime()
        true
    } catch (_: Exception) {
        release()
        false
    }

    /** Stops and returns the length in seconds. */
    fun stop(): Int {
        val r = rec ?: return 0
        val secs = ((SystemClock.elapsedRealtime() - started) / 1000L).toInt().coerceAtLeast(1)
        try { r.stop() } catch (_: Exception) { /* stopped too quickly: nothing was recorded */ }
        runCatching { r.release() }
        rec = null
        return secs
    }

    fun cancel() {
        stop()
        val f = file
        file = null
        if (f != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { f.delete() }
            }
        }
    }

    fun release() {
        runCatching { rec?.release() }
        rec = null
    }
}

private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun AudioBlockView(block: NoteBlock, vm: NoteEditorViewModel, readOnly: Boolean, onOpenMenu: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (block.path.isBlank()) {
        if (readOnly) return
        val recorder = remember { NoteAudioRecorder(context) }
        var recording by remember { mutableStateOf(false) }
        var elapsed by remember { mutableIntStateOf(0) }
        DisposableEffect(Unit) { onDispose { if (recording) recorder.cancel() else recorder.release() } }
        LaunchedEffect(recording) {
            elapsed = 0
            while (recording) { delay(1000); elapsed += 1 }
        }
        fun begin() {
            vm.stopDictation()
            scope.launch {
                val target = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.newAudioFile() }
                if (recorder.start(target)) recording = true else vm.reportNotice("Recording is not available right now")
            }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) begin() }
        val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.fillAudioFromUri(block.id, uri) }
        if (recording) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.errorContainer).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.error))
                Text(clock(elapsed), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = c.onErrorContainer, modifier = Modifier.weight(1f))
                NoteButton(Icons.Outlined.Close, "Discard", { recorder.cancel(); recording = false }, container = c.surfaceContainerHigh, content = c.onSurface, height = 44.dp)
                NoteButton(
                    Icons.Outlined.Stop, "Save recording",
                    {
                        val secs = recorder.stop()
                        val f = recorder.file
                        recording = false
                        if (f != null) vm.fillAudio(block.id, f, secs)
                    },
                    container = c.error, content = c.onError, height = 44.dp,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NoteLabelTile(
                    "Record", {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    Modifier.weight(1f), icon = Icons.Outlined.Mic, shape = joinStart(28.dp), container = c.surfaceContainerHigh,
                )
                NoteLabelTile(
                    "File", { import.launch(arrayOf("audio/*")) },
                    Modifier.weight(1f), icon = Icons.Outlined.Audiotrack, shape = JoinMid, container = c.surfaceContainerHigh,
                )
                NoteButton(
                    Icons.Outlined.Delete, "Delete audio block",
                    { vm.delete(block.id) },
                    shape = joinEnd(28.dp), width = 48.dp, height = 48.dp, container = c.surfaceContainerHigh, content = c.onSurfaceVariant,
                )
            }
        }
        return
    }
    var player by remember(block.path) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(block.path) { mutableStateOf(false) }
    var pos by remember(block.path) { mutableIntStateOf(0) }
    var dur by remember(block.path) { mutableIntStateOf(block.seconds * 1000) }
    var speed by remember(block.path) { mutableStateOf(1f) }
    val ctrlId = remember(block.id) { "note_audio_${block.id}" }
    fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }
    val mediaController = remember(block.path, ctrlId) {
        object : NebPlaybackNotificationManager.Controller {
            override val id: String = ctrlId
            override fun onPlayPause() {
                val p = player ?: return
                if (p.isPlaying) {
                    runCatching { p.pause() }
                    playing = false
                } else {
                    runCatching { p.start(); applySpeed(p) }
                    playing = true
                }
            }
            override fun onSeekTo(posMs: Long) {
                val target = posMs.toInt().coerceIn(0, dur.coerceAtLeast(1))
                pos = target
                runCatching { player?.seekTo(target) }
            }
            override fun onStop() {
                runCatching { player?.pause(); player?.seekTo(0) }
                playing = false
                pos = 0
            }
        }
    }
    fun syncPlaybackNotification(isNowPlaying: Boolean) {
        val noteTitle = vm.title.value.trim().ifEmpty { "Voice recording" }
        NebPlaybackNotificationManager.registerAndUpdate(
            context = context,
            controller = mediaController,
            title = noteTitle,
            subtitle = "Note audio",
            isPlaying = isNowPlaying,
            positionMs = pos.toLong(),
            durationMs = dur.toLong(),
            speed = speed,
        )
    }
    DisposableEffect(block.path) {
        onDispose {
            NebPlaybackNotificationManager.clearIfOwned(context, ctrlId)
            runCatching { player?.release() }
            player = null
        }
    }
    LaunchedEffect(playing) {
        if (playing) syncPlaybackNotification(true)
        var tick = 0
        while (playing) {
            pos = player?.currentPosition ?: 0
            tick++
            if (tick % 4 == 0) syncPlaybackNotification(true)
            delay(180)
        }
        if (!playing && pos > 0) syncPlaybackNotification(false)
    }
    fun toggle() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(block.path)
                prepare()
                setOnCompletionListener {
                    playing = false
                    pos = 0
                    NebPlaybackNotificationManager.clearIfOwned(context, ctrlId)
                }
            }
        }.getOrNull()?.also { player = it; if (it.duration > 0) dur = it.duration }
        if (p == null) { vm.reportNotice("This recording cannot be played"); return }
        if (p.isPlaying) {
            p.pause()
            playing = false
            syncPlaybackNotification(false)
        } else {
            p.start()
            applySpeed(p)
            playing = true
            syncPlaybackNotification(true)
        }
    }
    fun seekTo(fraction: Float) {
        pos = (fraction.coerceIn(0f, 1f) * dur).toInt()
        player?.seekTo(pos)
        if (playing || pos > 0) syncPlaybackNotification(playing)
    }
    val activeTranscriptions by vm.transcriptions.collectAsStateWithLifecycle()
    val txState = activeTranscriptions[block.id]
    val transcribing = txState != null
    val txPct = txState?.progressPercent ?: 0
    var showTranscript by remember(block.id) { mutableStateOf(block.caption.isNotBlank() || transcribing) }
    LaunchedEffect(transcribing, block.caption) {
        if (transcribing || block.caption.isNotBlank()) showTranscript = true
    }
    val bars = remember(block.path) {
        var seed = block.path.hashCode().toLong() and 0x7fffffff
        FloatArray(22) { seed = (seed * 1103515245L + 12345L) and 0x7fffffff; 0.24f + 0.76f * ((seed shr 8) % 100) / 100f }
    }
    val track = c.onSurfaceVariant.copy(alpha = 0.36f)
    val cardBg = noteSurfaceTileColor()
    val tileBg = noteRaisedTileColor()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cardBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val radius by androidx.compose.animation.core.animateDpAsState(if (playing) 16.dp else 20.dp, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), label = "play_radius")
            NoteTile({ toggle() }, Modifier.size(56.dp), RoundedCornerShape(radius), c.primary, c.onPrimary) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(28.dp))
            }
            androidx.compose.foundation.Canvas(
                Modifier.weight(1f).height(44.dp)
                    .pointerInput(dur) { detectTapGestures { o -> seekTo(o.x / size.width) } }
                    .pointerInput(dur) { detectHorizontalDragGestures { change, _ -> change.consume(); seekTo(change.position.x / size.width) } },
            ) {
                val gap = 6.dp.toPx()
                val w = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.5.dp.toPx())
                val done = if (dur > 0) pos.toFloat() / dur else 0f
                bars.forEachIndexed { i, f ->
                    val h = size.height * f
                    drawRoundRect(
                        if ((i + 0.5f) / bars.size <= done) c.primary else track,
                        Offset(i * (w + gap), (size.height - h) / 2f), Size(w, h), CornerRadius(w / 2f),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${clock((if (playing || pos > 0) pos else 0) / 1000)} / ${clock(dur / 1000)}",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.primary, maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                NoteLabelTile(
                    if (speed == 1f) "1x" else "${speed}x",
                    { speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }; player?.takeIf { it.isPlaying }?.let(::applySpeed) },
                    Modifier.widthIn(min = 42.dp), shape = joinStart(18.dp, JoinEdge), height = 36.dp, size = 12, container = tileBg,
                )
                NoteTile(
                    {
                        showTranscript = !showTranscript
                        if (showTranscript && block.caption.isBlank() && !transcribing && !readOnly) {
                            vm.transcribeAudioBlock(block.id, block.path)
                        }
                    },
                    Modifier.height(36.dp),
                    shape = joinEnd(18.dp, JoinEdge),
                    container = if (showTranscript) c.primary else tileBg,
                    content = if (showTranscript) c.onPrimary else c.onSurface,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (transcribing) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = if (showTranscript) c.onPrimary else c.primary)
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.Subject, null, Modifier.size(16.dp))
                        }
                        Text("Transcript", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showTranscript,
            enter = expandVertically( androidx.compose.animation.core.spring(stiffness = 460f)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            var transcriptText by remember(block.id, block.caption) { mutableStateOf(block.caption) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tileBg).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = transcriptText,
                    onValueChange = {
                        transcriptText = it
                        vm.update(block.id, force = false) { b -> b.copy(caption = it) }
                    },
                    readOnly = readOnly,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (transcriptText.isEmpty()) {
                                Text(
                                    if (transcribing) "Transcribing audio on device (${txPct}%)..." else "Tap Transcribe or type transcript notes...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!readOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (transcribing) {
                            NoteLabelTile(
                                if (txPct > 0) "Transcribing ${txPct}%..." else "Transcribing...",
                                {},
                                shape = joinStart(16.dp, TightJoinEdge), height = 34.dp, size = 12, container = cardBg,
                            )
                            NoteLabelTile(
                                "Cancel",
                                { vm.cancelAudioTranscription(block.id) },
                                icon = Icons.Outlined.Close,
                                shape = joinEnd(16.dp, TightJoinEdge), height = 34.dp, size = 12, container = c.errorContainer,
                            )
                        } else if (transcriptText.isNotBlank()) {
                            // After transcription: only Retry and Insert into note buttons (Image 5 requirement)
                            NoteLabelTile(
                                "Retry",
                                { vm.transcribeAudioBlock(block.id, block.path) },
                                icon = Icons.Outlined.Refresh,
                                shape = joinStart(16.dp, TightJoinEdge), height = 34.dp, size = 12, container = cardBg,
                            )
                            NoteLabelTile(
                                "Insert into note",
                                { vm.insert(BlockType.Text, afterId = block.id, block = NoteBlock(type = BlockType.Text, text = transcriptText.trim())) },
                                shape = joinEnd(16.dp, TightJoinEdge), height = 34.dp, size = 12, container = cardBg,
                            )
                        } else {
                            NoteLabelTile(
                                "Transcribe",
                                { vm.transcribeAudioBlock(block.id, block.path) },
                                shape = RoundedCornerShape(16.dp), height = 34.dp, size = 12, container = cardBg,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------- tables

@Composable
fun TableBlockView(block: NoteBlock, vm: NoteEditorViewModel, readOnly: Boolean, fg: Color, onOpenMenu: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val rows = block.rows.ifEmpty { List(3) { List(3) { "" } } }
    val cols = rows.maxOf { it.size }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.nebHorizontalScroll(rememberScrollState()).clip(RoundedCornerShape(16.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.background(if (r == 0) c.surfaceContainerHigh else Color.Transparent)) {
                    for (col in 0 until cols) {
                        val text = row.getOrElse(col) { "" }
                        var value by remember(block.id, r, col) { mutableStateOf(TextFieldValue(text)) }
                        var sent by remember(block.id, r, col) { mutableStateOf(text) }
                        LaunchedEffect(text) {
                            if (text != sent) { value = TextFieldValue(text, TextRange(text.length)); sent = text }
                        }
                        Box(
                            Modifier.width(112.dp).heightMin().border(0.5.dp, c.outlineVariant).padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            BasicTextField(
                                value = value,
                                onValueChange = { nv -> value = nv; sent = nv.text; vm.setTableCell(block.id, r, col, nv.text) },
                                readOnly = readOnly,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = fg, fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal),
                                cursorBrush = SolidColor(c.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        if (!readOnly) {
            Row(Modifier.nebHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TableChip("Row", true) { vm.tableShape(block.id, addRow = 1) }
                TableChip("Column", true) { vm.tableShape(block.id, addCol = 1) }
                if (rows.size > 1) TableChip("Row", false) { vm.tableShape(block.id, addRow = -1) }
                if (cols > 1) TableChip("Column", false) { vm.tableShape(block.id, addCol = -1) }
                if (onOpenMenu != null) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).clickable(onClick = onOpenMenu),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.MoreHoriz, "Table options", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                }
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(c.errorContainer).clickable { vm.delete(block.id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Delete, "Delete table", tint = c.onErrorContainer, modifier = Modifier.size(17.dp)) }
            }
        }
    }
}

private fun Modifier.heightMin(): Modifier = this.then(Modifier.height(44.dp))

@Composable
private fun TableChip(label: String, add: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(if (add) Icons.Outlined.Add else Icons.Outlined.Remove, null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------------- drawings

private val InkColors = listOf(0xFF1B1B1F, 0xFF1E5FD0, 0xFFD32F2F, 0xFF2E7D32, 0xFFF59E0B).map { it.toInt() }
private val InkWidths = listOf(3f, 6f, 12f)
private val Paper = Color(0xFFFCFCFA)

private fun fillStrokePath(p: Path, points: List<Float>, w: Float, h: Float): Path {
    p.rewind()
    if (points.size >= 2) {
        p.moveTo(points[0] * w, points[1] * h)
        if (points.size == 2) p.lineTo(points[0] * w + 0.1f, points[1] * h)
        var i = 2
        while (i + 1 < points.size) {
            val mx = (points[i - 2] + points[i]) / 2f
            val my = (points[i - 1] + points[i + 1]) / 2f
            p.quadraticBezierTo(points[i - 2] * w, points[i - 1] * h, mx * w, my * h)
            i += 2
        }
        p.lineTo(points[points.size - 2] * w, points[points.size - 1] * h)
    }
    return p
}

@Composable
fun DrawingBlockView(
    block: NoteBlock,
    vm: NoteEditorViewModel,
    readOnly: Boolean,
    editing: Boolean = false,
    onEditingChange: (Boolean) -> Unit = {},
    onSelect: () -> Unit = {},
    onOpenMenu: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(block.id) {
        if (block.strokes.isEmpty() && !readOnly) onEditingChange(true)
    }
    var color by remember { mutableIntStateOf(InkColors[0]) }
    var width by remember { mutableFloatStateOf(InkWidths[1]) }
    var eraser by remember { mutableStateOf(false) }
    val current = remember { mutableStateListOf<Float>() }
    val strokePath = remember { Path() }
    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(block.height.dp).clip(shape).background(Paper).border(if (editing) 2.dp else 1.dp, if (editing) c.primary else c.outlineVariant, shape)
                .onSizeChanged { sizePx = it }
                .pointerInput(editing, eraser, color, width, block.strokes, sizePx) {
                    if (!editing) return@pointerInput
                    detectDragGestures(
                        onDragStart = { o ->
                            if (!eraser && sizePx.width > 0) { current.clear(); current.add(o.x / sizePx.width); current.add(o.y / sizePx.height) }
                        },
                        onDragEnd = {
                            if (!eraser && current.size >= 2) vm.addStroke(block.id, Stroke(color, width, current.toList()))
                            current.clear()
                        },
                        onDragCancel = { current.clear() },
                    ) { change, _ ->
                        change.consume()
                        if (sizePx.width <= 0) return@detectDragGestures
                        val nx = change.position.x / sizePx.width
                        val ny = change.position.y / sizePx.height
                        if (eraser) {
                            val kept = block.strokes.filter { st ->
                                var hit = false
                                var i = 0
                                while (i + 1 < st.points.size && !hit) {
                                    val dx = (st.points[i] - nx) * sizePx.width
                                    val dy = (st.points[i + 1] - ny) * sizePx.height
                                    if (hypot(dx, dy) < 22f + st.width) hit = true
                                    i += 2
                                }
                                !hit
                            }
                            if (kept.size != block.strokes.size) vm.eraseStrokes(block.id, kept)
                        } else {
                            current.add(nx); current.add(ny)
                        }
                    }
                }
                .clickable(enabled = !editing && !readOnly) { onSelect() },
        ) {
            Canvas(Modifier.matchParentSize()) {
                block.strokes.forEach { st ->
                    drawPath(fillStrokePath(strokePath, st.points, size.width, size.height), Color(st.color), style = DrawStroke(st.width * density / 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (current.size >= 2) {
                    drawPath(fillStrokePath(strokePath, current, size.width, size.height), Color(color), style = DrawStroke(width * density / 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (block.strokes.isEmpty() && current.isEmpty()) {
                Text(
                    if (editing) "Draw here" else "Tap to select or draw",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B6B70),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (!readOnly) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(joinStart(15.dp, TightJoinEdge))
                            .background(if (editing) c.primary else Color(0xE622232A))
                            .clickable {
                                eraser = false
                                onEditingChange(!editing)
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Draw, null, tint = if (editing) c.onPrimary else Color.White, modifier = Modifier.size(14.dp))
                            Text(if (editing) "Done" else "Draw", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (editing) c.onPrimary else Color.White, maxLines = 1)
                        }
                    }
                    if (onOpenMenu != null) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(TightJoinMid)
                                .background(Color(0xE622232A))
                                .clickable(onClick = onOpenMenu),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.MoreHoriz, "Drawing options", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(joinEnd(15.dp, TightJoinEdge))
                            .background(c.errorContainer)
                            .clickable { onEditingChange(false); vm.delete(block.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete drawing", tint = c.onErrorContainer, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        if (editing && !readOnly) {
            Row(Modifier.nebHorizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkColors.forEach { ink ->
                    val on = !eraser && ink == color
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(ink)).clickable { color = ink; eraser = false }
                            .then(if (on) Modifier.border(3.dp, c.primary, CircleShape) else Modifier),
                    )
                }
                InkWidths.forEach { w ->
                    val on = width == w
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (on) c.primaryContainer else c.surfaceContainerHigh).clickable { width = w; eraser = false },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size((w + 4f).dp).clip(CircleShape).background(if (on) c.onPrimaryContainer else c.onSurfaceVariant)) }
                }
                Text(
                    "Eraser", style = MaterialTheme.typography.labelLarge, color = if (eraser) c.onPrimaryContainer else c.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (eraser) c.primaryContainer else c.surfaceContainerHigh)
                        .clickable { eraser = !eraser }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                IconButton(onClick = { vm.undoStroke(block.id) }, enabled = block.strokes.isNotEmpty()) { Icon(Icons.Outlined.Undo, "Undo stroke", tint = c.onSurfaceVariant) }
                Text(
                    "Clear", style = MaterialTheme.typography.labelLarge, color = if (block.strokes.isNotEmpty()) c.onSurfaceVariant else c.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh)
                        .clickable(enabled = block.strokes.isNotEmpty()) { vm.clearStrokes(block.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                IconButton(onClick = { vm.update(block.id) { it.copy(height = (it.height + 100f).coerceAtMost(720f)) } }) { Icon(Icons.Outlined.Add, "Taller", tint = c.onSurfaceVariant) }
                IconButton(onClick = { vm.update(block.id) { it.copy(height = (it.height - 100f).coerceAtLeast(160f)) } }) { Icon(Icons.Outlined.Remove, "Shorter", tint = c.onSurfaceVariant) }
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(c.errorContainer).clickable { onEditingChange(false); vm.delete(block.id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Delete, "Delete drawing", tint = c.onErrorContainer, modifier = Modifier.size(18.dp)) }
                Text(
                    "Done", style = MaterialTheme.typography.labelLarge, color = c.onPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.primary).clickable { onEditingChange(false); eraser = false }.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else if (!readOnly) {
            Spacer(Modifier.height(0.dp))
        }
    }
}
