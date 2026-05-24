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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.settings.SettingsManager
import com.aicompanion.settings.LanguageStyle
import com.aicompanion.settings.NagFrequency
import com.aicompanion.settings.ProviderProfile
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
    private lateinit var recyclerView: RecyclerView
    private lateinit var settingsAdapter: SettingsAdapter
    private val loadedItemTypes = mutableSetOf<Int>()

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

    private var switchScreenRecognition: Switch? = null
    private var switchSimpleScreenMode: Switch? = null
    private var switchVoiceRecognition: Switch? = null
    private var switchTts: Switch? = null
    private var spinnerTtsEngine: Spinner? = null
    private var spinnerEdgeVoice: Spinner? = null
    private var layoutEdgeVoice: View? = null
    private var layoutCloudTts: View? = null
    private var switchOfflineMode: Switch? = null
    private var btnWechatBind: com.google.android.material.button.MaterialButton? = null
    private var tvWechatStatus: TextView? = null
    private var switchSearchEnabled: Switch? = null
    private var switchLive2d: Switch? = null

    private var switchWakeEnabled: Switch? = null
    private var btnSetWakeTime: com.google.android.material.button.MaterialButton? = null
    private var btnSetWakeMessage: com.google.android.material.button.MaterialButton? = null
    private var tvWakeInfo: TextView? = null

    private var spinnerSearchProvider: Spinner? = null
    private var etSearchApiUrl: TextView? = null
    private var etSearchApiKey: TextView? = null
    private var etSearchEngineId: TextView? = null
    private var tilSearchApiUrl: View? = null
    private var tilSearchApiKey: View? = null
    private var tilSearchEngineId: View? = null

    private var isSpinnerInitialized = false

    private var seekTemp: SeekBar? = null
    private var tvTemp: TextView? = null
    private var seekTopP: SeekBar? = null
    private var tvTopP: TextView? = null
    private var seekFreqP: SeekBar? = null
    private var tvFreqP: TextView? = null
    private var seekPresP: SeekBar? = null
    private var tvPresP: TextView? = null
    private var seekMaxTok: SeekBar? = null
    private var etMaxTok: android.widget.EditText? = null
    private var layoutFreqP: View? = null
    private var layoutPresP: View? = null
    private var tvProviderHint: TextView? = null
    private var tvMaxTokLimit: TextView? = null

    private var switchSafetyMode: Switch? = null
    private var switchAutoStart: Switch? = null
    private var switchBackgroundRunning: Switch? = null
    private var switchVirtualWorld: Switch? = null
    private var switchEmotionAnalysis: Switch? = null
    private var radioDiaryTrigger: RadioGroup? = null
    private var btnLocalModel: com.google.android.material.button.MaterialButton? = null
    private var btnBubbleSkin: com.google.android.material.button.MaterialButton? = null
    private var btnAiFrame: com.google.android.material.button.MaterialButton? = null
    private var btnUserFrame: com.google.android.material.button.MaterialButton? = null
    private var btnClearChatHistory: com.google.android.material.button.MaterialButton? = null
    private var btnVirtualWorld: com.google.android.material.button.MaterialButton? = null
    private var etImageApiUrl: com.google.android.material.textfield.TextInputEditText? = null
    private var etImageApiKey: com.google.android.material.textfield.TextInputEditText? = null
    private var etImageModel: com.google.android.material.textfield.TextInputEditText? = null

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> findSettingsView(id: Int): T? {
        val rv = recyclerView
        for (i in 0 until rv.adapter!!.itemCount) {
            val holder = rv.findViewHolderForAdapterPosition(i)
            holder?.itemView?.findViewById<T>(id)?.let { return it }
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        try {
            settingsManager = SettingsManager(this)

            setupRecyclerView()

            recyclerView.post {
                initViews()
                loadSettings()
                setupClickListeners()
                applyTheme()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "设置加载失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_settings)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER

        val items = listOf(
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_APPEARANCE),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_SEARCH),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_LLM),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_SCREEN),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_ASR),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_TTS),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_USER),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_DIARY),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_AI_FEATURES),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_SAFETY),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_MEMORY),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_STYLE),
            SettingsAdapter.SettingsItem(SettingsAdapter.TYPE_FOOTER),
        )

        settingsAdapter = SettingsAdapter(items) { view, type ->
            onItemBound(view, type)
        }
        recyclerView.adapter = settingsAdapter
        recyclerView.setItemViewCacheSize(13)

        recyclerView.setHasFixedSize(true)
    }

    private fun onItemBound(view: View, type: Int) {
        if (type == SettingsAdapter.TYPE_FOOTER) {
            setupFooterClickListeners(view)
            return
        }

        updateFieldReferences(view, type)

        if (type !in loadedItemTypes) {
            loadedItemTypes.add(type)
            loadSettingsForType(view, type)
            setupListenersForType(view, type)
        }
    }

    private fun updateFieldReferences(view: View, type: Int) {
        when (type) {
            SettingsAdapter.TYPE_APPEARANCE -> {
                btnChangeTheme = view.findViewById(R.id.btn_change_theme)
                btnViewLog = view.findViewById(R.id.btn_view_log)
                btnModelManager = view.findViewById(R.id.btn_model_manager)
                btnModelAdjust = view.findViewById(R.id.btn_model_adjust)
                btnLocalModel = view.findViewById(R.id.btn_local_model)
                switchLive2d = view.findViewById(R.id.switch_live2d)
                btnBubbleSkin = view.findViewById(R.id.btn_bubble_skin)
                btnAiFrame = view.findViewById(R.id.btn_ai_frame)
                btnUserFrame = view.findViewById(R.id.btn_user_frame)
                btnStartOverlay = view.findViewById(R.id.btn_start_overlay)
                seekOverlaySize = view.findViewById(R.id.seek_overlay_size)
                tvOverlaySizeValue = view.findViewById(R.id.tv_overlay_size_value)
            }
            SettingsAdapter.TYPE_SEARCH -> {
                switchSearchEnabled = view.findViewById(R.id.switch_search_enabled)
                spinnerSearchProvider = view.findViewById(R.id.spinner_search_provider)
                etSearchApiUrl = view.findViewById(R.id.et_search_api_url)
                etSearchApiKey = view.findViewById(R.id.et_search_api_key)
                etSearchEngineId = view.findViewById(R.id.et_search_engine_id)
                tilSearchApiUrl = view.findViewById(R.id.til_search_api_url)
                tilSearchApiKey = view.findViewById(R.id.til_search_api_key)
                tilSearchEngineId = view.findViewById(R.id.til_search_engine_id)
            }
            SettingsAdapter.TYPE_LLM -> {
                spinnerApiProvider = view.findViewById(R.id.spinner_api_provider)
                tvApiProviderHint = view.findViewById(R.id.tv_api_provider_hint)
                etChatApiUrl = view.findViewById(R.id.et_chat_api_url)
                etChatApiKey = view.findViewById(R.id.et_chat_api_key)
                etChatModel = view.findViewById(R.id.et_chat_model)
                btnTestChatApi = view.findViewById(R.id.btn_test_chat_api)
                seekTemp = view.findViewById(R.id.seek_temperature)
                tvTemp = view.findViewById(R.id.tv_temperature_value)
                seekTopP = view.findViewById(R.id.seek_top_p)
                tvTopP = view.findViewById(R.id.tv_top_p_value)
                seekFreqP = view.findViewById(R.id.seek_freq_penalty)
                tvFreqP = view.findViewById(R.id.tv_freq_penalty_value)
                seekPresP = view.findViewById(R.id.seek_presence_penalty)
                tvPresP = view.findViewById(R.id.tv_presence_penalty_value)
                seekMaxTok = view.findViewById(R.id.seek_max_tokens)
                etMaxTok = view.findViewById(R.id.et_max_tokens)
                layoutFreqP = view.findViewById(R.id.layout_freq_penalty)
                layoutPresP = view.findViewById(R.id.layout_presence_penalty)
                tvProviderHint = view.findViewById(R.id.tv_provider_param_hint)
                tvMaxTokLimit = view.findViewById(R.id.tv_max_tokens_limit_hint)
            }
            SettingsAdapter.TYPE_SCREEN -> {
                switchScreenRecognition = view.findViewById(R.id.switch_screen_recognition)
                etScreenApiUrl = view.findViewById(R.id.et_screen_api_url)
                etScreenModel = view.findViewById(R.id.et_screen_model)
                switchSimpleScreenMode = view.findViewById(R.id.switch_simple_screen_mode)
            }
            SettingsAdapter.TYPE_ASR -> {
                switchVoiceRecognition = view.findViewById(R.id.switch_voice_recognition)
                etAsrApiUrl = view.findViewById(R.id.et_asr_api_url)
            }
            SettingsAdapter.TYPE_TTS -> {
                switchTts = view.findViewById(R.id.switch_tts)
                etTtsApiUrl = view.findViewById(R.id.et_tts_api_url)
                etTtsModel = view.findViewById(R.id.et_tts_model)
                switchEmotionAnalysis = view.findViewById(R.id.switch_emotion_analysis)
                spinnerTtsEngine = view.findViewById(R.id.spinner_tts_engine)
                spinnerEdgeVoice = view.findViewById(R.id.spinner_edge_voice)
                layoutEdgeVoice = view.findViewById(R.id.layout_edge_voice)
                layoutCloudTts = view.findViewById(R.id.layout_cloud_tts)
            }
            SettingsAdapter.TYPE_USER -> {
                etUserId = view.findViewById(R.id.et_user_id)
                switchOfflineMode = view.findViewById(R.id.switch_offline_mode)
                btnWechatBind = view.findViewById(R.id.btn_wechat_bind)
                tvWechatStatus = view.findViewById(R.id.tv_wechat_status)
            }
            SettingsAdapter.TYPE_DIARY -> {
                switchAutoStart = view.findViewById(R.id.switch_auto_start)
                switchBackgroundRunning = view.findViewById(R.id.switch_background_running)
                radioDiaryTrigger = view.findViewById(R.id.radio_diary_trigger)
            }
            SettingsAdapter.TYPE_AI_FEATURES -> {
                switchWakeEnabled = view.findViewById(R.id.switch_wake_enabled)
                btnSetWakeTime = view.findViewById(R.id.btn_set_wake_time)
                btnSetWakeMessage = view.findViewById(R.id.btn_set_wake_message)
                tvWakeInfo = view.findViewById(R.id.tv_wake_info)
                switchVirtualWorld = view.findViewById(R.id.switch_virtual_world)
                btnVirtualWorld = view.findViewById(R.id.btn_virtual_world)
                etImageApiUrl = view.findViewById(R.id.et_image_api_url)
                etImageApiKey = view.findViewById(R.id.et_image_api_key)
                etImageModel = view.findViewById(R.id.et_image_model)
            }
            SettingsAdapter.TYPE_SAFETY -> {
                switchSafetyMode = view.findViewById(R.id.switch_safety_mode)
            }
            SettingsAdapter.TYPE_MEMORY -> {
                btnPersonaEditor = view.findViewById(R.id.btn_persona_editor)
                btnClearChatHistory = view.findViewById(R.id.btn_clear_chat_history)
            }
            SettingsAdapter.TYPE_STYLE -> {
                radioNagFrequency = view.findViewById(R.id.radio_nag_frequency)
                radioLanguageStyle = view.findViewById(R.id.radio_language_style)
            }
        }
    }

    private fun loadSettingsForType(view: View, type: Int) {
        val sm = settingsManager ?: return
        when (type) {
            SettingsAdapter.TYPE_APPEARANCE -> {
                switchLive2d?.isChecked = sm.live2dEnabled
                setupOverlaySize()
            }
            SettingsAdapter.TYPE_SEARCH -> {
                switchSearchEnabled?.isChecked = sm.searchEnabled
                setupSearchSpinner()
                etSearchApiUrl?.text = sm.searchApiUrl
                etSearchApiKey?.text = sm.searchApiKey
                etSearchEngineId?.text = sm.searchEngineId
                updateSearchFieldsVisibility()
            }
            SettingsAdapter.TYPE_LLM -> {
                setupSpinner()
                etChatApiUrl?.text = sm.chatApiUrl
                etChatApiKey?.text = sm.chatApiKey
                etChatModel?.text = sm.chatModel
                setupLlmParams()
            }
            SettingsAdapter.TYPE_SCREEN -> {
                switchScreenRecognition?.isChecked = sm.screenRecognitionEnabled
                etScreenApiUrl?.text = sm.screenApiUrl
                etScreenModel?.text = sm.screenModel
                switchSimpleScreenMode?.isChecked = sm.simpleScreenMode
            }
            SettingsAdapter.TYPE_ASR -> {
                switchVoiceRecognition?.isChecked = sm.voiceRecognitionEnabled
                etAsrApiUrl?.text = sm.asrApiUrl
            }
            SettingsAdapter.TYPE_TTS -> {
                switchTts?.isChecked = sm.ttsEnabled
                etTtsApiUrl?.text = sm.ttsApiUrl
                etTtsModel?.text = sm.ttsModel
                setupTtsEngine(sm)
                setupTtsParams()
                switchEmotionAnalysis?.isChecked = sm.emotionAnalysisEnabled
            }
            SettingsAdapter.TYPE_USER -> {
                etUserId?.text = sm.userId
                switchOfflineMode?.isChecked = sm.offlineMode
                updateWechatStatus()
            }
            SettingsAdapter.TYPE_DIARY -> {
                switchAutoStart?.isChecked = sm.autoStart
                switchBackgroundRunning?.isChecked = sm.backgroundRunning
                radioDiaryTrigger?.check(
                    when (sm.diaryTriggerMode) {
                        com.aicompanion.settings.DiaryTriggerMode.MANUAL -> R.id.radio_diary_manual
                        com.aicompanion.settings.DiaryTriggerMode.MSG_50 -> R.id.radio_diary_50msg
                        com.aicompanion.settings.DiaryTriggerMode.HOURLY -> R.id.radio_diary_hourly
                        com.aicompanion.settings.DiaryTriggerMode.EVERY_2H -> R.id.radio_diary_2h
                        com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM -> R.id.radio_diary_10pm
                    }
                )
            }
            SettingsAdapter.TYPE_AI_FEATURES -> {
                switchWakeEnabled?.isChecked = WakeUpScheduler.isWakeupEnabled(this)
                updateWakeInfoDisplay()
                val vwManager = VirtualWorldManager(this)
                switchVirtualWorld?.isChecked = vwManager.isEnabled
                etImageApiUrl?.setText(vwManager.imageApiUrl)
                etImageApiKey?.setText(vwManager.imageApiKey)
                etImageModel?.setText(vwManager.imageModel)
            }
            SettingsAdapter.TYPE_SAFETY -> {
                switchSafetyMode?.isChecked = com.aicompanion.safety.ContentSafetyFilter.isEnabled(this)
            }
            SettingsAdapter.TYPE_MEMORY -> {
                // No settings to load for memory buttons
            }
            SettingsAdapter.TYPE_STYLE -> {
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
            }
        }
    }

    private fun setupListenersForType(view: View, type: Int) {
        when (type) {
            SettingsAdapter.TYPE_APPEARANCE -> {
                btnChangeTheme?.setOnClickListener { showThemePicker() }
                btnViewLog?.setOnClickListener { showLive2DLog() }
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
                btnLocalModel?.setOnClickListener {
                    try {
                        startActivity(Intent(this, LocalModelActivity::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "无法打开本地模型: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                btnBubbleSkin?.setOnClickListener {
                    try {
                        startActivity(Intent(this, SkinShopActivity::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "无法打开皮肤商店", Toast.LENGTH_SHORT).show()
                    }
                }
                btnAiFrame?.setOnClickListener {
                    try {
                        val intent = Intent(this, SkinShopActivity::class.java)
                        intent.putExtra("tab", 1)
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "无法打开皮肤商店", Toast.LENGTH_SHORT).show()
                    }
                }
                btnUserFrame?.setOnClickListener {
                    try {
                        val intent = Intent(this, SkinShopActivity::class.java)
                        intent.putExtra("tab", 2)
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "无法打开皮肤商店", Toast.LENGTH_SHORT).show()
                    }
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
            }
            SettingsAdapter.TYPE_LLM -> {
                btnTestChatApi?.setOnClickListener { testChatApi() }
                setupEndpointAutoComplete()
            }
            SettingsAdapter.TYPE_AI_FEATURES -> {
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
                switchVirtualWorld?.setOnCheckedChangeListener { _, isChecked ->
                    val vwMgr = VirtualWorldManager(this)
                    if (isChecked) {
                        if (!vwMgr.hasChatModelConfigured()) {
                            Toast.makeText(this, "请先配置聊天API才能启用虚拟世界", Toast.LENGTH_LONG).show()
                            switchVirtualWorld?.isChecked = false
                            return@setOnCheckedChangeListener
                        }
                    }
                    vwMgr.isEnabled = isChecked
                    if (!isChecked) {
                        vwMgr.isRunning = false
                    }
                }
                btnVirtualWorld?.setOnClickListener {
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
            SettingsAdapter.TYPE_SAFETY -> {
                switchSafetyMode?.setOnCheckedChangeListener { _, isChecked ->
                    com.aicompanion.safety.ContentSafetyFilter.setEnabled(this, isChecked)
                }
            }
            SettingsAdapter.TYPE_USER -> {
                btnWechatBind?.setOnClickListener {
                    try {
                        startActivity(Intent(this, com.aicompanion.ilink.WechatBindActivity::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, "无法打开微信绑定: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            SettingsAdapter.TYPE_DIARY -> {
                switchAutoStart?.setOnCheckedChangeListener { _, isChecked ->
                    settingsManager?.autoStart = isChecked
                }
                switchBackgroundRunning?.setOnCheckedChangeListener { _, isChecked ->
                    settingsManager?.backgroundRunning = isChecked
                }
            }
            SettingsAdapter.TYPE_MEMORY -> {
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
                btnClearChatHistory?.setOnClickListener {
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
            }
        }
    }

    private fun updateWechatStatus() {
        val authManager = com.aicompanion.ilink.IlinkAuthManager(this)
        if (authManager.isBound) {
            tvWechatStatus?.text = "已绑定 ✓"
            tvWechatStatus?.setTextColor(0xFF07c160.toInt())
            btnWechatBind?.text = "管理微信"
        } else {
            tvWechatStatus?.text = "未绑定"
            tvWechatStatus?.setTextColor(0xFF667788.toInt())
            btnWechatBind?.text = "绑定微信"
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
                val btn = findSettingsView<View>(id) ?: return
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

        for (i in 0 until settingsAdapter.itemCount) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i)
            if (holder != null) {
                val type = settingsAdapter.items[i].type
                updateFieldReferences(holder.itemView, type)
            }
        }
    }

    private fun setupFooterClickListeners(footerView: View) {
        footerView.findViewById<View?>(R.id.donateBtn)?.setOnClickListener {
            showDonateDialog()
        }
        footerView.findViewById<View?>(R.id.tvBilibiliLink)?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://space.bilibili.com/1523985433"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开链接，请手动搜索B站UID: 1523985433", Toast.LENGTH_LONG).show()
            }
        }
        footerView.findViewById<View?>(R.id.tvDouyinLink)?.setOnClickListener {
            val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = android.content.ClipData.newPlainText("抖音ID", "31991565756")
            clip.setPrimaryClip(clipData)
            Toast.makeText(this, "抖音ID已复制到剪贴板：31991565756", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSettings() {
        // Settings are loaded per-item via onItemBound callback
        // This method is kept for compatibility - initial visible items are already loaded
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
        // Click listeners are set up per-item via onItemBound callback
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
                updateParamsForProvider(providerId)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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

    private fun setupLlmParams() {
        val sm = settingsManager ?: return

        seekTemp?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvTemp?.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sm = settingsManager ?: return
                sm.llmTemperature = seekBar?.progress?.div(100f) ?: return
            }
        })

        seekTopP?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvTopP?.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sm = settingsManager ?: return
                sm.llmTopP = seekBar?.progress?.div(100f) ?: return
            }
        })

        seekFreqP?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val profile = sm.getCurrentProfile()
                val range = profile.freqPenaltyRange ?: (-2f)..2f
                val value = range.start + (progress / 400f) * (range.endInclusive - range.start)
                tvFreqP?.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sm = settingsManager ?: return
                val profile = sm.getCurrentProfile()
                val range = profile.freqPenaltyRange ?: (-2f)..2f
                val value = range.start + ((seekBar?.progress ?: 0) / 400f) * (range.endInclusive - range.start)
                sm.llmFrequencyPenalty = value
            }
        })

        seekPresP?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val profile = sm.getCurrentProfile()
                val range = profile.presPenaltyRange ?: (-2f)..2f
                val value = range.start + (progress / 400f) * (range.endInclusive - range.start)
                tvPresP?.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sm = settingsManager ?: return
                val profile = sm.getCurrentProfile()
                val range = profile.presPenaltyRange ?: (-2f)..2f
                val value = range.start + ((seekBar?.progress ?: 0) / 400f) * (range.endInclusive - range.start)
                sm.llmPresencePenalty = value
            }
        })

        seekMaxTok?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val limit = sm.getEffectiveMaxTokensLimit()
                val value = if (limit <= 10000) {
                    progress.coerceIn(50, limit)
                } else {
                    val scaled = (progress / 10000f * limit).toInt()
                    scaled.coerceIn(50, limit)
                }
                etMaxTok?.setText("$value")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sm = settingsManager ?: return
                val limit = sm.getEffectiveMaxTokensLimit()
                val value = if (limit <= 10000) {
                    (seekBar?.progress ?: 50).coerceIn(50, limit)
                } else {
                    ((seekBar?.progress ?: 0) / 10000f * limit).toInt().coerceIn(50, limit)
                }
                sm.llmMaxTokens = value
            }
        })

        etMaxTok?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val et = etMaxTok ?: return@setOnFocusChangeListener
                val text = et.text?.toString()?.trim() ?: ""
                val value = text.toIntOrNull()?.coerceIn(50, sm.getEffectiveMaxTokensLimit()) ?: sm.llmMaxTokens
                et.setText("$value")
                sm.llmMaxTokens = value
                updateMaxTokensSeekBar(seekMaxTok, value, sm.getEffectiveMaxTokensLimit())
            }
        }

        updateParamsForProvider(sm.apiProvider)
    }

    private fun updateMaxTokensSeekBar(seekBar: SeekBar?, value: Int, limit: Int) {
        if (limit <= 10000) {
            seekBar?.max = limit
            seekBar?.progress = value.coerceIn(0, limit)
        } else {
            seekBar?.max = 10000
            seekBar?.progress = (value.toFloat() / limit * 10000).toInt().coerceIn(0, 10000)
        }
    }

    fun updateParamsForProvider(providerId: String) {
        val sm = settingsManager ?: return
        val profile = ProviderProfile.getProfile(providerId)

        seekTemp?.max = (profile.tempRange.endInclusive * 100).toInt()
        val currentTemp = sm.llmTemperature.coerceIn(profile.tempRange)
        seekTemp?.progress = (currentTemp * 100).toInt()
        tvTemp?.text = String.format("%.2f", currentTemp)

        seekTopP?.max = (profile.topPRange.endInclusive * 100).toInt()
        val currentTopP = sm.llmTopP.coerceIn(profile.topPRange)
        seekTopP?.progress = (currentTopP * 100).toInt()
        tvTopP?.text = String.format("%.2f", currentTopP)

        layoutFreqP?.visibility = if (profile.supportsFreqPenalty) View.VISIBLE else View.GONE
        if (profile.supportsFreqPenalty && profile.freqPenaltyRange != null) {
            val range = profile.freqPenaltyRange
            val currentFreqP = sm.llmFrequencyPenalty.coerceIn(range)
            val progress = ((currentFreqP - range.start) / (range.endInclusive - range.start) * 400).toInt()
            seekFreqP?.progress = progress.coerceIn(0, 400)
            tvFreqP?.text = String.format("%.2f", currentFreqP)
        }

        layoutPresP?.visibility = if (profile.supportsPresPenalty) View.VISIBLE else View.GONE
        if (profile.supportsPresPenalty && profile.presPenaltyRange != null) {
            val range = profile.presPenaltyRange
            val currentPresP = sm.llmPresencePenalty.coerceIn(range)
            val progress = ((currentPresP - range.start) / (range.endInclusive - range.start) * 400).toInt()
            seekPresP?.progress = progress.coerceIn(0, 400)
            tvPresP?.text = String.format("%.2f", currentPresP)
        }

        val limit = profile.maxTokensLimit
        val currentMaxTok = sm.llmMaxTokens.coerceIn(50, limit)
        sm.llmMaxTokens = currentMaxTok
        etMaxTok?.setText("$currentMaxTok")
        updateMaxTokensSeekBar(seekMaxTok, currentMaxTok, limit)
        tvMaxTokLimit?.text = "上限: ${formatTokenCount(limit)} (当前厂商: ${profile.displayName})"

        val hints = profile.paramHints
        if (hints.isNotEmpty()) {
            val hintBuilder = StringBuilder()
            hints.values.forEach { hint ->
                hintBuilder.appendLine("• $hint")
            }
            tvProviderHint?.text = hintBuilder.toString().trim()
            tvProviderHint?.visibility = View.VISIBLE
        } else {
            tvProviderHint?.visibility = View.GONE
        }
    }

    private fun formatTokenCount(count: Int): String {
        return when {
            count >= 1000 -> "${count / 1000}K"
            else -> "$count"
        }
    }

    private fun setupTtsEngine(sm: SettingsManager) {
        val engineNames = listOf("Edge TTS (免费)", "云端 TTS", "自动", "仅本地")
        val engineValues = listOf(
            com.aicompanion.voice.TtsManager.ENGINE_EDGE,
            com.aicompanion.voice.TtsManager.ENGINE_CLOUD,
            com.aicompanion.voice.TtsManager.ENGINE_AUTO,
            com.aicompanion.voice.TtsManager.ENGINE_LOCAL
        )

        val engineAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, engineNames)
        engineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTtsEngine?.adapter = engineAdapter

        val currentEngine = sm.ttsEngineMode
        val engineIdx = engineValues.indexOf(currentEngine).coerceAtLeast(0)
        spinnerTtsEngine?.setSelection(engineIdx)

        val voices = com.aicompanion.voice.EdgeTtsEngine.VOICES
        val voiceNames = voices.map { "${it.displayName} (${it.gender}) - ${it.locale}" }
        val voiceAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, voiceNames)
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEdgeVoice?.adapter = voiceAdapter

        val currentVoice = sm.ttsVoice
        val voiceIdx = voices.indexOfFirst { it.id == currentVoice }.coerceAtLeast(0)
        spinnerEdgeVoice?.setSelection(voiceIdx)

        updateTtsVisibility(currentEngine)

        spinnerTtsEngine?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedEngine = engineValues[position]
                sm.ttsEngineMode = selectedEngine
                updateTtsVisibility(selectedEngine)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerEdgeVoice?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in voices.indices) {
                    sm.ttsVoice = voices[position].id
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun updateTtsVisibility(engine: String) {
        when (engine) {
            com.aicompanion.voice.TtsManager.ENGINE_EDGE -> {
                layoutEdgeVoice?.visibility = View.VISIBLE
                layoutCloudTts?.visibility = View.GONE
            }
            com.aicompanion.voice.TtsManager.ENGINE_CLOUD -> {
                layoutEdgeVoice?.visibility = View.GONE
                layoutCloudTts?.visibility = View.VISIBLE
            }
            else -> {
                layoutEdgeVoice?.visibility = View.GONE
                layoutCloudTts?.visibility = View.GONE
            }
        }
    }

    private fun setupTtsParams() {
        val sm = settingsManager ?: return

        val seekPitch = findSettingsView<SeekBar>(R.id.seek_tts_pitch)
        val tvPitch = findSettingsView<TextView>(R.id.tv_tts_pitch_value)
        val seekRate = findSettingsView<SeekBar>(R.id.seek_tts_rate)
        val tvRate = findSettingsView<TextView>(R.id.tv_tts_rate_value)

        seekPitch?.progress = (sm.ttsPitch * 100).toInt().coerceIn(50, 150)
        tvPitch?.text = String.format("%.2f", sm.ttsPitch)
        seekRate?.progress = (sm.ttsRate * 100).toInt().coerceIn(50, 200)
        tvRate?.text = String.format("%.2f", sm.ttsRate)

        seekPitch?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvPitch?.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sm = settingsManager ?: return
                sm.ttsPitch = (seekBar?.progress ?: 100) / 100f
            }
        })

        seekRate?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                tvRate?.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sm = settingsManager ?: return
                sm.ttsRate = (seekBar?.progress ?: 100) / 100f
            }
        })
    }

    private fun setupOverlaySize() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val sizePercent = prefs.getInt("overlay_size_percent", 100)

        seekOverlaySize?.progress = sizePercent
        tvOverlaySizeValue?.text = "${sizePercent}%"

        seekOverlaySize?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOverlaySizeValue?.text = "${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit().putInt("overlay_size_percent", seekBar?.progress ?: 100).apply()
                com.aicompanion.overlay.OverlayWindow.notifySizeChanged()
            }
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
        updateWechatStatus()
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

        sm.chatApiUrl = findSettingsView<TextView>(R.id.et_chat_api_url)?.text?.toString() ?: sm.chatApiUrl
        sm.chatApiKey = findSettingsView<TextView>(R.id.et_chat_api_key)?.text?.toString() ?: sm.chatApiKey
        sm.chatModel = findSettingsView<TextView>(R.id.et_chat_model)?.text?.toString() ?: sm.chatModel
        sm.screenApiUrl = findSettingsView<TextView>(R.id.et_screen_api_url)?.text?.toString() ?: sm.screenApiUrl
        sm.screenModel = findSettingsView<TextView>(R.id.et_screen_model)?.text?.toString() ?: sm.screenModel
        sm.asrApiUrl = findSettingsView<TextView>(R.id.et_asr_api_url)?.text?.toString() ?: sm.asrApiUrl
        sm.ttsApiUrl = findSettingsView<TextView>(R.id.et_tts_api_url)?.text?.toString() ?: sm.ttsApiUrl
        sm.ttsModel = findSettingsView<TextView>(R.id.et_tts_model)?.text?.toString() ?: sm.ttsModel
        sm.userId = findSettingsView<TextView>(R.id.et_user_id)?.text?.toString() ?: sm.userId

        sm.screenRecognitionEnabled = findSettingsView<Switch>(R.id.switch_screen_recognition)?.isChecked ?: sm.screenRecognitionEnabled
        sm.simpleScreenMode = findSettingsView<Switch>(R.id.switch_simple_screen_mode)?.isChecked ?: sm.simpleScreenMode
        sm.voiceRecognitionEnabled = findSettingsView<Switch>(R.id.switch_voice_recognition)?.isChecked ?: sm.voiceRecognitionEnabled
        sm.ttsEnabled = findSettingsView<Switch>(R.id.switch_tts)?.isChecked ?: sm.ttsEnabled
        sm.offlineMode = findSettingsView<Switch>(R.id.switch_offline_mode)?.isChecked ?: sm.offlineMode
        sm.live2dEnabled = findSettingsView<Switch>(R.id.switch_live2d)?.isChecked ?: sm.live2dEnabled

        val nagRadio = findSettingsView<RadioGroup>(R.id.radio_nag_frequency)
        sm.nagFrequency = when (nagRadio?.checkedRadioButtonId) {
            R.id.radio_low -> NagFrequency.LOW
            R.id.radio_medium -> NagFrequency.MEDIUM
            R.id.radio_high -> NagFrequency.HIGH
            else -> NagFrequency.OFF
        }

        val langRadio = findSettingsView<RadioGroup>(R.id.radio_language_style)
        sm.languageStyle = when (langRadio?.checkedRadioButtonId) {
            R.id.radio_tsundere -> LanguageStyle.TSUNDERE
            R.id.radio_cute -> LanguageStyle.CUTE
            else -> LanguageStyle.NORMAL
        }

        sm.autoStart = findSettingsView<Switch>(R.id.switch_auto_start)?.isChecked ?: sm.autoStart
        sm.backgroundRunning = findSettingsView<Switch>(R.id.switch_background_running)?.isChecked ?: sm.backgroundRunning

        val diaryRadio = findSettingsView<RadioGroup>(R.id.radio_diary_trigger)
        sm.diaryTriggerMode = when (diaryRadio?.checkedRadioButtonId) {
            R.id.radio_diary_manual -> com.aicompanion.settings.DiaryTriggerMode.MANUAL
            R.id.radio_diary_50msg -> com.aicompanion.settings.DiaryTriggerMode.MSG_50
            R.id.radio_diary_hourly -> com.aicompanion.settings.DiaryTriggerMode.HOURLY
            R.id.radio_diary_2h -> com.aicompanion.settings.DiaryTriggerMode.EVERY_2H
            R.id.radio_diary_10pm -> com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM
            else -> com.aicompanion.settings.DiaryTriggerMode.DAILY_10PM
        }

        sm.searchEnabled = findSettingsView<Switch>(R.id.switch_search_enabled)?.isChecked ?: sm.searchEnabled
        sm.searchApiUrl = findSettingsView<TextView>(R.id.et_search_api_url)?.text?.toString() ?: sm.searchApiUrl
        sm.searchApiKey = findSettingsView<TextView>(R.id.et_search_api_key)?.text?.toString() ?: sm.searchApiKey
        sm.searchEngineId = findSettingsView<TextView>(R.id.et_search_engine_id)?.text?.toString() ?: sm.searchEngineId

        val vwMgr = VirtualWorldManager(this)
        vwMgr.imageApiUrl = findSettingsView<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_api_url)?.text?.toString() ?: vwMgr.imageApiUrl
        vwMgr.imageApiKey = findSettingsView<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_api_key)?.text?.toString() ?: vwMgr.imageApiKey
        vwMgr.imageModel = findSettingsView<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_model)?.text?.toString() ?: vwMgr.imageModel

        sm.emotionAnalysisEnabled = findSettingsView<Switch>(R.id.switch_emotion_analysis)?.isChecked ?: sm.emotionAnalysisEnabled
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
