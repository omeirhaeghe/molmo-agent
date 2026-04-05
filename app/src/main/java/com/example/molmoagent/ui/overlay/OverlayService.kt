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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.molmoagent.MainActivity
import com.example.molmoagent.agent.AgentState
import kotlin.math.abs

class OverlayService : Service(), OverlayVisibilityController {

    private lateinit var windowManager: WindowManager

    // ── FAB ──────────────────────────────────────────────────────────────
    private var fabView: ComposeView? = null
    private var fabParams: WindowManager.LayoutParams? = null
    private var fabLifecycleOwner = OverlayLifecycleOwner()

    private var fabDragStartX = 0f
    private var fabDragStartY = 0f
    private var fabDragInitialX = 0
    private var fabDragInitialY = 0
    private var fabIsDragging = false

    // ── Panel ─────────────────────────────────────────────────────────────
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelLifecycleOwner = OverlayLifecycleOwner()
    private var isFocusable = false
    private var panelExpanded = false

    // ── Glow ──────────────────────────────────────────────────────────────
    private var glowView: ComposeView? = null
    private var glowParams: WindowManager.LayoutParams? = null
    private val glowLifecycleOwner = OverlayLifecycleOwner()

    // ─────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────

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
            ACTION_HIDE -> hideAll()
            ACTION_STOP -> { hideAll(); stopSelf() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideAll()
        glowLifecycleOwner.handleDestroy()
        if (instance == this) instance = null
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────
    // FAB
    // ─────────────────────────────────────────────────────────────────────

    private fun showFab() {
        if (fabView != null) return

        val metrics = windowManager.currentWindowMetrics.bounds
        val displayWidth = metrics.width()
        val displayHeight = metrics.height()
        val density = resources.displayMetrics.density
        val fabSizePx = (52 * density).toInt()
        val marginPx = (20 * density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = displayWidth - fabSizePx - marginPx
            y = displayHeight / 2 - fabSizePx / 2
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(fabLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(fabLifecycleOwner)
            setContent {
                val agentLoop = AgentLoopHolder.agentLoop
                val state by agentLoop?.state?.collectAsState()
                    ?: remember { mutableStateOf(AgentState.IDLE) }
                FloatingFab(agentState = state)
            }
            setOnTouchListener(fabTouchListener)
        }

        fabLifecycleOwner.handleStart()
        windowManager.addView(composeView, params)
        fabView = composeView
        fabParams = params
    }

    private fun hideFab() {
        fabView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        fabView = null
        fabParams = null
        fabLifecycleOwner.handleDestroy()
        fabLifecycleOwner = OverlayLifecycleOwner()
    }

    private val fabTouchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                fabDragStartX = event.rawX
                fabDragStartY = event.rawY
                fabDragInitialX = fabParams?.x ?: 0
                fabDragInitialY = fabParams?.y ?: 0
                fabIsDragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - fabDragStartX
                val dy = event.rawY - fabDragStartY
                if (!fabIsDragging && (abs(dx) > 8f || abs(dy) > 8f)) fabIsDragging = true
                if (fabIsDragging) {
                    fabParams?.let { p ->
                        p.x = fabDragInitialX + dx.toInt()
                        p.y = fabDragInitialY + dy.toInt()
                        fabView?.let { v ->
                            try { windowManager.updateViewLayout(v, p) } catch (_: Exception) {}
                        }
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (fabIsDragging) snapFabToEdge()
                else if (panelExpanded) collapsePanel()
                else expandPanel()
                fabIsDragging = false
                true
            }
            else -> false
        }
    }

    private fun snapFabToEdge() {
        val view = fabView ?: return
        val params = fabParams ?: return
        val metrics = windowManager.currentWindowMetrics.bounds
        val displayWidth = metrics.width()
        val displayHeight = metrics.height()
        val density = resources.displayMetrics.density
        val marginPx = (20 * density).toInt()
        val fabSizePx = view.width.takeIf { it > 0 } ?: (52 * density).toInt()

        val centerX = params.x + fabSizePx / 2
        params.x = if (centerX < displayWidth / 2) marginPx
                   else displayWidth - fabSizePx - marginPx

        val statusBarHeight = (60 * density).toInt()
        params.y = params.y.coerceIn(statusBarHeight, displayHeight - fabSizePx - marginPx)

        try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────
    // Panel
    // ─────────────────────────────────────────────────────────────────────

    private fun expandPanel() {
        panelExpanded = true
        hideFab()
        showPanel()
    }

    private fun collapsePanel() {
        panelExpanded = false
        removePanel()
        showFab()
    }

    private fun showPanel() {
        if (panelView != null) return

        isFocusable = false
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(panelLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(panelLifecycleOwner)
            setContent {
                FloatingPanel(
                    onSubmitTask = { task ->
                        setFocusable(false)
                        AgentLoopHolder.startTask(task)
                    },
                    onStop = { AgentLoopHolder.cancelTask() },
                    onPause = { AgentLoopHolder.agentLoop?.pause() },
                    onResume = { AgentLoopHolder.agentLoop?.resume() },
                    onOpenApp = {
                        val intent = Intent(this@OverlayService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    },
                    onDismiss = { collapsePanel() },
                    onInputFocused = { focused -> setFocusable(focused) }
                )
            }
        }

        panelLifecycleOwner.handleStart()
        windowManager.addView(composeView, params)
        panelView = composeView
        panelParams = params
    }

    private fun removePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        panelParams = null
        panelLifecycleOwner.handleDestroy()
        panelLifecycleOwner = OverlayLifecycleOwner()
    }

    fun setFocusable(focusable: Boolean) {
        if (focusable == isFocusable) return
        isFocusable = focusable
        val params = panelParams ?: return
        params.flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        panelView?.let {
            try { windowManager.updateViewLayout(it, params) } catch (_: Exception) {}
        }
    }

    // Called by AgentLoopHolder: hide panel while agent runs, show when done
    fun setPanelVisible(visible: Boolean) {
        if (visible) {
            // Task complete — show panel with result, hide FAB
            if (panelView == null) {
                panelExpanded = true
                showPanel()
            } else {
                panelView?.post { panelView?.visibility = View.VISIBLE }
            }
            fabView?.post { fabView?.visibility = View.GONE }
        } else {
            // Agent running — hide panel, show FAB with status dot
            panelView?.post { panelView?.visibility = View.GONE }
            if (fabView == null) showFab()
            else fabView?.post { fabView?.visibility = View.VISIBLE }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Glow
    // ─────────────────────────────────────────────────────────────────────

    private fun showGlowOverlay() {
        if (glowView != null) return

        val params = WindowManager.LayoutParams(
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
                    InferenceGlowOverlay(agentState = it, onTap = { AgentLoopHolder.cancelTask() })
                }
            }
        }

        glowLifecycleOwner.handleStart()
        windowManager.addView(glow, params)
        glowView = glow
        glowParams = params
    }

    private fun hideGlowOverlay() {
        glowView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            glowView = null
            glowParams = null
        }
    }

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

    // ─────────────────────────────────────────────────────────────────────
    // Top-level show/hide
    // ─────────────────────────────────────────────────────────────────────

    /** Entry point from MainActivity: show panel immediately so user can type a task. */
    private fun showOverlay() {
        showGlowOverlay()
        expandPanel()
    }

    private fun hideAll() {
        hideFab()
        removePanel()
        hideGlowOverlay()
    }

    // ─────────────────────────────────────────────────────────────────────
    // OverlayVisibilityController (called by AgentLoop during screenshot)
    // ─────────────────────────────────────────────────────────────────────

    override fun hide() {
        fabView?.post { fabView?.visibility = View.GONE }
        panelView?.post { panelView?.visibility = View.GONE }
        glowView?.post { glowView?.visibility = View.GONE }
    }

    override fun show() {
        fabView?.post { fabView?.visibility = View.VISIBLE }
        glowView?.post { glowView?.visibility = View.VISIBLE }
    }

    override fun showGlowOnly() {
        fabView?.post { fabView?.visibility = View.VISIBLE }
        glowView?.post { glowView?.visibility = View.VISIBLE }
        // panel stays hidden while agent is acting
    }

    // ─────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Clawlando Overlay", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows the Clawlando floating overlay" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
