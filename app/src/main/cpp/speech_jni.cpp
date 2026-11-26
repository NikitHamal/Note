// JNI bridge for the on-device speech engines used by Notes.
//
//  - Speech to text: Cactus Whistle on the Needle engine (statically linked, see needle/).
//  - Text to speech: Gradium Phonon, libptts_ffi.so, opened with dlopen so the app still
//    runs on ABIs and phones where that library is not shipped.
//
// Both engines are process-global or single-threaded: the Kotlin side serialises every call.

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstddef>
#include <string>
#include <vector>

#include "tiny_voca.h"

#ifndef NEB_NO_NEEDLE
#include "needle.h"
#endif

#include <cstring>

#ifdef NDEBUG
#define LOGE(...) ((void)0)
#else
#define LOG_TAG "NebSpeech"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif

namespace {

std::string toStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return std::string();
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

jstring toJava(JNIEnv* env, const char* s) {
    return env->NewStringUTF(s ? s : "");
}

std::string unxor(const uint8_t* enc, size_t n, uint8_t k) {
    std::string s(n, '\0');
    for (size_t i = 0; i < n; ++i) s[i] = static_cast<char>(enc[i] ^ static_cast<uint8_t>(k + i * 7u));
    return s;
}

// ------------------------------------------------------------------ Hardware FTZ & NEON Audio Kernels

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#endif
#include <algorithm>
#include <cmath>

inline void enableFlushToZero() {
#if defined(__aarch64__)
    uint64_t fpcr = 0;
    __asm__ __volatile__("mrs %0, fpcr" : "=r"(fpcr));
    if ((fpcr & (1ULL << 24)) == 0) {
        fpcr |= (1ULL << 24);
        __asm__ __volatile__("msr fpcr, %0" : : "r"(fpcr));
    }
#elif defined(__arm__) && defined(__VFP_FP__) && !defined(__SOFTFP__)
    uint32_t fpscr = 0;
    __asm__ __volatile__("vmrs %0, fpscr" : "=r"(fpscr));
    if ((fpscr & (1U << 24)) == 0) {
        fpscr |= (1U << 24);
        __asm__ __volatile__("vmsr fpscr, %0" : : "r"(fpscr));
    }
#endif
}

// ------------------------------------------------------------------ Whistle / Shruti (STT)

#ifndef NEB_NO_NEEDLE
void* gModelMap = nullptr;
size_t gModelLen = 0;
std::vector<char> gSttStreamBuf;
std::vector<float> gPcmScratch;
bool gStreamFresh = true;
bool gBatchTranscribed = false;

int conditionPcmNeon(const float* src, int n, std::vector<float>& dst, bool trimSilence) {
    if (n <= 0 || src == nullptr) return 0;
    dst.resize(static_cast<size_t>(n));
    float* out = dst.data();
    float peak = 0.0f;
    int i = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    float32x4_t vMax = vdupq_n_f32(0.0f);
    float32x4_t vOne = vdupq_n_f32(1.0f);
    float32x4_t vNegOne = vdupq_n_f32(-1.0f);
    float32x4_t vEps = vdupq_n_f32(1e-6f);
    float32x4_t vZero = vdupq_n_f32(0.0f);
    for (; i + 16 <= n; i += 16) {
        for (int k = 0; k < 16; k += 4) {
            float32x4_t v = vld1q_f32(src + i + k);
            v = vmaxq_f32(vNegOne, vminq_f32(vOne, v));
            uint32x4_t mask = vcgtq_f32(vabsq_f32(v), vEps);
            v = vbslq_f32(mask, v, vZero);
            vMax = vmaxq_f32(vMax, vabsq_f32(v));
            vst1q_f32(out + i + k, v);
        }
    }
#if defined(__aarch64__)
    peak = vmaxvq_f32(vMax);
#else
    float32x2_t m2 = vmax_f32(vget_low_f32(vMax), vget_high_f32(vMax));
    peak = vget_lane_f32(vpmax_f32(m2, m2), 0);
#endif
#endif
    for (; i < n; ++i) {
        float v = src[i];
        if (!(v >= -1.0f && v <= 1.0f)) v = 0.0f;
        float av = std::fabs(v);
        if (av < 1e-6f) { v = 0.0f; av = 0.0f; }
        if (av > peak) peak = av;
        out[i] = v;
    }
    if (peak >= 0.012f && peak < 0.28f) {
        const float gain = std::min(3.8f, 0.62f / peak);
        int j = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
        float32x4_t vGain = vdupq_n_f32(gain);
        for (; j + 16 <= n; j += 16) {
            vst1q_f32(out + j, vmulq_f32(vld1q_f32(out + j), vGain));
            vst1q_f32(out + j + 4, vmulq_f32(vld1q_f32(out + j + 4), vGain));
            vst1q_f32(out + j + 8, vmulq_f32(vld1q_f32(out + j + 8), vGain));
            vst1q_f32(out + j + 12, vmulq_f32(vld1q_f32(out + j + 12), vGain));
        }
#endif
        for (; j < n; ++j) out[j] *= gain;
    }
    if (!trimSilence || n < 4000) return n;
    const float silThresh = 0.0025f;
    int firstVoice = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    float32x4_t vSil = vdupq_n_f32(silThresh);
    while (firstVoice + 16 <= n) {
        uint32x4_t m0 = vcgeq_f32(vabsq_f32(vld1q_f32(out + firstVoice)), vSil);
        uint32x4_t m1 = vcgeq_f32(vabsq_f32(vld1q_f32(out + firstVoice + 4)), vSil);
        uint32x4_t m2 = vcgeq_f32(vabsq_f32(vld1q_f32(out + firstVoice + 8)), vSil);
        uint32x4_t m3 = vcgeq_f32(vabsq_f32(vld1q_f32(out + firstVoice + 12)), vSil);
        uint32x4_t any = vorrq_u32(vorrq_u32(m0, m1), vorrq_u32(m2, m3));
        uint32x2_t any2 = vorr_u32(vget_low_u32(any), vget_high_u32(any));
        if (vget_lane_u32(vpmax_u32(any2, any2), 0) != 0) break;
        firstVoice += 16;
    }
#endif
    while (firstVoice < n && std::fabs(out[firstVoice]) < silThresh) ++firstVoice;
    if (firstVoice >= n) return 0;
    int lastVoice = n - 1;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    while (lastVoice - 16 >= firstVoice) {
        int base = lastVoice - 15;
        uint32x4_t m0 = vcgeq_f32(vabsq_f32(vld1q_f32(out + base)), vSil);
        uint32x4_t m1 = vcgeq_f32(vabsq_f32(vld1q_f32(out + base + 4)), vSil);
        uint32x4_t m2 = vcgeq_f32(vabsq_f32(vld1q_f32(out + base + 8)), vSil);
        uint32x4_t m3 = vcgeq_f32(vabsq_f32(vld1q_f32(out + base + 12)), vSil);
        uint32x4_t any = vorrq_u32(vorrq_u32(m0, m1), vorrq_u32(m2, m3));
        uint32x2_t any2 = vorr_u32(vget_low_u32(any), vget_high_u32(any));
        if (vget_lane_u32(vpmax_u32(any2, any2), 0) != 0) break;
        lastVoice -= 16;
    }
#endif
    while (lastVoice > firstVoice && std::fabs(out[lastVoice]) < silThresh) --lastVoice;
    int start = std::max(0, firstVoice - 1920);
    int end = std::min(n, lastVoice + 2400);
    int len = end - start;
    if (start > 0 && len > 0) std::memmove(out, out + start, static_cast<size_t>(len) * sizeof(float));
    return len;
}
#endif

jboolean JNICALL nativeSttCompiled(JNIEnv*, jobject) {
#ifndef NEB_NO_NEEDLE
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

// Maps the .cact read-only and hands it to the engine. The mapping is kept for the life of
// the process: the engine has no unload call and may keep pointing into it.
jint JNICALL nativeSttLoad(JNIEnv* env, jobject, jstring path) {
#ifdef NEB_NO_NEEDLE
    (void) env; (void) path;
    return -1;
#else
    enableFlushToZero();
    std::string p = toStd(env, path);
    int fd = open(p.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -2;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size <= 0) { close(fd); return -3; }
    void* map = mmap(nullptr, (size_t) st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (map == MAP_FAILED) return -4;
    madvise(map, (size_t) st.st_size, MADV_WILLNEED | MADV_SEQUENTIAL);
    int rc = -5;
    try {
        rc = needle_load(reinterpret_cast<const unsigned char*>(map), (unsigned long long) st.st_size);
    } catch (...) {
        rc = -6;
    }
    if (rc < 0) {
        munmap(map, (size_t) st.st_size);
        return rc;
    }
    if (gModelMap != nullptr && gModelLen > 0) {
        munmap(gModelMap, gModelLen);
    }
    gModelMap = map;
    gModelLen = (size_t) st.st_size;
    gStreamFresh = true;
    gBatchTranscribed = false;
    return needle_models();
#endif
}

void JNICALL nativeSttReset(JNIEnv*, jobject) {
#ifndef NEB_NO_NEEDLE
    gStreamFresh = true;
    gBatchTranscribed = false;
    try { needle_reset(); } catch (...) {}
#endif
}

void JNICALL nativeSttAdviseIdle(JNIEnv*, jobject) {
#ifndef NEB_NO_NEEDLE
    if (gModelMap != nullptr && gModelLen > 0) {
        madvise(gModelMap, gModelLen, MADV_DONTNEED);
    }
    std::vector<char>().swap(gSttStreamBuf);
    std::vector<float>().swap(gPcmScratch);
#endif
}

jstring JNICALL nativeSttError(JNIEnv* env, jobject) {
#ifdef NEB_NO_NEEDLE
    return toJava(env, "This device build has no speech engine");
#else
    return toJava(env, needle_last_error());
#endif
}

jstring JNICALL nativeSttStream(
        JNIEnv* env, jobject, jfloatArray pcm, jint samples, jstring lang, jstring keywords) {
#ifdef NEB_NO_NEEDLE
    (void) env; (void) pcm; (void) samples; (void) lang; (void) keywords;
    return nullptr;
#else
    if (pcm == nullptr || samples <= 0) return nullptr;
    enableFlushToZero();
    std::string l = toStd(env, lang), k = toStd(env, keywords);
    bool explicitBatch = false;
    if (k.rfind("@batch\n", 0) == 0) {
        explicitBatch = true;
        k.erase(0, 7);
    }
    const bool useSinglePassBatch = explicitBatch || (gStreamFresh && samples >= 64000);
    int condSamples = 0;
    jfloat* crit = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(pcm, nullptr));
    if (crit != nullptr) {
        condSamples = conditionPcmNeon(crit, samples, gPcmScratch, useSinglePassBatch);
        env->ReleasePrimitiveArrayCritical(pcm, crit, JNI_ABORT);
    } else {
        jfloat* data = env->GetFloatArrayElements(pcm, nullptr);
        if (data == nullptr) return nullptr;
        condSamples = conditionPcmNeon(data, samples, gPcmScratch, useSinglePassBatch);
        env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);
    }
    if (condSamples <= 0) {
        if (useSinglePassBatch) {
            gStreamFresh = false;
            gBatchTranscribed = true;
        }
        return toJava(env, "{\"text\":\"\",\"pending\":\"\"}");
    }
    if (gSttStreamBuf.size() < (1 << 17)) gSttStreamBuf.resize(1 << 17, 0);
    const int cap = (int) gSttStreamBuf.size();
    gSttStreamBuf[0] = '\0';
    int rc = -1;
    try {
        if (useSinglePassBatch) {
            gStreamFresh = false;
            gBatchTranscribed = true;
            rc = needle_transcribe(
                gPcmScratch.data(), condSamples,
                l.empty() ? nullptr : l.c_str(),
                k.empty() ? nullptr : k.c_str(),
                0, gSttStreamBuf.data(), cap
            );
        } else {
            gStreamFresh = false;
            gBatchTranscribed = false;
            rc = needle_stream_transcribe_process(
                gPcmScratch.data(), condSamples,
                l.empty() ? nullptr : l.c_str(),
                k.empty() ? nullptr : k.c_str(),
                gSttStreamBuf.data(), cap
            );
        }
    } catch (...) {
        rc = -1;
    }
    if (rc < 0) return nullptr;
    return toJava(env, gSttStreamBuf.data());
#endif
}

jstring JNICALL nativeSttStop(JNIEnv* env, jobject) {
#ifdef NEB_NO_NEEDLE
    (void) env;
    return nullptr;
#else
    gStreamFresh = true;
    if (gBatchTranscribed) {
        gBatchTranscribed = false;
        return toJava(env, "{\"text\":\"\",\"pending\":\"\"}");
    }
    if (gSttStreamBuf.size() < (1 << 17)) gSttStreamBuf.resize(1 << 17, 0);
    const int cap = (int) gSttStreamBuf.size();
    gSttStreamBuf[0] = '\0';
    int rc = -1;
    try {
        rc = needle_stream_transcribe_stop(gSttStreamBuf.data(), cap);
    } catch (...) {
        rc = -1;
    }
    if (rc < 0) return nullptr;
    return toJava(env, gSttStreamBuf.data());
#endif
}

// ------------------------------------------------------------------ Phonon (TTS)

struct PttsHandle;
struct PttsResult {
    uint32_t frames;
    size_t samples;
    double ttfa_ms;
    double total_ms;
};
typedef bool (*PttsFrameFn)(const float* pcm, size_t n, void* user);

struct PttsApi {
    void* lib = nullptr;
    PttsHandle* (*ptts_new)(const char*, uint32_t, const char*) = nullptr;
    bool (*ptts_speak)(PttsHandle*, const char*, PttsFrameFn, void*, PttsResult*) = nullptr;
    const char* (*ptts_voices)(const PttsHandle*) = nullptr;
    bool (*ptts_set_voice)(PttsHandle*, const char*) = nullptr;
    bool (*ptts_set_language)(PttsHandle*, const char*) = nullptr;
    const char* (*ptts_last_error)(const PttsHandle*) = nullptr;
    void (*ptts_free)(PttsHandle*) = nullptr;
    bool tried = false;
    bool ok = false;
};

PttsApi gTts;

bool openTts() {
    if (gTts.tried) return gTts.ok;
    gTts.tried = true;
    static const uint8_t kPttsLib[14] = {
        0x35,0x09,0x05,0x1e,0x01,0x08,0xf0,0xd5,0xf7,0xfe,0xf6,0x88,0xde,0xdb
    };
    gTts.lib = dlopen(unxor(kPttsLib, 14, 0x59).c_str(), RTLD_NOW | RTLD_LOCAL);
    if (gTts.lib == nullptr) {
        LOGE("tts unavailable: %s", dlerror());
        return false;
    }
#define BIND(name) gTts.name = reinterpret_cast<decltype(gTts.name)>(dlsym(gTts.lib, #name))
    BIND(ptts_new);
    BIND(ptts_speak);
    BIND(ptts_voices);
    BIND(ptts_set_voice);
    BIND(ptts_set_language);
    BIND(ptts_last_error);
    BIND(ptts_free);
#undef BIND
    gTts.ok = gTts.ptts_new && gTts.ptts_speak && gTts.ptts_voices && gTts.ptts_set_voice &&
              gTts.ptts_set_language && gTts.ptts_last_error && gTts.ptts_free;
    return gTts.ok;
}

struct SpeakCtx {
    JNIEnv* env;
    jobject sink;
    jmethodID onPcm;
    jfloatArray cachedArr = nullptr;
    jsize cachedCap = 0;
    bool failed = false;
};

bool onFrame(const float* pcm, size_t n, void* user) {
    SpeakCtx* ctx = static_cast<SpeakCtx*>(user);
    JNIEnv* env = ctx->env;
    jsize len = (jsize) n;
    if (ctx->cachedArr == nullptr || ctx->cachedCap != len) {
        if (ctx->cachedArr != nullptr) env->DeleteLocalRef(ctx->cachedArr);
        ctx->cachedArr = env->NewFloatArray(len);
        ctx->cachedCap = len;
    }
    if (ctx->cachedArr == nullptr) { ctx->failed = true; return false; }
    env->SetFloatArrayRegion(ctx->cachedArr, 0, len, pcm);
    jboolean keep = env->CallBooleanMethod(ctx->sink, ctx->onPcm, ctx->cachedArr);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        ctx->failed = true;
        return false;
    }
    return keep == JNI_TRUE;
}

