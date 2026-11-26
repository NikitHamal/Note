@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neb.ians.ui.screens.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.graphics.shapes.RoundedPolygon
import com.neb.ians.R
import com.neb.ians.ui.components.NebGlyphShapes
import com.neb.ians.ui.components.NebIconButton
import com.neb.ians.ui.components.NebMorphBlob
import com.neb.ians.ui.components.nebPressable
import com.neb.ians.ui.components.nebShape
import com.neb.ians.ui.components.nebToast
import com.neb.ians.ui.theme.NebShapes

private class Credit(
    val shape: RoundedPolygon,
    val icon: ImageVector,
    val name: String,
    val by: String,
    val text: String,
    val toneIndex: Int,
    val url: String,
)

private class KernelSpec(
    val shape: RoundedPolygon,
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val toneIndex: Int,
)

private class PortfolioWork(
    val name: String,
    val summary: String,
    val url: String,
)

private class Library(val name: String, val by: String, val license: String, val url: String)

private val PortfolioWorks = listOf(
    PortfolioWork("NEBians", "Material 3 Expressive Android app and Django platform for Nepali students", "https://nebians.consica.com.np/"),
    PortfolioWork("Nik 0.1", "Sub-10 MB romanized-Nepali hybrid AR + diffusion transformer with custom SIMD kernel", "https://nikit.is-a.dev/"),
    PortfolioWork("Lami Nepal", "Native Android app with real-time chat, FCM push, and full Nepali localization", "https://nikit.is-a.dev/"),
    PortfolioWork("AstroWeb", "Vedic astrology web platform with Kundali and Dasha calculations", "https://nikit.is-a.dev/"),
)

private val Models = listOf(
    Credit(
        shape = NebGlyphShapes.Cookie,
        icon = Icons.Outlined.Translate,
        name = "Shruti Nepali ASR",
        by = "Udgamlabs \u00B7 29.7 MB",
        text = "Nepali and English offline speech-to-text powered by the Needle C++ engine with custom ARM NEON audio conditioning and silence-aligned single-pass windows.",
        toneIndex = 0,
        url = "https://huggingface.co/udgamlabs/shruti-nepali",
    ),
    Credit(
        shape = NebGlyphShapes.Sunny,
        icon = Icons.Outlined.Mic,
        name = "Whistle ASR",
        by = "Cactus Compute \u00B7 17.7 MB",
        text = "Ultra-compact multilingual offline speech recognition for English, German, French, Spanish, Italian, Dutch, and Polish.",
        toneIndex = 1,
        url = "https://huggingface.co/Cactus-Compute/whistle",
    ),
    Credit(
        shape = NebGlyphShapes.Flower,
        icon = Icons.Outlined.GraphicEq,
        name = "TinyVoca Neural TTS",
        by = "n4ze3m + Custom C++ NEON \u00B7 8.0 MB",
        text = "Two-voice (F2/M2) offline English and Devanagari Nepali text-to-speech running on our custom 4x2 register-blocked ARM NEON GEMM and radix-2/4 FFT cepstral vocoder.",
        toneIndex = 2,
        url = "https://huggingface.co/n4ze3m/tiny-voca",
    ),
    Credit(
        shape = NebGlyphShapes.Clover,
        icon = Icons.Outlined.RecordVoiceOver,
        name = "Phonon 31M TTS",
        by = "Gradium \u00B7 65.1 MB",
        text = "Natural neural voice reader with on-demand voice catalog that reads notes aloud offline. Apache-2.0.",
        toneIndex = 0,
        url = "https://huggingface.co/gradium/phonon-31m-en",
    ),
)

