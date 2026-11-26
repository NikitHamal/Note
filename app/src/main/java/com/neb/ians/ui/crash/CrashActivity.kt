package com.neb.ians.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.neb.ians.ui.components.nebHorizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.neb.ians.ui.components.NebShapes
import com.neb.ians.ui.components.nebShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neb.ians.MainActivity
import com.neb.ians.ui.theme.NEBiansTheme
import com.neb.ians.ui.components.NebButton
import com.neb.ians.ui.components.NebButtonSize
import com.neb.ians.ui.components.NebButtonTone

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logFromIntent = intent.getStringExtra("crash_log")
        val typeFromIntent = intent.getStringExtra("crash_type")
        val msgFromIntent = intent.getStringExtra("crash_message")

        val prefs = getSharedPreferences("nebians_crash_logs", Context.MODE_PRIVATE)
        val stackTrace = logFromIntent ?: prefs.getString("last_crash_log", "No stack trace recorded.") ?: "No log."
        val crashType = typeFromIntent ?: prefs.getString("last_crash_type", "Unknown Error") ?: "Error"
        val crashMessage = msgFromIntent ?: prefs.getString("last_crash_message", "App encountered an error.") ?: "Error"

        setContent {
            NEBiansTheme {
                CrashScreen(
                    crashType = crashType,
                    crashMessage = crashMessage,
                    stackTrace = stackTrace,
                    onRestart = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

/**
 * The screen after a crash, in M3X: page colour, one expressive tile that springs in, a short title, the error in a
 * hairline card (type as a tonal chip), the log folded away until asked for, and a single filled action at the bottom
 * with copy and share beside it. No shadows, no gradients, no emojis.
 */
@Composable
fun CrashScreen(
    crashType: String,
    crashMessage: String,
    stackTrace: String,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val shortType = crashType.substringAfterLast('.').ifBlank { crashType }
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "crashChevron")
    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1f - pop.value) * -24f }
                    .clip(nebShape(NebShapes.Accent))
                    .background(c.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = c.onErrorContainer, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurface
            )
            Text(
                text = "NEBians closed unexpectedly.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth().clip(cardShape).background(c.surfaceContainerLowest).border(1.dp, c.outlineVariant, cardShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = shortType,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onErrorContainer,
                    maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.errorContainer).padding(horizontal = 12.dp, vertical = 5.dp)
                )
                Text(
                    text = crashMessage.take(400),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurface,
                    maxLines = 8
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().clip(cardShape).background(c.surfaceContainerLowest).border(1.dp, c.outlineVariant, cardShape)
                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(cardShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = !open }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Notes, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Text("Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.onSurface, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (open) "Hide log" else "Show log",
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = turn }
                    )
                }
                if (open) {
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surfaceContainerLow)
                            .verticalScroll(rememberScrollState())
                            .nebHorizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stackTrace,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.onSurfaceVariant,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NebButton(
                    text = if (copied) "Copied" else "Copy log",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("NEBians crash log", stackTrace))
                        copied = true
                    },
                    icon = Icons.Filled.ContentCopy,
                    tone = NebButtonTone.Tonal,
                    size = NebButtonSize.Standard,
                    modifier = Modifier.weight(1f)
                )
                NebButton(
                    text = "Share",
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "NEBians crash log")
                            putExtra(Intent.EXTRA_TEXT, stackTrace)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    icon = Icons.Filled.Share,
                    tone = NebButtonTone.Tonal,
                    size = NebButtonSize.Standard,
                    modifier = Modifier.weight(1f)
                )
            }
            NebButton(
                text = "Restart",
                onClick = onRestart,
                icon = Icons.Filled.Refresh,
                size = NebButtonSize.Hero,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
