package com.aicompanion.plugin

import android.content.Context
import com.aicompanion.models.ToolDefinition
import com.aicompanion.settings.SettingsManager
import com.aicompanion.shell.ShellExecutor
import org.json.JSONObject

/**
 * 命令执行工具：让 AI 在安卓设备上执行 shell 命令。
 *
 * 默认关闭（设置 → 无障碍 → 命令执行）。高危命令需要额外放行开关。
 * 能力分级见 [ShellExecutor]：APP(免root) / ROOT / SHIZUKU(ADB级)。
 */
class ShellPlugin(private val context: Context) : ToolPlugin {

    override val name = "run_shell"
    override val description = "在安卓设备上执行 shell 命令"

    override fun isEnabled(): Boolean = SettingsManager(context).shellEnabled

    override fun getDefinition() = ToolDefinition(
        name = "run_shell",
        description = """在安卓设备上执行一条 shell 命令，并返回 stdout/stderr。

【什么时候用】
- 查询设备信息：getprop / dumpsys battery / wm size / df -h / free -m
- 应用管理：pm list packages | grep xxx
- 拉起页面：am start -a android.intent.action.VIEW -d "https://..."
- 用 Shizuku/root 通道时可以：input tap x y、input text xxx、settings put、screencap -p /sdcard/a.png

【注意】
- 默认通道以「本应用身份(APP)」运行，权限有限；若设备已装 Shizuku 并授权，会自动升到 ADB 级能力
- 删除数据、卸载应用、改系统设置等命令会被安全策略拦截，需要用户在设置里显式放行
- 命令要幂等、避免交互式命令（如 top、vi），必要时用 timeout 参数控制
- 先用 action=capabilities 查询当前可用通道，再决定命令怎么写""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("exec", "capabilities"),
                    "description" to "exec=执行命令；capabilities=查询当前可用的执行通道"
                ),
                "command" to mapOf("type" to "string", "description" to "要执行的 shell 命令"),
                "timeout_ms" to mapOf("type" to "integer", "description" to "超时毫秒，默认 20000"),
                "mode" to mapOf(
                    "type" to "string",
                    "enum" to listOf("auto", "app", "shizuku", "adb", "root"),
                    "description" to "指定执行通道，默认 auto"
                )
            ),
            "required" to listOf("action")
        )
    )

    override suspend fun execute(arguments: String): String {
        val args = JSONObject(arguments)
        val action = args.optString("action", "exec").lowercase()
        val sm = SettingsManager(context)

        if (!sm.shellEnabled) {
            return "命令执行功能未开启。请让用户到「设置 → 无障碍 → 命令执行」打开开关后再试。"
        }

        if (action == "capabilities") {
            return "当前命令执行能力：\n" + ShellExecutor.capabilityReport() +
                "\n\n说明：APP 通道免 root 但权限有限；Shizuku 通道为 ADB 级（可 input/settings/screencap）；ROOT 通道能力最全。"
        }

        val command = args.optString("command", "").trim()
        if (command.isBlank()) return "请提供要执行的命令（command 参数）"

        val timeout = args.optLong("timeout_ms", sm.shellTimeoutMs.toLong()).coerceIn(3000L, 120000L)
        val mode = ShellExecutor.Mode.from(args.optString("mode", sm.shellMode))
        // 注意：是否放行高危命令由用户设置决定，不接受模型自行申请放行
        val result = ShellExecutor.run(
            context = context,
            command = command,
            timeoutMs = timeout,
            mode = mode,
            allowDangerous = sm.shellAllowDangerous
        )
        return result.toText()
    }
}
