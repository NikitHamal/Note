package com.neb.ians.speech

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class SpeechModelId(val isStt: Boolean) {
    ShrutiNepali(true),
    Whistle(true),
    TinyVoca(false),
    Phonon(false),
}

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val done: Long, val total: Long) : ModelState {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }
    data class Installed(val bytes: Long) : ModelState
    data class Failed(val message: String) : ModelState
}

data class ModelFile(val name: String, val url: String, val bytes: Long)

/** A voice from Gradium's public catalog (`gradium/phonon-voices`). */
data class PhononVoice(
    val name: String,
    val remotePath: String,
    val gender: String,
    val age: String,
    val description: String,
)

sealed interface VoiceCatalog {
    data object Loading : VoiceCatalog
    data class Ready(val voices: List<PhononVoice>) : VoiceCatalog
    data object Unavailable : VoiceCatalog
}

object SpeechCatalog {
    const val SHRUTI_LABEL = "Shruti"
    const val WHISTLE_LABEL = "Whistle"
    const val TINY_VOCA_LABEL = "TinyVoca"
    const val PHONON_LABEL = "Phonon"

    private const val SHRUTI_BASE = "https://huggingface.co/udgamlabs/shruti-nepali/resolve/main/"
    private const val WHISTLE_BASE = "https://huggingface.co/Cactus-Compute/whistle/resolve/main/"
    private const val TINY_VOCA_BASE = "https://huggingface.co/n4ze3m/tiny-voca/resolve/main/"
    private const val PHONON_BASE = "https://huggingface.co/gradium/phonon-31m-en/resolve/main/"
    const val VOICES_BASE = "https://huggingface.co/gradium/phonon-voices/resolve/main/"

    val shrutiFiles = listOf(
        ModelFile("shruti-nepali.cact", SHRUTI_BASE + "shruti-nepali.cact", 29_659_439L),
    )

    val whistleFiles = listOf(
        ModelFile("whistle.cact", WHISTLE_BASE + "whistle.cact", 17_720_000L),
    )

    val tinyVocaFiles = listOf(
        ModelFile("tiny-voca-js.gguf", TINY_VOCA_BASE + "gguf/tiny-voca-js.gguf", 7_988_384L),
    )

    val phononFiles = listOf(
        ModelFile("config.json", PHONON_BASE + "config.json", 4_634L),
        ModelFile("tokenizer.json", PHONON_BASE + "tokenizer.json", 277_967L),
        ModelFile("default-voice.safetensors", PHONON_BASE + "default-voice.safetensors", 256_136L),
        ModelFile("model.q8.gguf", PHONON_BASE + "model.q8.gguf", 64_512_352L),
    )

    val shrutiLanguages = listOf(
        "ne" to "Ne+EN",
        "en" to "English only",
        "" to "Auto",
    )

    val whistleLanguages = listOf(
        "" to "Auto", "en" to "English", "de" to "German", "fr" to "French",
        "es" to "Spanish", "it" to "Italian", "nl" to "Dutch", "pl" to "Polish",
    )

    val tinyVocaVoices = listOf(
        "F2" to "Female \u00B7 English & Nepali",
        "M2" to "Male \u00B7 English & Nepali",
    )

    fun files(id: SpeechModelId): List<ModelFile> = when (id) {
        SpeechModelId.ShrutiNepali -> shrutiFiles
        SpeechModelId.Whistle -> whistleFiles
        SpeechModelId.TinyVoca -> tinyVocaFiles
        SpeechModelId.Phonon -> phononFiles
    }

    fun totalBytes(id: SpeechModelId): Long = files(id).sumOf { it.bytes }
}

/**
 * Owns the on-device speech models: download with resume, remove, and the voice catalog.
 * Models live in `filesDir/speech`, not in the APK, and are only fetched when the user
 * asks for them. Downloads run in an app-wide scope so they survive leaving the screen.
 */
