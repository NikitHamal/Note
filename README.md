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
  - 4x2 register-blocked ARM NEON GEMM (`linearOp`) reusing weight rows across 4 time-steps in 16 SIMD accumulators.
  - Vectorized ARM NEON Abramowitz & Stegun `erf` GELU, vectorized LayerNorm, and 4-wide complex butterfly Radix-2 FFT.
  - Hardware ARM64/ARMv7 Flush-To-Zero (`FTZ`) denormal elimination + zero-copy `GetPrimitiveArrayCritical` NEON audio conditioning & auto-gain normalization.
  - Single-pass `needle_transcribe` fast-path for recorded audio windows + rolling 8.5s VAD pause auto-commit for lag-free continuous dictation on low-end devices.
