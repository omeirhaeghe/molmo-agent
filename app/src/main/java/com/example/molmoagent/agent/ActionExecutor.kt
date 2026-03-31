package com.example.molmoagent.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.molmoagent.accessibility.AgentAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun execute(action: AgentAction): ActionResult {
        val service = AgentAccessibilityService.instance
            ?: return ActionResult.Failure("Accessibility service not running")

        return when (action) {
            is AgentAction.Click -> {
                service.gestureDispatcher.tapNormalized(action.normX, action.normY)
            }

            is AgentAction.LongPress -> {
                service.gestureDispatcher.longPressNormalized(action.normX, action.normY)
            }

            is AgentAction.Type -> {
                if (service.setTextOnFocusedNode(action.text)) {
                    ActionResult.Success
                } else {
                    ActionResult.Failure("No focused input field found")
                }
            }

            is AgentAction.Scroll -> {
                service.gestureDispatcher.scroll(action.direction)
            }

            is AgentAction.Swipe -> {
                val dm = context.resources.displayMetrics
                service.gestureDispatcher.swipe(
                    startX = action.startX * dm.widthPixels,
                    startY = action.startY * dm.heightPixels,
                    endX = action.endX * dm.widthPixels,
                    endY = action.endY * dm.heightPixels
                )
            }

            is AgentAction.PressBack -> service.globalActions.pressBack()
            is AgentAction.PressHome -> service.globalActions.pressHome()
            is AgentAction.OpenNotifications -> service.globalActions.openNotifications()
            is AgentAction.OpenRecents -> service.globalActions.openRecents()

            is AgentAction.OpenApp -> {
                launchApp(action.appName)
            }

            is AgentAction.GoToUrl -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult.Success
                } catch (e: Exception) {
                    ActionResult.Failure("Failed to open URL: ${e.message}")
                }
            }

            is AgentAction.SendMessageToUser -> {
                // This is handled by AgentLoop directly
                ActionResult.Success
            }

            is AgentAction.Wait -> {
                delay(action.durationMs)
                ActionResult.Success
            }
        }
    }

    private fun launchApp(appName: String): ActionResult {
        val pm = context.packageManager
        val lowerName = appName.lowercase()

        // Search installed apps for a matching name
        val intent = pm.getInstalledApplications(0)
            .firstOrNull { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                label == lowerName || label.contains(lowerName)
            }
            ?.let { appInfo ->
                pm.getLaunchIntentForPackage(appInfo.packageName)
            }

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult.Success
        } else {
            ActionResult.Failure("App not found: $appName")
        }
    }
}
