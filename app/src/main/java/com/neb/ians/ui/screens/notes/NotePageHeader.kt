@file:OptIn(ExperimentalLayoutApi::class)

package com.neb.ians.ui.screens.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

/** Height of the cover banner (starts below the status bar, never overlaps it). */
internal val NoteCoverHeight: Dp = 180.dp

/** Clearance for the floating top bar when a page has no cover. */
internal val NoteBarClearance: Dp = 80.dp

/**
 * The top of the page. A cover runs edge to edge below the status bar with a 64dp icon tile hanging off its lower edge
 * and the title under it; without a cover the 48dp icon tile sits beside the title. Tag chips and the cover chip follow.
 */
@Composable
internal fun NotePageHeader(
    icon: String, cover: Int, photo: File?, bg: Color, fg: Color,
    surfaceTile: Color, raisedTile: Color,
    editable: Boolean, tags: List<String>,
    onIcon: () -> Unit, onCover: () -> Unit, onClearCover: () -> Unit, onAddTag: () -> Unit, onRemoveTag: (String) -> Unit,
    title: @Composable (Modifier) -> Unit,
) {
    val hasCover = cover != 0
    val hasIcon = icon.isNotEmpty()
    Column(Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))) {
        if (hasCover) NoteCoverArea(cover, photo, editable, onCover, onClearCover)
        else Spacer(Modifier.height(NoteBarClearance))
        if (hasCover) {
            if (hasIcon || editable) {
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier.padding(start = 20.dp)
                        .layout { m, constraints ->
                            val placeable = m.measure(constraints)
                            val pull = 32.dp.roundToPx()
                            layout(placeable.width, placeable.height - pull) { placeable.place(0, -pull) }
                        }
                        .border(4.dp, bg, shape)
                        .clickableIf(editable, shape, onIcon),
                ) {
                    if (hasIcon) NoteIconTile(icon, 64.dp, container = raisedTile, content = fg)
                    else NoteTile(null, Modifier.size(64.dp), shape, raisedTile, fg.copy(alpha = 0.85f)) {
                        Icon(Icons.Outlined.SentimentSatisfied, "Add icon", Modifier.size(28.dp))
                    }
                }
            }
            title(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
        } else {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val shape = RoundedCornerShape(16.dp)
                Box(Modifier.clickableIf(editable, shape, onIcon), contentAlignment = Alignment.Center) {
                    if (hasIcon) NoteIconTile(icon, 48.dp, container = surfaceTile, content = fg)
                    else NoteTile(null, Modifier.size(48.dp), shape, surfaceTile, fg.copy(alpha = 0.8f)) {
                        Icon(Icons.Outlined.SentimentSatisfied, "Add icon", Modifier.size(24.dp))
                    }
                }
                title(Modifier.weight(1f))
            }
        }
        if (tags.isNotEmpty() || editable) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val c = MaterialTheme.colorScheme
                tags.forEach { t ->
                    NoteChip(t, null, if (editable) ({ onRemoveTag(t) }) else null, raisedTile, c.primary)
                }
                if (editable && tags.size < 8) NoteChip("Tag", Icons.Outlined.Add, onAddTag, surfaceTile, fg.copy(alpha = 0.85f))
                if (editable && !hasCover) NoteChip("Cover", Icons.Outlined.Image, onCover, surfaceTile, fg.copy(alpha = 0.85f))
            }
        }
    }
}

private fun Modifier.clickableIf(on: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit): Modifier =
    if (on) this.clip(shape).clickable(onClick = onClick) else this

/** A small label chip: tag, add tag, add cover. */
@Composable
private fun NoteChip(label: String, icon: ImageVector?, onClick: (() -> Unit)?, container: Color, content: Color) {
    NoteTile(onClick, Modifier.height(34.dp), RoundedCornerShape(17.dp), container, content) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** The cover and its Change / Remove pills, shown on tap. */
@Composable
private fun NoteCoverArea(cover: Int, photo: File?, editable: Boolean, onCover: () -> Unit, onClearCover: () -> Unit) {
    var tools by remember { mutableStateOf(false) }
    LaunchedEffect(tools) { if (tools) { kotlinx.coroutines.delay(4200); tools = false } }
    Box(
        Modifier.fillMaxWidth().height(NoteCoverHeight)
            .then(if (editable) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tools = !tools } else Modifier),
    ) {
        NoteCover(cover, photo, Modifier.matchParentSize())
        if (editable) {
            AnimatedVisibility(
                visible = tools,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.86f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f),
            ) {
                val c = MaterialTheme.colorScheme
                Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NoteTile({ tools = false; onCover() }, Modifier.height(40.dp), joinStart(20.dp), c.surface, c.onSurface) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Image, null, Modifier.size(16.dp))
                            Text("Change", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    NoteButton(Icons.Outlined.Close, "Remove cover", { tools = false; onClearCover() }, shape = joinEnd(20.dp), container = c.surface, height = 40.dp, width = 40.dp)
                }
            }
        }
    }
}
