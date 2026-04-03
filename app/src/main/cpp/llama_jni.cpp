#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <sys/stat.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "LlamaCppJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model   * g_model   = nullptr;
static llama_context * g_ctx     = nullptr;
static mtmd_context  * g_mtmd    = nullptr;
static llama_sampler * g_sampler = nullptr;
static std::mutex      g_mutex;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_molmoagent_inference_local_LlamaCppBridge_loadModel(
        JNIEnv *env, jobject /* thiz */,
        jstring modelPath, jstring mmprojPath,
        jint nCtx, jint nThreads) {

    std::lock_guard<std::mutex> lock(g_mutex);

    const char *model_path  = env->GetStringUTFChars(modelPath, nullptr);
    const char *mmproj_path = env->GetStringUTFChars(mmprojPath, nullptr);

    LOGI("Loading model: %s", model_path);
    LOGI("Loading mmproj: %s", mmproj_path);

    // Validate file existence before loading
    struct stat st;
    if (stat(model_path, &st) != 0) {
        LOGE("Model file not found: %s", model_path);
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }
    if (stat(mmproj_path, &st) != 0) {
        LOGE("Mmproj file not found: %s", mmproj_path);
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }

    // Initialize llama backend
    llama_backend_init();

    // Load model
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    g_model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(modelPath, model_path);

    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }

    // Create context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = nCtx;
    ctx_params.n_threads = nThreads;
    ctx_params.n_batch   = 512;
    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }

    // Initialize multimodal context
    mtmd_context_params mtmd_params = mtmd_context_params_default();
    mtmd_params.use_gpu    = false;
    mtmd_params.n_threads  = nThreads;
    mtmd_params.warmup     = false;
    g_mtmd = mtmd_init_from_file(mmproj_path, g_model, mtmd_params);
    env->ReleaseStringUTFChars(mmprojPath, mmproj_path);

    if (!g_mtmd) {
        LOGE("Failed to initialize multimodal context");
        llama_free(g_ctx);
        llama_model_free(g_model);
        g_ctx   = nullptr;
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Create sampler (greedy with temperature)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(42));

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_example_molmoagent_inference_local_LlamaCppBridge_runVisionInference(
        JNIEnv *env, jobject /* thiz */,
        jbyteArray imageBytes, jstring prompt,
        jint maxTokens, jfloat temperature) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_model || !g_ctx || !g_mtmd || !g_sampler) {
        return env->NewStringUTF("ERROR: Model not loaded");
    }

    // Get image data
    jsize img_len = env->GetArrayLength(imageBytes);
    jbyte *img_data = env->GetByteArrayElements(imageBytes, nullptr);

    // Create bitmap from JPEG buffer using the helper
    mtmd_bitmap *bitmap = mtmd_helper_bitmap_init_from_buf(
            g_mtmd,
            reinterpret_cast<const unsigned char *>(img_data),
            static_cast<size_t>(img_len));
    env->ReleaseByteArrayElements(imageBytes, img_data, JNI_ABORT);

    if (!bitmap) {
        LOGE("Failed to decode image");
        return env->NewStringUTF("ERROR: Failed to decode image");
    }

    // Get prompt string
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);

    // The prompt should contain the media marker where the image goes
    // mtmd_default_marker() returns "<__media__>"
    // We expect the prompt to already contain this marker

    // Tokenize prompt + image
    mtmd_input_text input_text;
    input_text.text          = prompt_cstr;
    input_text.add_special   = true;
    input_text.parse_special = true;

    const mtmd_bitmap *bitmaps_arr[] = { bitmap };
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();

    int32_t tok_result = mtmd_tokenize(g_mtmd, chunks, &input_text, bitmaps_arr, 1);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    mtmd_bitmap_free(bitmap);

    if (tok_result != 0) {
        LOGE("Tokenization failed with code %d", tok_result);
        mtmd_input_chunks_free(chunks);
        return env->NewStringUTF("ERROR: Tokenization failed");
    }

    // Clear KV cache for fresh generation
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // Evaluate all chunks (text + image)
    llama_pos n_past = 0;
    int32_t eval_result = mtmd_helper_eval_chunks(
            g_mtmd, g_ctx, chunks,
            n_past,          // starting position
            0,               // seq_id
            512,             // n_batch
            true,            // logits_last
            &n_past);        // updated position

    mtmd_input_chunks_free(chunks);

    if (eval_result != 0) {
        LOGE("Chunk evaluation failed with code %d", eval_result);
        return env->NewStringUTF("ERROR: Evaluation failed");
    }

    // Update sampler temperature
    llama_sampler_free(g_sampler);
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(42));

    // Generate tokens
    std::string result;
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    for (int i = 0; i < maxTokens; i++) {
        llama_token token_id = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Check for EOS
        if (llama_vocab_is_eog(vocab, token_id)) {
            break;
        }

        // Detokenize
        char buf[256];
        int n = llama_token_to_piece(vocab, token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        // Prepare next batch
        llama_batch batch = llama_batch_get_one(&token_id, 1);
        batch.pos[0] = n_past;
        n_past++;

        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Decode failed at token %d", i);
            break;
        }
    }

    LOGI("Generated %zu chars", result.size());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_molmoagent_inference_local_LlamaCppBridge_unloadModel(
        JNIEnv * /* env */, jobject /* thiz */) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_mtmd)    { mtmd_free(g_mtmd);             g_mtmd    = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);              g_ctx     = nullptr; }
    if (g_model)   { llama_model_free(g_model);      g_model   = nullptr; }

    llama_backend_free();
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_example_molmoagent_inference_local_LlamaCppBridge_isModelLoaded(
        JNIEnv * /* env */, jobject /* thiz */) {

    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_ctx != nullptr && g_mtmd != nullptr) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
