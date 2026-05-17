/** 设置页面: API配置/功能开关/偏好设置/模型管理入口 */
package com.aicompanion.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aicompanion.R
import com.aicompanion.settings.SettingsManager
import com.aicompanion.settings.LanguageStyle
import com.aicompanion.settings.NagFrequency
import com.aicompanion.diary.DiaryManager
import com.aicompanion.models.Emotion
import com.aicompanion.theme.ThemeManager
import com.aicompanion.wakeup.WakeUpScheduler
import com.aicompanion.virtualworld.VirtualWorldManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SettingsActivity : AppCompatActivity() {

    private var settingsManager: SettingsManager? = null

    // UI components
    private var btnPersonaEditor: com.google.android.material.button.MaterialButton? = null
    private var btnModelManager: com.google.android.material.button.MaterialButton? = null
    private var btnModelAdjust: com.google.android.material.button.MaterialButton? = null
    private var btnChangeTheme: com.google.android.material.button.MaterialButton? = null
    private var btnViewLog: com.google.android.material.button.MaterialButton? = null
    private var btnStartOverlay: com.google.android.material.button.MaterialButton? = null
    private var btnTestChatApi: com.google.android.material.button.MaterialButton? = null
    private var seekOverlaySize: SeekBar? = null
    private var tvOverlaySizeValue: TextView? = null
    private var radioNagFrequency: RadioGroup? = null
    private var radioLanguageStyle: RadioGroup? = null

    // API settings
    private var spinnerApiProvider: Spinner? = null
    private var tvApiProviderHint: TextView? = null
    private var etChatApiUrl: TextView? = null
    private var etChatApiKey: TextView? = null
    private var etChatModel: TextView? = null
    private var etScreenApiUrl: TextView? = null
    private var etScreenModel: TextView? = null
    private var etAsrApiUrl: TextView? = null
    private var etTtsApiUrl: TextView? = null
    private var etTtsModel: TextView? = null
    private var etUserId: TextView? = null

    // Switches
    private var switchScreenRecognition: Switch? = null
    private var switchSimpleScreenMode: Switch? = null
    private var switchVoiceRecognition: Switch? = null
    private var switchTts: Switch? = null
    private var switchOfflineMode: Switch? = null
    private var switchSearchEnabled: Switch? = null
    private var switchLive2d: Switch? = null

    // Wake up settings
    private var switchWakeEnabled: Switch? = null
    private var btnSetWakeTime: com.google.android.material.button.MaterialButton? = null
    private var btnSetWakeMessage: com.google.android.material.button.MaterialButton? = null
    private var tvWakeInfo: TextView? = null

    // Search config
    private var spinnerSearchProvider: Spinner? = null
    private var etSearchApiUrl: TextView? = null
    private var etSearchApiKey: TextView? = null
    private var etSearchEngineId: TextView? = null
    private var tilSearchApiUrl: View? = null
    private var tilSearchApiKey: View? = null
    private var tilSearchEngineId: View? = null

    // Spinner auto-fill guard
    private var isSpinnerInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        try {
            settingsManager = SettingsManager(this)

            initViews()
            setupSpinner()
            loadSettings()
            setupClickListeners()
            applyTheme()
        } catch (e: Exception) {
            Toast.makeText(this, "设置加载失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyTheme() {
        try {
            val scheme = ThemeManager.getCurrentScheme(this)
            val primaryColor = safeParseColor(scheme.primaryColor, 0xFF667eea.toInt())
            val accentColor = safeParseColor(scheme.accentColor, 0xFFaabbdd.toInt())
            val cardColor = safeParseColor(scheme.cardColor, 0xFF1a1a2e.toInt())
            val dangerColor = safeParseColor("#cc3344", 0xFFcc3344.toInt())

            window.statusBarColor = cardColor

            val colorMap = mapOf(
                R.id.btn_model_manager to primaryColor,
                R.id.btn_start_overlay to accentColor,
                R.id.btn_persona_editor to primaryColor,
                R.id.btn_delete_all_memories to dangerColor
            )

            val outlineColorMap = mapOf(
                R.id.btn_change_theme to primaryColor,
                R.id.btn_view_log to accentColor,
                R.id.btn_model_adjust to primaryColor,
                R.id.btn_set_wake_time to primaryColor,
                R.id.btn_set_wake_message to accentColor,
                R.id.btn_view_memories to primaryColor,
                R.id.btn_test_chat_api to primaryColor
            )

            fun applyBtnColor(id: Int, tintColor: Int, textColor: Int, outline: Boolean) {
                val btn = findViewById<View?>(id) ?: return
                try {
                    if (btn is com.google.android.material.button.MaterialButton) {
                        if (outline) {
                            btn.strokeColor = android.content.res.ColorStateList.valueOf(tintColor)
                            btn.setTextColor(textColor)
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                        } else {
                            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(tintColor)
                            btn.setTextColor(android.graphics.Color.WHITE)
                        }
                    } else if (btn is android.widget.Button) {
                        if (outline) {
                            (btn as android.widget.Button).setTextColor(textColor)
                        } else {
                            (btn as android.widget.Button).setBackgroundColor(tintColor)
                            (btn as android.widget.Button).setTextColor(android.graphics.Color.WHITE)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "applyBtnColor error for id=$id: ${e.message}")
                }
            }

            colorMap.forEach { (id, color) -> applyBtnColor(id, color, android.graphics.Color.WHITE, false) }
            outlineColorMap.forEach { (id, color) -> applyBtnColor(id, color, color, true) }

            ThemeManager.applyTheme(this)
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "applyTheme error: ${e.message}")
        }
    }

    private fun initViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Buttons
        btnPersonaEditor = findViewById<View?>(R.id.btn_persona_editor) as? com.google.android.material.button.MaterialButton
        btnModelManager = findViewById<View?>(R.id.btn_model_manager) as? com.google.android.material.button.MaterialButton
        btnModelAdjust = findViewById<View?>(R.id.btn_model_adjust) as? com.google.android.material.button.MaterialButton
        btnChangeTheme = findViewById<View?>(R.id.btn_change_theme) as? com.google.android.material.button.MaterialButton
        btnViewLog = findViewById<View?>(R.id.btn_view_log) as? com.google.android.material.button.MaterialButton
        btnStartOverlay = findViewById<View?>(R.id.btn_start_overlay) as? com.google.android.material.button.MaterialButton
        btnTestChatApi = findViewById<View?>(R.id.btn_test_chat_api) as? com.google.android.material.button.MaterialButton

        // SeekBar
        seekOverlaySize = findViewById(R.id.seek_overlay_size)
        tvOverlaySizeValue = findViewById(R.id.tv_overlay_size_value)

        // Radio groups
        radioNagFrequency = findViewById(R.id.radio_nag_frequency)
        radioLanguageStyle = findViewById(R.id.radio_language_style)

        // API settings
        spinnerApiProvider = findViewById(R.id.spinner_api_provider)
        tvApiProviderHint = findViewById(R.id.tv_api_provider_hint)
        etChatApiUrl = findViewById(R.id.et_chat_api_url)
        etChatApiKey = findViewById(R.id.et_chat_api_key)
        etChatModel = findViewById(R.id.et_chat_model)
        etScreenApiUrl = findViewById(R.id.et_screen_api_url)
        etScreenModel = findViewById(R.id.et_screen_model)
        etAsrApiUrl = findViewById(R.id.et_asr_api_url)
        etTtsApiUrl = findViewById(R.id.et_tts_api_url)
        etTtsModel = findViewById(R.id.et_tts_model)
        etUserId = findViewById(R.id.et_user_id)

        // Switches
        switchScreenRecognition = findViewById(R.id.switch_screen_recognition)
        switchSimpleScreenMode = findViewById(R.id.switch_simple_screen_mode)
        switchVoiceRecognition = findViewById(R.id.switch_voice_recognition)
        switchTts = findViewById(R.id.switch_tts)
        switchOfflineMode = findViewById(R.id.switch_offline_mode)
        switchLive2d = findViewById(R.id.switch_live2d)

        findViewById<Switch>(R.id.switch_safety_mode)?.isChecked = com.aicompanion.safety.ContentSafetyFilter.isEnabled(this)

        // Wake up settings
        switchWakeEnabled = findViewById(R.id.switch_wake_enabled)
        btnSetWakeTime = findViewById<View?>(R.id.btn_set_wake_time) as? com.google.android.material.button.MaterialButton
        btnSetWakeMessage = findViewById<View?>(R.id.btn_set_wake_message) as? com.google.android.material.button.MaterialButton
        tvWakeInfo = findViewById(R.id.tv_wake_info)

        // Search config
        switchSearchEnabled = findViewById(R.id.switch_search_enabled)
        spinnerSearchProvider = findViewById(R.id.spinner_search_provider)
        etSearchApiUrl = findViewById(R.id.et_search_api_url)
        etSearchApiKey = findViewById(R.id.et_search_api_key)
        etSearchEngineId = findViewById(R.id.et_search_engine_id)
        tilSearchApiUrl = findViewById(R.id.til_search_api_url)
        tilSearchApiKey = findViewById(R.id.til_search_api_key)
        tilSearchEngineId = findViewById(R.id.til_search_engine_id)

        setupOverlaySize()
        setupEndpointAutoComplete()
    }

    private fun loadSettings() {
        val sm = settingsManager ?: return

        // Load API settings
        etChatApiUrl?.text = sm.chatApiUrl
        etChatApiKey?.text = sm.chatApiKey
        etChatModel?.text = sm.chatModel
        etScreenApiUrl?.text = sm.screenApiUrl
        etScreenModel?.text = sm.screenModel
        etAsrApiUrl?.text = sm.asrApiUrl
        etTtsApiUrl?.text = sm.ttsApiUrl
        etTtsModel?.text = sm.ttsModel
        etUserId?.text = sm.userId

        switchScreenRecognition?.isChecked = sm.screenRecognitionEnabled
        switchSimpleScreenMode?.isChecked = sm.simpleScreenMode
        switchVoiceRecognition?.isChecked = sm.voiceRecognitionEnabled
        switchTts?.isChecked = sm.ttsEnabled
        switchOfflineMode?.isChecked = sm.offlineMode
        switchLive2d?.isChecked = sm.live2dEnabled

        // Radio groups
        radioNagFrequency?.check(
            when (sm.nagFrequency) {
                NagFrequency.OFF -> R.id.radio_off
                NagFrequency.LOW -> R.id.radio_low
                NagFrequency.MEDIUM -> R.id.radio_medium
                NagFrequency.HIGH -> R.id.radio_high
            }
        )

        radioLanguageStyle?.check(
            when (sm.languageStyle) {
                LanguageStyle.NORMAL -> R.id.radio_normal
                LanguageStyle.TSUNDERE -> R.id.radio_tsundere
                LanguageStyle.CUTE -> R.id.radio_cute
            }
        )

        // Load wake up settings
        switchWakeEnabled?.isChecked = WakeUpScheduler.isWakeupEnabled(this)
        updateWakeInfoDisplay()

        // Diary + background settings
        findViewById<Switch>(R.id.switch_auto_start)?.isChecked = sm.autoStart
        findViewById<Switch>(R.id.switch_background_running)?.isChecked = sm.backgroundRunning

        findViewById<RadioGroup>(R.id.radio_diary_trigger)?.check(
            when (sm.diaryTriggerMode) {
                com.aicompanion.settings.DiaryTriggerMode.MANUAL -> R.id.radio_diary_manual
                com.aicompanion.settings.DiaryTriggerMode.MESSAGES_50 -> R.id.radio_diary_50msg
                com.aicompanion.settings.DiaryTriggerMode.HOURLY -> R.id.radio_diary_hourly
                com.aicompanion.settings.DiaryTriggerMode.TWO_HOURS -> R.id.radio_diary_2h
                com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM -> R.id.radio_diary_10pm
            }
        )

        // Load search settings
        switchSearchEnabled?.isChecked = sm.searchEnabled
        setupSearchSpinner()
        etSearchApiUrl?.text = sm.searchApiUrl
        etSearchApiKey?.text = sm.searchApiKey
        etSearchEngineId?.text = sm.searchEngineId
        updateSearchFieldsVisibility()

        val vwManager = VirtualWorldManager(this)
        findViewById<Switch>(R.id.switch_virtual_world)?.isChecked = vwManager.isEnabled

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_api_url)?.setText(vwManager.imageApiUrl)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_api_key)?.setText(vwManager.imageApiKey)
        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_model)?.setText(vwManager.imageModel)
    }

    private fun updateWakeInfoDisplay() {
        val (hour, minute) = WakeUpScheduler.getWakeupTime(this)
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        tvWakeInfo?.text = if (switchWakeEnabled?.isChecked == true) {
            "已设定：每天 $timeStr 唤醒"
        } else {
            "未设置"
        }
    }

    private fun setupClickListeners() {
        (findViewById<View?>(R.id.donateBtn))?.setOnClickListener {
            showDonateDialog()
        }

        btnPersonaEditor?.setOnClickListener {
            try {
                val intent = Intent(this, PersonaEditorActivity::class.java)
                val personaId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getString("active_persona_id", "default") ?: "default"
                intent.putExtra("persona_id", personaId)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开角色设定: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnModelManager?.setOnClickListener {
            try {
                startActivity(Intent(this, ModelManagerActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开模型管理: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnModelAdjust?.setOnClickListener {
            try {
                startActivity(Intent(this, ModelAdjustActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开模型调整: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_local_model)?.setOnClickListener {
            try {
                startActivity(Intent(this, LocalModelActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开本地模型: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnChangeTheme?.setOnClickListener {
            showThemePicker()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_bubble_skin)?.setOnClickListener {
            try {
                startActivity(Intent(this, SkinShopActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开皮肤商店", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_ai_frame)?.setOnClickListener {
            try {
                val intent = Intent(this, SkinShopActivity::class.java)
                intent.putExtra("tab", 1)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开皮肤商店", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_user_frame)?.setOnClickListener {
            try {
                val intent = Intent(this, SkinShopActivity::class.java)
                intent.putExtra("tab", 2)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开皮肤商店", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewLog?.setOnClickListener {
            showLive2DLog()
        }

        btnStartOverlay?.setOnClickListener {
            try {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                    return@setOnClickListener
                }
                val serviceIntent = Intent(this, com.aicompanion.services.OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "悬浮窗服务已启动", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnTestChatApi?.setOnClickListener {
            testChatApi()
        }

        switchWakeEnabled?.setOnCheckedChangeListener { _, _ ->
            startActivity(Intent(this, com.aicompanion.wakeup.WakeUpActivity::class.java))
            updateWakeInfoDisplay()
        }

        btnSetWakeTime?.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.wakeup.WakeUpActivity::class.java))
        }

        btnSetWakeMessage?.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.wakeup.WakeUpActivity::class.java))
        }

        findViewById<View?>(R.id.tvBilibiliLink)?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://space.bilibili.com/1523985433"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开链接，请手动搜索B站UID: 1523985433", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<View?>(R.id.tvDouyinLink)?.setOnClickListener {
            val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = android.content.ClipData.newPlainText("抖音ID", "31991565756")
            clip.setPrimaryClip(clipData)
            Toast.makeText(this, "抖音ID已复制到剪贴板：31991565756", Toast.LENGTH_LONG).show()
        }

        findViewById<Switch>(R.id.switch_safety_mode)?.setOnCheckedChangeListener { _, isChecked ->
            com.aicompanion.safety.ContentSafetyFilter.setEnabled(this, isChecked)
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_clear_chat_history)?.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("清空聊天记录")
                .setMessage("确定要清空当前角色的所有聊天记录吗？此操作不可撤销。")
                .setPositiveButton("清空") { _, _ ->
                    val personaId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getString("active_persona_id", "default") ?: "default"
                    getSharedPreferences("chat_history_$personaId", MODE_PRIVATE).edit().clear().apply()
                    Toast.makeText(this, "聊天记录已清空", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        findViewById<Switch>(R.id.switch_auto_start)?.setOnCheckedChangeListener { _, isChecked ->
            settingsManager?.autoStart = isChecked
        }

        findViewById<Switch>(R.id.switch_background_running)?.setOnCheckedChangeListener { _, isChecked ->
            settingsManager?.backgroundRunning = isChecked
        }

        findViewById<Switch>(R.id.switch_virtual_world)?.setOnCheckedChangeListener { _, isChecked ->
            val vwMgr = VirtualWorldManager(this)
            if (isChecked) {
                if (!vwMgr.hasChatModelConfigured()) {
                    Toast.makeText(this, "请先配置聊天API才能启用虚拟世界", Toast.LENGTH_LONG).show()
                    findViewById<Switch>(R.id.switch_virtual_world)?.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            vwMgr.isEnabled = isChecked
            if (!isChecked) {
                vwMgr.isRunning = false
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_virtual_world)?.setOnClickListener {
            val vwMgr = VirtualWorldManager(this)
            if (!vwMgr.isEnabled) {
                Toast.makeText(this, "请先开启虚拟世界开关", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!vwMgr.hasChatModelConfigured()) {
                Toast.makeText(this, "请先配置聊天API", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                startActivity(Intent(this, VirtualWorldActivity::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "无法打开虚拟世界: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showThemePicker() {
        val schemes = com.aicompanion.theme.ThemeManager.schemes
        val names = schemes.map { it.name }.toTypedArray()
        val currentId = com.aicompanion.theme.ThemeManager.getCurrentScheme(this).id
        val currentIndex = schemes.indexOfFirst { it.id == currentId }

        android.app.AlertDialog.Builder(this)
            .setTitle("选择主题色调")
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val selectedScheme = schemes[which]
                com.aicompanion.theme.ThemeManager.setScheme(this, selectedScheme.id)
                dialog.dismiss()
                applyTheme()
                Toast.makeText(this, "已切换到 ${selectedScheme.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showBubbleSkinDialog() {
        val skins = com.aicompanion.theme.BubbleSkinManager.builtinSkins
        val currentSkin = com.aicompanion.theme.BubbleSkinManager.getActiveSkin(this)
        val items = skins.map { it.name }.toTypedArray()
        val currentIndex = skins.indexOfFirst { it.id == currentSkin.id }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("聊天气泡皮肤")
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                com.aicompanion.theme.BubbleSkinManager.setActiveSkin(this, skins[which].id)
                dialog.dismiss()
                Toast.makeText(this, "气泡皮肤已切换为「${skins[which].name}」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAvatarFrameDialog(isAi: Boolean) {
        val frames = com.aicompanion.theme.BubbleSkinManager.builtinFrames
        val currentFrame = if (isAi) com.aicompanion.theme.BubbleSkinManager.getActiveAiFrame(this) else com.aicompanion.theme.BubbleSkinManager.getActiveUserFrame(this)
        val items = frames.map { it.name }.toTypedArray()
        val currentIndex = frames.indexOfFirst { it.id == currentFrame.id }.coerceAtLeast(0)
        val title = if (isAi) "AI头像框" else "我的头像框"
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                if (isAi) com.aicompanion.theme.BubbleSkinManager.setActiveAiFrame(this, frames[which].id)
                else com.aicompanion.theme.BubbleSkinManager.setActiveUserFrame(this, frames[which].id)
                dialog.dismiss()
                Toast.makeText(this, "头像框已切换为「${frames[which].name}」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLive2DLog() {
        val log = "Live2D 日志请在主界面的设置按钮中查看"
        android.app.AlertDialog.Builder(this)
            .setTitle("Live2D Debug Log")
            .setMessage(log)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun testChatApi() {
        val url = etChatApiUrl?.text?.toString()?.trim() ?: ""
        val key = etChatApiKey?.text?.toString()?.trim() ?: ""
        val model = etChatModel?.text?.toString()?.trim() ?: ""
        if (url.isEmpty()) {
            Toast.makeText(this, "请先选择API厂商或填写API地址", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在测试连接...", Toast.LENGTH_SHORT).show()

        val testClient = com.aicompanion.network.ApiClient(url, key, model)
        testClient.testConnection { _, message ->
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSpinner() {
        val providers = arrayOf("自定义", "OpenAI", "阿里云百炼", "智谱AI", "MiniMax", "月之暗面", "n1n", "DeepSeek", "硅基流动", "OpenRouter", "通义千问")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerApiProvider?.adapter = adapter

        val savedProvider = settingsManager?.apiProvider ?: "custom"
        val savedIndex = when (savedProvider) {
            "openai" -> 1; "aliyun" -> 2; "zhipu" -> 3; "minimax" -> 4
            "moonshot" -> 5; "n1n" -> 6; "deepseek" -> 7; "siliconflow" -> 8; "openrouter" -> 9
            "qwen" -> 10
            else -> 0
        }
        spinnerApiProvider?.setSelection(savedIndex)

        spinnerApiProvider?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val (providerId, url, model) = when (position) {
                    1 -> Triple("openai", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini")
                    2 -> Triple("aliyun", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus")
                    3 -> Triple("zhipu", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash")
                    4 -> Triple("minimax", "https://api.minimax.chat/v1/text/chatcompletion_v2", "MiniMax-Text-01")
                    5 -> Triple("moonshot", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k")
                    6 -> Triple("n1n", "https://api.n1n.ai/v1/chat/completions", "gpt-4o-mini")
                    7 -> Triple("deepseek", "https://api.deepseek.com/v1/chat/completions", "deepseek-v4-flash")
                    8 -> Triple("siliconflow", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2.5-7B-Instruct")
                    9 -> Triple("openrouter", "https://openrouter.ai/api/v1/chat/completions", "google/gemini-2.0-flash-001")
                    10 -> Triple("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-max")
                    else -> Triple("custom", "", "")
                }
                if (isSpinnerInitialized && url.isNotEmpty()) {
                    etChatApiUrl?.setText(url)
                    // Only auto-fill model if user hasn't customized it:
                    // model is empty OR matches a known default from another provider
                    val currentModel = etChatModel?.text?.toString()?.trim() ?: ""
                    val knownDefaults = setOf(
                        "gpt-4o-mini", "qwen-plus", "qwen-max", "glm-4-flash",
                        "MiniMax-Text-01", "moonshot-v1-8k", "deepseek-chat", "deepseek-v4-flash",
                        "Qwen/Qwen2.5-7B-Instruct", "google/gemini-2.0-flash-001"
                    )
                    if (currentModel.isEmpty() || currentModel in knownDefaults) {
                        etChatModel?.setText(model)
                    }
                    tvApiProviderHint?.text = "已自动填充 ${providers[position]} 配置"
                    tvApiProviderHint?.visibility = android.view.View.VISIBLE
                }
                settingsManager?.apiProvider = providerId
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Use post to ensure any deferred selection callbacks fire BEFORE we enable auto-fill.
        // This prevents the spinner from overwriting saved model/URL on initial setup.
        spinnerApiProvider?.post {
            isSpinnerInitialized = true
        }
    }

    private fun setupEndpointAutoComplete() {
        etChatApiUrl?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                autoCompleteEndpoint()
            }
        }
    }

    private fun autoCompleteEndpoint() {
        val url = etChatApiUrl?.text?.toString()?.trim() ?: return
        if (url.isEmpty()) return

        if (url.contains("/chat/completions")) return
        if (url.contains("/text/chatcompletion_v2")) return

        val completions = mapOf(
            "/v1" to "/chat/completions",
            "/v1/" to "chat/completions",
            "/v4" to "/chat/completions",
            "/v4/" to "chat/completions",
            "/compatible-mode/v1" to "/chat/completions",
            "/compatible-mode/v1/" to "chat/completions",
        )

        for ((base, suffix) in completions) {
            if (url.endsWith(base)) {
                val completed = url.removeSuffix(base) + base.trimEnd('/') + "/" + suffix.trimStart('/')
                etChatApiUrl?.setText(completed)
                (etChatApiUrl as? android.widget.EditText)?.setSelection(completed.length)
                Toast.makeText(this, "已自动补全端点: $suffix", Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    private fun setupOverlaySize() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val sizePercent = prefs.getInt("overlay_size_percent", 100)

        seekOverlaySize?.progress = sizePercent
        tvOverlaySizeValue?.text = "${sizePercent}%"

        seekOverlaySize?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOverlaySizeValue?.text = "${progress}%"
                prefs.edit().putInt("overlay_size_percent", progress).apply()
                com.aicompanion.overlay.OverlayWindow.notifySizeChanged()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:$packageName")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        switchWakeEnabled?.isChecked = WakeUpScheduler.isWakeupEnabled(this)
        updateWakeInfoDisplay()
        applyTheme()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (android.provider.Settings.canDrawOverlays(this)) {
                try {
                    val serviceIntent = Intent(this, com.aicompanion.services.OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    Toast.makeText(this, "悬浮窗服务已启动", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            saveSettings()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSettings() {
        val sm = settingsManager ?: return

        sm.chatApiUrl = etChatApiUrl?.text?.toString() ?: ""
        sm.chatApiKey = etChatApiKey?.text?.toString() ?: ""
        sm.chatModel = etChatModel?.text?.toString() ?: "gpt-4o-mini"
        sm.screenApiUrl = etScreenApiUrl?.text?.toString() ?: ""
        sm.screenModel = etScreenModel?.text?.toString() ?: "gpt-4o"
        sm.asrApiUrl = etAsrApiUrl?.text?.toString() ?: ""
        sm.ttsApiUrl = etTtsApiUrl?.text?.toString() ?: ""
        sm.ttsModel = etTtsModel?.text?.toString() ?: "tts-1"
        sm.userId = etUserId?.text?.toString() ?: sm.userId

        sm.screenRecognitionEnabled = switchScreenRecognition?.isChecked ?: false
        sm.simpleScreenMode = switchSimpleScreenMode?.isChecked ?: false
        sm.voiceRecognitionEnabled = switchVoiceRecognition?.isChecked ?: false
        sm.ttsEnabled = switchTts?.isChecked ?: false
        sm.offlineMode = switchOfflineMode?.isChecked ?: false
        sm.live2dEnabled = switchLive2d?.isChecked ?: true

        sm.nagFrequency = when (radioNagFrequency?.checkedRadioButtonId) {
            R.id.radio_low -> NagFrequency.LOW
            R.id.radio_medium -> NagFrequency.MEDIUM
            R.id.radio_high -> NagFrequency.HIGH
            else -> NagFrequency.OFF
        }

        sm.languageStyle = when (radioLanguageStyle?.checkedRadioButtonId) {
            R.id.radio_tsundere -> LanguageStyle.TSUNDERE
            R.id.radio_cute -> LanguageStyle.CUTE
            else -> LanguageStyle.NORMAL
        }

        sm.autoStart = findViewById<Switch>(R.id.switch_auto_start)?.isChecked ?: true
        sm.backgroundRunning = findViewById<Switch>(R.id.switch_background_running)?.isChecked ?: true

        sm.diaryTriggerMode = when (findViewById<RadioGroup>(R.id.radio_diary_trigger)?.checkedRadioButtonId) {
            R.id.radio_diary_manual -> com.aicompanion.settings.DiaryTriggerMode.MANUAL
            R.id.radio_diary_hourly -> com.aicompanion.settings.DiaryTriggerMode.HOURLY
            R.id.radio_diary_2h -> com.aicompanion.settings.DiaryTriggerMode.TWO_HOURS
            R.id.radio_diary_10pm -> com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM
            else -> com.aicompanion.settings.DiaryTriggerMode.MESSAGES_50
        }

        sm.searchEnabled = switchSearchEnabled?.isChecked ?: true
        sm.searchApiUrl = etSearchApiUrl?.text?.toString() ?: ""
        sm.searchApiKey = etSearchApiKey?.text?.toString() ?: ""
        sm.searchEngineId = etSearchEngineId?.text?.toString() ?: ""

        val vwMgr = VirtualWorldManager(this)
        vwMgr.imageApiUrl = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_api_url)?.text?.toString() ?: ""
        vwMgr.imageApiKey = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_api_key)?.text?.toString() ?: ""
        vwMgr.imageModel = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_model)?.text?.toString() ?: "dall-e-3"

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun setupSearchSpinner() {
        val providers = arrayOf("DuckDuckGo (免费)", "必应搜索 API", "百度搜索")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSearchProvider?.adapter = adapter

        val savedProvider = settingsManager?.searchProvider ?: "duckduckgo"
        val savedIndex = when (savedProvider) {
            "bing" -> 1
            "baidu" -> 2
            else -> 0
        }
        spinnerSearchProvider?.setSelection(savedIndex)

        spinnerSearchProvider?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val providerId = when (position) {
                    1 -> "bing"
                    2 -> "baidu"
                    else -> "duckduckgo"
                }
                settingsManager?.searchProvider = providerId
                updateSearchFieldsVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        switchSearchEnabled?.setOnCheckedChangeListener { _, _ -> updateSearchFieldsVisibility() }
    }

    private fun updateSearchFieldsVisibility() {
        val enabled = switchSearchEnabled?.isChecked ?: true
        val provider = settingsManager?.searchProvider ?: "duckduckgo"

        spinnerSearchProvider?.visibility = if (enabled) View.VISIBLE else View.GONE
        tilSearchApiUrl?.visibility = if (enabled && provider == "bing") View.VISIBLE else View.GONE
        tilSearchApiKey?.visibility = if (enabled && provider == "bing") View.VISIBLE else View.GONE
        tilSearchEngineId?.visibility = View.GONE
    }

    private fun showDonateDialog() {
        try {
            val imageView = android.widget.ImageView(this)
            val inputStream = assets.open("donate_qrcode.png")
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val padding = (16 * resources.displayMetrics.density).toInt()
            imageView.setPadding(padding, padding, padding, padding)

            android.app.AlertDialog.Builder(this)
                .setTitle("感谢你的支持 💛")
                .setView(imageView)
                .setPositiveButton("已扫码，感谢!", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "加载二维码失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun safeParseColor(colorStr: String?, default: Int): Int {
        if (colorStr.isNullOrEmpty()) return default
        return try {
            android.graphics.Color.parseColor(colorStr)
        } catch (_: Exception) {
            default
        }
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 2001
    }
}
