#ifndef NEB_TINY_VOCA_H
#define NEB_TINY_VOCA_H

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace neb_voca {

struct VocaModel;

typedef bool (*VocaPcmCallback)(const float* pcm, size_t count, void* user);

VocaModel* voca_load(const char* gguf_path, std::string* err_out);
void voca_free(VocaModel* model);

// Synthesizes [text] at 24 kHz mono float PCM using voice_id (0 = F2, 1 = M2) and streams
// each sentence's audio to [cb]. Returns false if cancelled or on error.
bool voca_speak(
    VocaModel* model,
    const char* text,
    int voice_id,
    float speed,
    VocaPcmCallback cb,
    void* user,
    std::string* err_out
);

}  // namespace neb_voca

#endif  // NEB_TINY_VOCA_H
