// NeuroPocket SD bridge (own shared lib libnpsd.so).
// txt2img on CPU via stable-diffusion.cpp. Returns raw RGB bytes to Kotlin,
// PNG encoding happens on the Kotlin side (Bitmap.compress).

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>

#include "stable-diffusion.h"

static sd_ctx_t * g_sd = nullptr;
static std::mutex g_sd_mu;

static std::string jstr(JNIEnv * env, jstring j) {
    if (!j) return std::string();
    const char * c = env->GetStringUTFChars(j, nullptr);
    std::string s(c ? c : "");
    if (c) env->ReleaseStringUTFChars(j, c);
    return s;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_neuropocket_app_engine_SdNative_isLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_sd_mu);
    return g_sd ? JNI_TRUE : JNI_FALSE;
}

// 0 ok, -1 bad path, -2 ctx fail. vae/taesd may be empty.
JNIEXPORT jint JNICALL
Java_com_neuropocket_app_engine_SdNative_loadModel(
        JNIEnv * env, jobject, jstring jmodel, jstring jvae, jstring jtaesd, jint nThreads) {
    std::lock_guard<std::mutex> lk(g_sd_mu);
    std::string model = jstr(env, jmodel);
    std::string vae = jstr(env, jvae);
    std::string taesd = jstr(env, jtaesd);
    if (model.empty()) return -1;
    if (g_sd) { free_sd_ctx(g_sd); g_sd = nullptr; }

    // keep c-strings alive during new_sd_ctx (it copies paths internally, but be safe: locals outlive call)
    sd_ctx_params_t p;
    sd_ctx_params_init(&p);
    p.model_path = model.c_str();
    std::string vaeHold = vae, taesdHold = taesd;
    if (!vaeHold.empty()) p.vae_path = vaeHold.c_str();
    if (!taesdHold.empty()) p.taesd_path = taesdHold.c_str();
    int threads = (int) nThreads;
    if (threads < 1) threads = 4;
    if (threads > 8) threads = 8;
    p.n_threads = threads;
    p.flash_attn = false;

    g_sd = new_sd_ctx(&p);
    return g_sd ? 0 : -2;
}

JNIEXPORT void JNICALL
Java_com_neuropocket_app_engine_SdNative_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_sd_mu);
    if (g_sd) { free_sd_ctx(g_sd); g_sd = nullptr; }
}

JNIEXPORT void JNICALL
Java_com_neuropocket_app_engine_SdNative_cancel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_sd_mu);
    if (g_sd) sd_cancel_generation(g_sd, SD_CANCEL_ALL);
}

// Returns RGB byte array (w*h*3) or null on failure.
JNIEXPORT jbyteArray JNICALL
Java_com_neuropocket_app_engine_SdNative_render(
        JNIEnv * env, jobject,
        jstring jprompt, jstring jneg,
        jint w, jint h, jint steps, jfloat cfg, jlong seed, jstring jsampler) {
    std::string prompt = jstr(env, jprompt);
    std::string neg = jstr(env, jneg);
    std::string sampler = jstr(env, jsampler);
    int width = (int) w, height = (int) h;
    if (width < 256) width = 512;
    if (width > 768) width = 768;
    if (height < 256) height = 512;
    if (height > 768) height = 768;
    int nsteps = (int) steps;
    if (nsteps < 1) nsteps = 4;
    if (nsteps > 30) nsteps = 30;

    std::lock_guard<std::mutex> lk(g_sd_mu);
    if (!g_sd) return nullptr;

    sd_img_gen_params_t gp;
    sd_img_gen_params_init(&gp);
    gp.prompt = prompt.c_str();
    std::string negHold = neg;
    gp.negative_prompt = negHold.c_str();
    gp.width = width;
    gp.height = height;
    gp.sample_params.sample_steps = nsteps;
    gp.sample_params.guidance.txt_cfg = (float) cfg;
    if (!sampler.empty()) {
        gp.sample_params.sample_method = str_to_sample_method(sampler.c_str());
        gp.sample_params.scheduler = sd_get_default_scheduler(g_sd, gp.sample_params.sample_method);
    }
    gp.seed = (int64_t) seed;
    gp.batch_count = 1;

    sd_image_t * imgs = nullptr;
    int n_imgs = 0;
    if (!generate_image(g_sd, &gp, &imgs, &n_imgs) || !imgs || n_imgs < 1) {
        return nullptr;
    }
    jbyteArray out = nullptr;
    if (imgs[0].data && imgs[0].channel >= 3) {
        size_t n = (size_t) imgs[0].width * imgs[0].height * 3;
        out = env->NewByteArray((jsize) n);
        if (out) {
            if (imgs[0].channel == 3) {
                env->SetByteArrayRegion(out, 0, (jsize) n, reinterpret_cast<jbyte *>(imgs[0].data));
            } else {
                // RGBA -> RGB
                std::vector<uint8_t> rgb(n);
                for (size_t i = 0; i < (size_t) imgs[0].width * imgs[0].height; i++) {
                    rgb[i * 3] = imgs[0].data[i * 4];
                    rgb[i * 3 + 1] = imgs[0].data[i * 4 + 1];
                    rgb[i * 3 + 2] = imgs[0].data[i * 4 + 2];
                }
                env->SetByteArrayRegion(out, 0, (jsize) n, reinterpret_cast<jbyte *>(rgb.data()));
            }
        }
    }
    free_sd_images(imgs, n_imgs);
    return out;
}


