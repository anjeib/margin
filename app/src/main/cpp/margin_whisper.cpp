// Minimal JNI bridge to whisper.cpp for offline dictation.
#include <jni.h>
#include <string>
#include <thread>
#include <android/log.h>
#include "whisper.h"

#define TAG "MarginWhisper"

extern "C" {

JNIEXPORT jlong JNICALL
Java_ru_anjeib_margin_voice_WhisperNative_init(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) __android_log_print(ANDROID_LOG_ERROR, TAG, "failed to load model");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_ru_anjeib_margin_voice_WhisperNative_free(JNIEnv *, jobject, jlong ptr) {
    if (ptr) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

JNIEXPORT jstring JNICALL
Java_ru_anjeib_margin_voice_WhisperNative_transcribe(JNIEnv *env, jobject, jlong ptr, jfloatArray samples,
                                                     jstring lang, jstring prompt, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (!ctx) return env->NewStringUTF("");
    const char *l = env->GetStringUTFChars(lang, nullptr);
    const char *p = env->GetStringUTFChars(prompt, nullptr);
    jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.no_timestamps = true;
    params.translate = false;
    params.language = l;
    params.n_threads = threads > 0 ? threads : 4;
    params.initial_prompt = p[0] ? p : nullptr; // known names/projects help spelling
    params.suppress_nst = true;

    std::string out;
    if (whisper_full(ctx, params, data, n) == 0) {
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; ++i) out += whisper_full_get_segment_text(ctx, i);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full failed");
    }
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    env->ReleaseStringUTFChars(lang, l);
    env->ReleaseStringUTFChars(prompt, p);
    return env->NewStringUTF(out.c_str());
}

}
