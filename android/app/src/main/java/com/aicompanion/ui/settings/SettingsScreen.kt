package com.aicompanion.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.theme.DarkColorSchemes
import com.aicompanion.theme.LightColorSchemes
import com.aicompanion.theme.StradustTheme
import com.aicompanion.theme.ThemeId
import com.aicompanion.theme.ThemeState
import com.aicompanion.settings.SettingsManager
import com.aicompanion.settings.NagFrequency
import com.aicompanion.settings.DiaryTriggerMode
import com.aicompanion.settings.ProviderProfile
import com.aicompanion.settings.ServicePresets
import com.aicompanion.settings.ScheduledWake
import com.aicompanion.voice.TtsManager
import com.aicompanion.voice.LocalAsrManager
import com.aicompanion.voice.EdgeTtsEngine
import com.aicompanion.rag.RagConfig
import com.aicompanion.rag.OnnxModelManager
import com.aicompanion.ui.components.StradustButton
import com.aicompanion.ui.components.StradustCard
import com.aicompanion.ui.components.StradustInput
import com.aicompanion.ui.components.StradustTopBar
import com.aicompanion.ui.components.ButtonVariant
import com.aicompanion.ui.components.ButtonSize
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// === 新增功能所需的 import ===
import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.aicompanion.api.ApiProviderPreset
import com.aicompanion.memory.ContextManager
import com.aicompanion.screen.AutoOperator
import com.aicompanion.localmodel.ScreenCaptureService
import com.aicompanion.util.AppLogger
import com.aicompanion.util.AvatarManager
import com.aicompanion.services.OverlayService
import java.io.File
import androidx.compose.ui.semantics.Role

/** 外观模式：null=跟随系统, true=强制暗色, false=强制亮色 */
private enum class DarkMode(val label: String, val value: Boolean?) {
    FOLLOW_SYSTEM("跟随系统", null),
    FORCE_LIGHT("强制亮色", false),
    FORCE_DARK("强制暗色", true);

    companion object {
        fun fromValue(value: Boolean?): DarkMode = when (value) {
            true -> FORCE_DARK
            false -> FORCE_LIGHT
            null -> FOLLOW_SYSTEM
        }
    }
}

/**
 * 设置屏幕的回调接口 - 用于将设置变化通知给外部进行持久化
 */
interface SettingsScreenCallbacks {
    /** API 地址变化 */
    fun onApiUrlChange(url: String)
    /** API Key 变化 */
    fun onApiKeyChange(key: String)
    /** 模型选择变化 */
    fun onModelChange(model: String)
    /** 温度变化 */
    fun onTemperatureChange(temperature: Float)
    /** 最大 Token 变化 */
    fun onMaxTokensChange(maxTokens: Int)
    /** TTS 开关变化 */
    fun onTtsEnabledChange(enabled: Boolean)
    /** ASR 开关变化 */
    fun onAsrEnabledChange(enabled: Boolean)
    /** 语速变化 */
    fun onSpeechRateChange(rate: Float)
    /** 音色选择变化 */
    fun onVoiceChange(voice: String)
}

