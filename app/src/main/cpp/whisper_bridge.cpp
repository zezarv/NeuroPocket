// NeuroPocket whisper bridge (own shared lib libnpwhisper.so).
// WAV 16kHz mono required in v1 (int16 or float32 PCM). Stereo is averaged.

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <algorithm>

#include "whisper.h"

static whisper_context * g_wctx = nullptr;
static std::mutex g_wmu;

static std::string jstr(JNIEnv * env, jstring j) {
    if (!j) return std::string();
    const char * c = env->GetStringUTFChars(j, nullptr);
    std::string s(c ? c : "");
    if (c) env->ReleaseStringUTFChars(j, c);
    return s;
}

// minimal WAV reader: PCM16 or FLOAT32, any channels (averaged), any rate (returned, caller validates)
static bool read_wav(const std::string & path, std::vector<float> & out_pcm, int & out_rate, std::string & err) {
    FILE * f = fopen(path.c_str(), "rb");
    if (!f) { err = "no open wav"; return false; }
    auto rd32 = [&]() -> uint32_t { uint32_t v = 0; fread(&v, 1, 4, f); return v; };
    auto rd16 = [&]() -> uint16_t { uint16_t v = 0; fread(&v, 1, 2, f); return v; };
    char riff[4], wave[4];
    if (fread(riff, 1, 4, f) != 4 || fread(wave, 1, 4, f) != 4) { fclose(f); err = "bad riff"; return false; }
    // skip RIFF size
    fseek(f, 4, SEEK_CUR);
    if (memcmp(riff, "RIFF", 4) || memcmp(wave, "WAVE", 4)) { fclose(f); err = "not wav"; return false; }
    int audio_fmt = 0, channels = 0, rate = 0;
    std::vector<uint8_t> data;
    while (!feof(f)) {
        char id[4]; uint32_t sz = 0;
        if (fread(id, 1, 4, f) != 4) break;
        if (fread(&sz, 1, 4, f) != 4) break;
        long next = ftell(f) + (long) sz + (sz & 1);
        if (!memcmp(id, "fmt ", 4)) {
            audio_fmt = rd16(); channels = rd16(); rate = (int) rd32();
            fseek(f, 4, SEEK_CUR); // byte rate
            fseek(f, 2, SEEK_CUR); // block align
            uint16_t bps = rd16();
            if (audio_fmt == 1 && bps != 16) { /* keep, validate later */ }
            if (audio_fmt != 1 && audio_fmt != 3) { fclose(f); err = "only pcm16/float32"; return false; }
            if (bps != 16 && audio_fmt == 1) { fclose(f); err = "need 16-bit"; return false; }
            if (bps != 32 && audio_fmt == 3) { fclose(f); err = "need 32-bit float"; return false; }
        } else if (!memcmp(id, "data", 4)) {
            data.resize(sz);
            if (fread(data.data(), 1, sz, f) != sz) { fclose(f); err = "short data"; return false; }
            break; // first data chunk is enough
        }
        fseek(f, next, SEEK_SET);
    }
    fclose(f);
    if (data.empty() || channels <= 0 || rate <= 0) { err = "no audio"; return false; }
    out_rate = rate;
    size_t frames = 0;
    if (audio_fmt == 1) {
        size_t samples = data.size() / 2;
        frames = samples / (size_t) channels;
        out_pcm.resize(frames);
        const int16_t * p = reinterpret_cast<const int16_t *>(data.data());
        for (size_t i = 0; i < frames; i++) {
            double acc = 0;
            for (int c = 0; c < channels; c++) acc += p[i * channels + c] / 32768.0;
            out_pcm[i] = (float)(acc / channels);
        }
    } else {
        size_t samples = data.size() / 4;
        frames = samples / (size_t) channels;
        out_pcm.resize(frames);
        const float * p = reinterpret_cast<const float *>(data.data());
        for (size_t i = 0; i < frames; i++) {
            double acc = 0;
            for (int c = 0; c < channels; c++) acc += p[i * channels + c];
            out_pcm[i] = (float)(acc / channels);
        }
    }
    return true;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_neuropocket_app_engine_WhisperNative_isLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_wmu);
    return g_wctx ? JNI_TRUE : JNI_FALSE;
}

// 0 ok, -1 path, -2 init fail
JNIEXPORT jint JNICALL
Java_com_neuropocket_app_engine_WhisperNative_loadModel(JNIEnv * env, jobject, jstring jpath) {
    std::lock_guard<std::mutex> lk(g_wmu);
    std::string path = jstr(env, jpath);
    if (path.empty()) return -1;
    if (g_wctx) { whisper_free(g_wctx); g_wctx = nullptr; }
    struct whisper_context_params cparams = whisper_context_default_params();
    g_wctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    return g_wctx ? 0 : -2;
}

