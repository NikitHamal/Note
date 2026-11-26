#include "tiny_voca.h"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>

extern "C" {
struct z_stream_s {
    const unsigned char* next_in;
    unsigned int avail_in;
    unsigned long total_in;
    unsigned char* next_out;
    unsigned int avail_out;
    unsigned long total_out;
    const char* msg;
    void* state;
    void* zalloc;
    void* zfree;
    void* opaque;
    int data_type;
    unsigned long adler;
    unsigned long reserved;
};
int inflateInit2_(z_stream_s* strm, int windowBits, const char* version, int stream_size);
int inflate(z_stream_s* strm, int flush);
int inflateEnd(z_stream_s* strm);
}
#include <cctype>
#include <cmath>
#include <cstring>
#include <thread>
#include <unordered_map>
#include <utility>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#endif

namespace neb_voca {
namespace {

constexpr int kSampleRate = 24000;
constexpr int kHop = 256;
constexpr int kChannels = 192;
constexpr int kHeads = 4;
constexpr int kEncoderLayers = 4;
constexpr int kProsodyLayers = 2;
constexpr int kAcousticLayers = 5;
constexpr int kSynthFft = 512;
constexpr int kSynthHop = 128;
constexpr int kNoiseFft = 256;
constexpr int kNoiseHop = 64;
constexpr int kCepCausal = 80;
constexpr int kCepAnti = 16;
constexpr int kNoiseCep = 32;
constexpr float kHarmonicLimit = 0.47f;
constexpr float kPitchRefHz = 200.0f;
constexpr float kF0Min = 50.0f;
constexpr float kF0Max = 600.0f;
constexpr int kMaxTokens = 512;
constexpr int kMaxFrames = 3000;
constexpr int kPhoneOffset = 17;
constexpr int kPhoneCount = 59;

inline float halfToFloat(uint16_t bits) {
    uint32_t sign = static_cast<uint32_t>(bits & 0x8000u) << 16;
    uint32_t exp = (bits >> 10) & 31u;
    uint32_t frac = bits & 1023u;
    uint32_t out_bits = 0;
    if (exp == 0) {
        if (frac != 0) {
            exp = 113;
            while ((frac & 1024u) == 0) { frac <<= 1; --exp; }
            frac &= 1023u;
            out_bits = sign | (exp << 23) | (frac << 13);
        } else {
            out_bits = sign;
        }
    } else if (exp == 31) {
        out_bits = sign | 0x7F800000u | (frac << 13);
    } else {
        out_bits = sign | ((exp + 112u) << 23) | (frac << 13);
    }
    float res;
    std::memcpy(&res, &out_bits, sizeof(float));
    return res;
}

struct Tensor {
    std::vector<int> shape;
    std::vector<float> data;
};

struct Layer {
    const float* weight = nullptr;
    const float* bias = nullptr;
    int out_dim = 0;
    int in_dim = 0;
    int taps = 1;
};

struct TransformerBlock {
    Layer norm1, qkv, out, norm2, depthwise, expand, project;
};

struct TransformerStack {
    Layer norm;
    std::vector<TransformerBlock> blocks;
};

struct PhoneUnit {
    uint8_t id;
    uint8_t stress;
};

bool gunzipBuffer(const uint8_t* src, size_t src_len, std::vector<uint8_t>* out) {
    z_stream_s strm{};
    if (inflateInit2_(&strm, 16 + 15, "1.2.11", static_cast<int>(sizeof(z_stream_s))) != 0) return false;
    strm.next_in = src;
    strm.avail_in = static_cast<unsigned int>(src_len);
    out->clear();
    uint8_t chunk[32768];
    int ret = 0;
    while (ret == 0) {
        strm.next_out = chunk;
        strm.avail_out = sizeof(chunk);
        ret = inflate(&strm, 0);
        if (ret != 0 && ret != 1) {
            inflateEnd(&strm);
            return false;
        }
        size_t have = sizeof(chunk) - strm.avail_out;
        out->insert(out->end(), chunk, chunk + have);
    }
    inflateEnd(&strm);
    return true;
}

void unpackTensor(const uint8_t* ptr, uint32_t type, size_t count, std::vector<float>* out) {
    out->resize(count);
    float* dst = out->data();
    if (type == 0) {
        std::memcpy(dst, ptr, count * sizeof(float));
    } else if (type == 1) {
        for (size_t i = 0; i < count; ++i) {
            uint16_t h;
            std::memcpy(&h, ptr + 2 * i, 2);
            dst[i] = halfToFloat(h);
        }
    } else if (type == 8) {
        size_t blocks = count >> 5;
        for (size_t b = 0; b < blocks; ++b) {
            const uint8_t* bp = ptr + b * 34;
            uint16_t h;
            std::memcpy(&h, bp, 2);
            float scale = halfToFloat(h);
            const int8_t* qs = reinterpret_cast<const int8_t*>(bp + 2);
            float* bd = dst + (b << 5);
            for (int j = 0; j < 32; ++j) bd[j] = scale * static_cast<float>(qs[j]);
        }
        for (size_t i = blocks << 5; i < count; ++i) {
            size_t blk = (i >> 5) * 34;
            uint16_t h;
            std::memcpy(&h, ptr + blk, 2);
            dst[i] = halfToFloat(h) * static_cast<float>(static_cast<int8_t>(ptr[blk + 2 + (i & 31)]));
        }
    } else if (type == 2) {
        size_t blocks = count >> 5;
        for (size_t b = 0; b < blocks; ++b) {
            const uint8_t* bp = ptr + b * 18;
            uint16_t h;
            std::memcpy(&h, bp, 2);
            float scale = halfToFloat(h);
            float* bd = dst + (b << 5);
            for (int j = 0; j < 16; ++j) {
                uint8_t byte = bp[2 + j];
                bd[j] = scale * static_cast<float>((byte & 15) - 8);
                bd[j + 16] = scale * static_cast<float>((byte >> 4) - 8);
            }
        }
    }
}

static const char* const kPhoneSymbols[kPhoneCount] = {
    "p", "b", "t", "d", "k", "\xC9\xA1", "f", "v", "\xCE\xB8", "\xC3\xB0", "s", "z", "\xCA\x83", "\xCA\x92", "h",
    "m", "n", "\xC5\x8B", "l", "\xC9\xB9", "j", "w", "t\xCA\x83", "d\xCA\x92", "\xC9\xBE", "\xCA\x94",
    "\xC9\x99", "\xC9\x90", "\xC9\x9A", "\xE1\xB5\xBB", "\xC9\xAA", "i", "i\xCB\x90", "\xC9\x9B", "\xC3\xA6",
    "\xCA\x8C", "\xC9\x91\xCB\x90", "\xC9\x94", "\xC9\x94\xCB\x90", "\xCA\x8A", "u\xCB\x90", "\xC9\x9C\xCB\x90",
    "e\xC9\xAA", "a\xC9\xAA", "\xC9\x94\xC9\xAA", "o\xCA\x8A", "a\xCA\x8A", "i\xC9\x99", "o\xCB\x90",
    "\xC9\x91\xCB\x90\xC9\xB9", "\xC9\x94\xCB\x90\xC9\xB9", "o\xCB\x90\xC9\xB9", "\xC9\xAA\xC9\xB9",
    "\xC9\x9B\xC9\xB9", "\xCA\x8A\xC9\xB9", "a\xC9\xAA\xC9\x9A", "a\xC9\xAA\xC9\x99", "\xC9\x99l", "n\xCC\xA9"
};

inline bool isVowelId(uint8_t id) { return id >= 43 && id <= 75; }
inline bool isPauseId(uint8_t id) { return id <= 16; }

struct GgufReader {
    const uint8_t* base;
    size_t size;
    size_t at = 0;

