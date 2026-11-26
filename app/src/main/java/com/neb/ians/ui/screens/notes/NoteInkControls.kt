package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Maps the stored block alignment (0 start, 1 centre, 2 end) to a text alignment. */
fun inkAlign(a: Int): TextAlign = when (a) { 1 -> TextAlign.Center; 2 -> TextAlign.End; else -> TextAlign.Start }

private val BlockPaletteIndices = intArrayOf(0, 6, 5, 4, 3, 1, 7)

/**
 * The 7-swatch Colour section of the Block Menu (`03_block_menu.png`):
 * Neutral, Blue, Teal, Green, Amber, Red, Violet (`46dp` circles with `2dp` primary ring + checkmark when selected).
 */
@Composable
internal fun BlockColourSection(current: Int, isCallout: Boolean = false, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    val dark = remember(c.surface) { c.surface.luminance() < 0.5f }
    val neutralSwatch = noteRaisedTileColor()
    val effectiveCurrent = when {
        isCallout && current == 0 -> 6
        isCallout && current == 9 -> 0
        else -> current
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Colour", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BlockPaletteIndices.forEach { inkIdx ->
                val swatch = when {
                    inkIdx == 0 -> neutralSwatch
                    inkIdx == 6 -> c.primaryContainer
                    else -> NoteInk.fill(inkIdx, dark) ?: neutralSwatch
                }
                val selected = effectiveCurrent == inkIdx
                val checkTint = when {
                    inkIdx == 0 -> c.onSurface
                    inkIdx == 6 -> c.onPrimaryContainer
                    else -> NoteInk.text(inkIdx, dark) ?: Color.White
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .then(if (selected) Modifier.border(2.dp, c.primary, CircleShape) else Modifier)
                        .clickable {
                            val stored = if (isCallout && inkIdx == 0) 9 else if (isCallout && inkIdx == 6) 0 else inkIdx
                            onPick(stored)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            if (inkIdx == 0) "Default" else NoteInk.names[inkIdx - 1],
                            tint = checkTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
