package com.aicompanion.system

import android.os.Build

/**
 * 系统能力命令目录：把「闹钟、免打扰、亮度、飞行模式……」这类系统功能
 * 用 shell 命令实现，不需要为每个功能单独写一个 Kotlin 插件。
 *
 * 为什么需要这个目录，而不是让模型直接自由拼 shell：
 *
 * 1. **命令随版本变**。实测这台 Android 10：
 *    - `cmd media_session volume` → "No shell command implementation"
 *    - `cmd wifi set-wifi-enabled` → "Security exception: Uid 2000 does not have access"
 *    - `cmd notification set_dnd` / `cmd connectivity airplane-mode` → 无输出、也没生效
 *    同一个需求在 Android 11+ 上又是另一套命令。
 *
 * 2. **静默失败太常见**。命令返回 0 但设置没变，模型会以为成功了，
 *    然后基于错误前提继续往下做。所以每个 op 都必须带 [Op.probe]：
 *    执行完立刻回读真实值，把「我设了 X / 读到 Y」一起返回给模型。
 *
 * 3. **注入**。op 的参数全部走类型 + 范围校验，不接受任意字符串拼进命令。
 *
 * 这就是"不写插件也能控系统"的正确形态：白名单目录 + 回读验证 + 自由 shell 兜底。
 */
object SystemOps {

    enum class Type { INT, BOOL, ENUM, TEXT }

    data class Param(
        val name: String,
        val type: Type,
        val hint: String,
        val min: Int = 0,
        val max: Int = 0,
        val choices: List<String> = emptyList(),
    )

    /** 风险级别：HIGH 一定要用户点头 */
    enum class Risk { LOW, MEDIUM, HIGH }

    data class Op(
        val id: String,
        val label: String,
        val params: List<Param> = emptyList(),
        val minSdk: Int = Build.VERSION_CODES.BASE,
        /** 回读命令：执行后立刻跑，用来确认真的生效 */
        val probe: String? = null,
        val risk: Risk = Risk.LOW,
        val build: (Map<String, String>, Int) -> String,
    )

    private fun boolParam(name: String = "on") = Param(name, Type.BOOL, "true / false")

    private fun b(v: Map<String, String>, key: String = "on"): Boolean =
        v[key]?.trim()?.equals("true", true) == true ||
            v[key]?.trim() == "1" ||
            v[key]?.trim()?.equals("on", true) == true

