package com.example.molmoagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.util.Log
import android.view.WindowManager
import com.example.molmoagent.agent.ActionResult
import com.example.molmoagent.agent.ScrollDirection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GestureDispatcher(private val service: AccessibilityService) {

    // Use currentWindowMetrics.bounds which always reflects the full physical display
    // (including system bars). displayMetrics.heightPixels may exclude the status bar
    // or nav bar in a Service context, causing coordinates to be mapped against a
    // smaller height than the screenshot's actual 2992px.
    private val windowManager: WindowManager
        get() = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val displayWidth: Int
        get() = windowManager.currentWindowMetrics.bounds.width()

    val displayHeight: Int
        get() = windowManager.currentWindowMetrics.bounds.height()

    /**
     * Tap at pixel coordinates.
     */
    suspend fun tap(x: Float, y: Float): ActionResult {
        Log.d("Clawlando", "[GestureDispatcher] tap($x, $y) on ${displayWidth}x${displayHeight} screen")
        (service as? AgentAccessibilityService)?.tapIndicator?.show(x, y)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        return dispatchGesture(stroke)
    }

    /**
     * Tap at normalized coordinates (0.0 to 1.0).
     */
    suspend fun tapNormalized(normX: Float, normY: Float): ActionResult {
        val pixelX = normX * displayWidth
        val pixelY = normY * displayHeight
        return tap(pixelX, pixelY)
    }

    /**
     * Long press at pixel coordinates.
     */
    suspend fun longPress(x: Float, y: Float): ActionResult {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1000L)
        return dispatchGesture(stroke)
    }

    /**
     * Long press at normalized coordinates.
     */
    suspend fun longPressNormalized(normX: Float, normY: Float): ActionResult {
        val pixelX = normX * displayWidth
        val pixelY = normY * displayHeight
        return longPress(pixelX, pixelY)
    }

    /**
     * Swipe from one point to another in pixel coordinates.
     */
    suspend fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300L
    ): ActionResult {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        return dispatchGesture(stroke)
    }

    /**
     * Scroll in a direction by swiping the center of the screen.
     */
    suspend fun scroll(direction: ScrollDirection): ActionResult {
        val centerX = displayWidth / 2f
        val centerY = displayHeight / 2f
        val distance = displayHeight / 3f

        val (startX, startY, endX, endY) = when (direction) {
            // Swipe up to scroll down (content moves up)
            ScrollDirection.DOWN -> arrayOf(centerX, centerY + distance / 2, centerX, centerY - distance / 2)
            // Swipe down to scroll up
            ScrollDirection.UP -> arrayOf(centerX, centerY - distance / 2, centerX, centerY + distance / 2)
            // Swipe left to scroll right
            ScrollDirection.RIGHT -> arrayOf(centerX + distance / 2, centerY, centerX - distance / 2, centerY)
            // Swipe right to scroll left
            ScrollDirection.LEFT -> arrayOf(centerX - distance / 2, centerY, centerX + distance / 2, centerY)
        }

        return swipe(startX, startY, endX, endY, 400L)
    }

    private suspend fun dispatchGesture(stroke: GestureDescription.StrokeDescription): ActionResult =
        suspendCancellableCoroutine { cont ->
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        Log.d("Clawlando", "[GestureDispatcher] onCompleted")
                        if (cont.isActive) cont.resume(ActionResult.Success)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        Log.w("Clawlando", "[GestureDispatcher] onCancelled")
                        if (cont.isActive) cont.resume(ActionResult.Failure("Gesture was cancelled by system"))
                    }
                },
                null
            )

            if (!dispatched) {
                Log.e("Clawlando", "[GestureDispatcher] dispatchGesture returned false — not dispatched")
                if (cont.isActive) cont.resume(ActionResult.Failure("dispatchGesture returned false"))
            }
        }
}
