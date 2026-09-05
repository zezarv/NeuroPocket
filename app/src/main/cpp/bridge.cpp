// NeuroPocket JNI bridge over llama.cpp (CPU, arm64-v8a).
// Stateless per generate(): full prompt in, text out. Single generation at a time.

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <android/log.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "NeuroPocket"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;
static const llama_vocab * g_vocab = nullptr;
static mtmd_context * g_mtmd = nullptr;
static int g_gpu_layers = 0;
static llama_model * g_emodel = nullptr;
static llama_context * g_ectx = nullptr;
static const llama_vocab * g_evocab = nullptr;
static std::mutex g_mu;
static std::mutex g_emu;
static std::atomic<bool> g_cancel{false};
static bool g_backend_init = false;

static std::string jstr_to_str(JNIEnv * env, jstring j) {
    if (!j) return std::string();
    const char * c = env->GetStringUTFChars(j, nullptr);
    std::string s(c ? c : "");
    if (c) env->ReleaseStringUTFChars(j, c);
    return s;
}

static void free_sampler() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
}

static llama_sampler * make_sampler(float temp, float top_p, int top_k, uint32_t seed) {
    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sp);
    if (top_k > 0) llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    if (top_p < 1.0f) llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    if (temp != 1.0f) llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed));
    return smpl;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_neuropocket_app_engine_LlamaNative_systemInfo(JNIEnv * env, jobject) {
    std::string s = "llama.cpp d230ddd android arm64-v8a cpu";
    return env->NewStringUTF(s.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_neuropocket_app_engine_LlamaNative_isLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_mu);
    return (g_model && g_ctx) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_neuropocket_app_engine_LlamaNative_supportsGpu(JNIEnv *, jobject) {
    if (!g_backend_init) { llama_backend_init(); g_backend_init = true; }
    return llama_supports_gpu_offload() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_neuropocket_app_engine_LlamaNative_cancel(JNIEnv *, jobject) {
    g_cancel.store(true);
}

// returns 0 ok, -1 bad path, -2 model load fail, -3 ctx fail
JNIEXPORT jint JNICALL
Java_com_neuropocket_app_engine_LlamaNative_loadModel(JNIEnv * env, jobject, jstring jpath, jint nCtx, jint nThreads, jint nGpu) {
    std::lock_guard<std::mutex> lk(g_mu);
    std::string path = jstr_to_str(env, jpath);
    if (path.empty()) return -1;

    // unload previous
    free_sampler();
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;

    if (!g_backend_init) { llama_backend_init(); g_backend_init = true; }

    int threads = (int) nThreads;
    if (threads < 1) threads = 4;
    if (threads > 8) threads = 8;
    int ctx_size = (int) nCtx;
    if (ctx_size < 512) ctx_size = 2048;
    if (ctx_size > 8192) ctx_size = 8192;
    int gpu = (int) nGpu;
    if (gpu < 0) gpu = 0;
    if (gpu > 999) gpu = 999;
    g_gpu_layers = gpu;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = gpu;
    LOGI("loading model %s (gpu_layers=%d, gpu_offload_supported=%d)", path.c_str(), gpu,
         (int) llama_supports_gpu_offload());
    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) { LOGE("model load failed"); return -2; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) ctx_size;
    cparams.n_batch = 512;
    cparams.n_ubatch = 128;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    cparams.offload_kqv = false;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) { llama_model_free(g_model); g_model = nullptr; LOGE("ctx init failed"); return -3; }

    g_vocab = llama_model_get_vocab(g_model);
    g_cancel.store(false);
    LOGI("model loaded ok, n_ctx=%d threads=%d", ctx_size, threads);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_neuropocket_app_engine_LlamaNative_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_mu);
    free_sampler();
    if (g_mtmd) { mtmd_free(g_mtmd); g_mtmd = nullptr; }
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
}

// ---------- Vision (mtmd) ----------

JNIEXPORT jboolean JNICALL
Java_com_neuropocket_app_engine_LlamaNative_isVisionLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_mu);
    return g_mtmd ? JNI_TRUE : JNI_FALSE;
}

