package com.example.molmoagent.agent

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionParser @Inject constructor() {

    private val clickPattern = Regex("""click\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val longPressPattern = Regex("""long_press\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val typePattern = Regex("""type\(\s*"([^"]*)"\s*\)""", RegexOption.IGNORE_CASE)
    private val scrollPattern = Regex("""scroll\(\s*(up|down|left|right)\s*\)""", RegexOption.IGNORE_CASE)
    private val swipePattern = Regex("""swipe\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val pressBackPattern = Regex("""press_back\(\s*\)""", RegexOption.IGNORE_CASE)
    private val pressHomePattern = Regex("""press_home\(\s*\)""", RegexOption.IGNORE_CASE)
    private val openNotificationsPattern = Regex("""open_notifications\(\s*\)""", RegexOption.IGNORE_CASE)
    private val openRecentsPattern = Regex("""open_recents\(\s*\)""", RegexOption.IGNORE_CASE)
    private val openAppPattern = Regex("""open_app\(\s*"([^"]*)"\s*\)""", RegexOption.IGNORE_CASE)
    private val gotoPattern = Regex("""goto\(\s*"([^"]*)"\s*\)""", RegexOption.IGNORE_CASE)
    private val sendMsgPattern = Regex("""send_msg_to_user\(\s*"([^"]*)"\s*\)""", RegexOption.IGNORE_CASE)
    private val waitPattern = Regex("""wait\(\s*\)""", RegexOption.IGNORE_CASE)

    /**
     * Parse a raw action string from the model into an AgentAction.
     * Returns null if the action cannot be parsed.
     */
    fun parse(rawAction: String): AgentAction? {
        val trimmed = rawAction.trim()

        clickPattern.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toFloatOrNull() ?: return null
            val y = match.groupValues[2].toFloatOrNull() ?: return null
            return AgentAction.Click(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }

        longPressPattern.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toFloatOrNull() ?: return null
            val y = match.groupValues[2].toFloatOrNull() ?: return null
            return AgentAction.LongPress(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }

        typePattern.find(trimmed)?.let { match ->
            return AgentAction.Type(match.groupValues[1])
        }

        scrollPattern.find(trimmed)?.let { match ->
            val direction = when (match.groupValues[1].lowercase()) {
                "up" -> ScrollDirection.UP
                "down" -> ScrollDirection.DOWN
                "left" -> ScrollDirection.LEFT
                "right" -> ScrollDirection.RIGHT
                else -> return null
            }
            return AgentAction.Scroll(direction)
        }

        swipePattern.find(trimmed)?.let { match ->
            val sx = match.groupValues[1].toFloatOrNull() ?: return null
            val sy = match.groupValues[2].toFloatOrNull() ?: return null
            val ex = match.groupValues[3].toFloatOrNull() ?: return null
            val ey = match.groupValues[4].toFloatOrNull() ?: return null
            return AgentAction.Swipe(sx, sy, ex, ey)
        }

        pressBackPattern.find(trimmed)?.let {
            return AgentAction.PressBack
        }

        pressHomePattern.find(trimmed)?.let {
            return AgentAction.PressHome
        }

        openNotificationsPattern.find(trimmed)?.let {
            return AgentAction.OpenNotifications
        }

        openRecentsPattern.find(trimmed)?.let {
            return AgentAction.OpenRecents
        }

        openAppPattern.find(trimmed)?.let { match ->
            return AgentAction.OpenApp(match.groupValues[1])
        }

        gotoPattern.find(trimmed)?.let { match ->
            return AgentAction.GoToUrl(match.groupValues[1])
        }

        sendMsgPattern.find(trimmed)?.let { match ->
            return AgentAction.SendMessageToUser(match.groupValues[1])
        }

        waitPattern.find(trimmed)?.let {
            return AgentAction.Wait()
        }

        return null
    }
}