private val Kernels = listOf(
    KernelSpec(
        shape = NebGlyphShapes.Cookie,
        icon = Icons.Outlined.Memory,
        title = "4x2 ARM NEON GEMM",
        detail = "Register-blocked matrix kernel reuses weight rows across 4 time-steps in 16 SIMD accumulators",
        toneIndex = 0,
    ),
    KernelSpec(
        shape = NebGlyphShapes.Sunny,
        icon = Icons.Outlined.Bolt,
        title = "Single-Pass Needle ASR",
        detail = "Direct needle_transcribe batch path + rolling 8.5s VAD pause reset for lag-free dictation",
        toneIndex = 1,
    ),
    KernelSpec(
        shape = NebGlyphShapes.Flower,
        icon = Icons.Outlined.Speed,
        title = "Vectorized NEON FFT & GELU",
        detail = "4-wide complex butterfly SIMD stages + Abramowitz-Stegun NEON GELU and LayerNorm",
        toneIndex = 2,
    ),
    KernelSpec(
        shape = NebGlyphShapes.Gem,
        icon = Icons.Outlined.Mic,
        title = "Hardware FTZ & Auto-Gain",
        detail = "ARM64/ARMv7 Flush-To-Zero denormal elimination + zero-copy JNI NEON mic normalization",
        toneIndex = 0,
    ),
)

