package com.example.molmoagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AgentAccessibilityService : AccessibilityService() {

    val gestureDispatcher by lazy { GestureDispatcher(this) }
    val globalActions by lazy { GlobalActions(this) }
    val tapIndicator by lazy { TapIndicator(this) }

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
     * Type text into the currently focused input field.
     * Strategy 1: ACTION_SET_TEXT on the accessibility-focused node (native EditText).
     * Strategy 2: Clipboard paste on the focused node.
     * Strategy 3: Search the window tree for any editable node (WhatsApp / React Native / Flutter).
     */
    fun setTextOnFocusedNode(text: String): Boolean {
        val root = rootInActiveWindow
        val focusedNode = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        Log.d("Clawlando", "[type] focusedNode=${focusedNode?.className}, root=${root?.packageName}")

        // Try ACTION_SET_TEXT on focused node first
        if (focusedNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                Log.d("Clawlando", "[type] SET_TEXT on focused node succeeded")
                return true
            }
            // Clipboard paste on focused node
            Log.d("Clawlando", "[type] SET_TEXT failed, trying clipboard paste on focused node")
            if (pasteViaClipboard(focusedNode, text)) return true
        }

        // Fallback: search entire tree for any editable node
        Log.d("Clawlando", "[type] focused node not found or paste failed, searching tree for editable node")
        if (root != null) {
            val editable = findEditableNode(root)
            Log.d("Clawlando", "[type] editable node found: ${editable?.className} pkg=${editable?.packageName}")
            if (editable != null) {
                // Click it first to ensure focus
                editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    Log.d("Clawlando", "[type] SET_TEXT on editable node succeeded")
                    return true
                }
                if (pasteViaClipboard(editable, text)) return true
            }
        }

        Log.e("Clawlando", "[type] all strategies failed")
        return false
    }

    private fun pasteViaClipboard(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("agent_text", text))
            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.d("Clawlando", "[type] clipboard paste result: $pasted")
            pasted
        } catch (e: Exception) {
            Log.e("Clawlando", "[type] clipboard paste exception: ${e.message}")
            false
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Press the Enter / IME action key.
     * First tries to find and click the action key inside the on-screen keyboard window.
     * Falls back to performing an IME action on the focused input node.
     */
    fun pressEnter(): Boolean {
        // Walk IME window tree for Enter / Go / Search / Send / Done key
        for (window in windows ?: emptyList()) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                val root = window.root ?: continue
                val key = findEnterNode(root)
                if (key != null) {
                    key.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        // Fallback: ask the focused input field to perform its IME action (e.g. "Go", "Search")
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            // ACTION_IME_ENTER is 0x01000000 — supported by most EditText-backed nodes
            return focused.performAction(0x01000000)
        }
        return false
    }

    private fun findEnterNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) {
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val enterKeywords = setOf("search", "go", "send", "done", "enter", "next", "↵", "return")
            if (enterKeywords.any { text == it || desc == it || desc.contains(it) || text.contains(it) }) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEnterNode(child)
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
