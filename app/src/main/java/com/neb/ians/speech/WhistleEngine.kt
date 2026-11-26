package com.neb.ians.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

sealed interface DictationState {
    data object Idle : DictationState
    data object Preparing : DictationState
    data object Listening : DictationState
    data object Finishing : DictationState
    data class Error(val message: String) : DictationState
}

data class FileTranscriptionState(
    val blockId: String,
    val progressPercent: Int = 0,
    val partialText: String = "",
)

/**
 * Live dictation and background audio-file transcription with Whistle / Shruti.
 * Every engine call runs on one dedicated thread because the native engine is process-global.
 */
@Singleton
class WhistleEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: SpeechModelManager,
) {
    private val engineThread by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "neb-whistle").apply { priority = Thread.NORM_PRIORITY + 1 }
        }.asCoroutineDispatcher()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    val state: StateFlow<DictationState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    /** 0..1 input loudness for the live meter. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _pending = MutableStateFlow("")
    val pending: StateFlow<String> = _pending.asStateFlow()

    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    private val _committed = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val committed: SharedFlow<String> = _committed.asSharedFlow()

    private val _transcriptions = MutableStateFlow<Map<String, FileTranscriptionState>>(emptyMap())
    val transcriptions: StateFlow<Map<String, FileTranscriptionState>> = _transcriptions.asStateFlow()

    private val fileJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val cancelledBlocks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @Volatile private var loadedPath: String? = null
    @Volatile private var stopRequested = false
    private var session: Job? = null
    private var idleJob: Job? = null

    val supported: Boolean get() = models.sttSupportedCached
    val hasInstalledModel: Boolean get() = models.isAnySttInstalled()
    val isRunning: Boolean get() = session?.isActive == true

    init {
        NebTranscriptionNotificationManager.cancelHandler = { blockId ->
            if (blockId != null) cancelFileTranscription(blockId) else cancelAllFileTranscriptions()
        }
        scope.launch {
            models.removed.collect { id ->
                if (id.isStt) {
                    loadedPath = null
                    cancel()
                    cancelAllFileTranscriptions()
                    scope.launch(engineThread) { NativeSpeech.sttAdviseIdle() }
                }
            }
        }
    }

    fun clearError() {
        if (_state.value is DictationState.Error) _state.value = DictationState.Idle
    }

    /** The caller has already been granted RECORD_AUDIO. */
    fun start() {
        if (isRunning) return
        if (!supported) return fail("Speech to text is not available on this device")
        if (!models.isAnySttInstalled()) return fail("Download the speech to text model first")
        idleJob?.cancel()
        stopRequested = false
        _pending.value = ""
        _seconds.value = 0
        _state.value = DictationState.Preparing
        session = scope.launch {
            try {
                val effectiveLang = resolveEffectiveLang()
                withContext(engineThread) {
                    ensureLoaded()
                    NativeSpeech.sttReset()
                }
                record(effectiveLang)
                _state.value = DictationState.Idle
            } catch (e: CancellationException) {
                _state.value = DictationState.Idle
                throw e
            } catch (t: Throwable) {
                fail(t.message ?: "Dictation stopped")
            } finally {
                _level.value = 0f
                _pending.value = ""
                scheduleIdleTrim()
            }
        }
    }

    private fun resolveEffectiveLang(): String? {
        val activeId = models.activeSttModel()
        val rawLang = models.sttLanguage.value.trim()
        val whistleValid = setOf("en", "de", "fr", "es", "it", "nl", "pl")
        return when (activeId) {
            SpeechModelId.ShrutiNepali -> when {
                rawLang == "ne" || rawLang == "en" -> rawLang
                else -> null
            }
            SpeechModelId.Whistle -> if (rawLang in whistleValid) rawLang else null
            else -> null
        }
    }

    private fun scheduleIdleTrim() {
        idleJob?.cancel()
        idleJob = scope.launch(engineThread) {
            kotlinx.coroutines.delay(60_000)
            if (!isRunning && fileJobs.isEmpty()) NativeSpeech.sttAdviseIdle()
        }
    }

    /** Ends the session after the words still in flight are transcribed. */
    fun stop() {
        stopRequested = true
        if (_state.value == DictationState.Listening) {
            _state.value = DictationState.Finishing
        }
    }

    /** Ends the session now. */
    fun cancel() {
        stopRequested = true
        session?.cancel()
    }

    fun cancelFileTranscription(blockId: String) {
        cancelledBlocks.add(blockId)
        fileJobs.remove(blockId)?.cancel()
        _transcriptions.value = _transcriptions.value - blockId
        if (_transcriptions.value.isEmpty()) {
            NebTranscriptionNotificationManager.dismiss(context)
        }
    }

    fun cancelAllFileTranscriptions() {
        val keys = fileJobs.keys.toList()
        keys.forEach { id ->
            cancelledBlocks.add(id)
            fileJobs.remove(id)?.cancel()
        }
        _transcriptions.value = emptyMap()
        NebTranscriptionNotificationManager.dismiss(context)
    }

    /**
     * Launches background audio-file transcription on the app-wide scope so switching apps
     * or leaving the screen never interrupts transcription, while posting a system notification
     * with live progress and a Cancel button.
     */
    fun startFileTranscription(
        blockId: String,
        filePath: String,
        onPartial: (String) -> Unit = {},
        onFinished: (Result<String>) -> Unit = {},
    ) {
        cancelFileTranscription(blockId)
        cancelledBlocks.remove(blockId)
        if (!supported || !models.isAnySttInstalled()) {
            onFinished(Result.failure(IllegalStateException("Download a speech-to-text model in Voice settings first")))
            return
        }
        val modelLabel = if (models.activeSttModel() == SpeechModelId.ShrutiNepali) SpeechCatalog.SHRUTI_LABEL else SpeechCatalog.WHISTLE_LABEL
        _transcriptions.value = _transcriptions.value + (blockId to FileTranscriptionState(blockId, 2, ""))
        NebTranscriptionNotificationManager.showProgress(context, blockId, modelLabel, 2)

        val job = scope.launch {
            try {
                val samples = withContext(Dispatchers.IO) {
                    decodeAudioFileTo16kMono(filePath) { blockId in cancelledBlocks || !isActive }
                }
                if (blockId in cancelledBlocks || !isActive) throw CancellationException()
                if (samples.isEmpty()) {
                    throw IllegalStateException("Could not decode audio recording")
                }
                _transcriptions.value = _transcriptions.value + (blockId to FileTranscriptionState(blockId, 10, ""))
                NebTranscriptionNotificationManager.showProgress(context, blockId, modelLabel, 10)

                val text = transcribeSamples(
                    samples = samples,
                    shouldCancel = { blockId in cancelledBlocks || !isActive },
                    onProgress = { pct, partial ->
                        val scaledPct = (10 + (pct * 90) / 100).coerceIn(10, 99)
                        _transcriptions.value = _transcriptions.value + (blockId to FileTranscriptionState(blockId, scaledPct, partial))
                        NebTranscriptionNotificationManager.showProgress(context, blockId, modelLabel, scaledPct)
                        if (partial.isNotBlank()) onPartial(partial)
                    }
                )
                if (blockId in cancelledBlocks || !isActive) throw CancellationException()
                onFinished(Result.success(text))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                onFinished(Result.failure(t))
            } finally {
                fileJobs.remove(blockId)
                cancelledBlocks.remove(blockId)
                _transcriptions.value = _transcriptions.value - blockId
                if (_transcriptions.value.isEmpty()) {
                    NebTranscriptionNotificationManager.dismiss(context)
                }
                scheduleIdleTrim()
            }
        }
        fileJobs[blockId] = job
    }

    /**
     * Transcribes a 16 kHz mono float buffer using single-pass 20s silence-aligned windows
     * (`sttReset` -> `sttStream` -> `sttStop` per window) so 20s clips run in 1 pass instead of 7.
     */
    suspend fun transcribeSamples(
        samples: FloatArray,
        shouldCancel: () -> Boolean = { false },
        onProgress: ((Int, String) -> Unit)? = null,
    ): String = withContext(engineThread) {
        if (!supported || !models.isAnySttInstalled() || samples.isEmpty()) return@withContext ""
        idleJob?.cancel()
        val effectiveLang = resolveEffectiveLang()
        val keywords = "NEB\nSEE\nIOE\nCEE\nMECEE\nCTEVT\nKathmandu\nPokhara\nLalitpur\nBhaktapur\nTribhuvan"
        ensureLoaded()
        val out = StringBuilder()
        val rate = 16_000
        val minWindow = rate * 15
        val targetWindow = rate * 20
        val maxWindow = rate * 24
        var offset = 0
        while (offset < samples.size) {
            if (shouldCancel() || !isActive) throw CancellationException()
            val remaining = samples.size - offset
            val end = if (remaining <= maxWindow) {
                samples.size
            } else {
                findQuietSplit(samples, offset + minWindow, min(samples.size, offset + targetWindow + rate * 2))
            }
            val window = if (offset == 0 && end == samples.size) samples else samples.copyOfRange(offset, end)
            NativeSpeech.sttReset()
            val streamJson = NativeSpeech.sttStream(window, window.size, effectiveLang, keywords)
            val stopJson = NativeSpeech.sttStop()
            val windowText = extractWindowTranscript(streamJson, stopJson)
            if (windowText.isNotEmpty()) {
                if (out.isNotEmpty()) out.append(' ')
                out.append(windowText)
            }
            offset = end
            val pct = ((offset.toLong() * 100L) / samples.size.coerceAtLeast(1)).toInt().coerceIn(1, 100)
            onProgress?.invoke(pct, out.toString().trim())
        }
        out.toString().trim()
    }

    private fun extractWindowTranscript(streamJson: String?, stopJson: String?): String {
        var sText = ""
        var sPending = ""
        if (!streamJson.isNullOrBlank()) {
            runCatching {
                val o = JSONObject(streamJson)
                sText = o.optString("text").trim()
                sPending = o.optString("pending").trim()
            }
        }
        var tText = ""
        var tPending = ""
        if (!stopJson.isNullOrBlank()) {
            runCatching {
                val o = JSONObject(stopJson)
                tText = o.optString("text").trim()
                tPending = o.optString("pending").trim()
            }
        }
        val tail = tText.ifEmpty { tPending }.ifEmpty { sPending }
        return when {
            sText.isEmpty() -> tail
            tail.isEmpty() || tail.equals(sText, ignoreCase = true) || sText.endsWith(tail, ignoreCase = true) -> sText
            tail.startsWith(sText, ignoreCase = true) -> tail
            else -> "$sText $tail".trim()
        }
    }

    /** Finds the lowest-energy 50ms frame in `[searchStart, searchEnd)` so windows split on natural pauses. */
    private fun findQuietSplit(samples: FloatArray, searchStart: Int, searchEnd: Int): Int {
        val frame = 800 // 50ms at 16kHz
        if (searchEnd - searchStart <= frame * 2) return searchEnd
        var bestPos = (searchStart + searchEnd) / 2
        var bestEnergy = Float.MAX_VALUE
        var idx = searchStart
        while (idx + frame <= searchEnd) {
            var energy = 0f
            for (i in 0 until frame) {
                val v = samples[idx + i]
                energy += v * v
            }
            if (energy < bestEnergy) {
                bestEnergy = energy
                bestPos = idx + frame / 2
            }
            idx += frame / 2
        }
        return bestPos
    }

    /**
     * Zero-boxing streaming audio decoder & resampler -> 16 kHz mono primitive `FloatArray`.
     * Prevents JVM heap OOM on long audio recordings across low-end and high-end devices.
     */
    fun decodeAudioFileTo16kMono(path: String, shouldCancel: () -> Boolean = { false }): FloatArray {
        val extractor = android.media.MediaExtractor()
        var codec: android.media.MediaCodec? = null
        try {
            extractor.setDataSource(path)
            var trackIndex = -1
            var format: android.media.MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return FloatArray(0)
            extractor.selectTrack(trackIndex)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: return FloatArray(0)
            var srcRate = if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8_000)
            } else 44_100
            var channels = if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1
            var pcmEncoding = if (Build.VERSION.SDK_INT >= 24 && format.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
            } else android.media.AudioFormat.ENCODING_PCM_16BIT

            val durationUs = if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                format.getLong(android.media.MediaFormat.KEY_DURATION).coerceAtLeast(0L)
            } else 0L
            val targetRate = 16_000
            val maxSamples = targetRate * 1200 // Cap at 20 minutes (19.2M floats = 76 MB max primitive array)
            val estSamples = if (durationUs > 0L) {
                ((durationUs / 1_000_000.0) * targetRate + targetRate * 2).toInt().coerceIn(targetRate * 4, maxSamples)
            } else targetRate * 30

            var out = FloatArray(estSamples)
            var outSize = 0
            fun appendOutSample(v: Float) {
                if (outSize >= maxSamples) return
                if (outSize >= out.size) {
                    val nextCap = min(maxSamples, max(out.size + targetRate * 15, (out.size * 3) / 2))
                    out = out.copyOf(nextCap)
                }
                out[outSize++] = v.coerceIn(-1f, 1f)
            }

            // Streaming linear resampler state from srcRate -> 16000 Hz
            var srcPos = 0.0
            var prevMono = 0f
            var hasPrev = false
            fun feedMonoSample(currMono: Float) {
                if (srcRate == targetRate) {
                    appendOutSample(currMono)
                    return
                }
                if (!hasPrev) {
                    prevMono = currMono
                    hasPrev = true
                    appendOutSample(currMono)
                    srcPos = srcRate.toDouble() / targetRate.toDouble()
                    return
                }
                val step = srcRate.toDouble() / targetRate.toDouble()
                while (srcPos <= 1.0) {
                    val frac = srcPos.toFloat()
                    val interp = prevMono + (currMono - prevMono) * frac
                    appendOutSample(interp)
                    srcPos += step
                }
                srcPos -= 1.0
                prevMono = currMono
            }

            codec = android.media.MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = android.media.MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var idleSpin = 0

            while (!sawOutputEOS && outSize < maxSamples && idleSpin < 500) {
                if (shouldCancel()) {
                    return FloatArray(0)
                }
                var progressed = false
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        val sampleSize = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        progressed = true
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.order(java.nio.ByteOrder.nativeOrder())
                            if (pcmEncoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                                val floats = outBuf.asFloatBuffer()
                                val nFloats = floats.remaining()
                                var sIdx = 0
                                while (sIdx + channels <= nFloats) {
                                    var sum = 0f
                                    for (ch in 0 until channels) sum += floats.get(sIdx + ch)
                                    feedMonoSample(sum / channels)
                                    sIdx += channels
                                }
                            } else {
                                val shorts = outBuf.asShortBuffer()
                                val nShorts = shorts.remaining()
                                var sIdx = 0
                                while (sIdx + channels <= nShorts) {
                                    var sum = 0f
                                    for (ch in 0 until channels) sum += shorts.get(sIdx + ch) * (1f / 32768f)
                                    feedMonoSample(sum / channels)
                                    sIdx += channels
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                    }
                    outIdx == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        progressed = true
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                            srcRate = newFmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8_000)
                        }
                        if (newFmt.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = newFmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (Build.VERSION.SDK_INT >= 24 && newFmt.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFmt.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                }
                if (progressed) idleSpin = 0 else idleSpin++
            }
            return if (outSize == out.size) out else out.copyOf(outSize)
        } catch (_: Throwable) {
            return FloatArray(0)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun fail(message: String) {
        _state.value = DictationState.Error(message)
    }

    private fun ensureLoaded() {
        val target = models.activeSttFile().path
        if (loadedPath == target) return
        val rc = NativeSpeech.sttLoad(target)
        if (rc < 0 || (rc and 2) == 0) throw IllegalStateException(NativeSpeech.sttError().ifBlank { "The speech model could not be loaded" })
        loadedPath = target
    }

    @SuppressLint("MissingPermission")
    private suspend fun record(lang: String?) = withContext(Dispatchers.IO) {
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, rate * 2 * 4),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("The microphone is busy")
        }
        val ns = runCatching {
            if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                android.media.audiofx.NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        val agc = runCatching {
            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                android.media.audiofx.AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        val keywords = "NEB\nSEE\nIOE\nCEE\nMECEE\nCTEVT\nKathmandu\nPokhara\nLalitpur\nBhaktapur\nTribhuvan\nKathmandu University\nPokhara University\nPurbanchal\nLok Sewa\nNRB\nMBBS\nCSIT\nBCA\nBIM\nBBA"
        val chunks = Channel<FloatArray>(capacity = 10, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
        val consumer = launch(engineThread) {
            var segmentSamples = 0
            try {
                for (firstChunk in chunks) {
                    var merged = firstChunk
                    while (merged.size < rate * 3) {
                        val next = chunks.tryReceive().getOrNull() ?: break
                        val combined = FloatArray(merged.size + next.size)
                        System.arraycopy(merged, 0, combined, 0, merged.size)
                        System.arraycopy(next, 0, combined, merged.size, next.size)
                        merged = combined
                    }
                    handle(NativeSpeech.sttStream(merged, merged.size, lang, keywords))
                    segmentSamples += merged.size
                    // Cap rolling encoder window at ~8.5s when a natural breath/pause ends a phrase
                    if (segmentSamples >= (rate * 85) / 10) {
                        var tailEnergy = 0.0
                        val tailLen = min(merged.size, rate / 4)
                        val tailStart = merged.size - tailLen
                        for (i in tailStart until merged.size) {
                            val v = merged[i]
                            tailEnergy += v * v
                        }
                        val tailRms = if (tailLen > 0) sqrt(tailEnergy / tailLen).toFloat() else 1f
                        if (tailRms < 0.0032f || segmentSamples >= rate * 14) {
                            handle(NativeSpeech.sttStop())
                            NativeSpeech.sttReset()
                            segmentSamples = 0
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) { handle(NativeSpeech.sttStop()); _pending.value = "" }
            }
        }
        val frame = ShortArray(rate / 5) // 200ms low-latency read frames
        val acc = FloatArray(rate)
        var filled = 0
        var total = 0L
        var chunkTarget = (rate * 65) / 100 // 0.65s first chunk for 35% faster time-to-first-word
        var silentChunks = 0
        var hadSpeechInChunk = false
        var dcMean = 0f
        try {
            rec.startRecording()
            _state.value = DictationState.Listening
            while (isActive && !stopRequested) {
                val maxRead = min(frame.size, acc.size - filled)
                if (maxRead <= 0) {
                    chunks.send(acc.copyOf(filled))
                    filled = 0
                    hadSpeechInChunk = false
                    continue
                }
                val n = rec.read(frame, 0, maxRead)
                if (n < 0) throw IllegalStateException("The microphone stopped")
                if (n == 0) continue
                var sum = 0.0
                for (i in 0 until n) {
                    val raw = frame[i] * (1f / 32768f)
                    dcMean = dcMean * 0.995f + raw * 0.005f
                    val f = (raw - dcMean).coerceIn(-1f, 1f)
                    sum += f * f
                    acc[filled + i] = f
                }
                filled += n
                total += n
                val rms = sqrt(sum / n).toFloat()
                if (rms >= 0.0032f) hadSpeechInChunk = true
                _level.value = min(1f, rms * 7.5f)
                _seconds.value = (total / rate).toInt()
                val vadEndpointFlush = hadSpeechInChunk && rms < 0.0022f && filled >= (rate * 35) / 100
                if (filled >= chunkTarget || vadEndpointFlush) {
                    var chunkSq = 0.0
                    for (i in 0 until filled) chunkSq += acc[i] * acc[i]
                    val chunkRms = sqrt(chunkSq / filled).toFloat()
                    if (chunkRms < 0.0018f && !hadSpeechInChunk) {
                        silentChunks++
                    } else {
                        silentChunks = 0
                    }
                    if (silentChunks <= 2 || _pending.value.isNotEmpty()) {
                        chunks.send(acc.copyOf(filled))
                        chunkTarget = (rate * 85) / 100 // 0.85s steady-state responsive chunks
                    }
                    filled = 0
                    hadSpeechInChunk = false
                }
            }
        } finally {
            runCatching { rec.stop() }
            runCatching { ns?.release() }
            runCatching { agc?.release() }
            rec.release()
            if (filled > 0 && isActive) chunks.trySend(acc.copyOf(filled))
            _state.value = DictationState.Finishing
            chunks.close()
            withContext(NonCancellable) { consumer.join() }
        }
    }

    private fun handle(json: String?) {
        if (json.isNullOrBlank()) return
        try {
            val o = JSONObject(json)
            val text = o.optString("text").trim()
            if (text.isNotEmpty()) _committed.tryEmit(text)
            _pending.value = o.optString("pending").trim()
        } catch (_: Exception) {
        }
    }
}