@Singleton
class SpeechModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val root by lazy { File(context.filesDir, "speech") }
    private val prefs by lazy { context.getSharedPreferences("neb_speech", Context.MODE_PRIVATE) }

    // A separate lazy client: nothing from the app's API client (auth headers) should reach a CDN.
    private val client by lazy {
        OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(4, 2, TimeUnit.MINUTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val shrutiFile: File get() = File(root, "shruti-nepali/shruti-nepali.cact")
    val whistleFile: File get() = File(root, "whistle/whistle.cact")
    val tinyVocaFile: File get() = File(root, "tiny-voca/tiny-voca-js.gguf")
    val phononDir: File get() = File(root, "phonon-31m-en")
    private val voicesDir: File get() = File(phononDir, "voices")

    private val _shruti = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    private val _whistle = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    private val _tinyVoca = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    private val _phonon = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    val shruti: StateFlow<ModelState> = _shruti.asStateFlow()
    val whistle: StateFlow<ModelState> = _whistle.asStateFlow()
    val tinyVoca: StateFlow<ModelState> = _tinyVoca.asStateFlow()
    val phonon: StateFlow<ModelState> = _phonon.asStateFlow()

    private val _selectedStt = MutableStateFlow(SpeechModelId.ShrutiNepali)
    val selectedStt: StateFlow<SpeechModelId> = _selectedStt.asStateFlow()

    private val _selectedTts = MutableStateFlow(SpeechModelId.TinyVoca)
    val selectedTts: StateFlow<SpeechModelId> = _selectedTts.asStateFlow()

    private val _stt = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    private val _tts = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    val stt: StateFlow<ModelState> = _stt.asStateFlow()
    val tts: StateFlow<ModelState> = _tts.asStateFlow()

    private val _installedVoices = MutableStateFlow<List<String>>(emptyList())
    val installedVoices: StateFlow<List<String>> = _installedVoices.asStateFlow()

    private val _voiceBusy = MutableStateFlow<Set<String>>(emptySet())
    val voiceBusy: StateFlow<Set<String>> = _voiceBusy.asStateFlow()

    private val _catalog = MutableStateFlow<VoiceCatalog>(VoiceCatalog.Loading)
    val catalog: StateFlow<VoiceCatalog> = _catalog.asStateFlow()

    private val _sttLanguage = MutableStateFlow("ne")
    val sttLanguage: StateFlow<String> = _sttLanguage.asStateFlow()

    private val _voice = MutableStateFlow("default")
    val voice: StateFlow<String> = _voice.asStateFlow()

    private val _vocaVoice = MutableStateFlow("F2")
    val vocaVoice: StateFlow<String> = _vocaVoice.asStateFlow()

    @Volatile var sttSupportedCached: Boolean = true; private set
    @Volatile var vocaSupportedCached: Boolean = true; private set
    @Volatile var phononSupportedCached: Boolean = true; private set
    @Volatile var ttsSupportedCached: Boolean = true; private set

    init {
        scope.launch {
            _shruti.value = initialState(SpeechModelId.ShrutiNepali)
            _whistle.value = initialState(SpeechModelId.Whistle)
            _tinyVoca.value = initialState(SpeechModelId.TinyVoca)
            _phonon.value = initialState(SpeechModelId.Phonon)

            val savedStt = prefs.getString("selected_stt", null)
            _selectedStt.value = when {
                savedStt == SpeechModelId.Whistle.name -> SpeechModelId.Whistle
                savedStt == SpeechModelId.ShrutiNepali.name -> SpeechModelId.ShrutiNepali
                _shruti.value is ModelState.Installed -> SpeechModelId.ShrutiNepali
                _whistle.value is ModelState.Installed -> SpeechModelId.Whistle
                else -> SpeechModelId.ShrutiNepali
            }
            val savedTts = prefs.getString("selected_tts", null)
            _selectedTts.value = when {
                savedTts == SpeechModelId.Phonon.name -> SpeechModelId.Phonon
                savedTts == SpeechModelId.TinyVoca.name -> SpeechModelId.TinyVoca
                _tinyVoca.value is ModelState.Installed -> SpeechModelId.TinyVoca
                _phonon.value is ModelState.Installed -> SpeechModelId.Phonon
                else -> SpeechModelId.TinyVoca
            }
            val defaultLang = if (_selectedStt.value == SpeechModelId.ShrutiNepali) "ne" else ""
            _sttLanguage.value = prefs.getString("stt_lang", defaultLang) ?: defaultLang
            _voice.value = prefs.getString("voice", "default") ?: "default"
            _vocaVoice.value = prefs.getString("voca_voice", "F2") ?: "F2"
            _installedVoices.value = scanVoices()
            syncAggregateStates()
            sttSupportedCached = runCatching { NativeSpeech.sttSupported }.getOrDefault(false)
            vocaSupportedCached = runCatching { NativeSpeech.vocaSupported }.getOrDefault(false)
            phononSupportedCached = runCatching { NativeSpeech.phononSupported }.getOrDefault(false)
            ttsSupportedCached = vocaSupportedCached || phononSupportedCached
        }
    }

    private fun syncAggregateStates() {
        val sttSel = stateFlow(_selectedStt.value).value
        _stt.value = if (sttSel is ModelState.Installed || sttSel is ModelState.Downloading) sttSel
        else if (_shruti.value is ModelState.Installed) _shruti.value
        else if (_whistle.value is ModelState.Installed) _whistle.value
        else sttSel

        val ttsSel = stateFlow(_selectedTts.value).value
        _tts.value = if (ttsSel is ModelState.Installed || ttsSel is ModelState.Downloading) ttsSel
        else if (_tinyVoca.value is ModelState.Installed) _tinyVoca.value
        else if (_phonon.value is ModelState.Installed) _phonon.value
        else ttsSel
    }

    private val _removed = MutableSharedFlow<SpeechModelId>(extraBufferCapacity = 4)
    val removed: SharedFlow<SpeechModelId> = _removed.asSharedFlow()

    private val jobs = java.util.concurrent.ConcurrentHashMap<SpeechModelId, Job>()

    fun stateOf(id: SpeechModelId): StateFlow<ModelState> = stateFlow(id)
    fun isInstalled(id: SpeechModelId): Boolean {
        val cur = stateFlow(id).value
        if (cur is ModelState.Installed) return true
        if (cur is ModelState.Downloading) return false
        val disk = initialState(id)
        if (disk is ModelState.Installed) {
            stateFlow(id).value = disk
            syncAggregateStates()
            return true
        }
        return false
    }
    fun isAnySttInstalled(): Boolean = isInstalled(SpeechModelId.ShrutiNepali) || isInstalled(SpeechModelId.Whistle)
    fun isAnyTtsInstalled(): Boolean = isInstalled(SpeechModelId.TinyVoca) || isInstalled(SpeechModelId.Phonon)

    fun activeSttModel(): SpeechModelId {
        val sel = _selectedStt.value
        if (isInstalled(sel)) return sel
        if (isInstalled(SpeechModelId.ShrutiNepali)) return SpeechModelId.ShrutiNepali
        if (isInstalled(SpeechModelId.Whistle)) return SpeechModelId.Whistle
        return sel
    }

    fun activeSttFile(): File = if (activeSttModel() == SpeechModelId.ShrutiNepali) shrutiFile else whistleFile

    fun activeTtsModel(): SpeechModelId {
        val sel = _selectedTts.value
        if (isInstalled(sel)) return sel
        if (isInstalled(SpeechModelId.TinyVoca)) return SpeechModelId.TinyVoca
        if (isInstalled(SpeechModelId.Phonon)) return SpeechModelId.Phonon
        return sel
    }

    fun selectSttModel(id: SpeechModelId) {
        if (!id.isStt) return
        _selectedStt.value = id
        if (id == SpeechModelId.ShrutiNepali && _sttLanguage.value !in listOf("ne", "en", "")) {
            setSttLanguage("ne")
        } else if (id == SpeechModelId.Whistle && _sttLanguage.value !in SpeechCatalog.whistleLanguages.map { it.first }) {
            setSttLanguage("")
        }
        syncAggregateStates()
        scope.launch { prefs.edit().putString("selected_stt", id.name).apply() }
    }

    fun selectTtsModel(id: SpeechModelId) {
        if (id.isStt) return
        _selectedTts.value = id
        syncAggregateStates()
        scope.launch { prefs.edit().putString("selected_tts", id.name).apply() }
    }

    fun setSttLanguage(code: String) {
        _sttLanguage.value = code
        scope.launch { prefs.edit().putString("stt_lang", code).apply() }
    }

    fun setVoice(name: String) {
        _voice.value = name
        scope.launch { prefs.edit().putString("voice", name).apply() }
    }

    fun setVocaVoice(name: String) {
        _vocaVoice.value = name
        scope.launch { prefs.edit().putString("voca_voice", name).apply() }
    }

    private fun stateFlow(id: SpeechModelId): MutableStateFlow<ModelState> = when (id) {
        SpeechModelId.ShrutiNepali -> _shruti
        SpeechModelId.Whistle -> _whistle
        SpeechModelId.TinyVoca -> _tinyVoca
        SpeechModelId.Phonon -> _phonon
    }

    private fun modelDir(id: SpeechModelId): File = when (id) {
        SpeechModelId.ShrutiNepali -> File(root, "shruti-nepali")
        SpeechModelId.Whistle -> File(root, "whistle")
        SpeechModelId.TinyVoca -> File(root, "tiny-voca")
        SpeechModelId.Phonon -> phononDir
    }

    private fun isValidModelFile(f: File, expectedBytes: Long): Boolean =
        f.exists() && f.length() >= (expectedBytes * 95L / 100L).coerceAtLeast(1L)

    private fun initialState(id: SpeechModelId): ModelState {
        val files = SpeechCatalog.files(id)
        val dir = modelDir(id)
        val present = files.all { isValidModelFile(File(dir, it.name), it.bytes) }
        return if (present) ModelState.Installed(files.sumOf { File(dir, it.name).length() }) else ModelState.NotInstalled
    }

    private fun scanVoices(): List<String> {
        val names = voicesDir.listFiles { f -> f.extension == "safetensors" && f.length() > 1024L }?.map { it.nameWithoutExtension }.orEmpty()
        return names.sorted()
    }

    fun download(id: SpeechModelId) {
        if (jobs[id]?.isActive == true) return
        val total = SpeechCatalog.totalBytes(id)
        val dir = modelDir(id)
        val state = stateFlow(id)
        if (id.isStt) selectSttModel(id) else selectTtsModel(id)
        jobs[id] = scope.launch {
            try {
                dir.mkdirs()
                val free = StatFs(context.filesDir.path).availableBytes
                if (free < total * 12 / 10) throw IOException("Not enough free storage")
                state.value = ModelState.Downloading(0, total)
                syncAggregateStates()
                var finished = 0L
                for (file in SpeechCatalog.files(id)) {
                    val dest = File(dir, file.name)
                    if (isValidModelFile(dest, file.bytes)) {
                        finished += dest.length()
                        state.value = ModelState.Downloading(finished, total)
                        syncAggregateStates()
                        continue
                    } else if (dest.exists()) {
                        dest.delete()
                    }
                    val base = finished
                    fetch(file.url, dest) { current ->
                        state.value = ModelState.Downloading(base + current, total)
                        syncAggregateStates()
                    }
                    if (!isValidModelFile(dest, file.bytes)) {
                        dest.delete()
                        throw IOException("Downloaded model file is incomplete")
                    }
                    finished += dest.length()
                }
                state.value = ModelState.Installed(finished)
                if (id == SpeechModelId.Phonon) _installedVoices.value = scanVoices()
                if (id == SpeechModelId.ShrutiNepali && _sttLanguage.value.isBlank()) setSttLanguage("ne")
                syncAggregateStates()
            } catch (e: CancellationException) {
                state.value = initialState(id)
                syncAggregateStates()
                throw e
            } catch (e: Exception) {
                state.value = ModelState.Failed(friendly(e))
                syncAggregateStates()
            }
        }
    }

    fun cancel(id: SpeechModelId) {
        jobs[id]?.cancel()
    }

    fun remove(id: SpeechModelId) {
        jobs[id]?.cancel()
        scope.launch {
            _removed.emit(id)
            kotlinx.coroutines.delay(150)
            modelDir(id).deleteRecursively()
            stateFlow(id).value = ModelState.NotInstalled
            if (id == SpeechModelId.Phonon) _installedVoices.value = emptyList()
            syncAggregateStates()
        }
    }

    fun loadCatalog() {
        scope.launch {
            val cache = File(root, "voices.jsonl")
            if (_catalog.value !is VoiceCatalog.Ready && cache.exists()) {
                runCatching { parseCatalog(cache.readText()) }.getOrNull()?.let {
                    _catalog.value = VoiceCatalog.Ready(it)
                }
            }
            try {
                val text = client.newCall(Request.Builder().url(SpeechCatalog.VOICES_BASE + "voices.jsonl").build())
                    .execute().use { r ->
                        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                        r.body?.string().orEmpty()
                    }
                val voices = parseCatalog(text)
                if (voices != null) {
                    root.mkdirs()
                    cache.writeText(text)
                    _catalog.value = VoiceCatalog.Ready(voices)
                }
            } catch (_: Exception) {
                if (_catalog.value !is VoiceCatalog.Ready) _catalog.value = VoiceCatalog.Unavailable
            }
        }
    }

    private fun parseCatalog(text: String): List<PhononVoice>? {
        val out = ArrayList<PhononVoice>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val o = try { JSONObject(line) } catch (_: Exception) { continue }
            // Phonon 31m speaks English only.
            if (o.optString("language") != "en") continue
            val name = o.optString("name").filter { it.isLetterOrDigit() }
            if (name.isEmpty()) continue
            out += PhononVoice(
                name = name,
                remotePath = o.optString("filename"),
                gender = o.optString("gender"),
                age = o.optString("age"),
                description = o.optString("description"),
            )
        }
        return out.takeIf { it.isNotEmpty() }
    }

    fun downloadVoice(voice: PhononVoice) {
        if (voice.name in _voiceBusy.value) return
        _voiceBusy.update { it + voice.name }
        scope.launch {
            try {
                voicesDir.mkdirs()
                val dest = File(voicesDir, voice.name + ".safetensors")
                fetch(SpeechCatalog.VOICES_BASE + voice.remotePath, dest) {}
                if (!dest.exists() || dest.length() <= 1024L) {
                    dest.delete()
                    throw IOException("Voice download incomplete")
                }
                _installedVoices.value = scanVoices()
            } catch (_: Exception) {
                // The row goes back to its download button; the user can retry.
            } finally {
                _voiceBusy.update { it - voice.name }
            }
        }
    }

    fun removeVoice(name: String) {
        scope.launch {
            if (_voice.value == name) setVoice("default")
            File(voicesDir, "$name.safetensors").delete()
            _installedVoices.value = scanVoices()
        }
    }

    private suspend fun fetch(url: String, dest: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        var have = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(url)
            .header("User-Agent", "NEBians-Android")
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 416) {
                // The partial file is already the whole file.
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) throw IOException("Could not save the file")
                return@withContext
            }
            if (!resp.isSuccessful) throw IOException("Server answered ${resp.code}")
            val append = resp.code == 206
            if (!append) have = 0L
            val body = resp.body ?: throw IOException("Empty response")
            val remaining = body.contentLength()
            var written = have
            FileOutputStream(part, append).use { out ->
                val buf = ByteArray(64 * 1024)
                val input = body.byteStream()
                var lastTick = 0L
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    written += n
                    val now = System.nanoTime()
                    if (now - lastTick > 80_000_000L) {
                        lastTick = now
                        onProgress(written)
                    }
                }
            }
            if (remaining >= 0 && written != have + remaining) throw IOException("The download was cut short")
            onProgress(written)
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IOException("Could not save the file")
    }

    private fun friendly(e: Exception): String = when {
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
            "No connection. Try again when you are online."
        e is IOException && e.message?.startsWith("Not enough") == true -> "Not enough free storage"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Download failed"
    }
}
