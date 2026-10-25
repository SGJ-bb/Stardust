package com.aicompanion.screen

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.aicompanion.util.AppLogger
import kotlin.concurrent.thread

/**
 * 只做一件事：把当前前台界面（比如微信）的 UI 树体检结果打到 logcat。
 *
 * 为什么单独开一个服务：探针必须读"别人的窗口"，所以不能把本应用拉回前台
 * （MainActivity 一启动，活动窗口就变成我们自己的了）。
 *
 * 用法：
 *   adb shell am startservice -n com.aicompanion/.screen.UiProbeService              # UI 树体检
 *   adb shell am startservice -n com.aicompanion/.screen.UiProbeService --ez ocr true # 只测 OCR 兜底
 */
class UiProbeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wantOcr = intent?.getBooleanExtra("ocr", false) == true
        val wantVision = intent?.getBooleanExtra("vision", false) == true
        val wantReset = intent?.getBooleanExtra("reset_hostile", false) == true
        val wantWake = intent?.getBooleanExtra("wake", false) == true
        val wantLlm = intent?.getBooleanExtra("llm", false) == true
        val wantHeal = intent?.getBooleanExtra("heal", false) == true
        val shellCmd = intent?.getStringExtra("shell")
        // 配对格式：--es pair "777652:127.0.0.1:43335"
        val pairSpec = intent?.getStringExtra("pair")
        val tapText = intent?.getStringExtra("tap")
        thread(name = "ui-probe") {
            // 自愈/清名单这类动作不依赖无障碍服务本身（旧写法放在 svc==null 之后，
            // 结果"无障碍被关了"时正好走不到自愈分支 —— 真机踩过）。
            if (wantHeal) {
                val before = A11ySelfHeal.isEnabled(this)
                val r = A11ySelfHeal.heal(this)
                AppLogger.i("UiProbe", "HEAL before=$before result=$r after=${A11ySelfHeal.isEnabled(this)} " +
                    "component=${A11ySelfHeal.current(this)} canHeal=${A11ySelfHeal.canHeal(this)}")
                stopSelf()
                return@thread
            }
            if (wantReset) {
                ScreenRecognitionService.clearA11yHostileCache()
                AppLogger.i("UiProbe", "已清空「不上报树」名单")
                stopSelf()
                return@thread
            }
            // 逐行 dump 全量节点：排查"为什么这个页面读不到内容"时用
            val wantDump = intent?.getBooleanExtra("dump", false) == true
            if (wantDump) {
                runCatching {
                    ScreenRecognitionService.refreshAndWait(3000L, fullMode = true)
                    val service = ScreenRecognitionService.getInstance()
                    val lines = if (service != null) rawDump(service)
                                else ScreenRecognitionService.dumpNodes()
                    AppLogger.i("UiDump", "=== 共 ${lines.size} 个节点（原始全字段）：${ScreenRecognitionService.getCurrentPageClass()} ===")
                    lines.forEach { AppLogger.i("UiDump", it) }
                    AppLogger.i("UiDump", "=== END ===")
                }.onFailure { AppLogger.e("UiDump", "dump 失败: ${it.message}") }
                stopSelf()
                return@thread
            }
            // 通用工具调用：必须走 Service 而不是 Activity ——
            // Activity 一启动就把自己顶到前台，learn_ui 学到的会变成星尘自己的界面。
            val toolSpec = intent?.getStringExtra("tool_call")
            if (!toolSpec.isNullOrBlank()) {
                runCatching {
                    val o = org.json.JSONObject(toolSpec)
                    val mgr = com.aicompanion.action.AIActionManager(this)
                    val out = kotlinx.coroutines.runBlocking {
                        mgr.executeTool(o.optString("name"), o.optString("args", "{}"))
                    }
                    AppLogger.i("UiProbe", "TOOL[${o.optString("name")}] ${out.replace("\n", " ⏎ ").take(700)}")
                }.onFailure { AppLogger.e("UiProbe", "TOOL 失败: ${it.javaClass.simpleName}: ${it.message}") }
                stopSelf()
                return@thread
            }
            if (!pairSpec.isNullOrBlank()) {
                val parts = pairSpec.split(":")
                val code = parts.getOrNull(0).orEmpty().trim()
                val host = parts.getOrNull(1)?.ifBlank { null } ?: "127.0.0.1"
                val port = parts.getOrNull(2)?.toIntOrNull() ?: 0
                AppLogger.i("UiProbe", "PAIR-BEGIN code=$code host=$host port=$port")
                val r = com.aicompanion.adb.AdbPairing.pair(this, host, port, code)
                AppLogger.i("UiProbe", "PAIR-END ok=${r.ok} msg=${r.message}")
                r.log.forEach { AppLogger.i("UiProbe", "PAIR| $it") }
                stopSelf()
                return@thread
            }
            val svc = ScreenRecognitionService.getInstance()
            if (svc == null) {
                AppLogger.e("UiProbe", "无障碍服务未运行")
            } else if (!shellCmd.isNullOrBlank()) {
                val r = kotlinx.coroutines.runBlocking {
                    // 诊断入口放行高危：只用于自测快路径，正式执行仍受设置里的开关约束
                    com.aicompanion.shell.ShellExecutor.run(
                        this@UiProbeService, shellCmd, 8000L, allowDangerous = true
                    )
                }
                AppLogger.i("UiProbe", "SHELL[$shellCmd] exit=${r.exitCode} mode=${r.mode} out=${r.stdout.take(80)} err=${r.stderr.take(120)}")
            } else if (wantLlm) {
                // 模型真正看到的文本。优先走"直接遍历当前窗口"这条不依赖缓存的路径，
                // 因为快照缓存会因为超时/退判拿到上一次浅采集的结果（实测 depth 只到 9，
                // 而微信会话名在 depth 23）。拿不到窗口时才退回原来的实现。
                AppLogger.i("UiProbe", "LLM-BEGIN")
                val svc = ScreenRecognitionService.getInstance()
                val text = if (svc != null) {
                    ScreenRecognitionService.keepAwakeForAWhile()
                    val lines = rawDump(svc)
                    if (lines.isEmpty()) AutoOperator.formatScreenTreeForLLM()
                    else buildString {
                        appendLine("当前屏幕节点（直接遍历，共 ${lines.size} 条）：")
                        lines.forEach { appendLine(it) }
                    }
                } else {
                    AutoOperator.formatScreenTreeForLLM()
                }
                // 分段打印：一条 logcat 装不下几百行，截断后就看不出后半截有什么了
                text.lineSequence().take(300).forEach { AppLogger.i("UiProbe", "LLM| " + it.take(220)) }
                AppLogger.i("UiProbe", "LLM-END chars=${text.length}")
            } else if (wantWake) {
                AppLogger.i("UiProbe", "WAKE-BEGIN on=${ScreenWaker.isScreenOn(this)} locked=${ScreenWaker.isLocked(this)}")
                val st = ScreenWaker.ensureAwake(this)
                AppLogger.i("UiProbe", "WAKE-END " + ScreenWaker.describe(st))
            } else if (wantVision) {
                AppLogger.i("UiProbe", "VISION-BEGIN")
                AppLogger.i("UiProbe", ScreenRecognitionService.refreshByVision())
                AppLogger.i("UiProbe", "VISION-END")
            } else if (wantOcr) {
                // 只验证 OCR 兜底是否能读出当前页面
                AppLogger.i("UiProbe", "OCR-BEGIN")
                AppLogger.i("UiProbe", ScreenRecognitionService.refreshByOcr())
                AppLogger.i("UiProbe", "OCR-END")
            } else if (!tapText.isNullOrBlank()) {
                // 验证"按文字点击"：OCR/视觉目标应走坐标手势
                AppLogger.i("UiProbe", "TAP-BEGIN $tapText")
                val hit = ScreenRecognitionService.findClickCandidatesMulti(tapText)
                AppLogger.i("UiProbe", "候选=${hit.candidates.size} 策略=${hit.strategy}")
                val ok = hit.candidates.firstOrNull()
                    ?.let { ScreenRecognitionService.performClickData(it) } ?: false
                AppLogger.i("UiProbe", "TAP-END ok=$ok")
            } else {
                AppLogger.i("UiProbe", UiTreeProbe.probe(svc).toText())
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /**
     * 直接遍历原始 AccessibilityNodeInfo，把有信息的节点拼成紧凑文本。
     *
     * 为什么不用现成的 JSON 树：那条链路依赖 refreshAndWait 的快照缓存，
     * 实测超时后会拿到上一次浅采集的结果（nodeCount 有值但最大 depth 只有 9，
     * 而微信会话名挂在 depth 23）——表现为"看起来读了，其实什么都没读到"。
     * 这里直接走 rootInActiveWindow，不依赖缓存。
     */
    private fun rawDump(service: android.accessibilityservice.AccessibilityService, verbose: Boolean = false): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?, d: Int) {
            if (n == null || d > 40 || out.size > 600) return
            val text = runCatching { n.text?.toString()?.trim().orEmpty() }.getOrDefault("")
            val desc = runCatching { n.contentDescription?.toString()?.trim().orEmpty() }.getOrDefault("")
            val hint = runCatching { n.hintText?.toString()?.trim().orEmpty() }.getOrDefault("")
            val state = runCatching { n.stateDescription?.toString()?.trim().orEmpty() }.getOrDefault("")
            val tip = runCatching { n.tooltipText?.toString()?.trim().orEmpty() }.getOrDefault("")
            val click = runCatching { n.isClickable }.getOrDefault(false)
            val childCount = runCatching { n.childCount }.getOrDefault(0)
            val cls = runCatching { n.className?.toString()?.substringAfterLast('.') ?: "" }.getOrDefault("")
            val id = runCatching { n.viewIdResourceName?.substringAfterLast('/') ?: "" }.getOrDefault("")

            val label = text.ifBlank { desc }
            val worth = label.isNotBlank() || hint.isNotBlank() || state.isNotBlank() || tip.isNotBlank() || click
            if (worth || verbose) {
                val r = android.graphics.Rect()
                runCatching { n.getBoundsInScreen(r) }
                val extra = buildString {
                    if (hint.isNotBlank()) append(" hint=「$hint」")
                    if (state.isNotBlank()) append(" state=「$state」")
                    if (tip.isNotBlank()) append(" tip=「$tip」")
                    if (id.isNotBlank()) append(" #$id")
                    if (click) append(" [可点]")
                    if (childCount > 0) append(" child=$childCount")
                }
                out.add("d$d $cls「$label」(${r.left},${r.top},${r.right},${r.bottom})$extra")
            }
            for (i in 0 until childCount) walk(runCatching { n.getChild(i) }.getOrNull(), d + 1)
        }
        walk(runCatching { service.rootInActiveWindow }.getOrNull(), 0)
        return out
    }
}