std::string gVocaLastError;

jboolean JNICALL nativeTtsAvailable(JNIEnv*, jobject) {
    return openTts() ? JNI_TRUE : JNI_FALSE;
}

jlong JNICALL nativeTtsLoad(JNIEnv* env, jobject, jstring dir, jstring lang, jint threads) {
    if (!openTts()) return 0;
    char buf[16];
    snprintf(buf, sizeof(buf), "%d", threads > 0 ? threads : 2);
    setenv("RAYON_NUM_THREADS", buf, 0);
    std::string d = toStd(env, dir), l = toStd(env, lang);
    PttsHandle* h = gTts.ptts_new(d.c_str(), 0, l.c_str());
    return reinterpret_cast<jlong>(h);
}

jstring JNICALL nativeTtsError(JNIEnv* env, jobject, jlong handle) {
    if (!gTts.ok) return toJava(env, "Text to speech is not available on this device");
    return toJava(env, gTts.ptts_last_error(reinterpret_cast<PttsHandle*>(handle)));
}

jobjectArray JNICALL nativeTtsVoices(JNIEnv* env, jobject, jlong handle) {
    jclass str = env->FindClass("java/lang/String");
    std::vector<std::string> names;
    if (gTts.ok && handle != 0) {
        const char* p = gTts.ptts_voices(reinterpret_cast<PttsHandle*>(handle));
        while (p && *p) {
            names.emplace_back(p);
            p += names.back().size() + 1;
        }
    }
    jobjectArray arr = env->NewObjectArray((jsize) names.size(), str, nullptr);
    for (size_t i = 0; i < names.size(); i++) {
        jstring s = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(arr, (jsize) i, s);
        env->DeleteLocalRef(s);
    }
    return arr;
}

