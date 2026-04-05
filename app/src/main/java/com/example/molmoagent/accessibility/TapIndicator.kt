package com.example.molmoagent.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Shows a brief crosshair circle at each tap location so the user can verify coordinates.
 */
class TapIndicator(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null

    private val SIZE = 80 // dp-ish, in pixels

    fun show(x: Float, y: Float) {
        handler.post {
            hide()
            val view = CircleView(context, SIZE)
            val params = WindowManager.LayoutParams(
                SIZE, SIZE,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = (x - SIZE / 2).toInt()
                this.y = (y - SIZE / 2).toInt()
            }
            try {
                windowManager.addView(view, params)
                currentView = view
                handler.postDelayed({ hide() }, 600)
            } catch (_: Exception) {}
        }
    }

    fun hide() {
        currentView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            currentView = null
        }
    }

    private class CircleView(context: Context, private val size: Int) : View(context) {
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00BCD4.toInt()
            alpha = 180
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            alpha = 220
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            alpha = 200
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        override fun onDraw(canvas: Canvas) {
            val cx = size / 2f
            val cy = size / 2f
            val r = size / 2f - 4f
            canvas.drawCircle(cx, cy, r, circlePaint)
            canvas.drawCircle(cx, cy, r, ringPaint)
            canvas.drawLine(cx, cy - r, cx, cy + r, crossPaint)
            canvas.drawLine(cx - r, cy, cx + r, cy, crossPaint)
        }
    }
}
