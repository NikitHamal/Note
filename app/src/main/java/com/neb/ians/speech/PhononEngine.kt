package com.neb.ians.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

sealed interface ReadState {
    data object Idle : ReadState
    data object Loading : ReadState
    data object Speaking : ReadState
    data object Paused : ReadState
    data class Error(val message: String) : ReadState
}

data class ReadProgress(val index: Int, val total: Int, val text: String)

/**
 * Read aloud with Phonon. Text is spoken one sentence block at a time so pause, skip and
 * stop respond within a sentence; audio streams to an [AudioTrack] as it is generated.
 * Every native call runs on one thread, and the loaded model is released after a quiet
 * minute so it does not sit in memory.
 */
@Singleton
class PhononEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: SpeechModelManager,
) {
    private class Run(val segments: List<String>, val voice: String?) {
        @Volatile var cancelled = false
        @Volatile var paused = false
        @Volatile var jump = -1
    }

    private val engineThread by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "neb-phonon") }.asCoroutineDispatcher() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state.asStateFlow()
    private val _progress = MutableStateFlow<ReadProgress?>(null)
    val progress: StateFlow<ReadProgress?> = _progress.asStateFlow()
    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    @Volatile private var run: Run? = null
    @Volatile private var track: AudioTrack? = null
    private var phononHandle = 0L
    private var vocaHandle = 0L
    private var loadedVoices: List<String> = emptyList()
    private var appliedVoice: String? = null
    private var idleJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    val supported: Boolean get() = models.ttsSupportedCached

    init {
        activeInstance = this
        scope.launch {
            models.removed.collect { id ->
                if (!id.isStt) {
                    stop()
                    scope.launch(engineThread) { release() }
                }
            }
        }
    }

    fun releaseIfIdle() {
        if (run != null) return
        idleJob?.cancel()
        if (phononHandle != 0L || vocaHandle != 0L) {
            scope.launch(engineThread) {
                if (run == null) release()
            }
        }
    }

    companion object {
        @Volatile
        private var activeInstance: PhononEngine? = null

        fun trimMemoryIfIdle() {
            activeInstance?.releaseIfIdle()
        }
    }

    fun clearError() {
        if (_state.value is ReadState.Error) _state.value = ReadState.Idle
    }

    /** Pre-warms TinyVoca on the background engine thread so Read-Aloud starts with zero cold-load latency. */
    fun prewarmIfInstalled() {
        if (!supported || run != null) return
        if (models.activeTtsModel() == SpeechModelId.TinyVoca && models.isInstalled(SpeechModelId.TinyVoca)) {
            idleJob?.cancel()
            scope.launch(engineThread) {
                runCatching { ensureVocaLoaded() }
                idleJob = scope.launch(engineThread) {
                    delay(90_000)
                    if (run == null) release()
                }
            }
        }
    }

    fun play(segments: List<String>, from: Int = 0, voice: String? = null) {
        if (segments.isEmpty()) return
        if (!supported) { _state.value = ReadState.Error("Text to speech is not available on this device"); return }
        if (!models.isAnyTtsInstalled()) { _state.value = ReadState.Error("Download the text to speech model first"); return }
        run?.cancelled = true
        val r = Run(segments, voice)
        run = r
        idleJob?.cancel()
        _state.value = ReadState.Loading
        _progress.value = ReadProgress(from.coerceIn(0, segments.lastIndex), segments.size, segments[from.coerceIn(0, segments.lastIndex)])
        scope.launch(engineThread) { loop(r, from.coerceIn(0, segments.lastIndex)) }
    }

    private val mediaController = object : NebPlaybackNotificationManager.Controller {
        override val id: String = "phonon_tts"
        override fun onPlayPause() = toggle()
        override fun onSeekTo(posMs: Long) {
            val idx = (posMs / 1000L).toInt()
            seekTo(idx)
        }
        override fun onStop() = stop()
    }

    private fun updatePlaybackNotification(isPlaying: Boolean) {
        val p = _progress.value ?: return
        NebPlaybackNotificationManager.registerAndUpdate(
            context = context,
            controller = mediaController,
            title = p.text.take(72),
            subtitle = "Reading note (${p.index + 1}/${p.total})",
            isPlaying = isPlaying,
            positionMs = p.index * 1000L,
            durationMs = p.total * 1000L,
            speed = _speed.value,
        )
    }

    fun cycleSpeed() {
        val next = when (_speed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        _speed.value = next
        if (models.activeTtsModel() == SpeechModelId.Phonon) {
            runCatching {
                track?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.let { t ->
                    t.playbackParams = android.media.PlaybackParams().setSpeed(next)
                }
            }
        }
        updatePlaybackNotification(_state.value == ReadState.Speaking)
    }

    fun pause() {
        val r = run ?: return
        if (_state.value != ReadState.Speaking) return
        r.paused = true
        runCatching { track?.pause() }
        _state.value = ReadState.Paused
        updatePlaybackNotification(false)
    }

    fun resume() {
        val r = run ?: return
        if (_state.value != ReadState.Paused) return
        r.paused = false
        runCatching { track?.play() }
        _state.value = ReadState.Speaking
        updatePlaybackNotification(true)
    }

    fun toggle() = if (_state.value == ReadState.Paused) resume() else pause()

    fun seekTo(index: Int) {
        val r = run ?: return
        r.jump = index.coerceIn(0, r.segments.lastIndex)
    }

    fun next() {
        val p = _progress.value ?: return
        seekTo(p.index + 1)
    }

    fun previous() {
        val p = _progress.value ?: return
        seekTo(p.index - 1)
    }

    fun stop() {
        run?.cancelled = true
        run = null
        _state.value = ReadState.Idle
        _progress.value = null
        runCatching { track?.pause(); track?.flush() }
        NebPlaybackNotificationManager.clearIfOwned(context, "phonon_tts")
    }

    /** Releases the loaded models; a later [play] reloads whichever engine is active. */
    private fun release() {
        if (phononHandle != 0L) NativeSpeech.ttsFree(phononHandle)
        phononHandle = 0L
        if (vocaHandle != 0L) NativeSpeech.vocaFree(vocaHandle)
        vocaHandle = 0L
        loadedVoices = emptyList()
        appliedVoice = null
    }

    private fun ensureVocaLoaded() {
        if (vocaHandle != 0L) return
        vocaHandle = NativeSpeech.vocaLoad(models.tinyVocaFile.path)
        if (vocaHandle == 0L) throw IllegalStateException(NativeSpeech.vocaError().ifBlank { "The TinyVoca model could not be loaded" })
    }

    private fun ensurePhononLoaded(voice: String) {
        val wanted = voice.ifBlank { "default" }
        val stale = phononHandle != 0L && wanted != "default" && wanted !in loadedVoices
        if (stale) {
            NativeSpeech.ttsFree(phononHandle)
            phononHandle = 0L
            loadedVoices = emptyList()
            appliedVoice = null
        }
        if (phononHandle == 0L) {
            val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
            phononHandle = NativeSpeech.ttsLoad(models.phononDir.path, "en", threads)
            if (phononHandle == 0L) throw IllegalStateException(NativeSpeech.ttsError(0).ifBlank { "The voice model could not be loaded" })
            loadedVoices = NativeSpeech.ttsVoices(phononHandle).toList()
        }
        val target = if (wanted in loadedVoices) wanted else loadedVoices.firstOrNull { it == "default" }
        if (target != null && target != appliedVoice) {
            if (NativeSpeech.ttsSetVoice(phononHandle, target)) appliedVoice = target
        }
    }

    private fun loop(r: Run, from: Int) {
        var audioTrack: AudioTrack? = null
        try {
            val activeModel = models.activeTtsModel()
            val useVoca = activeModel == SpeechModelId.TinyVoca || (!models.phononSupportedCached && models.isInstalled(SpeechModelId.TinyVoca))
            if (useVoca) {
                ensureVocaLoaded()
            } else {
                ensurePhononLoaded(r.voice ?: models.voice.value)
            }
            if (r.cancelled) return
            audioTrack = buildTrack()
            track = audioTrack
            requestFocus()
            audioTrack.play()
            _state.value = ReadState.Speaking
            val counter = LongArray(1)
            var i = from
            while (!r.cancelled && i < r.segments.size) {
                _progress.value = ReadProgress(i, r.segments.size, r.segments[i])
                updatePlaybackNotification(!r.paused)
                val t = audioTrack
                val sink = object : PcmSink {
                    override fun onPcm(pcm: FloatArray): Boolean = !r.cancelled && r.jump < 0 && writeAll(t, pcm, r, counter)
                }
                val curSpeed = _speed.value.coerceIn(0.5f, 2.0f)
                val ok = if (useVoca) {
                    val vName = (r.voice ?: models.vocaVoice.value).uppercase()
                    val vId = if (vName == "M2") 1 else 0
                    NativeSpeech.vocaSpeak(vocaHandle, r.segments[i], vId, curSpeed, sink)
                } else {
                    if (curSpeed != 1.0f) {
                        runCatching { t.playbackParams = android.media.PlaybackParams().setSpeed(curSpeed) }
                    }
                    NativeSpeech.ttsSpeak(phononHandle, r.segments[i], sink)
                }
                if (r.cancelled) break
                val jump = r.jump
                if (jump >= 0) {
                    r.jump = -1
                    i = jump
                    runCatching { t.pause(); t.flush(); counter[0] = 0; if (!r.paused) t.play() }
                    continue
                }
                if (!ok) {
                    val err = if (useVoca) NativeSpeech.vocaError() else NativeSpeech.ttsError(phononHandle)
                    throw IllegalStateException(err.ifBlank { "Speech stopped unexpectedly" })
                }
                i++
            }
            if (!r.cancelled) drain(audioTrack, r, counter[0])
        } catch (t: Throwable) {
            if (!r.cancelled) _state.value = ReadState.Error(t.message ?: "Read aloud stopped")
            release()
        } finally {
            runCatching { audioTrack?.stop() }
            runCatching { audioTrack?.release() }
            if (track === audioTrack) track = null
            abandonFocus()
            if (run === r || run == null) {
                if (_state.value !is ReadState.Error) _state.value = ReadState.Idle
                _progress.value = null
                run = null
                NebPlaybackNotificationManager.clearIfOwned(context, "phonon_tts")
            }
            idleJob = scope.launch(engineThread) {
                delay(60_000)
                if (run == null) release()
            }
        }
    }

    private fun writeAll(t: AudioTrack, pcm: FloatArray, r: Run, counter: LongArray): Boolean {
        var off = 0
        while (off < pcm.size) {
            while (r.paused && !r.cancelled && r.jump < 0) {
                try { Thread.sleep(40) } catch (_: InterruptedException) { return false }
            }
            if (r.cancelled || r.jump >= 0) return false
            val w = t.write(pcm, off, pcm.size - off, AudioTrack.WRITE_NON_BLOCKING)
            if (w < 0) return false
            if (w == 0) {
                try { Thread.sleep(8) } catch (_: InterruptedException) { return false }
            } else {
                off += w
                counter[0] += w.toLong()
            }
        }
        return true
    }

    private fun drain(t: AudioTrack, r: Run, written: Long) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!r.cancelled && r.jump < 0 && System.currentTimeMillis() < deadline) {
            val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (head >= written && !r.paused) break
            try { Thread.sleep(40) } catch (_: InterruptedException) { return }
            if (r.paused) continue
        }
    }

    private fun buildTrack(): AudioTrack {
        val rate = 24_000
        val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBytes, rate * 4 * 3))
            .build()
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = req
            audio.requestAudioFocus(req)
        } else {
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            audio.abandonAudioFocus(focusListener)
        }
    }
}
