# MainActivity 职责分离重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 MainActivity.kt（3167行）中的非 UI 业务逻辑提取到独立的 Coordinator 类中，使 MainActivity 只负责 UI 绑定和生命周期调度。

**Architecture:** 引入 Coordinator 模式，每个 Coordinator 封装一组相关业务逻辑（Live2D、专注计时、日记、主动搭话、虚拟世界、新手引导、手机自动化），通过生命周期回调与 MainActivity 解耦。Coordinator 不持有 Activity 引用，通过回调/接口与 UI 通信。

**Tech Stack:** Kotlin, AndroidX Lifecycle, Coroutines

---

## File Structure

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| Create | `ui/coordinator/BaseCoordinator.kt` | Coordinator 基类，定义生命周期接口 |
| Create | `ui/coordinator/Live2DCoordinator.kt` | Live2D 模型加载、触摸交互、偏移/缩放 |
| Create | `ui/coordinator/FocusTimerCoordinator.kt` | 番茄钟计时、完成/取消 |
| Create | `ui/coordinator/DiaryCoordinator.kt` | 日记定时触发、手动触发、LLM 生成 |
| Create | `ui/coordinator/ProactiveChatCoordinator.kt` | 主动搭话调度与执行 |
| Create | `ui/coordinator/VirtualWorldCoordinator.kt` | 虚拟世界定时 tick |
| Create | `ui/coordinator/OnboardingCoordinator.kt` | 新手引导、onboarding 对话框 |
| Create | `ui/coordinator/AutoOperationCoordinator.kt` | 手机自动化操作 |
| Modify | `ui/MainActivity.kt` | 移除提取的逻辑，改为委托给 Coordinator |

---

### Task 1: 创建 BaseCoordinator 基类

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/BaseCoordinator.kt`

- [ ] **Step 1: 创建 BaseCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Coordinator 基类：封装一组相关业务逻辑，通过生命周期回调与 Activity 解耦。
 * 子类在 onCreate 中初始化资源，在 onDestroy 中释放资源。
 */
abstract class BaseCoordinator(
    protected val context: Context,
    protected val lifecycleOwner: LifecycleOwner
) {
    protected val handler = Handler(Looper.getMainLooper())
    protected val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle

    /** Activity onCreate 时调用 */
    open fun onCreate() {}

    /** Activity onResume 时调用 */
    open fun onResume() {}

    /** Activity onPause 时调用 */
    open fun onPause() {}

    /** Activity onDestroy 时调用，必须释放所有资源 */
    open fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
    }

    protected fun isAtLeast(state: Lifecycle.State): Boolean {
        return lifecycle.currentState.isAtLeast(state)
    }

    protected fun runIfAlive(block: () -> Unit) {
        if (isAtLeast(Lifecycle.State.CREATED)) {
            handler.post(block)
        }
    }
}
```

---