// 0 ok, -1 empty, -2 no text model (load vision GGUF via loadModel first), -3 init fail, -4 no vision support
JNIEXPORT jint JNICALL
Java_com_neuropocket_app_engine_LlamaNative_loadVision(JNIEnv * env, jobject, jstring jmmproj, jint nThreads) {
    std::lock_guard<std::mutex> lk(g_mu);
    std::string path = jstr_to_str(env, jmmproj);
    if (path.empty()) return -1;
    if (!g_model) return -2;
    if (g_mtmd) { mtmd_free(g_mtmd); g_mtmd = nullptr; }

    int threads = (int) nThreads;
    if (threads < 1) threads = 4;
    if (threads > 8) threads = 8;

    mtmd_context_params mp = mtmd_context_params_default();
    mp.use_gpu = g_gpu_layers > 0;
    mp.n_threads = threads;
    mp.print_timings = false;
    mp.warmup = false;
    g_mtmd = mtmd_init_from_file(path.c_str(), g_model, mp);
    if (!g_mtmd) { LOGE("mtmd init failed"); return -3; }
    if (!mtmd_support_vision(g_mtmd)) { mtmd_free(g_mtmd); g_mtmd = nullptr; return -4; }
    LOGI("mtmd vision loaded ok");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_neuropocket_app_engine_LlamaNative_unloadVision(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (g_mtmd) { mtmd_free(g_mtmd); g_mtmd = nullptr; }
}

// ---------- Embeddings (RAG) ----------

JNIEXPORT jboolean JNICALL
Java_com_neuropocket_app_engine_LlamaNative_isEmbedLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_emu);
    return (g_emodel && g_ectx) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_neuropocket_app_engine_LlamaNative_embedDim(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_emu);
    if (!g_emodel) return -1;
    return llama_model_n_embd(g_emodel);
}

// 0 ok, -1 empty, -2 load fail, -3 ctx fail
JNIEXPORT jint JNICALL
Java_com_neuropocket_app_engine_LlamaNative_loadEmbed(JNIEnv * env, jobject, jstring jpath, jint nThreads) {
    std::lock_guard<std::mutex> lk(g_emu);
    std::string path = jstr_to_str(env, jpath);
    if (path.empty()) return -1;
    if (g_ectx) { llama_free(g_ectx); g_ectx = nullptr; }
    if (g_emodel) { llama_model_free(g_emodel); g_emodel = nullptr; }
    g_evocab = nullptr;
    if (!g_backend_init) { llama_backend_init(); g_backend_init = true; }

    int threads = (int) nThreads;
    if (threads < 1) threads = 4;
    if (threads > 8) threads = 8;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // embed-модель всегда на CPU: маленькая и быстрая
    g_emodel = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_emodel) return -2;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 512;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    cparams.embeddings = true;
    cparams.pooling_type = LLAMA_POOLING_TYPE_MEAN;
    g_ectx = llama_init_from_model(g_emodel, cparams);
    if (!g_ectx) { llama_model_free(g_emodel); g_emodel = nullptr; return -3; }
    g_evocab = llama_model_get_vocab(g_emodel);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_neuropocket_app_engine_LlamaNative_unloadEmbed(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_emu);
    if (g_ectx) { llama_free(g_ectx); g_ectx = nullptr; }
    if (g_emodel) { llama_model_free(g_emodel); g_emodel = nullptr; }
    g_evocab = nullptr;
}

// Возвращает float[] длиной n*dim или null. Каждый текст усекается до 400 токенов.
JNIEXPORT jfloatArray JNICALL
Java_com_neuropocket_app_engine_LlamaNative_embedBatch(JNIEnv * env, jobject, jobjectArray jtexts) {
    std::lock_guard<std::mutex> lk(g_emu);
    if (!g_emodel || !g_ectx || !g_evocab) return nullptr;
    jsize n = env->GetArrayLength(jtexts);
    if (n <= 0 || n > 64) return nullptr;
    int dim = llama_model_n_embd(g_emodel);
    if (dim <= 0) return nullptr;

    std::vector<float> out((size_t) n * dim, 0.0f);
    std::vector<llama_token> toks(512);
    for (jsize i = 0; i < n; i++) {
        jstring js = (jstring) env->GetObjectArrayElement(jtexts, i);
        std::string text = jstr_to_str(env, js);
        env->DeleteLocalRef(js);
        int32_t nt = llama_tokenize(g_evocab, text.c_str(), (int32_t) text.size(),
                                    toks.data(), 400, true, false);
        if (nt <= 0) continue;
        if (nt > 400) nt = 400;
        llama_memory_t mem = llama_get_memory(g_ectx);
        if (mem) llama_memory_clear(mem, true);
        llama_batch batch = llama_batch_get_one(toks.data(), nt);
        if (llama_decode(g_ectx, batch) != 0) continue;
        float * e = llama_get_embeddings_seq(g_ectx, 0);
        if (!e) continue;
        memcpy(out.data() + (size_t) i * dim, e, (size_t) dim * sizeof(float));
    }
    jfloatArray res = env->NewFloatArray((jsize) out.size());
    if (!res) return nullptr;
    env->SetFloatArrayRegion(res, 0, (jsize) out.size(), out.data());
    return res;
}

