// JNI bridge to whisper.cpp: load a model, transcribe 16 kHz mono float PCM, free the model.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <algorithm>
#include "whisper.h"

#define TAG "NeboWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_grungert_nebo_ml_WhisperNative_initContext(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_grungert_nebo_ml_WhisperNative_freeContext(JNIEnv *, jobject, jlong ptr) {
    if (ptr != 0) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

// Returns raw UTF-8 bytes: whisper can split a multi-byte character across segments, which
// NewStringUTF (modified UTF-8) would reject, so decoding happens on the Kotlin side.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_grungert_nebo_ml_WhisperNative_transcribe(JNIEnv *env, jobject, jlong ptr, jfloatArray samples,
                                                   jstring language, jstring prompt) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) return env->NewByteArray(0);

    const char *lang = env->GetStringUTFChars(language, nullptr);
    const char *initial = env->GetStringUTFChars(prompt, nullptr);
    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    p.beam_search.beam_size = 3;
    p.n_threads = std::max(2, std::min(6, (int) std::thread::hardware_concurrency() - 1));
    p.language = lang;
    p.translate = false;
    p.no_context = true;
    p.single_segment = false;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_special = false;
    p.print_timestamps = false;
    p.initial_prompt = initial;
    p.suppress_blank = true;

    std::string text;
    LOGI("transcribing %d samples with %d threads", n, p.n_threads);
    if (whisper_full(ctx, p, data, n) == 0) {
        int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; i++) text += whisper_full_get_segment_text(ctx, i);
    }

    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);
    env->ReleaseStringUTFChars(prompt, initial);
    jbyteArray out = env->NewByteArray((jsize) text.size());
    env->SetByteArrayRegion(out, 0, (jsize) text.size(), reinterpret_cast<const jbyte *>(text.data()));
    return out;
}
