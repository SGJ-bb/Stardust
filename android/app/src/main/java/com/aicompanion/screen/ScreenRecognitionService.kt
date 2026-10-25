package com.aicompanion.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 无障碍屏幕识别与操作服务。
 *
 * 设计要点（v2，修复"看不见 UI 树 / 拿不到某些板块 / 未找到可点击元素"）：
 * 1. 多窗口采集：除 rootInActiveWindow 外，还遍历 dialogs / popup / IME 等独立窗口，
 *    这些窗口的内容此前完全不可见（典型："进入 App 后拿不到某个板块"）。
 * 2. 点击容器标签继承：Android 里 clickable 常常挂在无文字的容器
 *    （ConstraintLayout / CardView / RecyclerView item）上，文字在子 TextView 里。
 *    旧实现只取节点自身 text/desc，于是整片可点区域被丢弃 → "明明全是可点击元素却找不到"。
 * 3. 无标签可点元素也会列出（带 index + 坐标），保证 LLM 至少能用索引点。
 * 4. 深度/元素上限可配置，命中上限时输出截断标记，不再静默丢弃整棵剩余子树。
 * 5. 同步刷新（refreshAndWait）：旧实现异步刷新后立刻读缓存，
 *    规划器实际看到的是"上一个页面"的内容 —— 这是最隐蔽也最致命的一处。
 * 6. 点击解析多级兜底：路径 → 精确 bounds → 中心点命中 → 最近可点祖先 → 坐标手势。
 */
/**
 * 之所以是 open：伪装服务 [com.google.android.accessibility.selecttospeak.SelectToSpeakService]
 * 要继承它（微信只对"系统类名"的无障碍服务放行节点树）。
 */
