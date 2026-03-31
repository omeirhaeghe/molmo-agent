package com.example.molmoagent.accessibility

import android.accessibilityservice.AccessibilityService
import com.example.molmoagent.agent.ActionResult

class GlobalActions(private val service: AccessibilityService) {

    fun pressBack(): ActionResult {
        return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            ActionResult.Success
        } else {
            ActionResult.Failure("Failed to press back")
        }
    }

    fun pressHome(): ActionResult {
        return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
            ActionResult.Success
        } else {
            ActionResult.Failure("Failed to press home")
        }
    }

    fun openNotifications(): ActionResult {
        return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) {
            ActionResult.Success
        } else {
            ActionResult.Failure("Failed to open notifications")
        }
    }

    fun openRecents(): ActionResult {
        return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) {
            ActionResult.Success
        } else {
            ActionResult.Failure("Failed to open recents")
        }
    }
}
