package com.example.molmoagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AgentAccessibilityService : AccessibilityService() {

    val gestureDispatcher by lazy { GestureDispatcher(this) }
    val globalActions by lazy { GlobalActions(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to accessibility events
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Take a screenshot using AccessibilityService API (API 30+).
     * Returns the bitmap or null on failure.
     */
    suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )
                    screenshot.hardwareBuffer.close()
                    cont.resume(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    cont.resume(null)
                }
            }
        )
    }

    /**
     * Set text on the currently focused input field.
     */
    fun setTextOnFocusedNode(text: String): Boolean {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