### Task 2: 提取 Live2DCoordinator

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/Live2DCoordinator.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt` — 移除 Live2D 相关代码，委托给 Live2DCoordinator

从 MainActivity 中提取以下成员和方法：
- 成员变量: `live2dView`, `offsetX`, `offsetY`, `modelBaseScale`, `modelNaturalW`, `modelNaturalH`, `lastLoadedModelPath`, `isModelLoaded`, `lastLive2DTouchTime`, `LIVE2D_TOUCH_COOLDOWN_MS`, `longPressPending`, `dragActive`, `touchDownRawX`, `touchDownRawY`, `lastTouchRawX`, `lastTouchRawY`, `touchDownX`, `touchDownY`, `longPressRunnable`
- 方法: `loadLive2DSettings()`, `hideLive2DView()`, `ensureLive2DView()`, `loadLive2DModel()`, `setupLive2DTouch()`

- [ ] **Step 1: 创建 Live2DCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import android.view.ViewStub
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.aicompanion.R
import com.aicompanion.live2d.Live2DWebView
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import com.aicompanion.voice.VoiceManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Live2DCoordinator(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val prefs: SharedPreferences,
    private val logHistory: MutableList<String>,
    private val voiceManagerProvider: () -> VoiceManager?,
    private val onPetMessage: (String, com.aicompanion.models.Emotion, com.aicompanion.models.Action) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "Live2DCoordinator"
        private const val LIVE2D_TOUCH_COOLDOWN_MS = 3000L
    }

    var live2dView: Live2DWebView? = null
        private set

    private var offsetX = 0f
    private var offsetY = 0f
    private var modelBaseScale = 1f
    private var modelNaturalW = 0f
    private var modelNaturalH = 0f
    private var lastLoadedModelPath: String? = null
    private var isModelLoaded = false
    private var lastLive2DTouchTime = 0L

    private var longPressPending = false
    private var dragActive = false
    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var longPressRunnable: Runnable? = null

    fun loadSettings() {
        offsetX = prefs.getFloat("model_offset_x", 0f)
        offsetY = prefs.getFloat("model_offset_y", 0f)
        modelBaseScale = prefs.getFloat("model_scale", 1f)
    }

    fun hideView() {
        live2dView?.pauseRendering()
        live2dView?.visibility = View.GONE
    }

    fun ensureView(stub: ViewStub?): Live2DWebView? {
        if (live2dView != null) return live2dView
        if (stub == null) return null
        live2dView = stub.inflate() as? Live2DWebView
        return live2dView
    }

    fun loadModel() {
        val webView = live2dView ?: return
        webView.visibility = View.VISIBLE
        webView.resumeRendering()

        webView.setOnModelInfo { width, height, baseScale ->
            modelNaturalW = width
            modelNaturalH = height
        }

        webView.setOnModelLoaded { success ->
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@setOnModelLoaded
            handler.post {
                if (!success) {
                    val failedLog = live2dView?.getLog() ?: ""
                    logHistory.add("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] 模型加载失败:")
                    failedLog.lines().takeLast(20).forEach { logHistory.add("  $it") }

                    val currentPath = prefs.getString("active_model_path", "")
                    if (!currentPath.isNullOrEmpty()) {
                        android.util.Log.w(TAG, "Custom model failed, falling back to default")
                        prefs.edit().remove("active_model_path").apply()
                        lastLoadedModelPath = null
                        live2dView?.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
                    } else {
                        Toast.makeText(context, "皮套加载失败", Toast.LENGTH_LONG).show()
                    }
                } else {
                    live2dView?.translationX = offsetX
                    live2dView?.translationY = offsetY
                    val scale = prefs.getFloat("model_scale", 1f)
                    live2dView?.setModelScale(scale.coerceIn(0.3f, 3.0f))
                    isModelLoaded = true
                }
            }
        }

        setupTouch()

        try {
            val customModelPath = prefs.getString("active_model_path", "")
            if (!customModelPath.isNullOrEmpty() && !customModelPath.startsWith("file:///android_asset/")) {
                val file = File(customModelPath)
                if (file.exists() && file.isFile) {
                    lastLoadedModelPath = customModelPath
                    webView.loadLive2DModelFromPath(customModelPath)
                } else {
                    prefs.edit().remove("active_model_path").apply()
                    lastLoadedModelPath = null
                    webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
                }
            } else {
                lastLoadedModelPath = null
                webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "loadLive2DModel failed: ${e.message}", e)
            try { webView.loadLive2DModelFromAssets("vtuber/PurpleBird/PurpleBird.model3.json") } catch (e: Exception) { AppLogger.e(TAG, "loadLive2DModel: ${e.message}") }
        }
    }

    private fun setupTouch() {
        val view = live2dView ?: return
        view.touchHandler = lambda@{ event ->
            if (!isModelLoaded) return@lambda false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    longPressPending = true
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = Runnable {
                        if (longPressPending) {
                            dragActive = true
                            longPressPending = false
                            lastTouchRawX = touchDownRawX
                            lastTouchRawY = touchDownRawY
                            live2dView?.alpha = 0.85f
                        }
                    }
                    longPressRunnable?.let { handler.postDelayed(it, 300) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragActive) {
                        val dx = event.rawX - lastTouchRawX
                        val dy = event.rawY - lastTouchRawY
                        live2dView?.translationX = (live2dView?.translationX ?: 0f) + dx
                        live2dView?.translationY = (live2dView?.translationY ?: 0f) + dy
                        lastTouchRawX = event.rawX
                        lastTouchRawY = event.rawY
                        return@lambda true
                    }
                    if (longPressPending) {
                        val dx = Math.abs(event.x - touchDownX)
                        val dy = Math.abs(event.y - touchDownY)
                        if (dx > 10 || dy > 10) {
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                            longPressPending = false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    if (dragActive) {
                        dragActive = false
                        live2dView?.alpha = 0.9f
                        offsetX = live2dView?.translationX ?: 0f
                        offsetY = live2dView?.translationY ?: 0f
                        prefs.edit()
                            .putFloat("model_offset_x", offsetX)
                            .putFloat("model_offset_y", offsetY)
                            .apply()
                        return@lambda true
                    }
                    if (longPressPending) {
                        longPressPending = false
                        val now = System.currentTimeMillis()
                        if (now - lastLive2DTouchTime < LIVE2D_TOUCH_COOLDOWN_MS) return@lambda true
                        lastLive2DTouchTime = now
                        val webViewHeight = live2dView?.height?.toFloat() ?: 1f
                        live2dView?.tapModelRegion(event.x, event.y, webViewHeight)
                        val normalizedY = event.y / webViewHeight.coerceAtLeast(1f)
                        val responseText = when {
                            normalizedY < 0.25f -> listOf("喵~", "嗯...好舒服", "不要摸头啦...", "再摸一下嘛~").random()
                            normalizedY < 0.5f -> listOf("干嘛戳我！", "别戳脸！", "哼，讨厌~", "呜...别戳了").random()
                            else -> listOf("嗯？", "抱抱~", "好暖和...", "嘿嘿~").random()
                        }
                        voiceManagerProvider()?.speak(responseText)
                        return@lambda true
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressPending = false
                    if (dragActive) {
                        dragActive = false
                        live2dView?.alpha = 0.9f
                        offsetX = live2dView?.translationX ?: 0f
                        offsetY = live2dView?.translationY ?: 0f
                        prefs.edit()
                            .putFloat("model_offset_x", offsetX)
                            .putFloat("model_offset_y", offsetY)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun setEmotion(emotion: com.aicompanion.models.Emotion) {
        live2dView?.setEmotion(emotion)
    }

    fun setAction(action: com.aicompanion.models.Action) {
        live2dView?.setAction(action)
    }

    fun updatePositionAndScale() {
        live2dView?.post {
            live2dView?.translationX = offsetX
            live2dView?.translationY = offsetY
            live2dView?.setModelScale(modelBaseScale.coerceIn(0.3f, 3.0f))
        }
    }

    fun checkModelChange() {
        val currentModelPath = prefs.getString("active_model_path", "")
        if (currentModelPath != lastLoadedModelPath) {
            loadModel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        live2dView?.cleanup()
        live2dView = null
    }
}
```

