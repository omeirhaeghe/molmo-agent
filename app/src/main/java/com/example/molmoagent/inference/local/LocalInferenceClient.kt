package com.example.molmoagent.inference.local

import android.graphics.Bitmap
import com.example.molmoagent.agent.AgentStep
import com.example.molmoagent.inference.ImageProcessor
import com.example.molmoagent.inference.InferenceClient
import com.example.molmoagent.inference.ModelResponse
import com.example.molmoagent.inference.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInferenceClient @Inject constructor(
    private val llamaBridge: LlamaCppBridge,
    private val promptBuilder: SmolVLMPromptBuilder,
    private val imageProcessor: ImageProcessor,
    private val responseParser: ResponseParser
) : InferenceClient {

    override suspend fun predictNextAction(
        screenshot: Bitmap,
        taskGoal: String,
        previousSteps: List<AgentStep>
    ): ModelResponse = withContext(Dispatchers.Default) {
        if (!LlamaCppBridge.isAvailable) {
            throw IllegalStateException("Local inference not available: native library failed to load")
        }
        if (!llamaBridge.isModelLoaded()) {
            throw IllegalStateException("Local model not loaded. Download and load the model first.")
        }

        // Convert bitmap to JPEG bytes (not base64 - JNI takes raw bytes)
        val resized = imageProcessor.resize(screenshot)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, ImageProcessor.JPEG_QUALITY, stream)
        val imageBytes = stream.toByteArray()

        val prompt = promptBuilder.buildPrompt(taskGoal, previousSteps)

        val rawText = withTimeout(120_000) {
            llamaBridge.runVisionInference(
                imageBytes = imageBytes,
                prompt = prompt,
                maxTokens = 512,
                temperature = 0.1f
            )
        }

        if (rawText.startsWith("ERROR:")) {
            throw RuntimeException(rawText)
        }

        responseParser.parse(rawText)
    }

    override suspend fun testConnection(): Boolean {
        return llamaBridge.isModelLoaded()
    }
}
