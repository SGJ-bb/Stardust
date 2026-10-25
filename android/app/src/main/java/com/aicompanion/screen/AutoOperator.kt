/** 自动操作器: 通过Android辅助功能实现自动点击/滑动/输入文本等屏幕操作 */
package com.aicompanion.screen

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.aicompanion.screen.ScreenRecognitionService.Companion.performClick
import com.aicompanion.screen.ScreenRecognitionService.Companion.performClickByIndex
import com.aicompanion.screen.ScreenRecognitionService.Companion.performGlobalAction
import com.aicompanion.screen.ScreenRecognitionService.Companion.performInput
import com.aicompanion.screen.ScreenRecognitionService.Companion.performScroll
import com.aicompanion.screen.ScreenRecognitionService.Companion.performSwipe
import com.aicompanion.screen.ScreenRecognitionService.Companion.performSwipeDirection
import com.aicompanion.screen.ScreenRecognitionService.Companion.performTapAt

data class AutoAction(
    val type: String,
    val text: String = "",
    val index: Int = -1,
    /**
     * 目标寻址。
     * - click/click 类：填树视图的稳定 id（形如 "n14"），点击优先走它，其次才走 text/index
     * - input 类：目标输入框的 text/contentDescription 标识（空则用当前焦点）
     */
    val target: String = "",
    val direction: String = "forward",
    val durationMs: Int = 2000,
    /** input 动作：要输入的文本内容 */
    val content: String = "",
    /** swipe / tap 动作：自定义坐标 */
    val startX: Float = -1f,
    val startY: Float = -1f,
    val endX: Float = -1f,
    val endY: Float = -1f,
    /** swipe 动作时长（ms） */
    val swipeDuration: Long = 400L,
    /**
     * 步骤断言：本动作执行后必须成立的条件，不成立则该步判为失败。
     *
     * 为什么需要它：底层 performClick 返回 true 只代表「事件发出去了」，
     * 不代表「点对了、页面确实变了」。没有断言时，一堆点歪的操作会被算成成功，
     * LLM 拿到「7/7 成功」的假反馈后继续基于错误前提规划。
     *
     * 语法：多个条件用逗号或分号分隔，需全部满足。支持前缀：
     *   出现:文字      屏幕上必须出现该文字
     *   消失:文字      该文字必须消失
     *   包名:com.xxx   当前前台包名需包含该串
     *   变化           屏幕内容或前台包名至少有一项变化
     * 不带前缀的纯文字等价于「出现:文字」。
     */
    val expect: String = "",
)

/** 操作执行结果（含失败原因，供 LLM 反馈） */
data class ActionResult(
    val success: Boolean,
    val errorMsg: String = ""
)

/** 一批动作的执行结果，以及「是否发生了页面级变化」 */
data class BatchResult(
    val lines: List<String>,
    val successCount: Int,
    val pageChanged: Boolean
) {
    val allSuccess: Boolean get() = successCount == lines.size
}

class AutoOperator {