    uint32_t u32() {
        if (at + 4 > size) return 0;
        uint32_t v;
        std::memcpy(&v, base + at, 4);
        at += 4;
        return v;
    }
    uint64_t u64() {
        if (at + 8 > size) return 0;
        uint64_t v;
        std::memcpy(&v, base + at, 8);
        at += 8;
        return v;
    }
    std::string str() {
        uint64_t len = u64();
        if (at + len > size) { at = size; return {}; }
        std::string s(reinterpret_cast<const char*>(base + at), static_cast<size_t>(len));
        at += static_cast<size_t>(len);
        return s;
    }
    void skipValue(uint32_t type) {
        static const uint8_t kSizes[13] = {1, 1, 2, 2, 4, 4, 4, 1, 0, 0, 8, 8, 8};
        if (type == 8) { (void)str(); return; }
        if (type == 9) {
            uint32_t inner = u32();
            uint64_t count = u64();
            if (inner < 2) { at = std::min(size, at + static_cast<size_t>(count)); return; }
            for (uint64_t i = 0; i < count && at < size; ++i) skipValue(inner);
            return;
        }
        if (type < 13) at = std::min(size, at + kSizes[type]);
    }
};

std::vector<PhoneUnit> parseIpaPhones(
    const std::string& text,
    const std::unordered_map<std::string, uint8_t>& sym_to_id
) {
    std::vector<PhoneUnit> out;
    size_t i = 0, n = text.size();
    while (i < n) {
        while (i < n && std::isspace(static_cast<unsigned char>(text[i]))) ++i;
        if (i >= n) break;
        size_t j = i;
        while (j < n && !std::isspace(static_cast<unsigned char>(text[j]))) ++j;
        std::string tok = text.substr(i, j - i);
        i = j;
        uint8_t stress = 0;
        if (tok.size() > 2 && static_cast<uint8_t>(tok[0]) == 0xCB && static_cast<uint8_t>(tok[1]) == 0x88) {
            stress = 1; tok.erase(0, 2);
        } else if (tok.size() > 2 && static_cast<uint8_t>(tok[0]) == 0xCB && static_cast<uint8_t>(tok[1]) == 0x8C) {
            stress = 2; tok.erase(0, 2);
        }
        auto it = sym_to_id.find(tok);
        if (it != sym_to_id.end()) out.push_back({it->second, stress});
    }
    return out;
}

void unpackLexiconBytes(
    const std::vector<uint8_t>& bytes,
    std::unordered_map<std::string, std::vector<PhoneUnit>>* lexicon
) {
    if (bytes.size() < 4) return;
    uint32_t count = 0;
    std::memcpy(&count, bytes.data(), 4);
    lexicon->reserve(count + 1024);
    std::vector<std::string> words(count);
    uint8_t buf[4096]{};
    size_t at = 4, sz = bytes.size();
    for (uint32_t i = 0; i < count && at < sz; ++i) {
        uint8_t same = bytes[at++];
        size_t end = at;
        while (end < sz && bytes[end] != 0) ++end;
        size_t chunk = std::min<size_t>(end - at, sizeof(buf) - same);
        if (chunk > 0) std::memcpy(buf + same, bytes.data() + at, chunk);
        words[i].assign(reinterpret_cast<const char*>(buf), same + chunk);
        at = end + 1;
    }
    for (uint32_t i = 0; i < count && at < sz; ++i) {
        uint8_t same = bytes[at++];
        size_t end = at;
        while (end < sz && bytes[end] != 0) ++end;
        size_t chunk = std::min<size_t>(end - at, sizeof(buf) - same);
        if (chunk > 0) std::memcpy(buf + same, bytes.data() + at, chunk);
        size_t len = same + chunk;
        std::vector<PhoneUnit> units;
        units.reserve(len);
        for (size_t k = 0; k < len; ++k) {
            uint8_t b = buf[k];
            if (b == 0) continue;
            uint8_t stress = static_cast<uint8_t>((b - 1) / kPhoneCount);
            uint8_t p_idx = static_cast<uint8_t>((b - 1) % kPhoneCount);
            units.push_back({static_cast<uint8_t>(kPhoneOffset + p_idx), stress});
        }
        (*lexicon)[ std::move(words[i]) ] = std::move(units);
        at = end + 1;
    }
}

std::string numberToWords(long long n) {
    if (n == 0) return "zero";
    if (n < 0) return "minus " + numberToWords(-n);
    static const char* const kOnes[] = {
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    };
    static const char* const kTens[] = {
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };
    std::string out;
    auto append = [&](const std::string& s) {
        if (!s.empty()) { if (!out.empty()) out += ' '; out += s; }
    };
    if (n >= 1000000) { append(numberToWords(n / 1000000) + " million"); n %= 1000000; }
    if (n >= 1000) { append(numberToWords(n / 1000) + " thousand"); n %= 1000; }
    if (n >= 100) { append(std::string(kOnes[n / 100]) + " hundred"); n %= 100; }
    if (n >= 20) {
        std::string t = kTens[n / 10];
        if (n % 10) t += std::string(" ") + kOnes[n % 10];
        append(t);
    } else if (n > 0) {
        append(kOnes[n]);
    }
    return out;
}

std::vector<PhoneUnit> pluralizePhones(std::vector<PhoneUnit> stem) {
    if (stem.empty()) return stem;
    uint8_t last = stem.back().id;
    // s=27, z=28, ʃ=29, ʒ=30, tʃ=39, dʒ=40 -> + ᵻ(46) z(28)
    if (last == 27 || last == 28 || last == 29 || last == 30 || last == 39 || last == 40) {
        stem.push_back({46, 0});
        stem.push_back({28, 0});
    } else if (last == 17 || last == 19 || last == 21 || last == 23 || last == 25 || last == 42) {
        stem.push_back({27, 0});
    } else {
        stem.push_back({28, 0});
    }
    return stem;
}

std::vector<PhoneUnit> pastPhones(std::vector<PhoneUnit> stem) {
    if (stem.empty()) return stem;
    uint8_t last = stem.back().id;
    // t=19, d=20, ɾ=41 -> + ᵻ(46) d(20)
    if (last == 19 || last == 20 || last == 41) {
        stem.push_back({46, 0});
        stem.push_back({20, 0});
    } else if (last == 17 || last == 21 || last == 23 || last == 25 || last == 27 || last == 29 || last == 39 || last == 42) {
        stem.push_back({19, 0});
    } else {
        stem.push_back({20, 0});
    }
    return stem;
}

std::vector<PhoneUnit> fallbackGraphemePhones(const std::string& w) {
    // Clean English grapheme-to-phoneme fallback when a rare word is not in the 125k CMU lexicon.
    std::vector<PhoneUnit> out;
    bool stressed = false;
    for (size_t i = 0; i < w.size(); ++i) {
        char c = w[i];
        char nx = (i + 1 < w.size()) ? w[i + 1] : 0;
        if (c == 't' && nx == 'h') { out.push_back({25, 0}); ++i; continue; }
        if (c == 's' && nx == 'h') { out.push_back({29, 0}); ++i; continue; }
        if (c == 'c' && nx == 'h') { out.push_back({39, 0}); ++i; continue; }
        if (c == 'p' && nx == 'h') { out.push_back({23, 0}); ++i; continue; }
        if (c == 'n' && nx == 'g') { out.push_back({34, 0}); ++i; continue; }
        uint8_t id = 0;
        bool vowel = false;
        switch (c) {
            case 'a': id = 51; vowel = true; break;
            case 'e': id = (i + 1 == w.size() && !out.empty()) ? 0 : 50; vowel = (id != 0); break;
            case 'i': case 'y': id = 47; vowel = true; break;
            case 'o': id = 53; vowel = true; break;
            case 'u': id = 52; vowel = true; break;
            case 'b': id = 18; break; case 'c': id = (nx == 'e' || nx == 'i' || nx == 'y') ? 27 : 21; break;
            case 'd': id = 20; break; case 'f': id = 23; break; case 'g': id = 22; break;
            case 'h': id = 31; break; case 'j': id = 40; break; case 'k': id = 21; break;
            case 'l': id = 35; break; case 'm': id = 32; break; case 'n': id = 33; break;
            case 'p': id = 17; break; case 'q': id = 21; break; case 'r': id = 36; break;
            case 's': id = 27; break; case 't': id = 19; break; case 'v': id = 24; break;
            case 'w': id = 38; break; case 'z': id = 28; break;
            case 'x': out.push_back({21, 0}); id = 27; break;
            default: break;
        }
        if (id != 0) {
            uint8_t st = (vowel && !stressed) ? 1 : 0;
            if (st) stressed = true;
            out.push_back({id, st});
        }
    }
    return out;
}

inline float dotProduct(const float* a, const float* b, int n) {
    int i = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    float32x4_t s0 = vdupq_n_f32(0.0f);
    float32x4_t s1 = vdupq_n_f32(0.0f);
    float32x4_t s2 = vdupq_n_f32(0.0f);
    float32x4_t s3 = vdupq_n_f32(0.0f);
    for (; i + 16 <= n; i += 16) {
        s0 = vmlaq_f32(s0, vld1q_f32(a + i), vld1q_f32(b + i));
        s1 = vmlaq_f32(s1, vld1q_f32(a + i + 4), vld1q_f32(b + i + 4));
        s2 = vmlaq_f32(s2, vld1q_f32(a + i + 8), vld1q_f32(b + i + 8));
        s3 = vmlaq_f32(s3, vld1q_f32(a + i + 12), vld1q_f32(b + i + 12));
    }
    s0 = vaddq_f32(vaddq_f32(s0, s1), vaddq_f32(s2, s3));
    for (; i + 4 <= n; i += 4) {
        s0 = vmlaq_f32(s0, vld1q_f32(a + i), vld1q_f32(b + i));
    }
#if defined(__aarch64__)
    float sum = vaddvq_f32(s0);
#else
    float32x2_t p = vadd_f32(vget_low_f32(s0), vget_high_f32(s0));
    float sum = vget_lane_f32(vpadd_f32(p, p), 0);
#endif
#else
    float sum = 0.0f;
#endif
    for (; i < n; ++i) sum += a[i] * b[i];
    return sum;
}

// Custom 4x2 Register-Blocked ARM NEON GEMM kernel: reuses each loaded weight row across 4 time-steps
std::vector<float> linearOp(const float* x, int count, const Layer& l) {
    const int out_d = l.out_dim, in_d = l.in_dim;
    std::vector<float> y(static_cast<size_t>(count) * out_d);
    int t = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    for (; t + 4 <= count; t += 4) {
        const float* x0 = x + static_cast<size_t>(t) * in_d;
        const float* x1 = x0 + in_d;
        const float* x2 = x1 + in_d;
        const float* x3 = x2 + in_d;
        float* y0 = y.data() + static_cast<size_t>(t) * out_d;
        float* y1 = y0 + out_d;
        float* y2 = y1 + out_d;
        float* y3 = y2 + out_d;
        int o = 0;
        for (; o + 2 <= out_d; o += 2) {
            const float* w0 = l.weight + static_cast<size_t>(o) * in_d;
            const float* w1 = w0 + in_d;
            float32x4_t a00 = vdupq_n_f32(0.0f), a10 = vdupq_n_f32(0.0f), a20 = vdupq_n_f32(0.0f), a30 = vdupq_n_f32(0.0f);
            float32x4_t a01 = vdupq_n_f32(0.0f), a11 = vdupq_n_f32(0.0f), a21 = vdupq_n_f32(0.0f), a31 = vdupq_n_f32(0.0f);
            int i = 0;
            for (; i + 4 <= in_d; i += 4) {
                float32x4_t vw0 = vld1q_f32(w0 + i);
                float32x4_t vw1 = vld1q_f32(w1 + i);
                float32x4_t vx0 = vld1q_f32(x0 + i);
                float32x4_t vx1 = vld1q_f32(x1 + i);
                float32x4_t vx2 = vld1q_f32(x2 + i);
                float32x4_t vx3 = vld1q_f32(x3 + i);
                a00 = vmlaq_f32(a00, vw0, vx0); a10 = vmlaq_f32(a10, vw0, vx1);
                a20 = vmlaq_f32(a20, vw0, vx2); a30 = vmlaq_f32(a30, vw0, vx3);
                a01 = vmlaq_f32(a01, vw1, vx0); a11 = vmlaq_f32(a11, vw1, vx1);
                a21 = vmlaq_f32(a21, vw1, vx2); a31 = vmlaq_f32(a31, vw1, vx3);
            }
            auto hsum = [](float32x4_t v) -> float {
#if defined(__aarch64__)
                return vaddvq_f32(v);
#else
                float32x2_t p = vadd_f32(vget_low_f32(v), vget_high_f32(v));
                return vget_lane_f32(vpadd_f32(p, p), 0);
#endif
            };
            float b0 = l.bias ? l.bias[o] : 0.0f;
            float b1 = l.bias ? l.bias[o + 1] : 0.0f;
            float s00 = b0 + hsum(a00), s10 = b0 + hsum(a10), s20 = b0 + hsum(a20), s30 = b0 + hsum(a30);
            float s01 = b1 + hsum(a01), s11 = b1 + hsum(a11), s21 = b1 + hsum(a21), s31 = b1 + hsum(a31);
            for (; i < in_d; ++i) {
                float fw0 = w0[i], fw1 = w1[i];
                s00 += fw0 * x0[i]; s10 += fw0 * x1[i]; s20 += fw0 * x2[i]; s30 += fw0 * x3[i];
                s01 += fw1 * x0[i]; s11 += fw1 * x1[i]; s21 += fw1 * x2[i]; s31 += fw1 * x3[i];
            }
            y0[o] = s00; y1[o] = s10; y2[o] = s20; y3[o] = s30;
            y0[o + 1] = s01; y1[o + 1] = s11; y2[o + 1] = s21; y3[o + 1] = s31;
        }
        for (; o < out_d; ++o) {
            const float* wr = l.weight + static_cast<size_t>(o) * in_d;
            float b = l.bias ? l.bias[o] : 0.0f;
            y0[o] = b + dotProduct(wr, x0, in_d);
            y1[o] = b + dotProduct(wr, x1, in_d);
            y2[o] = b + dotProduct(wr, x2, in_d);
            y3[o] = b + dotProduct(wr, x3, in_d);
        }
    }
#endif
    for (; t < count; ++t) {
        const float* xr = x + static_cast<size_t>(t) * in_d;
        float* yr = y.data() + static_cast<size_t>(t) * out_d;
        for (int o = 0; o < out_d; ++o) {
            const float* wr = l.weight + static_cast<size_t>(o) * in_d;
            yr[o] = (l.bias ? l.bias[o] : 0.0f) + dotProduct(wr, xr, in_d);
        }
    }
    return y;
}

std::vector<float> normalizeOp(const std::vector<float>& x, int count, const Layer& l) {
    const int w = l.out_dim;
    std::vector<float> y(x.size());
    const float inv_w = 1.0f / static_cast<float>(w);
    for (int t = 0; t < count; ++t) {
        const float* xr = x.data() + static_cast<size_t>(t) * w;
        float* yr = y.data() + static_cast<size_t>(t) * w;
        float sum = 0.0f, sq_sum = 0.0f;
        int i = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
        float32x4_t vSum = vdupq_n_f32(0.0f), vSq = vdupq_n_f32(0.0f);
        for (; i + 4 <= w; i += 4) {
            float32x4_t v = vld1q_f32(xr + i);
            vSum = vaddq_f32(vSum, v);
            vSq = vmlaq_f32(vSq, v, v);
        }
#if defined(__aarch64__)
        sum = vaddvq_f32(vSum);
        sq_sum = vaddvq_f32(vSq);
#else
        float32x2_t p1 = vadd_f32(vget_low_f32(vSum), vget_high_f32(vSum));
        float32x2_t p2 = vadd_f32(vget_low_f32(vSq), vget_high_f32(vSq));
        sum = vget_lane_f32(vpadd_f32(p1, p1), 0);
        sq_sum = vget_lane_f32(vpadd_f32(p2, p2), 0);
#endif
#endif
        for (; i < w; ++i) { sum += xr[i]; sq_sum += xr[i] * xr[i]; }
        float mean = sum * inv_w;
        float var = std::max(0.0f, sq_sum * inv_w - mean * mean);
        float scale = 1.0f / std::sqrt(var + 1e-5f);
        int j = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
        float32x4_t vMean = vdupq_n_f32(mean), vScale = vdupq_n_f32(scale);
        for (; j + 4 <= w; j += 4) {
            float32x4_t vx = vsubq_f32(vld1q_f32(xr + j), vMean);
            float32x4_t vw = vmulq_f32(vld1q_f32(l.weight + j), vScale);
            float32x4_t vb = vld1q_f32(l.bias + j);
            vst1q_f32(yr + j, vmlaq_f32(vb, vx, vw));
        }
#endif
        for (; j < w; ++j) yr[j] = (xr[j] - mean) * scale * l.weight[j] + l.bias[j];
    }
    return y;
}

// High-accuracy vectorized ARM NEON GELU using Abramowitz & Stegun 7.1.26 erf approximation (< 1.5e-7 max error)
void geluInPlace(std::vector<float>* x) {
    float* ptr = x->data();
    const size_t n = x->size();
    size_t i = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
    const float32x4_t vInvSqrt2 = vdupq_n_f32(0.7071067811865475f);
    const float32x4_t vHalf = vdupq_n_f32(0.5f);
    const float32x4_t vOne = vdupq_n_f32(1.0f);
    const float32x4_t vZero = vdupq_n_f32(0.0f);
    const float32x4_t vP = vdupq_n_f32(0.3275911f);
    const float32x4_t vA1 = vdupq_n_f32(0.254829592f);
    const float32x4_t vA2 = vdupq_n_f32(-0.284496736f);
    const float32x4_t vA3 = vdupq_n_f32(1.421413741f);
    const float32x4_t vA4 = vdupq_n_f32(-1.453152027f);
    const float32x4_t vA5 = vdupq_n_f32(1.061405429f);
    const float32x4_t vClip = vdupq_n_f32(3.9f);
    for (; i + 4 <= n; i += 4) {
        float32x4_t v = vld1q_f32(ptr + i);
        float32x4_t z = vmulq_f32(v, vInvSqrt2);
        float32x4_t az = vminq_f32(vabsq_f32(z), vClip);
        float32x4_t denom = vmlaq_f32(vOne, vP, az);
#if defined(__aarch64__)
        float32x4_t t = vdivq_f32(vOne, denom);
#else
        float32x4_t rec = vrecpeq_f32(denom);
        rec = vmulq_f32(vrecpsq_f32(denom, rec), rec);
        float32x4_t t = vmulq_f32(vrecpsq_f32(denom, rec), rec);
#endif
        float32x4_t poly = vmlaq_f32(vA4, vA5, t);
        poly = vmlaq_f32(vA3, poly, t);
        poly = vmlaq_f32(vA2, poly, t);
        poly = vmlaq_f32(vA1, poly, t);
        poly = vmulq_f32(poly, t);
        // Fast 4-term exp(-az^2) via (1 - az^2 / 16)^16 or exact scalar lane expf:
        float z2[4];
        vst1q_f32(z2, vnegq_f32(vmulq_f32(az, az)));
        float e4[4] = { std::expf(z2[0]), std::expf(z2[1]), std::expf(z2[2]), std::expf(z2[3]) };
        float32x4_t erfAbs = vsubq_f32(vOne, vmulq_f32(poly, vld1q_f32(e4)));
        uint32x4_t negMask = vcltq_f32(z, vZero);
        float32x4_t erfSigned = vbslq_f32(negMask, vnegq_f32(erfAbs), erfAbs);
        vst1q_f32(ptr + i, vmulq_f32(vmulq_f32(v, vHalf), vaddq_f32(vOne, erfSigned)));
    }
#endif
    constexpr float kInvSqrt2 = 0.7071067811865475f;
    for (; i < n; ++i) {
        float v = ptr[i];
        ptr[i] = 0.5f * v * (1.0f + std::erff(v * kInvSqrt2));
    }
}

std::vector<float> convolveOp(const std::vector<float>& x, int count, const Layer& l) {
    const int out_d = l.out_dim, in_d = l.in_dim, taps = l.taps, reach = taps >> 1;
    std::vector<float> y(static_cast<size_t>(count) * out_d);
    for (int t = 0; t < count; ++t) {
        int first = std::max(0, reach - t), end = std::min(taps, count + reach - t);
        float* yr = y.data() + static_cast<size_t>(t) * out_d;
        for (int o = 0; o < out_d; ++o) {
            float sum = l.bias[o];
            const float* w_base = l.weight + static_cast<size_t>(o) * taps * in_d;
            for (int k = first; k < end; ++k) {
                const float* xr = x.data() + static_cast<size_t>(t + k - reach) * in_d;
                sum += dotProduct(w_base + static_cast<size_t>(k) * in_d, xr, in_d);
            }
            yr[o] = sum;
        }
    }
    return y;
}

// Contiguous channel-vectorized ARM NEON depthwise convolution (weights pre-transposed to [taps, ch])
std::vector<float> depthwiseOp(const std::vector<float>& x, int count, const Layer& l) {
    const int ch = l.out_dim, taps = l.taps, reach = taps >> 1;
    std::vector<float> y(static_cast<size_t>(count) * ch);
    for (int t = 0; t < count; ++t) {
        int first = std::max(0, reach - t), end = std::min(taps, count + reach - t);
        float* yr = y.data() + static_cast<size_t>(t) * ch;
        std::memcpy(yr, l.bias, static_cast<size_t>(ch) * sizeof(float));
        for (int k = first; k < end; ++k) {
            const float* wk = l.weight + static_cast<size_t>(k) * ch;
            const float* xk = x.data() + static_cast<size_t>(t + k - reach) * ch;
            int c = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
            for (; c + 16 <= ch; c += 16) {
                vst1q_f32(yr + c, vmlaq_f32(vld1q_f32(yr + c), vld1q_f32(wk + c), vld1q_f32(xk + c)));
                vst1q_f32(yr + c + 4, vmlaq_f32(vld1q_f32(yr + c + 4), vld1q_f32(wk + c + 4), vld1q_f32(xk + c + 4)));
                vst1q_f32(yr + c + 8, vmlaq_f32(vld1q_f32(yr + c + 8), vld1q_f32(wk + c + 8), vld1q_f32(xk + c + 8)));
                vst1q_f32(yr + c + 12, vmlaq_f32(vld1q_f32(yr + c + 12), vld1q_f32(wk + c + 12), vld1q_f32(xk + c + 12)));
            }
            for (; c + 4 <= ch; c += 4) {
                vst1q_f32(yr + c, vmlaq_f32(vld1q_f32(yr + c), vld1q_f32(wk + c), vld1q_f32(xk + c)));
            }
#endif
            for (; c < ch; ++c) yr[c] += wk[c] * xk[c];
        }
    }
    return y;
}

std::vector<float> attentionOp(const std::vector<float>& x, int count, const TransformerBlock& blk, int heads, const std::vector<float>& turn) {
    const int ch = blk.out.out_dim, width = ch / heads;
    std::vector<float> qkv = linearOp(x.data(), count, blk.qkv);
    std::vector<float> mixed(static_cast<size_t>(count) * ch, 0.0f);
    const float scale = 1.0f / std::sqrt(static_cast<float>(width));
    std::vector<float> queries(static_cast<size_t>(count) * width);
    std::vector<float> keys(static_cast<size_t>(count) * width);
    std::vector<float> share(static_cast<size_t>(count) * count);
    for (int h = 0; h < heads; ++h) {
        for (int t = 0; t < count; ++t) {
            const int q = t * 3 * ch + h * width, k = q + ch, row = t * width;
            for (int i = 0; i < width; i += 2) {
                float cs = turn[row + i], sn = turn[row + i + 1];
                queries[row + i] = qkv[q + i] * cs - qkv[q + i + 1] * sn;
                queries[row + i + 1] = qkv[q + i] * sn + qkv[q + i + 1] * cs;
                keys[row + i] = qkv[k + i] * cs - qkv[k + i + 1] * sn;
                keys[row + i + 1] = qkv[k + i] * sn + qkv[k + i + 1] * cs;
            }
        }
        for (int t = 0; t < count; ++t) {
            float top = -1e30f;
            const float* qt = queries.data() + static_cast<size_t>(t) * width;
            for (int j = 0; j < count; ++j) {
                float s = dotProduct(qt, keys.data() + static_cast<size_t>(j) * width, width);
                share[t * count + j] = s;
                if (s > top) top = s;
            }
            float total = 0.0f;
            for (int j = 0; j < count; ++j) {
                float e = std::exp((share[t * count + j] - top) * scale);
                share[t * count + j] = e;
                total += e;
            }
            float inv_total = 1.0f / total;
            float* dst = mixed.data() + t * ch + h * width;
            for (int j = 0; j < count; ++j) {
                float w = share[t * count + j] * inv_total;
                const float* vr = qkv.data() + j * 3 * ch + 2 * ch + h * width;
                int i = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
                float32x4_t wv = vdupq_n_f32(w);
                for (; i + 16 <= width; i += 16) {
                    vst1q_f32(dst + i, vmlaq_f32(vld1q_f32(dst + i), vld1q_f32(vr + i), wv));
                    vst1q_f32(dst + i + 4, vmlaq_f32(vld1q_f32(dst + i + 4), vld1q_f32(vr + i + 4), wv));
                    vst1q_f32(dst + i + 8, vmlaq_f32(vld1q_f32(dst + i + 8), vld1q_f32(vr + i + 8), wv));
                    vst1q_f32(dst + i + 12, vmlaq_f32(vld1q_f32(dst + i + 12), vld1q_f32(vr + i + 12), wv));
                }
                for (; i + 4 <= width; i += 4) {
                    vst1q_f32(dst + i, vmlaq_f32(vld1q_f32(dst + i), vld1q_f32(vr + i), wv));
                }
#endif
                for (; i < width; ++i) dst[i] += w * vr[i];
            }
        }
    }
    return linearOp(mixed.data(), count, blk.out);
}

std::vector<float> transformerOp(std::vector<float> x, int count, const TransformerStack& stack, int heads) {
    const int width = stack.norm.out_dim / heads;
    std::vector<float> inv_freq(width / 2);
    for (int i = 0; i < width; i += 2) {
        inv_freq[i >> 1] = 1.0f / std::pow(10000.0f, static_cast<float>(i) / static_cast<float>(width));
    }
    std::vector<float> turn(static_cast<size_t>(count) * width);
    for (int t = 0; t < count; ++t) {
        for (int i = 0; i < width; i += 2) {
            float ang = static_cast<float>(t) * inv_freq[i >> 1];
            turn[t * width + i] = std::cos(ang);
            turn[t * width + i + 1] = std::sin(ang);
        }
    }
    for (const auto& blk : stack.blocks) {
        auto attn = attentionOp(normalizeOp(x, count, blk.norm1), count, blk, heads, turn);
        for (size_t i = 0; i < x.size(); ++i) x[i] += attn[i];
        auto near = depthwiseOp(normalizeOp(x, count, blk.norm2), count, blk.depthwise);
        auto expd = linearOp(near.data(), count, blk.expand);
        geluInPlace(&expd);
        auto proj = linearOp(expd.data(), count, blk.project);
        for (size_t i = 0; i < x.size(); ++i) x[i] += proj[i];
    }
    return normalizeOp(x, count, stack.norm);
}

struct FftPlan {
    int n;
    std::vector<std::pair<uint16_t, uint16_t>> swaps;
    std::vector<float> tw_cos;
    std::vector<float> tw_sin;
    std::vector<float> window;
    std::vector<float> window_norm;
    std::vector<float> window_sq;