- [ ] **Step 2: 修改 MainActivity — 替换 Live2D 相关成员为 Live2DCoordinator**

在 MainActivity 中：
1. 添加 `private var live2DCoordinator: Live2DCoordinator? = null`
2. 在 `onCreate` 的 initStep 中初始化：
   - `initStep("Live2DSettings")` 改为 `live2DCoordinator?.loadSettings()`
   - `initStep("Live2DModel")` 改为 `live2DCoordinator?.loadModel()`
3. 在 `onResume` 中替换 Live2D 相关代码
4. 在 `onDestroy` 中添加 `live2DCoordinator?.onDestroy()`
5. 删除所有被提取的成员变量和方法

---

### Task 3: 提取 FocusTimerCoordinator

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/FocusTimerCoordinator.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

从 MainActivity 中提取：
- 成员变量: `focusActive`, `focusSecondsLeft`, `focusEndTime`, `focusRunnable`
- 方法: `startFocusTimer()`, `completeFocusSession()`, `cancelFocusTimer()`

- [ ] **Step 1: 创建 FocusTimerCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import com.aicompanion.affection.AffectionManager
import com.aicompanion.gamify.AchievementManager
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion

class FocusTimerCoordinator(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val appPrefs: SharedPreferences,
    private val affectionManagerProvider: () -> AffectionManager?,
    private val achievementManagerProvider: () -> AchievementManager?,
    private val onPetMessage: (String, Emotion, Action) -> Unit,
    private val onUpdateAffectionDisplay: () -> Unit,
    private val onCheckAiMomentTrigger: () -> Unit,
    private val onShowAchievementUnlock: (com.aicompanion.models.Achievement) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    var isActive: Boolean = false
        private set

    private var focusSecondsLeft = 0
    private var focusEndTime = 0L
    private var focusRunnable: Runnable? = null

    fun start() {
        isActive = true
        focusSecondsLeft = 25 * 60
        focusEndTime = System.currentTimeMillis() + focusSecondsLeft * 1000L
        onPetMessage("🍅 专注模式启动！25分钟，我会陪着你~", Emotion.HAPPY, Action.STRETCH)
        focusRunnable = object : Runnable {
            override fun run() {
                if (!isActive) return
                focusSecondsLeft = ((focusEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                if (focusSecondsLeft <= 0) complete()
                else handler.postDelayed(this, 1000)
            }
        }
        focusRunnable?.let { handler.postDelayed(it, 1000) }
    }

    private fun complete() {
        isActive = false
        val pomodoros = appPrefs.getInt("total_pomodoros", 0) + 1
        appPrefs.edit().putInt("total_pomodoros", pomodoros).apply()
        val ach = achievementManagerProvider()?.updateProgress("pomodoro", pomodoros)
        if (ach != null) onShowAchievementUnlock(ach)
        affectionManagerProvider()?.addAffection(3, "专注完成")
        onUpdateAffectionDisplay()
        onCheckAiMomentTrigger()
        onPetMessage("🎉 专注完成！你真棒！好感+3", Emotion.HAPPY, Action.TAIL_FLICK)
    }

    fun cancel() {
        isActive = false
        focusRunnable?.let { handler.removeCallbacks(it) }
        onPetMessage("专注取消了，没关系，下次继续~", Emotion.NEUTRAL, Action.IDLE)
    }

    override fun onPause() {
        focusRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onResume() {
        if (isActive && focusSecondsLeft > 0) {
            focusRunnable = object : Runnable {
                override fun run() {
                    if (!isActive) return
                    focusSecondsLeft = ((focusEndTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    if (focusSecondsLeft <= 0) complete()
                    else handler.postDelayed(this, 1000)
                }
            }
            focusRunnable?.let { handler.postDelayed(it, 1000) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        focusRunnable?.let { handler.removeCallbacks(it) }
    }
}
```

- [ ] **Step 2: 修改 MainActivity — 替换专注计时相关代码**

1. 添加 `private var focusTimerCoordinator: FocusTimerCoordinator? = null`
2. 在 onCreate 中初始化
3. 在功能面板中 `startFocusTimer()` → `focusTimerCoordinator?.start()`，`cancelFocusTimer()` → `focusTimerCoordinator?.cancel()`
4. 删除被提取的成员变量和方法

---

### Task 4: 提取 DiaryCoordinator

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/DiaryCoordinator.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

从 MainActivity 中提取：
- 成员变量: `diaryRunnable`, `diaryWriting`
- 方法: `scheduleDiaryTimer()`, `checkTurnsDiaryTrigger()`, `triggerManualDiary()`, `autoTriggerDiary()`, `analyzeLocalMood()`

- [ ] **Step 1: 创建 DiaryCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.aicompanion.affection.AffectionManager
import com.aicompanion.diary.DiaryManager
import com.aicompanion.memory.ContextManager
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.milestone.MilestoneManager
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.DiaryTriggerMode
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryCoordinator(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val memoryScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val apiClientProvider: () -> ApiClient?,
    private val affectionManagerProvider: () -> AffectionManager?,
    private val contextManagerProvider: () -> ContextManager?,
    private val milestoneManagerProvider: () -> MilestoneManager?,
    private val getPersonaInfo: suspend () -> Pair<String, String>,
    private val getMessages: () -> List<com.aicompanion.ui.ChatMessage>,
    private val onPetMessage: (String, Emotion, Action) -> Unit,
    private val onSetLoading: (Boolean) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "DiaryCoordinator"
    }

    private var diaryRunnable: Runnable? = null
    private var diaryWriting = false

    fun scheduleDiaryTimer(personaId: String) {
        val sm = settingsManagerProvider() ?: return
        val mode = sm.diaryTriggerMode
        if (mode == DiaryTriggerMode.MANUAL) return
        if (mode == DiaryTriggerMode.MSG_50) return

        val delayMs = when (mode) {
            DiaryTriggerMode.HOURLY -> 60 * 60 * 1000L
            DiaryTriggerMode.EVERY_2H -> 2 * 60 * 60 * 1000L
            else -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 22)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis - System.currentTimeMillis()
            }
        }

        diaryRunnable = Runnable {
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@Runnable
            memoryScope.launch {
                try {
                    val dm = DiaryManager(context, personaId)
                    val am = affectionManagerProvider() ?: return@launch
                    val todayDiary = dm.getDiaryByDate(
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    )
                    if (todayDiary == null && getMessages().count { it.isUser } >= 5) {
                        withContext(Dispatchers.Main) {
                            autoTriggerDiary(dm, am, personaId)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "diaryRunnable: ${e.message}")
                }
                withContext(Dispatchers.Main) {
                    if (isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) scheduleDiaryTimer(personaId)
                }
            }
        }
        handler.postDelayed(diaryRunnable!!, delayMs)
    }

    fun checkTurnsDiaryTrigger(personaId: String) {
        val sm = settingsManagerProvider() ?: return
        val mode = sm.diaryTriggerMode
        if (mode != DiaryTriggerMode.MSG_50) return

        val ctxMgr = contextManagerProvider() ?: return
        val totalTurns = ctxMgr.sessionManager.currentTurnCount
        if (totalTurns < 50) return

        val prefs = context.getSharedPreferences("diary_trigger_$personaId", android.content.Context.MODE_PRIVATE)
        val lastTriggeredTurns = prefs.getInt("last_diary_turns_trigger", 0)
        if (totalTurns - lastTriggeredTurns < 50) return

        val dm = DiaryManager(context, personaId)
        val am = affectionManagerProvider() ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayDiary = dm.getDiaryByDate(today)
        if (todayDiary != null) return

        if (getMessages().count { it.isUser } < 5) return

        prefs.edit().putInt("last_diary_turns_trigger", totalTurns).apply()
        autoTriggerDiary(dm, am, personaId)
    }

    fun triggerManualDiary(personaId: String) {
        val dm = DiaryManager(context, personaId)
        val am = affectionManagerProvider() ?: return
        onSetLoading(true)
        onPetMessage("正在为你写今天的日记...", Emotion.NEUTRAL, Action.IDLE)
        autoTriggerDiary(dm, am, personaId)
        memoryScope.launch {
            kotlinx.coroutines.delay(3000)
            withContext(Dispatchers.Main) { onSetLoading(false) }
        }
    }

    private fun autoTriggerDiary(dm: DiaryManager, am: AffectionManager, personaId: String) {
        val client = apiClientProvider() ?: return
        val sm = settingsManagerProvider() ?: return
        if (sm.chatApiUrl.isBlank()) return
        if (diaryWriting) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val existingDiary = dm.getDiaryByDate(today)
        if (existingDiary != null && dm.getTodayDiaryAppendCount() >= 3) return
        if (!dm.canUpdateDiary()) return

        diaryWriting = true
        dm.markDiaryUpdated()

        memoryScope.launch {
            try {
                val persona = getPersonaInfo()
                val chatTexts = getMessages().map { it.text }
                if (chatTexts.isEmpty()) return@launch

                val combined = chatTexts.joinToString(" | ")
                val poolBlock = contextManagerProvider()?.memoryPool?.getPoolBlock() ?: ""
                val diaryContext = if (poolBlock.isNotBlank()) {
                    "今天记忆池内容:\n$poolBlock\n\n聊天记录:\n$combined"
                } else {
                    combined
                }

                val localMood = analyzeLocalMood(diaryContext)

                val llmContent = withContext(Dispatchers.IO) {
                    client.generateDiaryContent(
                        chatTexts, persona.first, persona.second, localMood,
                        when (localMood) {
                            "happy" -> "🥰"; "sad" -> "😢"; "excited" -> "🤩"
                            "calm" -> "😌"; "sentimental" -> "🌙"; else -> "😊"
                        },
                        am.affectionLevel,
                        isUpdate = existingDiary != null
                    )
                }

                if (llmContent != null && llmContent.isNotBlank()) {
                    val currentExisting = dm.getDiaryByDate(today)
                    if (currentExisting != null) {
                        if (dm.getTodayDiaryAppendCount() < 3) {
                            dm.appendLlmDiaryUpdate(llmContent, chatTexts, am.affectionLevel)
                        }
                    } else {
                        dm.saveLlmDiary(llmContent, chatTexts, am.affectionLevel)
                        milestoneManagerProvider()?.recordMilestone("first_diary", "第一篇日记", "星尘写的第一篇日记", "diary")
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "📔 日记已更新", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                    milestoneManagerProvider()?.recordMilestone("first_diary", "第一篇日记", "星尘写的第一篇日记", "diary")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
                    }
                }

                val sm2 = settingsManagerProvider()
                if (sm2 != null && sm2.userPersonalityDef.isBlank()) {
                    try {
                        val summaryResult = client.summarizeUserPersonality(
                            personaName = persona.first,
                            recentChatSummary = chatTexts.takeLast(30).joinToString("\n"),
                            currentSummary = sm2.getAiSummarizedPersonality(personaId),
                            affectionLevel = am.affectionLevel
                        )
                        if (!summaryResult.isNullOrBlank()) {
                            sm2.setAiSummarizedPersonality(personaId, summaryResult)
                            com.aicompanion.prompt.PromptBuilder.invalidateCache()
                        }
                    } catch (e: Exception) { AppLogger.e(TAG, "autoTriggerDiary: ${e.message}") }
                }
            } catch (e: Exception) {
                android.util.Log.d(TAG, "LLM diary failed, falling back: ${e.message}")
                val chatTexts = getMessages().map { it.text }
                dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                milestoneManagerProvider()?.recordMilestone("first_diary", "第一篇日记", "星尘写的第一篇日记", "diary")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "📔 日记已生成（本地模式）", Toast.LENGTH_SHORT).show()
                }
            } finally {
                diaryWriting = false
            }
        }
    }

    private fun analyzeLocalMood(text: String): String {
        val lower = text.lowercase()
        val happyWords = listOf("哈哈", "开心", "喜欢", "太好了", "棒", "nice", "love", "good", "可爱")
        val sadWords = listOf("难过", "伤心", "哭", "不好", "烦", "生气", "sad", "bad", "讨厌")
        val excitedWords = listOf("厉害", "冲", "加油", "go", "yes", "完美", "了不起", "冲啊")
        val calmWords = listOf("安静", "舒服", "平静", "放松", "休息", "calm", "peace", "冥想")
        val sentimentalWords = listOf("回忆", "想念", "记得", "曾经", "星空", "月光", "诗", "夜晚")
        val scores = mapOf(
            "happy" to happyWords.count { lower.contains(it) },
            "sad" to sadWords.count { lower.contains(it) },
            "excited" to excitedWords.count { lower.contains(it) },
            "calm" to calmWords.count { lower.contains(it) },
            "sentimental" to sentimentalWords.count { lower.contains(it) }
        )
        val max = scores.maxByOrNull { it.value }
        return if (max != null && max.value > 0) max.key else "normal"
    }

    override fun onPause() {
        diaryRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onResume() {
        // 日记定时器在 onResume 后由外部重新调度
    }

    override fun onDestroy() {
        super.onDestroy()
        diaryRunnable?.let { handler.removeCallbacks(it) }
    }
}
```

- [ ] **Step 2: 修改 MainActivity — 替换日记相关代码**

---

### Task 5: 提取 ProactiveChatCoordinator

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/ProactiveChatCoordinator.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

从 MainActivity 中提取：
- 成员变量: `proactiveRunnable`
- 方法: `triggerProactiveChat()`, `scheduleProactiveChat()`

- [ ] **Step 1: 创建 ProactiveChatCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.aicompanion.interaction.ProactiveInteractionEngine
import com.aicompanion.memory.ContextManager
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.ui.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProactiveChatCoordinator(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val messageScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val apiClientProvider: () -> ApiClient?,
    private val contextManagerProvider: () -> ContextManager?,
    private val proactiveEngineProvider: () -> ProactiveInteractionEngine?,
    private val getPersonaInfo: suspend (String) -> Pair<String, String>,
    private val getMessages: () -> List<ChatMessage>,
    private val isInForeground: () -> Boolean,
    private val onPetMessage: (String, Emotion, Action) -> Unit,
    private val onUpdatePetDisplay: (com.aicompanion.models.ChatResponse) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "ProactiveChatCoordinator"
    }

    private var proactiveRunnable: Runnable? = null

    fun schedule() {
        val sm = settingsManagerProvider() ?: return
        if (!sm.isNagEnabled) return
        proactiveRunnable = Runnable {
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@Runnable
            val engine = proactiveEngineProvider()
            if (engine?.shouldTriggerInteraction(sm.nagFrequency.name.lowercase()) == true) {
                trigger()
            }
            schedule()
        }
        handler.postDelayed(proactiveRunnable!!, 120000L)
    }

    private fun trigger() {
        if (isInForeground()) return
        val client = apiClientProvider() ?: return
        val sm = settingsManagerProvider() ?: return

        val chatHistory = getMessages().takeLast(sm.contextTurns).map { msg ->
            Pair(msg.isUser, msg.text)
        }

        messageScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    if (sm.chatApiUrl.isNotBlank()) {
                        val memCtx = contextManagerProvider()?.memoryPool?.getPoolBlock()
                        val recentText = chatHistory.takeLast(3).map { it.second }.joinToString(" ")
                        val persona = getPersonaInfo(recentText)
                        client.generateNagContent(persona.first, persona.second, memoryContext = memCtx, chatHistory = chatHistory)
                    } else null
                }
                if (response != null && response.text.isNotBlank() && response.errorMessage == null) {
                    onPetMessage(response.text, response.emotion, response.action)
                    onUpdatePetDisplay(response)
                } else {
                    val fallback = proactiveEngineProvider()?.getIdlePhrase()
                    if (fallback != null && isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
                        onPetMessage(fallback.first, fallback.second, fallback.third)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "LLM proactive chat failed: ${e.message}")
                val fallback = proactiveEngineProvider()?.getIdlePhrase()
                if (fallback != null && isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
                    onPetMessage(fallback.first, fallback.second, fallback.third)
                }
            }
        }
    }

    override fun onPause() {
        proactiveRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        proactiveRunnable?.let { handler.removeCallbacks(it) }
    }
}
```

- [ ] **Step 2: 修改 MainActivity — 替换主动搭话相关代码**

---

### Task 6: 提取 VirtualWorldCoordinator

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/VirtualWorldCoordinator.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

从 MainActivity 中提取：
- 成员变量: `virtualWorldRunnable`
- 方法: `scheduleVirtualWorldTick()`, `findActiveVirtualWorld()`

- [ ] **Step 1: 创建 VirtualWorldCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.aicompanion.util.AppLogger
import com.aicompanion.virtualworld.VirtualWorldManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VirtualWorldCoordinator(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val memoryScope: CoroutineScope
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "VirtualWorldCoordinator"
    }

    private var virtualWorldRunnable: Runnable? = null

    fun scheduleTick() {
        virtualWorldRunnable = Runnable {
            if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@Runnable

            memoryScope.launch {
                try {
                    val worldsToTick = mutableListOf<VirtualWorldManager>()

                    val globalVw = VirtualWorldManager(context, "")
                    if (globalVw.isEnabled && globalVw.isRunning) {
                        worldsToTick.add(globalVw)
                    }

                    try {
                        val gcManager = com.aicompanion.groupchat.GroupChatManager(context)
                        gcManager.load()
                        for (group in gcManager.getAllGroups()) {
                            val groupVw = VirtualWorldManager(context, group.id)
                            if (groupVw.isEnabled && groupVw.isRunning) {
                                worldsToTick.add(groupVw)
                            }
                        }
                    } catch (e: Exception) { AppLogger.e(TAG, "scheduleTick: ${e.message}") }

                    for (vwManager in worldsToTick) {
                        if (vwManager.shouldTick()) {
                            try { vwManager.runSimulationTick() }
                            catch (e: Exception) { AppLogger.e(TAG, "virtualWorldTick: ${e.message}") }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "scheduleVirtualWorldTick: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    if (isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED) && virtualWorldRunnable != null) {
                        handler.postDelayed(virtualWorldRunnable!!, 60000L)
                    }
                }
            }
        }
        handler.postDelayed(virtualWorldRunnable!!, 60000L)
    }

    fun findActiveVirtualWorld(): VirtualWorldManager? {
        val globalVw = VirtualWorldManager(context, "")
        if (globalVw.isEnabled) return globalVw

        try {
            val gcManager = com.aicompanion.groupchat.GroupChatManager(context)
            gcManager.load()
            for (group in gcManager.getAllGroups()) {
                val groupVw = VirtualWorldManager(context, group.id)
                if (groupVw.isEnabled) return groupVw
            }
        } catch (e: Exception) { AppLogger.e(TAG, "findActiveVirtualWorld: ${e.message}") }

        return null
    }

    override fun onPause() {
        virtualWorldRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualWorldRunnable?.let { handler.removeCallbacks(it) }
    }
}
```

- [ ] **Step 2: 修改 MainActivity — 替换虚拟世界相关代码**

---

### Task 7: 提取 OnboardingCoordinator

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/OnboardingCoordinator.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

从 MainActivity 中提取：
- 方法: `loadWelcomeMessage()`, `showOnboardingDialog()`, `showTutorial()`

- [ ] **Step 1: 创建 OnboardingCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.aicompanion.R
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OnboardingCoordinator(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val messageScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val getAppPrefs: () -> android.content.SharedPreferences,
    private val getPersonaInfo: suspend () -> Pair<String, String>,
    private val onPetMessage: (String, Emotion, Action) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    fun loadWelcomeMessage() {
        val prefs = getAppPrefs()
        val sm = settingsManagerProvider() ?: return
        if (!sm.onboardingCompleted) {
            showOnboardingDialog()
        } else if (prefs.getBoolean("first_launch", true)) {
            prefs.edit().putBoolean("first_launch", false).apply()
            showTutorial()
            messageScope.launch {
                val name = getPersonaInfo().first
                onPetMessage("你好呀！我是${name}，你的AI伙伴~", Emotion.HAPPY, Action.TAIL_FLICK)
            }
        }
    }

    private fun showOnboardingDialog() {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return
        val sm = settingsManagerProvider() ?: return
        val activity = context as? android.app.Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val contentView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val tvGenderLabel = TextView(context).apply {
            text = "你是男生还是女生？"
            textSize = 16f
            setTextColor(0xFFd0d0e0.toInt())
        }
        contentView.addView(tvGenderLabel)

        val genderGroup = RadioGroup(context).apply { orientation = RadioGroup.HORIZONTAL }
        val rbMale = RadioButton(context).apply { text = "男生"; textSize = 15f; setTextColor(0xFFd0d0e0.toInt()) }
        val rbFemale = RadioButton(context).apply { text = "女生"; textSize = 15f; setTextColor(0xFFd0d0e0.toInt()) }
        genderGroup.addView(rbMale)
        genderGroup.addView(rbFemale)
        contentView.addView(genderGroup)

        val spacer1 = View(context)
        contentView.addView(spacer1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (16 * context.resources.displayMetrics.density).toInt()
        ))

        val tvBirthdayLabel = TextView(context).apply {
            text = "你的生日是？"
            textSize = 16f
            setTextColor(0xFFd0d0e0.toInt())
        }
        contentView.addView(tvBirthdayLabel)

        val tvBirthday = TextView(context).apply {
            text = if (sm.userBirthday.isNotBlank()) sm.userBirthday else "点击选择生日"
            textSize = 15f
            setTextColor(0xFF667eea.toInt())
            setPadding(0, (8 * context.resources.displayMetrics.density).toInt(), 0, (8 * context.resources.displayMetrics.density).toInt())
        }
        tvBirthday.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            val currentText = tvBirthday.text?.toString() ?: ""
            if (currentText.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val parts = currentText.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            android.app.DatePickerDialog(
                context,
                R.style.ThemeOverlay_Companion_DatePicker,
                { _, year, month, day ->
                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                    tvBirthday.text = dateStr
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        contentView.addView(tvBirthday)

        val dialog = AlertDialog.Builder(context)
            .setTitle("初次见面，认识一下吧 ✨")
            .setView(contentView)
            .setCancelable(false)
            .setPositiveButton("开始使用") { _, _ ->
                val gender = when (genderGroup.checkedRadioButtonId) {
                    rbMale.id -> "male"
                    rbFemale.id -> "female"
                    else -> ""
                }
                sm.userGender = gender
                val birthday = tvBirthday.text?.toString()?.trim() ?: ""
                if (birthday.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    sm.userBirthday = birthday
                }
                sm.onboardingCompleted = true
                getAppPrefs().edit().putBoolean("first_launch", false).apply()
                showTutorial()
                messageScope.launch {
                    val name = getPersonaInfo().first
                    onPetMessage("你好呀！我是${name}，很高兴认识你~", Emotion.HAPPY, Action.TAIL_FLICK)
                }
            }
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_settings_card)
    }

    fun showTutorial() {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return
        val activity = context as? android.app.Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val steps = listOf(
            "👋 欢迎使用星尘 AI 桌宠！" to "我是你的专属AI伙伴，可以陪你聊天、记录心情和日记。\n\n点击「下一步」了解基本操作~",
            "💬 聊天与表情" to "在底部输入框发消息和我聊天~\n\n点击 😊 按钮选择你的心情，我会根据你的情绪回复哦！",
            "⚙️ API 配置" to "点击右上角 ⚙ 设置按钮，配置你的 AI API。\n\n选择厂商后会自动填充地址和模型，只需填入 API 密钥即可~",
            "🎮 更多功能" to "• 📅 签到 — 每日打卡领好感\n• 🏆 成就 — 解锁各种有趣成就\n• 📔 日记 — 自动生成每日日记\n• 🍅 专注 — 番茄钟计时",
            "🐾 Live2D 互动" to "• 点击 ⋮ 按钮打开功能面板\n• 在设置中可以调整模型大小\n• 悬浮窗模式让我在桌面陪伴你~"
        )
        var step = 0
        val dialog = AlertDialog.Builder(context)
            .setTitle(steps[step].first)
            .setMessage(steps[step].second)
            .setPositiveButton("下一步") { _, _ -> }
            .setNegativeButton("跳过", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            step++
            if (step < steps.size) {
                dialog.setTitle(steps[step].first)
                dialog.setMessage(steps[step].second)
                if (step == steps.size - 1) dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "完成"
            } else {
                dialog.dismiss()
            }
        }
    }
}
```

- [ ] **Step 2: 修改 MainActivity — 替换新手引导相关代码**

---

### Task 8: 提取 AutoOperationCoordinator

**Files:**
- Create: `android/app/src/main/java/com/aicompanion/ui/coordinator/AutoOperationCoordinator.kt`
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

从 MainActivity 中提取：
- 方法: `showAutoOperationDialog()`, `executeAutoOperation()`

- [ ] **Step 1: 创建 AutoOperationCoordinator.kt**

```kotlin
package com.aicompanion.ui.coordinator

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.aicompanion.models.Action
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoOperationCoordinator(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val messageScope: CoroutineScope,
    private val settingsManagerProvider: () -> SettingsManager?,
    private val apiClientProvider: () -> ApiClient?,
    private val onPetMessage: (String, Emotion, Action) -> Unit
) : BaseCoordinator(context, lifecycleOwner) {

    companion object {
        private const val TAG = "AutoOperationCoordinator"
    }

    fun showAutoOperationDialog() {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return
        val activity = context as? android.app.Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        val sm = settingsManagerProvider() ?: return
        if (sm.chatApiUrl.isBlank()) {
            Toast.makeText(context, "请先在设置中配置API", Toast.LENGTH_SHORT).show()
            return
        }

        if (!com.aicompanion.screen.AutoOperator.isServiceReady()) {
            AlertDialog.Builder(context)
                .setTitle("需要无障碍权限")
                .setMessage("手机自动化需要开启「星尘AI」的无障碍服务才能操作手机。\n\n请前往：系统设置 → 无障碍 → 已安装应用 → 星尘AI → 开启服务\n\n开启后重新进入此功能即可使用。")
                .setPositiveButton("去开启") { _, _ ->
                    try { activity.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { AppLogger.e(TAG, "showAutoOperationDialog: ${e.message}") }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        val input = EditText(context).apply {
            hint = "例：帮我把音量调到最大 / 打开微信找张三"
            setTextColor(0xFFe8e8f0.toInt())
            setHintTextColor(0xFF667788.toInt())
            setBackgroundResource(android.R.color.transparent)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xFF0f0c29.toInt())
            addView(input)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("🤖 手机自动化")
            .setView(container)
            .setPositiveButton("开始执行", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val request = input.text.toString().trim()
                if (request.isEmpty()) return@setOnClickListener
                dialog.dismiss()
                executeAutoOperation(request)
            }
        }

        dialog.show()
    }

    private fun executeAutoOperation(userRequest: String) {
        if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return

        Toast.makeText(context, "🤖 正在分析指令...", Toast.LENGTH_LONG).show()
        onPetMessage("主人让我帮你操作手机：$userRequest", Emotion.HAPPY, Action.IDLE)

        messageScope.launch {
            try {
                val screenInfo = withContext(Dispatchers.IO) {
                    com.aicompanion.screen.AutoOperator.formatScreenForLLM()
                }

                val llmResult = withContext(Dispatchers.IO) {
                    apiClientProvider()?.analyzeAutoOperation(userRequest, screenInfo)
                }

                if (llmResult.isNullOrBlank() || llmResult == "[]") {
                    onPetMessage("抱歉，我没法理解这个操作该怎么办😅", Emotion.SAD, Action.IDLE)
                    return@launch
                }

                val actions = com.aicompanion.screen.AutoOperator.parseActionsFromLLM(llmResult)
                if (actions.isEmpty()) {
                    onPetMessage("抱歉，我没法理解这个操作该怎么办😅", Emotion.SAD, Action.IDLE)
                    return@launch
                }

                onPetMessage("明白了！正在执行${actions.size}个步骤...", Emotion.TSUNDERE, Action.EAR_TWITCH)

                var successCount = 0
                for ((i, action) in actions.withIndex()) {
                    if (!isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) return@launch

                    val stepResult = withContext(Dispatchers.IO) {
                        com.aicompanion.screen.AutoOperator.executeAction(action)
                    }

                    if (stepResult) successCount++
                    withContext(Dispatchers.IO) { kotlinx.coroutines.delay(800) }
                }

                val reportStr = buildString {
                    append("完成！${successCount}/${actions.size}个步骤执行成功")
                    if (successCount == actions.size) append("，搞定啦~")
                    else append("，有几个步骤没成功，可能界面不太一样")
                }
                onPetMessage(reportStr, Emotion.HAPPY, Action.IDLE)

            } catch (e: Exception) {
                Log.e(TAG, "AutoOperation failed: ${e.message}")
                onPetMessage("操作失败了：${e.message}", Emotion.SAD, Action.IDLE)
            }
        }
    }
}
```

- [ ] **Step 2: 修改 MainActivity — 替换手机自动化相关代码**

---

### Task 9: 重构 MainActivity 使用所有 Coordinator

**Files:**
- Modify: `android/app/src/main/java/com/aicompanion/ui/MainActivity.kt`

- [ ] **Step 1: 添加所有 Coordinator 成员变量**

在 MainActivity 类顶部添加：
```kotlin
private var live2DCoordinator: Live2DCoordinator? = null
private var focusTimerCoordinator: FocusTimerCoordinator? = null
private var diaryCoordinator: DiaryCoordinator? = null
private var proactiveChatCoordinator: ProactiveChatCoordinator? = null
private var virtualWorldCoordinator: VirtualWorldCoordinator? = null
private var onboardingCoordinator: OnboardingCoordinator? = null
private var autoOperationCoordinator: AutoOperationCoordinator? = null
```

- [ ] **Step 2: 在 onCreate 中初始化所有 Coordinator**

替换原有的 initStep 调用，将逻辑委托给 Coordinator。

- [ ] **Step 3: 在 onResume/onPause/onDestroy 中委托给 Coordinator**

- [ ] **Step 4: 删除所有已提取到 Coordinator 中的成员变量和方法**

- [ ] **Step 5: 更新所有引用点**

确保 `showFeaturePanel()`、`setupClickListeners()` 等方法中的调用指向新的 Coordinator。

---

### Task 10: 编译验证和回归测试

**Files:**
- All modified files

- [ ] **Step 1: 运行 Gradle 编译**

```bash
cd android && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查所有 Coordinator 的导入和引用**

确保没有遗漏的引用或编译错误。

- [ ] **Step 3: 验证 MainActivity 行数减少**

Expected: MainActivity.kt 从 ~3167 行减少到 ~2000 行以下

- [ ] **Step 4: 提交代码**

```bash
git add -A
git commit -m "refactor: extract coordinators from MainActivity to separate concerns"
```
