package com.jarvis.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service for detecting focused text fields and pasting text.
 * Kept for optional use but not required for the main Jarvis intent pipeline.
 */
class TextPasteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisA11y"

        var instance: TextPasteAccessibilityService? = null
            private set

        fun isServiceActive(): Boolean = instance != null
    }

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

    fun getAppContext(): String {
        return "$currentAppName - $currentWindowTitle"
    }

    fun pasteText(text: String): Boolean {
        if (text.isBlank()) return false

        val focusedNode = findFocusedEditableNode()
        if (focusedNode != null) {
            return pasteIntoNode(focusedNode, text)
        }

        Log.w(TAG, "No focused editable text field found")
        return false
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            return focusedNode
        }

        val root = rootInActiveWindow ?: return null
        return findEditableNode(root)
    }

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

    private fun pasteIntoNode(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val existingText = node.text?.toString() ?: ""
            val selStart = node.textSelectionStart
            val selEnd = node.textSelectionEnd

            val mergedText = if (selStart >= 0 && selEnd >= 0 &&
                selStart <= existingText.length && selEnd <= existingText.length) {
                val before = existingText.substring(0, selStart)
                val after = existingText.substring(selEnd)
                "$before$text$after"
            } else {
                if (existingText.isNotEmpty()) "$existingText $text" else text
            }

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, mergedText)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                val newCursorPos = if (selStart >= 0) selStart + text.length else mergedText.length
                val cursorArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
                Log.d(TAG, "Text inserted at cursor position successfully")
            } else {
                pasteViaClipboard(node, text)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to paste text", e)
            false
        }
    }

    private fun pasteViaClipboard(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Jarvis", text)
            clipboard.setPrimaryClip(clip)

            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.d(TAG, "Text pasted via clipboard fallback")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard paste fallback also failed", e)
            false
        }
    }
}
