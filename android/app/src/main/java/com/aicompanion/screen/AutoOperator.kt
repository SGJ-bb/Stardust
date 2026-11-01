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
        data class ScreenSnapshot(val pkg: String, val text: String, val inputText: String = "")

        private fun snapshot(): ScreenSnapshot = ScreenSnapshot(
            currentPackage(),
            ScreenRecognitionService.getLastScreenText(),
            // 输入框当前内容：发送类动作只能靠它判断成败——
            // 发出去的消息文本仍然留在聊天记录里，用「文字消失」必然误判失败（实测微信）。
            ScreenRecognitionService.currentInputText()
        )

        /**
         * 断言前重新采一次屏幕。
         *
         * 必须是全量（fullMode）而不是浅采集：浅采集 depth=8，而 B 站点赞按钮在 depth 11，
         * 状态文字「已点赞」根本进不了浅快照，断言必然判失败。
         * 超时给 2 秒——全量遍历几百个节点大约 1~1.5 秒。
         */
        private fun refreshForExpect() {
            runCatching { ScreenRecognitionService.refreshAndWait(2000L, fullMode = true) }
        }

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
                } else if (clause in setOf("输入框为空", "输入框已清空", "输入框清空") ||
                    clause.equals("input_empty", ignoreCase = true)
                ) {
                    // 无冒号写法，必须在这里拦下：否则会掉进下面的默认分支被当成「出现:输入框为空」
                    kind = "输入框为空"
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
                    "输入框为空", "input_empty", "inputempty", "输入框已清空", "输入框清空" ->
                        after.inputText.isBlank()
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
                        "输入框为空", "input_empty", "inputempty", "输入框已清空", "输入框清空" ->
                            "输入框里还留着「${after.inputText.take(20)}」"
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

        /**
         * 按包名启动应用。用 PackageManager 拿启动 Intent，不需要 shell 权限。
         * 找不到包（或没有启动入口）返回 false，让模型退回"桌面点图标"那条路。
         */
        private fun launchAppByPackage(pkg: String): Boolean {
            if (pkg.isBlank()) return false
            return try {
                val ctx = ScreenRecognitionService.getInstance() ?: return false
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
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
                // 直接按包名启动。比"回桌面点图标"可靠得多：桌面图标可能在别的屏、
                // 被文件夹收着、或者名字被截断，实测模型会连点错三四个 App
                // （误开夸克、Termux、应用市场）。包名放在 text 字段里以避免改数据类。
                "launch" -> {
                    val pkg = action.text.ifBlank { action.target }.trim()
                    val ok = launchAppByPackage(pkg)
                    // 启动 App 要 1~3 秒，而断言的校验紧跟在这行之后就跑了
                    // （之前实测：launch 成功、断言却报"当前是天气App"，因为 B站还没拉起来
                    // 就把这一步判成失败，模型于是以为启动失败，转头去翻桌面）。
                    // 这里等一下，让断言有时间看到真实的包名。
                    if (ok) try { Thread.sleep(1800) } catch (_: InterruptedException) {}
                    ActionResult(ok, if (ok) "" else "未找到应用：$pkg（包名可能不对）")
                }
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
                // 校验前等一下：点击→页面切换/内容刷新有 300~800ms 的延迟，
                // 立刻比对会把"已经点成功、页面正在切"判成失败
                // （实测：点 B站搜索栏 → 断言"没有变化" → 模型以为没点着，重复点）。
                try { Thread.sleep(700) } catch (_: Exception) {}
                // 断言必须基于**刚采的**内容，不能用缓存。
                // 之前这里直接读 getLastScreenText()，而它是上一次采集的结果——
                // 点了点赞、状态明明变成「已点赞」，断言却拿旧文本比对，判成失败
                // （实测 B站点赞：实际已点赞成功，AI 被告知"没出现已点赞"，于是重复点）。
                // 而且浅采集 depth=8，点赞按钮在 depth 11，本来就读不到，必须走全量。
                // 先用当前缓存比一次：大多数"出现:xxx"靠这步就过了，省掉一次全量采集（约 1~1.5 秒）。
                // 不通过再花钱重采，避免每个动作都无条件付这个成本。
                var err = evalExpect(action.expect, before, snapshot())
                if (err != null) {
                    refreshForExpect()
                    err = evalExpect(action.expect, before, snapshot())
                }
                if (err != null) {
                    // 二次复查：0.7 秒对"重页面"不够——B 站搜索页点下按钮要 1~2 秒才刷出结果，
                    // 网易云、淘宝这类也一样。一次不通过就判死会让模型以为没点着、重复点或者换招，
                    // 越走越偏。所以再等 1.5 秒复查一轮，只有两轮都不满足才算失败。
                    //
                    // 为什么不做成全局提高延迟：那会让每个动作都慢 1.5 秒，
                    // 十几步下来多花二十秒，而且快操作（点开关、翻页）本来就不需要等。
                    try { Thread.sleep(1500) } catch (_: Exception) {}
                    refreshForExpect()
                    err = evalExpect(action.expect, before, snapshot())
                }
                if (err != null) {
                    // 最后一招：performAction 报成功但状态没变 —— 不少自绘控件只认真实触摸
                    // （实测 QQ 聊天页的「发送」按钮：无障碍 click 返回 true，消息却没发出去）。
                    // 用记录的落点补一记手势，再判一次；成功就当这一步过。
                    val c = lastClickCenter
                    if (c != null && action.type == "click") {
                        runCatching { ScreenRecognitionService.performTapAt(c.x, c.y) }
                        try { Thread.sleep(900) } catch (_: Exception) {}
                        refreshForExpect()
                        val err2 = evalExpect(action.expect, before, snapshot())
                        if (err2 == null) return result
                    }
                    return ActionResult(false, err)
                }
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
        /** 最近一次点击落点，用于「点了但状态没变」时补一记真实手势 */
        private var lastClickCenter: android.graphics.PointF? = null

        private fun executeClick(action: AutoAction): String? {
            // 快照互不相同的坑：模型看到的是 formatScreenTreeForLLM() 的树，
            // 而这里按 index 点击查的是 ScreenRecognitionService 的内部缓存。
            // 缓存可能因为上一轮采集超时/被清空而为空 —— 这时再点必然报
            // 「当前快照共 0 个可点击元素」，模型就此陷入反复失败的循环（实测卡了很多轮）。
            // 所以点之前先确认缓存是活的，空了就当场补采一次。
            if (ScreenRecognitionService.getClickableData().isEmpty() ||
                ScreenRecognitionService.getUiTree().isEmpty()
            ) {
                ScreenRecognitionService.refreshAndWait(3000L, fullMode = true)
            }
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
                runCatching {
                    val d = ScreenRecognitionService.getClickableData().getOrNull(action.index)
                    if (d != null) lastClickCenter = android.graphics.PointF(
                        d.bounds.centerX().toFloat(), d.bounds.centerY().toFloat()
                    )
                }
                if (performClickByIndex(action.index)) return null
                // 索引失配通常是快照过期（页面刚刷新、列表滚动、元素重排）。
                // 无脑报错会让模型陷入「同一个 index 反复失败」的死循环，
                // 所以这里补采一次快照、按同样的 index 再试一次。
                ScreenRecognitionService.refreshAndWait(2500L, fullMode = true)
                if (performClickByIndex(action.index)) return null
                return "索引越界或点击失败: index=${action.index}（补采后仍有 ${ScreenRecognitionService.getClickableData().size} 个可点击元素）"
            }
            if (action.text.isNotBlank()) {
                var located = ScreenRecognitionService.findClickCandidatesMulti(action.text)
                var candidates = located.candidates
                if (candidates.isEmpty()) {
                    // 找不到就再采一次再说"找不到"。
                    // 层级深的 App（QQ 的聊天页）采集容易超时，缓存里可能只剩 1 个元素，
                    // 于是屏幕上明明有「发送」按钮，却报"未找到可点击元素"（实测 QQ 第二次踩到）。
                    // 补采一次的成本约 1~2 秒，比让模型误以为按钮不存在、开始乱点划算得多。
                    ScreenRecognitionService.refreshAndWait(3000L, fullMode = true)
                    located = ScreenRecognitionService.findClickCandidatesMulti(action.text)
                    candidates = located.candidates
                }
                return when {
                    candidates.isEmpty() ->
                        "未找到可点击元素: ${action.text}。已按「资源ID / 文字 / 描述 / 类名」多路匹配，" +
                            "当前快照共 ${ScreenRecognitionService.getClickableData().size} 个可点击元素。" +
                            "可先 read 重新读屏，或改用 index / 坐标 tap"
                    candidates.size > 1 ->
                        "找到 ${candidates.size} 个匹配「${action.text}」的元素（命中方式：${located.strategy}），请用 index 精确指定"
                    else -> {
                        val first = candidates.first()
                        lastClickCenter = android.graphics.PointF(
                            first.bounds.centerX().toFloat(),
                            first.bounds.centerY().toFloat()
                        )
                        val ok = ScreenRecognitionService.performClickData(first)
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
                        // 按包名启动应用：包名可能写在 package / text / target 任一字段里
                        "launch", "open_app", "app_start" -> AutoAction(
                            type = "launch",
                            text = obj.optString(
                                "package",
                                obj.optString("pkg", obj.optString("text", obj.optString("target", "")))
                            )
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
            // 自绘列表补采：树里只有一排空行、屏幕上明明有字
            // （QQ 的联系人好友列表、微信朋友圈是 Canvas 画的，uiautomator / TalkBack 也读不到，
            //   不是我们伪装不够）。OCR 出来的每一行会合并成可点元素，模型既能读也能点。
            val enriched = ScreenRecognitionService.enrichWithOcrIfNeeded()
            val json = ScreenRecognitionService.getUiTreeJson()
            if (enriched > 0) {
                val extra = ScreenRecognitionService.getLastScreenText()
                    .substringAfter("[截图识别补充]", "")
                    .trim()
                return "当前屏幕 UI 树（JSON）：\n" + json +
                    "\n\n=== 截图识别补充（这些文字无障碍树里没有，点它们等于点对应区域）===\n" + extra
            }
            return "当前屏幕 UI 树（JSON）：\n" + json
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
