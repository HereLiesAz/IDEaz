// JNI glue for llama.cpp — SKELETON.
//
// Targets a recent llama.cpp C API (llama_model_load_from_file / llama_init_from_model
// / llama_sampler_*). The C API still shifts between releases, so match this to the
// tag you pin as the ./llama.cpp submodule and adjust names if the build complains.
// This cannot be compiled or run-verified without that submodule + the NDK.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

#define LOG_TAG "llama-android"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_nativeLoad(JNIEnv *env, jobject, jstring jpath) {
    static bool backend_inited = false;
    if (!backend_inited) { llama_backend_init(); backend_inited = true; }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mparams = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (model == nullptr) { LOGE("model load failed"); return 0; }
    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_nativeComplete(JNIEnv *env, jobject, jlong modelPtr,
                                                   jstring jprompt, jint maxTokens) {
    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    if (model == nullptr) return env->NewStringUTF("");
    const llama_vocab *vocab = llama_model_get_vocab(model);

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string promptStr(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Tokenize.
    int n_prompt = -llama_tokenize(vocab, promptStr.c_str(), promptStr.size(),
                                   nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, promptStr.c_str(), promptStr.size(),
                       tokens.data(), tokens.size(), true, true) < 0) {
        return env->NewStringUTF("");
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = n_prompt + maxTokens + 8;
    cparams.n_batch = cparams.n_ctx;
    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) { LOGE("ctx init failed"); return env->NewStringUTF(""); }

    // Greedy sampler.
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string out;
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    int generated = 0;
    char piece[256];

    while (generated < maxTokens) {
        if (llama_decode(ctx, batch) != 0) break;
        llama_token next = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) break;
        int n = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, true);
        if (n > 0) out.append(piece, n);
        batch = llama_batch_get_one(&next, 1);
        generated++;
    }

    llama_sampler_free(smpl);
    llama_free(ctx);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_nativeUnload(JNIEnv *, jobject, jlong modelPtr) {
    auto *model = reinterpret_cast<llama_model *>(modelPtr);
    if (model != nullptr) llama_model_free(model);
}
