package com.aicompanion.agent

import org.json.JSONObject

/**
 * 工具调用前的安全闸门。
 *
 * 三档：
 * - [Decision.Allow]   直接执行
 * - [Decision.Confirm] 需要用户点头（不可逆、动别人、动系统）
 * - [Decision.Deny]    一律不做（会切断我们自己的通道 / 明显自毁）
 *
 * 判定原则：**按"能不能撤销"分级**，而不是按工具名。
 * 点一下屏幕可以撤销（再点回去），删文件、卸载应用、改 adb 开关不能。
 */
object ToolPolicy {

    sealed class Decision {
        object Allow : Decision()
        data class Confirm(val reason: String) : Decision()
        data class Deny(val reason: String) : Decision()
    }

    /** 改了会直接切断我们自己的调试通道 */
    private val SELF_KILL_SETTINGS = setOf(
        "adb_enabled",
        "adb_wifi_enabled",
        "development_settings_enabled",
    )

    /** 明显破坏性 / 不可恢复的命令特征 */
    private val DANGEROUS_SHELL = listOf(
        "rm -rf", "rm -fr", "rm -r ", "rm /", " mkfs", "dd if=", "fastboot ",
        "pm uninstall", "pm clear", "reboot", "shutdown", "su ", "su$",
        "wipe data", "format ", "chmod 777 /", "mount -o remount,rw /system",
    )

    /** 会把消息发出去给人看的动作（不能替用户擅自发） */
    private val OUTBOUND_TOOLS = setOf(
        "send_message", "send_wechat", "send_sms", "send_email", "make_call",
    )

    fun check(name: String, arguments: String): Decision {
        val a = runCatching { JSONObject(arguments.ifBlank { "{}" }) }.getOrNull()
        val action = a?.optString("action", "").orEmpty()

        // 1) 直接拒绝：动我们自己的通道
        if (name == "device" && (action == "settings_put" || action == "settings_delete")) {
            val key = a?.optString("key", "").orEmpty()
            if (key in SELF_KILL_SETTINGS) {
                return Decision.Deny("改 $key 会切断当前调试通道，之后所有设备操作都会失效")
            }
        }

        // 2) 直接拒绝：明确不可恢复的 shell 命令
        if (name == "run_shell" || name == "shell") {
            val cmd = a?.optString("command", "").orEmpty().lowercase()
            if (cmd.isBlank()) return Decision.Deny("命令为空")
            DANGEROUS_SHELL.firstOrNull { cmd.contains(it) }?.let {
                return Decision.Deny("命令包含高危片段「${it.trim()}」，不做")
            }
        }

        // 3) 需要确认
        when (name) {
            "run_shell", "shell" -> {
                val cmd = a?.optString("command", "").orEmpty()
                // 写 / 删 / 装 / 改系统分区这类先问一句
                val risky = listOf("rm ", "pm install", "pm disable", "pm enable", "setprop", "mount", "mv ", "cp ",
                    "settings put", "settings delete", "am force-stop", "input keyevent 26")
                if (risky.any { cmd.contains(it) }) {
                    return Decision.Confirm("这条命令会改动系统状态：「${cmd.take(60)}」")
                }
            }
            "file" -> if (action == "delete") {
                return Decision.Confirm("要删除文件「${a?.optString("path").orEmpty()}」，删了恢复不回来")
            }
            "system" -> {
                // 系统功能目录：按目录里声明的风险级别决定要不要问
                val opId = a?.optString("op", "").orEmpty()
                val op = com.aicompanion.system.SystemOps.find(opId)
                if (op != null && op.risk != com.aicompanion.system.SystemOps.Risk.LOW) {
                    return Decision.Confirm("要${op.label}，这会改动系统状态")
                }
            }
            "device" -> when (action) {
                "app_clear" -> return Decision.Confirm(
                    "要清除「${a?.optString("package").orEmpty()}」的全部数据（登录状态、聊天记录都会没）"
                )
                "app_stop" -> return Decision.Confirm("要强制停止「${a?.optString("package").orEmpty()}」")
                "long_press" -> { /* 长按可能触发卸载/删除，但归为普通操作 */ }
            }
        }
        if (name in OUTBOUND_TOOLS) {
            return Decision.Confirm("要替你发出一条消息")
        }
        return Decision.Allow
    }
}