jboolean JNICALL nativeTtsSetVoice(JNIEnv* env, jobject, jlong handle, jstring name) {
    if (!gTts.ok || handle == 0) return JNI_FALSE;
    std::string n = toStd(env, name);
    return gTts.ptts_set_voice(reinterpret_cast<PttsHandle*>(handle), n.c_str()) ? JNI_TRUE : JNI_FALSE;
}

// sink.onPcm(FloatArray): Boolean, 24 kHz mono floats; return false to stop early.
jboolean JNICALL nativeTtsSpeak(JNIEnv* env, jobject, jlong handle, jstring text, jobject sink) {
    if (!gTts.ok || handle == 0) return JNI_FALSE;
    enableFlushToZero();
    jclass cls = env->GetObjectClass(sink);
    jmethodID mid = env->GetMethodID(cls, "onPcm", "([F)Z");
    if (mid == nullptr) return JNI_FALSE;
    std::string t = toStd(env, text);
    SpeakCtx ctx{env, sink, mid};
    PttsResult res{};
    bool ok = gTts.ptts_speak(reinterpret_cast<PttsHandle*>(handle), t.c_str(), onFrame, &ctx, &res);
    if (ctx.cachedArr != nullptr) env->DeleteLocalRef(ctx.cachedArr);
    return (ok && !ctx.failed) ? JNI_TRUE : JNI_FALSE;
}

