package com.example.molmoagent.agent

sealed class AgentAction {
    /** Tap at normalized coordinates (0.0 to 1.0) */
    data class Click(val normX: Float, val normY: Float) : AgentAction()

    /** Long press at normalized coordinates */
    data class LongPress(val normX: Float, val normY: Float) : AgentAction()

    /** Type text into the currently focused input */
    data class Type(val text: String) : AgentAction()

    /** Scroll in a direction */
    data class Scroll(val direction: ScrollDirection) : AgentAction()

    /** Swipe between two normalized coordinate points */
    data class Swipe(
        val startX: Float, val startY: Float,
        val endX: Float, val endY: Float
    ) : AgentAction()

    /** Press the back button */
    data object PressBack : AgentAction()

    /** Press the home button */
    data object PressHome : AgentAction()

    /** Open the notification shade */
    data object OpenNotifications : AgentAction()

    /** Open recent apps */
    data object OpenRecents : AgentAction()

    /** Launch an app by name */
    data class OpenApp(val appName: String) : AgentAction()

    /** Open a URL in the browser */
    data class GoToUrl(val url: String) : AgentAction()

    /** Task is complete, send message to user */
    data class SendMessageToUser(val message: String) : AgentAction()

    /** Wait for UI to settle */
    data class Wait(val durationMs: Long = 1000) : AgentAction()
}

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

sealed class ActionResult {
    data object Success : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}

data class AgentStep(
    val stepNumber: Int,
    val screenshotBase64: String?,
    val thought: String,
    val action: AgentAction,
    val result: ActionResult? = null
)
