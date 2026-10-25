package com.aicompanion.plugin

import android.content.Context
import android.os.Build
import com.aicompanion.adb.AdbSelf
import com.aicompanion.models.ToolDefinition
import com.aicompanion.system.SystemOps
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 系统功能工具：闹钟、免打扰、亮度、飞行模式、WiFi、蓝牙、截屏、电池……
 *
 * 存在的意义：这些功能**不需要逐个写 Kotlin 插件**，底层全是同一条 shell，
 * 差别只在命令。所以做成「命令目录 + 回读验证」：
 *   - 目录在 [SystemOps]，按 Android 版本选命令；
 *   - 执行完立刻跑 probe 回读真实值，避免"命令成功但没生效"被当成成功；
 *   - 参数类型/范围校验，不接受任意字符串进命令（防注入）。
 *
 * 需要 shell(2000) 通道；没有通道时如实说明，而不是假装失败。
 */
class SystemShellPlugin(private val context: Context) : ToolPlugin {

    override val name = "system"
    override val description =
        "系统功能：闹钟/计时器/亮度/免打扰/飞行模式/WiFi/蓝牙/定位/截屏/录屏/电池/应用管理"

    override fun getDefinition() = ToolDefinition(
        name = "system",
        description = """操作系统级功能（通过 shell 执行，比逐条发命令更安全可靠）。
【怎么用】
- 不确定有哪些操作可用 → 先 action=catalog 看清单
- 执行某个操作 → action=run，op=操作id，params=参数对象

【覆盖范围】
闹钟/计时器、屏幕亮度与自动亮度、自动旋转、息屏时间、充电保持常亮、
免打扰、媒体控制、WiFi/移动数据/蓝牙/飞行模式/定位、
截屏/录屏、电池/存储/网络状态、震动、装应用/卸载/清除数据。

【重要】
- 每次执行后都会回读真实值（如 settings get），返回「已设置 X / 实际读到 Y」
- 如果回读值和期望不符，说明系统没接受，不要假设成功了
- 闹钟/计时器是调起系统闹钟应用，不是本应用的提醒；要本应用提醒请用 set_alarm
- 没有 shell 通道时这个工具会直接告诉你不可用""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("catalog", "run"),
                    "description" to "catalog=查看可用的系统操作清单；run=执行"
                ),
                "op" to mapOf("type" to "string", "description" to "操作 id，如 alarm.set / brightness.set / dnd.set"),
                "params" to mapOf(
                    "type" to "object",
                    "description" to "参数对象，如 {\"hour\":7,\"minute\":30} 或 {\"on\":true} 或 {\"value\":120}"
                )
            ),
            "required" to listOf("action")
        )
    )

    override suspend fun execute(arguments: String): String = withContext(Dispatchers.IO) {
        val a = JSONObject(arguments)
        val action = a.optString("action", "catalog")
        val sdk = Build.VERSION.SDK_INT

        when (action) {
            "catalog" -> SystemOps.catalog(sdk)

            "run" -> {
                val id = a.optString("op", "")
                val op = SystemOps.find(id)
                    ?: return@withContext "错误：没有这个操作「$id」。先用 action=catalog 看可用清单。"
                if (sdk < op.minSdk) {
                    return@withContext "错误：${op.label} 需要 Android API ${op.minSdk}+，当前是 $sdk"
                }

                val params = mutableMapOf<String, String>()
                a.optJSONObject("params")?.let { p ->
                    p.keys().forEach { k -> params[k] = p.optString(k) }
                }
                // 也允许把参数平铺在顶层（模型经常这么写）
                listOf("on", "value", "hour", "minute", "label", "seconds", "ms", "key", "name", "package", "path")
                    .forEach { k -> if (!a.has("params") && a.has(k)) params[k] = a.optString(k) }

                SystemOps.validate(op, params)?.let { return@withContext "错误：$it" }

                // 通道没连上就主动连一次，而不是直接放弃。
                // 用户不需要知道"通道"这回事，AI 也不该因为"这次恰好没连"就报失败。
                if (!AdbSelf.isReady()) {
                    val st = AdbSelf.autoConnect(context)
                    if (!st.connected) {
                        return@withContext "没有 shell 通道（uid 2000），这个操作执行不了。\n" +
                            "原因：${st.error}\n" +
                            "需要在 Android 11+ 上完成一次无线调试配对，或先执行 adb tcpip 5555。"
                    }
                }

                val cmd = op.build(params, sdk)
                val t0 = System.currentTimeMillis()
                val r = AdbSelf.execRaw(context, cmd, 15000)
                val cost = System.currentTimeMillis() - t0
                AppLogger.i("SystemTool", "${op.id} exit=${r.exitCode} ${cost}ms cmd=$cmd")

                // 回读验证：这一步是"有没有真的生效"的唯一依据
                val probeText = op.probe?.let { p ->
                    val pr = AdbSelf.execRaw(context, p, 8000)
                    pr.stdout.trim().ifBlank { pr.stderr.trim() }
                }

                buildString {
                    appendLine("已执行：${op.label}")
                    append("命令：${cmd.take(160)}")
                    appendLine()
                    if (r.exitCode != 0) {
                        appendLine("退出码：${r.exitCode}")
                        if (r.stderr.isNotBlank()) appendLine("错误：${r.stderr.trim().take(200)}")
                    }
                    if (probeText != null) {
                        appendLine("回读：$probeText")
                        val expected = expectedFor(op.id, params)
                        if (expected != null) {
                            val ok = probeText.trim() == expected
                            appendLine(
                                if (ok) "✓ 已确认生效（期望 $expected）"
                                else "⚠️ 回读值与期望不符（期望 $expected，实际 ${probeText.trim()}）：系统可能没接受，别当成功"
                            )
                        }
                    } else {
                        appendLine("（该操作没有可回读的状态，只能确认命令已发出）")
                    }
                    append("耗时：${cost}ms")
                }
            }

            else -> "错误：action 只能是 catalog / run"
        }
    }

    /** 期望的回读值，用于判断"真的生效了吗" */
    private fun expectedFor(opId: String, v: Map<String, String>): String? {
        fun onOff(truthy: String = "1", falsy: String = "0"): String {
            val raw = (v["on"] ?: "").trim().lowercase()
            val isOn = raw == "true" || raw == "1" || raw == "on"
            return if (isOn) truthy else falsy
        }
        return when (opId) {
            "brightness.set" -> v["value"]?.trim()
            "brightness.auto" -> onOff()
            "rotate.set" -> onOff()
            "timeout.set" -> v["ms"]?.trim()
            "dnd.set" -> onOff(truthy = "2", falsy = "0")
            "wifi.set" -> onOff(truthy = "2", falsy = "1")
            "bluetooth.set" -> onOff()
            "data.set" -> onOff()
            "airplane.set" -> onOff()
            "gps.set" -> onOff(truthy = "3", falsy = "0")
            else -> null
        }
    }
}
