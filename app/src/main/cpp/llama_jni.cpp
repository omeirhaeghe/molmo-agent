#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <sys/stat.h>
#include <time.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "LlamaCppJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static long long time_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

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

    long long t_start = time_ms();

    // Validate file existence before loading
    struct stat st;
    if (stat(model_path, &st) != 0) {
        LOGE("Model file not found: %s", model_path);
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }
    LOGI("Model file size: %.1f MB", st.st_size / (1024.0 * 1024.0));

    if (stat(mmproj_path, &st) != 0) {
        LOGE("Mmproj file not found: %s", mmproj_path);
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }
    LOGI("Mmproj file size: %.1f MB", st.st_size / (1024.0 * 1024.0));

    // Initialize llama backend
    LOGI("[load] Initializing llama backend...");
    llama_backend_init();

    // Load model
    LOGI("[load] Loading language model (this may take a while)...");
    long long t_model = time_ms();
    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    g_model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(modelPath, model_path);

    if (!g_model) {
        LOGE("Failed to load model — possibly out of RAM");
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }
    LOGI("[load] Language model loaded in %lld ms", time_ms() - t_model);

    // Create context
    LOGI("[load] Creating context (n_ctx=%d, n_threads=%d, n_batch=512)...", nCtx, nThreads);
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = nCtx;
    ctx_params.n_threads = nThreads;
    ctx_params.n_batch   = 512;
    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        LOGE("Failed to create context — possibly out of RAM");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }
    LOGI("[load] Context created");

    // Initialize multimodal context
    LOGI("[load] Loading vision projector...");
    long long t_mmproj = time_ms();
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
    LOGI("[load] Vision projector loaded in %lld ms", time_ms() - t_mmproj);

    // Create sampler (greedy with temperature)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(42));

    LOGI("[load] Model ready — total load time: %lld ms", time_ms() - t_start);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_example_molmoagent_inference_local_LlamaCppBridge_runVisionInference(
        JNIEnv *env, jobject /* thiz */,
        jbyteArray imageBytes, jstring prompt,
        jint maxTokens, jfloat temperature) {

    std::lock_guard<std::mutex> lock(g_mutex);

    long long t_total = time_ms();

    if (!g_model || !g_ctx || !g_mtmd || !g_sampler) {
        LOGE("[infer] Model not loaded");
        return env->NewStringUTF("ERROR: Model not loaded");
    }

    LOGI("[infer] === Starting vision inference (maxTokens=%d, temp=%.2f) ===", maxTokens, temperature);

    // Get image data
    jsize img_len = env->GetArrayLength(imageBytes);
    jbyte *img_data = env->GetByteArrayElements(imageBytes, nullptr);
    LOGI("[infer] Image: %d bytes JPEG", img_len);

    // Create bitmap from JPEG buffer using the helper
    long long t_step = time_ms();
    mtmd_bitmap *bitmap = mtmd_helper_bitmap_init_from_buf(
            g_mtmd,
            reinterpret_cast<const unsigned char *>(img_data),
            static_cast<size_t>(img_len));
    env->ReleaseByteArrayElements(imageBytes, img_data, JNI_ABORT);

    if (!bitmap) {
        LOGE("[infer] Failed to decode image");
        return env->NewStringUTF("ERROR: Failed to decode image");
    }
    LOGD("[infer] Image decoded in %lld ms", time_ms() - t_step);

    // Get prompt string
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    LOGD("[infer] Prompt length: %zu chars", strlen(prompt_cstr));

    // Tokenize prompt + image
    t_step = time_ms();
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
        LOGE("[infer] Tokenization failed with code %d", tok_result);
        mtmd_input_chunks_free(chunks);
        return env->NewStringUTF("ERROR: Tokenization failed");
    }
    LOGI("[infer] Tokenized in %lld ms", time_ms() - t_step);

    // Clear KV cache for fresh generation
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // Evaluate all chunks (text + image)
    LOGI("[infer] Evaluating prompt + image embeddings (prefill)...");
    t_step = time_ms();
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
        LOGE("[infer] Prefill failed with code %d", eval_result);
        return env->NewStringUTF("ERROR: Evaluation failed");
    }
    LOGI("[infer] Prefill done: %d tokens in %lld ms (%.1f tok/s)",
         n_past, time_ms() - t_step,
         n_past * 1000.0 / (time_ms() - t_step + 1));

    // Update sampler temperature
    llama_sampler_free(g_sampler);
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(42));

    // Generate tokens
    LOGI("[infer] Generating tokens...");
    long long t_gen = time_ms();
    std::string result;
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int tokens_generated = 0;

    for (int i = 0; i < maxTokens; i++) {
        llama_token token_id = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Check for EOS
        if (llama_vocab_is_eog(vocab, token_id)) {
            LOGD("[infer] EOS token at position %d", i);
            break;
        }

        // Detokenize
        char buf[256];
        int n = llama_token_to_piece(vocab, token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }
        tokens_generated++;

        // Log progress every 10 tokens
        if (tokens_generated % 10 == 0) {
            long long elapsed = time_ms() - t_gen;
            LOGD("[infer] %d tokens generated (%.1f tok/s) ...",
                 tokens_generated, tokens_generated * 1000.0 / (elapsed + 1));
        }

        // Prepare next batch
        llama_batch batch = llama_batch_get_one(&token_id, 1);
        batch.pos[0] = n_past;
        n_past++;

        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("[infer] Decode failed at token %d", i);
            break;
        }
    }

    long long t_gen_total = time_ms() - t_gen;
    long long t_total_elapsed = time_ms() - t_total;
    LOGI("[infer] === Done: %d tokens, %zu chars in %lld ms (%.1f tok/s) | total: %lld ms ===",
         tokens_generated, result.size(), t_gen_total,
         tokens_generated * 1000.0 / (t_gen_total + 1), t_total_elapsed);
    LOGI("[infer] Output: %.200s%s", result.c_str(), result.size() > 200 ? "..." : "");
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