    explicit FftPlan(int size) : n(size), window(size), window_norm(size), window_sq(size) {
        for (int i = 1, j = 0; i < size; ++i) {
            int bit = size >> 1;
            for (; j & bit; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) swaps.emplace_back(static_cast<uint16_t>(i), static_cast<uint16_t>(j));
        }
        for (int span = 2; span <= size; span <<= 1) {
            double ang = -2.0 * M_PI / static_cast<double>(span);
            for (int k = 0; k < span / 2; ++k) {
                tw_cos.push_back(static_cast<float>(std::cos(ang * k)));
                tw_sin.push_back(static_cast<float>(std::sin(ang * k)));
            }
        }
        float inv_n = 1.0f / static_cast<float>(size);
        for (int i = 0; i < size; ++i) {
            float w = static_cast<float>(0.5 - 0.5 * std::cos(2.0 * M_PI * i / size));
            window[i] = w;
            window_norm[i] = w * inv_n;
            window_sq[i] = w * w;
        }
    }

    void run(float* re, float* im) const {
        for (const auto& sw : swaps) {
            std::swap(re[sw.first], re[sw.second]);
            std::swap(im[sw.first], im[sw.second]);
        }
        size_t tw_off = 0;
        for (int span = 2; span <= n; span <<= 1) {
            const int half = span >> 1;
            const float* cs = tw_cos.data() + tw_off;
            const float* sn = tw_sin.data() + tw_off;
            for (int a = 0; a < n; a += span) {
                int k = 0;
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
                for (; k + 4 <= half; k += 4) {
                    int i0 = a + k, i1 = i0 + half;
                    float32x4_t vc = vld1q_f32(cs + k);
                    float32x4_t vs = vld1q_f32(sn + k);
                    float32x4_t r0 = vld1q_f32(re + i0);
                    float32x4_t m0 = vld1q_f32(im + i0);
                    float32x4_t r1 = vld1q_f32(re + i1);
                    float32x4_t m1 = vld1q_f32(im + i1);
                    float32x4_t xr = vmlsq_f32(vmulq_f32(r1, vc), m1, vs);
                    float32x4_t xi = vmlaq_f32(vmulq_f32(r1, vs), m1, vc);
                    vst1q_f32(re + i1, vsubq_f32(r0, xr));
                    vst1q_f32(im + i1, vsubq_f32(m0, xi));
                    vst1q_f32(re + i0, vaddq_f32(r0, xr));
                    vst1q_f32(im + i0, vaddq_f32(m0, xi));
                }
#endif
                for (; k < half; ++k) {
                    int i0 = a + k, i1 = i0 + half;
                    float c = cs[k], s = sn[k];
                    float xr = re[i1] * c - im[i1] * s;
                    float xi = re[i1] * s + im[i1] * c;
                    re[i1] = re[i0] - xr; im[i1] = im[i0] - xi;
                    re[i0] += xr; im[i0] += xi;
                }
            }
            tw_off += static_cast<size_t>(half);
        }
    }
};

const FftPlan& getFftPlan(int size) {
    static const FftPlan plan512(512);
    static const FftPlan plan256(256);
    return size == 512 ? plan512 : plan256;
}

template <typename CepFn>
std::vector<float> filteredSource(const std::vector<float>& source, int size, int hop, int frames, CepFn cepstrum_fn) {
    const int half = size / 2;
    const FftPlan& plan = getFftPlan(size);
    std::vector<float> re(size), im(size), cr(size), ci(size);
    std::vector<float> sum(static_cast<size_t>(frames) * hop + size, 0.0f);
    std::vector<float> weight(sum.size(), 0.0f);
    const int src_len = static_cast<int>(source.size());
    for (int f = 0; f < frames; ++f) {
        int first = f * hop - half;
        for (int n = 0; n < size; ++n) {
            int at = first + n;
            re[n] = (at >= 0 && at < src_len) ? source[at] * plan.window[n] : 0.0f;
            im[n] = 0.0f;
        }
        plan.run(re.data(), im.data());
        std::fill(cr.begin(), cr.end(), 0.0f);
        std::fill(ci.begin(), ci.end(), 0.0f);
        cepstrum_fn(f, cr);
        plan.run(cr.data(), ci.data());
        for (int k = 0; k <= half; ++k) {
            float gain = std::exp(std::clamp(cr[k], -14.0f, 6.0f));
            float cs = gain * std::cos(ci[k]), sn = gain * std::sin(ci[k]);
            float a = re[k], b = im[k];
            re[k] = a * cs - b * sn;
            im[k] = -(a * sn + b * cs);
            if (k > 0 && k < half) { re[size - k] = re[k]; im[size - k] = -im[k]; }
        }
        plan.run(re.data(), im.data());
        size_t base = static_cast<size_t>(f) * hop;
        for (int n = 0; n < size; ++n) {
            sum[base + n] += re[n] * plan.window_norm[n];
            weight[base + n] += plan.window_sq[n];
        }
    }
    std::vector<float> out(source.size());
    for (size_t n = 0; n < out.size(); ++n) {
        float w = weight[n + half];
        out[n] = w > 1e-10f ? (sum[n + half] / w) : 0.0f;
    }
    return out;
}

std::vector<float> pulseTrainSource(const std::vector<float>& f0, int hop, int sample_rate, float limit) {
    const int frames = static_cast<int>(f0.size());
    std::vector<float> out(static_cast<size_t>(frames) * hop);
    std::vector<float> knots(frames + 1);
    for (int i = 0; i <= frames; ++i) {
        knots[i] = std::logf(std::max(f0[std::min(i, frames - 1)], 1.0f));
    }
    constexpr float kTwoPi = static_cast<float>(2.0 * M_PI);
    const float inv_sr = 1.0f / static_cast<float>(sample_rate);
    const float lim_sr = limit * static_cast<float>(sample_rate);
    double turns = 0.0;
    for (int low = 0; low < frames; ++low) {
        float freq = std::expf(knots[low]);
        const float freq_step = std::expf((knots[low + 1] - knots[low]) / static_cast<float>(hop));
        float* dst = out.data() + static_cast<size_t>(low) * hop;
        for (int k = 0; k < hop; ++k) {
            turns += static_cast<double>(freq * inv_sr);
            turns -= std::floor(turns);
            float theta = static_cast<float>(turns) * kTwoPi;
            float harmonics = std::max(lim_sr / freq, 1.0f);
            float whole = std::floorf(harmonics);
            float below = 2.0f * std::sinf(theta * 0.5f);
            float core = std::abs(below) < 1e-5f ? whole : (std::sinf((whole + 0.5f) * theta) / below - 0.5f);
            dst[k] = (core + (harmonics - whole) * std::cosf((whole + 1.0f) * theta)) * std::sqrtf(2.0f / harmonics);
            freq *= freq_step;
        }
    }
    return out;
}

const std::vector<float>& getGaussianRing() {
    static const std::vector<float> ring = []() {
        constexpr size_t kRingSize = 8192;
        uint32_t state = 0x9E3779B9u;
        auto uniform = [&]() -> double {
            state += 0x6D2B79F5u;
            uint32_t z = (state ^ (state >> 15)) * (state | 1u);
            z ^= z + (z ^ (z >> 7)) * (z | 61u);
            return static_cast<double>(z ^ (z >> 14)) / 4294967296.0;
        };
        std::vector<float> r(kRingSize);
        for (size_t i = 0; i < kRingSize; i += 2) {
            double u1 = std::min(1.0 - 1e-12, uniform());
            double rad = std::sqrt(-2.0 * std::log(1.0 - u1));
            double ang = 2.0 * M_PI * uniform();
            r[i] = static_cast<float>(rad * std::cos(ang));
            r[i + 1] = static_cast<float>(rad * std::sin(ang));
        }
        return r;
    }();
    return ring;
}

std::vector<float> gaussianNoiseSource(size_t count, uint32_t seed = 0) {
    const auto& ring = getGaussianRing();
    const size_t mask = ring.size() - 1;
    std::vector<float> out(count);
    size_t idx = static_cast<size_t>(seed * 2654435761u) & mask;
    for (size_t i = 0; i < count; ++i) {
        out[i] = ring[(idx + i) & mask];
    }
    return out;
}

inline double lifterCoeff(int n) { return n == 0 ? 2.0 : 1.0 / (1.0 + n / 4.0); }

inline int roundEven(float x) {
    float down = std::floor(x), rest = x - down;
    int d = static_cast<int>(down);
    return (rest > 0.5f || (rest == 0.5f && (d % 2 != 0))) ? d + 1 : d;
}

}  // namespace

struct VocaModel {
    std::unordered_map<std::string, Tensor> tensors;
    std::unordered_map<std::string, std::vector<PhoneUnit>> lexicon;
    std::unordered_map<std::string, std::vector<PhoneUnit>> func_words;
    std::unordered_map<char, std::vector<PhoneUnit>> letter_names;
    const float* tokens_w = nullptr;
    const float* stress_w = nullptr;
    const float* voices_w = nullptr;
    const float* frame_voices_w = nullptr;
    TransformerStack encoder, prosody_stack, acoustic_stack;
    Layer dur_conv1, dur_norm1, dur_conv2, dur_norm2, dur_out;
    Layer frame_pos, prosody_head, prosody_proj, voc_harm, voc_noise;