    val all: List<Op> = listOf(
        // ---------------- 时间类 ----------------
        Op(
            id = "alarm.set",
            label = "设定系统闹钟（调起系统闹钟，非本应用提醒）",
            params = listOf(
                Param("hour", Type.INT, "0-23", 0, 23),
                Param("minute", Type.INT, "0-59", 0, 59),
                Param("label", Type.TEXT, "闹钟标签（可选）"),
            ),
        ) { v, _ ->
            val h = v["hour"]!!.toInt(); val m = v["minute"]!!.toInt()
            val label = (v["label"] ?: "闹钟").replace("'", "").take(30)
            "am start -a android.intent.action.SET_ALARM " +
                "--ei android.intent.extra.alarm.HOUR $h " +
                "--ei android.intent.extra.alarm.MINUTES $m " +
                "--es android.intent.extra.alarm.MESSAGE '$label' " +
                "--ez android.intent.extra.alarm.SKIP_UI true"
        },

        Op(
            id = "timer.set",
            label = "设定计时器",
            params = listOf(
                Param("seconds", Type.INT, "1-86400", 1, 86400),
                Param("label", Type.TEXT, "标签（可选）"),
            ),
        ) { v, _ ->
            val s = v["seconds"]!!.toInt()
            val label = (v["label"] ?: "计时").replace("'", "").take(20)
            "am start -a android.intent.action.SET_TIMER " +
                "--ei android.intent.extra.alarm.LENGTH $s " +
                "--es android.intent.extra.alarm.MESSAGE '$label' " +
                "--ez android.intent.extra.alarm.SKIP_UI true"
        },

        // ---------------- 显示 ----------------
        Op(
            id = "brightness.set",
            label = "设置屏幕亮度（0-255）",
            params = listOf(Param("value", Type.INT, "0-255", 0, 255)),
            probe = "settings get system screen_brightness",
        ) { v, _ -> "settings put system screen_brightness ${v["value"]!!.toInt()}" },

        Op(
            id = "brightness.auto",
            label = "自动亮度开关",
            params = listOf(boolParam()),
            probe = "settings get system screen_brightness_mode",
        ) { v, _ -> "settings put system screen_brightness_mode ${if (b(v)) 1 else 0}" },

        Op(
            id = "rotate.set",
            label = "自动旋转开关",
            params = listOf(boolParam()),
            probe = "settings get system accelerometer_rotation",
        ) { v, _ -> "settings put system accelerometer_rotation ${if (b(v)) 1 else 0}" },

        Op(
            id = "timeout.set",
            label = "息屏时间",
            params = listOf(Param("ms", Type.INT, "5000-1800000", 5000, 1_800_000)),
            probe = "settings get system screen_off_timeout",
        ) { v, _ -> "settings put system screen_off_timeout ${v["ms"]!!.toInt()}" },

        Op(
            id = "stayon.set",
            label = "充电时保持屏幕常亮",
            params = listOf(boolParam()),
            probe = "settings get global stay_on_while_plugged_in",
        ) { v, _ -> "svc power stayon ${if (b(v)) "usb" else "false"}" },

        // ---------------- 声音 / 通知 ----------------
        Op(
            id = "dnd.set",
            label = "免打扰开关",
            params = listOf(boolParam()),
            probe = "settings get global zen_mode",
            risk = Risk.MEDIUM,
        ) { v, sdk ->
            // Android 11+ 有 cmd 接口；更老的只能直接改 zen_mode（0=关 2=全部拦截）
            if (sdk >= Build.VERSION_CODES.R) {
                "cmd notification set_dnd ${if (b(v)) "on" else "off"}"
            } else {
                "settings put global zen_mode ${if (b(v)) 2 else 0}"
            }
        },

        Op(
            id = "media.key",
            label = "媒体控制（播放/暂停/上一首/下一首/音量）",
            params = listOf(
                Param("key", Type.ENUM, "播放控制", choices = listOf("play_pause", "next", "prev", "vol_up", "vol_down", "mute")),
            ),
        ) { v, _ ->
            val code = when (v["key"]) {
                "play_pause" -> 85
                "next" -> 87
                "prev" -> 88
                "vol_up" -> 24
                "vol_down" -> 25
                "mute" -> 164
                else -> 85
            }
            // 音量的 probe 用 settings 读不到（音量在 audio service 里），故只执行
            "input keyevent $code"
        },

        // ---------------- 网络 ----------------
        Op(
            id = "wifi.set",
            label = "WiFi 开关",
            params = listOf(boolParam()),
            probe = "settings get global wifi_on",
            risk = Risk.MEDIUM,
        ) { v, _ -> "svc wifi ${if (b(v)) "enable" else "disable"}" },

        Op(
            id = "data.set",
            label = "移动数据开关",
            params = listOf(boolParam()),
            probe = "settings get global mobile_data",
            risk = Risk.MEDIUM,
        ) { v, _ -> "svc data ${if (b(v)) "enable" else "disable"}" },

        Op(
            id = "bluetooth.set",
            label = "蓝牙开关",
            params = listOf(boolParam()),
            probe = "settings get global bluetooth_on",
            risk = Risk.MEDIUM,
        ) { v, _ -> "svc bluetooth ${if (b(v)) "enable" else "disable"}" },

        Op(
            id = "airplane.set",
            label = "飞行模式（会断网，包括 WiFi）",
            params = listOf(boolParam()),
            probe = "settings get global airplane_mode_on",
            risk = Risk.HIGH,
        ) { v, on ->
            val onOff = if (b(v)) 1 else 0
            // 只改 settings 不会立刻生效，必须再广播一次让系统重读
            "settings put global airplane_mode_on $onOff; " +
                "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state ${if (b(v)) "true" else "false"}"
        },

        Op(
            id = "gps.set",
            label = "定位开关",
            params = listOf(boolParam()),
            probe = "settings get secure location_mode",
            risk = Risk.MEDIUM,
        ) { v, _ -> "settings put secure location_mode ${if (b(v)) 3 else 0}" },

        Op(
            id = "net.info",
            label = "查网络状态（IP、WiFi 名）",
        ) { _, _ -> "ip -4 addr show wlan0; dumpsys wifi | grep -m1 'mWifiInfo'" },

        // ---------------- 截屏 / 录屏 ----------------
        Op(
            id = "screenshot",
            label = "截屏并返回文件路径",
            params = listOf(Param("name", Type.TEXT, "文件名（可选）")),
        ) { v, _ ->
            val n = (v["name"] ?: "shot_${System.currentTimeMillis()}").replace(Regex("[^A-Za-z0-9_.-]"), "")
            "screencap -p /sdcard/Pictures/$n.png; ls -l /sdcard/Pictures/$n.png"
        },

        Op(
            id = "record.set",
            label = "录屏（最长 180 秒）",
            params = listOf(Param("seconds", Type.INT, "1-180", 1, 180)),
            risk = Risk.MEDIUM,
        ) { v, _ ->
            val s = v["seconds"]!!.toInt()
            "screenrecord --time-limit $s /sdcard/Pictures/rec_${System.currentTimeMillis()}.mp4"
        },

        // ---------------- 设备状态 ----------------
        Op(id = "battery.info", label = "电池信息") { _, _ -> "dumpsys battery | head -20" },

        Op(id = "storage.info", label = "存储空间") { _, _ -> "df -h /data /storage/emulated 2>/dev/null" },

        Op(id = "screen.off", label = "熄屏", risk = Risk.MEDIUM) { _, _ -> "input keyevent 26" },

        Op(
            id = "vibrate.set",
            label = "震动",
            params = listOf(Param("ms", Type.INT, "50-3000", 50, 3000)),
        ) { v, _ -> "cmd vibrator vibrate ${v["ms"]!!.toInt()}" },

        // ---------------- 应用 ----------------
        Op(
            id = "app.install",
            label = "安装 APK（需给绝对路径）",
            params = listOf(Param("path", Type.TEXT, "/sdcard/xxx.apk")),
            risk = Risk.HIGH,
        ) { v, _ ->
            val p = v["path"]!!.replace(Regex("[^A-Za-z0-9_./-]"), "")
            "pm install -r '$p'"
        },

        Op(
            id = "app.uninstall",
            label = "卸载应用",
            params = listOf(Param("package", Type.TEXT, "包名")),
            risk = Risk.HIGH,
        ) { v, _ ->
            val p = v["package"]!!.replace(Regex("[^A-Za-z0-9_.]"), "")
            "pm uninstall '$p'"
        },

        Op(
            id = "cache.clear",
            label = "清除应用全部数据（等于重置该应用）",
            params = listOf(Param("package", Type.TEXT, "包名")),
            risk = Risk.HIGH,
        ) { v, _ ->
            val p = v["package"]!!.replace(Regex("[^A-Za-z0-9_.]"), "")
            "pm clear '$p'"
        },
    )

