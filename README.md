# Note — Local AI Dictation, Nepali Shruti ASR, Transcription & Neural Voice Notes

Created & developed by **Nikit Hamal** ([nikit.is-a.dev](https://nikit.is-a.dev/)).

## Highlights

- **Material 3 Expressive (`m3x`) UI**: Zero-shadow tonal surfaces, connected M3X controls, finite rounded corners, spring physics, and custom procedural cover art.
- **On-Device Local AI Models (Download or Remove On Demand)**:
  - **Shruti Nepali ASR** (`29.7 MB`): Offline Nepali & English speech recognition on the Needle C++ engine.
  - **Whistle ASR** (`17.7 MB`): Offline multilingual speech recognition (English, German, French, Spanish, Italian, Dutch, Polish).
  - **TinyVoca Neural TTS** (`8.0 MB`): Offline two-voice (`F2`/`M2`) English & Devanagari Nepali neural text-to-speech.
  - **Phonon 31M TTS** (`65.1 MB`): Studio-grade offline neural voice reader with on-demand voice catalog.
- **Custom Native C++17 & ARM NEON Kernels (`app/src/main/cpp/`)**:
  - **4x4 Dual-Core Register-Blocked ARM64 NEON GEMM (`linearOp`), Persistent FTZ Worker Pool & NEON `Q8_0`/`Q4_0` Dequantizer**: Reuses weight rows across 4 time-steps x 4 output channels in 16 SIMD accumulators on ARM64, parallelizes matrix projections across 2 CPU cores via a persistent FTZ-enabled worker thread (`PersistentWorker`), Vectorizes GGUF `Q8_0`/`Q4_0` weight dequantization, and pre-transposes depthwise weights to `[taps, ch]` for contiguous 16-wide NEON execution.
  - **Custom 4-Wide ARM NEON `vexpq_f32_fast` & `vsincosq_f32_fast`, Precomputed RoPE & `O(T)` L1 Dual-Core Attention**: Replaces millions of scalar `std::exp`, `std::sin`, and `std::cos` calls with 4-wide Cephes minimax NEON kernels, precomputes static `kMaxFrames x 48` RoPE tables, replaces `O(T^2)` attention matrices with L1-resident `O(T)` rows, and runs attention heads + cepstral vocoder branches in parallel across `PersistentWorker`.
  - **Vectorized NEON GELU, LayerNorm, Radix-2 FFT, 8-Wide Overlap-Add & Nepali Devanagari Phonemizer**: 100% in-register Abramowitz & Stegun `erf` + `vexpq_f32_fast` GELU, 4-wide complex butterfly FFT stages, 8-wide NEON overlap-add, spoken Nepali Devanagari number expansion (`०`..`९`), diphthong coalescing (`-ाइ`/`-ाउ`), and conjunct rules (`ज्ञ`, `क्ष`, `त्र`, `श्र`, `द्य`).
  - **Hardware ARM64/ARMv7 Flush-To-Zero (`FTZ`), 16-Wide NEON Silence Trimmer & Pre-Warmed Models**: Eliminates subnormal float microcode traps on budget Cortex-A53/A55 cores, normalizes quiet microphones via zero-copy `GetPrimitiveArrayCritical`, pre-warms installed STT/TTS models on the background engine thread, and routes voice recordings through deterministic `@batch` single-pass `needle_transcribe`.
