package com.example.molmoagent.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.molmoagent.MainActivity
import com.example.molmoagent.agent.AgentLoop
import com.example.molmoagent.agent.AgentState

class OverlayService : Service(), OverlayVisibilityController {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private val lifecycleOwner = OverlayLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_STOP -> {
                hideOverlay()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        lifecycleOwner.handleDestroy()
        if (instance == this) instance = null
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FloatingPanel(
                    onSubmitTask = { task ->
                        val agentLoop = AgentLoopHolder.agentLoop
                        if (agentLoop != null && agentLoop.state.value == AgentState.IDLE) {
                            AgentLoopHolder.startTask(task)
                        }
                    },
                    onStop = {
                        AgentLoopHolder.agentLoop?.cancel()
                    },
                    onPause = {
                        AgentLoopHolder.agentLoop?.pause()
                    },
                    onResume = {
                        AgentLoopHolder.agentLoop?.resume()
                    },
                    onOpenApp = {
                        val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                )
            }
        }

        lifecycleOwner.handleStart()
        windowManager.addView(composeView, params)
        overlayView = composeView
        overlayParams = params
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun hide() {
        overlayView?.post { overlayView?.visibility = View.GONE }
    }

    override fun show() {
        overlayView?.post { overlayView?.visibility = View.VISIBLE }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Molmo Agent Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the Molmo Agent floating overlay"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Molmo Agent")
            .setContentText("Agent overlay is active")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "molmo_overlay"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW = "com.example.molmoagent.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.example.molmoagent.HIDE_OVERLAY"
        const val ACTION_STOP = "com.example.molmoagent.STOP_OVERLAY"

        @Volatile
        var instance: OverlayService? = null
            private set
    }
}
