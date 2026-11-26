package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private val SuggestedTags = listOf("Study", "Ideas", "Work", "Goals")

/** A tag is one word after a hash. Suggestions that the page already carries are left out. */
@Composable
internal fun NoteTagSheet(existing: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80L)
        runCatching { focus.requestFocus() }
    }
    val suggestions = remember(existing) { SuggestedTags.filter { s -> existing.none { it.equals(s, true) } } }
    NoteSheet(onDismiss = onDismiss, title = "Add tag", inset = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(28.dp)
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(shape).background(noteRaisedTileColor()).border(1.5.dp, c.primary, shape).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#", color = c.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Box(Modifier.width(10.dp))
                BasicTextField(
                    value = text, onValueChange = { text = it.take(24) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd(text.trim()) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            if (suggestions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.forEachIndexed { i, s ->
                        NoteLabelTile(
                            s, { onAdd(s) }, Modifier.weight(1f),
                            shape = rowItemShape(i, suggestions.size, outer = 22.dp, inner = JoinEdge),
                            height = 44.dp,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteTile(onDismiss, Modifier.weight(1f).height(52.dp), RoundedCornerShape(26.dp), noteRaisedTileColor(), c.primary) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                NoteTile(
                    {
                        val chosen = text.trim().ifBlank { suggestions.firstOrNull().orEmpty() }
                        if (chosen.isNotBlank()) onAdd(chosen) else onDismiss()
                    },
                    Modifier.weight(1f).height(52.dp), RoundedCornerShape(26.dp), c.primary, c.onPrimary,
                ) {
                    Text("Add", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
