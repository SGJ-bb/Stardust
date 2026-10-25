/**
 * UI 树体检探针：回答"这个页面到底能不能取到树、缺什么"。
 *
 * 为什么要它：像微信这种自绘/混合渲染的 App，取不到内容时往往不是"无障碍坏了"，
 * 而是某个具体原因（活动窗口为空 / 文本挂在子节点 / 目标没有可点节点 / 内容在输入法窗口）。
 * 靠猜没用，先把数字打出来。
 */
package com.aicompanion.screen

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.aicompanion.util.AppLogger

object UiTreeProbe {

    private const val TAG = "UiTreeProbe"

    data class Report(
        val pkg: String,
        val activeRootNull: Boolean,
        val windowCount: Int,
        val nodes: Int,
        val textNodes: Int,
        val clickableNodes: Int,
        val imagesWithoutText: Int,
        val sampleTexts: List<String>,
        val sampleClickables: List<String>,
        val warnings: List<String>,
        val hints: List<String>,
        /** 逐窗口明细：id/类型/包名/节点数/文本数/可点数/样本 */
        val windowLines: List<String> = emptyList(),
    ) {
        fun toText(): String = buildString {
            appendLine("pkg=$pkg activeRootNull=$activeRootNull windows=$windowCount")
            appendLine("nodes=$nodes text=$textNodes clickable=$clickableNodes img_noText=$imagesWithoutText")
            windowLines.forEach { appendLine(it) }
            if (sampleTexts.isNotEmpty()) appendLine("texts: " + sampleTexts.joinToString(" | "))
            if (sampleClickables.isNotEmpty()) appendLine("clickable: " + sampleClickables.joinToString(" | "))
            warnings.forEach { appendLine("⚠ $it") }
            hints.forEach { appendLine("→ 建议：$it") }
        }.trim()
    }

    private fun wtypeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "应用"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "输入法"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "系统"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "无障碍浮层"
        else -> "type$type"
    }

    fun probe(service: ScreenRecognitionService, maxDepth: Int = 40): Report {
        val root = service.rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: ""

        var nodes = 0
        var textNodes = 0
        var clickables = 0
        var imagesNoText = 0
        val texts = ArrayList<String>()
        val clickLabels = ArrayList<String>()

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > maxDepth) return
            nodes++
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val hint = runCatching { node.hintText?.toString()?.trim().orEmpty() }.getOrDefault("")
            val label = text.ifBlank { desc.ifBlank { hint } }
            if (label.isNotBlank()) {
                textNodes++
                if (texts.size < 12) texts.add(label.take(24))
            }
            if (node.isClickable) {
                clickables++
                if (clickLabels.size < 10) clickLabels.add(
                    (label.ifBlank { node.viewIdResourceName ?: node.className?.toString() ?: "?" }).take(24)
                )
            }
            val cls = node.className?.toString().orEmpty()
            if (label.isBlank() && (cls.contains("Image") || cls.contains("View"))) imagesNoText++
            for (i in 0 until runCatching { node.childCount }.getOrDefault(0)) {
                walk(runCatching { node.getChild(i) }.getOrNull(), depth + 1)
            }
        }

        walk(root, 0)

        // 逐窗口明细：哪个窗口才有内容，一目了然（微信这种多窗口自绘最需要）
        val winLines = ArrayList<String>()
        var imeWindow = false
        var extraTextNodes = 0
        var windowCount = 0
        runCatching {
            for (w in service.windows) {
                windowCount++
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) imeWindow = true
                val r = w.root ?: run {
                    winLines.add("win#${w.id} type=${wtypeName(w.type)} 无 root")
                    return@runCatching
                }
                try {
                    var n = 0
                    var t = 0
                    var c = 0
                    val samples = ArrayList<String>()
                    fun count(node: AccessibilityNodeInfo?, d: Int) {
                        if (node == null || d > 20 || n > 600) return
                        n++
                        val label = node.text?.toString()?.trim()
                            ?: node.contentDescription?.toString()?.trim()
                        if (!label.isNullOrBlank()) {
                            t++
                            if (samples.size < 4) samples.add(label.take(14))
                        }
                        if (node.isClickable) c++
                        for (i in 0 until runCatching { node.childCount }.getOrDefault(0)) {
                            count(runCatching { node.getChild(i) }.getOrNull(), d + 1)
                        }
                    }
                    count(r, 0)
                    if (!w.isActive) extraTextNodes += t
                    winLines.add(
                        "win#${w.id}${if (w.isActive) "*" else " "} " +
                            "${wtypeName(w.type)} ${r.packageName ?: "?"} " +
                            "nodes=$n text=$t click=$c" +
                            if (samples.isEmpty()) "" else "  [" + samples.joinToString(" / ") + "]"
                    )
                } finally {
                    runCatching { r.recycle() }
                }
            }
        }

        val warnings = ArrayList<String>()
        val hints = ArrayList<String>()
        if (root != null && nodes <= 2 && extraTextNodes > 0) {
            warnings.add("活动窗口是空壳（仅 $nodes 个节点），真正内容在同应用的其他窗口")
            hints.add("已按多窗口合并处理；若仍空白，用截图 + 识别兜底")
        }
        if (root == null) {
            warnings.add("活动窗口为空（rootInActiveWindow=null）")
            hints.add("改用 service.windows 遍历；再不行用截图 + OCR + 坐标点击")
        }
        if (nodes > 0 && textNodes == 0) {
            warnings.add("有节点但一个文本都没有（典型自绘界面）")
            hints.add("用 contentDescription / hintText；再不行走截图识别")
        }
        if (nodes > 0 && textNodes * 10 < nodes) {
            warnings.add("文本节点占比很低（${textNodes}/${nodes}）")
            hints.add("点击目标可能只有图标，按位置或父节点定位")
        }
        if (clickables == 0 && nodes > 0) {
            warnings.add("没有任何可点节点")
            hints.add("用坐标点击（无障碍手势），或先 scroll 让节点进入可访问范围")
        }
        if (imeWindow) {
            hints.add("检测到输入法窗口：输入框内容常在其中，输入后要重取树确认")
        }
        if (extraTextNodes > 0) {
            hints.add("附加窗口里有 $extraTextNodes 个文本节点（对话框/弹窗），别只看活动窗口")
        }
        if (pkg == "com.tencent.mm") {
            hints.add("微信：聊天列表/朋友圈多为自绘，优先用 resource-id + 坐标混合定位")
            hints.add("微信输入框 setText 可能被拦；可用我们的 ADB 通道 input text 兜底")
        }

        val report = Report(
            pkg = pkg,
            activeRootNull = root == null,
            windowCount = windowCount,
            nodes = nodes,
            textNodes = textNodes,
            clickableNodes = clickables,
            imagesWithoutText = imagesNoText,
            sampleTexts = texts,
            sampleClickables = clickLabels,
            warnings = warnings,
            hints = hints,
            windowLines = winLines,
        )
        AppLogger.i(TAG, "体检结果\n${report.toText()}")
        runCatching { root?.recycle() }
        return report
    }
}
