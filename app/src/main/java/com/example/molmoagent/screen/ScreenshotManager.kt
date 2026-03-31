package com.example.molmoagent.screen

import android.graphics.Bitmap
import com.example.molmoagent.accessibility.AgentAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotManager @Inject constructor() {

    /**
     * Capture a screenshot via AccessibilityService (API 30+).
     * Returns null if the service is not running or capture fails.
     */
    suspend fun captureScreen(): Bitmap? {
        val service = AgentAccessibilityService.instance ?: return null
        return service.captureScreen()
    }

    fun isAvailable(): Boolean = AgentAccessibilityService.isRunning()
}