private val Libraries = listOf(
    Library("Jetpack Compose, Material 3 Expressive", "Google", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    Library("Kotlin, Coroutines, Serialization", "JetBrains", "Apache-2.0", "https://kotlinlang.org/"),
    Library("Room, Hilt, DataStore", "Google", "Apache-2.0", "https://developer.android.com/jetpack"),
    Library("Needle Speech Runtime", "Cactus Compute", "Apache-2.0", "https://huggingface.co/Cactus-Compute/whistle"),
    Library("OkHttp", "Square", "Apache-2.0", "https://square.github.io/okhttp/"),
    Library("Coil", "Coil contributors", "Apache-2.0", "https://coil-kt.github.io/coil/"),
    Library("Poppins", "Indian Type Foundry", "SIL OFL 1.1", "https://fonts.google.com/specimen/Poppins"),
    Library("Material Symbols and icons", "Google", "Apache-2.0", "https://fonts.google.com/icons"),
)

@Composable
private fun roleTonePair(index: Int): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> cs.primaryContainer to cs.onPrimaryContainer
        1 -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.tertiaryContainer to cs.onTertiaryContainer
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenVoice: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val info = remember(context) { AppInfo.read(context) }

    Scaffold(
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = { NebIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Poster(onOpenVoice = onOpenVoice)

            VersionRow(info) {
                copy(context, "Note ${info.name} (${info.build}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                context.nebToast("Version copied")
            }

            Heading("Creator & Developer")
            CreatorCard(
                onOpenPortfolio = { openUrl(context, "https://nikit.is-a.dev/") },
                onOpenProject = { url -> openUrl(context, url) },
            )

            Heading("Runs on your phone")
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Models.forEachIndexed { i, m ->
                    CreditTile(
                        c = m,
                        shape = NebShapes.stackItem(i, Models.size, outer = 24.dp, inner = 8.dp),
                        onClick = { openUrl(context, m.url) },
                    )
                }
            }

            Heading("Custom C++ inference kernels")
            val kernelRows = remember { Kernels.chunked(2) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                kernelRows.forEachIndexed { r, pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pair.forEachIndexed { c, k ->
                            KernelGridTile(
                                k = k,
                                shape = NebShapes.gridCell(r, c, kernelRows.size, pair.size, outer = 24.dp, inner = 8.dp),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            Heading("Built on open source")
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Libraries.forEachIndexed { i, l ->
                    LibraryRow(
                        l = l,
                        shape = NebShapes.stackItem(i, Libraries.size, outer = 22.dp, inner = 6.dp),
                        onClick = { openUrl(context, l.url) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun Poster(onOpenVoice: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var shapeIdx by remember { mutableIntStateOf(0) }
    val shapes = remember {
        listOf(
            NebGlyphShapes.Cookie,
            NebGlyphShapes.Sunny,
            NebGlyphShapes.Flower,
            NebGlyphShapes.Clover,
            NebGlyphShapes.Burst,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(28.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .nebPressable(scale = 0.94f) { shapeIdx = (shapeIdx + 1) % shapes.size },
                contentAlignment = Alignment.Center,
            ) {
                val morphPos by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = shapeIdx.toFloat(),
                    animationSpec = com.neb.ians.ui.theme.nebSpatialSpec(),
                    label = "aboutMorphPos",
                )
                NebMorphBlob(
                    shapes = shapes,
                    position = { morphPos },
                    color = scheme.primaryContainer,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(R.drawable.ic_nebians_logo),
                    contentDescription = "Note",
                    modifier = Modifier.size(56.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Note",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Local AI dictation, Nepali Shruti ASR, transcription, and neural voice notes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.primaryContainer)
                .nebPressable(scale = 0.97f, onClick = onOpenVoice)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.GraphicEq, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Text(
                "Manage on-device voice models",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CreatorCard(onOpenPortfolio: () -> Unit, onOpenProject: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NebShapes.stackItem(0, 2, outer = 24.dp, inner = 8.dp))
                .background(scheme.surfaceContainerLowest)
                .border(1.dp, scheme.outlineVariant, NebShapes.stackItem(0, 2, outer = 24.dp, inner = 8.dp))
                .nebPressable(scale = 0.985f, onClick = onOpenPortfolio)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(nebShape(NebGlyphShapes.Cookie))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "NH",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Nikit Hamal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Creator & Developer",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.Language, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                    Text(
                        "nikit.is-a.dev",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSecondaryContainer,
                        maxLines = 1,
                    )
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                "Specializing in native Android development with Kotlin and Jetpack Compose, on-device AI inference, custom SIMD kernels, and Python/Django architectures.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }

        val workRows = remember { PortfolioWorks.chunked(2) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            workRows.forEachIndexed { r, pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pair.forEachIndexed { c, w ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(NebShapes.gridCell(r, c, workRows.size, pair.size, outer = 22.dp, inner = 8.dp))
                                .background(scheme.surfaceContainerLow)
                                .nebPressable(scale = 0.98f) { onOpenProject(w.url) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    w.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                w.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelGridTile(k: KernelSpec, shape: Shape, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (fill, onFill) = roleTonePair(k.toneIndex)
    Column(
        modifier = modifier
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(nebShape(k.shape))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(k.icon, null, tint = onFill, modifier = Modifier.size(20.dp))
        }
        Text(
            k.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            k.detail,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VersionRow(info: AppInfo, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val cells = listOf(
        "Version" to info.name,
        "Build" to info.build.toString(),
        "Engine" to "NEON C++17",
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.forEachIndexed { idx, (label, value) ->
            val shape = NebShapes.rowItem(idx, cells.size, outer = 22.dp, inner = 6.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(scheme.surfaceContainerLowest)
                    .border(1.dp, scheme.outlineVariant, shape)
                    .nebPressable(scale = 0.97f, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (idx == cells.lastIndex) {
                        Icon(Icons.Outlined.ContentCopy, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    }
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun CreditTile(c: Credit, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (fill, onFill) = roleTonePair(c.toneIndex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, shape)
            .nebPressable(scale = 0.985f, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(nebShape(c.shape))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(c.icon, null, tint = onFill, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    c.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    c.by,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
            Text(
                c.text,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryRow(l: Library, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, shape)
            .nebPressable(scale = 0.985f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Code, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                l.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${l.by} \u00B7 ${l.license}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

private class AppInfo(val name: String, val build: Long) {
    companion object {
        fun read(context: Context): AppInfo = try {
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            AppInfo(p.versionName ?: "1.0.0", PackageInfoCompat.getLongVersionCode(p))
        } catch (_: Exception) {
            AppInfo("1.0.0", 1L)
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("version", text))
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
