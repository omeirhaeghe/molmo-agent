package com.example.molmoagent.inference.local

import android.graphics.Bitmap
import android.util.Log
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

        Log.i(TAG, "[local] Step ${previousSteps.size + 1} — preparing inference for: $taskGoal")

        // Convert bitmap to JPEG bytes (not base64 - JNI takes raw bytes)
        val resized = imageProcessor.resize(screenshot)
        Log.d(TAG, "[local] Screenshot resized to ${resized.width}x${resized.height}")
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, ImageProcessor.JPEG_QUALITY, stream)
        val imageBytes = stream.toByteArray()
        Log.d(TAG, "[local] JPEG compressed: ${imageBytes.size / 1024} KB")

        val prompt = promptBuilder.buildPrompt(taskGoal, previousSteps)
        Log.d(TAG, "[local] Prompt built (${prompt.length} chars, ${previousSteps.size} previous steps)")

        Log.i(TAG, "[local] Calling JNI inference...")
        val t0 = System.currentTimeMillis()

        val rawText = withTimeout(120_000) {
            llamaBridge.runVisionInference(
                imageBytes = imageBytes,
                prompt = prompt,
                maxTokens = 512,
                temperature = 0.1f
            )
        }

        val elapsed = System.currentTimeMillis() - t0
        Log.i(TAG, "[local] JNI returned in ${elapsed}ms (${rawText.length} chars)")

        if (rawText.startsWith("ERROR:")) {
            Log.e(TAG, "[local] Inference error: $rawText")
            throw RuntimeException(rawText)
        }

        val response = responseParser.parse(rawText)
        Log.i(TAG, "[local] Parsed — thought: ${response.thought.take(80)}...")
        Log.i(TAG, "[local] Parsed — action: ${response.rawAction}")
        response
    }

    companion object {
        private const val TAG = "Clawlando"
    }

    override suspend fun testConnection(): Boolean {
        return llamaBridge.isModelLoaded()
    }
}
