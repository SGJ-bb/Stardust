package com.aicompanion.plugin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.app.KeyguardManager
import android.provider.Settings
import com.aicompanion.adb.AdbSelf
import com.aicompanion.models.ToolDefinition
import com.aicompanion.screen.ScreenRecognitionService
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 设备控制工具：把 shell(2000) 的能力包成 AI 可调用的动作。
 *
 * 设计原则（这是"能干活"和"乱点一气"的分界）：
 *  1. **每个动作都自带验证**：改设置后回读比对；点按/滑动后看界面快照版本有没有变化。
 *     验证失败会明确告诉模型"没生效"，它才会换方案，而不是宣布成功。
 *  2. **危险动作有闸门**：改 adb 开关、卸载、清数据一律拦；改系统设置只允许白名单之外的非致命键。
 *  3. **优先按文字点，不按坐标点**：列表顺序会变（QQ 联系人、微信会话），
 *     `tap_text` 用文字定位，`tap(x,y)` 只留给没有文字的图标按钮。
 */
class DevicePlugin(private val context: Context) : ToolPlugin {

    private companion object {
        const val TAG = "DevicePlugin"

        /** 这些键改了会断掉自己的通道/调试能力，直接拒绝 */
        val FORBIDDEN_KEYS = setOf(
            "adb_enabled", "adb_wifi_enabled", "development_settings_enabled",
            "adb_allowed_connection_time", "package_verifier_enable",
        )
    }

    override val name = "device"

    override val description = "直接操作安卓设备：改系统设置、点按滑动输入、截图、管理应用"

    override fun isEnabled(): Boolean = true

