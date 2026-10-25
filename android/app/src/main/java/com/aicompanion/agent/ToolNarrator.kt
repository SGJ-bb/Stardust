package com.aicompanion.agent

import org.json.JSONObject

/**
 * 把「工具名 + JSON 参数」翻成一句人话，给状态条用。
 *
 * 目的很单纯：用户看 `device({"action":"tap","x":540,"y":1200})` 等于没看见，
 * 但看「点按屏幕 (540, 1200)」就知道 AI 在动他的手机。
 */
object ToolNarrator {

    fun narrate(name: String, arguments: String): String {
        val a = runCatching { JSONObject(arguments.ifBlank { "{}" }) }.getOrNull()
        val action = a?.optString("action", "").orEmpty()

        return when (name) {
            "device" -> when (action) {
                "info" -> "读取设备信息"
                "settings_get" -> "读取设置 ${a?.optString("ns")}/${a?.optString("key")}"
                "settings_put" -> "修改设置 ${a?.optString("ns")}/${a?.optString("key")} = ${a?.optString("value")}"
                "settings_delete" -> "删除设置 ${a?.optString("ns")}/${a?.optString("key")}"
                "tap" -> "点按屏幕 (${a?.optInt("x")}, ${a?.optInt("y")})"
                "long_press" -> "长按屏幕 (${a?.optInt("x")}, ${a?.optInt("y")})"
                "swipe" -> "滑动 (${a?.optInt("x1")},${a?.optInt("y1")}) → (${a?.optInt("x2")},${a?.optInt("y2")})"
                "text" -> "输入文字「${a?.optString("text").orEmpty().take(12)}」"
                "key" -> "按键 ${a?.optString("key")}"
                "screenshot" -> "截屏"
                "app_list" -> "列出应用"
                "app_start" -> "打开应用 ${a?.optString("package") ?: a?.optString("app")}"
                "app_stop" -> "停止应用 ${a?.optString("package") ?: a?.optString("app")}"
                "app_clear" -> "清除应用数据 ${a?.optString("package") ?: a?.optString("app")}"
                "screen_state" -> "查询屏幕状态"
                else -> "设备操作 $action"
            }
            "system" -> {
                val opId = a?.optString("op", "").orEmpty()
                val op = com.aicompanion.system.SystemOps.find(opId)
                when {
                    action == "catalog" -> "查看系统操作清单"
                    op != null -> op.label
                    else -> "系统操作 $opId"
                }
            }
            "run_shell", "shell" -> "执行命令：${a?.optString("command").orEmpty().take(40)}"
            "file" -> when (action) {
                "read" -> "读取文件 ${shortPath(a)}"
                "write" -> "写入文件 ${shortPath(a)}"
                "append" -> "追加内容到 ${shortPath(a)}"
                "delete" -> "删除 ${shortPath(a)}"
                "list" -> "列出目录 ${shortPath(a)}"
                "search" -> "搜索文件「${a?.optString("query").orEmpty().take(16)}」"
                "mkdir" -> "新建目录 ${shortPath(a)}"
                "copy", "move" -> "移动/复制 ${shortPath(a)}"
                "info" -> "查看文件信息 ${shortPath(a)}"
                else -> "文件操作 $action"
            }
            "search_web" -> "联网搜索「${a?.optString("query").orEmpty().take(20)}」"
            "operate_phone" -> when (action) {
                "click", "tap" -> "点击「${a?.optString("text") ?: a?.optString("target")}」"
                "input", "type" -> "输入文字"
                "scroll", "swipe" -> "滚动界面"
                "back" -> "返回上一页"
                "home" -> "回到桌面"
                "open" -> "打开 ${a?.optString("app") ?: a?.optString("package")}"
                "snapshot", "read", "observe" -> "看当前屏幕"
                else -> "操作手机 $action"
            }
            "plan" -> when (action) {
                "set" -> "制定计划"
                "update" -> "更新计划进度"
                "get" -> "读取计划"
                else -> "计划操作"
            }
            "calendar" -> when (action) {
                "agenda" -> "查看日历安排（${a?.optString("date").orEmpty().ifBlank { "今天" }}）"
                "add" -> "写进手机日历「${a?.optString("title").orEmpty().take(12)}」"
                "delete" -> "删除日历「${a?.optString("title").orEmpty().take(12)}」"
                else -> "日历操作"
            }
            "add_calendar_event" -> "记日历「${a?.optString("title").orEmpty().take(12)}」"
            "set_alarm" -> "${a?.optInt("minutes")} 分钟后提醒"
            "set_alarm_at_time" -> "设置 ${a?.optInt("hour")}:${a?.optInt("minute")} 提醒"
            "generate_image" -> "生成图片「${a?.optString("prompt").orEmpty().take(14)}」"
            "search_memory" -> "回忆「${a?.optString("query").orEmpty().take(14)}」"
            "search_diary" -> "翻日记找「${a?.optString("query").orEmpty().take(14)}」"
            "save_memory" -> "记住一条信息"
            else -> name
        }
    }

    private fun shortPath(a: JSONObject?): String {
        val p = a?.optString("path").orEmpty()
        if (p.isBlank()) return ""
        return p.substringAfterLast('/').ifBlank { p }
    }
}
