package com.example.molmoagent.inference

import android.graphics.Bitmap
import com.example.molmoagent.agent.AgentStep

data class ModelResponse(
    val thought: String,
    val rawAction: String
)

interface InferenceClient {

    /**
     * Send a screenshot and task context to the model and get the next action.
     */
    suspend fun predictNextAction(
        screenshot: Bitmap,
        taskGoal: String,
        previousSteps: List<AgentStep>
    ): ModelResponse

    /**
     * Ask the model to narrate what it did to complete a task, given the full step history.
     * Returns a friendly first-person summary of the actions taken.
     */
    suspend fun summarizeTask(goal: String, steps: List<AgentStep>): String

    /**
     * Test the connection to the inference server.
     */
    suspend fun testConnection(): Boolean
}
