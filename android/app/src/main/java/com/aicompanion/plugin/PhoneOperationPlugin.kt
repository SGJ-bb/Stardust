package com.aicompanion.plugin

import android.content.Context
import com.aicompanion.localmodel.LocalModelManager
import com.aicompanion.models.ToolDefinition
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.screen.AppSkillManager
import com.aicompanion.screen.AutoAction
import com.aicompanion.screen.AutoOperator
import com.aicompanion.screen.ScreenRecognitionService
import com.aicompanion.skill.SkillCreator
import com.aicompanion.skill.SkillStore
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 手机操作工具插件
 *
 * v2 核心变化：从「一次规划 → 盲执行整段动作」改为「观察 → 决策 → 行动 → 再观察」闭环。
 *
 * 旧行为的致命问题：
 *   规划只在开始时读一次屏幕（而且读到的还可能是异步刷新的旧缓存），
 *   之后 LLM 完全看不到新页面，却要照着开局的屏幕把剩下的步骤走完。
 *   表现就是用户看到的：「点进一个 app 就拿不到某些板块的内容」「未找到可点击元素」。
 *
 * 新行为：
 *   每一轮都重新读取当前屏幕交给 LLM，由 LLM 决定下一步 1~4 个动作；
 *   执行中一旦检测到切换 App（包名变化）就立即停下、重新观察；
 *   每轮都可选择「精简树 / 完整树」，遇到截断自动降级到完整树。
 */
