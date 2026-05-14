/** 自动操作器: 通过Android辅助功能实现自动点击/滑动/输入文本等屏幕操作 */
package com.aicompanion.screen

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.aicompanion.screen.ScreenRecognitionService.Companion.performClick
import com.aicompanion.screen.ScreenRecognitionService.Companion.performClickByIndex
import com.aicompanion.screen.ScreenRecognitionService.Companion.performGlobalAction
import com.aicompanion.screen.ScreenRecognitionService.Companion.performScroll

data class AutoAction(
    val type: String,
    val text: String = "",
    val index: Int = -1,
    val direction: String = "forward",
    val durationMs: Int = 2000
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
            val elements = ScreenRecognitionService.getClickableElements()
            if (screenText.isBlank() && elements.isEmpty()) {
                return "(未检测到屏幕内容)"
            }
            val sb = StringBuilder()
            sb.appendLine("=== 屏幕文字 ===")
            sb.appendLine(screenText)
            sb.appendLine()
            sb.appendLine("=== 可点击元素 ===")
            elements.forEachIndexed { i, elem ->
                val label = if (elem.text.isNotBlank()) elem.text else elem.contentDescription
                sb.appendLine("[$i] $label")
            }
            return sb.toString()
        }

        fun executeAction(action: AutoAction): Boolean {
            return when (action.type) {
                "click" -> executeClick(action)
                "back" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "scroll" -> performScroll(action.direction)
                "notifications" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "recents" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "wait" -> {
                    try { Thread.sleep(action.durationMs.toLong()) } catch (_: Exception) {}
                    true
                }
                else -> {
                    Log.d(TAG, "Unknown action type: ${action.type}")
                    false
                }
            }
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
{"action":"click|back|home|scroll|wait","text":"要点的文字","index":数字索引}
""".trimIndent()
        }
    }
}