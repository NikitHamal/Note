package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.speech.ModelState

/**
 * One on-device model: its glyph tile and title, an optional [picker] inside the card, then the state it is in
 * (download, progress, ready with Remove). [extras] show once the model is installed.
 */
@Composable
internal fun ModelShell(
    icon: ImageVector,
    title: String,
    meta: String?,
    state: ModelState,
    supported: Boolean,
    inSheet: Boolean = false,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    picker: @Composable () -> Unit = {},
    extras: @Composable () -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    val cardColor = if (inSheet) noteRaisedTileColor() else noteSurfaceTileColor()
    val innerTileColor = if (inSheet) noteSurfaceTileColor() else noteRaisedTileColor()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NoteTile(null, Modifier.size(52.dp), RoundedCornerShape(16.dp), c.primaryContainer, c.onPrimaryContainer) {
                    Icon(icon, null, Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (meta != null) Text(meta, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            picker()
            when {
                !supported -> Text("Not supported on this phone", style = MaterialTheme.typography.bodyMedium, color = c.error)
                state is ModelState.NotInstalled -> DownloadButton("Download", onDownload)
                state is ModelState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = c.error)
                    DownloadButton("Try again", onDownload)
                }
                state is ModelState.Downloading -> {
                    NoteProgress(state.fraction, Modifier.fillMaxWidth(), height = 8.dp, trackColor = innerTileColor)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatBytes(state.done)} of ${formatBytes(state.total)}",
                            style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        NoteTile(onCancel, Modifier.height(40.dp), RoundedCornerShape(20.dp), innerTileColor, c.primary) {
                            Text("Cancel", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                state is ModelState.Installed -> {
                    NoteTile(null, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(24.dp), innerTileColor) {
                        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = c.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Ready", Modifier.weight(1f), color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            NoteTile({ confirm = true }, Modifier.height(36.dp), RoundedCornerShape(18.dp), c.errorContainer, c.onErrorContainer) {
                                Text("Remove", Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                    extras()
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = com.neb.ians.ui.theme.nebFloatColor(),
            tonalElevation = 0.dp,
            title = { Text("Remove $title?") },
            text = { Text("You can download it again at any time.") },
            confirmButton = { TextButton(onClick = { confirm = false; onRemove() }) { Text("Remove", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun DownloadButton(label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    NoteTile(onClick, Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(28.dp), c.primary, c.onPrimary) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Download, null, Modifier.size(20.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** A thick rounded bar that fills as bytes arrive. */
@Composable
internal fun NoteProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = noteRaisedTileColor(),
) {
    val c = MaterialTheme.colorScheme
    val p by androidx.compose.animation.core.animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        androidx.compose.animation.core.tween(240),
        label = "note_progress",
    )
    val r = RoundedCornerShape(height / 2)
    Box(modifier.height(height).clip(r).background(trackColor)) {
        Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.06f)).height(height).clip(r).background(c.primary))
    }
}
