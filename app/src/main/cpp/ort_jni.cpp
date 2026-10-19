// The meaning model (multilingual-e5-small) on the ONNX Runtime that ships with sherpa-onnx, through its C API.
// One runtime serves both the speech model and this one, instead of two copies in the APK.

#include <jni.h>
#include <android/log.h>
#include <vector>
#include "onnxruntime_c_api.h"

#define TAG "NeboOrt"

namespace {

// The runtime in the APK is 1.28; asking for its API version, not the headers', keeps the table it hands back valid.
constexpr uint32_t kApiVersion = 28;

const OrtApi* api() {
    static const OrtApi* a = OrtGetApiBase()->GetApi(kApiVersion);
    return a;
}

// True when the call succeeded; otherwise the error is logged and released.
bool ok(OrtStatus* s) {
    if (s == nullptr) return true;
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", api()->GetErrorMessage(s));
    api()->ReleaseStatus(s);
    return false;
}

struct Model {
    OrtEnv* env = nullptr;
    OrtSession* session = nullptr;
    OrtMemoryInfo* memory = nullptr;
    char* output = nullptr;   // the name of the first output, from the default allocator

    ~Model() {
        OrtAllocator* alloc = nullptr;
        if (output && ok(api()->GetAllocatorWithDefaultOptions(&alloc))) alloc->Free(alloc, output);
        if (memory) api()->ReleaseMemoryInfo(memory);
        if (session) api()->ReleaseSession(session);
        if (env) api()->ReleaseEnv(env);
    }
};

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_grungert_nebo_ml_OrtNative_open(JNIEnv* jenv, jobject, jstring path, jint threads) {
    if (api() == nullptr) { __android_log_print(ANDROID_LOG_ERROR, TAG, "runtime lacks API %u", kApiVersion); return 0; }
    auto* m = new Model();
    const char* p = jenv->GetStringUTFChars(path, nullptr);
    OrtSessionOptions* opts = nullptr;
    OrtAllocator* alloc = nullptr;
    bool good = ok(api()->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "nebo", &m->env)) &&
        ok(api()->CreateSessionOptions(&opts)) &&
        ok(api()->SetIntraOpNumThreads(opts, threads)) &&
        ok(api()->CreateSession(m->env, p, opts, &m->session)) &&
        ok(api()->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &m->memory)) &&
        ok(api()->GetAllocatorWithDefaultOptions(&alloc)) &&
        ok(api()->SessionGetOutputName(m->session, 0, alloc, &m->output));
    if (opts) api()->ReleaseSessionOptions(opts);
    jenv->ReleaseStringUTFChars(path, p);
    if (!good) { delete m; return 0; }
    return reinterpret_cast<jlong>(m);
}

// The model's first output for these token ids (attention on every token, one segment), flattened, or null.
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_grungert_nebo_ml_OrtNative_run(JNIEnv* jenv, jobject, jlong handle, jlongArray idsArray) {
    auto* m = reinterpret_cast<Model*>(handle);
    const jsize n = jenv->GetArrayLength(idsArray);
    std::vector<int64_t> ids(n), mask(n, 1), types(n, 0);
    jenv->GetLongArrayRegion(idsArray, 0, n, reinterpret_cast<jlong*>(ids.data()));
    const int64_t shape[2] = {1, n};
    OrtValue* inputs[3] = {nullptr, nullptr, nullptr};
    std::vector<int64_t>* data[3] = {&ids, &mask, &types};
    bool good = true;
    for (int i = 0; i < 3 && good; i++) {
        good = ok(api()->CreateTensorWithDataAsOrtValue(m->memory, data[i]->data(), n * sizeof(int64_t), shape, 2,
            ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &inputs[i]));
    }
    const char* inNames[3] = {"input_ids", "attention_mask", "token_type_ids"};
    const char* outNames[1] = {m->output};
    OrtValue* out = nullptr;
    jfloatArray result = nullptr;
    if (good && ok(api()->Run(m->session, nullptr, inNames, inputs, 3, outNames, 1, &out))) {
        OrtTensorTypeAndShapeInfo* info = nullptr;
        size_t count = 0;
        float* values = nullptr;
        if (ok(api()->GetTensorTypeAndShape(out, &info)) && ok(api()->GetTensorShapeElementCount(info, &count)) &&
            ok(api()->GetTensorMutableData(out, reinterpret_cast<void**>(&values)))) {
            result = jenv->NewFloatArray(static_cast<jsize>(count));
            if (result) jenv->SetFloatArrayRegion(result, 0, static_cast<jsize>(count), values);
        }
        if (info) api()->ReleaseTensorTypeAndShapeInfo(info);
        api()->ReleaseValue(out);
    }
    for (auto* v : inputs) if (v) api()->ReleaseValue(v);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_grungert_nebo_ml_OrtNative_close(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Model*>(handle);
}
