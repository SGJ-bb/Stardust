package com.aicompanion.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
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

        /**
         * 滑动手势：从起点滑到终点
         * @param durationMs 滑动时长（短=快滑，长=慢滑），建议 200-500ms
         */
        fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
            val service = currentInstance ?: return false
            return try {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build()
                service.dispatchGesture(gesture, null, null)
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "performSwipe error: ${e.message}")
                false
            }
        }

        /** 长按手势（不移动坐标，持续指定时长） */
        fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean {
            val service = currentInstance ?: return false
            // ScreenRecognitionService 未持有 settingsManager，通过 SettingsManager(service) 读取配置
            val settings = com.aicompanion.settings.SettingsManager(service)
            if (!settings.longPressEnabled) {
                AppLogger.w(TAG, "长按手势已被禁用")
                return false
            }
            return try {
                val path = Path().apply {
                    moveTo(x, y)
                    // 不调用 lineTo，保持原地
                }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
                val gesture = GestureDescription.Builder()
                    .addStroke(stroke)
                    .build()
                service.dispatchGesture(gesture, null, null)
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "长按手势失败: ${e.message}")
                false
            }
        }

        /** 按文字找到元素后长按 */
        fun performLongPressByText(text: String, durationMs: Long): Boolean {
            val service = currentInstance ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                val nodes = findNodesByText(root, text)
                for (node in nodes) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val cx = bounds.centerX().toFloat()
                    val cy = bounds.centerY().toFloat()
                    return performLongPress(cx, cy, durationMs)
                }
                return false
            } finally {
                root.recycle()
            }
        }

        /** 递归查找所有 text/contentDescription 匹配的节点 */
        private fun findNodesByText(node: AccessibilityNodeInfo, target: String): List<AccessibilityNodeInfo> {
            val result = mutableListOf<AccessibilityNodeInfo>()
            findNodesByTextRecursive(node, target, result)
            return result
        }

        private fun findNodesByTextRecursive(node: AccessibilityNodeInfo, target: String, out: MutableList<AccessibilityNodeInfo>) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (text.contains(target, ignoreCase = true) || desc.contains(target, ignoreCase = true)) {
                out.add(node)
                return
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findNodesByTextRecursive(child, target, out)
            }
        }

        /**
         * 预设方向滑动（自动计算坐标，基于屏幕中心，滑动距离为短边的 30%）
         * @param direction "up"/"down"/"left"/"right"
         */
        fun performSwipeDirection(direction: String): Boolean {
            val service = currentInstance ?: return false
            val metrics = service.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()
            val centerX = w / 2f
            val centerY = h / 2f
            val offset = minOf(w, h) * 0.3f
            return when (direction) {
                "up" -> performSwipe(centerX, centerY + offset, centerX, centerY - offset, 400L)
                "down" -> performSwipe(centerX, centerY - offset, centerX, centerY + offset, 400L)
                "left" -> performSwipe(centerX + offset, centerY, centerX - offset, centerY, 400L)
                "right" -> performSwipe(centerX - offset, centerY, centerX + offset, centerY, 400L)
                else -> false
            }
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

        /**
         * 文本输入：找到目标输入框并填入内容
         * @param targetText 输入框的 text/contentDescription 匹配文本（空则用当前焦点节点）
         * @param content 要输入的内容
         * @return 是否成功
         */
        fun performInput(targetText: String, content: String): Boolean {
            val service = currentInstance ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                val targetNode = if (targetText.isBlank()) {
                    // 未指定目标：使用当前焦点节点
                    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                } else {
                    // 按文本匹配可编辑节点
                    findEditableNodeByText(root, targetText)
                } ?: return false

                return try {
                    // 1. 聚焦输入框
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    // 2. 填入内容
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            content
                        )
                    }
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } finally {
                    if (targetNode !== root) targetNode.recycle()
                }
            } finally {
                root.recycle()
            }
        }

        /** 递归查找可编辑的输入框节点（按 text/contentDescription 模糊匹配） */
        private fun findEditableNodeByText(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
            val isEditable = node.isEditable ||
                node.className?.toString()?.contains("EditText") == true
            if (isEditable) {
                val nodeText = listOfNotNull(
                    node.text?.toString(),
                    node.contentDescription?.toString()
                ).joinToString(" ")
                if (nodeText.contains(target, ignoreCase = true)) return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findEditableNodeByText(child, target)
                if (result != null) {
                    if (child !== result) child.recycle()
                    return result
                }
                child.recycle()
            }
            return null
        }

        /**
         * 查找所有文本匹配的可点击元素（歧义消除用）
         * @return 匹配的 ClickableData 列表，供调用方用 index 精确点击
         */
        fun findClickCandidates(text: String): List<ClickableData> {
            val clickables = _lastClickableData.get()
            if (text.isBlank()) return emptyList()
            return clickables.filter { data ->
                data.text.contains(text, ignoreCase = true) ||
                    data.desc.contains(text, ignoreCase = true)
            }
        }

        fun refreshScreenData(): Boolean {
            val service = currentInstance ?: return false
            if (refreshPending.getAndSet(true)) return true
            bgExecutor.execute {
                try {
                    val root = service.rootInActiveWindow ?: run {
                        // 首次为 null 时延迟 300ms 重试一次（窗口切换瞬间可能未就绪）
                        Thread.sleep(300)
                        service.rootInActiveWindow
                    } ?: return@execute
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
    // 事件节流：不同事件类型用不同节流窗口，避免高频回调卡顿
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
        cachedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
    }

    /**
     * 去抖刷新：取消旧任务，延迟 throttleMs 后刷新屏幕数据
     * 用于高频事件（VIEW_TEXT_CHANGED/VIEW_CLICKED）的节流
     */
    private fun scheduleRefresh(throttleMs: Long) {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        val runnable = Runnable { refreshScreenData() }
        refreshRunnable = runnable
        refreshHandler.postDelayed(runnable, throttleMs)
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

            // 按事件类型差异化处理：窗口切换立即刷新，点击/文本变化节流刷新
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // 窗口切换：立即刷新（页面切换需要及时感知）
                    refreshScreenData()
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    // 点击事件：300ms 节流（快速连点时只刷新最后一次）
                    scheduleRefresh(300L)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // 文本变化：500ms 节流（输入框打字时高频触发，需强节流）
                    scheduleRefresh(500L)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "onAccessibilityEvent error: ${e.message}")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
        currentInstance = null
        super.onDestroy()
    }
}
