package com.example.molmoagent.inference.local

import com.example.molmoagent.agent.AgentStep
import com.example.molmoagent.inference.MolmoPromptBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds prompts for SmolVLM2-2.2B-Instruct using its chat template.
 * Reuses the same system prompt and action space as MolmoPromptBuilder.
 *
 * The image marker <__media__> is used by llama.cpp's mtmd module to
 * replace with actual image embeddings during tokenization.
 */
@Singleton
class SmolVLMPromptBuilder @Inject constructor() {

    companion object {
        // The default media marker used by llama.cpp's mtmd module
        const val IMAGE_MARKER = "<__media__>"
    }

    fun buildPrompt(taskGoal: String, previousSteps: List<AgentStep>): String {
        val sb = StringBuilder()

        // System message with the action space definition
        sb.appendLine(MolmoPromptBuilder.SYSTEM_PROMPT)
        sb.appendLine()

        // User message with image + task
        sb.appendLine("# GOAL")
        sb.appendLine(taskGoal)
        sb.appendLine()

        if (previousSteps.isNotEmpty()) {
            sb.appendLine("# PREVIOUS STEPS")
            previousSteps.forEach { step ->
                sb.appendLine("## Step ${step.stepNumber}")
                sb.appendLine("THOUGHT: ${step.thought}")
                sb.appendLine("ACTION: ${MolmoPromptBuilder.formatAction(step.action)}")
                sb.appendLine()
            }
        }

        sb.appendLine("# CURRENTLY ACTIVE SCREEN")
        // The image marker tells mtmd where to insert the image embeddings
        sb.appendLine(IMAGE_MARKER)
        sb.appendLine()

        sb.appendLine("# NEXT STEP")
        sb.appendLine("Analyze the screenshot and provide your next THOUGHT and ACTION.")

        return sb.toString()
    }
}
