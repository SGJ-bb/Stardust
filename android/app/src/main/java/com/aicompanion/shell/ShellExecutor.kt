package com.aicompanion.shell

import android.content.Context
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit

/**
 * 安卓命令执行器：让 AI 能在设备上跑 shell 命令。
 *
 * 三级通道（按能力从低到高，自动降级）：
 * 1. APP（默认可用，无需 root）
 *    `Runtime.exec(["sh","-c",cmd])`，以本应用 UID 运行。可用：
 *    getprop / pm list packages / dumpsys（部分）/ am start（拉起页面）/ 文件读写（自己沙箱 + 授权目录）/
 *    logcat（仅本应用）/ cmd 的少量子命令。不可用：screencap、settings put、input tap（需 INJECT_EVENTS）。
 * 2. ROOT（设备已 root 时可用）
 *    `su -c cmd`，等同 root shell，能力完整。
 * 3. SHIZUKU（推荐，无需 root）
 *    通过 Shizuku 拿到 ADB(shell UID) 执行 `newProcess`。可执行 input tap/text、settings put、
 *    screencap、dumpsys、pm grant 等 ADB 级命令。需要用户在 Shizuku 中授权本应用。
 *    这里用反射调用，避免引入编译期依赖 —— 未安装/未授权时自动跳过。
 */
object ShellExecutor {

    private const val TAG = "ShellExecutor"
    private const val MAX_OUTPUT_CHARS = 8000

    /** 执行通道 */
    enum class Mode {
        /** 自动：shizuku → adb(自建) → root → app */
        AUTO, APP, ROOT, SHIZUKU, ADB;