JNIEXPORT jstring JNICALL
Java_com_neuropocket_app_engine_LlamaNative_describeImage(
        JNIEnv * env, jobject, jbyteArray jimg, jstring jprompt, jint maxTokens, jfloat temperature) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_model || !g_ctx || !g_vocab || !g_mtmd) {
        return env->NewStringUTF("__ERR:NO_VISION");
    }
    std::string prompt = jstr_to_str(env, jprompt);
    if (prompt.empty()) prompt = "Опиши подробно, что на этом изображении.";
    int n_predict = (int) maxTokens;
    if (n_predict < 8) n_predict = 128;
    if (n_predict > 512) n_predict = 512;
    g_cancel.store(false);

    jsize img_len = env->GetArrayLength(jimg);
    jbyte * img_ptr = env->GetByteArrayElements(jimg, nullptr);
    if (!img_ptr || img_len <= 0) return env->NewStringUTF("__ERR:IMG");

    mtmd_helper_init_opt opt = mtmd_helper_init_opt_default();
    mtmd_helper_bitmap_wrapper wrap =
        mtmd_helper_bitmap_init_from_buf(g_mtmd, reinterpret_cast<unsigned char *>(img_ptr), (size_t) img_len, true, opt);
    env->ReleaseByteArrayElements(jimg, img_ptr, JNI_ABORT);
    if (!wrap.bitmap) return env->NewStringUTF("__ERR:DECODE_IMG");

    std::string full_text = std::string(mtmd_get_marker(g_mtmd)) + "\n" + prompt;
    mtmd_input_text txt{full_text.c_str(), full_text.size(), true, false};
    const mtmd_bitmap * bitmaps[1] = {wrap.bitmap};
    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    int32_t tok_rc = mtmd_tokenize(g_mtmd, chunks, &txt, bitmaps, 1);
    mtmd_bitmap_free(wrap.bitmap);
    if (tok_rc != 0) { mtmd_input_chunks_free(chunks); return env->NewStringUTF("__ERR:TOKENIZE"); }

    llama_memory_t mem = llama_get_memory(g_ctx);
    if (mem) llama_memory_clear(mem, true);

    uint32_t n_batch = llama_n_batch(g_ctx);
    if (n_batch < 64) n_batch = 64;
    llama_pos new_past = 0;
    if (mtmd_helper_eval_chunks(g_mtmd, g_ctx, chunks, 0, 0, (int32_t) n_batch, true, &new_past) != 0) {
        mtmd_input_chunks_free(chunks);
        return env->NewStringUTF("__ERR:EVAL");
    }
    mtmd_input_chunks_free(chunks);

    free_sampler();
    g_sampler = make_sampler((float) temperature, 0.9f, 40, (uint32_t) llama_time_us());

    std::string out;
    out.reserve(1024);
    char piece[256];
    for (int i = 0; i < n_predict; i++) {
        if (g_cancel.load()) break;
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, tok);
        if (llama_vocab_is_eog(g_vocab, tok)) break;
        int n = llama_token_to_piece(g_vocab, tok, piece, sizeof(piece), 0, true);
        if (n > 0) out.append(piece, n);
        if ((int) out.size() > 4000) break;
        llama_batch batch = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }
    if (out.empty()) out = "...";
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_neuropocket_app_engine_LlamaNative_generate(
        JNIEnv * env, jobject,
        jstring jprompt, jint maxTokens, jfloat temperature, jfloat topP, jint topK, jint seed) {
    std::string prompt = jstr_to_str(env, jprompt);
    int n_predict = (int) maxTokens;
    if (n_predict < 8) n_predict = 128;
    if (n_predict > 1024) n_predict = 1024;
    float temp = (float) temperature;
    float topp = (float) topP;
    int topk = (int) topK;
    if (topp <= 0 || topp > 1) topp = 0.9f;

    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_model || !g_ctx || !g_vocab) {
        return env->NewStringUTF("__ERR:NO_MODEL");
    }
    g_cancel.store(false);

    free_sampler();
    g_sampler = make_sampler(temp, topp, topk, (uint32_t) seed);

    llama_memory_t mem = llama_get_memory(g_ctx);
    if (mem) llama_memory_clear(mem, true);

    // tokenize
    const int n_prompt_max = 4096;
    std::vector<llama_token> prompt_tokens(n_prompt_max);
    int32_t n_prompt = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(),
                                      prompt_tokens.data(), n_prompt_max, true, false);
    if (n_prompt < 0) {
        // buffer too small: retry bigger
        prompt_tokens.resize(-n_prompt + 8);
        n_prompt = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(),
                                  prompt_tokens.data(), (int32_t) prompt_tokens.size(), true, false);
    }
    if (n_prompt <= 0) return env->NewStringUTF("__ERR:TOKENIZE");

    uint32_t n_ctx = llama_n_ctx(g_ctx);
    int max_keep = (int) n_ctx - n_predict - 32;
    if (max_keep < 256) max_keep = 256;
    if (n_prompt > max_keep) {
        // keep tail (recent turns matter most)
        std::vector<llama_token> tail(prompt_tokens.begin() + (n_prompt - max_keep), prompt_tokens.begin() + n_prompt);
        prompt_tokens.swap(tail);
        n_prompt = (int32_t) prompt_tokens.size();
    } else {
        prompt_tokens.resize(n_prompt);
    }

    // decode prompt in chunks
    uint32_t n_batch = llama_n_batch(g_ctx);
    if (n_batch < 64) n_batch = 64;
    for (int32_t i = 0; i < n_prompt; i += (int32_t) n_batch) {
        int32_t n = std::min<int32_t>((int32_t) n_batch, n_prompt - i);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i, n);
        int rc = llama_decode(g_ctx, batch);
        if (rc != 0) return env->NewStringUTF("__ERR:DECODE_PROMPT");
        if (g_cancel.load()) return env->NewStringUTF("");
    }

    std::string out;
    out.reserve(2048);
    char piece[256];

    for (int i = 0; i < n_predict; i++) {
        if (g_cancel.load()) break;
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, tok);
        if (llama_vocab_is_eog(g_vocab, tok)) break;
        int n = llama_token_to_piece(g_vocab, tok, piece, sizeof(piece), 0, true);
        if (n > 0) out.append(piece, n);
        if ((int) out.size() > 6000) break;
        llama_batch batch = llama_batch_get_one(&tok, 1);
        int rc = llama_decode(g_ctx, batch);
        if (rc != 0) break;
    }

    if (out.empty()) out = "...";
    return env->NewStringUTF(out.c_str());
}

