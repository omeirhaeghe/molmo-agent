package com.example.molmoagent.inference

import com.example.molmoagent.agent.AgentStep
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MolmoPromptBuilder @Inject constructor() {

    companion object {
        val SYSTEM_PROMPT = """
            You are a mobile phone automation agent. You observe the phone screen via screenshots and take actions to complete user tasks.

            Available actions:
            - click(x, y): Tap at normalized coordinates (0.0 to 1.0 for both x and y)
            - long_press(x, y): Long press at normalized coordinates
            - type("text"): Type text into the currently focused input field
            - scroll(up|down|left|right): Scroll the screen in the given direction
            - press_back(): Press the Android back button
            - press_home(): Press the Android home button
            - open_notifications(): Open the notification shade
            - open_recents(): Open the recent apps view
            - open_app("app name"): Launch an app by name
            - goto("url"): Open a URL in the browser
            - wait(): Wait for the UI to settle
            - send_msg_to_user("message"): Task is complete, send a message to the user

            For each step, respond with exactly two lines:
            THOUGHT: <your reasoning about what you see and what to do next>
            ACTION: <one action from the list above>

            Important:
            - Coordinates are normalized from 0.0 (top/left) to 1.0 (bottom/right)
            - Only output ONE action per step
            - When the task is complete, use send_msg_to_user to report the result
            - If you are stuck or cannot complete the task, use send_msg_to_user to explain why
        """.trimIndent()
    }

    /**
     * Build the full prompt for the model including task goal, previous steps, and current screenshot.
     * The screenshot is provided separately as an image input.
     */
    fun buildPrompt(taskGoal: String, previousSteps: List<AgentStep>): String {
        val sb = StringBuilder()

        sb.appendLine("# GOAL")
        sb.appendLine(taskGoal)
        sb.appendLine()

        if (previousSteps.isNotEmpty()) {
            sb.appendLine("# PREVIOUS STEPS")
            for (step in previousSteps) {
                sb.appendLine("## Step ${step.stepNumber}")
                sb.appendLine("THOUGHT: ${step.thought}")
                sb.appendLine("ACTION: ${formatAction(step)}")
                sb.appendLine()
            }
        }

        sb.appendLine("# CURRENTLY ACTIVE SCREEN")
        sb.appendLine("[See the attached screenshot]")
        sb.appendLine()
        sb.appendLine("# NEXT STEP")
        sb.appendLine("Analyze the screenshot and provide your next THOUGHT and ACTION.")

        return sb.toString()
    }

    private fun formatAction(step: AgentStep): String {
        return when (val action = step.action) {
            is com.example.molmoagent.agent.AgentAction.Click -> "click(${action.normX}, ${action.normY})"
            is com.example.molmoagent.agent.AgentAction.LongPress -> "long_press(${action.normX}, ${action.normY})"
            is com.example.molmoagent.agent.AgentAction.Type -> "type(\"${action.text}\")"
            is com.example.molmoagent.agent.AgentAction.Scroll -> "scroll(${action.direction.name.lowercase()})"
            is com.example.molmoagent.agent.AgentAction.Swipe -> "swipe(${action.startX}, ${action.startY}, ${action.endX}, ${action.endY})"
            is com.example.molmoagent.agent.AgentAction.PressBack -> "press_back()"
            is com.example.molmoagent.agent.AgentAction.PressHome -> "press_home()"
            is com.example.molmoagent.agent.AgentAction.OpenNotifications -> "open_notifications()"
            is com.example.molmoagent.agent.AgentAction.OpenRecents -> "open_recents()"
            is com.example.molmoagent.agent.AgentAction.OpenApp -> "open_app(\"${action.appName}\")"
            is com.example.molmoagent.agent.AgentAction.GoToUrl -> "goto(\"${action.url}\")"
            is com.example.molmoagent.agent.AgentAction.SendMessageToUser -> "send_msg_to_user(\"${action.message}\")"
            is com.example.molmoagent.agent.AgentAction.Wait -> "wait()"
        }
    }
}
