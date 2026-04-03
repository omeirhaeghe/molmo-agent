package com.example.molmoagent.inference

import android.graphics.Bitmap
import com.example.molmoagent.agent.AgentStep
import com.example.molmoagent.inference.local.LlamaCppBridge
import com.example.molmoagent.inference.local.LocalInferenceClient
import com.example.molmoagent.inference.local.ModelDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages switching between cloud (HuggingFace) and local (SmolVLM) inference.
 * Implements InferenceClient so AgentLoop can use it transparently.
 */
@Singleton
class InferenceClientManager @Inject constructor(
    private val cloudClient: HuggingFaceClient,
    private val localClient: LocalInferenceClient,
    private val llamaBridge: LlamaCppBridge,
    private val downloadManager: ModelDownloadManager
) : InferenceClient {

    enum class InferenceMode { CLOUD, LOCAL }

    private val _mode = MutableStateFlow(InferenceMode.CLOUD)
    val mode: StateFlow<InferenceMode> = _mode

    var endpointUrl: String
        get() = cloudClient.endpointUrl
        set(value) { cloudClient.endpointUrl = value }

    private val activeClient: InferenceClient
        get() = when (_mode.value) {
            InferenceMode.CLOUD -> cloudClient
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
     * Switch to local inference. Loads the model if not already loaded.
     * Returns a Result indicating success or failure.
     */
    suspend fun switchToLocal(): Result<Unit> {
        if (!LlamaCppBridge.isAvailable) {
            return Result.failure(IllegalStateException("Local inference not available: native library failed to load"))
        }
        if (!downloadManager.isDownloaded) {
            return Result.failure(IllegalStateException("Model not downloaded"))
        }
        if (!llamaBridge.isModelLoaded()) {
            val loaded = llamaBridge.loadModel(
                downloadManager.modelPath,
                downloadManager.mmprojPath
            )
            if (!loaded) {
                return Result.failure(RuntimeException("Failed to load model. Device may not have enough RAM."))
            }
        }
        _mode.value = InferenceMode.LOCAL
        return Result.success(Unit)
    }

    /**
     * Switch to cloud inference. Optionally unloads the local model to free RAM.
     */
    fun switchToCloud(unloadLocal: Boolean = true) {
        _mode.value = InferenceMode.CLOUD
        if (unloadLocal && llamaBridge.isModelLoaded()) {
            llamaBridge.unloadModel()
        }
    }

    fun setMode(mode: InferenceMode) {
        _mode.value = mode
    }
}
