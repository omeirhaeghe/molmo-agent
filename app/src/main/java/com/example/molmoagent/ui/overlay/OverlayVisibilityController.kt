package com.example.molmoagent.ui.overlay

/**
 * Interface for controlling overlay visibility during screenshot capture.
 * The overlay must be hidden when taking screenshots so it doesn't appear in them.
 */
interface OverlayVisibilityController {
    fun hide()
    fun show()
    /** Restore only the glow border — keeps the panel hidden during a running task. */
    fun showGlowOnly()
}