// img2img: init-картинка ARGB int[] + сила переделки. Возвращает RGB или null.
JNIEXPORT jbyteArray JNICALL
Java_com_neuropocket_app_engine_SdNative_renderImg(
        JNIEnv * env, jobject,
        jstring jprompt, jstring jneg,
        jintArray jpx, jint iw, jint ih,
        jint w, jint h, jint steps, jfloat cfg, jlong seed, jstring jsampler, jfloat strength) {
    std::string prompt = jstr(env, jprompt);
    std::string neg = jstr(env, jneg);
    std::string sampler = jstr(env, jsampler);
    int width = (int) w, height = (int) h;
    if (width < 256) width = 512;
    if (width > 768) width = 768;
    if (height < 256) height = 512;
    if (height > 768) height = 768;
    int nsteps = (int) steps;
    if (nsteps < 1) nsteps = 4;
    if (nsteps > 30) nsteps = 30;
    float str = (float) strength;
    if (str < 0.1f) str = 0.1f;
    if (str > 1.0f) str = 1.0f;

    std::lock_guard<std::mutex> lk(g_sd_mu);
    if (!g_sd) return nullptr;

    jsize npix = env->GetArrayLength(jpx);
    jint * px = env->GetIntArrayElements(jpx, nullptr);
    if (!px || npix < (int) iw * (int) ih || iw <= 0 || ih <= 0) {
        if (px) env->ReleaseIntArrayElements(jpx, px, JNI_ABORT);
        return nullptr;
    }
    std::vector<uint8_t> rgb_in((size_t) iw * ih * 3);
    for (int i = 0; i < (int) iw * ih; i++) {
        uint32_t p32 = (uint32_t) px[i];
        rgb_in[i * 3] = (p32 >> 16) & 0xFF;
        rgb_in[i * 3 + 1] = (p32 >> 8) & 0xFF;
        rgb_in[i * 3 + 2] = p32 & 0xFF;
    }
    env->ReleaseIntArrayElements(jpx, px, JNI_ABORT);

    sd_image_t init{(uint32_t) iw, (uint32_t) ih, 3, rgb_in.data()};

    sd_img_gen_params_t gp;
    sd_img_gen_params_init(&gp);
    gp.prompt = prompt.c_str();
    std::string negHold = neg;
    gp.negative_prompt = negHold.c_str();
    gp.width = width;
    gp.height = height;
    gp.init_image = init;
    gp.strength = str;
    gp.sample_params.sample_steps = nsteps;
    gp.sample_params.guidance.txt_cfg = (float) cfg;
    if (!sampler.empty()) {
        gp.sample_params.sample_method = str_to_sample_method(sampler.c_str());
        gp.sample_params.scheduler = sd_get_default_scheduler(g_sd, gp.sample_params.sample_method);
    }
    gp.seed = (int64_t) seed;
    gp.batch_count = 1;

    sd_image_t * imgs = nullptr;
    int n_imgs = 0;
    if (!generate_image(g_sd, &gp, &imgs, &n_imgs) || !imgs || n_imgs < 1) {
        return nullptr;
    }
    jbyteArray out = nullptr;
    if (imgs[0].data && imgs[0].channel >= 3) {
        size_t n = (size_t) imgs[0].width * imgs[0].height * 3;
        out = env->NewByteArray((jsize) n);
        if (out) {
            if (imgs[0].channel == 3) {
                env->SetByteArrayRegion(out, 0, (jsize) n, reinterpret_cast<jbyte *>(imgs[0].data));
            } else {
                std::vector<uint8_t> rgb(n);
                for (size_t i = 0; i < (size_t) imgs[0].width * imgs[0].height; i++) {
                    rgb[i * 3] = imgs[0].data[i * 4];
                    rgb[i * 3 + 1] = imgs[0].data[i * 4 + 1];
                    rgb[i * 3 + 2] = imgs[0].data[i * 4 + 2];
                }
                env->SetByteArrayRegion(out, 0, (jsize) n, reinterpret_cast<jbyte *>(rgb.data()));
            }
        }
    }
    free_sd_images(imgs, n_imgs);
    return out;
}

} // extern "C"