void JNICALL nativeTtsFree(JNIEnv*, jobject, jlong handle) {
    if (gTts.ok && handle != 0) gTts.ptts_free(reinterpret_cast<PttsHandle*>(handle));
}

// ------------------------------------------------------------------ TinyVoca (TTS, all ABIs)

jlong JNICALL nativeVocaLoad(JNIEnv* env, jobject, jstring path) {
    enableFlushToZero();
    std::string p = toStd(env, path);
    gVocaLastError.clear();
    neb_voca::VocaModel* m = neb_voca::voca_load(p.c_str(), &gVocaLastError);
    return reinterpret_cast<jlong>(m);
}

jstring JNICALL nativeVocaError(JNIEnv* env, jobject) {
    return toJava(env, gVocaLastError.empty() ? "Could not load TinyVoca model" : gVocaLastError.c_str());
}

jboolean JNICALL nativeVocaSpeak(
        JNIEnv* env, jobject, jlong handle, jstring text, jint voiceId, jfloat speed, jobject sink) {
    if (handle == 0 || sink == nullptr) return JNI_FALSE;
    enableFlushToZero();
    jclass cls = env->GetObjectClass(sink);
    jmethodID mid = env->GetMethodID(cls, "onPcm", "([F)Z");
    if (mid == nullptr) return JNI_FALSE;
    std::string t = toStd(env, text);
    SpeakCtx ctx{env, sink, mid};
    gVocaLastError.clear();
    bool ok = neb_voca::voca_speak(
        reinterpret_cast<neb_voca::VocaModel*>(handle),
        t.c_str(),
        (int) voiceId,
        (float) speed,
        onFrame,
        &ctx,
        &gVocaLastError
    );
    if (ctx.cachedArr != nullptr) env->DeleteLocalRef(ctx.cachedArr);
    return (ok && !ctx.failed) ? JNI_TRUE : JNI_FALSE;
}

void JNICALL nativeVocaFree(JNIEnv*, jobject, jlong handle) {
    if (handle != 0) neb_voca::voca_free(reinterpret_cast<neb_voca::VocaModel*>(handle));
}

// ------------------------------------------------------------------ Anti-Tamper & Request Proof (HMAC-SHA256)

inline uint32_t rotr32(uint32_t x, uint32_t n) { return (x >> n) | (x << (32u - n)); }