    override fun getDefinition() = ToolDefinition(
        name = "device",
        description = """以 shell(2000) 身份操作设备。写操作会自动验证是否真的生效。

【action 与参数】
- info                          → 通道状态、uid、屏幕分辨率（做坐标换算前先调它）
- settings_get   {ns,key}       ns = global|secure|system
- settings_put   {ns,key,value} 写完会回读比对，不一致会明确报错
- settings_delete{ns,key}
- tap            {x,y}          坐标是物理像素，左上角为 0,0
- long_press     {x,y,ms}       默认 600ms
- swipe          {x1,y1,x2,y2,ms}
- text           {text}         输入文本（自动处理空格）
- key            {key}          home|back|recent|enter|power|volume_up|volume_down
- screenshot                    → 返回本地文件路径（可用文件工具或视觉能力看）
- app_list       {filter?}      列出已安装包名
- app_start      {pkg}          用 monkey 启动主界面
- ui_tree                       → 读当前界面结构（节点、文字、可点元素、可滚动区域）。
                                 第一次操作某个 App、或者不确定界面上有什么时，先调它。
- ocr            {force?}       → 截图识别，把「屏幕上看得见但树里读不到」的文字补进来。
                                 遇到列表只有一排空格子（QQ 联系人好友列表、微信朋友圈）时用它。
- app_stop       {pkg}          强制停止
- app_clear      {pkg}          清空应用数据（危险，需用户明确要求）
- screen_state                  → 是否亮屏/锁屏

【纪律】
1. 需要点屏幕时，先用 screenshot 看清界面，再算坐标；不要凭记忆猜。
2. 删/清类动作先问用户，除非他在本次对话里已明确授权。
3. 写设置失败（回读不一致）通常是权限或键名错，换个写法或告诉用户，不要重复同一条命令。
4. 改完 adb/无线调试相关设置会被拒绝 —— 那会切断我们自己的通道。
""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf(
                        "info", "settings_get", "settings_put", "settings_delete",
                        "tap", "tap_text", "long_press", "swipe", "text", "key",
                        "screenshot", "app_list", "app_start", "app_stop", "app_clear",
                        "screen_state",
                    ),
                    "description" to "要执行的动作",
                ),
                "ns" to mapOf("type" to "string", "enum" to listOf("global", "secure", "system")),
                "key" to mapOf("type" to "string", "description" to "设置键名，或按键名(home/back/...)"),
                "value" to mapOf("type" to "string", "description" to "settings_put 的值"),
                "x" to mapOf("type" to "integer"), "y" to mapOf("type" to "integer"),
                "x1" to mapOf("type" to "integer"), "y1" to mapOf("type" to "integer"),
                "x2" to mapOf("type" to "integer"), "y2" to mapOf("type" to "integer"),
                "ms" to mapOf("type" to "integer", "description" to "时长/间隔毫秒"),
                "text" to mapOf("type" to "string"),
                "filter" to mapOf("type" to "string", "description" to "app_list 的包名过滤"),
                "pkg" to mapOf("type" to "string"),
            ),
            "required" to listOf("action"),
        ),
    )

    override suspend fun execute(arguments: String): String = withContext(Dispatchers.IO) {
        val a = runCatching { JSONObject(arguments) }.getOrElse { return@withContext "参数不是合法 JSON" }
        val action = a.optString("action").lowercase()
        try {
            when (action) {
                "info" -> info()
                "settings_get" -> settingsGet(a)
                "settings_put" -> settingsPut(a)
                "settings_delete" -> settingsDelete(a)
                "tap" -> tap(a.optInt("x"), a.optInt("y"))
                "tap_text" -> tapText(a.optString("text"))
                "long_press" -> longPress(a.optInt("x"), a.optInt("y"), a.optInt("ms", 600))
                "swipe" -> swipe(a.optInt("x1"), a.optInt("y1"), a.optInt("x2"), a.optInt("y2"), a.optInt("ms", 300))
                "text" -> inputText(a.optString("text"))
                "key" -> key(a.optString("key"))
                "screenshot" -> screenshot()
                "app_list" -> appList(a.optString("filter"))
                "app_start" -> appStart(a.optString("pkg"))
                "app_stop" -> appStop(a.optString("pkg"))
                "app_clear" -> appClear(a.optString("pkg"))
                // 主动读一次界面结构。给 AI「摸一个陌生 App」用的第一批数据：
                // 有多少节点、最深几层、哪些有文字、哪些能点。
                "ui_tree" -> com.aicompanion.screen.AutoOperator.formatScreenTreeForLLM()
                "screen_state" -> screenState()
                "ocr" -> ocrRead(a.optBoolean("force", true))
                else -> "未知 action：$action"
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "device $action 失败: ${e.message}", e)
            "执行失败：${e.javaClass.simpleName}: ${e.message}"
        }
    }

    // ------------------------------------------------------------------ 基础信息

    /**
     * 命令通道。
     *
     * 关键：`execRaw` 自己声明的 timeoutMs **不可靠** —— 它里面要先做端口探测（上万次），
     * 通道没建立时实测能卡 40 秒以上（传 3000ms 也照样卡）。
     * 所以这里再套一层硬超时：到点就返回失败，绝不让调用方陪着一起等。
     */
    private fun sh(cmd: String, timeoutMs: Int = 12000): com.aicompanion.adb.AdbShellResult {
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        return try {
            val t = pool.submit<com.aicompanion.adb.AdbShellResult> {
                AdbSelf.execRaw(context, cmd, timeoutMs)
            }
            t.get((timeoutMs + 1500).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            com.aicompanion.adb.AdbShellResult(-1, "", "命令通道无响应（超过 ${timeoutMs + 1500}ms）", false)
        } catch (e: Exception) {
            com.aicompanion.adb.AdbShellResult(-1, "", "命令通道异常：${e.message}", false)
        } finally {
            runCatching { pool.shutdownNow() }
        }
    }

    /** 设备信息。全部读系统 API —— 以前靠 `id` 命令探测通道，通道没开就整个不可用 */
    private fun info(): String {
        val pm = context.packageManager
        val ver = runCatching {
            pm.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        return buildString {
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("本 App：$ver (${context.packageName})")
            appendLine("屏幕：${context.resources.displayMetrics.widthPixels}x" +
                "${context.resources.displayMetrics.heightPixels} @${context.resources.displayMetrics.densityDpi}dpi")
            append("无障碍服务：${if (ScreenRecognitionService.getInstance() != null) "已连接" else "未连接"}")
            append("｜截屏通道：${if (com.aicompanion.screen.ScreenCaptureManager.isReady) "已就绪" else "未授权"}")
        }
    }

    // ------------------------------------------------------------------ 设置读写（带回读验证）

    private fun nsOf(a: JSONObject): String {
        val ns = a.optString("ns", "global").lowercase()
        return if (ns in setOf("global", "secure", "system")) ns else "global"
    }

    private fun settingsGet(a: JSONObject): String {
        val key = a.optString("key")
        if (key.isBlank()) return "缺少 key"
        val ns = nsOf(a)
        val v = when (ns) {
            "global" -> Settings.Global.getString(context.contentResolver, key)
            "system" -> Settings.System.getString(context.contentResolver, key)
            else -> Settings.Secure.getString(context.contentResolver, key)
        }
        return if (v == null) "$ns/$key = (未设置)" else "$ns/$key = $v"
    }

    private fun settingsPut(a: JSONObject): String {
        val ns = nsOf(a); val key = a.optString("key"); val value = a.optString("value")
        if (key.isBlank()) return "缺少 key"
        if (key in FORBIDDEN_KEYS) return "拒绝：$key 会切断调试通道，不能改"
        // 写系统设置需要 WRITE_SECURE_SETTINGS（普通 App 拿不到，只能 adb 授予）。
        // 以前走 shell，通道不通就白等；现在直接调 API，立刻得到明确的权限结论。
        return try {
            when (ns) {
                "global" -> Settings.Global.putString(context.contentResolver, key, value)
                "system" -> Settings.System.putString(context.contentResolver, key, value)
                else -> Settings.Secure.putString(context.contentResolver, key, value)
            }
            settingsGet(a).removePrefix("读取失败")
        } catch (e: SecurityException) {
            "没有权限写系统设置（需要 WRITE_SECURE_SETTINGS）。" +
                "普通 App 拿不到这个权限：要么用 adb 授予，要么改用无障碍去系统设置界面里点。"
        } catch (e: Exception) {
            "写入失败：${e.message}"
        }
    }

    private fun settingsDelete(a: JSONObject): String {
        val ns = nsOf(a); val key = a.optString("key")
        if (key.isBlank()) return "缺少 key"
        if (key in FORBIDDEN_KEYS) return "拒绝：$key 会切断调试通道，不能删"
        return try {
            val ok = when (ns) {
                "global" -> Settings.Global.putString(context.contentResolver, key, null)
                "system" -> Settings.System.putString(context.contentResolver, key, null)
                else -> Settings.Secure.putString(context.contentResolver, key, null)
            }
            if (ok) "已删除：$ns/$key" else "删除失败（系统拒绝）"
        } catch (e: SecurityException) {
            "没有权限改系统设置（需要 WRITE_SECURE_SETTINGS）"
        } catch (e: Exception) {
            "删除失败：${e.message}"
        }
    }

    // ------------------------------------------------------------------ 触摸与输入（带界面变化验证）

    private fun tap(x: Int, y: Int): String {
        // 手势优先。dispatchGesture 是无障碍自带能力，不依赖 ADB 通道。
        val before = ScreenRecognitionService.getSnapshotVersion()
        val ok = ScreenRecognitionService.performTapAt(x.toFloat(), y.toFloat())
        if (!ok) return actWithVerify("input tap $x $y", "点按($x,$y)")
        Thread.sleep(450)
        runCatching { ScreenRecognitionService.refreshAndWait(1200, false) }
        val after = ScreenRecognitionService.getSnapshotVersion()
        return if (after != before) "点按($x,$y) 已执行（界面已变化，验证通过）"
        else "点按($x,$y) 已发送（界面无变化）"
    }

    /**
     * 按文字点击。
     *
     * 为什么必须有这个动作：列表里的元素顺序是会变的（QQ 联系人、微信会话、搜索结果），
     * 用坐标点"第 3 个"今天对、明天就点错人。文字是稳定的锚点。
     *
     * 实现直接在无障碍树上找，而不是读缓存快照——快照可能是上一屏的，
     * 那样会出现"点了但点的是旧坐标"这种最难查的问题。
     */
    /**
     * 按文字点击。三级定位，全部是「每次现算位置」，不存任何固定坐标：
     *
     * 1. **无障碍树**（最快，最准）：找到文字节点 → 自己可点 → 向上找可点祖先 → 手势兜底
     * 2. **OCR**：微信、QQ 这类自绘列表根本不上报无障碍树，树里永远是空的。
     *    这时截图识别文字，拿到文字框中心就点——位置是当场算出来的，
     *    联系人顺序怎么变都不影响。
     * 3. **视觉模型**：纯图标（没有文字）的按钮，让多模态模型直接给位置。
     */
    private fun tapText(query: String): String {
        if (query.isBlank()) return "text 为空"
        val svc = ScreenRecognitionService.getInstance()
            ?: return "按文字点击需要无障碍服务，请先在设置里开启"

        val root = runCatching { svc.rootInActiveWindow }.getOrNull()
        val sample = mutableListOf<String>()
        val nodes = if (root != null) findTextNodes(root, query, sample) else emptyList()

        if (nodes.isNotEmpty()) {
            val beforePage = runCatching { ScreenRecognitionService.getCurrentPageClass() }.getOrDefault("")
            val beforeVer = ScreenRecognitionService.getSnapshotVersion()
            // 同名的节点可能有好几个（列表项、头像的资料卡入口、标题栏…）。
            // 挨个试，点完看界面有没有真的变，变了才算成功——
            // 以前找到第一个就 performAction，结果是点了头像弹出资料卡，还以为点成功了。
            for (node in nodes.take(6)) {
                val b = android.graphics.Rect()
                node.getBoundsInScreen(b)
                val h = b.height().coerceAtLeast(30).toFloat()
                val screenW = runCatching {
                    svc.resources.displayMetrics.widthPixels.toFloat()
                }.getOrDefault(1080f)

                // 一条候选有几种点法，逐个试、点完校验。
                //
                // 顺序很重要，**手势点右侧空白必须排第一**：
                // 微信列表行的文字节点常常是"头像"那一份（text 空、description 是名字），
                // 先 performAction 就会打开联系人资料页——页面确实变了，校验还判成功，
                // 结果以为进了聊天其实停在资料卡上。实测踩了两次。
                // 而右侧空白一定落在"这一行"的空白区，进的是会话。
                // 对普通按钮（发送/确定）右侧会落空，自然会继续试下一个落点。
                val attempts = listOf<() -> Boolean>(
                    { tapPoint(svc, (b.right + h).coerceAtMost(screenW - 6f), b.exactCenterY()) },
                    {
                        var cur: android.view.accessibility.AccessibilityNodeInfo? = node
                        var ok = false
                        while (cur != null) {
                            if (cur.isClickable && cur.performAction(
                                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
                                )
                            ) { ok = true; break }
                            cur = cur.parent
                        }
                        ok
                    },
                    { tapPoint(svc, b.exactCenterX(), b.exactCenterY()) },
                    { tapPoint(svc, b.left - h, b.exactCenterY()) },
                )

                for (attempt in attempts) {
                    if (!attempt()) continue
                    Thread.sleep(550)
                    val afterPage = runCatching { ScreenRecognitionService.getCurrentPageClass() }.getOrDefault("")
                    val afterVer = ScreenRecognitionService.getSnapshotVersion()
                    if (afterPage != beforePage || afterVer != beforeVer) {
                        AppLogger.i(TAG, "tap_text「$query」无障碍树命中(${nodes.size}个候选)")
                        return "已点击「$query」（无障碍树，共 ${nodes.size} 处匹配）"
                    }
                }
            }
            AppLogger.w(TAG, "tap_text「$query」无障碍树 ${nodes.size} 个候选都没反应，转 OCR")
        }

        // —— 树里没有：OCR 兜底 ——
        // 先收起软键盘。键盘上的候选词也会被 OCR 认成文字（搜「久别辞」时键盘刚好
        // 提示「久别辞」），于是"点联系人"点到了键盘上——用户看到的就是闪一下没反应。
        // ESC 只收键盘、不关页面，比 BACK 安全。
        hideKeyboardIfShown()
        AppLogger.i(TAG, "tap_text「$query」无障碍树未命中，转 OCR")

        // 最多试两轮：OCR 是有延迟的，这中间只要界面动一下（顶部弹出一条新消息横幅、
        // 列表自己刷新），算出来的坐标就作废了。所以点完必须校验界面有没有真的变，
        // 没变就重新拍一屏再点一次。
        repeat(2) { attempt ->
            val beforePage = runCatching { ScreenRecognitionService.getCurrentPageClass() }.getOrDefault("")
            val beforeVer = ScreenRecognitionService.getSnapshotVersion()
            runCatching { ScreenRecognitionService.refreshByOcr() }
            var hit = ScreenRecognitionService.findClickCandidatesMulti(query)
            if (hit.candidates.isEmpty()) {
                runCatching { ScreenRecognitionService.refreshByVision() }
                hit = ScreenRecognitionService.findClickCandidatesMulti(query)
            }

            val c = hit.candidates.firstOrNull()
            if (c == null) {
                return "屏幕上没有「$query」。当前能看到的：${
                    sample.distinct().take(12).joinToString("、").ifBlank { "（这个界面读不到元素，可能还在加载）" }
                }"
            }

            // 同一个文字框要点三个位置，因为「文字」和「它所在的那一行」不是同一个响应区：
            // 微信搜索结果点文字没反应、点头像才进会话；而「发送」这种按钮又必须点正中。
            // 所以从文字中心开始，失败就左右挪一个行高的距离再试。
            val b = c.bounds
            val h = b.height().coerceAtLeast(30).toFloat()
            val points = listOf(
                b.exactCenterX() to b.exactCenterY(),
                (b.left - h) to b.exactCenterY(),
                (b.right + h) to b.exactCenterY(),
            )

            for ((px, py) in points) {
                if (px < 3f || py < 3f) continue
                if (!tapPoint(svc, px, py)) continue
                Thread.sleep(600)
                val afterPage = runCatching { ScreenRecognitionService.getCurrentPageClass() }.getOrDefault("")
                val afterVer = ScreenRecognitionService.getSnapshotVersion()
                val changed = afterPage != beforePage || afterVer != beforeVer
                AppLogger.i(
                    TAG,
                    "tap_text「$query」第${attempt + 1}次 (${px.toInt()},${py.toInt()}) " +
                        "strategy=${hit.strategy} 命中${hit.candidates.size}处 changed=$changed"
                )
                if (changed) {
                    return "已点击「$query」（${hit.strategy}，屏幕上共 ${hit.candidates.size} 处匹配，点了最靠上的那个）"
                }
            }
            AppLogger.w(TAG, "tap_text「$query」第${attempt + 1}次三个落点都没反应，重试")
        }

        return "已点击「$query」，但界面没有变化 —— 可能那一行不可点，或界面在点击瞬间位移了"
    }

    /** 软键盘显示中就收起来（KEYCODE_ESCAPE=111，收键盘但不退页面） */
    private fun hideKeyboardIfShown() {
        runCatching {
            val shown = sh("dumpsys input_method | grep -c 'mInputShown=true'").stdout.trim()
            if (shown.isNotBlank() && shown != "0") {
                sh("input keyevent 111")
                Thread.sleep(400)
                AppLogger.i(TAG, "已收起软键盘")
            }
        }
    }

    /**
     * 在无障碍树里找文字/描述匹配的节点，按屏幕位置从上到下返回。
     *
     * 返回列表而不是单个：同一个词在列表里可能对应多个节点（行容器、头像 desc、标题栏），
     * 谁先谁后完全取决于 View 树的遍历顺序，跟用户在屏幕上看到的顺序未必一致。
     */
    private fun findTextNodes(
        root: android.view.accessibility.AccessibilityNodeInfo,
        query: String,
        sample: MutableList<String>
    ): List<android.view.accessibility.AccessibilityNodeInfo> {
        val exact = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        val descExact = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        val partial = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40) return
            runCatching {
                val t = node.text?.toString()?.trim().orEmpty()
                val d = node.contentDescription?.toString()?.trim().orEmpty()
                val label = t.ifBlank { d }
                if (label.isNotBlank()) {
                    if (sample.size < 14) sample.add(label.take(12))
                    when {
                        t.equals(query, ignoreCase = true) -> exact.add(node)
                        d.equals(query, ignoreCase = true) -> descExact.add(node)
                        label.contains(query, ignoreCase = true) -> partial.add(node)
                    }
                }
                for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
            }
        }
        walk(root, 0)

        fun topOf(n: android.view.accessibility.AccessibilityNodeInfo): Int = runCatching {
            val r = android.graphics.Rect()
            n.getBoundsInScreen(r)
            r.top
        }.getOrDefault(Int.MAX_VALUE)

        // text 匹配排在 desc 匹配前面：列表行的头像通常 text 为空、contentDescription 是
        // 联系人名，先点它就会弹出资料卡而不是进入会话。
        return when {
            exact.isNotEmpty() -> exact.sortedBy { topOf(it) }
            descExact.isNotEmpty() -> descExact.sortedBy { topOf(it) }
            else -> partial.sortedBy { topOf(it) }
        }
    }

    /** 在屏幕坐标上点一下（手势，不依赖任何节点） */
    private fun tapPoint(svc: android.accessibilityservice.AccessibilityService, x: Float, y: Float): Boolean {
        if (x <= 0f || y <= 0f) return false
        return runCatching {
            svc.dispatchGesture(
                android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(
                        android.accessibilityservice.GestureDescription.StrokeDescription(
                            android.graphics.Path().apply { moveTo(x, y) }, 0L, 60L
                        )
                    ).build(),
                null, null,
            )
        }.getOrDefault(false)
    }

    private fun longPress(x: Int, y: Int, ms: Int): String =
        actWithVerify("input swipe $x $y $x $y $ms", "长按($x,$y) ${ms}ms")

    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int): String {
        val before = ScreenRecognitionService.getSnapshotVersion()
        val ok = ScreenRecognitionService.performSwipe(
            x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), ms.toLong()
        )
        if (!ok) return actWithVerify("input swipe $x1 $y1 $x2 $y2 $ms", "滑动($x1,$y1)→($x2,$y2) ${ms}ms")
        Thread.sleep(500)
        runCatching { ScreenRecognitionService.refreshAndWait(1500, false) }
        val after = ScreenRecognitionService.getSnapshotVersion()
        return if (after != before) "滑动($x1,$y1)→($x2,$y2) 已执行（界面已变化，验证通过）"
        else "滑动($x1,$y1)→($x2,$y2) 已发送（界面无变化）"
    }

    /**
     * 输入文本。关键不是"发出去"，而是"写进去了"。
     *
     * 两条路都会骗人：ACTION_SET_TEXT 和 KEYCODE_PASTE 都可能返回 true 而输入框还是空的
     * （微信的 MMEditText 对前者就是这样）。所以每一路做完都要回读输入框内容，
     * 写进去了才算成功，否则换下一条路。
     *
     * 顺序按"快 → 慢"排：
     * 1. 无障碍 SET_TEXT（一次调用，最快）
     * 2. 剪贴板 + PASTE（自绘输入框通常吃这一套）
     * 3. shell input text（只对纯 ASCII 有效）
     */
    private fun inputText(text: String): String {
        if (text.isBlank()) return "text 为空"
        val head = text.take(6)

        fun written(): Boolean {
            val cur = runCatching { ScreenRecognitionService.readFocusedText() }.getOrDefault("")
            return cur.isNotBlank() && (cur.contains(head) || text.contains(cur.trim()))
        }

        // 1) 无障碍直接写
        val setOk = runCatching { ScreenRecognitionService.performInput("", text) }.getOrDefault(false)
        if (setOk) {
            Thread.sleep(180)
            if (written()) return "已输入「${text.take(30)}」（无障碍写入）"
        }

        // 2) 剪贴板 + 粘贴
        //    先试无障碍 ACTION_PASTE（直接对输入节点执行粘贴，Compose 也认），
        //    失败再退到 KEYCODE_PASTE 按键（原生 EditText 才吃这一套）。
        val pasteOk = runCatching {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("stardust", text))
            Thread.sleep(150)
            ScreenRecognitionService.performPaste() || sh("input keyevent 279").ok
        }.getOrDefault(false)
        if (pasteOk) {
            Thread.sleep(250)
            if (written()) return "已输入「${text.take(30)}」（剪贴板粘贴）"
        }

        // 3) 纯 ASCII 兜底
        if (text.none { it.code > 127 }) {
            val escaped = text.replace(" ", "%s").replace("'", "'\\''")
            actWithVerify("input text '$escaped'", "输入「${text.take(20)}」")
            if (written()) return "已输入「${text.take(30)}」（shell）"
        }

        val now = runCatching { ScreenRecognitionService.readFocusedText() }.getOrDefault("")
        AppLogger.w(TAG, "inputText 全部失败，输入框当前内容=「${now.take(20)}」")
        return "没写进去：请先点一下输入框让它获得焦点再输入（当前输入框：${if (now.isBlank()) "空" else "「${now.take(20)}」"}）"
    }

    private fun key(k: String): String {
        // 先走无障碍全局动作：home/back/recent 这三件事 AccessibilityService 本来就能干，
        // 不需要 App 自建的 ADB 通道。之前一律用 `input keyevent`，
        // 通道没打开时每个按键都要白等 8 秒然后失败（实测整批测试全挂在这上面）。
        val global = when (k.lowercase()) {
            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
            "recent", "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> -1
        }
        if (global >= 0) {
            val before = ScreenRecognitionService.getSnapshotVersion()
            val ok = ScreenRecognitionService.performGlobalAction(global)
            if (!ok) return "按键 $k 失败：无障碍服务未连接（可改用坐标/文字点击绕开）"
            Thread.sleep(450)
            runCatching { ScreenRecognitionService.refreshAndWait(1200, false) }
            val after = ScreenRecognitionService.getSnapshotVersion()
            return if (after != before) "按键 $k 已执行（界面已变化，验证通过）"
            else "按键 $k 已发送（界面无变化，可能本来就在目标页）"
        }
        val code = when (k.lowercase()) {
            "home" -> "KEYCODE_HOME"; "back" -> "KEYCODE_BACK"; "recent" -> "KEYCODE_APP_SWITCH"
            "enter" -> "KEYCODE_ENTER"; "power" -> "KEYCODE_POWER"
            "volume_up" -> "KEYCODE_VOLUME_UP"; "volume_down" -> "KEYCODE_VOLUME_DOWN"
            else -> if (k.startsWith("KEYCODE_")) k else return "未知按键：$k"
        }
        return actWithVerify("input keyevent $code", "按键 $code")
    }

    /**
     * 执行动作并验证"界面真的变了"。
     * 依据是我们自己的无障碍服务的快照版本号：每次窗口内容变化都会 +1。
     * 没变化≠一定失败（比如切到同一页面），所以要如实说"无法确认"，而不是断言成功。
     */
    private fun actWithVerify(cmd: String, label: String): String {
        val before = ScreenRecognitionService.getSnapshotVersion()
        val r = sh(cmd, 8000)
        if (!r.ok) return "$label 失败：${r.summary()}"
        Thread.sleep(450)
        runCatching { ScreenRecognitionService.refreshAndWait(1200, false) }
        val after = ScreenRecognitionService.getSnapshotVersion()
        return if (after != before) "$label 已执行（界面已变化，验证通过）"
        else "$label 已执行，但界面未检测到变化 —— 可能没生效或页面本身没变，建议 screenshot 确认"
    }

    // ------------------------------------------------------------------ 截图与屏幕状态

    private fun screenshot(): String {
        val bmp = com.aicompanion.screen.ScreenCaptureManager.capture()
            ?: return "截图失败：截屏通道未就绪（需要在「星尘」里点一次「允许录制屏幕」；" +
                "这台机器 Android 10，没有无障碍截屏接口）"
        return try {
            val dir = java.io.File(context.filesDir, "shots").apply { mkdirs() }
            val f = java.io.File(dir, "ai_shot_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            "已截图：${f.absolutePath}（${bmp.width}x${bmp.height}）"
        } catch (e: Exception) {
            "截图成功但保存失败：${e.message}"
        } finally {
            runCatching { bmp.recycle() }
        }
    }

    private fun screenState(): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val on = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive else @Suppress("DEPRECATION") pm.isScreenOn
        val locked = km.isKeyguardLocked
        return "亮屏：${if (on) "是" else "否"}｜锁屏：${if (locked) "是" else "否"}"
    }

    /**
     * 截图识别读屏：把「树里读不到、但屏幕上看得见」的文字补进当前快照。
     *
     * 典型场景：QQ 的联系人好友列表、微信朋友圈 —— 行容器可点但一个字都没有，
     * uiautomator / TalkBack 同样读不到（不是伪装不够，是 App 真的没上报）。
     * 补进来的每一条都带坐标，所以既能读也能点。
     */
    private fun ocrRead(force: Boolean): String {
        // 截屏通道没建立时，直接把授权界面拉起来（用户点一次就好），并把原因说清楚。
        if (!com.aicompanion.screen.ScreenCaptureManager.isReady) {
            runCatching {
                val i = android.content.Intent(context, com.aicompanion.ui.MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("request_projection", true)
                }
                context.startActivity(i)
            }
            return "截图识别还不可用：这台机器是 Android " +
                android.os.Build.VERSION.RELEASE +
                "，无障碍截屏接口（takeScreenshot）要 Android 11 才有。" +
                "已经打开「星尘」并弹出系统授权窗，请点一次「允许」——之后 OCR 就能一直用。"
        }
        // 先刷一次快照：这个动作可能被单独调用，缓存里还是上一屏的内容。
        // 刷新放在这里而不是 enrichWithOcrIfNeeded 里面 —— 那个函数可能跑在采集线程上，
        // 在里面等采集会自己等自己（实测卡死）。
        if (ScreenRecognitionService.getClickableData().size < 5) {
            ScreenRecognitionService.refreshAndWait(2500L, fullMode = true)
        }
        val added = ScreenRecognitionService.enrichWithOcrIfNeeded(force)
        val text = ScreenRecognitionService.getLastScreenText()
        val extra = if (text.contains("[截图识别补充]"))
            text.substringAfter("[截图识别补充]").trim() else ""
        return when {
            added > 0 && extra.isNotBlank() ->
                "截图识别补进了 $added 条文字（这些在无障碍树里没有）：\n$extra"
            extra.isNotBlank() ->
                "当前快照已包含截图识别内容：\n$extra"
            else ->
                "这次没有从截图里补到新文字（树里可能已经读全，或截屏/OCR 不可用）"
        }
    }

    // ------------------------------------------------------------------ 应用管理

    private fun appList(filter: String): String {
        val pm = context.packageManager
        val all = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val names = all.map { it.packageName }
            .filter { filter.isBlank() || it.contains(filter, ignoreCase = true) }
        if (names.isEmpty()) return "没有匹配「$filter」的包（共 ${all.size} 个已安装应用）"
        return "共 ${names.size} 个：\n" + names.take(60).joinToString("\n")
    }

    private fun appStart(pkg: String): String {
        if (pkg.isBlank()) return "缺少 pkg"
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return "启动失败：$pkg 没有可启动的入口（可能没装，或它没有桌面图标）"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "已启动 $pkg"
        } catch (e: Exception) {
            "启动失败：${e.message}"
        }
    }

    private fun appStop(pkg: String): String {
        if (pkg.isBlank()) return "缺少 pkg"
        // 普通 App 没有「杀掉别人」的公开 API（killBackgroundProcesses 只对自己有效），
        // 只能借命令通道。把超时压到 3 秒：通道不通时立刻说清楚，不要白等。
        val r = sh("am force-stop $pkg", 3000)
        return if (r.ok) "已强制停止 $pkg"
        else "停止失败：${r.summary()}（普通 App 无法停止其他应用，需要命令通道或用户手动操作）"
    }

    private fun appClear(pkg: String): String {
        if (pkg.isBlank()) return "缺少 pkg"
        val r = sh("pm clear $pkg", 20000)
        return if (r.ok) "已清空 $pkg 的数据（不可恢复）" else "清空失败：${r.summary()}"
    }

    private fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