// Streaming variant: same core, but each decoded piece is pushed to sink.emit(String).
// sink is com.neuropocket.app.engine.TokenSink
JNIEXPORT jstring JNICALL
Java_com_neuropocket_app_engine_LlamaNative_generateStream(        JNIEnv * env, jobject,
        jstring jprompt, jint maxTokens, jfloat temperature, jfloat topP, jint topK, jint seed, jobject sink) {
    std::string prompt = jstr_to_str(env, jprompt);
    int n_predict = (int) maxTokens;
    if (n_predict < 8) n_predict = 128;
    if (n_predict > 1024) n_predict = 1024;
    float temp = (float) temperature;
    float topp = (float) topP;
    int topk = (int) topK;
    if (topp <= 0 || topp > 1) topp = 0.9f;

    jmethodID mid_emit = nullptr;
    if (sink) {
        jclass cls = env->GetObjectClass(sink);
        if (cls) mid_emit = env->GetMethodID(cls, "emit", "(Ljava/lang/String;)V");
    }

    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_model || !g_ctx || !g_vocab) {
        return env->NewStringUTF("__ERR:NO_MODEL");
    }
    g_cancel.store(false);

    free_sampler();
    g_sampler = make_sampler(temp, topp, topk, (uint32_t) seed);

    llama_memory_t mem = llama_get_memory(g_ctx);
    if (mem) llama_memory_clear(mem, true);

    const int n_prompt_max = 4096;
    std::vector<llama_token> prompt_tokens(n_prompt_max);
    int32_t n_prompt = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(),
                                      prompt_tokens.data(), n_prompt_max, true, false);
    if (n_prompt < 0) {
        prompt_tokens.resize(-n_prompt + 8);
        n_prompt = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(),
                                  prompt_tokens.data(), (int32_t) prompt_tokens.size(), true, false);
    }
    if (n_prompt <= 0) return env->NewStringUTF("__ERR:TOKENIZE");

    uint32_t n_ctx = llama_n_ctx(g_ctx);
    int max_keep = (int) n_ctx - n_predict - 32;
    if (max_keep < 256) max_keep = 256;
    if (n_prompt > max_keep) {
        std::vector<llama_token> tail(prompt_tokens.begin() + (n_prompt - max_keep), prompt_tokens.begin() + n_prompt);
        prompt_tokens.swap(tail);
        n_prompt = (int32_t) prompt_tokens.size();
    } else {
        prompt_tokens.resize(n_prompt);
    }

    uint32_t n_batch = llama_n_batch(g_ctx);
    if (n_batch < 64) n_batch = 64;
    for (int32_t i = 0; i < n_prompt; i += (int32_t) n_batch) {
        int32_t n = std::min<int32_t>((int32_t) n_batch, n_prompt - i);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i, n);
        int rc = llama_decode(g_ctx, batch);
        if (rc != 0) return env->NewStringUTF("__ERR:DECODE_PROMPT");
        if (g_cancel.load()) return env->NewStringUTF("");
    }

    std::string out;
    out.reserve(2048);
    char piece[256];

    for (int i = 0; i < n_predict; i++) {
        if (g_cancel.load()) break;
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, tok);
        if (llama_vocab_is_eog(g_vocab, tok)) break;
        int n = llama_token_to_piece(g_vocab, tok, piece, sizeof(piece), 0, true);
        if (n > 0) {
            out.append(piece, n);
            if (sink && mid_emit) {
                jstring js = env->NewStringUTF(std::string(piece, n).c_str());
                if (js) {
                    env->CallVoidMethod(sink, mid_emit, js);
                    env->DeleteLocalRef(js);
                    if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
                }
            }
        }
        if ((int) out.size() > 6000) break;
        llama_batch batch = llama_batch_get_one(&tok, 1);
        int rc = llama_decode(g_ctx, batch);
        if (rc != 0) break;
    }

    if (out.empty()) out = "...";
    return env->NewStringUTF(out.c_str());
}

