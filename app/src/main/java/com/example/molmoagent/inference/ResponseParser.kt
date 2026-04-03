package com.example.molmoagent.inference

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared parser that extracts THOUGHT and ACTION from model response text.
 * Used by both HuggingFaceClient and LocalInferenceClient.
 */
@Singleton
class ResponseParser @Inject constructor() {

    fun parse(rawText: String): ModelResponse {
        val text = rawText.trim()

        // Try to split on "ACTION:" to separate thought from action
        val actionIndex = text.indexOf("ACTION:", ignoreCase = true)
        if (actionIndex >= 0) {
            val thoughtPart = text.substring(0, actionIndex).trim()
            val actionPart = text.substring(actionIndex + "ACTION:".length).trim()

            val thought = thoughtPart
                .removePrefix("THOUGHT:")
                .trim()

            return ModelResponse(
                thought = thought,
                rawAction = actionPart
            )
        }

        // Fallback: try to find thought and action on separate lines
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val thoughtLine = lines.find { it.startsWith("THOUGHT:", ignoreCase = true) }
        val actionLine = lines.find { it.startsWith("ACTION:", ignoreCase = true) }

        if (actionLine != null) {
            return ModelResponse(
                thought = thoughtLine?.removePrefix("THOUGHT:")?.trim() ?: "",
                rawAction = actionLine.removePrefix("ACTION:").trim()
            )
        }

        // Last resort: treat the entire text as the action
        return ModelResponse(
            thought = "",
            rawAction = text
        )
    }
}
