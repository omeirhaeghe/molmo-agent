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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.molmoagent.MainActivity
import com.example.molmoagent.agent.AgentLoop

class OverlayService : Service(), OverlayVisibilityController {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var glowView: ComposeView? = null
    private var glowParams: WindowManager.LayoutParams? = null
    private var isFocusable = false

    private val lifecycleOwner = OverlayLifecycleOwner()
    private val glowLifecycleOwner = OverlayLifecycleOwner()

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
        glowLifecycleOwner.handleDestroy()
        if (instance == this) instance = null
        super.onDestroy()
    }

    /**
     * Toggle the overlay window between focusable (keyboard works) and
     * not-focusable (touches pass through to apps behind).
     */
    fun setFocusable(focusable: Boolean) {
        if (focusable == isFocusable) return
        isFocusable = focusable
        val params = overlayParams ?: return
        params.flags = if (focusable) {
            // Focusable: keyboard can appear, but touches outside the overlay
            // are still forwarded thanks to FLAG_NOT_TOUCH_MODAL.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        // Start FOCUSABLE so the keyboard can appear when the user taps
        // the text field. FLAG_NOT_TOUCH_MODAL lets touches *outside* the
        // overlay pass through to the app behind.
        isFocusable = true
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FloatingPanel(
                    onSubmitTask = { task ->
                        // Drop focus so keyboard hides before agent starts
                        setFocusable(false)
                        AgentLoopHolder.startTask(task)
                    },
                    onStop = {
                        AgentLoopHolder.cancelTask()
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
                    },
                    onDismiss = {
                        setFocusable(false)
                        hideOverlay()
                    },
                    onInputFocused = { focused ->
                        // Make the overlay focusable when the text field is tapped
                        // so the soft keyboard can appear
                        setFocusable(focused)
                    }
                )
            }
        }

        lifecycleOwner.handleStart()
        windowManager.addView(composeView, params)
        overlayView = composeView
        overlayParams = params

        // Add the full-screen glow overlay (non-interactive, behind the panel)
        showGlowOverlay()
    }

    private fun showGlowOverlay() {
        if (glowView != null) return

        val glowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val glow = ComposeView(this).apply {
            setViewTreeLifecycleOwner(glowLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(glowLifecycleOwner)
            setContent {
                val agentLoop = AgentLoopHolder.agentLoop
                val state = agentLoop?.state?.collectAsState()
                state?.value?.let {
                    InferenceGlowOverlay(
                        agentState = it,
                        onTap = { AgentLoopHolder.cancelTask() }
                    )
                }
            }
        }

        glowLifecycleOwner.handleStart()
        windowManager.addView(glow, glowParams)
        glowView = glow
        this.glowParams = glowParams
    }

    /**
     * Toggle the glow overlay between touchable (intercepts all taps → cancel agent)
     * and non-touchable (all touches pass through to the app behind).
     */
    fun setGlowTouchable(touchable: Boolean) {
        val glow = glowView ?: return
        val params = glowParams ?: return
        params.flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        glow.post {
            try { windowManager.updateViewLayout(glow, params) } catch (_: Exception) {}
        }
    }

    fun setPanelVisible(visible: Boolean) {
        overlayView?.post {
            overlayView?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun hideGlowOverlay() {
        glowView?.let {
            windowManager.removeView(it)
            glowView = null
            glowParams = null
        }
    }

    private fun hideOverlay() {
        hideGlowOverlay()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun hide() {
        overlayView?.post { overlayView?.visibility = View.GONE }
        glowView?.post { glowView?.visibility = View.GONE }
    }

    override fun show() {
        overlayView?.post { overlayView?.visibility = View.VISIBLE }
        glowView?.post { glowView?.visibility = View.VISIBLE }
    }

    override fun showGlowOnly() {
        glowView?.post { glowView?.visibility = View.VISIBLE }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Clawlando Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the Clawlando floating overlay"
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
            .setContentTitle("Clawlando")
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
