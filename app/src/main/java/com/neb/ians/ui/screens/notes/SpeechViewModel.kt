package com.neb.ians.ui.screens.notes

import androidx.lifecycle.ViewModel
import com.neb.ians.speech.ModelState
import com.neb.ians.speech.PhononEngine
import com.neb.ians.speech.PhononVoice
import com.neb.ians.speech.ReadState
import com.neb.ians.speech.SpeechModelId
import com.neb.ians.speech.SpeechModelManager
import com.neb.ians.speech.VoiceCatalog
import com.neb.ians.speech.WhistleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** State and actions for setting up the on-device speech models. */
@HiltViewModel
class SpeechViewModel @Inject constructor(
    private val models: SpeechModelManager,
    private val whistle: WhistleEngine,
    private val phonon: PhononEngine,
) : ViewModel() {
    val stt: StateFlow<ModelState> = models.stt
    val tts: StateFlow<ModelState> = models.tts
    val shruti: StateFlow<ModelState> = models.shruti
    val whistleModel: StateFlow<ModelState> = models.whistle
    val tinyVoca: StateFlow<ModelState> = models.tinyVoca
    val phononModel: StateFlow<ModelState> = models.phonon
    val selectedStt: StateFlow<SpeechModelId> = models.selectedStt
    val selectedTts: StateFlow<SpeechModelId> = models.selectedTts
    val sttLanguage: StateFlow<String> = models.sttLanguage
    val voice: StateFlow<String> = models.voice
    val vocaVoice: StateFlow<String> = models.vocaVoice
    val installedVoices: StateFlow<List<String>> = models.installedVoices
    val voiceBusy: StateFlow<Set<String>> = models.voiceBusy
    val catalog: StateFlow<VoiceCatalog> = models.catalog
    val readState: StateFlow<ReadState> = phonon.state

    val sttSupported: Boolean get() = models.sttSupportedCached
    val vocaSupported: Boolean get() = models.vocaSupportedCached
    val phononSupported: Boolean get() = models.phononSupportedCached
    val ttsSupported: Boolean get() = models.ttsSupportedCached

    fun download(id: SpeechModelId) = models.download(id)
    fun cancel(id: SpeechModelId) = models.cancel(id)
    fun remove(id: SpeechModelId) = models.remove(id)
    fun selectStt(id: SpeechModelId) = models.selectSttModel(id)
    fun selectTts(id: SpeechModelId) = models.selectTtsModel(id)
    fun setSttLanguage(code: String) = models.setSttLanguage(code)
    fun loadCatalog() = models.loadCatalog()
    fun downloadVoice(v: PhononVoice) = models.downloadVoice(v)
    fun removeVoice(name: String) = models.removeVoice(name)
    fun useVoice(name: String) = models.setVoice(name)
    fun useVocaVoice(name: String) = models.setVocaVoice(name)

    fun preview(name: String) {
        if (phonon.state.value == ReadState.Speaking || phonon.state.value == ReadState.Loading) {
            phonon.stop()
            return
        }
        val samples = if (name == "F2" || name == "M2") {
            listOf("नमस्ते, म तपाईंको टिपोट यसरी पढ्नेछु।", "Hello, this is how I will read your notes.")
        } else {
            listOf("Hello, this is how I will read your notes.")
        }
        phonon.play(samples, 0, name)
    }

    fun clearError() = phonon.clearError()

    override fun onCleared() {
        phonon.stop()
        super.onCleared()
    }
}
