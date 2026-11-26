package com.neb.ians.speech

import android.util.Log

/** Receives the 24 kHz mono PCM that Phonon or TinyVoca produces; return false to stop early. */
interface PcmSink {
    fun onPcm(pcm: FloatArray): Boolean
}

/**
 * JNI surface of `libneb_speech.so` (see `src/main/cpp`). Whistle / Shruti (speech to text)
 * and TinyVoca / Phonon (text to speech) are single-threaded engines: callers serialise every call
 * through one dedicated thread each (see [WhistleEngine] and [PhononEngine]).
 */
internal object NativeSpeech {
    private val loaded: Boolean = try {
        System.loadLibrary("neb_speech")
        true
    } catch (t: Throwable) {
        Log.e("NebSpeech", "libneb_speech failed to load", t)
        false
    }

    val sttSupported: Boolean by lazy { loaded && sttCompiled() }

    /** TinyVoca runs in pure C++ inside libneb_speech.so and works on all supported ABIs. */
    val vocaSupported: Boolean get() = loaded

    /** Phonon's library is shipped for arm64 only and needs ARMv8.2 dotprod + fp16. */
    val phononSupported: Boolean by lazy { loaded && cpuHasDotprodAndFp16() && ttsAvailable() }

    /** True whenever at least one on-device TTS engine (TinyVoca or Phonon) is supported. */
    val ttsSupported: Boolean by lazy { vocaSupported || phononSupported }

    private fun cpuHasDotprodAndFp16(): Boolean = try {
        val info = java.io.File("/proc/cpuinfo").readText()
        val features = info.lineSequence().firstOrNull { it.startsWith("Features") }.orEmpty()
        features.contains("asimddp") && (features.contains("asimdhp") || features.contains("fphp"))
    } catch (_: Throwable) {
        false
    }

    // Whistle & Shruti Nepali (On-device C++ STT engine)
    external fun sttCompiled(): Boolean
    external fun sttLoad(path: String): Int
    external fun sttReset()
    external fun sttAdviseIdle()
    external fun sttError(): String
    external fun sttStream(pcm: FloatArray, samples: Int, lang: String?, keywords: String?): String?
    external fun sttStop(): String?

    // Phonon (Gradium 31M TTS)
    external fun ttsAvailable(): Boolean
    external fun ttsLoad(dir: String, lang: String, threads: Int): Long
    external fun ttsError(handle: Long): String
    external fun ttsVoices(handle: Long): Array<String>
    external fun ttsSetVoice(handle: Long, name: String): Boolean
    external fun ttsSpeak(handle: Long, text: String, sink: PcmSink): Boolean
    external fun ttsFree(handle: Long)

    // TinyVoca (4.5M two-voice F2/M2 TTS, 8 MB GGUF)
    external fun vocaLoad(path: String): Long
    external fun vocaError(): String
    external fun vocaSpeak(handle: Long, text: String, voiceId: Int, speed: Float, sink: PcmSink): Boolean
    external fun vocaFree(handle: Long)

    // Native C++ anti-tamper & HMAC-SHA256 request proof bridge
    external fun envFlags(apkPath: String): Int
    external fun signProof(payload: String): String

    fun safeAdviseIdle() {
        if (loaded) runCatching { sttAdviseIdle() }
    }

    fun safeEnvFlags(apkPath: String): Int =
        if (loaded) runCatching { envFlags(apkPath) }.getOrDefault(0) else 0

    fun safeSignProof(payload: String): String? =
        if (loaded) runCatching { signProof(payload) }.getOrNull()?.takeIf { it.length == 24 } else null
}