void sha256_raw(const uint8_t* data, size_t len, uint8_t out[32]) {
    static const uint32_t K[64] = {
        0x428a2f98u,0x71374491u,0xb5c0fbcfu,0xe9b5dba5u,0x3956c25bu,0x59f111f1u,0x923f82a4u,0xab1c5ed5u,
        0xd807aa98u,0x12835b01u,0x243185beu,0x550c7dc3u,0x72be5d74u,0x80deb1feu,0x9bdc06a7u,0xc19bf174u,
        0xe49b69c1u,0xefbe4786u,0x0fc19dc6u,0x240ca1ccu,0x2de92c6fu,0x4a7484aau,0x5cb0a9dcu,0x76f988dau,
        0x983e5152u,0xa831c66du,0xb00327c8u,0xbf597fc7u,0xc6e00bf3u,0xd5a79147u,0x06ca6351u,0x14292967u,
        0x27b70a85u,0x2e1b2138u,0x4d2c6dfcu,0x53380d13u,0x650a7354u,0x766a0abbu,0x81c2c92eu,0x92722c85u,
        0xa2bfe8a1u,0xa81a664bu,0xc24b8b70u,0xc76c51a3u,0xd192e819u,0xd6990624u,0xf40e3585u,0x106aa070u,
        0x19a4c116u,0x1e376c08u,0x2748774cu,0x34b0bcb5u,0x391c0cb3u,0x4ed8aa4au,0x5b9cca4fu,0x682e6ff3u,
        0x748f82eeu,0x78a5636fu,0x84c87814u,0x8cc70208u,0x90befffau,0xa4506cebu,0xbef9a3f7u,0xc67178f2u
    };
    uint32_t h[8] = {
        0x6a09e667u,0xbb67ae85u,0x3c6ef372u,0xa54ff53au,
        0x510e527fu,0x9b05688cu,0x1f83d9abu,0x5be0cd19u
    };
    size_t full = ((len + 9u + 63u) / 64u) * 64u;
    std::vector<uint8_t> msg(full, 0);
    if (len > 0) memcpy(msg.data(), data, len);
    msg[len] = 0x80u;
    uint64_t bits = static_cast<uint64_t>(len) * 8ULL;
    for (int i = 0; i < 8; ++i) msg[full - 1 - i] = static_cast<uint8_t>(bits >> (i * 8));
    for (size_t off = 0; off < full; off += 64) {
        uint32_t w[64];
        for (int i = 0; i < 16; ++i) {
            const uint8_t* p = msg.data() + off + i * 4;
            w[i] = (uint32_t(p[0]) << 24) | (uint32_t(p[1]) << 16) | (uint32_t(p[2]) << 8) | uint32_t(p[3]);
        }
        for (int i = 16; i < 64; ++i) {
            uint32_t s0 = rotr32(w[i - 15], 7) ^ rotr32(w[i - 15], 18) ^ (w[i - 15] >> 3);
            uint32_t s1 = rotr32(w[i - 2], 17) ^ rotr32(w[i - 2], 19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        uint32_t a=h[0], b=h[1], c=h[2], d=h[3], e=h[4], f=h[5], g=h[6], hh=h[7];
        for (int i = 0; i < 64; ++i) {
            uint32_t S1 = rotr32(e, 6) ^ rotr32(e, 11) ^ rotr32(e, 25);
            uint32_t ch = (e & f) ^ ((~e) & g);
            uint32_t t1 = hh + S1 + ch + K[i] + w[i];
            uint32_t S0 = rotr32(a, 2) ^ rotr32(a, 13) ^ rotr32(a, 22);
            uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
            uint32_t t2 = S0 + maj;
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
        }
        h[0]+=a; h[1]+=b; h[2]+=c; h[3]+=d; h[4]+=e; h[5]+=f; h[6]+=g; h[7]+=hh;
    }
    for (int i = 0; i < 8; ++i) {
        out[i*4+0] = static_cast<uint8_t>(h[i] >> 24);
        out[i*4+1] = static_cast<uint8_t>(h[i] >> 16);
        out[i*4+2] = static_cast<uint8_t>(h[i] >> 8);
        out[i*4+3] = static_cast<uint8_t>(h[i]);
    }
}

inline void secureZero(void* p, size_t n) {
    volatile uint8_t* vp = static_cast<volatile uint8_t*>(p);
    while (n--) *vp++ = 0;
}

struct XorNeedle {
    const uint8_t* enc;
    uint8_t len;
    uint8_t key;
    uint8_t flag;
};

static const uint8_t kNFrida[5]       = {0x51,0x4c,0x2c,0x28,0x32};
static const uint8_t kNGum[11]        = {0x5d,0x34,0x25,0x62,0x3c,0x2e,0x49,0x07,0x1d,0x16,0xf0};
static const uint8_t kNXposed[6]      = {0x45,0x34,0x24,0x21,0x3c,0x04};
static const uint8_t kNLspd[4]        = {0x2c,0x34,0x3e,0x31};
static const uint8_t kNEdxp[4]        = {0x26,0x2e,0x29,0x28};
static const uint8_t kNRiru[4]        = {0x34,0x24,0x26,0x2e};
static const uint8_t kNZygisk[6]      = {0x33,0x29,0x30,0x37,0x16,0x07};
static const uint8_t kNApkKiller[9]   = {0x2d,0x23,0x31,0x0a,0x01,0x03,0x1a,0x18,0xf6};
static const uint8_t kNMtHook[9]      = {0x23,0x3f,0x3f,0x09,0x1f,0x1a,0x16,0xef,0xec};
static const uint8_t kNSandHook[11]   = {0x3e,0x30,0x02,0x14,0x0f,0x1b,0x18,0xeb,0xe5,0xfe,0xf3};
static const uint8_t kNPine[7]        = {0x39,0x35,0x01,0x1a,0x18,0x16,0x1a};
static const uint8_t kNDobby[8]       = {0x34,0x36,0x04,0x09,0x1b,0x19,0xe0,0xf0};
static const uint8_t kNSubstrate[9]   = {0x28,0x17,0x0b,0x03,0x03,0x0c,0xe4,0xf8,0xf6};
static const uint8_t kNBinMt[7]       = {0x3c,0x0c,0x02,0x5d,0x17,0xf5,0xa6};
static const uint8_t kNNpMgr[9]       = {0x0f,0x18,0x02,0x17,0x13,0xe5,0xec,0xf7,0xeb};
static const uint8_t kNChelpus[7]     = {0x07,0x03,0x17,0x15,0xf0,0xf2,0xfd};
static const uint8_t kNLucky[10]      = {0x0b,0x1b,0x16,0x17,0xfa,0xfa,0xf0,0xec,0xfc,0xce};
static const uint8_t kNOriginApk[10]  = {0x05,0x03,0x11,0x18,0xef,0xe3,0xba,0xfa,0xd2,0xc2};

jint JNICALL nativeEnvFlags(JNIEnv* env, jobject, jstring apkPath) {
    int flags = 0;
    static const uint8_t kStatus[17] = {
        0x6e,0x38,0x3d,0x39,0x3e,0x4b,0x18,0x17,0x15,0xe6,0xa8,0xfd,0xe1,0xfd,0xd7,0xdf,0xc2
    };
    static const uint8_t kTracer[10] = {
        0x65,0x4a,0x5e,0x25,0x28,0x26,0x0b,0x0b,0x0d,0x4a
    };
    std::string tr = unxor(kTracer, 10, 0x31);
    FILE* fs = fopen(unxor(kStatus, 17, 0x41).c_str(), "r");
    if (fs) {
        char line[128];
        while (fgets(line, sizeof(line), fs)) {
            if (strncmp(line, tr.c_str(), 10) == 0) {
                if (atoi(line + 10) > 0) flags |= 0x01;
                break;
            }
        }
        fclose(fs);
    }
    secureZero(&tr[0], tr.size());

    static const XorNeedle kNeedles[] = {
        {kNFrida, 5, 0x37, 0x02}, {kNGum, 11, 0x3a, 0x02}, {kNXposed, 6, 0x3d, 0x02},
        {kNLspd, 4, 0x40, 0x02}, {kNEdxp, 4, 0x43, 0x02}, {kNRiru, 4, 0x46, 0x02},
        {kNZygisk, 6, 0x49, 0x02}, {kNApkKiller, 9, 0x4c, 0x02}, {kNMtHook, 9, 0x4f, 0x02},
        {kNSandHook, 11, 0x52, 0x02}, {kNPine, 7, 0x55, 0x02}, {kNDobby, 8, 0x58, 0x02},
        {kNSubstrate, 9, 0x5b, 0x02}, {kNBinMt, 7, 0x5e, 0x04}, {kNNpMgr, 9, 0x61, 0x04},
        {kNChelpus, 7, 0x64, 0x04}, {kNLucky, 10, 0x67, 0x04}, {kNOriginApk, 10, 0x6a, 0x04},
    };
    constexpr size_t kNeedleCount = sizeof(kNeedles) / sizeof(kNeedles[0]);
    std::string decNeedles[kNeedleCount];
    for (size_t i = 0; i < kNeedleCount; ++i) {
        decNeedles[i] = unxor(kNeedles[i].enc, kNeedles[i].len, kNeedles[i].key);
    }

    static const uint8_t kLdPreload[10] = {
        0x0e,0x0d,0x0f,0x07,0x0c,0x20,0x20,0x3c,0x3b,0xc5
    };
    std::string ldKey = unxor(kLdPreload, 10, 0x42);
    const char* ldVal = getenv(ldKey.c_str());
    secureZero(&ldKey[0], ldKey.size());
    if (ldVal != nullptr && ldVal[0] != '\0') {
        for (size_t i = 0; i < kNeedleCount; ++i) {
            if (strstr(ldVal, decNeedles[i].c_str()) != nullptr) {
                flags |= kNeedles[i].flag;
            }
        }
    }

    std::string p = toStd(env, apkPath);
    static const uint8_t kMaps[15] = {
        0x7c,0x2a,0x13,0x07,0x0c,0x59,0x0e,0xe1,0xe7,0xf4,0xb6,0xcd,0xc6,0xde,0xc6
    };
    FILE* fm = fopen(unxor(kMaps, 15, 0x53).c_str(), "r");
    if (fm) {
        char line[512];
        while (fgets(line, sizeof(line), fm)) {
            for (size_t i = 0; i < kNeedleCount; ++i) {
                if (strstr(line, decNeedles[i].c_str()) != nullptr) {
                    flags |= kNeedles[i].flag;
                }
            }
            if ((flags & 0x06) == 0x06) break;
        }
        fclose(fm);
    }

    if (!p.empty()) {
        for (size_t i = 0; i < kNeedleCount; ++i) {
            if (p.find(decNeedles[i]) != std::string::npos) {
                flags |= kNeedles[i].flag;
            }
        }
    }
    for (size_t i = 0; i < kNeedleCount; ++i) {
        if (!decNeedles[i].empty()) secureZero(&decNeedles[i][0], decNeedles[i].size());
    }
    return flags;
}

jstring JNICALL nativeSignProof(JNIEnv* env, jobject, jstring payload) {
    std::string msg = toStd(env, payload);
    static const uint8_t kEnc[12] = {
        0x4d,0x4f,0x53,0x15,0x52,0x75,0x35,0x79,0x28,0x0b,0x0e,0x41
    };
    std::string key = unxor(kEnc, 12, 0x23);
    uint8_t k_ipad[64], k_opad[64];
    memset(k_ipad, 0x36, 64);
    memset(k_opad, 0x5c, 64);
    for (size_t i = 0; i < key.size(); ++i) {
        uint8_t b = static_cast<uint8_t>(key[i]);
        k_ipad[i] ^= b;
        k_opad[i] ^= b;
    }
    if (!key.empty()) secureZero(&key[0], key.size());
    std::vector<uint8_t> inner(64 + msg.size());
    memcpy(inner.data(), k_ipad, 64);
    if (!msg.empty()) memcpy(inner.data() + 64, msg.data(), msg.size());
    uint8_t innerHash[32];
    sha256_raw(inner.data(), inner.size(), innerHash);
    uint8_t outer[96];
    memcpy(outer, k_opad, 64);
    memcpy(outer + 64, innerHash, 32);
    uint8_t mac[32];
    sha256_raw(outer, 96, mac);
    char hex[25];
    for (int i = 0; i < 12; ++i) snprintf(hex + i * 2, 3, "%02x", mac[i]);
    hex[24] = '\0';
    secureZero(k_ipad, sizeof(k_ipad));
    secureZero(k_opad, sizeof(k_opad));
    if (!inner.empty()) secureZero(inner.data(), inner.size());
    secureZero(innerHash, sizeof(innerHash));
    secureZero(outer, sizeof(outer));
    secureZero(mac, sizeof(mac));
    return env->NewStringUTF(hex);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }
    static const uint8_t kClsName[32] = {
        0x54,0x51,0x28,0x63,0x3d,0x3f,0x03,0x47,0x06,0x17,0x13,0xf7,0xa4,0xe1,0xe9,0xc5,
        0xc2,0xcd,0xdd,0x93,0x8d,0xab,0xa5,0xb1,0xa9,0x83,0xbe,0x84,0x9e,0x67,0x6a,0x78
    };
    std::string clsName = unxor(kClsName, 32, 0x37);
    jclass cls = env->FindClass(clsName.c_str());
    secureZero(&clsName[0], clsName.size());
    if (cls == nullptr) {
        env->ExceptionClear();
        return JNI_VERSION_1_6;
    }
    static const uint8_t kJniBlob[665] = {
        0x29,0x13,0x00,0xc2,0xe1,0xf6,0xd8,0xdc,0xae,0xaa,0xb8,0xe9,0xde,0x2a,0x4a,0x1d,
        0x59,0x43,0x30,0x1d,0x31,0x0a,0x1c,0x85,0xba,0xd3,0xc6,0xd8,0xb0,0xb2,0xcf,0x81,
        0x9b,0x69,0x73,0x0e,0x7d,0x4f,0x3a,0x3c,0x0c,0x08,0x47,0xa0,0xdf,0xa3,0xc3,0xc9,
        0xbe,0x85,0x81,0x82,0x9b,0x7f,0x18,0x0d,0x1b,0x69,0x4c,0x2a,0x12,0x07,0xc1,0xe9,
        0xec,0xce,0xc7,0xa4,0x87,0xbf,0x84,0x90,0x02,0x27,0x35,0x7f,0x36,0x30,0x24,0x29,
        0x2f,0x05,0xf6,0xfe,0xec,0xab,0x90,0xec,0x9e,0xb5,0x8d,0x8f,0x67,0x3c,0x4c,0x4c,
        0x54,0x20,0x7b,0x32,0x1a,0x09,0xe1,0xfb,0xc5,0x94,0xbc,0xba,0xa2,0x97,0xa3,0x89,
        0x78,0x72,0x45,0x5c,0x3e,0x63,0x03,0x23,0x3b,0x33,0xe6,0xf8,0xd0,0xd2,0xef,0xa1,
        0xbb,0x89,0x93,0x2e,0x5d,0x6f,0x5a,0x5c,0x2c,0x28,0x67,0x25,0x1c,0xe2,0xe6,0xfc,
        0x85,0xdb,0xa5,0xbf,0xb9,0xc4,0xab,0x71,0x60,0x76,0x42,0x5e,0x7d,0x7a,0x2c,0x07,
        0x1b,0xf1,0xf5,0x8e,0xc2,0xda,0xa6,0xb2,0xcd,0xbc,0x88,0x7b,0x7f,0x4d,0x57,0x06,
        0x4a,0x24,0x10,0x05,0x2d,0xff,0xf7,0xd5,0xb2,0x97,0xe5,0x95,0x8c,0x92,0x76,0x6c,
        0x35,0x4b,0x55,0x2f,0x29,0x74,0x3b,0x01,0xf0,0xe6,0xf2,0xce,0x8d,0xc3,0xa4,0xa9,
        0x99,0xb6,0x72,0x70,0x77,0x47,0x59,0x27,0x3e,0x3a,0x6c,0x51,0xaf,0xc9,0xa0,0xd9,
        0xce,0xb4,0x98,0x8e,0x8f,0x9f,0x08,0x3d,0x6e,0x45,0x5d,0x3f,0x37,0x4c,0x1c,0x1c,
        0xe4,0xf0,0x8b,0xe2,0xca,0xb9,0xb1,0x8b,0x95,0xc4,0x40,0x73,0x47,0x45,0x21,0x62,
        0x36,0x06,0x1a,0xe6,0xa1,0xc8,0xdc,0xc7,0xab,0xa1,0xbb,0xd2,0xbf,0x2a,0x5a,0x1d,
        0x5e,0x43,0x37,0x14,0x2c,0x19,0x17,0xf7,0x92,0xb7,0xe6,0x90,0x8a,0xb9,0x81,0x9b,
        0x9b,0x28,0x78,0x40,0x40,0x5c,0x67,0x06,0x16,0x1d,0x15,0xe7,0xf1,0x98,0xb0,0xc9,
        0xbe,0xa4,0xb2,0x9e,0x97,0x68,0x7d,0x56,0x32,0x17,0x06,0x70,0x3d,0x3f,0xea,0xec,
        0xec,0xc6,0x9b,0xad,0xaf,0xb5,0x8f,0xda,0x51,0x7b,0x6e,0x40,0x58,0x24,0x6b,0x5d,
        0x1e,0x03,0xf7,0xc2,0xfb,0xdf,0xee,0xaa,0xbb,0xbc,0x89,0xf9,0x2e,0x59,0x6c,0x47,
        0x5b,0x31,0x35,0x4e,0x02,0x1a,0xe6,0xf2,0x8d,0xfc,0xc8,0xbb,0xbf,0x8d,0x97,0xc6,
        0x23,0x4d,0x24,0x45,0x4a,0x38,0x0b,0x15,0x17,0x1e,0xe7,0x99,0x8e,0xf9,0x8c,0xa7,
        0xbb,0x91,0x95,0x2e,0x62,0x7a,0x46,0x52,0x6d,0x1c,0x28,0x1b,0x1f,0xed,0xf7,0xa6,
        0xe6,0xd4,0xab,0xbc,0xf1,0x85,0x9d,0x67,0x3d,0x76,0x4d,0x57,0x35,0x7c,0x13,0x1d,
        0x1f,0xe2,0xf7,0xc9,0x81,0xeb,0xab,0xb8,0xb1,0x86,0x92,0x62,0x2d,0x0a,0x6a,0x3d,
        0x3e,0x23,0x17,0x37,0x0c,0xee,0xfd,0xa5,0x9a,0xf5,0xe5,0x8f,0xe6,0x85,0x6f,0x6e,
        0x7b,0x6b,0x5b,0x20,0x2a,0x5b,0x40,0x39,0xe8,0xee,0xea,0xc8,0x99,0xaf,0xb1,0xb3,
        0x8d,0xd8,0x57,0x65,0x6c,0x42,0x56,0x22,0x69,0x76,0x26,0x79,0xf0,0xfc,0xc3,0xcc,
        0xff,0xb5,0xa6,0x8e,0x9c,0xfb,0x20,0x3c,0x6e,0x45,0x5d,0x3f,0x37,0x4c,0x1c,0x1c,
        0xe4,0xf0,0x8b,0xe2,0xca,0xb9,0xb1,0x8b,0x95,0xc4,0x0c,0x6f,0x49,0x50,0x21,0x1e,
        0x2a,0x02,0x15,0xea,0x8e,0xb3,0xe2,0xf9,0xa8,0xae,0xaa,0x88,0xd9,0x6f,0x71,0x73,
        0x4d,0x18,0x17,0x25,0x2c,0x02,0x16,0xe2,0xa9,0xd6,0xea,0xf5,0xa5,0xbc,0x8d,0xc2,
        0x94,0x62,0x76,0x0e,0x47,0x5a,0x26,0x26,0x4d,0x1c,0x0c,0xec,0xf3,0xc0,0xd8,0x92,
        0x9a,0xb4,0x89,0xa2,0x97,0x65,0x73,0x1e,0x1b,0x65,0x4c,0x2f,0x09,0x10,0xe1,0xcb,
        0xe8,0xc2,0xd1,0xc1,0xe6,0x91,0xc1,0xa3,0x02,0x6a,0x72,0x5f,0x70,0x2f,0x31,0x3a,
        0x19,0x77,0xac,0xdd,0xf4,0xca,0xce,0xa4,0xfd,0xb3,0x8d,0x97,0x61,0x3c,0x73,0x59,
        0x48,0x2e,0x3a,0x06,0x55,0x52,0xc1,0x95,0xd1,0xc6,0xdb,0xa7,0x86,0x91,0x9f,0x92,
        0x6c,0x17,0x0c,0x7d,0x54,0x2a,0x2e,0x04,0x5d,0x13,0xed,0xf7,0xc1,0x9c,0x93,0xb9,
        0xa8,0x8e,0x9a,0x66,0x35,0x32,0x64,0x5f,0x23,0x39,0x3d,0x46,0x1a,0xe2,0xfe,0xfa,
        0x85,0xe4,0xb0,0xa3,0xb7,0x85,0x9f,0x3e,0x12,
    };
    void* const fnPtrs[20] = {
        reinterpret_cast<void*>(nativeSttCompiled),
        reinterpret_cast<void*>(nativeSttLoad),
        reinterpret_cast<void*>(nativeSttReset),
        reinterpret_cast<void*>(nativeSttAdviseIdle),
        reinterpret_cast<void*>(nativeSttError),
        reinterpret_cast<void*>(nativeSttStream),
        reinterpret_cast<void*>(nativeSttStop),
        reinterpret_cast<void*>(nativeTtsAvailable),
        reinterpret_cast<void*>(nativeTtsLoad),
        reinterpret_cast<void*>(nativeTtsError),
        reinterpret_cast<void*>(nativeTtsVoices),
        reinterpret_cast<void*>(nativeTtsSetVoice),
        reinterpret_cast<void*>(nativeTtsSpeak),
        reinterpret_cast<void*>(nativeTtsFree),
        reinterpret_cast<void*>(nativeVocaLoad),
        reinterpret_cast<void*>(nativeVocaError),
        reinterpret_cast<void*>(nativeVocaSpeak),
        reinterpret_cast<void*>(nativeVocaFree),
        reinterpret_cast<void*>(nativeEnvFlags),
        reinterpret_cast<void*>(nativeSignProof),
    };
    char decoded[sizeof(kJniBlob)];
    for (size_t i = 0; i < sizeof(kJniBlob); ++i) {
        decoded[i] = static_cast<char>(kJniBlob[i] ^ static_cast<uint8_t>(0x5Au + i * 13u));
    }
    JNINativeMethod methods[20];
    const char* cur = decoded;
    for (int i = 0; i < 20; ++i) {
        methods[i].name = cur;
        cur += strlen(cur) + 1;
        methods[i].signature = cur;
        cur += strlen(cur) + 1;
        methods[i].fnPtr = fnPtrs[i];
    }
    if (env->RegisterNatives(cls, methods, 20) != 0) {
        env->ExceptionClear();
    }
    secureZero(decoded, sizeof(decoded));
    secureZero(methods, sizeof(methods));
    env->DeleteLocalRef(cls);
    return JNI_VERSION_1_6;
}
