package com.example.molmoagent.inference

import android.graphics.Bitmap
import android.util.Log
import com.example.molmoagent.agent.AgentStep
import com.example.molmoagent.inference.local.LlamaCppBridge
import com.example.molmoagent.inference.local.LocalInferenceClient
import com.example.molmoagent.inference.local.ModelDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages switching between cloud (MolmoWeb), Qwen (Qwen2.5-VL), and local (SmolVLM) inference.
 * Implements InferenceClient so AgentLoop can use it transparently.
 */
@Singleton
class InferenceClientManager @Inject constructor(
    private val cloudClient: HuggingFaceClient,
    private val localClient: LocalInferenceClient,
    private val llamaBridge: LlamaCppBridge,
    private val downloadManager: ModelDownloadManager
) : InferenceClient {

    enum class InferenceMode { CLOUD, QWEN, LOCAL }

    // Separate HuggingFaceClient instance for Qwen (same API format, different endpoint)
    private val qwenClient = HuggingFaceClient(cloudClient.imageProcessor, cloudClient.promptBuilder)

    private val _mode = MutableStateFlow(InferenceMode.CLOUD)
    val mode: StateFlow<InferenceMode> = _mode

    var endpointUrl: String
        get() = cloudClient.endpointUrl
        set(value) { cloudClient.endpointUrl = value }

    var qwenEndpointUrl: String
        get() = qwenClient.endpointUrl
        set(value) { qwenClient.endpointUrl = value }

    private val activeClient: InferenceClient
        get() = when (_mode.value) {
            InferenceMode.CLOUD -> cloudClient
            InferenceMode.QWEN -> qwenClient
            InferenceMode.LOCAL -> localClient
        }

    override suspend fun predictNextAction(
        screenshot: Bitmap,
        taskGoal: String,
        previousSteps: List<AgentStep>
    ): ModelResponse {
        return activeClient.predictNextAction(screenshot, taskGoal, previousSteps)
    }

    override suspend fun testConnection(): Boolean {
        return activeClient.testConnection()
    }

    /**
     * Switch to Qwen cloud inference.
     */
    fun switchToQwen(unloadLocal: Boolean = true) {
        Log.i(TAG, "[mode] Switching to QWEN inference")
        _mode.value = InferenceMode.QWEN
        if (unloadLocal && llamaBridge.isModelLoaded()) {
            llamaBridge.unloadModel()
            Log.i(TAG, "[mode] Local model unloaded from RAM")
        }
    }

    /**
     * Switch to local inference. Loads the model if not already loaded.
     * Returns a Result indicating success or failure.
     */
    suspend fun switchToLocal(): Result<Unit> {
        Log.i(TAG, "[mode] Switching to LOCAL inference...")
        if (!LlamaCppBridge.isAvailable) {
            Log.e(TAG, "[mode] Native library not available")
            return Result.failure(IllegalStateException("Local inference not available: native library failed to load"))
        }
        if (!downloadManager.isDownloaded) {
            Log.e(TAG, "[mode] Model files not downloaded")
            return Result.failure(IllegalStateException("Model not downloaded"))
        }
        if (!llamaBridge.isModelLoaded()) {
            Log.i(TAG, "[mode] Loading model into RAM...")
            val t0 = System.currentTimeMillis()
            val loaded = llamaBridge.loadModel(
                downloadManager.modelPath,
                downloadManager.mmprojPath
            )
            if (!loaded) {
                Log.e(TAG, "[mode] Model load failed after ${System.currentTimeMillis() - t0}ms")
                return Result.failure(RuntimeException("Failed to load model. Device may not have enough RAM."))
            }
            Log.i(TAG, "[mode] Model loaded in ${System.currentTimeMillis() - t0}ms")
        } else {
            Log.i(TAG, "[mode] Model already in RAM")
        }
        _mode.value = InferenceMode.LOCAL
        Log.i(TAG, "[mode] Now using LOCAL inference")
        return Result.success(Unit)
    }

    /**
     * Switch to cloud inference. Optionally unloads the local model to free RAM.
     */
    fun switchToCloud(unloadLocal: Boolean = true) {
        Log.i(TAG, "[mode] Switching to CLOUD inference (unloadLocal=$unloadLocal)")
        _mode.value = InferenceMode.CLOUD
        if (unloadLocal && llamaBridge.isModelLoaded()) {
            llamaBridge.unloadModel()
            Log.i(TAG, "[mode] Local model unloaded from RAM")
        }
    }

    companion object {
        private const val TAG = "Clawlando"
    }

    fun setMode(mode: InferenceMode) {
        _mode.value = mode
    }
}
