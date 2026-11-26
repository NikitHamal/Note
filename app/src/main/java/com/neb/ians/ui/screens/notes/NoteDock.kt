package com.neb.ians.ui.screens.notes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * The resting dock (`06_empty_note.png` / `10_study_note.png`): `Aa` opens the format bar with left-to-right stagger,
 * `+` opens Insert, and the right pill dictates.
 */
@Composable
internal fun NoteDockLeft(
    onFormat: () -> Unit, onInsert: () -> Unit,
    container: Color, content: Color,
    animateEntrance: Boolean = false,
) {
    val enter0 = remember(animateEntrance) { Animatable(if (animateEntrance) 0f else 1f) }
    val enter1 = remember(animateEntrance) { Animatable(if (animateEntrance) 0f else 1f) }
    LaunchedEffect(animateEntrance) {
        if (animateEntrance) {
            enter0.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 520f))
        }
    }
    LaunchedEffect(animateEntrance) {
        if (animateEntrance) {
            delay(28L)
            enter1.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 520f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        NoteTile(
            onFormat,
            Modifier.size(width = 64.dp, height = 56.dp).graphicsLayer {
                val p = enter0.value
                alpha = p.coerceIn(0f, 1f)
                translationX = -(1f - p) * 18.dp.toPx()
                val s = 0.84f + 0.16f * p
                scaleX = s
                scaleY = s
            },
            joinStart(28.dp, JoinEdge), container, content,
        ) {
            Text("Aa", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        NoteButton(
            Icons.Outlined.Add, "Insert", onInsert,
            modifier = Modifier.graphicsLayer {
                val p = enter1.value
                alpha = p.coerceIn(0f, 1f)
                translationX = -(1f - p) * 18.dp.toPx()
                val s = 0.84f + 0.16f * p
                scaleX = s
                scaleY = s
            },
            shape = joinEnd(28.dp, JoinEdge), container = container, content = content, width = 56.dp, height = 56.dp,
        )
    }
}

/**
 * Dictation control. The tone is the brand primary at rest (`#B9BEFF`) and the error tone (`#FFB4B8`) while listening;
 * the shape stays rounded (`20dp` in dock, `16dp` in format bar), and the pill breathes with the voice level on the render thread.
 */
@Composable
internal fun MicPill(
    listening: Boolean, level: StateFlow<Float>, onClick: () -> Unit,
    width: Dp = 72.dp, height: Dp = 56.dp, restRadius: Dp = 20.dp,
) {
    val c = MaterialTheme.colorScheme
    val lvl by level.collectAsStateWithLifecycle()
    val pulse by animateFloatAsState(if (listening) 1f + lvl * 0.10f else 1f, tween(90), label = "mic_pulse")
    val animW by animateDpAsState(width, spring(dampingRatio = 0.75f, stiffness = 480f), label = "mic_w")
    val animH by animateDpAsState(height, spring(dampingRatio = 0.75f, stiffness = 480f), label = "mic_h")
    val radius by animateDpAsState(if (listening) restRadius else restRadius, spring(dampingRatio = 0.7f, stiffness = 500f), label = "mic_radius")
    NoteTile(
        onClick,
        Modifier.size(animW, animH).graphicsLayer { scaleX = pulse; scaleY = pulse },
        RoundedCornerShape(radius),
        if (listening) c.error else c.primary,
        if (listening) c.onError else c.onPrimary,
    ) {
        Icon(
            if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
            if (listening) "Stop dictation" else "Dictate",
            Modifier.size(if (height <= 46.dp) 22.dp else 24.dp),
        )
    }
}
