package com.neb.ians.ui.screens.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class InsertItem(val label: String, val icon: ImageVector) {
    Heading("Heading", Icons.Outlined.Title), Checklist("Checklist", Icons.Outlined.Checklist),
    Bullets("Bullets", Icons.Outlined.FormatListBulleted), Numbered("Numbered", Icons.Outlined.FormatListNumbered),
    Toggle("Toggle", Icons.Outlined.ChevronRight), Callout("Callout", Icons.Outlined.Lightbulb),
    Code("Code", Icons.Outlined.Code), Divider("Divider", Icons.Outlined.HorizontalRule),
    Table("Table", Icons.Outlined.TableChart), Photo("Photo", Icons.Outlined.Image),
    Audio("Audio", Icons.Outlined.Mic), Drawing("Drawing", Icons.Outlined.Draw),
}

/** Inserts blocks at the caret. One photo picker serves the sheet and the keyboard bar. */
internal class BlockInserter(private val vm: NoteEditorViewModel, private val registry: BlockFieldRegistry, private val pickPhotos: () -> Unit) {
    fun photos() = pickPhotos()

    fun insert(item: InsertItem) {
        when (item) {
            InsertItem.Photo -> pickPhotos()
            InsertItem.Table -> vm.newTable()
            InsertItem.Heading -> vm.insert(BlockType.H2)
            InsertItem.Checklist -> vm.insert(BlockType.Todo)
            InsertItem.Bullets -> vm.insert(BlockType.Bullet)
            InsertItem.Numbered -> vm.insert(BlockType.Number)
            InsertItem.Toggle -> vm.insert(BlockType.Toggle)
            InsertItem.Callout -> vm.insert(BlockType.Callout)
            InsertItem.Code -> vm.insert(BlockType.Code)
            InsertItem.Divider -> vm.insert(BlockType.Divider)
            InsertItem.Audio -> vm.insert(BlockType.Audio)
            InsertItem.Drawing -> vm.insert(BlockType.Drawing)
        }
        registry.selectedId = null
    }
}

@Composable
internal fun rememberBlockInserter(vm: NoteEditorViewModel, registry: BlockFieldRegistry): BlockInserter {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        var after = registry.selectedId ?: vm.focusedId
        uris.forEach { uri ->
            val b = NoteBlock(type = BlockType.Image)
            vm.insert(BlockType.Image, afterId = after, block = b)
            vm.fillImage(b.id, uri)
            after = b.id
        }
    }
    return remember(vm, registry, launcher) {
        BlockInserter(vm, registry) { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

/** Twelve block kinds in a connected four by three bento grid (`24dp` outer corners, `8dp` inner corners). */
@Composable
internal fun NoteInsertSheet(inserter: BlockInserter, onDismiss: () -> Unit) {
    val rows = remember { InsertItem.entries.chunked(4) }
    NoteSheet(onDismiss = onDismiss, title = "Insert", inset = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { r, line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    line.forEachIndexed { c, item ->
                        val shape = gridCellShape(r, c, rows.size, line.size, outer = TileRadius, inner = JoinEdge)
                        NoteTile({ onDismiss(); inserter.insert(item) }, Modifier.weight(1f).height(80.dp), shape = shape) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item == InsertItem.Heading) {
                                    Text("H1", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                } else {
                                    Icon(item.icon, null, Modifier.size(22.dp))
                                }
                                Text(item.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
