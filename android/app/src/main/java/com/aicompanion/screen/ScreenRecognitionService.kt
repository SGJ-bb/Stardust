package com.aicompanion.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenRecognitionService : AccessibilityService() {

    companion object {
        private var currentInstance: ScreenRecognitionService? = null
        private var lastScreenText: String = ""
        private var lastClickableData: List<ClickableData> = emptyList()
        private var lastRefreshTime = 0L
        private const val REFRESH_THROTTLE_MS = 500L

        fun getInstance(): ScreenRecognitionService? = currentInstance
        fun getLastScreenText(): String = lastScreenText
        fun getClickableData(): List<ClickableData> = lastClickableData

        fun performClick(text: String): Boolean {
            val service = currentInstance ?: return false
            val data = lastClickableData.find {
                it.text.contains(text, ignoreCase = true) ||
                        it.desc.contains(text, ignoreCase = true)
            } ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                return findNodeByBounds(root, data.bounds)?.let { node ->
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    result
                } ?: false
            } finally {
                root.recycle()
            }
        }

        fun performClickByIndex(index: Int): Boolean {
            val service = currentInstance ?: return false
            if (index < 0 || index >= lastClickableData.size) return false
            val data = lastClickableData[index]
            val root = service.rootInActiveWindow ?: return false
            try {
                return findNodeByBounds(root, data.bounds)?.let { node ->
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    result
                } ?: false
            } finally {
                root.recycle()
            }
        }

        fun performGlobalAction(action: Int): Boolean {
            return currentInstance?.performGlobalAction(action) ?: false
        }

        fun performGesture(clickX: Float, clickY: Float): Boolean {
            val service = currentInstance ?: return false
            val path = Path().apply { moveTo(clickX, clickY) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            service.dispatchGesture(gesture, null, null)
            return true
        }

        fun performScroll(direction: String): Boolean {
            val service = currentInstance ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                val scrollable = findScrollableNode(root)
                if (scrollable != null) {
                    val action = when (direction) {
                        "forward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                        "backward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
                        else -> null
                    }
                    if (action != null) {
                        return scrollable.performAction(action.id)
                    }
                }
                return false
            } finally {
                root.recycle()
            }
        }

        private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findScrollableNode(child)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
            return null
        }

        fun refreshScreenData(): Boolean {
            val service = currentInstance ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                val text = extractText(root)
                lastScreenText = text
                lastClickableData = extractClickableData(root)
                lastRefreshTime = System.currentTimeMillis()
                return true
            } finally {
                root.recycle()
            }
        }

        private fun findNodeByBounds(root: AccessibilityNodeInfo, targetBounds: Rect): AccessibilityNodeInfo? {
            val rootBounds = Rect()
            root.getBoundsInScreen(rootBounds)
            if (rootBounds == targetBounds && root.isClickable) return root
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val result = findNodeByBounds(child, targetBounds)
                if (result != null) {
                    if (child !== result) child.recycle()
                    return result
                }
                child.recycle()
            }
            return null
        }

        private fun extractText(node: AccessibilityNodeInfo): String {
            val sb = StringBuilder()
            extractTextRecursive(node, sb)
            return sb.toString().trim()
        }

        private fun extractTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) sb.appendLine(text)
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank() && text.isNullOrBlank()) sb.appendLine("[$desc]")
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                extractTextRecursive(child, sb)
                child.recycle()
            }
        }

        private fun extractClickableData(node: AccessibilityNodeInfo): List<ClickableData> {
            val elements = mutableListOf<ClickableData>()
            extractClickableDataRecursive(node, elements)
            return elements
        }

        private fun extractClickableDataRecursive(node: AccessibilityNodeInfo, elements: MutableList<ClickableData>) {
            if (node.isClickable) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (text.isNotBlank() || desc.isNotBlank()) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    elements.add(ClickableData(text = text, desc = desc, bounds = rect))
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                extractClickableDataRecursive(child, elements)
                child.recycle()
            }
        }
    }

    data class ClickableData(
        val text: String,
        val desc: String,
        val bounds: Rect
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val packageName = event.packageName?.toString() ?: return
            val category = AppCategoryClassifier.classify(packageName)
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putString("current_app_category", category).apply()

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    refreshScreenData()
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val now = System.currentTimeMillis()
                    if (now - lastRefreshTime >= REFRESH_THROTTLE_MS) {
                        refreshScreenData()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        currentInstance = null
        super.onDestroy()
    }
}
