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

data class AutoAction(
    val type: String,
    val text: String = "",
    val index: Int = -1,
    val direction: String = "forward",
    val durationMs: Int = 2000,
    /** input 动作：要输入的文本内容 */
    val content: String = "",
    /** input 动作：目标输入框的 text/contentDescription 标识（空则用当前焦点） */
    val target: String = "",
    /** swipe 动作：自定义起点坐标（<0 时使用 direction 预设方向） */
    val startX: Float = -1f,
    val startY: Float = -1f,
    val endX: Float = -1f,
    val endY: Float = -1f,
    /** swipe 动作时长（ms） */
    val swipeDuration: Long = 400L,
)

/** 操作执行结果（含失败原因，供 LLM 反馈） */
data class ActionResult(
    val success: Boolean,
    val errorMsg: String = ""
)

class AutoOperator {

    companion object {
        private const val TAG = "AutoOperator"

        fun isServiceReady(): Boolean {
            return ScreenRecognitionService.getInstance() != null
        }

        fun readScreenText(): String {
            val service = ScreenRecognitionService.getInstance()
            if (service == null) {
                Log.d(TAG, "AccessibilityService not connected")
                return "(无障碍服务未连接)"
            }
            ScreenRecognitionService.refreshScreenData()
            val screenText = ScreenRecognitionService.getLastScreenText()
            val elements = ScreenRecognitionService.getClickableData()
            if (screenText.isBlank() && elements.isEmpty()) {
                return "(未检测到屏幕内容)"
            }
            val sb = StringBuilder()
            sb.appendLine("=== 屏幕文字 ===")
            sb.appendLine(screenText)
            sb.appendLine()
            sb.appendLine("=== 可点击元素 ===")
            elements.forEachIndexed { i, elem ->
                val label = if (elem.text.isNotBlank()) elem.text else elem.desc
                sb.appendLine("[$i] $label")
            }
            return sb.toString()
        }

        fun executeAction(action: AutoAction): ActionResult {
            val result = when (action.type) {
                "click" -> {
                    val ok = executeClick(action)
                    ActionResult(ok, if (!ok) "未找到可点击元素: ${action.text}" else "")
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
                "notifications" -> ActionResult(performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
                "recents" -> ActionResult(performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS))
                "wait" -> {
                    val safeDuration = action.durationMs.coerceIn(100, 10000)
                    try { Thread.sleep(safeDuration.toLong()) } catch (_: Exception) {}
                    ActionResult(true)
                }
                else -> ActionResult(false, "未知动作类型: ${action.type}")
            }
            // cooldown：动作间冷却（wait 动作不重复等待）
            if (action.type != "wait" && result.success) {
                val cooldown = getCooldownMs()
                if (cooldown > 0) {
                    try { Thread.sleep(cooldown.toLong()) } catch (_: Exception) {}
                }
            }
            return result
        }

        /** 从 ScreenRecognitionService 读取 AI 操作冷却时间配置 */
        private fun getCooldownMs(): Int {
            return try {
                val service = ScreenRecognitionService.getInstance() ?: return 200
                com.aicompanion.settings.SettingsManager(service).aiOperationCooldownMs
            } catch (_: Exception) { 200 }
        }

        /** 长按动作：按文字或坐标执行长按手势 */
        private fun executeLongPress(action: AutoAction): ActionResult {
            if (action.text.isNotBlank()) {
                // 按文字找到元素坐标后长按
                val ok = ScreenRecognitionService.performLongPressByText(action.text, action.durationMs.toLong().coerceAtLeast(500))
                return ActionResult(ok, if (!ok) "未找到长按目标: ${action.text}" else "")
            }
            if (action.startX >= 0 && action.startY >= 0) {
                val ok = ScreenRecognitionService.performLongPress(action.startX, action.startY, action.durationMs.toLong().coerceAtLeast(500))
                return ActionResult(ok)
            }
            return ActionResult(false, "长按需要 text 或坐标")
        }

        private fun executeClick(action: AutoAction): Boolean {
            if (action.index >= 0) {
                return performClickByIndex(action.index)
            }
            if (action.text.isNotBlank()) {
                return performClick(action.text)
            }
            return false
        }

        private fun executeSwipe(action: AutoAction): Boolean {
            // 自定义坐标优先，否则用预设方向
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
                    val type = obj.optString("action", "")
                    val action = when (type) {
                        "click" -> AutoAction(
                            type = "click",
                            text = obj.optString("text", ""),
                            index = obj.optInt("index", -1)
                        )
                        "back" -> AutoAction(type = "back")
                        "home" -> AutoAction(type = "home")
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
                    if (action != null) actions.add(action)
                }
            } catch (e: Exception) {
                Log.d(TAG, "parseActionsFromLLM failed: ${e.message}")
            }
            return actions
        }

        fun formatScreenForLLM(): String {
            val screenText = readScreenText()
            return """
当前屏幕内容：
$screenText

请返回一个JSON数组，每个元素包含:
{"action":"click|back|home|scroll|swipe|input|long_press|wait","text":"要点的文字或输入的内容","index":数字索引,"direction":"up|down|left|right|forward|backward","target":"输入框标识(空则用当前焦点)"}

动作说明:
- click: 点击元素(text匹配文字或index指定索引)
- scroll: 滚动(direction: forward/backward)
- swipe: 滑动(direction: up/down/left/right，或自定义坐标start_x/start_y/end_x/end_y)
- input: 输入文本(target:输入框标识, text:要输入的内容)
- long_press: 长按元素(text匹配文字或start_x/start_y坐标,duration:毫秒)
- back/home/notifications/recents: 系统动作
- wait: 等待(ms:毫秒)
""".trimIndent()
        }

        fun formatScreenForLLM(localAnalysis: com.aicompanion.localmodel.ScreenAnalysisResult?): String {
            val screenText = readScreenText()
            val sb = StringBuilder()
            sb.appendLine("当前屏幕内容：")
            sb.appendLine(screenText)

            if (localAnalysis != null) {
                val visionBlock = localAnalysis.toContextBlock()
                if (visionBlock.isNotBlank()) {
                    sb.appendLine()
                    sb.appendLine("=== 视觉识别 ===")
                    sb.appendLine(visionBlock)
                }
            }

            sb.appendLine()
            sb.appendLine("请返回一个JSON数组，每个元素包含:")
            sb.appendLine("{\"action\":\"click|back|home|scroll|swipe|input|long_press|wait\",\"text\":\"要点的文字或输入的内容\",\"index\":数字索引,\"direction\":\"up|down|left|right|forward|backward\",\"target\":\"输入框标识\"}")
            sb.appendLine()
            sb.appendLine("动作说明: click点击/scroll滚动(direction:forward|backward)/swipe滑动(direction:up|down|left|right)/input输入(target:输入框,text:内容)/long_press长按(text或start_x/start_y,duration:毫秒)/back/home/wait(ms)")
            return sb.toString()
        }
    }
}
