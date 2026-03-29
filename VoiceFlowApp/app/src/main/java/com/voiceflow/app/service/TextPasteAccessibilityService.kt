package com.voiceflow.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service for detecting focused text fields and pasting text.
 * Equivalent to text_paster.py + window_context.py from OpenVoiceFlow.
 *
 * This service:
 * 1. Tracks the currently focused text field (AccessibilityNodeInfo)
 * 2. Provides the current app name/window title for LLM context
 * 3. Pastes corrected text into the focused text field
 */
class TextPasteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TextPasteA11y"

        // Singleton reference so other components can interact with this service
        var instance: TextPasteAccessibilityService? = null
            private set

        /**
         * Check if the accessibility service is currently active.
         */
        fun isServiceActive(): Boolean = instance != null
    }

    // Track the currently focused editable node and app context
    private var currentAppName: String = "Unknown"
    private var currentWindowTitle: String = "Unknown"

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Track current app and window title
                event.packageName?.let { pkg ->
                    currentAppName = pkg.toString()
                }
                event.text?.joinToString(" ")?.let { title ->
                    if (title.isNotBlank()) {
                        currentWindowTitle = title
                    }
                }
                Log.d(TAG, "Window changed: $currentAppName - $currentWindowTitle")
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Track focused views for context
                event.packageName?.let { pkg ->
                    currentAppName = pkg.toString()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    /**
     * Get the current app context string for the LLM.
     * Equivalent to get_active_window_title() in OpenVoiceFlow.
     */
    fun getAppContext(): String {
        return "$currentAppName - $currentWindowTitle"
    }

    /**
     * Paste text into the currently focused text field.
     * Equivalent to paste_text() in OpenVoiceFlow.
     *
     * @return true if text was successfully pasted
     */
    fun pasteText(text: String): Boolean {
        if (text.isBlank()) return false

        val focusedNode = findFocusedEditableNode()
        if (focusedNode != null) {
            return pasteIntoNode(focusedNode, text)
        }

        Log.w(TAG, "No focused editable text field found")
        return false
    }

    /**
     * Find the currently focused editable text field.
     */
    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        // Try to find the input-focused node first
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            return focusedNode
        }

        // Fallback: search for any editable focused node
        val root = rootInActiveWindow ?: return null
        return findEditableNode(root)
    }

    /**
     * Recursively search for a focused editable node.
     */
    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Set text on an accessibility node.
     */
    private fun pasteIntoNode(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            // Try ACTION_SET_TEXT first (most reliable)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                Log.d(TAG, "Text pasted successfully via ACTION_SET_TEXT")
            } else {
                // Fallback: use clipboard
                pasteViaClipboard(node, text)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to paste text", e)
            false
        }
    }

    /**
     * Fallback: paste via clipboard (copy + paste action).
     */
    private fun pasteViaClipboard(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("VoiceFlow", text)
            clipboard.setPrimaryClip(clip)

            // Try paste action
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.d(TAG, "Text pasted via clipboard fallback")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard paste fallback also failed", e)
            false
        }
    }
}
