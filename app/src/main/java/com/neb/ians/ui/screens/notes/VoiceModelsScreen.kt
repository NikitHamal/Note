@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.neb.ians.ui.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neb.ians.speech.ModelState
import com.neb.ians.speech.SpeechCatalog
import com.neb.ians.speech.SpeechModelId

internal fun formatBytes(b: Long): String =
    if (b >= 1_000_000) String.format(java.util.Locale.US, "%.1f MB", b / 1_000_000.0)
    else String.format(java.util.Locale.US, "%d KB", (b / 1000).coerceAtLeast(1))

@Composable
fun VoiceModelsScreen(
    onNavigateBack: () -> Unit,
    vm: SpeechViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.loadCatalog() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NoteButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onNavigateBack)
                Text("Voice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SttModelCard(vm, inSettings = true) }
            item { TtsModelCard(vm, inSettings = true) }
        }
    }
}

private fun sizeLabel(name: String, id: SpeechModelId) = "$name \u00B7 ${formatBytes(SpeechCatalog.totalBytes(id))}"

/** The model picker: inside the card on the settings screen, above it in the setup sheet. */
@Composable
private fun <T> ModelPicker(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) =
    NoteSegments(options, selected, onSelect, Modifier.fillMaxWidth(), height = 48.dp)

@Composable
fun SttModelCard(vm: SpeechViewModel, inSettings: Boolean) {
    val selected by vm.selectedStt.collectAsStateWithLifecycle()
    val shrutiState by vm.shruti.collectAsStateWithLifecycle()
    val whistleState by vm.whistleModel.collectAsStateWithLifecycle()
    val lang by vm.sttLanguage.collectAsStateWithLifecycle()
    val shruti = selected == SpeechModelId.ShrutiNepali
    val label = if (shruti) SpeechCatalog.SHRUTI_LABEL else SpeechCatalog.WHISTLE_LABEL
    val langs = if (shruti) SpeechCatalog.shrutiLanguages else SpeechCatalog.whistleLanguages
    val models = listOf(
        SpeechModelId.ShrutiNepali to sizeLabel(SpeechCatalog.SHRUTI_LABEL, SpeechModelId.ShrutiNepali),
        SpeechModelId.Whistle to sizeLabel(SpeechCatalog.WHISTLE_LABEL, SpeechModelId.Whistle),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!inSettings) ModelPicker(models, selected) { vm.selectStt(it) }
        ModelShell(
            icon = Icons.Outlined.Mic,
            title = "Speech to text",
            meta = if (inSettings) null else sizeLabel(label, selected),
            state = if (shruti) shrutiState else whistleState,
            supported = vm.sttSupported,
            inSheet = !inSettings,
            onDownload = { vm.download(selected) },
            onCancel = { vm.cancel(selected) },
            onRemove = { vm.remove(selected) },
            picker = { if (inSettings) ModelPicker(models, selected) { vm.selectStt(it) } },
        ) {
            if (inSettings) NoteSegments(langs, lang, { vm.setSttLanguage(it) }, Modifier.fillMaxWidth(), height = 48.dp)
        }
    }
}

@Composable
fun TtsModelCard(vm: SpeechViewModel, inSettings: Boolean) {
    val selected by vm.selectedTts.collectAsStateWithLifecycle()
    val vocaState by vm.tinyVoca.collectAsStateWithLifecycle()
    val phononState by vm.phononModel.collectAsStateWithLifecycle()
    val voca = selected == SpeechModelId.TinyVoca
    val label = if (voca) SpeechCatalog.TINY_VOCA_LABEL else SpeechCatalog.PHONON_LABEL
    val models = listOf(
        SpeechModelId.TinyVoca to sizeLabel(SpeechCatalog.TINY_VOCA_LABEL, SpeechModelId.TinyVoca),
        SpeechModelId.Phonon to sizeLabel(SpeechCatalog.PHONON_LABEL, SpeechModelId.Phonon),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!inSettings) ModelPicker(models, selected) { vm.selectTts(it) }
        ModelShell(
            icon = Icons.Outlined.RecordVoiceOver,
            title = "Text to speech",
            meta = if (inSettings) null else sizeLabel(label, selected),
            state = if (voca) vocaState else phononState,
            supported = if (voca) vm.vocaSupported else vm.phononSupported,
            inSheet = !inSettings,
            onDownload = { vm.download(selected) },
            onCancel = { vm.cancel(selected) },
            onRemove = { vm.remove(selected) },
            picker = { if (inSettings) ModelPicker(models, selected) { vm.selectTts(it) } },
        ) {
            if (inSettings) { if (voca) TinyVocaVoiceList(vm) else VoiceList(vm) }
        }
    }
}

/** Shown from the editor when a voice feature is used before its model is on the phone. */
@Composable
fun VoiceSetupSheet(
    forDictation: Boolean,
    onReady: () -> Unit,
    onDismiss: () -> Unit,
    vm: SpeechViewModel = hiltViewModel(),
) {
    val sttState by vm.stt.collectAsStateWithLifecycle()
    val ttsState by vm.tts.collectAsStateWithLifecycle()
    val state = if (forDictation) sttState else ttsState
    LaunchedEffect(state) {
        if (state is ModelState.Installed) onReady()
    }
    NoteSheet(onDismiss = onDismiss, title = if (forDictation) "Dictate your notes" else "Listen to your notes", inset = 16.dp) {
        Column(Modifier.padding(bottom = 20.dp)) {
            if (forDictation) SttModelCard(vm, inSettings = false) else TtsModelCard(vm, inSettings = false)
        }
    }
}