open class ScreenRecognitionService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenRecognitionService"

        /** 根节点路径标记（子节点路径为 "0"、"0-2"…，根用 R 与之区分） */
        private const val ROOT_PATH = "R"

        /** 上一次 OCR 兜底识别出多少行（0 或极少 ⇒ 该考虑视觉模型） */
        @Volatile
        var lastOcrCount: Int = 0
            private set

        /** 服务自己的 Context：给 AutoOperator 这类没有 context 的调用方做 OCR 兜底 */
        @Volatile
        private var appContext: Context? = null

        private var currentInstance: ScreenRecognitionService? = null

        private val _lastScreenText = AtomicReference("")
        private val _lastClickableData = AtomicReference<List<ClickableData>>(emptyList())
        /** 结构化 UI 树（含稳定 id / 父子关系 / role / bounds），供"树视图"模式喂给 LLM */
        private val _lastUiTree = AtomicReference<List<UiNode>>(emptyList())

        /**
         * 输入法窗口占据的屏幕区域。
         *
         * 为什么必须单独记：截图 OCR 会把软键盘一起认出来，键盘上的候选词
         * （比如搜「久别辞」时键盘提示的「久别辞」）会混进可点候选里，
         * 于是"点联系人"点到了键盘上——用户看到的就是搜索框闪一下、什么都没发生。
         */
        private val _imeBounds = AtomicReference<android.graphics.Rect?>(null)
        /** 快照版本号：每次成功采集 +1，供外部判断数据新鲜度 */
        private val snapshotVersion = java.util.concurrent.atomic.AtomicLong(0)
        @Volatile private var lastRefreshTime = 0L
        /** 最近一次 WINDOW_STATE_CHANGED 的包名/类名，用于给页面起名 */
        @Volatile private var lastEventPackage = ""
        @Volatile private var lastEventClass = ""

        private val bgExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ScreenRefresh").also { it.isDaemon = true }
        }
        private val refreshPending = AtomicBoolean(false)
        private val refreshDirty = AtomicBoolean(false)
        private val screenChangeLatch = AtomicReference(CountDownLatch(1))

        // ===== 可调采集参数（由 SettingsManager 注入，默认值偏宽松以确保看得全）=====
        @Volatile var maxDepth = 8
        @Volatile var maxElements = 150
        /** 点击容器从子孙节点继承标签时向下查找的层数 */
        @Volatile var labelInheritDepth = 4

        fun getInstance(): ScreenRecognitionService? = currentInstance
        fun getLastScreenText(): String = _lastScreenText.get()
        fun getClickableData(): List<ClickableData> = _lastClickableData.get()

        /** 结构化 UI 树（含 id/pid/role/bounds） */
        fun getUiTree(): List<UiNode> = _lastUiTree.get()

        /** 有文字/描述说明的节点数，用来判断"这一屏是不是被深度截断了" */
        fun textNodeCount(): Int = _lastUiTree.get().count { it.text.isNotBlank() || it.desc.isNotBlank() }

        /**
         * 把当前缓存的节点逐行描述出来，给调试用。
         *
         * 为什么不用 JSON：一个页面的完整树是 6000+ 字符，logcat 单条会截断，
         * 逐行打才能一眼看出"到底哪一层丢了内容"（比如微信列表项在 depth 9 之外）。
         */
        fun dumpNodes(): List<String> = _lastUiTree.get().map { n ->
            "#${n.seq} d${n.depth} ${n.cls.ifBlank { "-" }}" +
                " | text=「${n.text}」 desc=「${n.desc}」" +
                " | ${n.bounds.left},${n.bounds.top},${n.bounds.right},${n.bounds.bottom}" +
                " | click=${n.clickable} scroll=${n.scrollable}"
        }

        /** 按 id 查找树节点（把 LLM 传来的 "n14" target 解析为实际节点） */
        fun getUiNodeById(id: String): UiNode? {
            if (id.isBlank()) return null
            val key = id.removePrefix("n")
            val idx = key.toIntOrNull() ?: return null
            return _lastUiTree.get().firstOrNull { it.seq == idx }
        }

        /** 按 id 解析出可点击索引（供 performClickByIndex），非可点击返回 -1 */
        fun resolveClickIndexById(id: String): Int {
            return getUiNodeById(id)?.index ?: -1
        }

        /**
         * 把结构化 UI 树序列化为紧凑 JSON（树视图模式喂给 LLM）。
         * 只输出带 role/身份的节点，天然携带层级(pid) —— 这就是"让 AI 看到 UI 树"的核心形态。
         */
        fun getUiTreeJson(maxNodes: Int = 200): String {
            val nodes = _lastUiTree.get()
            val service = currentInstance
            val metrics = service?.resources?.displayMetrics
            val arr = JSONArray()
            var kept = 0
            var truncated = false
            for (n in nodes) {
                if (kept >= maxNodes) { truncated = true; break }
                val obj = JSONObject()
                obj.put("id", n.id)
                obj.put("pid", n.pid)
                obj.put("role", n.role)
                obj.put("depth", n.depth)
                if (n.text.isNotBlank()) obj.put("text", n.text)
                if (n.desc.isNotBlank()) obj.put("desc", n.desc)
                obj.put("bounds", JSONArray().put(n.bounds.left).put(n.bounds.top).put(n.bounds.right).put(n.bounds.bottom))
                obj.put("center", JSONArray().put(n.bounds.centerX()).put(n.bounds.centerY()))
                obj.put("clickable", n.clickable)
                if (n.index >= 0) obj.put("index", n.index)
                n.checked?.let { obj.put("checked", it) }
                if (!n.enabled) obj.put("enabled", false)
                if (n.selected) obj.put("selected", true)
                if (n.scrollable) obj.put("scrollable", true)
                if (n.cls.isNotBlank()) obj.put("cls", n.cls)
                if (n.viewId.isNotBlank()) obj.put("viewId", n.viewId)
                obj.put("src", n.src)
                arr.put(obj)
                kept++
            }
            val meta = JSONObject()
            meta.put("package", getCurrentPackageName())
            if (getCurrentPageClass().isNotBlank()) meta.put("page", getCurrentPageClass())
            if (metrics != null) {
                meta.put("w", metrics.widthPixels)
                meta.put("h", metrics.heightPixels)
            }
            meta.put("truncated", truncated)
            meta.put("nodeCount", kept)
            meta.put("nodes", arr)
            return meta.toString()
        }
        fun isRefreshPending(): Boolean = refreshPending.get()
        fun getSnapshotVersion(): Long = snapshotVersion.get()
        fun getDataAgeMs(): Long = System.currentTimeMillis() - lastRefreshTime
        fun getCurrentPackageName(): String {
            val cached = lastEventPackage
            if (cached.isNotBlank()) return cached
            // 事件还没来得及更新时兜底读一次活跃窗口
            return try {
                val root = currentInstance?.rootInActiveWindow ?: return ""
                val pkg = root.packageName?.toString() ?: ""
                recycleQuiet(root)
                pkg
            } catch (_: Exception) {
                ""
            }
        }
        /**
         * 当前页面标识（用于把 UI 结构归档成"某 App 的某一页"）。
         *
         * 优先用事件里的类名；事件还没到（服务刚启动/界面刚切）时退回窗口信息——
         * 否则 learn_ui 会退化成"取第一个文本当页名"，同一页每次学出来名字都不一样，
         * 结果越学页面越多、互相覆盖。
         */
        fun getCurrentPageClass(): String {
            if (lastEventClass.isNotBlank()) return lastEventClass
            return try {
                val svc = currentInstance ?: return ""
                val win = svc.windows.firstOrNull { it.isActive } ?: return ""
                val title = if (android.os.Build.VERSION.SDK_INT >= 24) win.title?.toString() else null
                title?.takeIf { it.isNotBlank() }
                    ?: win.root?.className?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }
        }

        /** 应用采集参数（设置页改动后调用） */
        fun applyConfig(settings: SettingsManager) {
            maxDepth = settings.uiTreeMaxDepth
            maxElements = settings.uiTreeMaxElements
        }

        /**
         * 阻塞等待页面变化（WINDOW_STATE_CHANGED 事件），避免固定 sleep。
         */
        fun waitForScreenChange(timeoutMs: Long): Boolean {
            val latch = screenChangeLatch.get()
            return try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                false
            }
        }

        /**
         * 同步刷新屏幕快照：提交采集任务到单线程队列并阻塞等待完成。
         * 单线程队列保证排在所有在途刷新之后，因此返回时数据一定是新鲜的。
         * @return true = 已取到快照
         */
        // -------- 记住"不上报无障碍树"的 App（微信这类），下次直接走 OCR，不再白等 2 秒 --------

        private const val PREF_HOSTILE = "a11y_hostile_pkgs"

        /**
         * 当前连上的是不是伪装服务。
         * 伪装服务（类名冒充系统 SelectToSpeak）能正常拿到微信节点树，
         * 所以那份"不上报树"名单在这种情形下必须视为无效 —— 否则会白白退化成 OCR。
         */
        private fun connectedIsDisguised(): Boolean =
            currentInstance?.javaClass?.simpleName == "SelectToSpeakService"

        private fun hostilePrefs(): android.content.SharedPreferences? =
            appContext?.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        /**
         * 当前窗口的根节点是不是"空壳"：存在、但 0 个子节点、也没有任何文本/描述。
         * 微信 LauncherUI 实测就是这样（root 有 1 个节点、childCount=0）。
         * 用它来判定"这个 App 不上报树"，比"快照为空"可靠 —— 后者在首次采集前必然为真。
         */
        private fun treeLooksEmpty(): Boolean {
            val root = runCatching { currentInstance?.rootInActiveWindow }.getOrNull() ?: return false
            return try {
                runCatching { root.childCount }.getOrDefault(0) == 0 &&
                    root.text.isNullOrBlank() && root.contentDescription.isNullOrBlank()
            } finally {
                runCatching { root.recycle() }
            }
        }

        /**
         * 给上层（AutoOperator）用：一次"看→想→点"通常几十秒，这期间别让屏幕睡。
         * 只续期，不做点亮；没配置也安全。
         */
        fun keepAwakeForAWhile(timeoutMs: Long = 180_000L) {
            val ctx = appContext ?: return
            ScreenWaker.keepAwake(ctx, timeoutMs)
        }

        fun isKnownA11yHostile(pkg: String?): Boolean {
            if (pkg.isNullOrBlank()) return false
            // 伪装服务下微信会放行节点树，历史名单（用普通服务时记下的）必须作废，
            // 否则永远停在"明明读得到却直接走 OCR"。
            if (connectedIsDisguised()) return false
            val prefs = hostilePrefs() ?: return false
            return prefs.getStringSet(PREF_HOSTILE, emptySet<String>())?.contains(pkg) == true
        }

        private fun markA11yHostile(pkg: String?) {
            if (pkg.isNullOrBlank() || pkg == "com.android.systemui") return
            // 伪装服务下读得到树，不该把任何包记成"不上报"
            if (connectedIsDisguised()) return
            val prefs = hostilePrefs() ?: return
            if (isKnownA11yHostile(pkg)) return
            runCatching {
                val old = prefs.getStringSet(PREF_HOSTILE, emptySet<String>()) ?: emptySet<String>()
                prefs.edit().putStringSet(PREF_HOSTILE, old + pkg).apply()
                AppLogger.i(TAG, "记录「不上报无障碍树」的包名：$pkg（下次直接用 OCR）")
            }
        }

        /** 清空这份记忆（系统升级/App 改版后可能要重新探测） */
        fun clearA11yHostileCache() {
            hostilePrefs()?.edit()?.remove(PREF_HOSTILE)?.apply()
        }

        fun refreshAndWait(timeoutMs: Long = 1500L, fullMode: Boolean = false): Boolean {
            val service = currentInstance ?: return false
            // 熄屏状态下事件流会停，先点亮（唤醒失败也不拦，让上层按 OCR/提示处理）
            runCatching {
                val ctx = appContext
                if (ctx != null && !ScreenWaker.isScreenOn(ctx)) ScreenWaker.ensureAwake(ctx)
            }
            // 已知不上报树的应用：直接 OCR，省掉一次注定为空的采集 + 等待
            if (isKnownA11yHostile(lastEventPackage)) {
                val t = refreshByOcr()
                return t.isNotBlank() && !t.startsWith("(截图失败")
            }
            val latch = CountDownLatch(1)
            val ok = AtomicBoolean(false)
            bgExecutor.execute {
                try {
                    ok.set(captureNow(service, fullMode))
                } catch (e: Exception) {
                    AppLogger.e(TAG, "refreshAndWait error: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
            return try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS) && ok.get()
            } catch (_: Exception) {
                false
            }
        }

        private fun captureNow(service: ScreenRecognitionService, fullMode: Boolean): Boolean {
            var root: AccessibilityNodeInfo? = null
            try {
                root = service.rootInActiveWindow
                if (root == null) {
                    try { Thread.sleep(200) } catch (_: Exception) {}
                    root = service.rootInActiveWindow
                }
                val snapshot = extractStructured(service, root, fullMode)
                if (!snapshot.valid) return false
                // 退化快照 ⇒ 这个 App 大概率不上报树，记下来，下次直接 OCR
                if (root != null && snapshot.clickables.isEmpty() && snapshot.text.length < 160) {
                    markA11yHostile(lastEventPackage)
                }
                _lastScreenText.set(snapshot.text)
                _lastClickableData.set(snapshot.clickables)
                _lastUiTree.set(snapshot.uiNodes)
                captureImeBounds()
                lastRefreshTime = System.currentTimeMillis()
                snapshotVersion.incrementAndGet()
                return true
            } finally {
                try { root?.recycle() } catch (_: Exception) {}
            }
        }

        // ==================== OCR 兜底（App 不上报无障碍树时） ====================

        /**
         * 当前快照是不是"退化快照"：没有可点元素、文本也几乎为空。
         *
         * 实测微信 8.0.76 的 LauncherUI 就是这种情况 —— 原生 View 树完整可见
         * （dumpsys activity top 能看到 HomeUI$FitSystemWindowLayoutView → CustomViewPager），
         * 但无障碍只拿到 1 个空节点（uiautomator dump 同样是 1 个节点），
         * 说明是应用侧不给树，不是我们的配置或授权问题。
         */
        /** OCR 兜底（用服务自己的 Context，调用方无需持有） */
        fun refreshByOcr(): String {
            val ctx = appContext ?: return "(OCR 兜底不可用：无障碍服务未连接)"
            return refreshByOcr(ctx)
        }

        fun isSnapshotDegenerate(): Boolean {
            val text = _lastScreenText.get()
            val clickables = _lastClickableData.get()
            return clickables.isEmpty() && text.length < 160
        }

        /**
         * 截屏。优先无障碍 takeScreenshot（API 30+，无需额外权限），
         * 失败时退到自建 ADB 通道的 screencap。
         */
        fun captureBitmap(context: Context): Bitmap? {
            // 熄屏时先自己唤醒（命令通道最可靠；有密码锁则只能提示用户）
            if (!ScreenWaker.isScreenOn(context)) {
                val st = ScreenWaker.ensureAwake(context)
                if (!st.ready) AppLogger.w(TAG, "截屏前唤醒未完全就绪：${ScreenWaker.describe(st)}")
            }
            val svc = currentInstance
            if (svc != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val latch = CountDownLatch(1)
                    var out: Bitmap? = null
                    svc.takeScreenshot(
                        android.view.Display.DEFAULT_DISPLAY,
                        Executors.newSingleThreadExecutor(),
                        object : TakeScreenshotCallback {
                            override fun onSuccess(result: ScreenshotResult) {
                                try {
                                    val buf = result.hardwareBuffer
                                    val cs = ColorSpace.get(ColorSpace.Named.SRGB)
                                    val hw = Bitmap.wrapHardwareBuffer(buf, cs)
                                    // ML Kit 不吃 HARDWARE 位图，转成 ARGB_8888
                                    out = hw?.copy(Bitmap.Config.ARGB_8888, false)
                                    buf.close()
                                } catch (e: Exception) {
                                    AppLogger.e(TAG, "takeScreenshot 转换失败: ${e.message}")
                                } finally {
                                    latch.countDown()
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                AppLogger.w(TAG, "takeScreenshot 失败 code=$errorCode")
                                latch.countDown()
                            }
                        }
                    )
                    latch.await(3, TimeUnit.SECONDS)
                    if (out != null) return out
                } catch (e: Exception) {
                    AppLogger.w(TAG, "takeScreenshot 异常: ${e.message}")
                }
            }
            // 兜底：自建 ADB 通道
            val f = runCatching { com.aicompanion.adb.AdbSelf.screenshot(context, "ocr_shot.png") }.getOrNull()
            if (f != null && f.exists()) {
                return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            }
            return null
        }

        /**
         * 用 OCR 重建一份「可读 + 可点」的屏幕快照，写进和 UI 树同一套缓存。
         *
         * 关键点：OCR 结果按 ClickableData 登记（path 为空），
         * 于是 performClick / performClickData 的兜底链会自然走到"坐标手势"，
         * 不需要为 OCR 另写一套点击逻辑。
         */
        fun refreshByOcr(context: Context): String {
            ScreenWaker.keepAwake(context, 45_000L)
            // 熄屏先唤醒；锁屏挡住时不要拿锁屏画面去 OCR（那会得到"1:28/已连接 USB 调试"这种噪声）
            if (!ScreenWaker.isScreenOn(context) || ScreenWaker.isLocked(context)) {
                val st = ScreenWaker.ensureAwake(context)
                if (st.needsUnlock) {
                    return "(" + ScreenWaker.describe(st) + ")"
                }
            }
            // 只有"根节点确实是空壳"才记入名单，避免把尚未采集过的正常 App 误判
            val treeEmpty = treeLooksEmpty()
            val bmp = captureBitmap(context)
                ?: return "(截图失败：无障碍截屏不可用，且自建 ADB 通道未连接)"
            val res = try {
                OcrView.recognize(bmp)
            } finally {
                runCatching { bmp.recycle() }
            }
            val text = res.toUiText()
            captureImeBounds()
            _lastScreenText.set(text)
            _lastClickableData.set(
                res.items.map {
                    ClickableData(
                        text = it.text,
                        desc = "",
                        bounds = it.bounds,
                        cls = "ocr",
                        viewId = "",
                        path = "",
                    )
                }
            )
            _lastUiTree.set(
                res.items.mapIndexed { i, it ->
                    UiNode(
                        seq = i,
                        id = "ocr$i",
                        pid = "ocr",
                        depth = 1,
                        role = "text",
                        text = it.text,
                        desc = "",
                        bounds = it.bounds,
                        clickable = true,
                        editable = false,
                        scrollable = false,
                        checked = null,
                        enabled = true,
                        selected = false,
                        cls = "ocr",
                        viewId = "",
                        index = i,
                        src = "vision",
                    )
                }
            )
            lastRefreshTime = System.currentTimeMillis()
            snapshotVersion.incrementAndGet()
            lastOcrCount = res.items.size
            if (treeEmpty && res.items.isNotEmpty()) {
                val pkg = lastEventPackage.ifBlank {
                    runCatching {
                        currentInstance?.rootInActiveWindow?.packageName?.toString().orEmpty()
                    }.getOrDefault("")
                }
                markA11yHostile(pkg)
            }
            AppLogger.i(TAG, "OCR 兜底完成：${res.items.size} 条文本${if (res.blank) "（黑屏）" else ""}")
            // 明细日志：排查「明明屏幕上有却点不中」时，先看识别出来的是什么
            if (res.items.isNotEmpty()) {
                AppLogger.i(TAG, "OCR 明细：" + res.items.take(18)
                    .joinToString(" | ") { "${it.text.replace('\n', ' ').take(12)}@${it.cy}" })
            }
            return text
        }

        /**
         * 视觉定位兜底：截图（带坐标网格）发给对话模型，让它直接给坐标。
         * 专治"纯图标界面"——OCR 认不出图标，但模型看得出「这是返回/搜索/加号」。
         */
        fun refreshByVision(): String {
            val ctx = appContext ?: return "(视觉兜底不可用：无障碍服务未连接)"
            ScreenWaker.keepAwake(ctx, 120_000L)   // 视觉请求可能十几秒，别中途熄屏
            val bmp = captureBitmap(ctx) ?: return "(截图失败：无障碍截屏与自建 ADB 都不可用)"
            val targets = try {
                VisionGrounding.ground(ctx, bmp)
            } finally {
                runCatching { bmp.recycle() }
            }
            if (targets.isEmpty()) {
                return "(视觉模型未返回可用目标：可能未配置对话模型，或该页面确实没有可点元素)"
            }
            _lastClickableData.set(
                targets.map {
                    ClickableData(
                        text = it.text,
                        desc = "",
                        bounds = VisionGrounding.boundsOf(it),
                        cls = "vision",
                        viewId = "",
                        path = "",
                    )
                }
            )
            _lastUiTree.set(
                targets.mapIndexed { i, it ->
                    UiNode(
                        seq = i, id = "vis$i", pid = "vision", depth = 1, role = "button",
                        text = it.text, desc = "", bounds = VisionGrounding.boundsOf(it),
                        clickable = true, editable = false, scrollable = false, checked = null,
                        enabled = true, selected = false, cls = "vision", viewId = "",
                        index = i, src = "vision",
                    )
                }
            )
            val text = buildString {
                appendLine("屏幕目标（视觉模型定位，坐标为屏幕像素）：")
                targets.forEachIndexed { i, t ->
                    appendLine("${i + 1}. ${t.text}  @(${t.x},${t.y})")
                }
                append("提示：点击用 tap(x,y)。")
            }
            _lastScreenText.set(text)
            lastRefreshTime = System.currentTimeMillis()
            snapshotVersion.incrementAndGet()
            return text
        }

        // ==================== 手势操作 ====================

        fun performGlobalAction(action: Int): Boolean {
            return currentInstance?.performGlobalAction(action) ?: false
        }

        /** 单点手势点击（坐标兜底用） */
        fun performTapAt(x: Float, y: Float): Boolean {
            val service = currentInstance ?: return false
            return try {
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                    .build()
                service.dispatchGesture(gesture, null, null)
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "performTapAt error: ${e.message}")
                false
            }
        }

        fun performGesture(clickX: Float, clickY: Float): Boolean = performTapAt(clickX, clickY)

        fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
            val service = currentInstance ?: return false
            return try {
                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(endX, endY)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50L)))
                    .build()
                service.dispatchGesture(gesture, null, null)
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "performSwipe error: ${e.message}")
                false
            }
        }

        fun performLongPress(x: Float, y: Float, durationMs: Long): Boolean {
            val service = currentInstance ?: return false
            val settings = SettingsManager(service)
            if (!settings.longPressEnabled) {
                AppLogger.w(TAG, "长按手势已被禁用")
                return false
            }
            return try {
                val path = Path().apply { moveTo(x, y) }
                val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                service.dispatchGesture(gesture, null, null)
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "长按手势失败: ${e.message}")
                false
            }
        }

        fun performSwipeDirection(direction: String): Boolean {
            val service = currentInstance ?: return false
            val metrics = service.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()
            val centerX = w / 2f
            val centerY = h / 2f
            val offset = minOf(w, h) * 0.3f
            return when (direction) {
                "up" -> performSwipe(centerX, centerY + offset, centerX, centerY - offset, 400L)
                "down" -> performSwipe(centerX, centerY - offset, centerX, centerY + offset, 400L)
                "left" -> performSwipe(centerX + offset, centerY, centerX - offset, centerY, 400L)
                "right" -> performSwipe(centerX - offset, centerY, centerX + offset, centerY, 400L)
                else -> false
            }
        }

        // ==================== 点击（多级兜底） ====================

        fun performClick(text: String): Boolean {
            val service = currentInstance ?: return false
            val candidates = findClickCandidates(text)
            if (candidates.isEmpty()) return false
            return clickData(candidates.first())
        }

        /**
         * 直接点击一个「已经定位好」的元素。
         * 上层用 findClickCandidatesMulti 选定目标后应该调这个，而不是再按文字查一次 ——
         * 二次查找可能命中另一个元素，导致「判断用 A、实际点 B」。
         */
        fun performClickData(data: ClickableData): Boolean {
            if (currentInstance == null) return false
            return clickData(data)
        }

        fun performClickByIndex(index: Int): Boolean {
            val clickables = _lastClickableData.get()
            if (index < 0 || index >= clickables.size) return false
            return clickData(clickables[index])
        }

        /**
         * 设定开关/复选/单选的选中态。
         *
         * 注意：公开 SDK 里没有「设置选中态」的无障碍动作（ACTION_SET_CHECKED /
         * ACTION_ARGUMENT_CHECKED 属隐藏 API，编译拿不到），所以这里用公开 API 实现：
         * 先读当前 isChecked()，若已等于目标态则空操作返回 true；否则执行 ACTION_CLICK
         * 翻转（绝大多数开关/复选在 click 时切换状态）。路径/祖先定位失败时退回坐标手势。
         *
         * @param index 可点元素下标（通常来自 UiNode.index 或 id 解析）
         * @param checked 期望状态 true=选中
         */
        fun performSetChecked(index: Int, checked: Boolean): Boolean {
            val service = currentInstance ?: return false
            val clickables = _lastClickableData.get()
            if (index < 0 || index >= clickables.size) return false
            val data = clickables[index]
            val root = service.rootInActiveWindow ?: return false
            try {
                var node = resolveByPath(root, data.path)
                if (node == null) node = findClickableAncestorAt(root, data.bounds.centerX(), data.bounds.centerY())
                if (node != null) {
                    var ok = false
                    if (node.isCheckable) {
                        ok = node.isChecked == checked // 已处于期望态，无需操作
                    }
                    if (!ok) {
                        ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    if (node !== root) try { node.recycle() } catch (_: Exception) {}
                    if (ok) return true
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "performSetChecked error: ${e.message}")
            } finally {
                try { root.recycle() } catch (_: Exception) {}
            }
            // 节点操作失败 → 坐标手势兜底（点一下大多数开关都会在 click 与 checked 间翻转）
            return performTapAt(data.bounds.centerX().toFloat(), data.bounds.centerY().toFloat())
        }

        fun performLongPressByText(text: String, durationMs: Long): Boolean {
            val data = findClickCandidates(text).firstOrNull() ?: return false
            return performLongPress(data.bounds.centerX().toFloat(), data.bounds.centerY().toFloat(), durationMs)
        }

        /**
         * 点击一个已登记的 ClickableData。
         * 兜底顺序：路径定位 → 精确 bounds → 中心点最近可点祖先 → 坐标手势。
         */
        private fun clickData(data: ClickableData): Boolean {
            val service = currentInstance ?: return false
            // OCR / 视觉模型给出的目标没有节点路径。若继续走"找中心点可点祖先"，
            // 空树里唯一的根节点通常覆盖整屏 → 会点到屏幕中央（错得很难发现）。
            // 这类目标直接用坐标手势。
            if (data.cls == "ocr" || data.cls == "vision") {
                return performTapAt(
                    data.bounds.centerX().toFloat(),
                    data.bounds.centerY().toFloat()
                )
            }
            val root = service.rootInActiveWindow
            if (root != null) {
                try {
                    var node: AccessibilityNodeInfo? = null
                    // 1) 快照时记录的路径（同一棵树内最可靠）
                    if (data.path.isNotBlank()) {
                        node = resolveByPath(root, data.path)
                    }
                    // 2) 路径失效 → 精确 bounds
                    if (node == null) {
                        node = findNodeByBounds(root, data.bounds)
                    }
                    // 3) 仍失败 → 用中心点命中并向上找可点祖先
                    if (node == null) {
                        node = findClickableAncestorAt(root, data.bounds.centerX(), data.bounds.centerY())
                    }
                    if (node != null) {
                        val target = if (isClickableNode(node)) node else (findClickableAncestor(node) ?: node)
                        val ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (target !== root) try { target.recycle() } catch (_: Exception) {}
                        if (ok) return true
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "clickData error: ${e.message}")
                } finally {
                    try { root.recycle() } catch (_: Exception) {}
                }
            }
            // 4) 节点点击全部失败（Compose/自绘控件/WebView 常见）→ 坐标手势兜底
            AppLogger.w(TAG, "节点点击失败，回退坐标手势: ${data.bounds}")
            return performTapAt(data.bounds.centerX().toFloat(), data.bounds.centerY().toFloat())
        }

        private fun isClickableNode(node: AccessibilityNodeInfo): Boolean {
            if (node.isClickable) return true
            // 部分控件 isClickable=false 但暴露了 ACTION_CLICK
            return try {
                node.actionList?.any {
                    it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id
                } == true
            } catch (_: Exception) {
                false
            }
        }

        /** 从 node 向上找最近的可点击祖先（node 自身可点则返回自身） */
        private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var cur: AccessibilityNodeInfo? = node
            var guard = 0
            while (cur != null && guard++ < 12) {
                if (isClickableNode(cur)) return cur
                cur = try { cur.parent } catch (_: Exception) { null }
            }
            return null
        }

        /**
         * 按路径定位节点："R" 表示根，子节点为 "0"、"0-2"、"0-2-1"…
         * 用于快照失配时的可靠重定位（树未变化时命中率极高）。
         * 返回的节点由调用方负责 recycle；中间节点在这里回收。
         */
        private fun resolveByPath(root: AccessibilityNodeInfo, path: String): AccessibilityNodeInfo? {
            if (path.isBlank()) return null
            if (path == ROOT_PATH) return root
            var cur: AccessibilityNodeInfo? = null
            for ((i, p) in path.split('-').withIndex()) {
                val idx = p.toIntOrNull() ?: run { recycleQuiet(cur); return null }
                val parent = if (i == 0) root else cur!!
                val child = try { parent.getChild(idx) } catch (_: Exception) { null }
                if (child == null) {
                    recycleQuiet(cur)
                    return null
                }
                recycleQuiet(cur)
                cur = child
            }
            return cur
        }

        private fun findNodeByBounds(root: AccessibilityNodeInfo, targetBounds: Rect): AccessibilityNodeInfo? {
            val rootBounds = Rect()
            root.getBoundsInScreen(rootBounds)
            if (rootBounds == targetBounds) return root
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val result = findNodeByBounds(child, targetBounds)
                if (result != null) {
                    if (child !== result) recycleQuiet(child)
                    return result
                }
                recycleQuiet(child)
            }
            return null
        }

        private fun recycleQuiet(node: AccessibilityNodeInfo?) {
            if (node == null) return
            try { node.recycle() } catch (_: Exception) {}
        }

        /** 命中 (x,y) 的最深层可点击节点（含祖先回溯） */
        private fun findClickableAncestorAt(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
            val hit = findDeepestNodeAt(root, x, y) ?: return null
            val clickable = findClickableAncestor(hit)
            if (clickable == null) recycleQuiet(hit)
            return clickable
        }

        private fun findDeepestNodeAt(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
            val rect = Rect()
            root.getBoundsInScreen(rect)
            if (!rect.contains(x, y)) return null
            var best: AccessibilityNodeInfo? = root
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val deeper = findDeepestNodeAt(child, x, y)
                if (deeper != null) {
                    if (child !== deeper) recycleQuiet(child)
                    best = deeper
                    break
                }
                recycleQuiet(child)
            }
            return best
        }

        /**
         * 回读当前输入框里的文字，用于验证「到底写进去了没有」。
         *
         * 为什么必需：ACTION_SET_TEXT 和 KEYCODE_PASTE 都可能返回成功却什么都没写
         * （微信就是典型），只看返回值会把失败当成功，最后发出去一条空消息。
         */
        fun readFocusedText(): String {
            val service = currentInstance ?: return ""
            val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return ""
            return try {
                val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: findFirstEditable(root)
                node?.text?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            } finally {
                runCatching { root.recycle() }
            }
        }

        /**
         * 输入文本：找到目标输入框并填入内容。
         * 无目标时用当前焦点节点；找不到焦点则回退到"屏幕上第一个可见输入框"。
         */
        fun performInput(targetText: String, content: String): Boolean {
            val service = currentInstance ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                val targetNode = if (targetText.isBlank()) {
                    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditable(root)
                } else {
                    findEditableNodeByText(root, targetText)
                } ?: return false

                return try {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            content
                        )
                    }
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                } finally {
                    if (targetNode !== root) try { targetNode.recycle() } catch (_: Exception) {}
                }
            } finally {
                root.recycle()
            }
        }

        private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (isEditableNode(node)) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val r = findFirstEditable(child)
                if (r != null) {
                    if (child !== r) try { child.recycle() } catch (_: Exception) {}
                    return r
                }
                child.recycle()
            }
            return null
        }

        private fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
            return try {
                node.isEditable || node.className?.toString()?.contains("EditText") == true
            } catch (_: Exception) {
                false
            }
        }

        private fun findEditableNodeByText(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
            if (isEditableNode(node)) {
                val nodeText = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                    .joinToString(" ")
                if (nodeText.contains(target, ignoreCase = true)) return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findEditableNodeByText(child, target)
                if (result != null) {
                    if (child !== result) try { child.recycle() } catch (_: Exception) {}
                    return result
                }
                child.recycle()
            }
            return null
        }

        /** 查找所有文本匹配的可点击元素（歧义消除用） */
        /** 多路定位的结果：命中的候选 + 实际生效的策略（策略会回喂给 LLM 便于纠错） */
        data class LocateResult(
            val candidates: List<ClickableData>,
            val strategy: String
        )

        /**
         * 多级回退定位。
         *
         * 旧实现只按 text/desc 匹配：元素只有资源 ID 没有文字时找不到，UI 改版后也找不到，
         * 一律返回空，上层只能报「未找到可点击元素」。这里按可靠性从高到低依次尝试：
         *   1 资源ID精确 → 2 文字精确 → 3 描述精确 → 4 资源ID包含
         *   → 5 文字包含 → 6 描述包含 → 7 类名包含
         * 命中即返回，并把「是哪一路命中的」一并带出，方便 LLM 判断这次定位可不可靠。
         */
        /** 长度相同、最多一个字不同（容忍 OCR 的异体字/形近字漂移） */
        private fun nearEqual(a: String, b: String): Boolean {
            val x = a.trim()
            val y = b.trim()
            if (x.length != y.length || x.length < 2) return false
            var diff = 0
            for (i in x.indices) {
                if (x[i] != y[i]) {
                    diff++
                    if (diff > 1) return false
                }
            }
            return diff <= 1
        }

        /** 把当前输入法窗口的区域记下来（给"别点到键盘上"用） */
        private fun captureImeBounds() {
            val svc = currentInstance ?: run { _imeBounds.set(null); return }
            val b = runCatching {
                var found: android.graphics.Rect? = null
                for (w in svc.windows) {
                    val r = android.graphics.Rect()
                    w.getBoundsInScreen(r)
                    if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        found = r
                    }
                    AppLogger.d(TAG, "窗口 type=${w.type} id=${w.id} bounds=$r active=${w.isActive}")
                }
                found
            }.getOrNull()
            _imeBounds.set(b)
            AppLogger.i(TAG, "输入法区域：${b ?: "未识别"}")
        }

        private fun insideIme(c: ClickableData): Boolean {
            val ime = _imeBounds.get() ?: return false
            if (ime.height() <= 0) return false
            // 有些输入法会额外挂一个全屏容器窗口，那种 bounds 不能当键盘用，
            // 否则整屏候选都会被剔掉。真键盘的顶边一定在屏幕下半部。
            if (ime.top < ime.height() / 2) return false
            val cx = c.bounds.centerX()
            val cy = c.bounds.centerY()
            return ime.contains(cx, cy)
        }

        fun findClickCandidatesMulti(query: String): LocateResult {
            val all = _lastClickableData.get()
            val q = query.trim()
            if (q.isBlank()) return LocateResult(emptyList(), "空查询")
            if (all.isEmpty()) return LocateResult(emptyList(), "无元素快照")

            // 键盘区域里的东西不算可点目标（候选词、按键），先剔掉
            val noIme = all.filterNot { insideIme(it) }

            // 顶部一小条是状态栏/标题栏/搜索框：搜索框里的字就是查询词本身，
            // 不排掉的话「点联系人」永远先点到搜索框自己。真列表项不会挤在标题栏里。
            val screenH = runCatching {
                currentInstance?.resources?.displayMetrics?.heightPixels ?: 0
            }.getOrDefault(0)
            val clickables = if (screenH > 0) {
                noIme.filterNot { it.bounds.centerY() < screenH * 0.12 }.ifEmpty { noIme }
            } else noIme

            // 候选一律按「从上到下、从左到右」排。
            // 搜索结果里同一个词会出现多次（联系人、群聊、键盘候选词），
            // 不排序就会随机点到下面的群聊——真实踩过：想私聊结果进了群。
            fun List<ClickableData>.inScreenOrder(): List<ClickableData> =
                sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))

            // 包含匹配时「越短越准」：搜"久别辞"，
            // 「久别辞」这条是真名，「包含: 久别辞」是群聊的摘要行，不能让后者抢先。
            fun List<ClickableData>.byPrecision(): List<ClickableData> =
                sortedWith(compareBy({ it.text.trim().length }, { it.bounds.top }, { it.bounds.left }))

            val byIdExact = clickables.filter { it.viewId.isNotBlank() && it.viewId.equals(q, true) }
            if (byIdExact.isNotEmpty()) return LocateResult(byIdExact.inScreenOrder(), "资源ID精确")

            // 精确与近似放在一起按屏幕位置排。
            //
            // 为什么不能"精确优先"：搜「久别辞」时，OCR 会把联系人行认成异体字「久別辞」
            // （近似），却把下面「聊天记录」里的「久别辞」认得很准（精确）。
            // 精确优先就会点进聊天记录，而不是联系人。而搜索结果本来就是从上往下
            // 由相关到不相关，屏幕位置比"字面是否完全相等"更可信。
            val byTextExact = clickables.filter { it.text.trim().equals(q, true) }
            val byTextNear = clickables.filter { nearEqual(it.text, q) }
            val byTextBoth = (byTextExact + byTextNear).distinctBy { it.bounds }
            if (byTextBoth.isNotEmpty()) {
                return LocateResult(
                    byTextBoth.inScreenOrder(),
                    if (byTextExact.isNotEmpty()) "文字精确" else "文字近似"
                )
            }

            val byDescExact = clickables.filter { it.desc.trim().equals(q, true) }
            if (byDescExact.isNotEmpty()) return LocateResult(byDescExact.inScreenOrder(), "描述精确")

            val byIdPart = clickables.filter { it.viewId.isNotBlank() && it.viewId.contains(q, true) }
            if (byIdPart.isNotEmpty()) return LocateResult(byIdPart.inScreenOrder(), "资源ID包含")

            val byTextPart = clickables.filter { it.text.contains(q, true) }
            if (byTextPart.isNotEmpty()) return LocateResult(byTextPart.byPrecision(), "文字包含")

            val byDescPart = clickables.filter { it.desc.contains(q, true) }
            if (byDescPart.isNotEmpty()) return LocateResult(byDescPart.byPrecision(), "描述包含")

            val byClass = clickables.filter { it.cls.isNotBlank() && it.cls.contains(q, true) }
            if (byClass.isNotEmpty()) return LocateResult(byClass.inScreenOrder(), "类名包含")

            AppLogger.d(TAG, "findCandidates「$q」未命中：候选=${clickables.size}（原 ${all.size}，被键盘区过滤 ${all.size - clickables.size}）")
            return LocateResult(emptyList(), "未命中")
        }

        /** 兼容旧签名：只取候选列表 */
        fun findClickCandidates(text: String): List<ClickableData> =
            findClickCandidatesMulti(text).candidates

        /** 查找可滚动容器（供"翻页查看更多内容"使用） */
        fun performScroll(direction: String): Boolean {
            val service = currentInstance ?: return false
            val root = service.rootInActiveWindow ?: return false
            try {
                val scrollable = findScrollableNode(root) ?: return false
                val actionId = when (direction) {
                    "forward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id
                    "backward" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id
                    else -> return false
                }
                return scrollable.performAction(actionId)
            } finally {
                root.recycle()
            }
        }

        private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findScrollableNode(child)
                if (result != null) {
                    if (child !== result) try { child.recycle() } catch (_: Exception) {}
                    return result
                }
                child.recycle()
            }
            return null
        }

        // ==================== 快照采集 ====================

        /** 兼容旧调用：异步刷新（不推荐，规划前请用 refreshAndWait） */
        fun refreshScreenData(): Boolean = refreshScreenDataInternal(fullMode = false)
        fun refreshScreenDataFull(): Boolean = refreshScreenDataInternal(fullMode = true)

        private fun refreshScreenDataInternal(fullMode: Boolean): Boolean {
            val service = currentInstance ?: return false
            if (refreshPending.getAndSet(true)) {
                refreshDirty.set(true)
                return true
            }
            bgExecutor.execute {
                try {
                    captureNow(service, fullMode)
                } finally {
                    refreshPending.set(false)
                    if (refreshDirty.getAndSet(false)) {
                        refreshScreenDataInternal(fullMode)
                    }
                }
            }
            return true
        }

        private fun extractStructured(
            service: ScreenRecognitionService,
            activeRoot: AccessibilityNodeInfo?,
            fullMode: Boolean
        ): ScreenSnapshot {
            val lines = mutableListOf<Line>()
            val clickables = mutableListOf<ClickableData>()
            val uiNodes = mutableListOf<UiNode>()
            val textCounters = HashMap<String, Int>()   // label -> lines 下标
            val seenBounds = HashSet<String>()
            val truncated = booleanArrayOf(false)

            // 深度上限按实测校准：微信会话列表的会话名挂在 depth 23（虚拟节点，class 是 View），
            // 原来 fullMode 只放到 20，名字全被剪掉 —— 于是"列表读不到联系人"被误判成
            // "微信不给数据"，其实是自己切掉的。全量遍历（depth 40）实测能出 401 个节点。
            val depthLimit = if (fullMode) maxDepth.coerceAtLeast(35) else maxDepth
            val elementLimit = if (fullMode) maxElements.coerceAtLeast(900) else maxElements

            // --- 窗口 1：活跃窗口 ---
            val basePkg = activeRoot?.packageName?.toString() ?: lastEventPackage
            if (activeRoot != null) {
                walk(activeRoot, 0, ROOT_PATH, depthLimit, elementLimit, lines, clickables, textCounters, seenBounds, truncated, fullMode, uiNodes, "")
            }

            // --- 窗口 2..N：对话框 / 弹窗 / 输入法 / 悬浮窗 ---
            //
            // 关键修正（2026-10-24 真机实测微信得出）：
            // 微信首页的活动窗口是个**空壳**——实测 `windows=4`、活动窗口只有 1 个无文本节点，
            // 真实内容（会话名、可点行）挂在**同一个包名**的另一个窗口里。
            // 旧逻辑只补"不同包名的窗口 / 系统窗口"，于是微信首页永远被读成空白，
            // 表现为"AI 说看不到界面"。所以这里改成：活跃窗口没内容时，同包名的其他窗口也算数。
            val activeHasContent = lines.any { it.label.isNotBlank() }
            val activeWindowId = runCatching {
                service.windows.firstOrNull { it.isActive }?.id ?: -1
            }.getOrDefault(-1)
            var extraWindows = 0
            try {
                for (win in service.windows) {
                    val wtype = win.type
                    val wRoot = win.root ?: continue
                    try {
                        val wpkg = wRoot.packageName?.toString() ?: ""
                        val samePkgExtra = wpkg.isNotBlank() && wpkg == basePkg
                        // 用 windowId 判"是不是活跃窗口本身"，比引用相等可靠
                        val isSameAsActive = wpkg == basePkg && activeRoot != null &&
                            (wRoot === activeRoot || win.id == activeWindowId)
                        if (isSameAsActive) continue
                        val interesting = wtype == AccessibilityWindowInfo.TYPE_SYSTEM ||
                            wtype == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                            (wpkg.isNotBlank() && wpkg != basePkg) ||
                            (activeRoot == null) ||
                            (samePkgExtra && !activeHasContent)
                        if (!interesting) continue
                        val before = clickables.size
                        val winLines = mutableListOf<Line>()
                        walk(
                            wRoot, 1, "w${extraWindows}",
                            depthLimit, elementLimit, winLines,
                            clickables, textCounters, seenBounds, truncated, fullMode,
                            uiNodes, ""
                        )
                        if (clickables.size > before || winLines.any { it.label.isNotBlank() }) {
                            extraWindows++
                            lines.add(Line(depth = 0, kind = 'h', label = "— 附加窗口[${windowTypeName(wtype)}] ${wpkg.ifBlank { "?" }} —"))
                            lines.addAll(winLines)
                        }
                    } finally {
                        try { wRoot.recycle() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "collectWindows error: ${e.message}")
            }

            // --- 渲染 ---
            val metrics = service.resources.displayMetrics
            val sb = StringBuilder()
            sb.append("页面：").append(lastEventClass.ifBlank { basePkg.ifBlank { "未知" } })
            if (basePkg.isNotBlank()) sb.append(" (").append(basePkg).append(")")
            sb.append("  屏幕 ").append(metrics.widthPixels).append("x").append(metrics.heightPixels)
            if (extraWindows > 0) sb.append("  含").append(extraWindows).append("个附加窗口")
            sb.append('\n')
            if (!activeHasContent && extraWindows > 0) {
                sb.appendLine("⚠ 活动窗口是空壳（自绘界面常见），以下内容取自同应用的其他窗口。")
            }
            lines.forEach { sb.appendLine(it.render()) }
            if (truncated[0]) {
                sb.appendLine("⚠ 内容超长已截断（显示前 $elementLimit 个元素 / 深度 $depthLimit）。可用 scroll 翻页后重新读取，或调高设置里的「UI 树深度/元素上限」。")
            }
            if (lines.isEmpty()) {
                sb.appendLine("(当前窗口无可用文本或可点击元素，页面可能为纯图像/视频渲染)")
                sb.appendLine("→ 下一步：改用截图 + 识别（Screenshot 工具）；若需点击，用坐标手势 tapAt(x,y)。")
            }

            // 完全没有根节点且没采到任何内容 → 判定为无效快照，避免用垃圾覆盖上一份好数据
            val valid = activeRoot != null || clickables.isNotEmpty()
            return ScreenSnapshot(sb.toString().trim(), clickables, uiNodes, valid)
        }

        private fun windowTypeName(type: Int): String = when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "应用"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "输入法"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "系统对话框"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "无障碍浮层"
            else -> "窗口$type"
        }

        private fun walk(
            node: AccessibilityNodeInfo,
            depth: Int,
            path: String,
            maxDepth: Int,
            maxElements: Int,
            lines: MutableList<Line>,
            clickables: MutableList<ClickableData>,
            textCounters: HashMap<String, Int>,
            seenBounds: HashSet<String>,
            truncated: BooleanArray,
            fullMode: Boolean,
            uiNodes: MutableList<UiNode>,
            parentId: String
        ) {
            if (clickables.size >= maxElements) {
                truncated[0] = true
                return
            }
            val indent = depth.coerceAtMost(10)

            val ownText = node.text?.toString()?.trim().orEmpty()
            val ownDesc = node.contentDescription?.toString()?.trim().orEmpty()
            val clickable = isClickableNode(node)
            val editable = isEditableNode(node)
            val scrollable = try { node.isScrollable } catch (_: Exception) { false }
            val cls = simpleClass(node)
            val viewId = shortViewId(node)
            val checkable = try { node.isCheckable } catch (_: Exception) { false }
            val checked = if (checkable) try { node.isChecked } catch (_: Exception) { false } else null
            val enabled = try { node.isEnabled } catch (_: Exception) { true }
            val selected = try { node.isSelected } catch (_: Exception) { false }

            // 标签：自身 text → 自身 desc → （仅可点元素）子孙里第一个文字
            var label = ownText.ifBlank { ownDesc }
            var inherited = false
            if (label.isBlank() && clickable) {
                label = descendantLabel(node, labelInheritDepth)
                inherited = label.isNotBlank()
            }

            // 角色归并：告诉 LLM 这是什么控件（也是树视图模式下定位的抓手）
            val role = when {
                clickable && (cls.contains("Switch") || cls.contains("CheckBox") || cls.contains("RadioButton")) -> "checkbox"
                editable -> "textbox"
                clickable -> "button"
                scrollable -> "scroll"
                else -> if (label.isNotBlank() || ownText.isNotBlank()) "text" else "node"
            }

            val rect = Rect()
            try { node.getBoundsInScreen(rect) } catch (_: Exception) {}

            // 本帧是否产出一个 UiNode（决定子节点的父 id）
            var emittedId = parentId
            val emitUiNode = { idx: Int ->
                val seq = uiNodes.size
                val id = "n$seq"
                uiNodes.add(
                    UiNode(
                        seq = seq, id = id, pid = parentId, depth = depth, role = role,
                        text = label.ifBlank { ownText }, desc = ownDesc,
                        bounds = Rect(rect),
                        clickable = clickable, editable = editable, scrollable = scrollable,
                        checked = checked, enabled = enabled, selected = selected,
                        cls = cls, viewId = viewId, index = idx, src = "a11y"
                    )
                )
                emittedId = id
                id
            }

            when {
                clickable -> {
                    val boundsKey = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
                    // 同一元素可能同时出现在多个窗口（跨窗口镜像），按 bounds 去重
                    if (seenBounds.add(boundsKey)) {
                        val index = clickables.size
                        clickables.add(
                            ClickableData(
                                text = ownText,
                                desc = if (ownDesc.isNotBlank()) ownDesc else if (inherited) label else "",
                                bounds = Rect(rect),
                                cls = cls,
                                viewId = viewId,
                                path = path,
                                labelInherited = inherited
                            )
                        )
                        emitUiNode(index)
                        val hint = buildHint(cls, viewId, rect, label.isBlank(), fullMode)
                        val display = label.ifBlank { "‹无文字可点元素›" }
                        lines.add(Line(indent, 'c', display, index, hint))
                    }
                }
                editable -> {
                    emitUiNode(-1)
                    lines.add(Line(indent, 'i', label.ifBlank { "输入框" }, -1, buildHint(cls, viewId, rect, false, fullMode)))
                }
                label.isNotBlank() -> {
                    val key = "t:$label"
                    val existing = textCounters[key]
                    if (existing != null) {
                        lines[existing].count++
                    } else {
                        emitUiNode(-1)
                        textCounters[key] = lines.size
                        lines.add(Line(indent, 't', label))
                    }
                }
                scrollable -> {
                    emitUiNode(-1)
                    lines.add(Line(indent, 's', "可滚动区域", -1, buildHint(cls, viewId, rect, false, fullMode)))
                }
            }

            if (depth >= maxDepth) return
            val childCount = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                val childPath = if (path == ROOT_PATH) "$i" else "$path-$i"
                walk(
                    child, depth + 1, childPath,
                    maxDepth, maxElements, lines, clickables, textCounters, seenBounds, truncated, fullMode,
                    uiNodes, emittedId
                )
                recycleQuiet(child)
            }
        }

        private fun buildHint(cls: String, viewId: String, rect: Rect, unlabeled: Boolean, fullMode: Boolean): String {
            val sb = StringBuilder()
            if (cls.isNotBlank() || viewId.isNotBlank()) {
                sb.append(" {").append(cls)
                if (viewId.isNotBlank()) sb.append(' ').append('#').append(viewId)
                sb.append('}')
            }
            if (unlabeled || fullMode) {
                sb.append(" @").append(rect.centerX()).append(',').append(rect.centerY())
            }
            return sb.toString()
        }

        private fun simpleClass(node: AccessibilityNodeInfo): String {
            val raw = node.className?.toString() ?: return ""
            val name = raw.substringAfterLast('.')
            // 去掉系统噪声前缀
            return name.removePrefix("AppCompat").removePrefix("Material")
        }

        private fun shortViewId(node: AccessibilityNodeInfo): String {
            val raw = try { node.viewIdResourceName } catch (_: Exception) { null } ?: return ""
            return raw.substringAfterLast('/')
        }

        /** 向下查找第一个非空文字（用于无文字的可点击容器） */
        private fun descendantLabel(node: AccessibilityNodeInfo, depthLimit: Int): String {
            if (depthLimit <= 0) return ""
            val count = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                try {
                    val t = child.text?.toString()?.trim().orEmpty()
                    if (t.isNotBlank()) return t
                    val d = child.contentDescription?.toString()?.trim().orEmpty()
                    if (d.isNotBlank()) return d
                    val deeper = descendantLabel(child, depthLimit - 1)
                    if (deeper.isNotBlank()) return deeper
                } finally {
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
            return ""
        }

        fun shutdown() {
            try { bgExecutor.shutdownNow() } catch (_: Exception) {}
        }
    }

    /** 采集过程中的中间行，count 用于同名文本折叠显示 */
    private data class Line(
        val depth: Int,
        val kind: Char,
        @Volatile var label: String,
        val idx: Int = -1,
        val hint: String = "",
        var count: Int = 1
    ) {
        fun render(): String {
            val pad = "  ".repeat(depth)
            val suffix = if (count > 1) " ×$count" else ""
            return when (kind) {
                'c' -> "$pad[$idx] $label$hint$suffix"
                'i' -> "$pad[input] $label$hint$suffix"
                's' -> "$pad(scroll) $label$hint$suffix"
                'h' -> "$pad$label"
                else -> "$pad$label$suffix"
            }
        }
    }

    /** 屏幕快照：结构化文字 + 可点击元素列表（index 严格对应 [n]）+ 结构化 UI 树 */
    data class ScreenSnapshot(
        val text: String,
        val clickables: List<ClickableData>,
        val uiNodes: List<UiNode> = emptyList(),
        val valid: Boolean = true
    )

    data class ClickableData(
        val text: String,
        val desc: String,
        val bounds: Rect,
        val cls: String = "",
        val viewId: String = "",
        /** 子节点索引路径，如 "0-2-1"，用于树未变化时的精确重定位 */
        val path: String = "",
        /** 标签是否来自子孙节点（说明点击的是容器） */
        val labelInherited: Boolean = false
    )

    /**
     * 结构化 UI 树节点：给 LLM 的"树视图"数据。
     * @param seq 全局自增序号（id 即 "n"+seq）；pid 指向父节点 id，根级为 ""
     * @param role button/textbox/checkbox/scroll/text/node
     * @param index 可点击元素在快照 clickables 中的下标（非可点元素为 -1）
     * @param src a11y(无障碍) / vision(视觉兜底，Phase 3)
     */
    data class UiNode(
        val seq: Int,
        val id: String,
        val pid: String,
        val depth: Int,
        val role: String,
        val text: String,
        val desc: String,
        val bounds: Rect,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val checked: Boolean?,
        val enabled: Boolean,
        val selected: Boolean,
        val cls: String,
        val viewId: String,
        val index: Int,
        val src: String
    )

    private var cachedPrefs: android.content.SharedPreferences? = null
    private var lastCategorySaveTime = 0L
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentInstance = this
        appContext = applicationContext
        // 伪装服务能读到微信节点树 ⇒ 清掉历史误记，避免继续走 OCR 兜底
        if (javaClass.simpleName == "SelectToSpeakService") clearA11yHostileCache()
        cachedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        try {
            applyConfig(SettingsManager(this))
        } catch (_: Exception) {}
        // 服务重连后清掉旧快照，避免上层误用上一个会话的数据
        _lastScreenText.set("")
        _lastClickableData.set(emptyList())
        _lastUiTree.set(emptyList())
        AppLogger.i(TAG, "无障碍服务已连接，采集参数 depth=$maxDepth elements=$maxElements")
    }

    private fun scheduleRefresh(throttleMs: Long) {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        val runnable = Runnable { refreshScreenData() }
        refreshRunnable = runnable
        refreshHandler.postDelayed(runnable, throttleMs)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val packageName = event.packageName?.toString() ?: return
            val category = AppCategoryClassifier.classify(packageName)
            val now = System.currentTimeMillis()
            if (now - lastCategorySaveTime > 1000) {
                cachedPrefs?.edit()
                    ?.putString("current_app_category", category)
                    ?.putString("current_app_package", packageName)
                    ?.apply()
                lastCategorySaveTime = now
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val cls = event.className?.toString()?.substringAfterLast('.') ?: ""
                    // 只认「应用窗口」：输入法/系统对话框弹出来也会发窗口事件，
                    // 若把它们的包名记成当前包名，会让上层的「是否切换了 App」判断误报。
                    if (isApplicationWindow(event)) {
                        if (packageName.isNotBlank()) lastEventPackage = packageName
                        if (cls.isNotBlank()) lastEventClass = cls
                    }
                    refreshScreenData()
                    screenChangeLatch.getAndSet(CountDownLatch(1)).countDown()
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> scheduleRefresh(300L)
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> scheduleRefresh(500L)
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // 部分 App 进入新板块只发 CONTENT_CHANGED，不刷新会一直看到旧页面
                    if (isApplicationWindow(event) && packageName != lastEventPackage) {
                        lastEventPackage = packageName
                        refreshScreenData()
                    } else {
                        scheduleRefresh(600L)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "onAccessibilityEvent error: ${e.message}")
        }
    }

    /** 事件是否来自「应用窗口」（排除输入法 / 系统对话框 / 无障碍浮层） */
    private fun isApplicationWindow(event: AccessibilityEvent): Boolean {
        return try {
            val win = windows?.firstOrNull { it.id == event.windowId } ?: return true
            win.type == AccessibilityWindowInfo.TYPE_APPLICATION
        } catch (_: Exception) {
            true
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
        currentInstance = null
        super.onDestroy()
    }
}
