// inset-ok: parent Box in NoteEditorScreen applies statusBarsPadding() so cover never overlaps the status bar
package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The floating controls of a page: round back, joined undo and redo, pin, more. */
@Composable
internal fun NoteTopBar(
    container: Color, content: Color,
    canUndo: Boolean, canRedo: Boolean, pinned: Boolean, trashed: Boolean,
    onBack: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onPin: () -> Unit, onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        NoteButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, container = container, content = content)
        if (!trashed) {
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NoteButton(Icons.Outlined.Undo, "Undo", onUndo, shape = joinStart(22.dp, TightJoinEdge), container = container, content = content, enabled = canUndo, width = 44.dp)
                NoteButton(Icons.Outlined.Redo, "Redo", onRedo, shape = joinEnd(22.dp, TightJoinEdge), container = container, content = content, enabled = canRedo, width = 44.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        if (!trashed) {
            NoteButton(
                Icons.Outlined.PushPin, if (pinned) "Unpin" else "Pin", onPin,
                container = if (pinned) c.primaryContainer else container,
                content = if (pinned) c.onPrimaryContainer else content,
            )
            Spacer(Modifier.width(8.dp))
        }
        NoteButton(Icons.Outlined.MoreVert, "More", onMore, container = container, content = content)
    }
}