JNIEXPORT void JNICALL
Java_com_neuropocket_app_engine_WhisperNative_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_wmu);
    if (g_wctx) { whisper_free(g_wctx); g_wctx = nullptr; }
}

JNIEXPORT jstring JNICALL
Java_com_neuropocket_app_engine_WhisperNative_transcribe(JNIEnv * env, jobject, jstring jwav, jstring jlang, jint nThreads) {
    std::string wav = jstr(env, jwav);
    std::string lang = jstr(env, jlang);
    if (lang.empty()) lang = "ru";

    std::lock_guard<std::mutex> lk(g_wmu);
    if (!g_wctx) return env->NewStringUTF("__ERR:NO_MODEL");

    std::vector<float> pcm; int rate = 0; std::string err;
    if (!read_wav(wav, pcm, rate, err)) {
        std::string e = "__ERR:WAV:" + err;
        return env->NewStringUTF(e.c_str());
    }
    if (rate != 16000) {
        return env->NewStringUTF("__ERR:RATE16K");
    }
    if (pcm.size() > (size_t)(16000 * 60 * 10)) {
        return env->NewStringUTF("__ERR:TOOLONG10MIN");
    }

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.language = "ru";
    // keep language string alive: whisper copies? it stores pointer; use static
    static std::string lang_hold;
    lang_hold = lang;
    wparams.language = lang_hold.c_str();
    int threads = (int) nThreads;
    if (threads < 1) threads = 4;
    if (threads > 8) threads = 8;
    wparams.n_threads = threads;

    if (whisper_full(g_wctx, wparams, pcm.data(), (int) pcm.size()) != 0) {
        return env->NewStringUTF("__ERR:FULL");
    }
    int n = whisper_full_n_segments(g_wctx);
    std::string out;
    for (int i = 0; i < n; i++) {
        const char * t = whisper_full_get_segment_text(g_wctx, i);
        if (t) { out += t; out += "\n"; }
    }
    if (out.empty()) out = "(пусто)";
    return env->NewStringUTF(out.c_str());
}

// Тот же прогон, но строки "t0_ms|t1_ms|текст".
JNIEXPORT jstring JNICALL
Java_com_neuropocket_app_engine_WhisperNative_transcribeDetailed(JNIEnv * env, jobject, jstring jwav, jstring jlang, jint nThreads) {
    std::string wav = jstr(env, jwav);
    std::string lang = jstr(env, jlang);
    if (lang.empty()) lang = "ru";

    std::lock_guard<std::mutex> lk(g_wmu);
    if (!g_wctx) return env->NewStringUTF("__ERR:NO_MODEL");

    std::vector<float> pcm; int rate = 0; std::string err;
    if (!read_wav(wav, pcm, rate, err)) {
        std::string e = "__ERR:WAV:" + err;
        return env->NewStringUTF(e.c_str());
    }
    if (rate != 16000) {
        return env->NewStringUTF("__ERR:RATE16K");
    }
    if (pcm.size() > (size_t)(16000 * 60 * 10)) {
        return env->NewStringUTF("__ERR:TOOLONG10MIN");
    }

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    static std::string lang_hold2;
    lang_hold2 = lang;
    wparams.language = lang_hold2.c_str();
    int threads = (int) nThreads;
    if (threads < 1) threads = 4;
    if (threads > 8) threads = 8;
    wparams.n_threads = threads;

    if (whisper_full(g_wctx, wparams, pcm.data(), (int) pcm.size()) != 0) {
        return env->NewStringUTF("__ERR:FULL");
    }
    int n = whisper_full_n_segments(g_wctx);
    std::string out;
    char line_head[64];
    for (int i = 0; i < n; i++) {
        const char * txt = whisper_full_get_segment_text(g_wctx, i);
        if (!txt || !*txt) continue;
        int64_t t0 = whisper_full_get_segment_t0(g_wctx, i) * 10;
        int64_t t1 = whisper_full_get_segment_t1(g_wctx, i) * 10;
        snprintf(line_head, sizeof(line_head), "%lld|%lld|", (long long) t0, (long long) t1);
        out += line_head;
        out += txt;
        out += "\n";
    }
    if (out.empty()) out = "0|0|(пусто)\n";
    return env->NewStringUTF(out.c_str());
}

} // extern "C"
