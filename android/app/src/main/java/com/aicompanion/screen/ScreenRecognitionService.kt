/** 屏幕内容识别服务: 截屏+OCR识别当前屏幕内容, 用于AI感知用户正在做什么 */
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
        private var lastClickableElements: List<ClickableElement> = emptyList()

        fun getInstance(): ScreenRecognitionService? = currentInstance

        fun getLastScreenText(): String = lastScreenText

        fun getClickableElements(): List<ClickableElement> = lastClickableElements

        fun performClick(text: String): Boolean {
            val service = currentInstance ?: return false
            val element = lastClickableElements.find {
                it.text.contains(text, ignoreCase = true) ||
                        it.contentDescription.contains(text, ignoreCase = true)
            } ?: return false
            return element.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        fun performClickByIndex(index: Int): Boolean {
            val service = currentInstance ?: return false
            if (index < 0 || index >= lastClickableElements.size) return false
            return lastClickableElements[index].node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
                lastClickableElements = extractClickableElements(root)
                return true
            } finally {
                root.recycle()
            }
        }

        private fun extractText(node: AccessibilityNodeInfo): String {
            val sb = StringBuilder()
            extractTextRecursive(node, sb)
            return sb.toString().trim()
        }

        private fun extractTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                sb.appendLine(text)
            }
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrBlank() && text.isNullOrBlank()) {
                sb.appendLine("[${desc}]")
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                extractTextRecursive(child, sb)
                child.recycle()
            }
        }

        private fun extractClickableElements(node: AccessibilityNodeInfo): List<ClickableElement> {
            val elements = mutableListOf<ClickableElement>()
            extractClickableRecursive(node, elements)
            return elements
        }

        private fun extractClickableRecursive(node: AccessibilityNodeInfo, elements: MutableList<ClickableElement>) {
            if (node.isClickable) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (text.isNotBlank() || desc.isNotBlank()) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    elements.add(ClickableElement(
                        text = text,
                        contentDescription = desc,
                        bounds = rect,
                        node = node
                    ))
                }
            } else {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    extractClickableRecursive(child, elements)
                    child.recycle()
                }
            }
        }
    }

    data class ClickableElement(
        val text: String,
        val contentDescription: String,
        val bounds: Rect,
        val node: AccessibilityNodeInfo
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
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    Companion.refreshScreenData()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        currentInstance = null
        super.onDestroy()
    }
}