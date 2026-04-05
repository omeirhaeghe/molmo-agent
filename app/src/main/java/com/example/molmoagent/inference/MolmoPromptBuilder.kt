package com.example.molmoagent.inference

import com.example.molmoagent.agent.AgentAction
import com.example.molmoagent.agent.AgentStep
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MolmoPromptBuilder @Inject constructor() {

    companion object {
        val SYSTEM_PROMPT = """
            You are a mobile phone automation agent. You observe the phone screen via screenshots and take actions to complete user tasks.

            Available actions:
            - click(x, y): Tap at coordinates where x and y are integers from 0 to 1000 (0=left/top, 1000=right/bottom). Example: click(500, 500) taps the center.
            - long_press(x, y): Long press at coordinates (same 0–1000 scale)
            - type("text"): Type text into the currently focused input field
            - scroll(up|down|left|right): Scroll the screen in the given direction
            - press_enter(): Press the Enter / IME action key (submit search, confirm input, etc.)
            - press_back(): Press the Android back button
            - press_home(): Press the Android home button
            - open_notifications(): Open the notification shade
            - open_recents(): Open the recent apps view
            - open_app("app name"): Launch an app by name (PREFERRED — use this to open any app)
            - goto("url"): Open a URL in the browser
            - wait(): Wait for the UI to settle
            - send_msg_to_user("message"): Task is complete, send a message to the user

            For each step, respond with exactly two lines:
            THOUGHT: <your reasoning about what you see and what to do next>
            ACTION: <one action from the list above>

            Important:
            - Coordinates use a 0–1000 integer scale: (0,0) is top-left, (1000,1000) is bottom-right
            - Only output ONE action per step
            - ALWAYS use open_app("app name") to open an app — NEVER try to tap on home screen icons
            - ALWAYS call send_msg_to_user as your VERY NEXT action the moment the task is done. Do not take any extra steps after the task is visually complete.
            - If you are stuck or cannot complete the task, use send_msg_to_user to explain why
            - Never keep acting after the goal is achieved — immediately call send_msg_to_user
        """.trimIndent()

        fun formatAction(action: AgentAction): String {
            return when (action) {
                is AgentAction.Click -> "click(${(action.normX * 1000).toInt()}, ${(action.normY * 1000).toInt()})"
                is AgentAction.LongPress -> "long_press(${(action.normX * 1000).toInt()}, ${(action.normY * 1000).toInt()})"
                is AgentAction.Type -> "type(\"${action.text}\")"
                is AgentAction.Scroll -> "scroll(${action.direction.name.lowercase()})"
                is AgentAction.Swipe -> "swipe(${(action.startX * 1000).toInt()}, ${(action.startY * 1000).toInt()}, ${(action.endX * 1000).toInt()}, ${(action.endY * 1000).toInt()})"
                is AgentAction.PressEnter -> "press_enter()"
                is AgentAction.PressBack -> "press_back()"
                is AgentAction.PressHome -> "press_home()"
                is AgentAction.OpenNotifications -> "open_notifications()"
                is AgentAction.OpenRecents -> "open_recents()"
                is AgentAction.OpenApp -> "open_app(\"${action.appName}\")"
                is AgentAction.GoToUrl -> "goto(\"${action.url}\")"
                is AgentAction.SendMessageToUser -> "send_msg_to_user(\"${action.message}\")"
                is AgentAction.Wait -> "wait()"
            }
        }
    }

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
                sb.appendLine("ACTION: ${formatAction(step.action)}")
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
}
