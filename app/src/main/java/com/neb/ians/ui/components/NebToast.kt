package com.neb.ians.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.ui.theme.Poppins
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The four things a toast can say. Each owns one tonal pair (card + ink) in light and dark. */
enum class NebToastKind { Success, Info, Warning, Error }

/** Snackbar visuals that carry the toast kind, so the host never has to guess from the words. */
class NebToastVisuals(
    override val message: String,
    val kind: NebToastKind,
    override val actionLabel: String? = null,
    override val duration: androidx.compose.material3.SnackbarDuration = androidx.compose.material3.SnackbarDuration.Short,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
}

private val ErrorWords = Regex("fail|error|couldn't|could not|can't|cannot|unable|invalid|wrong|denied|not allowed|problem|try again|expired|no internet|missing|limit reached")
private val WarnWords = Regex("offline|maximum|required|too (large|long|big|many)|at least|wait for|slow")
private val SuccessWords = Regex("^(saved|sent|posted|copied|added|removed|deleted|updated|done|welcome|thanks|thank you|reported|published|shared|bookmarked|unlocked|joined|followed|verified|submitted|created|marked)|success")

/** Last resort for old call sites that only pass words. New code passes a [NebToastKind]. */
fun nebToastKindOf(message: String): NebToastKind {
    val s = message.lowercase()
    return when {
        ErrorWords.containsMatchIn(s) -> NebToastKind.Error
        WarnWords.containsMatchIn(s) -> NebToastKind.Warning
        SuccessWords.containsMatchIn(s) -> NebToastKind.Success
        else -> NebToastKind.Info
    }
}

private data class ToastTone(val card: Color, val ink: Color)

private fun toneOf(kind: NebToastKind, dark: Boolean): ToastTone = when (kind) {
    NebToastKind.Success -> if (dark) ToastTone(Color(0xFF1B3A26), Color(0xFFA9E9BC)) else ToastTone(Color(0xFFE0F5E7), Color(0xFF14663A))
    NebToastKind.Info -> if (dark) ToastTone(Color(0xFF1D3358), Color(0xFFB7CFFF)) else ToastTone(Color(0xFFE1EAFF), Color(0xFF0D3F94))
    NebToastKind.Warning -> if (dark) ToastTone(Color(0xFF3F3012), Color(0xFFFFD98E)) else ToastTone(Color(0xFFFFF0D5), Color(0xFF7A4C00))
    NebToastKind.Error -> if (dark) ToastTone(Color(0xFF451F1D), Color(0xFFFFB9B3)) else ToastTone(Color(0xFFFFE2E0), Color(0xFF9D1D1B))
}

/** The app's toast host: same state and calls as a snackbar host, drawn as [NebToast]. */
@Composable
fun NebToastHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        snackbar = { data -> NebToast(data) }
    )
}

/**
 * One tonal pill-card: outline glyph, message, optional bold action. No tile, no shadow, no timer line.
 * It springs up from below and a swipe sideways throws it away.
 */
@Composable
fun NebToast(data: SnackbarData) {
    val visuals = data.visuals
    val kind = (visuals as? NebToastVisuals)?.kind ?: remember(visuals.message) { nebToastKindOf(visuals.message) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tone = toneOf(kind, dark)
    val icon = when (kind) {
        NebToastKind.Success -> Icons.Outlined.CheckCircle
        NebToastKind.Info -> Icons.Outlined.Info
        NebToastKind.Warning -> Icons.Outlined.WarningAmber
        NebToastKind.Error -> Icons.Outlined.ErrorOutline
    }
    val appear = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }

    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .graphicsLayer {
                val a = appear.value
                translationY = (1f - a) * 32.dp.toPx()
                alpha = (a * 1.5f).coerceIn(0f, 1f) * (1f - (abs(drag.value) / (size.width * 0.9f)).coerceIn(0f, 1f))
                val s = 0.95f + 0.05f * a.coerceAtMost(1.04f)
                scaleX = s
                scaleY = s
            }
            .offset { IntOffset(drag.value.roundToInt(), 0) }
            .pointerInput(data) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(drag.value) > size.width * 0.28f) {
                                drag.animateTo(if (drag.value > 0) size.width.toFloat() else -size.width.toFloat(), tween(140))
                                data.dismiss()
                            } else drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f))
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } }
                ) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + amount) }
                }
            }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(RoundedCornerShape(22.dp))
            .background(tone.card)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = tone.ink, modifier = Modifier.size(24.dp))
        Text(
            visuals.message,
            style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, color = tone.ink),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp)
        )
        val action = visuals.actionLabel
        if (action != null) {
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = action) { data.performAction() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(action, style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tone.ink))
            }
        } else Box(Modifier.size(8.dp))
    }
}