    companion object {
        private const val TAG = "AutoOperator"

        fun isServiceReady(): Boolean {
            return ScreenRecognitionService.getInstance() != null
        }

        /** 当前前台包名（用于检测页面跳转） */
        fun currentPackage(): String = ScreenRecognitionService.getCurrentPackageName()

        /** 断言用的屏幕快照 */
        data class ScreenSnapshot(val pkg: String, val text: String)

        private fun snapshot(): ScreenSnapshot =
            ScreenSnapshot(currentPackage(), ScreenRecognitionService.getLastScreenText())

        /**
         * 校验步骤断言。返回 null 表示通过，非 null 为失败原因（会回喂给 LLM）。
         */
        private fun evalExpect(
            expect: String,
            before: ScreenSnapshot,
            after: ScreenSnapshot
        ): String? {
            val clauses = expect.split(Regex("[,，;；]"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (clauses.isEmpty()) return null

            for (clause in clauses) {
                val sep = clause.indexOfFirst { it == ':' || it == '：' }
                val kind: String
                val arg: String
                if (sep > 0) {
                    kind = clause.substring(0, sep).trim()
                    arg = clause.substring(sep + 1).trim()
                } else if (clause == "变化" || clause.equals("changed", ignoreCase = true)) {
                    kind = "变化"
                    arg = ""
                } else {
                    kind = "出现"
                    arg = clause
                }

                val passed = when (kind) {
                    "出现", "visible", "show", "contains", "text" ->
                        arg.isNotBlank() && after.text.contains(arg)
                    "消失", "gone", "absent", "invisible" ->
                        arg.isNotBlank() && !after.text.contains(arg)
                    "包名", "pkg", "package" ->
                        arg.isNotBlank() && after.pkg.contains(arg, ignoreCase = true)
                    "变化", "changed" ->
                        after.text != before.text || after.pkg != before.pkg
                    else -> arg.isNotBlank() && after.text.contains(arg)
                }

                if (!passed) {
                    val detail = when (kind) {
                        "出现", "visible", "show", "contains", "text" ->
                            "屏幕上没有出现「${arg}」"
                        "消失", "gone", "absent", "invisible" ->
                            "「${arg}」仍然在屏幕上"
                        "包名", "pkg", "package" ->
                            "当前前台包名是 ${after.pkg.ifBlank { "(未知)" }}"
                        "变化", "changed" ->
                            "屏幕内容与前台包名都没有变化"
                        else -> "条件不成立"
                    }
                    return "断言未通过（期望「${clause}」，实际${detail}）"
                }
            }
            return null
        }

        /**
         * 读取当前屏幕缓存文字。
         * 注意：这是「缓存」，需要最新数据请先调用 refreshAndWait。
         */
        fun readScreenText(): String {
            val service = ScreenRecognitionService.getInstance()
            if (service == null) {
                Log.d(TAG, "AccessibilityService not connected")
                return "(无障碍服务未连接)"
            }
            var screenText = ScreenRecognitionService.getLastScreenText()
            if (screenText.isBlank()) {
                // 缓存为空：同步刷新一次
                ScreenRecognitionService.refreshAndWait(1200L)
                screenText = ScreenRecognitionService.getLastScreenText()
            }
            return screenText.ifBlank { "(未检测到屏幕内容)" }
        }

        fun executeAction(action: AutoAction): ActionResult {
            // 有断言需求时，动作执行前先拍一张快照，供执行后比对
            val before = if (action.expect.isNotBlank()) snapshot() else null

            val result = when (action.type) {
                "click" -> {
                    val err = executeClick(action)
                    ActionResult(err == null, err ?: "")
                }
                "tap" -> {
                    if (action.startX >= 0 && action.startY >= 0) {
                        ActionResult(performTapAt(action.startX, action.startY))
                    } else {
                        ActionResult(false, "tap 需要 start_x/start_y 坐标")
                    }
                }
                "back" -> ActionResult(performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                "home" -> ActionResult(performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
                "scroll" -> ActionResult(performScroll(action.direction))
                "swipe" -> ActionResult(executeSwipe(action))
                "input" -> {
                    val ok = performInput(action.target, action.content)
                    ActionResult(ok, if (!ok) "未找到输入框: ${action.target}" else "")
                }
                "long_press" -> executeLongPress(action)
                "set_checked" -> {
                    val idx = resolveClickTarget(action)
                    val ok = if (idx >= 0) ScreenRecognitionService.performSetChecked(idx, action.text == "true" || action.text == "1")
                    else false
                    ActionResult(ok, if (!ok) "未找到要切换的选项（set_checked 需 target:id 或 index 或 text）" else "")
                }
                "notifications" -> ActionResult(performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
                "recents" -> ActionResult(performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS))
                "read" -> {
                    // 强制重读屏幕（等待新快照落地）
                    ScreenRecognitionService.refreshAndWait(1500L)
                    ActionResult(true)
                }
                "wait" -> {
                    val safeDuration = action.durationMs.coerceIn(100, 10000)
                    try { Thread.sleep(safeDuration.toLong()) } catch (_: Exception) {}
                    ActionResult(true)
                }
                else -> ActionResult(false, "未知动作类型: ${action.type}")
            }
            // 页面切换类动作：等页面变化信号（最多 1.2s，页面一变立刻继续）
            if (action.type != "wait" && action.type != "read" && result.success) {
                when (action.type) {
                    "click", "tap", "swipe", "back", "home", "notifications", "recents", "long_press", "set_checked" -> {
                        ScreenRecognitionService.waitForScreenChange(1200L)
                    }
                    else -> { try { Thread.sleep(50) } catch (_: Exception) {} }
                }
            }

            // 步骤断言：事件成功发出 ≠ 这一步真的达成了目的。
            // 没有这一层校验，"点歪了"会被算成成功，LLM 会基于假反馈继续往下走。
            if (before != null && result.success) {
                val err = evalExpect(action.expect, before, snapshot())
                if (err != null) return ActionResult(false, err)
            }
            return result
        }

        /**
         * 批量执行动作（反应式循环用）。
         * 执行中若检测到页面级变化（前台包名变了），停止执行剩余动作并返回 pageChanged=true，
         * 让上层重新观察屏幕后再规划 —— 这是「点进 App 后看不懂新页面」的核心修复。
         *
         * @param cooldownMs 动作间冷却
         * @param observeOnPageChange 是否在页面变化时中断
         * @return BatchResult（含每一步的执行反馈文本）
         */
        fun executeBatch(
            actions: List<AutoAction>,
            cooldownMs: Int = 200,
            observeOnPageChange: Boolean = true
        ): BatchResult {
            val lines = mutableListOf<String>()
            var successCount = 0
            var pageChanged = false
            val startPkg = currentPackage()
            val total = actions.size

            for ((index, action) in actions.withIndex()) {
                val result = executeAction(action)
                if (result.success) {
                    successCount++
                    lines.add("[${index + 1}/$total] ${action.type} 成功")
                } else {
                    lines.add("[${index + 1}/$total] ${action.type} 失败: ${result.errorMsg}")
                    // 断言失败意味着前提已经错了（事件点出去但没达到目的）。
                    // 此时继续按原计划走只会把错误放大，立即停下重新观察更划算。
                    if (result.errorMsg.startsWith("断言未通过") && index < total - 1) {
                        lines.add("↳ 该步未达成预期，剩余 ${total - index - 1} 步已跳过，请重新观察屏幕后再规划")
                        break
                    }
                }

                val pkgNow = currentPackage()
                if (observeOnPageChange && pkgNow.isNotBlank() && pkgNow != startPkg) {
                    pageChanged = true
                    if (index < total - 1) {
                        lines.add("↳ 检测到切换应用/页面($pkgNow)，暂停剩余 ${total - index - 1} 步以重新观察屏幕")
                    }
                    break
                }
                if (cooldownMs > 0 && index < total - 1) {
                    try { Thread.sleep(cooldownMs.toLong()) } catch (_: Exception) {}
                }
            }
            return BatchResult(lines, successCount, pageChanged)
        }

        /** 长按动作：按文字或坐标执行长按手势 */
        private fun executeLongPress(action: AutoAction): ActionResult {
            if (action.text.isNotBlank()) {
                val ok = ScreenRecognitionService.performLongPressByText(action.text, action.durationMs.toLong().coerceAtLeast(500))
                return ActionResult(ok, if (!ok) "未找到长按目标: ${action.text}" else "")
            }
            if (action.startX >= 0 && action.startY >= 0) {
                val ok = ScreenRecognitionService.performLongPress(action.startX, action.startY, action.durationMs.toLong().coerceAtLeast(500))
                return ActionResult(ok)
            }
            return ActionResult(false, "长按需要 text 或坐标")
        }

        /** 执行点击：返回 null 表示成功，非空为错误信息（供 LLM 反馈调整） */
        private fun executeClick(action: AutoAction): String? {
            // 树视图模式：优先用稳定 id（target:"n14"），跨快照最可靠
            val intId = resolveClickTarget(action)
            if (intId >= 0) {
                return if (performClickByIndex(intId)) null
                else "点击失败: target=${action.target}（元素已失效，请重新 read 读屏后取最新 id）"
            }
            if (action.target.startsWith("n") && action.index < 0 && action.text.isBlank()) {
                return "未找到元素 target=${action.target}。当前快照共 ${ScreenRecognitionService.getClickableData().size} 个可点击元素，" +
                    "元素可能已变化，请重新 read 读屏。"
            }
            if (action.index >= 0) {
                return if (performClickByIndex(action.index)) null
                else "索引越界或点击失败: index=${action.index}（当前快照共 ${ScreenRecognitionService.getClickableData().size} 个可点击元素）"
            }
            if (action.text.isNotBlank()) {
                val located = ScreenRecognitionService.findClickCandidatesMulti(action.text)
                val candidates = located.candidates
                return when {
                    candidates.isEmpty() ->
                        "未找到可点击元素: ${action.text}。已按「资源ID / 文字 / 描述 / 类名」多路匹配，" +
                            "当前快照共 ${ScreenRecognitionService.getClickableData().size} 个可点击元素。" +
                            "可先 read 重新读屏，或改用 index / 坐标 tap"
                    candidates.size > 1 ->
                        "找到 ${candidates.size} 个匹配「${action.text}」的元素（命中方式：${located.strategy}），请用 index 精确指定"
                    else -> {
                        val ok = ScreenRecognitionService.performClickData(candidates.first())
                        if (ok) null
                        else "点击失败: ${action.text}（命中方式：${located.strategy}）"
                    }
                }
            }
            return "click 动作需要 index、target:id 或 text 参数"
        }

        /** 归一化目标定位：优先 id("n14")，其次 index，返回可点击下标（找不到返回 -1） */
        private fun resolveClickTarget(action: AutoAction): Int {
            if (action.target.startsWith("n")) {
                return ScreenRecognitionService.resolveClickIndexById(action.target)
            }
            if (action.index >= 0 && action.index < ScreenRecognitionService.getClickableData().size) {
                return action.index
            }
            return -1
        }

        private fun executeSwipe(action: AutoAction): Boolean {
            return if (action.startX >= 0 && action.endX >= 0) {
                performSwipe(
                    action.startX, action.startY,
                    action.endX, action.endY,
                    action.swipeDuration
                )
            } else {
                performSwipeDirection(action.direction)
            }
        }

        fun parseActionsFromLLM(llmResponse: String): List<AutoAction> {
            val actions = mutableListOf<AutoAction>()
            try {
                val cleanJson = llmResponse.trim()
                    .replace(Regex("^```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("```\\s*$"), "")
                    .trim()
                if (!cleanJson.startsWith("[")) return actions

                val arr = org.json.JSONArray(cleanJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val type = obj.optString("action", obj.optString("type", ""))
                    val action = when (type) {
                        "click" -> AutoAction(
                            type = "click",
                            text = obj.optString("text", ""),
                            index = obj.optInt("index", -1),
                            target = obj.optString("target", "")
                        )
                        "set_checked" -> AutoAction(
                            type = "set_checked",
                            text = obj.optString("checked", obj.optString("state", "true")),
                            index = obj.optInt("index", -1),
                            target = obj.optString("target", "")
                        )
                        "tap" -> AutoAction(
                            type = "tap",
                            startX = obj.optDouble("x", obj.optDouble("start_x", -1.0)).toFloat(),
                            startY = obj.optDouble("y", obj.optDouble("start_y", -1.0)).toFloat()
                        )
                        "back" -> AutoAction(type = "back")
                        "home" -> AutoAction(type = "home")
                        "read" -> AutoAction(type = "read")
                        "scroll" -> AutoAction(
                            type = "scroll",
                            direction = obj.optString("direction", "forward")
                        )
                        "swipe" -> AutoAction(
                            type = "swipe",
                            direction = obj.optString("direction", "up"),
                            startX = obj.optDouble("start_x", -1.0).toFloat(),
                            startY = obj.optDouble("start_y", -1.0).toFloat(),
                            endX = obj.optDouble("end_x", -1.0).toFloat(),
                            endY = obj.optDouble("end_y", -1.0).toFloat(),
                            swipeDuration = obj.optLong("duration", 400L)
                        )
                        "input" -> AutoAction(
                            type = "input",
                            target = obj.optString("target", ""),
                            content = obj.optString("text", obj.optString("content", ""))
                        )
                        "long_press" -> AutoAction(
                            type = "long_press",
                            text = obj.optString("text", ""),
                            startX = obj.optDouble("start_x", -1.0).toFloat(),
                            startY = obj.optDouble("start_y", -1.0).toFloat(),
                            durationMs = obj.optInt("duration", 800)
                        )
                        "wait" -> AutoAction(
                            type = "wait",
                            durationMs = obj.optInt("ms", 2000)
                        )
                        "notifications" -> AutoAction(type = "notifications")
                        "recents" -> AutoAction(type = "recents")
                        else -> null
                    }
                    if (action != null) {
                        // expect 是通用字段，各动作类型都支持，这里统一附加
                        val expect = obj.optString("expect", "").trim()
                        actions.add(if (expect.isBlank()) action else action.copy(expect = expect))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "parseActionsFromLLM failed: ${e.message}")
            }
            return actions
        }

        /**
         * 屏幕信息（结构化树，深度/元素上限来自设置）：默认用此版本。
         *
         * 关键修复：同步等待采集完成后再读取。
         * 旧实现 refreshScreenData() 是异步提交、立即返回缓存，
         * 于是规划器拿到的常常是「上一个页面」的内容 —— 表现为
         * 「点进 App 后拿不到内容 / 找不到可点击元素」。
         */
        fun formatScreenForLLM(): String {
            // 单轮"看→想→点"可能几十秒，先续期屏幕，避免模型还在想、屏幕已经睡了
            ScreenRecognitionService.keepAwakeForAWhile()
            val ok = ScreenRecognitionService.refreshAndWait(2000L, fullMode = false)
            if (!ok && ScreenRecognitionService.getLastScreenText().isBlank()) {
                return buildScreenBlock("(屏幕数据读取失败，请确认无障碍服务已开启)")
            }
            if (ScreenRecognitionService.getClickableData().size < 5) {
                ScreenRecognitionService.refreshAndWait(2500L, fullMode = true)
            }
            // 退化快照（有窗口但读不到文本/可点元素）→ 自动 OCR 兜底。
            // 微信这类不上报无障碍树的 App 只能走这条路，否则模型看到的是空白页。
            if (ScreenRecognitionService.isSnapshotDegenerate()) {
                val ocr = ScreenRecognitionService.refreshByOcr()
                val ocrUsable = ocr.isNotBlank() &&
                    !ocr.startsWith("(截图失败") && !ocr.startsWith("(OCR 兜底不可用")
                // OCR 认不出东西（黑屏/纯图标/无文字）→ 第三级：让视觉模型给坐标
                if (!ocrUsable || ScreenRecognitionService.lastOcrCount < 3) {
                    val vis = ScreenRecognitionService.refreshByVision()
                    if (vis.isNotBlank() && !vis.startsWith("(视觉模型未返回") &&
                        !vis.startsWith("(视觉兜底不可用") && !vis.startsWith("(截图失败")
                    ) {
                        return buildScreenBlock(vis)
                    }
                }
                if (ocrUsable) return buildScreenBlock(ocr)
                return buildScreenBlock(
                    readScreenText() + "\n(该页面不上报无障碍树，OCR 与视觉兜底都没拿到内容)"
                )
            }
            return buildScreenBlock(readScreenText())
        }

        /** 完整版屏幕信息（放开深度与数量限制）：分级回退兜底用 */
        fun formatScreenForLLMFull(): String {
            ScreenRecognitionService.refreshAndWait(2500L, fullMode = true)
            return buildScreenBlock(readScreenText())
        }

        /**
         * 树视图版本：把结构化 UI 树（带稳定 id / pid 层级 / role / bounds）喂给 LLM。
         * 这是"让 AI 真正看到 UI 树"的主形态：层级关系显式、元素有稳定 id 可被引用。
         */
        fun formatScreenTreeForLLM(): String {
            ScreenRecognitionService.keepAwakeForAWhile()
            // 一律走全量采集。浅采集是 depth=8，而微信会话名在 depth 23，
            // 实测浅采集 46 个节点里没有任何联系人名，全量能到 401 个。
            // 注意超时不能给 2.5s：全量遍历 400+ 节点来不及，超时后拿到的还是上一次
            // 浅采集的缓存（表现为 nodeCount 有值但最大 depth 只有 9）。
            val ok = ScreenRecognitionService.refreshAndWait(5000L, fullMode = true)
            if (!ok && ScreenRecognitionService.getLastScreenText().isBlank()) {
                return "当前屏幕 UI 树：\n(屏幕数据读取失败，请确认无障碍服务已开启)"
            }
            // 浅采集（默认 depth=8 / 150 元素）可能只拿到状态栏 —— 微信这类层级深的 App
            // 实测只能出 19 个节点，而全量能出 298 个。可点元素太少就自动加深度重采一次。
            // 浅采集（默认 depth=8 / 150 元素）会漏内容：微信列表项挂在 depth 9 以外，
            // 实测浅采集 47 个节点里一个联系人名都没有，全量能到 298 个。
            // 所以判据不能只看"可点元素够不够"，文字节点太少也要重采。
            if (ScreenRecognitionService.getClickableData().size < 5 ||
                ScreenRecognitionService.textNodeCount() < 8
            ) {
                ScreenRecognitionService.refreshAndWait(2500L, fullMode = true)
            }
            if (ScreenRecognitionService.isSnapshotDegenerate()) {
                val ocr = ScreenRecognitionService.refreshByOcr()
                val ocrUsable = ocr.isNotBlank() &&
                    !ocr.startsWith("(截图失败") && !ocr.startsWith("(OCR 兜底不可用")
                if (!ocrUsable || ScreenRecognitionService.lastOcrCount < 3) {
                    val vis = ScreenRecognitionService.refreshByVision()
                    if (vis.isNotBlank() && !vis.startsWith("(视觉模型未返回") &&
                        !vis.startsWith("(视觉兜底不可用") && !vis.startsWith("(截图失败")
                    ) {
                        return "当前屏幕（视觉模型定位，该 App 不上报 UI 树）：\n" + vis
                    }
                }
                if (ocrUsable) return "当前屏幕（OCR 兜底，该 App 不上报 UI 树）：\n" + ocr
            }
            return "当前屏幕 UI 树（JSON）：\n" + ScreenRecognitionService.getUiTreeJson()
        }

        /** 完整版树视图（放开深度/数量上限） */
        fun formatScreenTreeForLLMFull(): String {
            ScreenRecognitionService.refreshAndWait(2500L, fullMode = true)
            return "当前屏幕 UI 树（JSON，完整版）：\n" + ScreenRecognitionService.getUiTreeJson(maxNodes = 500)
        }

        private fun buildScreenBlock(screenText: String): String {
            return "当前屏幕内容：\n$screenText"
        }

        fun formatScreenForLLM(localAnalysis: com.aicompanion.localmodel.ScreenAnalysisResult?): String {
            val sb = StringBuilder()
            sb.append(formatScreenForLLM())
            if (localAnalysis != null) {
                val visionBlock = localAnalysis.toContextBlock()
                if (visionBlock.isNotBlank()) {
                    sb.appendLine()
                    sb.appendLine("=== 视觉识别 ===")
                    sb.appendLine(visionBlock)
                }
            }
            return sb.toString()
        }
    }
}