    fun find(id: String): Op? = all.firstOrNull { it.id == id }

    /** 当前系统上可用的 op（按 minSdk 过滤） */
    fun available(sdk: Int = Build.VERSION.SDK_INT): List<Op> = all.filter { sdk >= it.minSdk }

    /** 给模型看的目录文本 */
    fun catalog(sdk: Int = Build.VERSION.SDK_INT): String {
        val sb = StringBuilder("当前系统 Android API $sdk，可用系统操作：\n")
        available(sdk).forEach { op ->
            val ps = if (op.params.isEmpty()) "" else
                op.params.joinToString(", ") { p ->
                    when (p.type) {
                        Type.INT -> "${p.name}(整数 ${p.min}-${p.max})"
                        Type.BOOL -> "${p.name}(true/false)"
                        Type.ENUM -> "${p.name}(${p.choices.joinToString("/")})"
                        Type.TEXT -> "${p.name}(文本)"
                    }
                }
            sb.appendLine("- ${op.id}：${op.label}${if (ps.isBlank()) "" else "　参数：$ps"}")
        }
        return sb.toString().trimEnd()
    }

    /** 校验参数，返回错误信息或 null */
    fun validate(op: Op, values: Map<String, String>): String? {
        for (p in op.params) {
            val raw = values[p.name]
            if (raw.isNullOrBlank()) {
                if (p.type == Type.TEXT) continue      // 文本参数可省略
                return "缺少参数 ${p.name}（${p.hint}）"
            }
            when (p.type) {
                Type.INT -> {
                    val n = raw.toIntOrNull() ?: return "${p.name} 必须是整数"
                    if (n < p.min || n > p.max) return "${p.name} 必须在 ${p.min}-${p.max} 之间"
                }
                Type.ENUM -> if (raw !in p.choices) return "${p.name} 只能是 ${p.choices.joinToString("/")}"
                Type.BOOL -> {
                    val ok = setOf("true", "false", "1", "0", "on", "off")
                    if (raw.lowercase() !in ok) return "${p.name} 只能是 true/false"
                }
                Type.TEXT -> Unit
            }
        }
        return null
    }
}
