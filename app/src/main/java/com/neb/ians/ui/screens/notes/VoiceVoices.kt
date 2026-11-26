package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neb.ians.speech.PhononVoice
import com.neb.ians.speech.ReadState
import com.neb.ians.speech.SpeechCatalog
import com.neb.ians.speech.VoiceCatalog

@Composable
internal fun TinyVocaVoiceList(vm: SpeechViewModel) {
    val selected by vm.vocaVoice.collectAsStateWithLifecycle()
    val reading by vm.readState.collectAsStateWithLifecycle()
    val voices = SpeechCatalog.tinyVocaVoices
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Voices", Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        voices.forEachIndexed { idx, (code, subtitle) ->
            val isSelected = selected == code
            VoiceRow(
                title = code,
                subtitle = subtitle,
                selected = isSelected,
                installed = true,
                busy = false,
                previewing = reading != ReadState.Idle && isSelected,
                shape = stackItemShape(idx, voices.size),
                onUse = { vm.useVocaVoice(code) },
                onPreview = { vm.useVocaVoice(code); vm.preview(code) },
                onDownload = {},
                onRemove = null,
            )
        }
    }
}

@Composable
internal fun VoiceList(vm: SpeechViewModel) {
    val c = MaterialTheme.colorScheme
    val installed by vm.installedVoices.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val busy by vm.voiceBusy.collectAsStateWithLifecycle()
    val selected by vm.voice.collectAsStateWithLifecycle()
    val reading by vm.readState.collectAsStateWithLifecycle()
    val remote = (catalog as? VoiceCatalog.Ready)?.voices.orEmpty()
    val remoteByName = remote.associateBy { it.name }
    val names = (listOf("default") + installed.filter { it != "default" }).distinct()
    val uninstalled = remote.filter { it.name !in installed }
    val totalRows = names.size + if (catalog is VoiceCatalog.Ready) uninstalled.size else 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Voices", Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        names.forEachIndexed { idx, name ->
            val isSelected = selected == name
            VoiceRow(
                title = if (name == "default") "Default" else name,
                subtitle = remoteByName[name]?.let { subtitleOf(it) },
                selected = isSelected,
                installed = true,
                busy = false,
                previewing = reading != ReadState.Idle && isSelected,
                shape = stackItemShape(idx, totalRows),
                onUse = { vm.useVoice(name) },
                onPreview = { vm.preview(name) },
                onDownload = {},
                onRemove = if (name == "default") null else ({ vm.removeVoice(name) }),
            )
        }
        when (catalog) {
            VoiceCatalog.Loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            VoiceCatalog.Unavailable -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("More voices need a connection", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                TextButton(onClick = { vm.loadCatalog() }) { Text("Retry") }
            }
            is VoiceCatalog.Ready -> uninstalled.forEachIndexed { idx, v ->
                VoiceRow(
                    title = v.name,
                    subtitle = subtitleOf(v),
                    selected = false,
                    installed = false,
                    busy = v.name in busy,
                    previewing = false,
                    shape = stackItemShape(names.size + idx, totalRows),
                    onUse = {},
                    onPreview = {},
                    onDownload = { vm.downloadVoice(v) },
                    onRemove = null,
                )
            }
        }
    }
}

private fun subtitleOf(v: PhononVoice): String =
    listOf(v.gender, v.age).filter { it.isNotBlank() }.joinToString(" \u00B7 ") { it.replaceFirstChar(Char::uppercase) }

@Composable
private fun VoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    installed: Boolean,
    busy: Boolean,
    previewing: Boolean,
    shape: Shape,
    onUse: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val c = MaterialTheme.colorScheme
    NoteTile(
        onClick = if (installed) onUse else onDownload,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        container = if (selected) c.primaryContainer else noteRaisedTileColor(),
        content = if (selected) c.onPrimaryContainer else c.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) c.onPrimaryContainer else c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (installed) {
                if (selected) Icon(Icons.Outlined.Check, "Selected", Modifier.padding(horizontal = 4.dp).size(20.dp))
                NoteButton(
                    if (previewing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, if (previewing) "Stop" else "Preview", onPreview,
                    container = noteSurfaceTileColor(), content = c.onSurface,
                )
                if (onRemove != null) NoteButton(Icons.Outlined.Delete, "Remove voice", onRemove, Modifier.padding(start = 4.dp), container = c.errorContainer, content = c.onErrorContainer)
            } else if (busy) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                NoteButton(Icons.Outlined.Download, "Download voice", onDownload, container = noteSurfaceTileColor(), content = c.primary)
            }
        }
    }
}