    Layer getLayer(const std::string& prefix) const {
        auto wit = tensors.find("model/" + prefix + ".weight");
        auto bit = tensors.find("model/" + prefix + ".bias");
        Layer l{};
        if (wit != tensors.end()) {
            l.weight = wit->second.data.data();
            const auto& sh = wit->second.shape;
            l.out_dim = sh.empty() ? 0 : sh[0];
            l.in_dim = sh.size() > 1 ? sh[1] : l.out_dim;
            l.taps = sh.size() > 2 ? sh[2] : 1;
        }
        if (bit != tensors.end()) l.bias = bit->second.data.data();
        return l;
    }

    TransformerStack getStack(const std::string& name, int layers) const {
        TransformerStack st{};
        st.norm = getLayer(name + ".norm");
        st.blocks.resize(layers);
        for (int i = 0; i < layers; ++i) {
            std::string p = name + ".blocks." + std::to_string(i) + ".";
            st.blocks[i] = {
                getLayer(p + "norm1"), getLayer(p + "attention.qkv"), getLayer(p + "attention.out"),
                getLayer(p + "norm2"), getLayer(p + "depthwise"), getLayer(p + "expand"), getLayer(p + "project")
            };
        }
        return st;
    }

    std::vector<PhoneUnit> lookupWord(const std::string& raw_word, bool is_clause_end) const {
        std::string w;
        w.reserve(raw_word.size());
        for (char c : raw_word) {
            if (std::isalpha(static_cast<unsigned char>(c)) || c == '\'') {
                w.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
            }
        }
        if (w.empty()) return {};
        if (!is_clause_end) {
            auto fit = func_words.find(w);
            if (fit != func_words.end()) return fit->second;
        }
        auto it = lexicon.find(w);
        if (it != lexicon.end()) return it->second;
        if (w.size() > 2 && w.substr(w.size() - 2) == "'s") {
            auto base = lookupWord(w.substr(0, w.size() - 2), true);
            if (!base.empty()) return pluralizePhones(std::move(base));
        }
        if (w.size() > 4 && w.substr(w.size() - 3) == "ing") {
            std::string s1 = w.substr(0, w.size() - 3);
            auto bIt = lexicon.find(s1);
            if (bIt == lexicon.end()) bIt = lexicon.find(s1 + "e");
            if (bIt != lexicon.end()) {
                auto p = bIt->second;
                p.push_back({47, 0}); p.push_back({34, 0});
                return p;
            }
        }
        if (w.size() > 3 && w.substr(w.size() - 2) == "ed") {
            std::string s1 = w.substr(0, w.size() - 2);
            auto bIt = lexicon.find(s1);
            if (bIt == lexicon.end()) bIt = lexicon.find(w.substr(0, w.size() - 1));
            if (bIt != lexicon.end()) return pastPhones(bIt->second);
        }
        if (w.size() > 3 && w.back() == 's') {
            std::string s1 = w.substr(0, w.size() - 1);
            auto bIt = lexicon.find(s1);
            if (bIt == lexicon.end() && w.size() > 4 && w.substr(w.size() - 2) == "es") {
                bIt = lexicon.find(w.substr(0, w.size() - 2));
            }
            if (bIt != lexicon.end()) return pluralizePhones(bIt->second);
        }
        bool all_upper = raw_word.size() <= 5 && std::all_of(raw_word.begin(), raw_word.end(), [](unsigned char c) { return std::isupper(c); });
        if (all_upper) {
            std::vector<PhoneUnit> sp;
            for (char c : w) {
                auto lit = letter_names.find(c);
                if (lit != letter_names.end()) sp.insert(sp.end(), lit->second.begin(), lit->second.end());
            }
            if (!sp.empty()) return sp;
        }
        return fallbackGraphemePhones(w);
    }
};

namespace {

uint8_t punctToId(char c) {
    switch (c) {
        case ',': return 5; case '.': return 6; case '?': return 7; case '!': return 8;
        case ';': return 9; case ':': return 10; case '"': return 11; case '(': return 13;
        case ')': return 14; case '-': return 15; default: return 0;
    }
}

inline int decodeDevanagariAt(const std::string& s, size_t at) {
    if (at + 2 >= s.size()) return -1;
    uint8_t b0 = static_cast<uint8_t>(s[at]);
    uint8_t b1 = static_cast<uint8_t>(s[at + 1]);
    uint8_t b2 = static_cast<uint8_t>(s[at + 2]);
    if (b0 != 0xE0 || (b1 != 0xA4 && b1 != 0xA5)) return -1;
    int cp = ((b1 & 0x3F) << 6) | (b2 & 0x3F);
    return (cp >= 0x0900 && cp <= 0x097F) ? cp : -1;
}

std::vector<PhoneUnit> phonemizeDevanagariWord(const std::vector<int>& cps) {
    std::vector<PhoneUnit> out;
    bool stressed = false;
    auto pushVowel = [&](uint8_t id) {
        uint8_t st = stressed ? 0 : 1;
        stressed = true;
        out.push_back({id, st});
    };
    for (size_t i = 0; i < cps.size(); ++i) {
        int cp = cps[i];
        int next = (i + 1 < cps.size()) ? cps[i + 1] : -1;
        if (cp == 0x0901 || cp == 0x0902) { out.push_back({34, 0}); continue; } // ँ / ं -> ŋ
        if (cp == 0x0903) { out.push_back({31, 0}); continue; } // ः -> h
        if (cp >= 0x0904 && cp <= 0x0914) {
            switch (cp) {
                case 0x0905: pushVowel(52); break; // अ -> ʌ
                case 0x0906: pushVowel(53); break; // आ -> ɑː
                case 0x0907: pushVowel(47); break; // इ -> ɪ
                case 0x0908: pushVowel(49); break; // ई -> iː
                case 0x0909: pushVowel(56); break; // उ -> ʊ
                case 0x090A: pushVowel(57); break; // ऊ -> uː
                case 0x090B: out.push_back({41, 0}); pushVowel(47); break; // ऋ -> ɾɪ
                case 0x090F: pushVowel(59); break; // ए -> eɪ
                case 0x0910: pushVowel(60); break; // ऐ -> aɪ
                case 0x0913: pushVowel(62); break; // ओ -> oʊ
                case 0x0914: pushVowel(63); break; // औ -> aʊ
                default: pushVowel(43); break;
            }
            continue;
        }
        if (cp >= 0x0915 && cp <= 0x0939) {
            uint8_t c1 = 0, c2 = 0;
            switch (cp) {
                case 0x0915: c1 = 21; break; // क -> k
                case 0x0916: c1 = 21; c2 = 31; break; // ख -> k h
                case 0x0917: c1 = 22; break; // ग -> ɡ
                case 0x0918: c1 = 22; c2 = 31; break; // घ -> ɡ h
                case 0x0919: c1 = 34; break; // ङ -> ŋ
                case 0x091A: c1 = 39; break; // च -> tʃ
                case 0x091B: c1 = 39; c2 = 31; break; // छ -> tʃ h
                case 0x091C: c1 = 40; break; // ज -> dʒ
                case 0x091D: c1 = 40; c2 = 31; break; // झ -> dʒ h
                case 0x091E: c1 = 33; break; // ञ -> n
                case 0x091F: case 0x0924: c1 = 19; break; // ट/त -> t
                case 0x0920: case 0x0925: c1 = 19; c2 = 31; break; // ठ/थ -> t h
                case 0x0921: case 0x0926: c1 = 20; break; // ड/द -> d
                case 0x0922: case 0x0927: c1 = 20; c2 = 31; break; // ढ/ध -> d h
                case 0x0923: case 0x0928: case 0x0929: c1 = 33; break; // ण/न -> n
                case 0x092A: c1 = 17; break; // प -> p
                case 0x092B: c1 = 23; break; // फ -> f
                case 0x092C: c1 = 18; break; // ब -> b
                case 0x092D: c1 = 18; c2 = 31; break; // भ -> b h
                case 0x092E: c1 = 32; break; // म -> m
                case 0x092F: c1 = 37; break; // य -> j
                case 0x0930: case 0x0931: c1 = 41; break; // र -> ɾ
                case 0x0932: case 0x0933: case 0x0934: c1 = 35; break; // ल -> l
                case 0x0935: c1 = 38; break; // व -> w
                case 0x0936: case 0x0937: c1 = 29; break; // श/ष -> ʃ
                case 0x0938: c1 = 27; break; // स -> s
                case 0x0939: c1 = 31; break; // ह -> h
                default: break;
            }
            if (c1 != 0) out.push_back({c1, 0});
            if (c2 != 0) out.push_back({c2, 0});
            if (next == 0x094D) { // ् virama suppresses inherent vowel
                ++i;
            } else if (next >= 0x093E && next <= 0x094C) {
                ++i;
                switch (next) {
                    case 0x093E: pushVowel(53); break; // ा -> ɑː
                    case 0x093F: pushVowel(47); break; // ि -> ɪ
                    case 0x0940: pushVowel(49); break; // ी -> iː
                    case 0x0941: pushVowel(56); break; // ु -> ʊ
                    case 0x0942: pushVowel(57); break; // ू -> uː
                    case 0x0943: out.push_back({41, 0}); pushVowel(47); break; // ृ -> ɾɪ
                    case 0x0947: pushVowel(59); break; // े -> eɪ
                    case 0x0948: pushVowel(60); break; // ै -> aɪ
                    case 0x094B: pushVowel(62); break; // ो -> oʊ
                    case 0x094C: pushVowel(63); break; // ौ -> aʊ
                    default: pushVowel(43); break;
                }
            } else {
                // Nepali word-final schwa deletion on multi-syllable words ending in a bare consonant
                bool word_end = (i + 1 == cps.size());
                bool after_conjunct = (i >= 2 && cps[i - 1] == 0x094D);
                if (!word_end || !stressed || after_conjunct) {
                    pushVowel(43); // inherent schwa ə
                }
            }
        }
    }
    return out;
}

void phonemizeText(const VocaModel& m, const std::string& text, std::vector<int>* ids, std::vector<int>* stress) {
    ids->clear(); stress->clear();
    ids->push_back(1); stress->push_back(0);  // <bos>
    bool prev_was_phone = false;
    size_t i = 0, n = text.size();
    while (i < n) {
        unsigned char c = static_cast<unsigned char>(text[i]);
        if (std::isspace(c)) { ++i; continue; }
        int dev = decodeDevanagariAt(text, i);
        if (dev >= 0) {
            if (dev == 0x0964 || dev == 0x0965) { // । or ॥ danda sentence stop
                if (ids->back() != 6 && ids->size() < kMaxTokens - 2) {
                    ids->push_back(6); stress->push_back(0);
                    prev_was_phone = false;
                }
                i += 3;
                continue;
            }
            if (dev >= 0x0966 && dev <= 0x096F) { // ०..९ Devanagari digits
                long long val = 0;
                int digits = 0;
                while (i < n && digits < 9) {
                    int dcp = decodeDevanagariAt(text, i);
                    if (dcp < 0x0966 || dcp > 0x096F) break;
                    val = val * 10 + (dcp - 0x0966);
                    i += 3;
                    ++digits;
                }
                std::string expanded = numberToWords(val);
                std::vector<int> sub_ids, sub_st;
                phonemizeText(m, expanded, &sub_ids, &sub_st);
                if (sub_ids.size() > 2) {
                    if (prev_was_phone) { ids->push_back(3); stress->push_back(0); }
                    for (size_t k = 1; k + 1 < sub_ids.size(); ++k) {
                        if (ids->size() < kMaxTokens - 2) {
                            ids->push_back(sub_ids[k]); stress->push_back(sub_st[k]);
                        }
                    }
                    prev_was_phone = true;
                }
                continue;
            }
            std::vector<int> cps;
            while (i < n) {
                int cp = decodeDevanagariAt(text, i);
                if (cp < 0 || cp == 0x0964 || cp == 0x0965 || (cp >= 0x0966 && cp <= 0x096F)) break;
                cps.push_back(cp);
                i += 3;
            }
            auto phones = phonemizeDevanagariWord(cps);
            if (!phones.empty()) {
                if (prev_was_phone) { ids->push_back(3); stress->push_back(0); }
                for (const auto& pu : phones) {
                    if (ids->size() < kMaxTokens - 2) {
                        ids->push_back(pu.id); stress->push_back(pu.stress);
                    }
                }
                prev_was_phone = true;
            }
            continue;
        }
        if (std::isdigit(c)) {
            size_t j = i;
            long long val = 0;
            while (j < n && std::isdigit(static_cast<unsigned char>(text[j])) && (j - i) < 9) {
                val = val * 10 + (text[j] - '0');
                ++j;
            }
            i = j;
            std::string expanded = numberToWords(val);
            std::vector<int> sub_ids, sub_st;
            phonemizeText(m, expanded, &sub_ids, &sub_st);
            if (sub_ids.size() > 2) {
                if (prev_was_phone) { ids->push_back(3); stress->push_back(0); }
                for (size_t k = 1; k + 1 < sub_ids.size(); ++k) {
                    if (ids->size() < kMaxTokens - 2) {
                        ids->push_back(sub_ids[k]); stress->push_back(sub_st[k]);
                    }
                }
                prev_was_phone = true;
            }
            continue;
        }
        if (std::isalpha(c)) {
            size_t j = i;
            while (j < n && (std::isalpha(static_cast<unsigned char>(text[j])) ||
                             (text[j] == '\'' && j + 1 < n && std::isalpha(static_cast<unsigned char>(text[j + 1]))))) {
                ++j;
            }
            std::string word = text.substr(i, j - i);
            i = j;
            size_t pk = i;
            while (pk < n && std::isspace(static_cast<unsigned char>(text[pk]))) ++pk;
            bool clause_end = (pk >= n || punctToId(text[pk]) != 0);
            auto phones = m.lookupWord(word, clause_end);
            if (!phones.empty()) {
                if (prev_was_phone) { ids->push_back(3); stress->push_back(0); }
                for (const auto& pu : phones) {
                    if (ids->size() < kMaxTokens - 2) {
                        ids->push_back(pu.id); stress->push_back(pu.stress);
                    }
                }
                prev_was_phone = true;
            }
            continue;
        }
        uint8_t pid = punctToId(static_cast<char>(c));
        if (pid != 0 && ids->back() != pid && ids->size() < kMaxTokens - 2) {
            ids->push_back(pid); stress->push_back(0);
            prev_was_phone = false;
        }
        ++i;
    }
    if (ids->size() > 1 && ids->back() >= kPhoneOffset) {
        ids->push_back(6); stress->push_back(0);  // trailing '.' so last word never breaks off
    }
    ids->push_back(2); stress->push_back(0);  // <eos>
}

std::vector<float> synthesizeUtterance(const VocaModel& m, const std::vector<int>& ids, const std::vector<int>& stress, int voice, float speed) {
    const int count = static_cast<int>(ids.size());
    if (count < 3 || count > kMaxTokens) return {};
    voice = std::clamp(voice, 0, 1);
    speed = std::clamp(speed, 0.5f, 2.0f);

    std::vector<float> x(static_cast<size_t>(count) * kChannels);
    for (int n = 0; n < count; ++n) {
        const float* tw = m.tokens_w + ids[n] * kChannels;
        const float* sw = m.stress_w + stress[n] * kChannels;
        const float* vw = m.voices_w + voice * kChannels;
        float* dst = x.data() + static_cast<size_t>(n) * kChannels;
        for (int i = 0; i < kChannels; ++i) dst[i] = tw[i] + sw[i] + vw[i];
    }
    auto hidden = transformerOp(std::move(x), count, m.encoder, kHeads);

    auto c1 = convolveOp(hidden, count, m.dur_conv1);
    geluInPlace(&c1);
    auto n1 = normalizeOp(c1, count, m.dur_norm1);
    auto c2 = convolveOp(n1, count, m.dur_conv2);
    geluInPlace(&c2);
    auto n2 = normalizeOp(c2, count, m.dur_norm2);
    auto dur_logits = linearOp(n2.data(), count, m.dur_out);

    std::vector<int> durations(count, 0);
    int frames = 0;
    const float max_log_dur = std::log1pf(400.0f);
    for (int n = 0; n < count; ++n) {
        float length = std::expm1f(std::clamp(dur_logits[2 * n], 0.0f, max_log_dur));
        bool present = !isPauseId(static_cast<uint8_t>(ids[n])) || dur_logits[2 * n + 1] > 0.0f;
        int d = present ? std::max(roundEven(length / speed), 1) : 0;
        durations[n] = d;
        frames += d;
    }
    if (frames < 1 || frames > kMaxFrames) return {};

    std::vector<int> owner(frames);
    std::vector<float> place(static_cast<size_t>(frames) * 3);
    for (int n = 0, t = 0; n < count; ++n) {
        float ld = std::log1pf(static_cast<float>(durations[n])) / 4.0f;
        for (int k = 0; k < durations[n]; ++k, ++t) {
            owner[t] = n;
            float p = (static_cast<float>(k) + 0.5f) / static_cast<float>(durations[n]);
            place[3 * t] = p; place[3 * t + 1] = 1.0f - p; place[3 * t + 2] = ld;
        }
    }
    auto fpos = linearOp(place.data(), frames, m.frame_pos);
    for (int t = 0; t < frames; ++t) {
        const float* hr = hidden.data() + static_cast<size_t>(owner[t]) * kChannels;
        const float* fv = m.frame_voices_w + voice * kChannels;
        float* dst = fpos.data() + static_cast<size_t>(t) * kChannels;
        for (int i = 0; i < kChannels; ++i) dst[i] += hr[i] + fv[i];
    }

    auto melody = transformerOp(std::move(fpos), frames, m.prosody_stack, kHeads);
    auto prosody = linearOp(melody.data(), frames, m.prosody_head);
    std::vector<float> f0(frames), told(static_cast<size_t>(frames) * 3);
    for (int t = 0; t < frames; ++t) {
        if (isVowelId(static_cast<uint8_t>(ids[owner[t]]))) {
            prosody[3 * t + 1] = std::max(prosody[3 * t + 1], 2.0f);
        }
        float pitch = prosody[3 * t];
        told[3 * t] = std::clamp(pitch, -2.0f, 1.5f);
        told[3 * t + 1] = prosody[3 * t + 1] > 0.0f ? 1.0f : 0.0f;
        told[3 * t + 2] = std::clamp(prosody[3 * t + 2], -4.5f, 3.0f);
        f0[t] = std::clamp(kPitchRefHz * std::exp(pitch), kF0Min, kF0Max);
    }

    auto pproj = linearOp(told.data(), frames, m.prosody_proj);
    for (size_t i = 0; i < pproj.size(); ++i) pproj[i] += melody[i];
    auto acoustic = transformerOp(std::move(pproj), frames, m.acoustic_stack, kHeads);

    const int samples = frames * kHop;
    std::vector<float> noise_part;
    auto computeNoise = [&]() {
        auto g = linearOp(acoustic.data(), frames, m.voc_noise);
        const int gw = m.voc_noise.out_dim, g_steps = kHop / kNoiseHop;
        noise_part = filteredSource(
            gaussianNoiseSource(static_cast<size_t>(samples), 0),
            kNoiseFft, kNoiseHop, g_steps * frames + 3,
            [&](int f, std::vector<float>& cep) {
                int sub = std::min(f, g_steps * frames - 1);
                int here = sub / g_steps;
                for (int i = 0; i < kNoiseCep; ++i) cep[i] = static_cast<float>(g[here * gw + i] * lifterCoeff(i));
                cep[0] += g[here * gw + kNoiseCep + (sub % g_steps)];
            }
        );
    };
    std::thread noiseThread;
    if (frames >= 24) {
        noiseThread = std::thread(computeNoise);
    } else {
        computeNoise();
    }

    auto h = linearOp(acoustic.data(), frames, m.voc_harm);
    const int hw = m.voc_harm.out_dim, h_steps = kHop / kSynthHop;
    auto voiced = filteredSource(
        pulseTrainSource(f0, kHop, kSampleRate, kHarmonicLimit),
        kSynthFft, kSynthHop, h_steps * frames + 3,
        [&](int f, std::vector<float>& cep) {
            int sub = std::min(f, h_steps * frames - 1);
            int here = sub / h_steps, next = std::min((sub + 1) / h_steps, frames - 1);
            for (int i = 0; i < kCepCausal; ++i) {
                cep[i] = static_cast<float>(0.5 * (h[here * hw + i] + h[next * hw + i]) * lifterCoeff(i));
            }
            for (int i = 0; i < kCepAnti; ++i) {
                int at = kCepCausal + i;
                cep[kSynthFft - 1 - i] = static_cast<float>(0.5 * (h[here * hw + at] + h[next * hw + at]) * lifterCoeff(i + 1));
            }
            cep[0] += h[here * hw + kCepCausal + kCepAnti + (sub % h_steps)];
        }
    );
    if (noiseThread.joinable()) noiseThread.join();

    float peak = 0.0f;
    for (int i = 0; i < samples; ++i) {
        voiced[i] += noise_part[i];
        peak = std::max(peak, std::abs(voiced[i]));
    }
    if (peak > 0.99f) {
        float scale = 0.99f / peak;
        for (float& s : voiced) s *= scale;
    }
    return voiced;
}

}  // namespace

VocaModel* voca_load(const char* gguf_path, std::string* err_out) {
    int fd = open(gguf_path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        if (err_out) *err_out = "Could not open TinyVoca model file";
        return nullptr;
    }
    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_size < 1024) {
        close(fd);
        if (err_out) *err_out = "TinyVoca model file is invalid";
        return nullptr;
    }
    size_t file_size = static_cast<size_t>(st.st_size);
    void* map = mmap(nullptr, file_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (map == MAP_FAILED) {
        if (err_out) *err_out = "Could not memory-map TinyVoca model";
        return nullptr;
    }
    madvise(map, file_size, MADV_SEQUENTIAL);

    GgufReader r{reinterpret_cast<const uint8_t*>(map), file_size, 0};
    if (r.u32() != 0x46554747u) {
        munmap(map, file_size);
        if (err_out) *err_out = "Not a valid GGUF model file";
        return nullptr;
    }
    (void)r.u32();  // version
    uint64_t tensor_count = r.u64();
    uint64_t key_count = r.u64();

    std::vector<std::string> emb_names;
    std::vector<uint64_t> emb_offsets;
    const uint8_t* emb_data = nullptr;
    size_t emb_data_len = 0;
    size_t alignment = 32;

    for (uint64_t i = 0; i < key_count && r.at < r.size; ++i) {
        std::string key = r.str();
        uint32_t vtype = r.u32();
        if (key == "general.alignment" && vtype == 4) {
            alignment = std::max<size_t>(1, r.u32());
        } else if (key == "audiocpp.embedded_files.names" && vtype == 9) {
            uint32_t inner = r.u32();
            uint64_t cnt = r.u64();
            if (inner == 8) {
                emb_names.reserve(cnt);
                for (uint64_t k = 0; k < cnt; ++k) emb_names.push_back(r.str());
            }
        } else if (key == "audiocpp.embedded_files.offsets" && vtype == 9) {
            uint32_t inner = r.u32();
            uint64_t cnt = r.u64();
            if (inner == 10) {
                emb_offsets.reserve(cnt);
                for (uint64_t k = 0; k < cnt; ++k) emb_offsets.push_back(r.u64());
            }
        } else if (key == "audiocpp.embedded_files.data" && vtype == 9) {
            uint32_t inner = r.u32();
            uint64_t cnt = r.u64();
            if (inner < 2 && r.at + cnt <= r.size) {
                emb_data = r.base + r.at;
                emb_data_len = static_cast<size_t>(cnt);
                r.at += emb_data_len;
            }
        } else {
            r.skipValue(vtype);
        }
    }

    struct TensorMeta { std::string name; std::vector<int> shape; uint32_t type; uint64_t offset; };
    std::vector<TensorMeta> tmetas;
    tmetas.reserve(tensor_count);
    for (uint64_t i = 0; i < tensor_count && r.at < r.size; ++i) {
        TensorMeta tm;
        tm.name = r.str();
        uint32_t ndims = r.u32();
        tm.shape.resize(ndims);
        for (uint32_t d = 0; d < ndims; ++d) tm.shape[ndims - 1 - d] = static_cast<int>(r.u64());
        tm.type = r.u32();
        tm.offset = r.u64();
        tmetas.push_back(std::move(tm));
    }

    size_t data_start = ((r.at + alignment - 1) / alignment) * alignment;
    auto* model = new VocaModel();
    for (const auto& tm : tmetas) {
        if (tm.name.rfind("model/", 0) != 0) continue;
        size_t count = 1;
        for (int s : tm.shape) count *= static_cast<size_t>(s);
        if (data_start + tm.offset < file_size) {
            Tensor t;
            t.shape = tm.shape;
            unpackTensor(r.base + data_start + tm.offset, tm.type, count, &t.data);
            if (tm.shape.size() == 3 && tm.shape[1] > 1 && tm.shape[2] > 1) {
                // Transpose [out_d, in_d, taps] -> [out_d, taps, in_d] for contiguous SIMD dot-products in convolveOp
                const int od = tm.shape[0], id = tm.shape[1], tp = tm.shape[2];
                std::vector<float> tr(t.data.size());
                for (int o = 0; o < od; ++o) {
                    for (int k = 0; k < tp; ++k) {
                        float* dst = tr.data() + (static_cast<size_t>(o) * tp + k) * id;
                        const float* src = t.data.data() + static_cast<size_t>(o) * id * tp + k;
                        for (int i = 0; i < id; ++i) dst[i] = src[static_cast<size_t>(i) * tp];
                    }
                }
                t.data = std::move(tr);
            } else if (tm.shape.size() == 3 && tm.shape[1] == 1 && tm.shape[2] > 1) {
                // Transpose depthwise [ch, 1, taps] -> [taps, ch] for 16-wide contiguous ARM NEON depthwiseOp
                const int ch = tm.shape[0], tp = tm.shape[2];
                std::vector<float> tr(t.data.size());
                for (int k = 0; k < tp; ++k) {
                    float* dst = tr.data() + static_cast<size_t>(k) * ch;
                    for (int c = 0; c < ch; ++c) {
                        dst[c] = t.data[static_cast<size_t>(c) * tp + k];
                    }
                }
                t.data = std::move(tr);
            }
            model->tensors[tm.name] = std::move(t);
        }
    }

    std::unordered_map<std::string, uint8_t> sym_to_id;
    for (int i = 0; i < kPhoneCount; ++i) sym_to_id[kPhoneSymbols[i]] = static_cast<uint8_t>(kPhoneOffset + i);

    if (emb_data && emb_offsets.size() == emb_names.size() + 1) {
        for (size_t i = 0; i < emb_names.size(); ++i) {
            uint64_t o0 = emb_offsets[i], o1 = emb_offsets[i + 1];
            if (o1 > o0 && o1 <= emb_data_len) {
                const uint8_t* ptr = emb_data + o0;
                size_t len = static_cast<size_t>(o1 - o0);
                if (emb_names[i] == "phonemizer/lexicon.packed.gz") {
                    std::vector<uint8_t> unpacked;
                    if (gunzipBuffer(ptr, len, &unpacked)) unpackLexiconBytes(unpacked, &model->lexicon);
                } else if (emb_names[i] == "phonemizer/function_words.txt" || emb_names[i] == "phonemizer/letters.txt") {
                    std::string txt(reinterpret_cast<const char*>(ptr), len);
                    size_t pos = 0;
                    while (pos < txt.size()) {
                        size_t eol = txt.find('\n', pos);
                        if (eol == std::string::npos) eol = txt.size();
                        std::string ln = txt.substr(pos, eol - pos);
                        pos = eol + 1;
                        if (ln.empty() || ln[0] == '#') continue;
                        size_t t1 = ln.find('\t');
                        if (t1 == std::string::npos) continue;
                        std::string key = ln.substr(0, t1);
                        if (emb_names[i] == "phonemizer/letters.txt" && key.size() == 1) {
                            model->letter_names[key[0]] = parseIpaPhones(ln.substr(t1 + 1), sym_to_id);
                        } else if (emb_names[i] == "phonemizer/function_words.txt") {
                            size_t t2 = ln.find('\t', t1 + 1);
                            if (t2 != std::string::npos) {
                                size_t t3 = ln.find('\t', t2 + 1);
                                std::string form = ln.substr(t2 + 1, t3 == std::string::npos ? std::string::npos : t3 - (t2 + 1));
                                auto ph = parseIpaPhones(form, sym_to_id);
                                for (auto& u : ph) u.stress = 0;
                                model->func_words[key] = std::move(ph);
                            }
                        }
                    }
                }
            }
        }
    }
    munmap(map, file_size);

    model->tokens_w = model->tensors["model/tokens.weight"].data.data();
    model->stress_w = model->tensors["model/stress.weight"].data.data();
    model->voices_w = model->tensors["model/voices.weight"].data.data();
    model->frame_voices_w = model->tensors["model/frame_voices.weight"].data.data();
    model->encoder = model->getStack("encoder", kEncoderLayers);
    model->dur_conv1 = model->getLayer("duration.conv1");
    model->dur_norm1 = model->getLayer("duration.norm1");
    model->dur_conv2 = model->getLayer("duration.conv2");
    model->dur_norm2 = model->getLayer("duration.norm2");
    model->dur_out = model->getLayer("duration.out");
    model->frame_pos = model->getLayer("frame_position");
    model->prosody_stack = model->getStack("prosody_stack", kProsodyLayers);
    model->prosody_head = model->getLayer("prosody_head");
    model->prosody_proj = model->getLayer("prosody_projection");
    model->acoustic_stack = model->getStack("acoustic_stack", kAcousticLayers);
    model->voc_harm = model->getLayer("vocoder.harmonic");
    model->voc_noise = model->getLayer("vocoder.noise");

    if (!model->tokens_w || !model->voc_harm.weight) {
        delete model;
        if (err_out) *err_out = "TinyVoca model weights are incomplete";
        return nullptr;
    }
    return model;
}

void voca_free(VocaModel* model) {
    delete model;
}

bool voca_speak(VocaModel* model, const char* text, int voice_id, float speed, VocaPcmCallback cb, void* user, std::string* err_out) {
    if (!model || !text || !cb) return false;
    std::string full(text);
    // Split long text into clause/sentence chunks (with a shorter first chunk for faster TTFA and Devanagari danda support).
    size_t start = 0, n = full.size();
    bool first_chunk = true;
    while (start < n) {
        while (start < n && std::isspace(static_cast<unsigned char>(full[start]))) ++start;
        if (start >= n) break;
        const size_t max_len = first_chunk ? 115 : 220;
        size_t end = start;
        while (end < n && (end - start) < max_len) {
            int dev = decodeDevanagariAt(full, end);
            if (dev == 0x0964 || dev == 0x0965) { // । or ॥
                end += 3;
                break;
            }
            char c = full[end++];
            if ((c == '.' || c == '!' || c == '?' || c == ';') && (end >= n || std::isspace(static_cast<unsigned char>(full[end])))) {
                break;
            }
            if (first_chunk && (end - start) >= 36 && (c == ',' || c == ':') && (end >= n || std::isspace(static_cast<unsigned char>(full[end])))) {
                break;
            }
        }
        if (end < n && !std::isspace(static_cast<unsigned char>(full[end]))) {
            size_t sp = full.find(' ', end);
            end = (sp == std::string::npos) ? n : sp;
        }
        first_chunk = false;
        std::string chunk = full.substr(start, end - start);
        start = end;
        std::vector<int> ids, stress;
        phonemizeText(*model, chunk, &ids, &stress);
        if (ids.size() <= 2) continue;
        auto audio = synthesizeUtterance(*model, ids, stress, voice_id, speed);
        if (audio.empty()) continue;
        constexpr size_t kStep = 2048;
        for (size_t off = 0; off < audio.size(); off += kStep) {
            size_t cnt = std::min(kStep, audio.size() - off);
            if (!cb(audio.data() + off, cnt, user)) return true;
        }
    }
    (void)err_out;
    return true;
}

}  // namespace neb_voca