// Замер скорости: разбор короткого промпта + генерация 32 токенов.
// Возвращает "pp_tok pp_ms gen_tok gen_ms" или "__ERR:...".
JNIEXPORT jstring JNICALL
Java_com_neuropocket_app_engine_LlamaNative_runBench(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_model || !g_ctx || !g_vocab) {
        return env->NewStringUTF("__ERR:NO_MODEL");
    }
    g_cancel.store(false);

    free_sampler();
    g_sampler = make_sampler(0.8f, 0.9f, 40, 42u);

    llama_memory_t mem = llama_get_memory(g_ctx);
    if (mem) llama_memory_clear(mem, true);

    const char * prompt = "Тест скорости локальной модели. Ответь одним коротким предложением.";
    std::vector<llama_token> toks(512);
    int32_t n_prompt = llama_tokenize(g_vocab, prompt, (int32_t) strlen(prompt),
                                      toks.data(), (int32_t) toks.size(), true, false);
    if (n_prompt <= 0) return env->NewStringUTF("__ERR:TOKENIZE");
    toks.resize(n_prompt);

    int64_t t0 = llama_time_us();
    llama_batch batch = llama_batch_get_one(toks.data(), n_prompt);
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("__ERR:DECODE");
    int64_t t1 = llama_time_us();

    char piece[256];
    int n_gen = 0;
    for (int i = 0; i < 32; i++) {
        if (g_cancel.load()) break;
        llama_token tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, tok);
        if (llama_vocab_is_eog(g_vocab, tok)) break;
        llama_token_to_piece(g_vocab, tok, piece, sizeof(piece), 0, true);
        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, b) != 0) break;
        n_gen++;
    }
    int64_t t2 = llama_time_us();

    char out[128];
    snprintf(out, sizeof(out), "%d %lld %d %lld",
             (int) n_prompt, (long long) (t1 - t0) / 1000,
             n_gen, (long long) (t2 - t1) / 1000);
    return env->NewStringUTF(out);
}

} // extern "C"
