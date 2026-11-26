package com.neb.ians.ui.screens.notes

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.ui.components. NebToastKind
import com.neb.ians.ui.components.nebToast
import java.io.File

internal class NoteMenuAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The page menu (`09_note_menu.png`): a row of 3 joined quick tiles (`28dp` outer, `8dp` inner),
 * a vertically joined stack of action rows (`24dp` top/bottom outer, `8dp` inner), and one destructive pill (`28dp`).
 */
@Composable
internal fun NoteMenuSheet(
    quick: List<NoteMenuAction>,
    rows: List<NoteMenuAction>,
    danger: NoteMenuAction,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    NoteSheet(onDismiss = onDismiss, title = null, inset = 16.dp) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                quick.forEachIndexed { i, a ->
                    val shape = rowItemShape(i, quick.size, outer = 28.dp, inner = JoinEdge)
                    NoteTile({ onDismiss(); a.onClick() }, Modifier.weight(1f).height(80.dp), shape) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(a.icon, null, Modifier.size(22.dp))
                            Text(a.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEachIndexed { i, a ->
                    val shape = stackItemShape(i, rows.size, outer = TileRadius, inner = JoinEdge)
                    NoteTile({ onDismiss(); a.onClick() }, Modifier.fillMaxWidth().height(56.dp), shape) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(a.icon, null, Modifier.size(22.dp))
                            Text(a.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            NoteTile(
                { onDismiss(); danger.onClick() }, Modifier.fillMaxWidth().height(56.dp),
                RoundedCornerShape(28.dp), c.errorContainer, c.onErrorContainer,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(danger.icon, null, Modifier.size(20.dp))
                    Text(danger.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

private val DefaultFolders = listOf("Study", "Ideas", "Work", "Goals", "Lecture", "Personal")

/** Functional "Move to folder" sheet: organises the note into a folder tag or Archive. */
@Composable
internal fun NoteFolderSheet(
    currentTags: List<String>,
    archived: Boolean,
    onMoveToFolder: (String) -> Unit,
    onToggleArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val rows = DefaultFolders.chunked(2)
    NoteSheet(onDismiss = onDismiss, title = "Move to folder", inset = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEachIndexed { r, line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        line.forEachIndexed { col, folder ->
                            val active = currentTags.any { it.equals(folder, ignoreCase = true) }
                            val shape = gridCellShape(r, col, rows.size, line.size)
                            NoteTile(
                                { onDismiss(); onMoveToFolder(folder) },
                                Modifier.weight(1f).height(56.dp),
                                shape = shape,
                                container = if (active) c.inverseSurface else noteRaisedTileColor(),
                                content = if (active) c.inverseOnSurface else c.onSurface,
                            ) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(if (active) Icons.Outlined.Check else Icons.Outlined.Folder, null, Modifier.size(20.dp))
                                    Text(folder, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            NoteTile(
                { onDismiss(); onToggleArchive() },
                Modifier.fillMaxWidth().height(56.dp),
                RoundedCornerShape(28.dp),
                noteRaisedTileColor(),
                c.onSurface,
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.Archive, null, Modifier.size(22.dp))
                    Text(if (archived) "Move out of Archive" else "Move to Archive", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** Functional "Export" sheet: exports the note as Markdown (.md), Plain text (.txt), Clipboard, or Share. */
@Composable
internal fun NoteExportSheet(
    onSaveMarkdown: () -> Unit,
    onSaveText: () -> Unit,
    onCopyText: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = listOf(
        NoteMenuAction(Icons.Outlined.Download, "Save as Markdown (.md)", onSaveMarkdown),
        NoteMenuAction(Icons.Outlined.Description, "Save as Plain text (.txt)", onSaveText),
        NoteMenuAction(Icons.Outlined.ContentCopy, "Copy text to clipboard", onCopyText),
        NoteMenuAction(Icons.Outlined.Share, "Share note", onShare),
    )
    NoteSheet(onDismiss = onDismiss, title = "Export", inset = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, a ->
                val shape = stackItemShape(i, items.size, outer = TileRadius, inner = JoinEdge)
                NoteTile({ onDismiss(); a.onClick() }, Modifier.fillMaxWidth().height(56.dp), shape) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(a.icon, null, Modifier.size(22.dp))
                        Text(a.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

internal fun exportNoteToDownloads(context: Context, title: String, content: String, ext: String, mime: String) {
    val safeName = title.trim().ifBlank { "Untitled" }.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(48)
    val fileName = "$safeName.$ext"
    val ok = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: false
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
            File(dir, fileName).writeText(content, Charsets.UTF_8)
            true
        }
    }.getOrDefault(false)
    if (ok) context.nebToast("Saved $fileName to Downloads", kind = NebToastKind.Success)
    else context.nebToast("Could not export note", kind = NebToastKind.Error)
}
