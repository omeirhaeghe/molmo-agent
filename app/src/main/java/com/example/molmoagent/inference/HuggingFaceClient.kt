package com.example.molmoagent.inference

import android.graphics.Bitmap
import com.example.molmoagent.agent.AgentStep
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuggingFaceClient @Inject constructor(
    val imageProcessor: ImageProcessor,
    val promptBuilder: MolmoPromptBuilder
) : InferenceClient {

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    var endpointUrl: String = ""

    override suspend fun predictNextAction(
        screenshot: Bitmap,
        taskGoal: String,
        previousSteps: List<AgentStep>
    ): ModelResponse = withContext(Dispatchers.IO) {
        val base64Image = imageProcessor.toBase64(screenshot)
        val prompt = promptBuilder.buildPrompt(taskGoal, previousSteps)

        val response = callEndpoint(prompt, base64Image)
        parseResponse(response)
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(endpointUrl)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful || response.code == 405 // Some endpoints only accept POST
        } catch (e: Exception) {
            false
        }
    }

    private fun callEndpoint(prompt: String, base64Image: String): String {
        // Build the request payload for HuggingFace Inference Endpoints.
        // The exact format depends on the model deployment, but MolmoWeb
        // uses the Transformers chat template format.
        val userContent = JsonArray().apply {
            // Image input
            add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", JsonObject().apply {
                    addProperty("url", "data:image/jpeg;base64,$base64Image")
                })
            })
            // Text input
            add(JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", prompt)
            })
        }

        val messages = JsonArray().apply {
            // System message
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", MolmoPromptBuilder.SYSTEM_PROMPT)
            })
            // User message with image + prompt
            add(JsonObject().apply {
                addProperty("role", "user")
                add("content", userContent)
            })
        }

        val payload = JsonObject().apply {
            add("messages", messages)
            addProperty("max_tokens", 512)
            addProperty("temperature", 0.1)
            add("parameters", JsonObject().apply {
                addProperty("max_new_tokens", 512)
            })
        }

        val body = gson.toJson(payload)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$endpointUrl/v1/chat/completions")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from inference server")

        if (!response.isSuccessful) {
            throw RuntimeException("Inference failed (${response.code}): $responseBody")
        }

        return extractGeneratedText(responseBody)
    }

    private fun extractGeneratedText(responseBody: String): String {
        val json = gson.fromJson(responseBody, JsonObject::class.java)

        // Try OpenAI-compatible format (chat completions)
        json.getAsJsonArray("choices")?.let { choices ->
            if (choices.size() > 0) {
                val choice = choices[0].asJsonObject
                choice.getAsJsonObject("message")?.let { message ->
                    return message.get("content").asString
                }
                // Fallback: text field directly on choice
                choice.get("text")?.let { return it.asString }
            }
        }

        // Try HuggingFace Inference API format
        json.getAsJsonArray("generated_text")?.let { arr ->
            if (arr.size() > 0) return arr[0].asString
        }
        json.get("generated_text")?.let { return it.asString }

        // Try direct array response
        val jsonArray = try {
            gson.fromJson(responseBody, JsonArray::class.java)
        } catch (e: Exception) { null }
        jsonArray?.let { arr ->
            if (arr.size() > 0) {
                val first = arr[0].asJsonObject
                first.get("generated_text")?.let { return it.asString }
            }
        }

        throw RuntimeException("Could not parse model response: $responseBody")
    }

    private fun parseResponse(rawText: String): ModelResponse {
        val lines = rawText.trim().lines()

        var thought = ""
        var action = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("THOUGHT:", ignoreCase = true) -> {
                    thought = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("ACTION:", ignoreCase = true) -> {
                    action = trimmed.substringAfter(":").trim()
                }
            }
        }

        if (thought.isEmpty() && action.isEmpty()) {
            // If the model didn't follow the format, treat entire output as thought
            // and try to extract an action from it
            thought = rawText.trim()
            action = rawText.trim()
        }

        return ModelResponse(thought = thought, rawAction = action)
    }
}