        companion object {
            fun from(value: String): Mode = when (value.lowercase()) {
                "app" -> APP
                "root" -> ROOT
                "shizuku" -> SHIZUKU
                "adb" -> ADB
                else -> AUTO
            }
        }
    }

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val mode: String,
        val timedOut: Boolean = false
    ) {
        val success: Boolean get() = !timedOut && exitCode == 0

        fun toText(): String {
            val sb = StringBuilder()
            sb.append("通道: ").append(mode)
            sb.append("  退出码: ").append(if (timedOut) "(超时)" else exitCode.toString())
            sb.append('\n')
            if (stdout.isNotBlank()) {
                sb.append("--- stdout ---\n").append(stdout.take(MAX_OUTPUT_CHARS))
                if (stdout.length > MAX_OUTPUT_CHARS) sb.append("\n…(输出过长已截断)")
                sb.append('\n')
            }
            if (stderr.isNotBlank()) {
                sb.append("--- stderr ---\n").append(stderr.take(MAX_OUTPUT_CHARS))
                if (stderr.length > MAX_OUTPUT_CHARS) sb.append("\n…(输出过长已截断)")
                sb.append('\n')
            }
            if (stdout.isBlank() && stderr.isBlank()) sb.append("(无输出)\n")
            return sb.toString().trim()
        }
    }

    // ==================== 能力探测 ====================

    @Volatile private var rootCached: Boolean? = null
    @Volatile private var shizukuCached: Boolean? = null
    @Volatile private var shizukuCheckedAt = 0L
    private const val SHIZUKU_CACHE_MS = 10_000L

    /** 是否可用 root（su -c id 返回 uid=0） */
    fun detectRoot(force: Boolean = false): Boolean {
        rootCached?.let { if (!force) return it }
        val ok = try {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            val finished = p.waitFor(3000, TimeUnit.MILLISECONDS)
            if (!finished) p.destroyForcibly()
            finished && out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
        rootCached = ok
        return ok
    }

    /** 是否已授权 Shizuku（服务未启动/未安装都返回 false） */
    fun detectShizuku(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        shizukuCached?.let { if (!force && now - shizukuCheckedAt < SHIZUKU_CACHE_MS) return it }
        val ok = ShizukuBridge.hasPermission()
        shizukuCached = ok
        shizukuCheckedAt = now
        return ok
    }

    /** 当前可用的执行通道（用于设置页展示） */
    fun availableModes(): List<String> {
        val list = mutableListOf("app")
        if (detectShizuku()) list.add("shizuku")
        if (com.aicompanion.adb.AdbSelf.isReady() ||
            com.aicompanion.shell.ShellDaemonClient.isConnected()
        ) list.add("adb")
        if (detectRoot()) list.add("root")
        return list
    }

    fun capabilityReport(): String = buildString {
        append("APP(免root)：可用\n")
        append("Shizuku(ADB级)：").append(ShizukuBridge.statusText()).append('\n')
        val adbState = com.aicompanion.adb.AdbSelf.state
        append("自建ADB通道：").append(
            if (adbState.connected) "已连接 ${adbState.uid}" else "未连接${if (adbState.error.isNotBlank()) "（${adbState.error}）" else ""}"
        ).append('\n')
        append("ROOT：").append(if (detectRoot()) "可用" else "不可用")
    }

    // ==================== 危险命令防护 ====================

    /** 命中即视为高危（需要用户在设置里显式放行） */
    private val DANGEROUS_PATTERNS = listOf(
        Regex("""\brm\s+-[a-zA-Z]*[rf]"""),
        Regex("""\bpm\s+(uninstall|clear|disable|hide)\b"""),
        Regex("""\bpm\s+delete-package"""),
        Regex("""\bwipe\b"""),
        Regex("""\bmkfs\b"""),
        Regex("""\bdd\s+if="""),
        Regex(""">\s*/dev/block"""),
        Regex("""\breboot\b"""),
        Regex("""\bformat\b"""),
        Regex("""\bsettings\s+(put|delete)\s+secure"""),
        Regex("""\bsettings\s+(put|delete)\s+global"""),
        Regex("""\bam\s+force-stop\s+android"""),
        Regex("""\bsvc\s+(power|data|wifi)\b"""),
        Regex("""\bchmod\s+777\s+/"""),
        Regex("""\bkill(all)?\b"""),
        Regex("""\bmenu\b"""),
        Regex("""\bmount\s+-o\s+rw\s+/system""")
    )

    private val SETTINGS_PUT = Regex("""^settings\s+put\s+(secure|system|global)\s+([A-Za-z0-9_.\-]+)\s+(.+)$""")

    /**
     * 应用侧执行 `settings put <ns> <key> <value>`。
     * 成功返回 ShellResult(channel="app-settings")；无权限/不匹配返回 null（交给命令通道）。
     */
    private fun tryAppSideSettingsPut(context: Context, cmd: String): ShellResult? {
        val m = SETTINGS_PUT.find(cmd) ?: return null
        val ns = m.groupValues[1]
        val key = m.groupValues[2]
        val value = m.groupValues[3].trim().trim('\'').trim('"').replace("\\ ", " ")
        return try {
            val cr = context.contentResolver
            val ok = when (ns) {
                "secure" -> android.provider.Settings.Secure.putString(cr, key, value)
                "global" -> android.provider.Settings.Global.putString(cr, key, value)
                "system" -> android.provider.Settings.System.putString(cr, key, value)
                else -> false
            }
            if (ok) ShellResult(0, "", "", "app-settings") else null
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun isDangerous(command: String): Boolean =
        DANGEROUS_PATTERNS.any { it.containsMatchIn(command) }

    // ==================== 执行 ====================

    /**
     * 执行一条命令。
     * @param mode 指定通道；AUTO 时按 shizuku → root → app 的顺序选择第一个可用的
     * @param allowDangerous 高危命令放行（由设置项控制）
     */
    suspend fun run(
        context: Context,
        command: String,
        timeoutMs: Long = 20000L,
        mode: Mode = Mode.AUTO,
        allowDangerous: Boolean = false
    ): ShellResult = withContext(Dispatchers.IO) {
        val cmd = command.trim()
        if (cmd.isBlank()) {
            return@withContext ShellResult(-1, "", "命令为空", "app")
        }
        if (isDangerous(cmd) && !allowDangerous) {
            return@withContext ShellResult(
                -1, "",
                "该命令被判定为高危操作（可能删除数据/卸载应用/改系统设置），已拦截。" +
                    "如确实要执行，请在 设置 → 无障碍 → 命令执行 中打开「允许高危命令」。",
                "guard"
            )
        }

        // 免 shell 快路径：拿到 WRITE_SECURE_SETTINGS（adb_elevate.bat 一次性授予、重启保留）后，
        // "settings put" 由应用自己写，不再依赖 Shizuku / 自建 ADB。
        tryAppSideSettingsPut(context, cmd)?.let { return@withContext it }

        val resolved = when (mode) {
            Mode.AUTO -> when {
                detectShizuku() -> Mode.SHIZUKU
                // 自建 ADB 通道已连上就直接用（省掉装 Shizuku）
                com.aicompanion.adb.AdbSelf.isReady() -> Mode.ADB
                detectRoot() -> Mode.ROOT
                else -> Mode.APP
            }
            else -> mode
        }

        when (resolved) {
            Mode.SHIZUKU -> runViaShizuku(cmd, timeoutMs)
            Mode.ADB -> {
                val r = com.aicompanion.adb.AdbSelf.execRaw(context, cmd, timeoutMs.toInt())
                ShellResult(r.exitCode, r.stdout, r.stderr, "adb")
            }
            Mode.ROOT -> runProcess(arrayOf("su", "-c", cmd), timeoutMs, "root")
            else -> runProcess(arrayOf("sh", "-c", cmd), timeoutMs, "app")
        }
    }

    private fun runViaShizuku(command: String, timeoutMs: Long): ShellResult {
        val process = ShizukuBridge.newProcess(command)
            ?: return ShellResult(
                -1, "",
                "Shizuku 通道不可用（${ShizukuBridge.statusText()}）。" +
                    "请安装 Shizuku、用无线调试启动服务，然后在 设置 → 无障碍 → 命令执行 里点「授权 Shizuku」。",
                "shizuku"
            )
        return capture(process, timeoutMs, "shizuku")
    }

    private fun runProcess(argv: Array<String>, timeoutMs: Long, modeName: String): ShellResult {
        return try {
            val pb = ProcessBuilder(*argv).redirectErrorStream(false)
            val process = pb.start()
            capture(process, timeoutMs, modeName)
        } catch (e: Exception) {
            AppLogger.e(TAG, "runProcess($modeName) 失败: ${e.message}")
            ShellResult(-1, "", "命令启动失败：${e.message}", modeName)
        }
    }

    private fun capture(process: Process, timeoutMs: Long, modeName: String): ShellResult {
        var stdout = ""
        var stderr = ""
        val outThread = thread(name = "shell-out") { stdout = process.inputStream.readTextSafe() }
        val errThread = thread(name = "shell-err") { stderr = process.errorStream.readTextSafe() }
        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            false
        }
        if (!finished) {
            // 先杀进程释放管道，避免读取线程永久阻塞
            try { process.destroyForcibly() } catch (_: Exception) {}
            outThread.join(500)
            errThread.join(500)
            return ShellResult(-1, stdout, stderr.ifBlank { "命令执行超时（${timeoutMs}ms），已强制终止" }, modeName, timedOut = true)
        }
        outThread.join(800)
        errThread.join(800)
        val code = try { process.exitValue() } catch (_: Exception) { -1 }
        return ShellResult(code, stdout.trim(), stderr.trim(), modeName)
    }

    private fun InputStream.readTextSafe(): String = try {
        bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        ""
    }
}