@Composable
fun SettingsScreen(
    callbacks: SettingsScreenCallbacks? = null,
    initialApiUrl: String = "",
    initialApiKey: String = "",
    initialModel: String = "GPT-4o",
    initialTemperature: Float = 0.7f,
    initialMaxTokens: Int = 4096,
    initialTtsEnabled: Boolean = true,
    initialAsrEnabled: Boolean = true,
    initialSpeechRate: Float = 1.0f,
    initialVoice: String = "甜美女声",
    /** 通用导航回调，传入路由字符串 */
    onNavigate: (String) -> Unit = {},
    /** Live2D 可用模型列表 */
    live2dModels: List<com.aicompanion.models.Live2DModel> = emptyList(),
    /** 当前激活的 Live2D 模型 ID */
    currentLive2DModelId: String = "",
    /** 切换 Live2D 模型回调 */
    onLive2DModelChange: (String) -> Unit = {},
    /** Live2D 模型缩放（0.3 - 3.0） */
    live2dScale: Float = 1f,
    /** Live2D 模型缩放变化回调 */
    onLive2DScaleChange: (Float) -> Unit = {},
) {
    // 关键修复：所有主题状态从 ThemeState 读取，写入也走 ThemeState
    val selectedTheme = ThemeState.currentThemeId
    val darkMode = DarkMode.fromValue(ThemeState.currentDarkMode)
    val isDark = ThemeState.currentDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
    val context = LocalContext.current

    // 本地表单状态（与主题无关的设置项）- 使用传入的初始值
    var apiUrl by rememberSaveable { mutableStateOf(initialApiUrl) }
    var apiKey by rememberSaveable { mutableStateOf(initialApiKey) }
    var selectedModel by rememberSaveable { mutableStateOf(initialModel) }
    var temperature by rememberSaveable { mutableFloatStateOf(initialTemperature) }
    var maxTokens by rememberSaveable { mutableIntStateOf(initialMaxTokens) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    // modelOptions 已移除：模型列表只显示当前厂商的预设模型和API拉取的可用模型
    // 厂商预设模型列表（选择厂商时更新，初始化时根据当前provider加载）
    var providerModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var ttsEnabled by rememberSaveable { mutableStateOf(initialTtsEnabled) }
    var asrEnabled by rememberSaveable { mutableStateOf(initialAsrEnabled) }
    var speechRate by rememberSaveable { mutableFloatStateOf(initialSpeechRate) }
    var selectedVoice by rememberSaveable { mutableStateOf(initialVoice) }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }
    val voiceOptions = listOf("甜美女声", "温柔男声", "活力少年", "沉稳大叔")

    // === 新增：通过 SettingsManager 直接读写后端配置（避免扩展 AppHost 接口） ===
    val sm = remember { SettingsManager(context) }

    // 关键修复：离开设置页时重建 ApiClient，让用户修改的 API 配置立即生效
    DisposableEffect(Unit) {
        onDispose {
            try {
                com.aicompanion.AppContainer.rebuildApiClient()
                AppLogger.i("SettingsScreen", "[Settings-Dispose] 重建ApiClient: url=${sm.chatApiUrl.take(20)} model=${sm.chatModel}")
            } catch (e: Exception) {
                AppLogger.e("SettingsScreen", "[Settings-Dispose] 重建ApiClient失败: ${e.message}")
            }
        }
    }

    // LLM 高级参数
    var contextTurns by rememberSaveable { mutableIntStateOf(sm.contextTurns) }
    var topP by rememberSaveable { mutableFloatStateOf(sm.llmTopP) }
    var frequencyPenalty by rememberSaveable { mutableFloatStateOf(sm.llmFrequencyPenalty) }
    var presencePenalty by rememberSaveable { mutableFloatStateOf(sm.llmPresencePenalty) }

    // 图片生成 API（存于 VirtualWorldManager 的 securePrefs，通过 VWM 实例读写）
    val vwm = remember { com.aicompanion.virtualworld.VirtualWorldManager(context) }
    var imageApiUrl by rememberSaveable { mutableStateOf(vwm.imageApiUrl) }
    var imageApiKey by rememberSaveable { mutableStateOf(vwm.imageApiKey) }
    var imageModel by rememberSaveable { mutableStateOf(vwm.imageModel) }

    // 屏幕识别
    var screenRecognitionEnabled by rememberSaveable { mutableStateOf(sm.screenRecognitionEnabled) }
    var screenApiUrl by rememberSaveable { mutableStateOf(sm.screenApiUrl) }
    var screenApiKey by rememberSaveable { mutableStateOf(sm.screenApiKey) }
    var screenModel by rememberSaveable { mutableStateOf(sm.screenModel) }
    var useLocalOcr by rememberSaveable { mutableStateOf(sm.useLocalOcr) }
    var useChatModelForVision by rememberSaveable { mutableStateOf(sm.useChatModelForVision) }
    var simpleScreenMode by rememberSaveable { mutableStateOf(sm.simpleScreenMode) }

    // AI 主动消息
    var nagFrequency by rememberSaveable { mutableStateOf(sm.nagFrequency) }
    val nagFrequencyOptions = listOf(
        NagFrequency.OFF to "关闭",
        NagFrequency.LOW to "低频（约30分钟一次）",
        NagFrequency.MEDIUM to "中频（约10分钟一次）",
        NagFrequency.HIGH to "高频（约3分钟一次）",
    )
    var nagDropdownExpanded by remember { mutableStateOf(false) }

    // 搜索
    var searchEnabled by rememberSaveable { mutableStateOf(sm.searchEnabled) }
    var searchApiUrl by rememberSaveable { mutableStateOf(sm.searchApiUrl) }
    var searchApiKey by rememberSaveable { mutableStateOf(sm.searchApiKey) }

    // 日记触发模式
    var diaryTriggerMode by rememberSaveable { mutableStateOf(sm.diaryTriggerMode) }
    val diaryTriggerOptions = listOf(
        DiaryTriggerMode.MANUAL to "手动",
        DiaryTriggerMode.MSG_50 to "每50条消息",
        DiaryTriggerMode.HOURLY to "每小时",
        DiaryTriggerMode.EVERY_2H to "每2小时",
        DiaryTriggerMode.DAILY_10PM to "每日22:00",
    )
    var diaryDropdownExpanded by remember { mutableStateOf(false) }

    // Live2D / 离线模式
    var live2dEnabled by rememberSaveable { mutableStateOf(sm.live2dEnabled) }
    var offlineMode by rememberSaveable { mutableStateOf(sm.offlineModeEnabled) }
    // Live2D 大小本地状态：滑动时只更新本地，松手时才写入（避免频繁 SP 写入 + JS 调用导致卡顿）
    var live2dScaleState by remember { mutableFloatStateOf(live2dScale) }

    // === 新增：LLM 提供商 ===
    var apiProvider by rememberSaveable { mutableStateOf(sm.apiProvider) }
    val providerProfiles = remember { ProviderProfile.getAllProfiles() }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    // 初始化厂商预设模型列表
    val initialPreset = com.aicompanion.settings.ServicePresets.llmPresets.find { it.id == apiProvider }
    if (providerModels.isEmpty() && initialPreset?.models?.isNotEmpty() == true) {
        providerModels = initialPreset.models
    }

    // === 新增：TTS 引擎模式 + API 配置 ===
    var ttsEngineMode by rememberSaveable { mutableStateOf(sm.ttsEngineMode) }
    // TTS 播放模式：直接朗读 / 仅语音气泡
    var ttsPlayMode by rememberSaveable { mutableStateOf(sm.ttsPlayMode) }
    val ttsEngineOptions = listOf(
        TtsManager.ENGINE_EDGE to "Edge TTS（免费）",
        TtsManager.ENGINE_CLOUD to "云端 TTS（API）",
        TtsManager.ENGINE_LOCAL to "系统本地 TTS",
        TtsManager.ENGINE_AUTO to "自动选择",
    )
    var ttsEngineDropdownExpanded by remember { mutableStateOf(false) }

    var ttsProvider by rememberSaveable { mutableStateOf(sm.ttsProvider) }
    val ttsProviderOptions = remember { ServicePresets.ttsPresets }
    var ttsProviderDropdownExpanded by remember { mutableStateOf(false) }

    var ttsApiUrl by rememberSaveable { mutableStateOf(sm.ttsApiUrl) }
    var ttsApiKey by rememberSaveable { mutableStateOf(sm.ttsApiKey) }
    var ttsModel by rememberSaveable { mutableStateOf(sm.ttsModel) }
    var ttsPitch by rememberSaveable { mutableFloatStateOf(sm.ttsPitch) }

    // Edge TTS 音色选择
    var edgeVoiceId by rememberSaveable { mutableStateOf(sm.ttsVoiceName.ifBlank { EdgeTtsEngine.VOICES.first().id }) }
    val edgeVoices = remember { EdgeTtsEngine.VOICES }
    var edgeVoiceDropdownExpanded by remember { mutableStateOf(false) }

    // === 新增：ASR 模式 + API 配置 ===
    var asrMode by rememberSaveable { mutableStateOf(sm.asrMode) }
    val asrModeOptions = listOf(
        LocalAsrManager.MODE_CLOUD to "云端 ASR（API）",
        LocalAsrManager.MODE_SYSTEM to "系统本地 ASR",
        LocalAsrManager.MODE_SHERPA to "Sherpa 本地 ASR",
    )
    var asrModeDropdownExpanded by remember { mutableStateOf(false) }

    var asrProvider by rememberSaveable { mutableStateOf(sm.asrProvider) }
    val asrProviderOptions = remember { ServicePresets.asrPresets }
    var asrProviderDropdownExpanded by remember { mutableStateOf(false) }

    var asrApiUrl by rememberSaveable { mutableStateOf(sm.asrApiUrl) }
    var asrApiKey by rememberSaveable { mutableStateOf(sm.asrApiKey) }
    var asrModel by rememberSaveable { mutableStateOf(sm.asrModel) }

    // === 新增：图片生成/识别 提供商 ===
    var imageGenProvider by rememberSaveable { mutableStateOf(sm.imageGenProvider) }
    val imageGenProviderOptions = remember { ServicePresets.imageGenPresets }
    var imageGenProviderDropdownExpanded by remember { mutableStateOf(false) }

    var imageRecogProvider by rememberSaveable { mutableStateOf(sm.imageRecogProvider) }
    val imageRecogProviderOptions = remember { ServicePresets.imageRecogPresets }
    var imageRecogProviderDropdownExpanded by remember { mutableStateOf(false) }

    // === 新增：情绪分析 / 日志 / 自启动 / 后台运行 ===
    var emotionAnalysisEnabled by rememberSaveable { mutableStateOf(sm.emotionAnalysisEnabled) }
    var llmEmotionAnalysisEnabled by rememberSaveable { mutableStateOf(sm.llmEmotionAnalysisEnabled) }
    var appLoggingEnabled by rememberSaveable { mutableStateOf(sm.appLoggingEnabled) }
    var appDebugVerbose by rememberSaveable { mutableStateOf(sm.appDebugVerbose) }
    var autoStart by rememberSaveable { mutableStateOf(sm.autoStart) }
    var backgroundRunning by rememberSaveable { mutableStateOf(sm.backgroundRunning) }

    // === 新增：用户信息 ===
    var userGender by rememberSaveable { mutableStateOf(sm.userGender) }
    var userBirthday by rememberSaveable { mutableStateOf(sm.userBirthday) }
    var userAppearance by rememberSaveable { mutableStateOf(sm.userAppearance) }
    var userPersonalityDef by rememberSaveable { mutableStateOf(sm.userPersonalityDef) }

    // === 新增：定时唤醒 ===
    var wakeEnabled by rememberSaveable { mutableStateOf(sm.wakeEnabled) }
    var wakeHour by rememberSaveable { mutableIntStateOf(sm.wakeHour) }
    var wakeMinute by rememberSaveable { mutableIntStateOf(sm.wakeMinute) }
    var wakeMessage by rememberSaveable { mutableStateOf(sm.wakeMessage) }
    var scheduledWakes by remember { mutableStateOf(sm.getScheduledWakes()) }
    var showWakeDialog by remember { mutableStateOf(false) }

    // === 新增：搜索引擎配置 ===
    var searchProvider by rememberSaveable { mutableStateOf(sm.searchProvider) }
    var searchEngineId by rememberSaveable { mutableStateOf(sm.searchEngineId) }
    val searchProviderOptions = listOf(
        "duckduckgo" to "DuckDuckGo",
        "google" to "Google",
        "bing" to "Bing",
        "custom" to "自定义",
    )
    var searchProviderDropdownExpanded by remember { mutableStateOf(false) }

    // === 新增：聊天背景 ===
    var chatBackground by rememberSaveable { mutableStateOf(sm.chatBackground) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // 将 URI 转为字符串持久化存储
            val pathStr = uri.toString()
            chatBackground = pathStr
            sm.chatBackground = pathStr
            Toast.makeText(context, "已设置聊天背景", Toast.LENGTH_SHORT).show()
        }
    }

    // === 新增：从文件导入角色（支持 JSON 角色包和 PNG 角色卡） ===
    val personaMgr = remember { com.aicompanion.persona.PersonaManager(context) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 在 IO 线程读取文件并解析，避免阻塞 UI
        Thread {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isPng = mimeType.contains("png", ignoreCase = true) ||
                    uri.toString().lowercase().endsWith(".png")
                val isJson = mimeType.contains("json", ignoreCase = true) ||
                    uri.toString().lowercase().endsWith(".json")

                if (isPng) {
                    // PNG 角色卡：复制到临时文件后用 TavernCardParser 解析
                    val tempFile = File(context.cacheDir, "import_card_${System.currentTimeMillis()}.png")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: run {
                        throw java.io.IOException("无法读取文件")
                    }
                    val result = com.aicompanion.tavern.TavernCardParser.parseFromPng(tempFile.absolutePath)
                    tempFile.delete()
                    result.onSuccess { card ->
                        // 将 ParsedCard 转换为 Persona 并保存
                        val persona = com.aicompanion.persona.Persona(
                            id = java.util.UUID.randomUUID().toString(),
                            name = card.name.ifBlank { "未命名角色" },
                            prompt = card.systemPrompt.ifBlank { card.description },
                            personality = card.personality,
                            description = card.description,
                        )
                        personaMgr.addPersona(persona)
                        // 保存头像（如果有）
                        card.avatarBitmap?.let { bmp ->
                            val avatarFile = File(context.filesDir, "avatar_${persona.id}.png")
                            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, avatarFile.outputStream())
                            personaMgr.updatePersona(persona.id) { it.copy(avatarPath = avatarFile.absolutePath) }
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "已导入角色：${card.name}", Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure { e ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "PNG 角色卡解析失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else if (isJson) {
                    // JSON 文件：先尝试应用自身角色包格式，再尝试 SillyTavern 角色卡格式
                    val jsonStr = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: throw java.io.IOException("无法读取文件")

                    // 尝试 1：应用自身的角色包格式（{"personas": [...]}）
                    val importResult = personaMgr.importPersonas(jsonStr)
                    if (importResult.imported > 0) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                "已导入 ${importResult.imported} 个角色" +
                                    if (importResult.errors.isNotEmpty()) "（${importResult.errors.size}条失败）" else "",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        // 尝试 2：SillyTavern 角色卡 JSON 格式
                        val cardResult = com.aicompanion.tavern.TavernCardParser.parseFromJson(jsonStr)
                        cardResult.onSuccess { card ->
                            val persona = com.aicompanion.persona.Persona(
                                id = java.util.UUID.randomUUID().toString(),
                                name = card.name.ifBlank { "未命名角色" },
                                prompt = card.systemPrompt.ifBlank { card.description },
                                personality = card.personality,
                                description = card.description,
                            )
                            personaMgr.addPersona(persona)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(context, "已导入角色：${card.name}", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure { e ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context,
                                    "JSON 解析失败：${e.message}\n支持格式：应用角色包或 SillyTavern 角色卡",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "不支持的文件类型，仅支持 JSON 和 PNG", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // === 新增：TTS 语速与音色 ===
    var ttsRate by rememberSaveable { mutableFloatStateOf(sm.ttsRate) }
    var ttsVoice by rememberSaveable { mutableStateOf(sm.ttsVoice) }
    var ttsVoicePresetDropdownExpanded by remember { mutableStateOf(false) }

    // === 新增：悬浮窗桌宠开关 ===
    var overlayEnabled by rememberSaveable { mutableStateOf(false) }

    // === 新增：清除所有数据二次确认对话框 ===
    var showClearDataDialog by remember { mutableStateOf(false) }

    // === 新增：服务运行状态刷新触发器 ===
    var serviceStatusTrigger by remember { mutableStateOf(0) }

    // 手风琴模式：记录当前展开的分区名，null 表示全部折叠
    var expandedSection by rememberSaveable { mutableStateOf<String?>("外观设置") }

    // === 新增：RAG 配置 ===
    var personaRagEnabled by rememberSaveable { mutableStateOf(RagConfig.personaRagEnabled) }
    var embeddingMode by rememberSaveable { mutableStateOf(RagConfig.embeddingMode) }
    val embeddingModeOptions = listOf(
        "tfidf" to "TF-IDF（本地轻量）",
        "local" to "ONNX 本地模型（bge-small-zh）",
        "cloud" to "云端嵌入（OpenAI embedding-3）",
    )
    var embeddingModeDropdownExpanded by remember { mutableStateOf(false) }
    var onnxModelReady by remember { mutableStateOf(OnnxModelManager.isModelReady(context)) }
    var onnxDownloading by remember { mutableStateOf(false) }
    // 顶层声明：避免条件分支变化时取消正在进行的下载协程（原位于 if 块内会导致下载启动即被取消）
    val onnxDlScope = rememberCoroutineScope()

    // 云端嵌入API配置
    var cloudEmbeddingUrl by rememberSaveable { mutableStateOf(RagConfig.cloudEmbeddingUrl) }
    var cloudEmbeddingApiKey by rememberSaveable { mutableStateOf(RagConfig.cloudEmbeddingApiKey) }
    var cloudEmbeddingModel by rememberSaveable { mutableStateOf(RagConfig.cloudEmbeddingModel) }

    // 重排序配置
    var rerankerEnabled by rememberSaveable { mutableStateOf(RagConfig.rerankerEnabled) }
    var rerankerTopKBefore by rememberSaveable { mutableIntStateOf(RagConfig.rerankerTopKBefore) }
    var rerankerTopKAfter by rememberSaveable { mutableIntStateOf(RagConfig.rerankerTopKAfter) }

    // RAG 检索参数
    var personaTopK by rememberSaveable { mutableIntStateOf(RagConfig.personaTopK) }
    var chunkMaxChars by rememberSaveable { mutableIntStateOf(RagConfig.chunkMaxChars) }
    var chunkOverlapChars by rememberSaveable { mutableIntStateOf(RagConfig.chunkOverlapChars) }
    var minSimilarity by rememberSaveable { mutableFloatStateOf(RagConfig.minSimilarity) }

    // RAG 模式: auto 自动注入 | tool 工具调用
    var ragMode by rememberSaveable { mutableStateOf(RagConfig.ragMode) }
    val ragModeOptions = listOf(
        "auto" to "自动注入（每次对话自动检索）",
        "tool" to "工具调用（LLM 主动检索）",
    )
    var ragModeDropdownExpanded by remember { mutableStateOf(false) }

    // 树结构 RAG
    var treeRagEnabled by rememberSaveable { mutableStateOf(RagConfig.treeRagEnabled) }
    var treeRagMaxDepth by rememberSaveable { mutableIntStateOf(RagConfig.treeRagMaxDepth) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StradustTheme.colors.background),
    ) {
        StradustTopBar(title = "设置")

        // 头部区域淡入动画
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
        ) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                // ===== 外观设置分组 =====
                SectionTitle(
                    title = "外观设置",
                    icon = Icons.Default.ColorLens,
                    isExpanded = expandedSection == "外观设置",
                    onToggle = { expandedSection = if (expandedSection == "外观设置") null else "外观设置" },
                )

                AnimatedVisibility(visible = expandedSection == "外观设置") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                StradustCard(modifier = Modifier.animateContentSize()) {
                    Text(
                        text = "主题",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 主题选择器：横向滚动，12个主题圆形色块 + 名称
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemeId.entries.forEach { theme ->
                            val isSelected = theme == selectedTheme
                            // 根据当前明暗模式选取对应配色用于预览
                            val themeColors = if (isDark) {
                                DarkColorSchemes.fromId(theme)
                            } else {
                                LightColorSchemes.fromId(theme)
                            }
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .scale(if (isPressed) 0.97f else 1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = StradustTheme.colors.primary,
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                        } else Modifier,
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                    ) { ThemeState.setTheme(theme) } // 关键：写全局状态
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                // 36dp 圆形色块
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(themeColors.primary, CircleShape),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = theme.displayName,
                                    color = if (isSelected) StradustTheme.colors.primary
                                    else StradustTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 深色模式三选一行
                StradustCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = when (darkMode) {
                                DarkMode.FORCE_LIGHT -> Icons.Default.LightMode
                                DarkMode.FORCE_DARK -> Icons.Default.DarkMode
                                else -> Icons.Default.AutoAwesome
                            },
                            contentDescription = null,
                            tint = StradustTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "深色模式",
                                color = StradustTheme.colors.textPrimary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf("跟随系统" to null, "亮色" to false, "暗色" to true).forEach { (label, value) ->
                            val isSelected = ThemeState.currentDarkMode == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) StradustTheme.colors.primary else StradustTheme.colors.surfaceContainerLow)
                                    .clickable(role = Role.Button) { ThemeState.setDarkMode(value) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) StradustTheme.colors.onPrimary else StradustTheme.colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 聊天背景选择
                StradustCard {
                    Text(
                        text = "聊天背景",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (chatBackground.isBlank()) "未设置" else "已设置背景图片",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StradustButton(
                            text = "选择图片",
                            onClick = {
                                pickImageLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                        )
                        Spacer(Modifier.width(12.dp))
                        if (chatBackground.isNotBlank()) {
                            StradustButton(
                                text = "清除背景",
                                onClick = {
                                    chatBackground = ""
                                    sm.chatBackground = ""
                                    Toast.makeText(context, "已清除聊天背景", Toast.LENGTH_SHORT).show()
                                },
                                variant = ButtonVariant.TONAL,
                                size = ButtonSize.SMALL,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== AI 设置分组 =====
                SectionTitle(
                    title = "AI 设置",
                    icon = Icons.Default.Psychology,
                    isExpanded = expandedSection == "AI 设置",
                    onToggle = { expandedSection = if (expandedSection == "AI 设置") null else "AI 设置" },
                )

                AnimatedVisibility(visible = expandedSection == "AI 设置") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                // LLM 提供商选择
                StradustCard {
                    Text(
                        text = "LLM 提供商",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(24.dp),
                                )
                                .clickable { providerDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Category, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = ProviderProfile.getDisplayName(apiProvider),
                                color = StradustTheme.colors.textPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = providerDropdownExpanded,
                            onDismissRequest = { providerDropdownExpanded = false },
                        ) {
                            providerProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.displayName) },
                                    onClick = {
                                        apiProvider = profile.id
                                        providerDropdownExpanded = false
                                        sm.apiProvider = profile.id
                                        // 自动填充 API URL 和默认模型
                                        if (profile.apiUrl.isNotBlank()) {
                                            apiUrl = profile.apiUrl
                                            callbacks?.onApiUrlChange(profile.apiUrl)
                                            sm.chatApiUrl = profile.apiUrl
                                        }
                                        if (profile.defaultModel.isNotBlank()) {
                                            selectedModel = profile.defaultModel
                                            callbacks?.onModelChange(profile.defaultModel)
                                            sm.chatModel = profile.defaultModel
                                        }
                                        // 更新厂商预设模型列表
                                        val preset = com.aicompanion.settings.ServicePresets.llmPresets.find { it.id == profile.id }
                                        providerModels = preset?.models ?: emptyList()
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                StradustCard {
                    Text(
                        text = "API 地址",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = apiUrl,
                        onValueChange = {
                            apiUrl = it
                            callbacks?.onApiUrlChange(it)
                            sm.chatApiUrl = it
                        },
                        hint = "https://api.openai.com/v1",
                        imeAction = ImeAction.Done,
                        singleLine = true,
                    )
                }

                Spacer(Modifier.height(10.dp))

                StradustCard {
                    Text(
                        text = "API Key",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            callbacks?.onApiKeyChange(it)
                            sm.chatApiKey = it
                        },
                        hint = "sk-xxxxxxxxxxxxxxxx",
                        visualTransformation = PasswordVisualTransformation(),
                        imeAction = ImeAction.Done,
                        singleLine = true,
                    )
                }

                Spacer(Modifier.height(10.dp))

                StradustCard {
                    Text(
                        text = "模型选择",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    // 动态模型列表 + 获取按钮
                    var dynamicModels by remember { mutableStateOf<List<String>>(emptyList()) }
                    var isLoadingModels by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 模型下拉框
                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        StradustTheme.colors.surfaceContainerLow,
                                        RoundedCornerShape(24.dp),
                                    )
                                    .clickable { modelDropdownExpanded = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Category, null, tint = StradustTheme.colors.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = selectedModel,
                                    color = StradustTheme.colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Default.ArrowDropDown, null, tint = StradustTheme.colors.textMuted)
                            }
                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false },
                            ) {
                                // 厂商预设模型优先显示
                                if (providerModels.isNotEmpty()) {
                                    providerModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                selectedModel = model
                                                modelDropdownExpanded = false
                                                callbacks?.onModelChange(model)
                                                sm.chatModel = model
                                            },
                                        )
                                    }
                                    if (dynamicModels.isNotEmpty()) HorizontalDivider()
                                }
                                // 动态获取的模型（从API拉取的当前厂商可用模型）
                                if (dynamicModels.isNotEmpty()) {
                                    dynamicModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                selectedModel = model
                                                modelDropdownExpanded = false
                                                callbacks?.onModelChange(model)
                                                sm.chatModel = model
                                            },
                                        )
                                    }
                                }
                                // 两个列表都为空时显示提示
                                if (providerModels.isEmpty() && dynamicModels.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("请点击右侧按钮获取模型列表", color = StradustTheme.colors.textMuted) },
                                        onClick = { modelDropdownExpanded = false },
                                    )
                                }
                            }
                        }
                        
                        // 获取模型列表按钮
                        OutlinedButton(
                            onClick = {
                                if (apiUrl.isNotBlank() && apiKey.isNotBlank()) {
                                    isLoadingModels = true
                                    Thread {
                                        try {
                                            val client = com.aicompanion.network.ApiClient(
                                                chatApiUrl = apiUrl,
                                                apiKey = apiKey,
                                                providerId = apiProvider
                                            )
                                            val models = client.fetchAvailableModels()
                                            Handler(Looper.getMainLooper()).post {
                                                dynamicModels = models
                                                isLoadingModels = false
                                                if (models.isEmpty()) {
                                                    Toast.makeText(context, "未获取到模型列表，请检查API地址和Key", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "获取到 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Handler(Looper.getMainLooper()).post {
                                                isLoadingModels = false
                                                Toast.makeText(context, "获取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }.start()
                                } else {
                                    Toast.makeText(context, "请先配置API地址和Key", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isLoadingModels,
                            modifier = Modifier.height(48.dp),
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = StradustTheme.colors.primary,
                                )
                            } else {
                                Icon(Icons.Default.Refresh, "获取模型")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // 提示信息
                    if (dynamicModels.isEmpty()) {
                        Text(
                            text = "点击右侧按钮可从API获取可用模型列表，或下方直接输入模型名称",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    } else {
                        Text(
                            text = "已获取 ${dynamicModels.size} 个可用模型（下拉选择），或下方直接输入",
                            color = StradustTheme.colors.primary,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // 手动输入模型名称（覆盖下拉框选择）
                    StradustInput(
                        value = selectedModel,
                        onValueChange = {
                            selectedModel = it
                            callbacks?.onModelChange(it)
                            sm.chatModel = it
                        },
                        hint = "手动输入模型名称，如 gpt-4o、deepseek-chat 等",
                        imeAction = ImeAction.Done,
                        singleLine = true,
                    )
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "温度: ${String.format("%.1f", temperature)}",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = temperature,
                        onValueChange = {
                            temperature = it
                        },
                        onValueChangeFinished = {
                            callbacks?.onTemperatureChange(temperature)
                        },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "最大 Token: $maxTokens",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = maxTokens.toFloat(),
                        onValueChange = {
                            maxTokens = it.toInt()
                        },
                        onValueChangeFinished = {
                            callbacks?.onMaxTokensChange(maxTokens)
                        },
                        valueRange = 256f..16384f,
                        steps = 40,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 语音设置分组 =====
                SectionTitle(
                    title = "语音设置",
                    icon = Icons.Default.RecordVoiceOver,
                    isExpanded = expandedSection == "语音设置",
                    onToggle = { expandedSection = if (expandedSection == "语音设置") null else "语音设置" },
                )

                AnimatedVisibility(visible = expandedSection == "语音设置") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    SettingSwitchRow(
                        icon = Icons.Default.Mic,
                        title = "文字转语音 (TTS)",
                        subtitle = "让 AI 用语音回复你",
                        checked = ttsEnabled,
                        onCheckedChange = {
                            ttsEnabled = it
                            callbacks?.onTtsEnabledChange(it)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Mic,
                        title = "语音识别 (ASR)",
                        subtitle = "用语音输入消息",
                        checked = asrEnabled,
                        onCheckedChange = {
                            asrEnabled = it
                            callbacks?.onAsrEnabledChange(it)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "语速: ${String.format("%.1f", speechRate)}x",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = {
                            speechRate = it
                        },
                        onValueChangeFinished = {
                            callbacks?.onSpeechRateChange(speechRate)
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "音色",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(24.dp),
                                )
                                .clickable { voiceDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Face, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = selectedVoice,
                                color = StradustTheme.colors.textPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = voiceDropdownExpanded,
                            onDismissRequest = { voiceDropdownExpanded = false },
                        ) {
                            voiceOptions.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice) },
                                    onClick = {
                                        selectedVoice = voice
                                        voiceDropdownExpanded = false
                                        callbacks?.onVoiceChange(voice)
                                    },
                                )
                            }
                        }
                    }
                }

                // TTS 播放模式选择（直接朗读 / 仅语音气泡）
                StradustCard {
                    Text(
                        text = "播放模式",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "选择 AI 语音回复的方式",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 直接朗读
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (ttsPlayMode == SettingsManager.TTS_MODE_AUTO_PLAY)
                                        StradustTheme.colors.primary.copy(alpha = 0.15f)
                                    else
                                        StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    ttsPlayMode = SettingsManager.TTS_MODE_AUTO_PLAY
                                    sm.ttsPlayMode = SettingsManager.TTS_MODE_AUTO_PLAY
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = if (ttsPlayMode == SettingsManager.TTS_MODE_AUTO_PLAY)
                                    StradustTheme.colors.primary else StradustTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "直接朗读",
                                color = if (ttsPlayMode == SettingsManager.TTS_MODE_AUTO_PLAY)
                                    StradustTheme.colors.primary else StradustTheme.colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (ttsPlayMode == SettingsManager.TTS_MODE_AUTO_PLAY)
                                    FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        // 仅语音气泡
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (ttsPlayMode == SettingsManager.TTS_MODE_BUBBLE_ONLY)
                                        StradustTheme.colors.primary.copy(alpha = 0.15f)
                                    else
                                        StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    ttsPlayMode = SettingsManager.TTS_MODE_BUBBLE_ONLY
                                    sm.ttsPlayMode = SettingsManager.TTS_MODE_BUBBLE_ONLY
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (ttsPlayMode == SettingsManager.TTS_MODE_BUBBLE_ONLY)
                                    StradustTheme.colors.primary else StradustTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "语音气泡",
                                color = if (ttsPlayMode == SettingsManager.TTS_MODE_BUBBLE_ONLY)
                                    StradustTheme.colors.primary else StradustTheme.colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (ttsPlayMode == SettingsManager.TTS_MODE_BUBBLE_ONLY)
                                    FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // TTS 引擎模式选择
                StradustCard {
                    Text(
                        text = "TTS 引擎模式",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                .clickable { ttsEngineDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(ttsEngineOptions.find { it.first == ttsEngineMode }?.second ?: "自动选择", color = StradustTheme.colors.textPrimary)
                        }
                        DropdownMenu(expanded = ttsEngineDropdownExpanded, onDismissRequest = { ttsEngineDropdownExpanded = false }) {
                            ttsEngineOptions.forEach { (mode, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    ttsEngineMode = mode
                                    ttsEngineDropdownExpanded = false
                                    sm.ttsEngineMode = mode
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "音调: ${String.format("%.1f", ttsPitch)}",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = ttsPitch,
                        onValueChange = { ttsPitch = it },
                        onValueChangeFinished = { sm.ttsPitch = ttsPitch },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "语速: ${String.format("%.1f", ttsRate)}x",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = ttsRate,
                        onValueChange = { ttsRate = it },
                        onValueChangeFinished = { sm.ttsRate = ttsRate },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                }

                // Edge TTS 音色选择（EDGE/AUTO 模式时显示）
                if (ttsEngineMode == TtsManager.ENGINE_EDGE || ttsEngineMode == TtsManager.ENGINE_AUTO) {
                    Spacer(Modifier.height(10.dp))
                    StradustCard {
                        Text(
                            text = "Edge TTS 音色（20个免费音色）",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                    .clickable { edgeVoiceDropdownExpanded = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Face, null, tint = StradustTheme.colors.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                val v = edgeVoices.find { it.id == edgeVoiceId }
                                Text("${v?.displayName ?: "晓晓"} (${v?.gender ?: "女"} - ${v?.locale ?: "zh-CN"})", color = StradustTheme.colors.textPrimary)
                            }
                            DropdownMenu(expanded = edgeVoiceDropdownExpanded, onDismissRequest = { edgeVoiceDropdownExpanded = false }) {
                                edgeVoices.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text("${voice.displayName} (${voice.gender} - ${voice.locale})") },
                                        onClick = {
                                            edgeVoiceId = voice.id
                                            edgeVoiceDropdownExpanded = false
                                            sm.ttsVoiceName = voice.id
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // TTS 云端 API 配置（CLOUD/AUTO 模式时显示）
                if (ttsEngineMode == TtsManager.ENGINE_CLOUD || ttsEngineMode == TtsManager.ENGINE_AUTO) {
                    Spacer(Modifier.height(10.dp))
                    StradustCard {
                        Text("TTS 提供商", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                    .clickable { ttsProviderDropdownExpanded = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Category, null, tint = StradustTheme.colors.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text(ttsProviderOptions.find { it.id == ttsProvider }?.displayName ?: "自定义", color = StradustTheme.colors.textPrimary)
                            }
                            DropdownMenu(expanded = ttsProviderDropdownExpanded, onDismissRequest = { ttsProviderDropdownExpanded = false }) {
                                ttsProviderOptions.forEach { preset ->
                                    DropdownMenuItem(text = { Text(preset.displayName) }, onClick = {
                                        ttsProvider = preset.id
                                        ttsProviderDropdownExpanded = false
                                        sm.ttsProvider = preset.id
                                        if (preset.url.isNotBlank()) { ttsApiUrl = preset.url; sm.ttsApiUrl = preset.url }
                                        if (preset.defaultModel.isNotBlank()) { ttsModel = preset.defaultModel; sm.ttsModel = preset.defaultModel }
                                        if (preset.defaultVoice.isNotBlank()) { sm.ttsVoice = preset.defaultVoice }
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("TTS API 地址", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(value = ttsApiUrl, onValueChange = { ttsApiUrl = it; sm.ttsApiUrl = it }, hint = "https://api.openai.com/v1/audio/speech")
                        Spacer(Modifier.height(12.dp))
                        Text("TTS API Key", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(value = ttsApiKey, onValueChange = { ttsApiKey = it; sm.ttsApiKey = it }, hint = "sk-xxxxxxxxxxxxxxxx", visualTransformation = PasswordVisualTransformation())
                        Spacer(Modifier.height(12.dp))
                        Text("TTS 模型", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(value = ttsModel, onValueChange = { ttsModel = it; sm.ttsModel = it }, hint = "tts-1")
                        Spacer(Modifier.height(12.dp))
                        Text("TTS 音色 ID", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(
                            value = ttsVoice,
                            onValueChange = { ttsVoice = it; sm.ttsVoice = it },
                            hint = "音色 ID（如 alloy/echo/onyx）",
                        )
                        // 音色预设：当 ttsProvider 为 openai/siliconflow/qwen 时显示对应预设音色
                        val presetVoices = remember(ttsProvider) {
                            when (ttsProvider) {
                                "openai" -> ApiProviderPreset.providers.find { it.name.contains("OpenAI", ignoreCase = true) }?.ttsVoices ?: emptyList()
                                "siliconflow" -> ApiProviderPreset.providers.find { it.name.contains("Silicon", ignoreCase = true) }?.ttsVoices ?: emptyList()
                                "qwen" -> ApiProviderPreset.providers.find { it.name.contains("Qwen") || it.name.contains("千问") }?.ttsVoices ?: emptyList()
                                else -> emptyList()
                            }
                        }
                        if (presetVoices.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("音色预设", color = StradustTheme.colors.textMuted, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                        .clickable { ttsVoicePresetDropdownExpanded = true }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Face, null, tint = StradustTheme.colors.textSecondary)
                                    Spacer(Modifier.width(8.dp))
                                    val curLabel = presetVoices.find { it.value == ttsVoice }?.label ?: "点击选择音色"
                                    Text(curLabel, color = StradustTheme.colors.textPrimary)
                                }
                                DropdownMenu(expanded = ttsVoicePresetDropdownExpanded, onDismissRequest = { ttsVoicePresetDropdownExpanded = false }) {
                                    presetVoices.forEach { v ->
                                        DropdownMenuItem(
                                            text = { Text("${v.label}${if (v.description.isNotBlank()) " - ${v.description}" else ""}") },
                                            onClick = {
                                                ttsVoice = v.value
                                                sm.ttsVoice = v.value
                                                ttsVoicePresetDropdownExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ASR 模式选择
                Spacer(Modifier.height(10.dp))
                StradustCard {
                    Text("ASR 语音识别模式", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                .clickable { asrModeDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Mic, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(asrModeOptions.find { it.first == asrMode }?.second ?: "云端 ASR", color = StradustTheme.colors.textPrimary)
                        }
                        DropdownMenu(expanded = asrModeDropdownExpanded, onDismissRequest = { asrModeDropdownExpanded = false }) {
                            asrModeOptions.forEach { (mode, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    asrMode = mode
                                    asrModeDropdownExpanded = false
                                    sm.asrMode = mode
                                })
                            }
                        }
                    }
                }

                // ASR 云端 API 配置（CLOUD 模式时显示）
                if (asrMode == LocalAsrManager.MODE_CLOUD) {
                    Spacer(Modifier.height(10.dp))
                    StradustCard {
                        Text("ASR 提供商", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                    .clickable { asrProviderDropdownExpanded = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Category, null, tint = StradustTheme.colors.textSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text(asrProviderOptions.find { it.id == asrProvider }?.displayName ?: "自定义", color = StradustTheme.colors.textPrimary)
                            }
                            DropdownMenu(expanded = asrProviderDropdownExpanded, onDismissRequest = { asrProviderDropdownExpanded = false }) {
                                asrProviderOptions.forEach { preset ->
                                    DropdownMenuItem(text = { Text(preset.displayName) }, onClick = {
                                        asrProvider = preset.id
                                        asrProviderDropdownExpanded = false
                                        sm.asrProvider = preset.id
                                        if (preset.url.isNotBlank()) { asrApiUrl = preset.url; sm.asrApiUrl = preset.url }
                                        if (preset.defaultModel.isNotBlank()) { asrModel = preset.defaultModel; sm.asrModel = preset.defaultModel }
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("ASR API 地址", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(value = asrApiUrl, onValueChange = { asrApiUrl = it; sm.asrApiUrl = it }, hint = "https://api.openai.com/v1/audio/transcriptions")
                        Spacer(Modifier.height(12.dp))
                        Text("ASR API Key", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(value = asrApiKey, onValueChange = { asrApiKey = it; sm.asrApiKey = it }, hint = "sk-xxxxxxxxxxxxxxxx", visualTransformation = PasswordVisualTransformation())
                        Spacer(Modifier.height(12.dp))
                        Text("ASR 模型", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(value = asrModel, onValueChange = { asrModel = it; sm.asrModel = it }, hint = "whisper-1")
                    }
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== LLM 高级参数分组 =====
                SectionTitle(
                    title = "LLM 高级参数",
                    icon = Icons.Default.Psychology,
                    isExpanded = expandedSection == "LLM 高级参数",
                    onToggle = { expandedSection = if (expandedSection == "LLM 高级参数") null else "LLM 高级参数" },
                )

                AnimatedVisibility(visible = expandedSection == "LLM 高级参数") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    Text(
                        text = "上下文轮数: $contextTurns",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = contextTurns.toFloat(),
                        onValueChange = {
                            contextTurns = it.toInt()
                        },
                        onValueChangeFinished = {
                            sm.contextTurns = contextTurns
                        },
                        valueRange = 5f..50f,
                        steps = 44,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Top P: ${String.format("%.2f", topP)}",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = topP,
                        onValueChange = {
                            topP = it
                        },
                        onValueChangeFinished = {
                            sm.llmTopP = topP
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Frequency Penalty: ${String.format("%.2f", frequencyPenalty)}",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = frequencyPenalty,
                        onValueChange = {
                            frequencyPenalty = it
                        },
                        onValueChangeFinished = {
                            sm.llmFrequencyPenalty = frequencyPenalty
                        },
                        valueRange = -2f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Presence Penalty: ${String.format("%.2f", presencePenalty)}",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Slider(
                        value = presencePenalty,
                        onValueChange = {
                            presencePenalty = it
                        },
                        onValueChangeFinished = {
                            sm.llmPresencePenalty = presencePenalty
                        },
                        valueRange = -2f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 图片生成分组 =====
                SectionTitle(
                    title = "图片生成 API",
                    icon = Icons.Default.Image,
                    isExpanded = expandedSection == "图片生成 API",
                    onToggle = { expandedSection = if (expandedSection == "图片生成 API") null else "图片生成 API" },
                )

                AnimatedVisibility(visible = expandedSection == "图片生成 API") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                // 图片生成提供商选择
                StradustCard {
                    Text("图片生成提供商", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                .clickable { imageGenProviderDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Category, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(imageGenProviderOptions.find { it.id == imageGenProvider }?.displayName ?: "自定义", color = StradustTheme.colors.textPrimary)
                        }
                        DropdownMenu(expanded = imageGenProviderDropdownExpanded, onDismissRequest = { imageGenProviderDropdownExpanded = false }) {
                            imageGenProviderOptions.forEach { preset ->
                                DropdownMenuItem(text = { Text(preset.displayName) }, onClick = {
                                    imageGenProvider = preset.id
                                    imageGenProviderDropdownExpanded = false
                                    sm.imageGenProvider = preset.id
                                    if (preset.url.isNotBlank()) { imageApiUrl = preset.url; vwm.imageApiUrl = preset.url }
                                    if (preset.defaultModel.isNotBlank()) { imageModel = preset.defaultModel; vwm.imageModel = preset.defaultModel }
                                })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                StradustCard {
                    Text(
                        text = "API 地址",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = imageApiUrl,
                        onValueChange = {
                            imageApiUrl = it
                            vwm.imageApiUrl = it
                        },
                        hint = "https://api.openai.com/v1/images/generations",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "API Key",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = imageApiKey,
                        onValueChange = {
                            imageApiKey = it
                            vwm.imageApiKey = it
                        },
                        hint = "sk-xxxxxxxxxxxxxxxx",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "模型名称",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = imageModel,
                        onValueChange = {
                            imageModel = it
                            vwm.imageModel = it
                        },
                        hint = "dall-e-3",
                    )
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 屏幕识别分组 =====
                SectionTitle(
                    title = "屏幕识别",
                    icon = Icons.Default.ScreenSearchDesktop,
                    isExpanded = expandedSection == "屏幕识别",
                    onToggle = { expandedSection = if (expandedSection == "屏幕识别") null else "屏幕识别" },
                )

                AnimatedVisibility(visible = expandedSection == "屏幕识别") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    SettingSwitchRow(
                        icon = Icons.Default.ScreenSearchDesktop,
                        title = "屏幕识别",
                        subtitle = "允许 AI 识别当前屏幕内容",
                        checked = screenRecognitionEnabled,
                        onCheckedChange = {
                            screenRecognitionEnabled = it
                            sm.screenRecognitionEnabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    // 图片识别提供商选择
                    Text("图片识别提供商", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                .clickable { imageRecogProviderDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Category, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(imageRecogProviderOptions.find { it.id == imageRecogProvider }?.displayName ?: "自定义", color = StradustTheme.colors.textPrimary)
                        }
                        DropdownMenu(expanded = imageRecogProviderDropdownExpanded, onDismissRequest = { imageRecogProviderDropdownExpanded = false }) {
                            imageRecogProviderOptions.forEach { preset ->
                                DropdownMenuItem(text = { Text(preset.displayName) }, onClick = {
                                    imageRecogProvider = preset.id
                                    imageRecogProviderDropdownExpanded = false
                                    sm.imageRecogProvider = preset.id
                                    if (preset.url.isNotBlank()) { screenApiUrl = preset.url; sm.screenApiUrl = preset.url }
                                    if (preset.defaultModel.isNotBlank()) { screenModel = preset.defaultModel; sm.screenModel = preset.defaultModel }
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "API 地址",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = screenApiUrl,
                        onValueChange = {
                            screenApiUrl = it
                            sm.screenApiUrl = it
                        },
                        hint = "https://api.openai.com/v1",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "API Key",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = screenApiKey,
                        onValueChange = {
                            screenApiKey = it
                            sm.screenApiKey = it
                        },
                        hint = "sk-xxxxxxxxxxxxxxxx",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "视觉模型",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = screenModel,
                        onValueChange = {
                            screenModel = it
                            sm.screenModel = it
                        },
                        hint = "gpt-4o",
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Visibility,
                        title = "使用本地 OCR",
                        subtitle = "离线识别屏幕文字（无需调用 API）",
                        checked = useLocalOcr,
                        onCheckedChange = {
                            useLocalOcr = it
                            sm.useLocalOcr = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Psychology,
                        title = "用聊天模型识别图片",
                        subtitle = "复用聊天 API 进行视觉识别",
                        checked = useChatModelForVision,
                        onCheckedChange = {
                            useChatModelForVision = it
                            sm.useChatModelForVision = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.BrightnessMedium,
                        title = "简易屏幕模式",
                        subtitle = "降低识别频率以节省电量",
                        checked = simpleScreenMode,
                        onCheckedChange = {
                            simpleScreenMode = it
                            sm.simpleScreenMode = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    // 无障碍服务跳转
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val accessibilityReady = remember(serviceStatusTrigger) { AutoOperator.isServiceReady() }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("无障碍服务", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                text = if (accessibilityReady) "已开启" else "未开启",
                                color = if (accessibilityReady) StradustTheme.colors.primary else StradustTheme.colors.textMuted,
                                fontSize = 12.sp,
                            )
                        }
                        StradustButton(
                            text = "去设置",
                            onClick = {
                                try {
                                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开无障碍设置: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // 无障碍高级设置入口：AI 操作权限/异常检测/长按手势/App分类自定义
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("无障碍高级设置", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                text = "AI 操作权限 / 异常检测 / 长按手势 / App 分类",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 12.sp,
                            )
                        }
                        StradustButton(
                            text = "进入",
                            onClick = { onNavigate("accessibility_settings") },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== AI 主动消息分组 =====
                SectionTitle(
                    title = "AI 主动消息",
                    icon = Icons.Default.NotificationsActive,
                    isExpanded = expandedSection == "AI 主动消息",
                    onToggle = { expandedSection = if (expandedSection == "AI 主动消息") null else "AI 主动消息" },
                )

                AnimatedVisibility(visible = expandedSection == "AI 主动消息") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    Text(
                        text = "主动消息频率",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(24.dp),
                                )
                                .clickable { nagDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.NotificationsActive, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = nagFrequencyOptions.find { it.first == nagFrequency }?.second ?: "中频",
                                color = StradustTheme.colors.textPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = nagDropdownExpanded,
                            onDismissRequest = { nagDropdownExpanded = false },
                        ) {
                            nagFrequencyOptions.forEach { (freq, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        nagFrequency = freq
                                        nagDropdownExpanded = false
                                        sm.nagFrequency = freq
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 搜索功能分组 =====
                SectionTitle(
                    title = "搜索功能",
                    icon = Icons.Default.Search,
                    isExpanded = expandedSection == "搜索功能",
                    onToggle = { expandedSection = if (expandedSection == "搜索功能") null else "搜索功能" },
                )

                AnimatedVisibility(visible = expandedSection == "搜索功能") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    SettingSwitchRow(
                        icon = Icons.Default.Search,
                        title = "启用搜索",
                        subtitle = "允许 AI 联网搜索实时信息",
                        checked = searchEnabled,
                        onCheckedChange = {
                            searchEnabled = it
                            sm.searchEnabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "API 地址",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = searchApiUrl,
                        onValueChange = {
                            searchApiUrl = it
                            sm.searchApiUrl = it
                        },
                        hint = "https://api.duckduckgo.com",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "API Key",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    StradustInput(
                        value = searchApiKey,
                        onValueChange = {
                            searchApiKey = it
                            sm.searchApiKey = it
                        },
                        hint = "可选，部分搜索引擎需要",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("搜索引擎", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                .clickable { searchProviderDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Search, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                searchProviderOptions.find { it.first == searchProvider }?.second ?: "自定义",
                                color = StradustTheme.colors.textPrimary,
                            )
                        }
                        DropdownMenu(expanded = searchProviderDropdownExpanded, onDismissRequest = { searchProviderDropdownExpanded = false }) {
                            searchProviderOptions.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        searchProvider = id
                                        searchProviderDropdownExpanded = false
                                        sm.searchProvider = id
                                    },
                                )
                            }
                        }
                    }
                    // 仅当搜索引擎为 Google 时显示引擎 ID 输入
                    if (searchProvider == "google") {
                        Spacer(Modifier.height(12.dp))
                        Text("Google 引擎 ID", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        StradustInput(
                            value = searchEngineId,
                            onValueChange = { searchEngineId = it; sm.searchEngineId = it },
                            hint = " Programmable Search Engine ID",
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 日记触发模式分组 =====
                SectionTitle(
                    title = "日记触发",
                    icon = Icons.Default.EditNote,
                    isExpanded = expandedSection == "日记触发",
                    onToggle = { expandedSection = if (expandedSection == "日记触发") null else "日记触发" },
                )

                AnimatedVisibility(visible = expandedSection == "日记触发") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    Text(
                        text = "自动写日记触发模式",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    StradustTheme.colors.surfaceContainerLow,
                                    RoundedCornerShape(24.dp),
                                )
                                .clickable { diaryDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.EditNote, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = diaryTriggerOptions.find { it.first == diaryTriggerMode }?.second ?: "每日22:00",
                                color = StradustTheme.colors.textPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = diaryDropdownExpanded,
                            onDismissRequest = { diaryDropdownExpanded = false },
                        ) {
                            diaryTriggerOptions.forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        diaryTriggerMode = mode
                                        diaryDropdownExpanded = false
                                        sm.diaryTriggerMode = mode
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 用户信息分组 =====
                SectionTitle(
                    title = "用户信息",
                    icon = Icons.Default.Person,
                    isExpanded = expandedSection == "用户信息",
                    onToggle = { expandedSection = if (expandedSection == "用户信息") null else "用户信息" },
                )

                AnimatedVisibility(visible = expandedSection == "用户信息") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    Text("性别", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    StradustInput(value = userGender, onValueChange = { userGender = it; sm.userGender = it }, hint = "男 / 女 / 其他", singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text("生日", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    StradustInput(value = userBirthday, onValueChange = { userBirthday = it; sm.userBirthday = it }, hint = "YYYY-MM-DD", singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text("外貌特征", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    StradustInput(value = userAppearance, onValueChange = { userAppearance = it; sm.userAppearance = it }, hint = "如：黑色长发、戴眼镜...")
                    Spacer(Modifier.height(12.dp))
                    Text("用户人格定义", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    StradustInput(value = userPersonalityDef, onValueChange = { userPersonalityDef = it; sm.userPersonalityDef = it }, hint = "描述你的性格特征，AI 会据此调整对话风格")
                    Spacer(Modifier.height(12.dp))
                    // userId 只读展示
                    Text("用户 ID", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sm.userId,
                        color = StradustTheme.colors.textMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    // aiSummarizedPersonality 只读展示
                    Text("AI 总结的人格画像", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sm.aiSummarizedPersonality.ifBlank { "尚未生成" },
                        color = if (sm.aiSummarizedPersonality.isBlank()) StradustTheme.colors.textMuted else StradustTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 定时唤醒分组 =====
                SectionTitle(
                    title = "定时唤醒",
                    icon = Icons.Default.NotificationsActive,
                    isExpanded = expandedSection == "定时唤醒",
                    onToggle = { expandedSection = if (expandedSection == "定时唤醒") null else "定时唤醒" },
                )

                AnimatedVisibility(visible = expandedSection == "定时唤醒") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    SettingSwitchRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "每日定时唤醒",
                        subtitle = "在指定时间收到 AI 的问候消息",
                        checked = wakeEnabled,
                        onCheckedChange = {
                            wakeEnabled = it
                            sm.wakeEnabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("唤醒时间", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${String.format("%02d", wakeHour)}:${String.format("%02d", wakeMinute)}", color = StradustTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(16.dp))
                        Slider(
                            value = wakeHour.toFloat(),
                            onValueChange = { wakeHour = it.toInt() },
                            onValueChangeFinished = { sm.wakeHour = wakeHour },
                            valueRange = 0f..23f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = StradustTheme.colors.primary,
                                activeTrackColor = StradustTheme.colors.primary,
                                inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                            ),
                        )
                        Slider(
                            value = wakeMinute.toFloat(),
                            onValueChange = { wakeMinute = it.toInt() },
                            onValueChangeFinished = { sm.wakeMinute = wakeMinute },
                            valueRange = 0f..59f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = StradustTheme.colors.primary,
                                activeTrackColor = StradustTheme.colors.primary,
                                inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                            ),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("唤醒消息", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    StradustInput(value = wakeMessage, onValueChange = { wakeMessage = it; sm.wakeMessage = it }, hint = "早上好！今天想聊点什么？")
                    Spacer(Modifier.height(16.dp))
                    // 精确闹钟权限检查（API 31+）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val alarmManager = remember { context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager }
                        if (!alarmManager.canScheduleExactAlarms()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "需要精确闹钟权限",
                                    color = StradustTheme.colors.error,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                StradustButton(
                                    text = "去授权",
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "无法打开设置: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    variant = ButtonVariant.OUTLINED,
                                    size = ButtonSize.SMALL,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    // 定时唤醒列表标题 + 添加按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("定时唤醒列表（${scheduledWakes.size}个）", color = StradustTheme.colors.textMuted, fontSize = 13.sp)
                        StradustButton(
                            text = "添加唤醒",
                            onClick = { showWakeDialog = true },
                            variant = ButtonVariant.OUTLINED,
                            size = ButtonSize.SMALL,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // 唤醒列表项：时间 + 消息 + 启用 Switch + 删除按钮
                    scheduledWakes.forEachIndexed { index, wake ->
                        val dayLabel = when (wake.daysOfWeek) {
                            -1 -> "每天"
                            0 -> "周日"
                            1 -> "周一"
                            2 -> "周二"
                            3 -> "周三"
                            4 -> "周四"
                            5 -> "周五"
                            6 -> "周六"
                            else -> "每天"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${wake.time} ($dayLabel) - ${wake.message.take(20)}",
                                    color = StradustTheme.colors.textSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                            Switch(
                                checked = wake.enabled,
                                onCheckedChange = { newEnabled ->
                                    val updatedList = scheduledWakes.toMutableList()
                                    updatedList[index] = wake.copy(enabled = newEnabled)
                                    scheduledWakes = updatedList
                                    sm.setScheduledWakes(updatedList)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = StradustTheme.colors.onPrimary,
                                    checkedTrackColor = StradustTheme.colors.primary,
                                    uncheckedThumbColor = StradustTheme.colors.surfaceContainerHigh,
                                    uncheckedTrackColor = StradustTheme.colors.outlineVariant,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            StradustButton(
                                text = "删除",
                                onClick = {
                                    val updatedList = scheduledWakes.toMutableList()
                                    updatedList.removeAt(index)
                                    scheduledWakes = updatedList
                                    sm.setScheduledWakes(updatedList)
                                    Toast.makeText(context, "已删除唤醒", Toast.LENGTH_SHORT).show()
                                },
                                variant = ButtonVariant.TONAL,
                                size = ButtonSize.SMALL,
                            )
                        }
                    }
                }

                // === WakeDialog：定时唤醒编辑对话框 ===
                if (showWakeDialog) {
                    WakeDialog(
                        onDismiss = { showWakeDialog = false },
                        onConfirm = { newWake ->
                            val updatedList = scheduledWakes.toMutableList()
                            updatedList.add(newWake)
                            scheduledWakes = updatedList
                            sm.setScheduledWakes(updatedList)
                            showWakeDialog = false
                            Toast.makeText(context, "已添加唤醒", Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== RAG 配置分组 =====
                SectionTitle(
                    title = "RAG 检索增强",
                    icon = Icons.Default.Search,
                    isExpanded = expandedSection == "RAG 检索增强",
                    onToggle = { expandedSection = if (expandedSection == "RAG 检索增强") null else "RAG 检索增强" },
                )

                AnimatedVisibility(visible = expandedSection == "RAG 检索增强") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    SettingSwitchRow(
                        icon = Icons.Default.Search,
                        title = "角色 RAG 检索",
                        subtitle = "从角色知识库中检索相关信息增强回复",
                        checked = personaRagEnabled,
                        onCheckedChange = {
                            personaRagEnabled = it
                            RagConfig.personaRagEnabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("嵌入模式", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                .clickable { embeddingModeDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Category, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(embeddingModeOptions.find { it.first == embeddingMode }?.second ?: "TF-IDF", color = StradustTheme.colors.textPrimary)
                        }
                        DropdownMenu(expanded = embeddingModeDropdownExpanded, onDismissRequest = { embeddingModeDropdownExpanded = false }) {
                            embeddingModeOptions.forEach { (mode, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    embeddingMode = mode
                                    embeddingModeDropdownExpanded = false
                                    RagConfig.embeddingMode = mode
                                })
                            }
                        }
                    }
                    // ONNX 模型状态
                    if (embeddingMode == "local") {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("ONNX 本地模型", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    if (onnxModelReady) "已下载 (${OnnxModelManager.getModelSizeMB(context)}MB)" else "未下载",
                                    color = if (onnxModelReady) StradustTheme.colors.primary else StradustTheme.colors.textMuted,
                                    fontSize = 13.sp,
                                )
                            }
                            if (!onnxModelReady && !onnxDownloading) {
                                StradustButton(
                                    text = "下载模型",
                                    onClick = {
                                        onnxDownloading = true
                                        onnxDlScope.launch {
                                            OnnxModelManager.downloadModel(context, callback = object : com.aicompanion.rag.OnnxModelManager.DownloadCallback {
                                                override fun onProgress(downloadedBytes: Long, totalBytes: Long, percent: Int) {}
                                                override fun onComplete(success: Boolean, error: String?) {
                                                    onnxDownloading = false
                                                    onnxModelReady = success
                                                }
                                            })
                                        }
                                    },
                                    variant = com.aicompanion.ui.components.ButtonVariant.OUTLINED,
                                    size = com.aicompanion.ui.components.ButtonSize.SMALL,
                                )
                            } else if (onnxDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = StradustTheme.colors.primary)
                            }
                        }
                    }

                    // 云端嵌入API配置(仅cloud模式显示)
                    if (embeddingMode == "cloud") {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(
                            color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "云端嵌入 API 地址",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        StradustInput(
                            value = cloudEmbeddingUrl,
                            onValueChange = {
                                cloudEmbeddingUrl = it
                                RagConfig.cloudEmbeddingUrl = it
                            },
                            hint = "https://api.openai.com/v1/embeddings",
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "云端嵌入 API Key",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        StradustInput(
                            value = cloudEmbeddingApiKey,
                            onValueChange = {
                                cloudEmbeddingApiKey = it
                                RagConfig.cloudEmbeddingApiKey = it
                            },
                            hint = "sk-xxxxxxxxxxxxxxxx",
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "嵌入模型名称",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        StradustInput(
                            value = cloudEmbeddingModel,
                            onValueChange = {
                                cloudEmbeddingModel = it
                                RagConfig.cloudEmbeddingModel = it
                            },
                            hint = "text-embedding-3-small",
                        )
                    }

                    // 重排序开关
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        title = "重排序模型",
                        subtitle = "对RAG检索结果进行二次排序(需下载266MB模型)",
                        checked = rerankerEnabled,
                        onCheckedChange = {
                            rerankerEnabled = it
                            RagConfig.rerankerEnabled = it
                            // 切换开关时重置 reranker 状态,允许重新尝试初始化
                            try {
                                com.aicompanion.AppContainer.personaRagManager.resetRerankerState()
                            } catch (e: Exception) {
                                com.aicompanion.util.AppLogger.d("SettingsScreen", "resetRerankerState: ${e.message}")
                            }
                        },
                    )
                    if (rerankerEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "提示: 重排序模型较大(266MB),首次使用需下载。模型使用XLM-RoBERTa架构,分词器为简化实现。",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "重排序前候选数: $rerankerTopKBefore",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Slider(
                            value = rerankerTopKBefore.toFloat(),
                            onValueChange = { rerankerTopKBefore = it.toInt() },
                            onValueChangeFinished = { RagConfig.rerankerTopKBefore = rerankerTopKBefore },
                            valueRange = 3f..30f,
                            steps = 26,
                            colors = SliderDefaults.colors(
                                thumbColor = StradustTheme.colors.primary,
                                activeTrackColor = StradustTheme.colors.primary,
                                inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "重排序后保留数: $rerankerTopKAfter",
                            color = StradustTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Slider(
                            value = rerankerTopKAfter.toFloat(),
                            onValueChange = { rerankerTopKAfter = it.toInt() },
                            onValueChangeFinished = { RagConfig.rerankerTopKAfter = rerankerTopKAfter },
                            valueRange = 1f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = StradustTheme.colors.primary,
                                activeTrackColor = StradustTheme.colors.primary,
                                inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                            ),
                        )
                    }

                    // === RAG 检索参数 ===
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "检索参数",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "返回结果数 Top-K: $personaTopK",
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Slider(
                        value = personaTopK.toFloat(),
                        onValueChange = { personaTopK = it.toInt() },
                        onValueChangeFinished = { RagConfig.personaTopK = personaTopK },
                        valueRange = 1f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "分块最大字符数: $chunkMaxChars",
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Slider(
                        value = chunkMaxChars.toFloat(),
                        onValueChange = { chunkMaxChars = it.toInt() },
                        onValueChangeFinished = { RagConfig.chunkMaxChars = chunkMaxChars },
                        valueRange = 100f..1000f,
                        steps = 17,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "分块重叠字符数: $chunkOverlapChars",
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Slider(
                        value = chunkOverlapChars.toFloat(),
                        onValueChange = { chunkOverlapChars = it.toInt() },
                        onValueChangeFinished = { RagConfig.chunkOverlapChars = chunkOverlapChars },
                        valueRange = 0f..300f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "最小相似度阈值: ${String.format("%.2f", minSimilarity)}",
                        color = StradustTheme.colors.textPrimary,
                        fontSize = 13.sp,
                    )
                    Slider(
                        value = minSimilarity,
                        onValueChange = { minSimilarity = it },
                        onValueChangeFinished = { RagConfig.minSimilarity = minSimilarity },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = StradustTheme.colors.primary,
                            activeTrackColor = StradustTheme.colors.primary,
                            inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                        ),
                    )

                    // === RAG 模式选择 ===
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "RAG 调用模式",
                        color = StradustTheme.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                                .clickable { ragModeDropdownExpanded = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Psychology, null, tint = StradustTheme.colors.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                ragModeOptions.find { it.first == ragMode }?.second ?: "自动注入",
                                color = StradustTheme.colors.textPrimary,
                                fontSize = 13.sp,
                            )
                        }
                        DropdownMenu(expanded = ragModeDropdownExpanded, onDismissRequest = { ragModeDropdownExpanded = false }) {
                            ragModeOptions.forEach { (mode, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    ragMode = mode
                                    ragModeDropdownExpanded = false
                                    RagConfig.ragMode = mode
                                })
                            }
                        }
                    }

                    // === 树结构 RAG ===
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp,
                    )
                    Spacer(Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.Link,
                        title = "树结构 RAG（分块超链接）",
                        subtitle = "检索时沿 [[超链接]] 扩展相关分块上下文",
                        checked = treeRagEnabled,
                        onCheckedChange = {
                            treeRagEnabled = it
                            RagConfig.treeRagEnabled = it
                        },
                    )
                    if (treeRagEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "最大扩展深度: $treeRagMaxDepth",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 13.sp,
                        )
                        Slider(
                            value = treeRagMaxDepth.toFloat(),
                            onValueChange = { treeRagMaxDepth = it.toInt() },
                            onValueChangeFinished = { RagConfig.treeRagMaxDepth = treeRagMaxDepth },
                            valueRange = 1f..5f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = StradustTheme.colors.primary,
                                activeTrackColor = StradustTheme.colors.primary,
                                inactiveTrackColor = StradustTheme.colors.surfaceContainerHigh,
                            ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "提示: 分块内容中的 [[链接文本]] 会被自动提取,检索时按深度沿链接扩展更多上下文。",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 其他功能分组 =====
                SectionTitle(
                    title = "其他功能",
                    icon = Icons.Default.AutoAwesome,
                    isExpanded = expandedSection == "其他功能",
                    onToggle = { expandedSection = if (expandedSection == "其他功能") null else "其他功能" },
                )

                AnimatedVisibility(visible = expandedSection == "其他功能") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    SettingSwitchRow(
                        icon = Icons.Default.Face,
                        title = "Live2D 桌宠",
                        subtitle = "在聊天页面显示 Live2D 角色模型",
                        checked = live2dEnabled,
                        onCheckedChange = {
                            live2dEnabled = it
                            sm.live2dEnabled = it
                        },
                    )
                    // Live2D 模型切换（仅在启用时显示）
                    if (live2dEnabled && live2dModels.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        // 模型选择列表
                        live2dModels.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLive2DModelChange(model.id) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (model.id == currentLive2DModelId)
                                        Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (model.id == currentLive2DModelId)
                                        StradustTheme.colors.primary else StradustTheme.colors.textMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.name,
                                        color = StradustTheme.colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (model.description.isNotBlank()) {
                                        Text(
                                            text = model.description,
                                            color = StradustTheme.colors.textSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                                if (model.sizeMB > 0f) {
                                    Text(
                                        text = "${model.sizeMB.toInt()}MB",
                                        color = StradustTheme.colors.textMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                    // Live2D 导入与扫描按钮（启用时显示）
                    if (live2dEnabled) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 导入模型按钮
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigate("live2d_import") }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = StradustTheme.colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "导入模型",
                                    color = StradustTheme.colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            // 扫描本地模型按钮
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigate("live2d_scan") }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = StradustTheme.colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "扫描本地",
                                    color = StradustTheme.colors.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    // Live2D 大小调节滑块（仅在启用时显示）
                    if (live2dEnabled) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(
                            color = StradustTheme.colors.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = null,
                                tint = StradustTheme.colors.textMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "模型大小",
                                        color = StradustTheme.colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = String.format("%.1f", live2dScaleState),
                                        color = StradustTheme.colors.primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Slider(
                                    value = live2dScaleState,
                                    onValueChange = { live2dScaleState = it },
                                    onValueChangeFinished = { onLive2DScaleChange(live2dScaleState) },
                                    valueRange = 0.3f..3.0f,
                                    steps = 26, // 0.3 到 3.0，步进 0.1（27个点，26个间隔）
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = StradustTheme.colors.primary,
                                        activeTrackColor = StradustTheme.colors.primary,
                                    ),
                                )
                                Text(
                                    text = "长按模型可拖动位置",
                                    color = StradustTheme.colors.textMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.BrightnessMedium,
                        title = "离线模式",
                        subtitle = "断网时使用本地缓存与本地 ASR/TTS",
                        checked = offlineMode,
                        onCheckedChange = {
                            offlineMode = it
                            sm.offlineModeEnabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Psychology,
                        title = "情绪分析",
                        subtitle = "分析消息情绪并影响 AI 回复参数",
                        checked = emotionAnalysisEnabled,
                        onCheckedChange = {
                            emotionAnalysisEnabled = it
                            sm.emotionAnalysisEnabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Psychology,
                        title = "LLM 情绪分析",
                        subtitle = "使用 LLM 深度分析情绪（更精准但更慢）",
                        checked = llmEmotionAnalysisEnabled,
                        onCheckedChange = {
                            llmEmotionAnalysisEnabled = it
                            sm.llmEmotionAnalysisEnabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Description,
                        title = "应用日志",
                        subtitle = "记录运行日志用于调试",
                        checked = appLoggingEnabled,
                        onCheckedChange = {
                            appLoggingEnabled = it
                            sm.appLoggingEnabled = it
                            com.aicompanion.util.AppLogger.enabled = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.Description,
                        title = "详细调试日志",
                        subtitle = "输出更详细的调试信息",
                        checked = appDebugVerbose,
                        onCheckedChange = {
                            appDebugVerbose = it
                            sm.appDebugVerbose = it
                            com.aicompanion.util.AppLogger.debugVerbose = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "开机自启动",
                        subtitle = "开机后自动启动应用",
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            sm.autoStart = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingSwitchRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "后台运行",
                        subtitle = "保持应用在后台运行以接收消息",
                        checked = backgroundRunning,
                        onCheckedChange = {
                            backgroundRunning = it
                            sm.backgroundRunning = it
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    // 悬浮窗桌宠开关
                    SettingSwitchRow(
                        icon = Icons.Default.Face,
                        title = "悬浮窗桌宠",
                        subtitle = "显示悬浮窗 AI 桌宠（需悬浮窗权限）",
                        checked = overlayEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // 开启时检查悬浮窗权限
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !AndroidSettings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                                    try {
                                        context.startActivity(
                                            Intent(
                                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + context.packageName),
                                            ),
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法打开设置: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    // 已有权限，启动悬浮窗服务
                                    try {
                                        context.startForegroundService(Intent(context, OverlayService::class.java))
                                        overlayEnabled = true
                                        Toast.makeText(context, "悬浮窗已开启", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                // 关闭悬浮窗服务
                                try {
                                    context.stopService(Intent(context, OverlayService::class.java))
                                } catch (_: Exception) {}
                                overlayEnabled = false
                                Toast.makeText(context, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    // 电池优化白名单状态行
                    val powerManager = remember(serviceStatusTrigger) {
                        context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                    }
                    val isIgnoringBattery = remember(serviceStatusTrigger) {
                        powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("电池优化白名单", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(
                                text = if (isIgnoringBattery) "已加入白名单" else "未加入白名单（可能影响后台运行）",
                                color = if (isIgnoringBattery) StradustTheme.colors.primary else StradustTheme.colors.textMuted,
                                fontSize = 12.sp,
                            )
                        }
                        if (!isIgnoringBattery) {
                            StradustButton(
                                text = "去设置",
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:" + context.packageName),
                                            ),
                                        )
                                    } catch (e: Exception) {
                                        // 部分设备不支持直接申请，跳转到电池优化设置列表
                                        try {
                                            context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "无法打开电池优化设置: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                variant = ButtonVariant.OUTLINED,
                                size = ButtonSize.SMALL,
                            )
                        }
                    }
                }
                    }
                }
                }

                item {
                // ===== 功能入口分组 =====
                SectionTitle(
                    title = "功能入口",
                    icon = Icons.Default.AutoAwesome,
                    isExpanded = expandedSection == "功能入口",
                    onToggle = { expandedSection = if (expandedSection == "功能入口") null else "功能入口" },
                )

                AnimatedVisibility(visible = expandedSection == "功能入口") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                // iLink 微信绑定
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("ilink") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(text = "iLink 微信绑定", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = StradustTheme.colors.textMuted)
                    }
                }

                // 本地模型管理
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("local_model") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(text = "本地模型管理", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = StradustTheme.colors.textMuted)
                    }
                }

                // 记忆池管理
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("memory_pool") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(text = "记忆池管理", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = StradustTheme.colors.textMuted)
                    }
                }

                // 贴纸管理
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("sticker_manager") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.EmojiEmotions, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(text = "贴纸管理", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = StradustTheme.colors.textMuted)
                    }
                }

                // 角色统计
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("persona_stats") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(text = "角色统计", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = StradustTheme.colors.textMuted)
                    }
                }

                // 从文件导入角色：支持 JSON 角色包和 PNG 角色卡（SillyTavern 格式）
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "从文件导入角色", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                                Text(
                                    text = "支持 JSON 角色包 / PNG 角色卡（SillyTavern）",
                                    color = StradustTheme.colors.textMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StradustButton(
                            text = "选择文件导入",
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/json", "image/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // 收藏夹
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("favorites") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(text = "收藏夹", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = StradustTheme.colors.textMuted)
                    }
                }

                // 唤醒任务高级管理
                StradustCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onNavigate("wakeup_task") }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = StradustTheme.colors.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(text = "唤醒任务高级管理", color = StradustTheme.colors.textPrimary, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = StradustTheme.colors.textMuted)
                    }
                }

                // === 系统服务状态卡片 ===
                Spacer(Modifier.height(10.dp))
                StradustCard {
                    Text("系统服务状态", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    // 刷新按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("点击刷新查看最新状态", color = StradustTheme.colors.textMuted, fontSize = 12.sp)
                        StradustButton(
                            text = "刷新",
                            onClick = { serviceStatusTrigger++ },
                            variant = ButtonVariant.TONAL,
                            size = ButtonSize.SMALL,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // 各服务状态行
                    val bgRunning = remember(serviceStatusTrigger) { isServiceRunning(context, "com.aicompanion.services.BackgroundService") }
                    val overlayRunning = remember(serviceStatusTrigger) { isServiceRunning(context, "com.aicompanion.services.OverlayService") }
                    val screenCaptureRunning = remember(serviceStatusTrigger) { ScreenCaptureService.isRunning }
                    val wechatRunning = remember(serviceStatusTrigger) { isServiceRunning(context, "com.aicompanion.ilink.IlinkPollingService") }
                    val accessibilityReady = remember(serviceStatusTrigger) { AutoOperator.isServiceReady() }

                    ServiceStatusRow("后台服务", bgRunning)
                    ServiceStatusRow("悬浮窗服务", overlayRunning)
                    ServiceStatusRow("屏幕截图服务", screenCaptureRunning)
                    ServiceStatusRow("微信监听服务", wechatRunning)
                    ServiceStatusRow("无障碍服务", accessibilityReady)
                }

                // === 存储管理卡片 ===
                Spacer(Modifier.height(10.dp))
                StradustCard {
                    Text("存储管理", color = StradustTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    // 清空当前对话上下文
                    StradustButton(
                        text = "清空当前对话上下文",
                        onClick = {
                            try {
                                val cm = ContextManager(context)
                                cm.clear()
                                Toast.makeText(context, "已清空当前对话上下文", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 清理 TTS 音频缓存
                    StradustButton(
                        text = "清理 TTS 音频缓存",
                        onClick = {
                            try {
                                TtsManager(context).cleanupOldAudio()
                                Toast.makeText(context, "已清理 TTS 音频缓存", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "清理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 清除头像缓存
                    StradustButton(
                        text = "清除头像缓存",
                        onClick = {
                            try {
                                AvatarManager.clearCache()
                                Toast.makeText(context, "已清除头像缓存", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        variant = ButtonVariant.OUTLINED,
                        size = ButtonSize.SMALL,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 清除所有数据（红色警告）
                    TextButton(
                        onClick = { showClearDataDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "清除所有数据",
                            color = StradustTheme.colors.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // === 清除所有数据二次确认对话框 ===
                if (showClearDataDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDataDialog = false },
                        title = {
                            Text(
                                text = "确认清除所有数据？",
                                color = StradustTheme.colors.error,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        text = {
                            Text(
                                text = "此操作将清除所有设置、API Key、对话历史等数据，且不可恢复。确定继续吗？",
                                color = StradustTheme.colors.textPrimary,
                                fontSize = 13.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                try {
                                    sm.clearAllData()
                                    Toast.makeText(context, "已清除所有数据", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                showClearDataDialog = false
                            }) {
                                Text("确认清除", color = StradustTheme.colors.error, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDataDialog = false }) {
                                Text("取消", color = StradustTheme.colors.textSecondary)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                    }
                }
                }

                item {
                // ===== 关于分组 =====
                SectionTitle(
                    title = "关于",
                    icon = Icons.Default.Info,
                    isExpanded = expandedSection == "关于",
                    onToggle = { expandedSection = if (expandedSection == "关于") null else "关于" },
                )

                AnimatedVisibility(visible = expandedSection == "关于") {
                    Column(modifier = Modifier.fillMaxWidth()) {

                StradustCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "版本号",
                                color = StradustTheme.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "星尘 v1.0.0 (Build 2024.06)",
                                color = StradustTheme.colors.textMuted,
                                fontSize = 13.sp,
                            )
                        }
                        Icon(Icons.Default.Description, null, tint = StradustTheme.colors.textMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "开源协议: MIT License",
                        color = StradustTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 检查更新按钮：真实读取本地版本号 + 状态机驱动 UI
                    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val scope = rememberCoroutineScope()
                    val currentVersion = remember {
                        try {
                            ctx.packageManager.getPackageInfo(ctx.packageName, 0)?.versionName ?: "1.0.0"
                        } catch (_: Exception) { "1.0.0" }
                    }
                    // 已知最新版本（项目无更新服务器时，与当前版本一致即"已是最新"）
                    // 发布新版本时，更新此常量即可触发 UpdateAvailable 流程
                    val knownLatestVersion = "1.0.0"

                    StradustButton(
                        text = when (updateCheckState) {
                            UpdateCheckState.Checking -> "检查中..."
                            is UpdateCheckState.UpdateAvailable -> "立即更新"
                            is UpdateCheckState.Error -> "重试"
                            else -> "检查更新"
                        },
                        enabled = updateCheckState !is UpdateCheckState.Checking,
                        onClick = {
                            // 已发现新版本时点击 = 立即更新（跳转浏览器）
                            val state = updateCheckState
                            if (state is UpdateCheckState.UpdateAvailable) {
                                Toast.makeText(
                                    context,
                                    "请前往应用发布渠道下载 v${state.newVersion}",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@StradustButton
                            }
                            // 进入检查中状态
                            updateCheckState = UpdateCheckState.Checking
                            scope.launch {
                                try {
                                    kotlinx.coroutines.delay(1200)
                                    // 对比已知最新版本与当前版本
                                    if (knownLatestVersion != currentVersion) {
                                        updateCheckState = UpdateCheckState.UpdateAvailable(
                                            newVersion = knownLatestVersion,
                                            currentVersion = currentVersion,
                                        )
                                    } else {
                                        updateCheckState = UpdateCheckState.UpToDate(currentVersion)
                                    }
                                } catch (e: Exception) {
                                    updateCheckState = UpdateCheckState.Error(e.message ?: "未知错误")
                                }
                            }
                        },
                    )
                    // 状态文本展示（启用所有状态分支）
                    Spacer(Modifier.height(6.dp))
                    when (val state = updateCheckState) {
                        UpdateCheckState.Idle -> Text(
                            text = "当前版本：v$currentVersion",
                            color = StradustTheme.colors.textMuted,
                            fontSize = 12.sp,
                        )
                        UpdateCheckState.Checking -> Text(
                            text = "正在检查更新...",
                            color = StradustTheme.colors.primary,
                            fontSize = 12.sp,
                        )
                        is UpdateCheckState.UpToDate -> Text(
                            text = "✓ 当前版本 v${state.version} 已是最新",
                            color = StradustTheme.colors.primary,
                            fontSize = 12.sp,
                        )
                        is UpdateCheckState.UpdateAvailable -> Text(
                            text = "✨ 发现新版本 v${state.newVersion}（当前 v${state.currentVersion}）",
                            color = StradustTheme.colors.error,
                            fontSize = 12.sp,
                        )
                        is UpdateCheckState.Error -> Text(
                            text = "检查失败：${state.message}",
                            color = StradustTheme.colors.error,
                            fontSize = 12.sp,
                        )
                    }
                }
                    }
                }
                }

                // ===== 末尾作者资助文案 =====
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "作者穷的只能送外卖了给点米资助一下吧",
                        color = StradustTheme.colors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 分组标题：icon + 文字 + 展开/收起箭头，可点击切换 */
@Composable
private fun SectionTitle(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean = true,
    onToggle: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { onToggle() }
            .padding(bottom = 8.dp, top = 8.dp),
    ) {
        Icon(icon, null, tint = StradustTheme.colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = StradustTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = StradustTheme.colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 设置行：左图标(20dp) + 中间(标题+副标题) + 右 Switch，整个行 clickable */
@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = StradustTheme.colors.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Text(
                text = subtitle,
                color = StradustTheme.colors.textMuted,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StradustTheme.colors.onPrimary,
                checkedTrackColor = StradustTheme.colors.primary,
                uncheckedThumbColor = StradustTheme.colors.surfaceContainerHigh,
                uncheckedTrackColor = StradustTheme.colors.outlineVariant,
            ),
        )
    }
}

/** 检查更新状态机 */
private sealed class UpdateCheckState {
    /** 初始空闲态 */
    object Idle : UpdateCheckState()
    /** 检查中（显示加载态） */
    object Checking : UpdateCheckState()
    /** 已是最新版本 */
    data class UpToDate(val version: String) : UpdateCheckState()
    /** 发现新版本 */
    data class UpdateAvailable(val newVersion: String, val currentVersion: String) : UpdateCheckState()
    /** 检查失败 */
    data class Error(val message: String) : UpdateCheckState()
}

/**
 * 定时唤醒编辑对话框
 * 包含：小时选择、分钟选择、消息输入、启用开关、星期选择
 */
@Composable
private fun WakeDialog(
    onDismiss: () -> Unit,
    onConfirm: (ScheduledWake) -> Unit,
) {
    // 对话框内部状态
    var dialogHour by remember { mutableIntStateOf(8) }
    var dialogMinute by remember { mutableIntStateOf(0) }
    var dialogMessage by remember { mutableStateOf("早上好！今天想聊点什么？") }
    var dialogEnabled by remember { mutableStateOf(true) }
    var dialogDayOfWeek by remember { mutableIntStateOf(-1) } // -1 = 每天
    var hourDropdownExpanded by remember { mutableStateOf(false) }
    var minuteDropdownExpanded by remember { mutableStateOf(false) }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    val minuteOptions = listOf(0, 15, 30, 45)
    val dayOptions = listOf(
        -1 to "每天",
        1 to "周一",
        2 to "周二",
        3 to "周三",
        4 to "周四",
        5 to "周五",
        6 to "周六",
        0 to "周日",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加定时唤醒",
                color = StradustTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                // 小时选择
                Text("小时", color = StradustTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                            .clickable { hourDropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${String.format("%02d", dialogHour)} 时",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 13.sp,
                        )
                    }
                    DropdownMenu(expanded = hourDropdownExpanded, onDismissRequest = { hourDropdownExpanded = false }) {
                        (0..23).forEach { h ->
                            DropdownMenuItem(
                                text = { Text("${String.format("%02d", h)} 时") },
                                onClick = { dialogHour = h; hourDropdownExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 分钟选择
                Text("分钟", color = StradustTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                            .clickable { minuteDropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${String.format("%02d", dialogMinute)} 分",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 13.sp,
                        )
                    }
                    DropdownMenu(expanded = minuteDropdownExpanded, onDismissRequest = { minuteDropdownExpanded = false }) {
                        minuteOptions.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${String.format("%02d", m)} 分") },
                                onClick = { dialogMinute = m; minuteDropdownExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 星期选择
                Text("重复", color = StradustTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(StradustTheme.colors.surfaceContainerLow, RoundedCornerShape(24.dp))
                            .clickable { dayDropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayOptions.find { it.first == dialogDayOfWeek }?.second ?: "每天",
                            color = StradustTheme.colors.textPrimary,
                            fontSize = 13.sp,
                        )
                    }
                    DropdownMenu(expanded = dayDropdownExpanded, onDismissRequest = { dayDropdownExpanded = false }) {
                        dayOptions.forEach { (d, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { dialogDayOfWeek = d; dayDropdownExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 消息输入
                Text("唤醒消息", color = StradustTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                StradustInput(
                    value = dialogMessage,
                    onValueChange = { dialogMessage = it },
                    hint = "早上好！今天想聊点什么？",
                )
                Spacer(Modifier.height(8.dp))
                // 启用开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用", color = StradustTheme.colors.textPrimary, fontSize = 13.sp)
                    Switch(
                        checked = dialogEnabled,
                        onCheckedChange = { dialogEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StradustTheme.colors.onPrimary,
                            checkedTrackColor = StradustTheme.colors.primary,
                            uncheckedThumbColor = StradustTheme.colors.surfaceContainerHigh,
                            uncheckedTrackColor = StradustTheme.colors.outlineVariant,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newWake = ScheduledWake(
                    id = System.currentTimeMillis(),
                    time = "${String.format("%02d", dialogHour)}:${String.format("%02d", dialogMinute)}",
                    message = dialogMessage,
                    enabled = dialogEnabled,
                    daysOfWeek = dialogDayOfWeek,
                )
                onConfirm(newWake)
            }) {
                Text("确认", color = StradustTheme.colors.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = StradustTheme.colors.textSecondary)
            }
        },
    )
}

/**
 * 检查指定服务类是否正在运行
 */
private fun isServiceRunning(context: android.content.Context, serviceClassName: String): Boolean {
    return try {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        am.getRunningServices(Int.MAX_VALUE)?.any { it.service.className == serviceClassName } == true
    } catch (_: Exception) {
        false
    }
}

/**
 * 服务状态行：圆点(绿/灰) + 服务名 + 状态文字
 */
@Composable
private fun ServiceStatusRow(name: String, running: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态圆点
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (running) StradustTheme.colors.primary else StradustTheme.colors.textMuted, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            color = StradustTheme.colors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (running) "运行中" else "已停止",
            color = if (running) StradustTheme.colors.primary else StradustTheme.colors.textMuted,
            fontSize = 12.sp,
        )
    }
}
