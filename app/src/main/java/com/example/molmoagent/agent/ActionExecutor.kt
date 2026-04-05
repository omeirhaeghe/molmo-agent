package com.example.molmoagent.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
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
                val pixelX = action.normX * service.gestureDispatcher.displayWidth
                val pixelY = action.normY * service.gestureDispatcher.displayHeight

                // On the home screen, use accessibility click on the nearest icon.
                // The model's coordinate precision is often insufficient to hit small icons
                // via gesture; node-based clicking is exact and reliable.
                if (service.isOnHomeScreen()) {
                    val node = service.findBestClickableNode(pixelX, pixelY)
                    if (node != null) {
                        val label = node.contentDescription?.toString() ?: node.text?.toString()
                        Log.d("Clawlando", "[smartClick] home screen: clicking node '$label'")
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return ActionResult.Success
                    }
                }

                // For in-app: try exact hit via accessibility first, fall back to gesture
                val node = service.findBestClickableNode(pixelX, pixelY)
                if (node != null) {
                    val b = android.graphics.Rect(); node.getBoundsInScreen(b)
                    val dist = kotlin.math.sqrt(
                        ((b.exactCenterX()-pixelX)*(b.exactCenterX()-pixelX) +
                         (b.exactCenterY()-pixelY)*(b.exactCenterY()-pixelY)).toDouble()
                    )
                    // Only use accessibility click if within 80px — avoids clicking wrong element
                    if (dist <= 80) {
                        Log.d("Clawlando", "[smartClick] in-app accessibility click dist=${dist.toInt()}px")
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return ActionResult.Success
                    }
                }

                // Fall back to gesture
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
                service.gestureDispatcher.swipe(
                    startX = action.startX * service.gestureDispatcher.displayWidth,
                    startY = action.startY * service.gestureDispatcher.displayHeight,
                    endX = action.endX * service.gestureDispatcher.displayWidth,
                    endY = action.endY * service.gestureDispatcher.displayHeight
                )
            }

            is AgentAction.PressEnter -> {
                if (service.pressEnter()) ActionResult.Success
                else ActionResult.Failure("Could not find Enter key or focused input")
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