class PhoneOperationPlugin(private val context: Context) : ToolPlugin {
    companion object {
        private const val TAG = "PhoneOpPlugin"

        /** 敏感页面关键词（支付/验证码/登录/转账），用于异常检测本地拦截 */
        private val PAYMENT_KEYWORDS = listOf("支付", "付款", "确认支付", "立即支付", "支付密码", "指纹支付", "面容支付", "转账", "汇款")
        private val CODE_KEYWORDS = listOf("验证码", "短信验证码", "图形验证", "获取验证码")
        private val LOGIN_KEYWORDS = listOf("登录密码", "输入密码", "重新输入密码", "请登录", "账号登录")

        /** 检测屏幕文字是否命中敏感页面，命中返回页面类别描述，未命中返回 null */
        private fun detectAbnormalByText(text: String): String? {
            return when {
                PAYMENT_KEYWORDS.any { it in text } -> "支付/转账页面"
                CODE_KEYWORDS.any { it in text } -> "验证码页面"
                LOGIN_KEYWORDS.any { it in text } -> "登录/密码页面"
                else -> null
            }
        }

        /**
         * 从用户指令中粗略猜测目标 APP 名（用于查 uiMap）。
         */
        private fun guessAppNameFromInstruction(instruction: String): String {
            val appKeywords = listOf(
                "网易云音乐" to "网易云音乐",
                "百度地图" to "百度地图",
                "哔哩哔哩" to "哔哩哔哩",
                "小红书" to "小红书",
                "拼多多" to "拼多多",
                "饿了么" to "饿了么",
                "高德地图" to "高德地图",
                "高德" to "高德地图",
                "B站" to "哔哩哔哩",
                "微信" to "微信",
                "淘宝" to "淘宝",
                "天猫" to "天猫",
                "京东" to "京东",
                "抖音" to "抖音",
                "快手" to "快手",
                "美团" to "美团",
                "QQ音乐" to "QQ音乐",
                "QQ" to "QQ",
                "设置" to "设置"
            )
            return appKeywords
                .firstOrNull { (kw, _) -> kw in instruction }
                ?.second ?: ""
        }

        /** 把旧版 UI 知识（uiMap + workflow）格式化为喂给 LLM 的字符串 */
        private fun formatUiKnowledgeForLLM(
            skill: AppSkillManager.Skill,
            instruction: String
        ): String {
            return buildString {
                append("APP: ").append(skill.appName).append("\n")
                append("【UI 结构知识】\n").append(skill.uiMap).append("\n")
                val matchedTask = skill.knownTasks.firstOrNull { task ->
                    task.triggerKeywords.any { kw -> kw in instruction } ||
                        task.taskName in instruction
                } ?: skill.knownTasks.firstOrNull()
                if (matchedTask != null) {
                    append("【匹配任务: ${matchedTask.taskName}】\n")
                    append(matchedTask.workflow).append("\n")
                }
            }
        }

        /** LLM 单步决策 */
        private sealed class Decision {
            data class Act(val actions: List<AutoAction>, val thought: String = "") : Decision()
            data class Done(val summary: String) : Decision()
            data class Err(val message: String) : Decision()
        }

        /** 解析单步决策；兼容模型直接吐动作数组的旧格式 */
        private fun parseDecision(raw: String): Decision? {
            val clean = raw.trim()
                .replace(Regex("^```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("```\\s*$"), "")
                .trim()
            if (clean.isBlank()) return null

            // 旧格式：直接是动作数组
            if (clean.startsWith("[")) {
                val acts = AutoOperator.parseActionsFromLLM(clean)
                return if (acts.isEmpty()) null else Decision.Act(acts)
            }

            return try {
                val obj = JSONObject(clean)
                obj.optString("error", "").takeIf { it.isNotBlank() }?.let { return Decision.Err(it) }
                if (obj.optBoolean("done", false)) {
                    return Decision.Done(
                        obj.optString("summary", obj.optString("message", "任务已完成"))
                    )
                }
                val arr = obj.optJSONArray("actions")
                if (arr != null && arr.length() > 0) {
                    val acts = AutoOperator.parseActionsFromLLM(arr.toString())
                    if (acts.isNotEmpty()) {
                        return Decision.Act(acts, obj.optString("thought", ""))
                    }
                }
                // 单个动作对象 {"action":"click",...}
                if (obj.optString("action", "").isNotBlank()) {
                    val acts = AutoOperator.parseActionsFromLLM("[$clean]")
                    if (acts.isNotEmpty()) return Decision.Act(acts)
                }
                null
            } catch (_: Exception) {
                val acts = AutoOperator.parseActionsFromLLM(clean)
                if (acts.isEmpty()) null else Decision.Act(acts)
            }
        }
    }

    override val name = "operate_phone"
    override val description = "帮用户操作手机上的应用"

    override fun getDefinition() = ToolDefinition(
        name = "operate_phone",
        description = """帮用户操作手机。当用户要求打开应用、发消息、搜索商品、点外卖、查天气等手机操作时调用此工具。
执行方式是「每一步都重新观察屏幕」的闭环，因此可以完成多页面、多步骤的复杂任务。
如果技能库里已有相关技能（见系统提示的「可用技能库」），先确保技能内容已读取，指令里可以顺带说明按技能执行。""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "instruction" to mapOf(
                    "type" to "string",
                    "description" to "用户的操作指令，尽量具体：如「打开微信给小明发消息说明天见」「在淘宝搜索咖啡豆并加入购物车」「打开设置把亮度调到最低」"
                )
            ),
            "required" to listOf("instruction")
        )
    )

    override fun isEnabled(): Boolean {
        // 始终向 LLM 暴露此工具，让 AI 知道自己具备操作手机的能力。
        // 服务未开启时在 execute() 中返回提示，由 LLM 转告用户去开启。
        return true
    }

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val instruction = args.optString("instruction", "")
        if (instruction.isEmpty()) {
            return "错误：请提供操作指令"
        }

        if (!AutoOperator.isServiceReady()) {
            return "错误：无障碍服务未开启，无法操作手机。请先在设置→无障碍中开启服务。"
        }

        return withContext(Dispatchers.IO) {
            try {
                val sm = SettingsManager(context)
                if (sm.chatApiUrl.isBlank()) {
                    return@withContext "错误：未配置API，无法分析操作指令"
                }

                val apiClient = ApiClient(
                    chatApiUrl = sm.chatApiUrl,
                    apiKey = sm.chatApiKey,
                    modelName = sm.chatModel
                )

                // 1. 先验知识：旧版 UI 地图 + 新版技能库
                val skillManager = AppSkillManager(context)
                val skillStore = SkillStore(context)
                skillStore.migrateFromAppSkillsIfNeeded()

                val guessedApp = guessAppNameFromInstruction(instruction)
                val uiKnowledge = if (guessedApp.isNotBlank()) {
                    skillManager.getSkillForApp(guessedApp)?.let { formatUiKnowledgeForLLM(it, instruction) }
                } else null
                val curPkg = AutoOperator.currentPackage()
                val skillBlock = skillStore.buildSkillContextBlock(instruction, curPkg, guessedApp)
                if (uiKnowledge != null) AppLogger.i(TAG, "命中旧版 UI 知识: $guessedApp")
                if (skillBlock.isNotBlank()) AppLogger.i(TAG, "命中技能库")

                val maxSteps = sm.aiOperationMaxRounds
                val strategy = sm.phoneOpStrategy
                AppLogger.i(TAG, "开始手机操作: strategy=$strategy maxSteps=$maxSteps instruction=$instruction")

                val result = if (strategy == "single_shot") {
                    runSingleShot(instruction, apiClient, sm, uiKnowledge, skillBlock, maxSteps)
                } else {
                    runReactiveLoop(instruction, apiClient, sm, uiKnowledge, skillBlock, maxSteps, sm.phoneOpScreenFormat == "tree")
                }

                // 3. 学习闭环：成功且步骤足够多时，沉淀为技能（失败不影响返回）
                if (result.success && result.actionCount >= 3) {
                    try {
                        val skill = SkillCreator.createFromOperationTrace(
                            context, apiClient, instruction, result.actionsJson
                        )
                        AppLogger.i(TAG, "技能沉淀: ${skill?.displayName ?: "跳过"}")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "技能沉淀失败: ${e.message}")
                    }
                    if (guessedApp.isNotBlank()) {
                        skillManager.incrementUseCount(guessedApp, true)
                    }
                } else if (guessedApp.isNotBlank()) {
                    skillManager.incrementUseCount(guessedApp, false)
                }

                result.summary
            } catch (e: Exception) {
                AppLogger.e(TAG, "execute failed: ${e.message}")
                "操作失败：${e.message}"
            }
        }
    }

    private data class OperationOutcome(
        val success: Boolean,
        val summary: String,
        val actionCount: Int = 0,
        val actionsJson: String = "[]"
    )

    /**
     * 反应式闭环：每轮读屏 → LLM 决策 → 执行少量动作 → 再读屏。
     */
    private fun runReactiveLoop(
        instruction: String,
        apiClient: ApiClient,
        sm: SettingsManager,
        uiKnowledge: String?,
        skillBlock: String,
        maxSteps: Int,
        useTree: Boolean
    ): OperationOutcome {
        val history = mutableListOf<String>()
        var executedActions = 0
        var successActions = 0
        val executedJson = StringBuilder("[")
        var consecutiveFailures = 0
        var readOnlyRounds = 0
        var useFullTree = false
        var visionFallbackCount = 0

        // 执行前：敏感页面检测
        if (sm.abnormalDetectionEnabled) {
            detectAbnormalByText(AutoOperator.readScreenText())?.let { abnormal ->
                return OperationOutcome(false, "操作已中止：检测到$abnormal，为安全起见已暂停。如需继续请手动操作，或在无障碍设置中关闭「异常检测」。")
            }
        }

        var step = 0
        while (step < maxSteps) {
            step++

            // 1) 观察：同步读取最新屏幕（必要时用完整树）
            var screen = when {
                useTree && useFullTree -> AutoOperator.formatScreenTreeForLLMFull()
                useTree -> AutoOperator.formatScreenTreeForLLM()
                useFullTree -> AutoOperator.formatScreenForLLMFull()
                else -> AutoOperator.formatScreenForLLM()
            }
            useFullTree = false

            // 无障碍读不到有效内容时自动切视觉兜底（需已授权录屏）。
            // 每次任务最多兜底 2 次：视觉分析较慢，反复触发会把整个流程拖垮。
            if (isScreenContentPoor(screen)) {
                if (visionFallbackCount < 2) {
                    visionFallbackCount++
                    val vision = tryVisionFallback()
                    if (vision != null) {
                        screen += "\n\n=== 视觉兜底（无障碍读不到内容，以下来自屏幕截图识别）===\n$vision"
                        AppLogger.i(TAG, "视觉兜底已启用（第 $visionFallbackCount 次）")
                    } else {
                        screen += "\n\n（无障碍读不到内容，视觉兜底也不可用：可能未授权录屏或本地模型未就绪。" +
                            "可以先用 home / back 让页面重新渲染，或 scroll 一次后再 read）"
                    }
                } else {
                    screen += "\n\n（无障碍读不到内容，视觉兜底本次已用过 $visionFallbackCount 次，不再重复尝试）"
                }
            }

            // 2) 决策
            val raw = apiClient.analyzePhoneOpStep(
                userRequest = instruction,
                screenInfo = screen,
                history = history,
                stepIndex = step,
                maxSteps = maxSteps,
                uiKnowledge = uiKnowledge ?: "",
                skillsSection = skillBlock
            )
            if (raw.isNullOrBlank()) {
                return OperationOutcome(
                    successActions > 0,
                    buildSummary(successActions, executedActions, history) + "\n（第 $step 轮：LLM 无响应，提前结束）"
                )
            }

            val decision = parseDecision(raw)
            if (decision == null) {
                history.add("第${step}轮：模型返回无法解析的内容（已跳过本轮）")
                consecutiveFailures++
                if (consecutiveFailures >= 3) {
                    return OperationOutcome(false, buildSummary(successActions, executedActions, history) + "\n（模型连续返回无法解析的内容，已停止）", executedActions)
                }
                continue
            }

            when (decision) {
                is Decision.Done -> {
                    val sb = buildSummary(successActions, executedActions, history)
                    val finalText = if (decision.summary.isNotBlank()) "$sb\n结果：${decision.summary}" else sb
                    return OperationOutcome(true, finalText, executedActions, executedJson.toString() + "]")
                }
                is Decision.Err -> {
                    return OperationOutcome(false, "操作已中止：${decision.message}\n已执行步骤：\n" + history.takeLast(8).joinToString("\n"), executedActions)
                }
                is Decision.Act -> {
                    if (decision.actions.isEmpty()) continue

                    // 只要求 read 而没有其它动作时，限制连续次数，避免空转
                    if (decision.actions.all { it.type == "read" }) {
                        readOnlyRounds++
                        if (readOnlyRounds >= 3) {
                            history.add("第${step}轮：模型反复要求重读屏幕，请基于当前屏幕直接执行动作或返回 done。")
                            continue
                        }
                    } else {
                        readOnlyRounds = 0
                    }

                    history.add("第${step}轮 计划${decision.actions.size}个动作${if (decision.thought.isNotBlank()) "（${decision.thought.take(60)}）" else ""}")

                    // 3) 行动
                    val batch = AutoOperator.executeBatch(
                        decision.actions,
                        cooldownMs = sm.aiOperationCooldownMs,
                        observeOnPageChange = sm.phoneOpObserveOnPageChange
                    )
                    history.addAll(batch.lines)
                    executedActions += decision.actions.size
                    successActions += batch.successCount
                    decision.actions.forEach { a ->
                        if (executedJson.length > 1) executedJson.append(',')
                        executedJson.append(actionToJson(a))
                    }

                    // 执行中异常检测
                    if (sm.abnormalDetectionEnabled) {
                        detectAbnormalByText(ScreenRecognitionService.getLastScreenText())?.let { abnormal ->
                            return OperationOutcome(false, "操作已中止：执行中检测到$abnormal，剩余步骤未执行。\n" + history.takeLast(6).joinToString("\n"), executedActions)
                        }
                    }

                    // 4) 失败处理与自愈：按失败类型给出针对性纠正建议，而不是一句笼统的"失败了"
                    val failedLines = batch.lines.filter { "失败:" in it }
                    if (batch.successCount == 0) {
                        consecutiveFailures++
                        useFullTree = true
                        val lastScreen = ScreenRecognitionService.getLastScreenText()
                        val truncated = lastScreen.contains("已截断")
                        val assertFailed = failedLines.count { it.contains("断言未通过") }
                        val notFound = failedLines.count { it.contains("未找到可点击元素") }
                        history.add(
                            buildString {
                                append("提示：上一轮动作全部失败。")
                                if (truncated) {
                                    append("屏幕内容被截断，下一轮改用完整树重读，必要时先 scroll 翻页。")
                                }
                                if (assertFailed > 0) {
                                    append("其中 $assertFailed 个属于「事件点了但预期没达成」，")
                                    append("通常是点到了非目标元素、或页面还没加载完。")
                                    append("建议先用 read 重读屏幕确认当前到底在哪个页面，再重新决定，不要重复同样的点击。")
                                }
                                if (notFound > 0) {
                                    append("其中 $notFound 个属于「找不到目标元素」，")
                                    append("建议改用快照里的 index，或先 scroll 翻页，或用坐标 tap。")
                                }
                                if (assertFailed == 0 && notFound == 0 && !truncated) {
                                    append("请重新观察当前屏幕，改用 index 或坐标 tap，或判断当前是否已经在目标页面。")
                                }
                            }
                        )
                        if (consecutiveFailures >= 4) {
                            return OperationOutcome(
                                false,
                                buildSummary(successActions, executedActions, history) + "\n（连续 $consecutiveFailures 轮动作全部失败，已停止。可到设置里调高「UI 树深度/元素上限」，或把「无障碍服务」重新开启一次）",
                                executedActions, executedJson.toString() + "]"
                            )
                        }
                    } else {
                        consecutiveFailures = 0
                        // 部分失败也要点出来，避免被"整体成功"掩盖掉
                        val assertFailed = failedLines.count { it.contains("断言未通过") }
                        if (assertFailed > 0) {
                            history.add(
                                "提示：上一轮有 $assertFailed 个动作未达成预期（断言失败），" +
                                    "请确认这几步的实际效果，必要时补做一次，或先用 read 确认屏幕当前状态。"
                            )
                        }
                    }
                }
            }
        }

        return OperationOutcome(
            successActions > 0,
            buildSummary(successActions, executedActions, history) + "\n（已达最大操作轮次 $maxSteps，任务可能还没完成。可在「设置 → 无障碍 → 最大操作轮次」调高后让我继续）",
            executedActions, executedJson.toString() + "]"
        )
    }

    /** 旧策略：一次规划整段动作后顺序执行（保留用于兼容/排障） */
    private fun runSingleShot(
        instruction: String,
        apiClient: ApiClient,
        sm: SettingsManager,
        uiKnowledge: String?,
        skillBlock: String,
        maxSteps: Int
    ): OperationOutcome {
        val screenInfo = AutoOperator.formatScreenForLLM()
        val llmResult = apiClient.analyzeAutoOperation(instruction, screenInfo, (uiKnowledge ?: "") + "\n" + skillBlock)
        if (llmResult.isNullOrBlank()) {
            return OperationOutcome(false, "无法理解操作指令：$instruction（LLM 无响应）")
        }

        val cleanJson = llmResult.trim()
            .replace(Regex("^```\\s*json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*$"), "")
            .trim()
        if (!cleanJson.startsWith("[")) {
            val obj = try { JSONObject(cleanJson) } catch (_: Exception) { null }
            obj?.optString("error", "")?.takeIf { it.isNotBlank() }?.let {
                return OperationOutcome(false, "操作被拒绝：$it")
            }
            val arr = obj?.optJSONArray("actions")
            if (arr == null) return OperationOutcome(false, "无法解析 LLM 响应：$llmResult")
        }

        val actionsJson = if (cleanJson.startsWith("[")) {
            cleanJson
        } else {
            try { JSONObject(cleanJson).optJSONArray("actions")?.toString() ?: "[]" } catch (_: Exception) { "[]" }
        }
        val actions = AutoOperator.parseActionsFromLLM(actionsJson)
        if (actions.isEmpty()) return OperationOutcome(false, "无法解析操作步骤：$instruction")

        if (sm.abnormalDetectionEnabled) {
            detectAbnormalByText(AutoOperator.readScreenText())?.let { abnormal ->
                return OperationOutcome(false, "操作已中止：检测到$abnormal，为安全起见已暂停。")
            }
        }

        val limited = actions.take(maxSteps)
        val batch = AutoOperator.executeBatch(limited, sm.aiOperationCooldownMs, observeOnPageChange = false)
        val summary = "操作完成：${batch.successCount}/${limited.size} 步成功（单次规划模式）\n" + batch.lines.joinToString("\n")
        return OperationOutcome(batch.allSuccess, summary, limited.size, actionsJson)
    }

    /**
     * 判断无障碍读到的屏幕内容是否「贫瘠」到需要视觉兜底。
     * 典型场景：页面用自绘/OpenGL 渲染（游戏、部分金融类 App），无障碍树几乎是空的，
     * 但屏幕上明明有内容 —— 这正是「未找到可点击元素但页面全是可点的」的另一种成因。
     */
    private fun isScreenContentPoor(screen: String): Boolean {
        // 兼容 text 与 tree 两种格式的报错前缀
        val body = screen
            .substringAfter("当前屏幕内容：", screen)
            .substringAfter("当前屏幕 UI 树", screen)
            .trim()
        if (body.contains("屏幕数据读取失败") || body.contains("未检测到屏幕内容")) return true
        val meaningful = body
            .replace(Regex("[\\s\\[\\]{}()（）【】:：,，。.·|\\-—>\"]"), "")
        return meaningful.length < 30
    }

    /**
     * 视觉兜底：无障碍读不到内容时，截图走本地 OCR / 视觉模型。
     * 前提是用户已授权录屏（MediaProjection），否则拿不到画布，返回 null。
     */
    private fun tryVisionFallback(): String? {
        return try {
            val manager = LocalModelManager(context)
            val result = runBlocking { manager.analyzeCurrentScreen() } ?: return null
            val block = result.toContextBlock()
            if (block.isBlank()) null else block
        } catch (e: Exception) {
            AppLogger.w(TAG, "vision fallback failed: ${e.message}")
            null
        }
    }

    private fun buildSummary(success: Int, executed: Int, history: List<String>): String {
        val sb = StringBuilder()
        sb.append("操作结束：执行 $executed 个动作，成功 $success 个\n")
        sb.append("【执行轨迹】\n")
        sb.append(history.takeLast(30).joinToString("\n"))
        return sb.toString().trim()
    }

    private fun actionToJson(a: AutoAction): String {
        val obj = JSONObject()
        obj.put("action", a.type)
        when (a.type) {
            "click" -> {
                if (a.index >= 0) obj.put("index", a.index)
                if (a.text.isNotBlank()) obj.put("text", a.text)
            }
            "tap" -> {
                obj.put("x", a.startX.toInt())
                obj.put("y", a.startY.toInt())
            }
            "input" -> {
                obj.put("text", a.content)
                if (a.target.isNotBlank()) obj.put("target", a.target)
            }
            "set_checked" -> {
                if (a.index >= 0) obj.put("index", a.index)
                if (a.text.isNotBlank()) obj.put("checked", a.text)
            }
            "swipe" -> obj.put("direction", a.direction)
            "scroll" -> obj.put("direction", a.direction)
            "long_press" -> if (a.text.isNotBlank()) obj.put("text", a.text)
            "wait" -> obj.put("ms", a.durationMs)
        }
        return obj.toString()
    }
}
