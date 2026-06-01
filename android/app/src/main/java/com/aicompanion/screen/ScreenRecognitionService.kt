package com.aicompanion.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aicompanion.util.AppLogger
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ScreenRecognitionService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenRecognitionService"
        private var currentInstance: ScreenRecognitionService? = null
        private val _lastScreenText = AtomicReference("")
        private val _lastClickableData = AtomicReference<List<ClickableData>>(emptyList())
        @Volatile private var lastRefreshTime = 0L
        private const val REFRESH_THROTTLE_MS = 2000L
        private val bgExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "ScreenRefresh").also { it.isDaemon = true } }
        private val refreshPending = AtomicBoolean(false)

        fun getInstance(): ScreenRecognitionService? = currentInstance
        fun getLastScreenText(): String = _lastScreenText.get()
        fun getClickableData(): List<ClickableData> = _lastClickableData.get()

        fun performClick(text: String): Boolean {
            val service = currentInstance ?: return false
            val data = _lastClickableData.get().find {
                it.text.contains(text, ignoreCase = true) ||
                        it.desc.contains(text, ignoreCase = true)
            } ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                val node = findNodeByBounds(root, data.bounds)
                if (node != null && node !== root) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    return result
                } else if (node === root) {
                    return root.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return false
            } finally {
                root.recycle()
            }
        }

        fun performClickByIndex(index: Int): Boolean {
            val service = currentInstance ?: return false
            val clickables = _lastClickableData.get()
            if (index < 0 || index >= clickables.size) return false
            val data = clickables[index]
            val root = service.rootInActiveWindow ?: return false
            try {
                val node = findNodeByBounds(root, data.bounds)
                if (node != null && node !== root) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    return result
                } else if (node === root) {
                    return root.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                return false
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
                if (scrollable != null && scrollable !== root) {
                    val action = when (direction) {
                        "forward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                        "backward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
                        else -> null
                    }
                    val result = if (action != null) scrollable.performAction(action.id) else false
                    scrollable.recycle()
                    return result
                } else if (scrollable === root) {
                    val action = when (direction) {
                        "forward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                        "backward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
                        else -> null
                    }
                    return if (action != null) root.performAction(action.id) else false
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
                    if (child !== result) child.recycle()
                    return result
                }
                child.recycle()
            }
            return null
        }

        fun refreshScreenData(): Boolean {
            val service = currentInstance ?: return false
            if (refreshPending.getAndSet(true)) return true
            bgExecutor.execute {
                try {
                    val root = service.rootInActiveWindow ?: return@execute
                    try {
                        val text = extractText(root)
                        val clickables = extractClickableData(root)
                        _lastScreenText.set(text)
                        _lastClickableData.set(clickables)
                        lastRefreshTime = System.currentTimeMillis()
                    } finally {
                        root.recycle()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "refreshScreenData error: ${e.message}")
                } finally {
                    refreshPending.set(false)
                }
            }
            return true
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

        fun shutdown() {
            try { bgExecutor.shutdownNow() } catch (_: Exception) {}
        }
    }

    data class ClickableData(
        val text: String,
        val desc: String,
        val bounds: Rect
    )

    private var cachedPrefs: android.content.SharedPreferences? = null
    private var lastCategorySaveTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
        cachedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val packageName = event.packageName?.toString() ?: return
            val category = AppCategoryClassifier.classify(packageName)
            val now = System.currentTimeMillis()
            if (now - lastCategorySaveTime > 1000) {
                cachedPrefs?.edit()?.putString("current_app_category", category)?.apply()
                lastCategorySaveTime = now
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    refreshScreenData()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "onAccessibilityEvent error: ${e.message}")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        currentInstance = null
        super.onDestroy()
    }
}
