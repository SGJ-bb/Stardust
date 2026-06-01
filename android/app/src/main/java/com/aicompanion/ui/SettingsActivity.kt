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
import android.widget.ImageView
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
import com.aicompanion.settings.ServicePresets
import com.aicompanion.diary.DiaryManager
import com.aicompanion.models.Emotion
import com.aicompanion.theme.ThemeManager
import com.aicompanion.wakeup.WakeUpScheduler
import com.aicompanion.virtualworld.VirtualWorldManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    private var btnTestTts: com.google.android.material.button.MaterialButton? = null
    private var tvTtsTestResult: TextView? = null
    private var btnTestAsr: com.google.android.material.button.MaterialButton? = null
    private var tvAsrTestResult: TextView? = null
    private var btnTestImageGen: com.google.android.material.button.MaterialButton? = null
    private var ivImageGenPreview: ImageView? = null
    private var tvImageGenTestResult: TextView? = null
    private var btnTestImageRecog: com.google.android.material.button.MaterialButton? = null
    private var tvImageRecogTestResult: TextView? = null
    private var seekOverlaySize: SeekBar? = null
    private var tvOverlaySizeValue: TextView? = null
    private var radioNagFrequency: RadioGroup? = null
    private var radioLanguageStyle: RadioGroup? = null

    private var spinnerApiProvider: Spinner? = null
    private var tvApiProviderHint: TextView? = null
    private var spinnerTtsProvider: Spinner? = null
    private var spinnerAsrProvider: Spinner? = null
    private var spinnerImageGenProvider: Spinner? = null
    private var spinnerImageRecogProvider: Spinner? = null
    private var isTtsProviderInitialized = false
    private var isAsrProviderInitialized = false
    private var isImageGenProviderInitialized = false
    private var isImageRecogProviderInitialized = false
    private var etChatApiUrl: TextView? = null
    private var etChatApiKey: TextView? = null
    private var etChatModel: TextView? = null
    private var etScreenApiUrl: TextView? = null
    private var etScreenModel: TextView? = null
    private var etAsrApiUrl: TextView? = null
    private var etAsrApiKey: TextView? = null
    private var etAsrModel: TextView? = null
    private var etTtsApiUrl: TextView? = null
    private var etTtsApiKey: TextView? = null
    private var etTtsModel: TextView? = null
    private var etTtsVoiceName: TextView? = null
    private var etUserId: TextView? = null
    private var spinnerUserGender: Spinner? = null
    private var etUserBirthday: TextView? = null
    private var etUserAppearance: TextView? = null

    private var switchScreenRecognition: Switch? = null
    private var switchSimpleScreenMode: Switch? = null
    private var switchChatModelVision: Switch? = null
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

    private val ttsKnownDefaults = setOf("tts-1", "tts-1-hd", "cosyvoice-v1", "s2-pro", "FunAudioLLM/CosyVoice2-0.5B")
    private val asrKnownDefaults = setOf("whisper-1", "FunAudioLLM/SenseVoiceSmall", "sensevoice-v1")
    private val imageGenKnownDefaults = setOf("dall-e-3", "Kwai-Kolors/Kolors", "kling-v3-image-generation", "cogview-4-250304")
    private val imageRecogKnownDefaults = setOf("gpt-4o", "qwen-vl-max", "glm-4v-flash", "Qwen/Qwen2-VL-7B-Instruct")

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
    private var etImageApiSecretKey: com.google.android.material.textfield.TextInputEditText? = null
    private var tvImageKeyHint: TextView? = null
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

            com.aicompanion.util.AppLogger.enabled = settingsManager?.appLoggingEnabled ?: true
            com.aicompanion.util.AppLogger.debugVerbose = settingsManager?.appDebugVerbose ?: false

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
        } else {
            refreshValuesForType(view, type)
        }
    }

    private fun refreshValuesForType(view: View, type: Int) {
        val sm = settingsManager ?: return
        when (type) {
            SettingsAdapter.TYPE_ASR -> {
                view.findViewById<Switch>(R.id.switch_voice_recognition)?.isChecked = sm.voiceRecognitionEnabled
                val asrUrl = view.findViewById<TextView>(R.id.et_asr_api_url)
                if (asrUrl?.hasFocus() != true) asrUrl?.text = sm.asrApiUrl
                val asrKey = view.findViewById<TextView>(R.id.et_asr_api_key)
                if (asrKey?.hasFocus() != true) asrKey?.text = sm.asrApiKey
                val asrModel = view.findViewById<TextView>(R.id.et_asr_model)
                if (asrModel?.hasFocus() != true) asrModel?.text = sm.asrModel
            }
            SettingsAdapter.TYPE_TTS -> {
                view.findViewById<Switch>(R.id.switch_tts)?.isChecked = sm.ttsEnabled
                val ttsUrl = view.findViewById<TextView>(R.id.et_tts_api_url)
                if (ttsUrl?.hasFocus() != true) ttsUrl?.text = sm.ttsApiUrl
                val ttsKey = view.findViewById<TextView>(R.id.et_tts_api_key)
                if (ttsKey?.hasFocus() != true) ttsKey?.text = sm.ttsApiKey
                val ttsModel = view.findViewById<TextView>(R.id.et_tts_model)
                if (ttsModel?.hasFocus() != true) ttsModel?.text = sm.ttsModel
                val ttsVoice = view.findViewById<TextView>(R.id.et_tts_voice_name)
                if (ttsVoice?.hasFocus() != true) ttsVoice?.text = sm.ttsVoiceName
                view.findViewById<Switch>(R.id.switch_emotion_analysis)?.isChecked = sm.emotionAnalysisEnabled
                updateTtsVisibility(sm.ttsEngineMode)
            }
            SettingsAdapter.TYPE_SCREEN -> {
                view.findViewById<Switch>(R.id.switch_screen_recognition)?.isChecked = sm.screenRecognitionEnabled
                val screenUrl = view.findViewById<TextView>(R.id.et_screen_api_url)
                if (screenUrl?.hasFocus() != true) screenUrl?.text = sm.screenApiUrl
                val screenModel = view.findViewById<TextView>(R.id.et_screen_model)
                if (screenModel?.hasFocus() != true) screenModel?.text = sm.screenModel
                view.findViewById<Switch>(R.id.switch_simple_screen_mode)?.isChecked = sm.simpleScreenMode
                view.findViewById<Switch>(R.id.switch_chat_model_vision)?.isChecked = sm.useChatModelForVision
            }
            SettingsAdapter.TYPE_LLM -> {
                val chatUrl = view.findViewById<TextView>(R.id.et_chat_api_url)
                if (chatUrl?.hasFocus() != true) chatUrl?.text = sm.chatApiUrl
                val chatKey = view.findViewById<TextView>(R.id.et_chat_api_key)
                if (chatKey?.hasFocus() != true) chatKey?.text = sm.chatApiKey
                val chatModel = view.findViewById<TextView>(R.id.et_chat_model)
                if (chatModel?.hasFocus() != true) chatModel?.text = sm.chatModel
            }
            SettingsAdapter.TYPE_USER -> {
                val userId = view.findViewById<TextView>(R.id.et_user_id)
                if (userId?.hasFocus() != true) userId?.text = sm.userId
                val userBday = view.findViewById<TextView>(R.id.et_user_birthday)
                if (userBday?.hasFocus() != true) userBday?.text = sm.userBirthday
                val userAppr = view.findViewById<TextView>(R.id.et_user_appearance)
                if (userAppr?.hasFocus() != true) userAppr?.text = sm.userAppearance
                view.findViewById<Switch>(R.id.switch_offline_mode)?.isChecked = sm.offlineMode
            }
            SettingsAdapter.TYPE_SEARCH -> {
                view.findViewById<Switch>(R.id.switch_search_enabled)?.isChecked = sm.searchEnabled
                val searchUrl = view.findViewById<TextView>(R.id.et_search_api_url)
                if (searchUrl?.hasFocus() != true) searchUrl?.text = sm.searchApiUrl
                val searchKey = view.findViewById<TextView>(R.id.et_search_api_key)
                if (searchKey?.hasFocus() != true) searchKey?.text = sm.searchApiKey
                val searchEngine = view.findViewById<TextView>(R.id.et_search_engine_id)
                if (searchEngine?.hasFocus() != true) searchEngine?.text = sm.searchEngineId
            }
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
                switchChatModelVision = view.findViewById(R.id.switch_chat_model_vision)
                spinnerImageRecogProvider = view.findViewById(R.id.spinner_image_recog_provider)
                btnTestImageRecog = view.findViewById(R.id.btn_test_image_recog)
                tvImageRecogTestResult = view.findViewById(R.id.tv_image_recog_test_result)
            }
            SettingsAdapter.TYPE_ASR -> {
                switchVoiceRecognition = view.findViewById(R.id.switch_voice_recognition)
                etAsrApiUrl = view.findViewById(R.id.et_asr_api_url)
                etAsrApiKey = view.findViewById(R.id.et_asr_api_key)
                etAsrModel = view.findViewById(R.id.et_asr_model)
                spinnerAsrProvider = view.findViewById(R.id.spinner_asr_provider)
                btnTestAsr = view.findViewById(R.id.btn_test_asr)
                tvAsrTestResult = view.findViewById(R.id.tv_asr_test_result)
            }
            SettingsAdapter.TYPE_TTS -> {
                switchTts = view.findViewById(R.id.switch_tts)
                etTtsApiUrl = view.findViewById(R.id.et_tts_api_url)
                etTtsApiKey = view.findViewById(R.id.et_tts_api_key)
                etTtsModel = view.findViewById(R.id.et_tts_model)
                etTtsVoiceName = view.findViewById(R.id.et_tts_voice_name)
                switchEmotionAnalysis = view.findViewById(R.id.switch_emotion_analysis)
                spinnerTtsEngine = view.findViewById(R.id.spinner_tts_engine)
                spinnerEdgeVoice = view.findViewById(R.id.spinner_edge_voice)
                layoutEdgeVoice = view.findViewById(R.id.layout_edge_voice)
                layoutCloudTts = view.findViewById(R.id.layout_cloud_tts)
                spinnerTtsProvider = view.findViewById(R.id.spinner_tts_provider)
                btnTestTts = view.findViewById(R.id.btn_test_tts)
                tvTtsTestResult = view.findViewById(R.id.tv_tts_test_result)
            }
            SettingsAdapter.TYPE_USER -> {
                etUserId = view.findViewById(R.id.et_user_id)
                spinnerUserGender = view.findViewById(R.id.spinner_user_gender)
                etUserBirthday = view.findViewById(R.id.et_user_birthday)
                etUserAppearance = view.findViewById(R.id.et_user_appearance)
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
                etImageApiSecretKey = view.findViewById(R.id.et_image_api_secret_key)
                tvImageKeyHint = view.findViewById(R.id.tv_image_key_hint)
                etImageModel = view.findViewById(R.id.et_image_model)
                spinnerImageGenProvider = view.findViewById(R.id.spinner_image_gen_provider)
                btnTestImageGen = view.findViewById(R.id.btn_test_image_gen)
                ivImageGenPreview = view.findViewById(R.id.iv_image_gen_preview)
                tvImageGenTestResult = view.findViewById(R.id.tv_image_gen_test_result)
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
                switchChatModelVision?.isChecked = sm.useChatModelForVision
                setupImageRecogProviderSpinner()
            }
            SettingsAdapter.TYPE_ASR -> {
                switchVoiceRecognition?.isChecked = sm.voiceRecognitionEnabled
                etAsrApiUrl?.text = sm.asrApiUrl
                etAsrApiKey?.text = sm.asrApiKey
                etAsrModel?.text = sm.asrModel
                setupAsrProviderSpinner()
            }
            SettingsAdapter.TYPE_TTS -> {
                switchTts?.isChecked = sm.ttsEnabled
                etTtsApiUrl?.text = sm.ttsApiUrl
                etTtsApiKey?.text = sm.ttsApiKey
                etTtsModel?.text = sm.ttsModel
                etTtsVoiceName?.text = sm.ttsVoiceName
                setupTtsEngine(sm)
                setupTtsParams()
                setupTtsProviderSpinner()
                switchEmotionAnalysis?.isChecked = sm.emotionAnalysisEnabled
            }
            SettingsAdapter.TYPE_USER -> {
                etUserId?.text = sm.userId
                setupGenderSpinner(sm)
                etUserBirthday?.text = sm.userBirthday
                etUserAppearance?.text = sm.userAppearance
                setupBirthdayPicker()
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
                etImageApiSecretKey?.setText(vwManager.imageApiSecretKey)
                etImageModel?.setText(vwManager.imageModel)
                setupImageGenProviderSpinner()
                val currentImageProvider = settingsManager?.imageGenProvider ?: "custom"
                if (currentImageProvider == "aliyun_kling") {
                    tvImageKeyHint?.visibility = View.VISIBLE
                    findSettingsView<com.google.android.material.textfield.TextInputLayout>(R.id.layout_image_api_secret_key)?.visibility = View.VISIBLE
                }
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
                btnViewLog?.setOnClickListener {
                    val logs = com.aicompanion.util.AppLogger.getRecentLogs(200)
                    if (logs.isBlank()) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("应用日志")
                            .setMessage("暂无日志记录")
                            .setPositiveButton("确定", null)
                            .show()
                        return@setOnClickListener
                    }
                    val scrollView = android.widget.ScrollView(this)
                    val tv = android.widget.TextView(this).apply {
                        text = logs
                        textSize = 11f
                        setPadding(24, 16, 24, 16)
                        setTextIsSelectable(true)
                    }
                    scrollView.addView(tv)
                    android.app.AlertDialog.Builder(this)
                        .setTitle("应用日志 (最近200条)")
                        .setView(scrollView)
                        .setPositiveButton("关闭", null)
                        .setNeutralButton("复制全部") { _, _ ->
                            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("logs", logs))
                            android.widget.Toast.makeText(this, "日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("清空") { _, _ ->
                            com.aicompanion.util.AppLogger.clear()
                            android.widget.Toast.makeText(this, "日志已清空", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .show()
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
            SettingsAdapter.TYPE_TTS -> {
                btnTestTts?.setOnClickListener { testTtsApi() }
            }
            SettingsAdapter.TYPE_ASR -> {
                btnTestAsr?.setOnClickListener { testAsrApi() }
            }
            SettingsAdapter.TYPE_SCREEN -> {
                btnTestImageRecog?.setOnClickListener { testImageRecogApi() }
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
                btnTestImageGen?.setOnClickListener { testImageGenApi() }
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

    private fun setupGenderSpinner(sm: SettingsManager) {
        val genders = arrayOf("未选择", "男", "女")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_dark, genders)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerUserGender?.adapter = adapter

        val saved = sm.userGender
        val idx = when (saved) {
            "male" -> 1; "female" -> 2; else -> 0
        }
        spinnerUserGender?.setSelection(idx)

        spinnerUserGender?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sm.userGender = when (position) {
                    1 -> "male"; 2 -> "female"; else -> ""
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupBirthdayPicker() {
        etUserBirthday?.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            val currentText = etUserBirthday?.text?.toString() ?: ""
            if (currentText.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                val parts = currentText.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            android.app.DatePickerDialog(
                this,
                R.style.ThemeOverlay_Companion_DatePicker,
                { _, year, month, day ->
                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                    etUserBirthday?.text = dateStr
                    settingsManager?.userBirthday = dateStr
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
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
        saveSettings()
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

    private fun testTtsApi() {
        val sm = settingsManager ?: return
        val url = etTtsApiUrl?.text?.toString()?.trim() ?: ""
        val key = etTtsApiKey?.text?.toString()?.trim() ?: ""
        val model = etTtsModel?.text?.toString()?.trim() ?: ""
        val voice = etTtsVoiceName?.text?.toString()?.trim() ?: ""
        if (url.isBlank() || key.isBlank()) {
            tvTtsTestResult?.text = "请先填写API地址和Key"
            tvTtsTestResult?.setTextColor(0xFFF44336.toInt())
            tvTtsTestResult?.visibility = View.VISIBLE
            return
        }
        btnTestTts?.isEnabled = false
        btnTestTts?.text = "测试中..."
        tvTtsTestResult?.visibility = View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.aicompanion.util.AppLogger.i("TTS_TEST", "开始测试TTS: url=$url, model=$model, voice=$voice")
                val preset = com.aicompanion.settings.ServicePresets.findTtsPreset(sm.ttsProvider)
                val formatType = preset.formatType
                val jsonBody = com.aicompanion.network.ProviderAdapter.buildTtsRequest(formatType, model, "你好，我是星尘，很高兴认识你！", voice)
                val headers = com.aicompanion.network.ProviderAdapter.buildTtsHeaders(formatType, key, model)
                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = okhttp3.Request.Builder().url(url).post(requestBody)
                headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: "无响应体"
                    com.aicompanion.util.AppLogger.e("TTS_TEST", "TTS测试失败: HTTP ${response.code}, body=$errBody")
                    withContext(Dispatchers.Main) {
                        tvTtsTestResult?.text = "失败: HTTP ${response.code} - ${errBody.take(100)}"
                        tvTtsTestResult?.setTextColor(0xFFF44336.toInt())
                        tvTtsTestResult?.visibility = View.VISIBLE
                        btnTestTts?.isEnabled = true
                        btnTestTts?.text = "测试语音合成"
                    }
                    return@launch
                }
                val contentType = response.header("Content-Type", "")
                val bodyBytes = response.body?.bytes()
                if (bodyBytes == null || bodyBytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        tvTtsTestResult?.text = "失败: 响应为空"
                        tvTtsTestResult?.setTextColor(0xFFF44336.toInt())
                        tvTtsTestResult?.visibility = View.VISIBLE
                        btnTestTts?.isEnabled = true
                        btnTestTts?.text = "测试语音合成"
                    }
                    return@launch
                }
                if (contentType?.contains("audio") == true || bodyBytes.size > 1000) {
                    val tempFile = java.io.File(cacheDir, "tts_test_${System.currentTimeMillis()}.mp3")
                    tempFile.writeBytes(bodyBytes)
                    com.aicompanion.util.AppLogger.i("TTS_TEST", "TTS测试成功: 音频大小=${bodyBytes.size}字节, 保存至=${tempFile.absolutePath}")
                    withContext(Dispatchers.Main) {
                        tvTtsTestResult?.text = "成功! 音频大小: ${bodyBytes.size / 1024}KB"
                        tvTtsTestResult?.setTextColor(0xFF4CAF50.toInt())
                        tvTtsTestResult?.visibility = View.VISIBLE
                        btnTestTts?.isEnabled = true
                        btnTestTts?.text = "测试语音合成"
                        try {
                            val mp = android.media.MediaPlayer()
                            mp.setDataSource(tempFile.absolutePath)
                            mp.prepare()
                            mp.start()
                            mp.setOnCompletionListener { mp.release() }
                        } catch (_: Exception) {}
                    }
                } else {
                    val bodyStr = String(bodyBytes, Charsets.UTF_8).take(200)
                    withContext(Dispatchers.Main) {
                        tvTtsTestResult?.text = "响应非音频格式: $bodyStr"
                        tvTtsTestResult?.setTextColor(0xFFFF9800.toInt())
                        tvTtsTestResult?.visibility = View.VISIBLE
                        btnTestTts?.isEnabled = true
                        btnTestTts?.text = "测试语音合成"
                    }
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("TTS_TEST", "TTS测试异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    tvTtsTestResult?.text = "异常: ${e.message?.take(100)}"
                    tvTtsTestResult?.setTextColor(0xFFF44336.toInt())
                    tvTtsTestResult?.visibility = View.VISIBLE
                    btnTestTts?.isEnabled = true
                    btnTestTts?.text = "测试语音合成"
                }
            }
        }
    }

    private fun testAsrApi() {
        val sm = settingsManager ?: return
        val url = etAsrApiUrl?.text?.toString()?.trim() ?: ""
        val key = etAsrApiKey?.text?.toString()?.trim() ?: ""
        val model = etAsrModel?.text?.toString()?.trim() ?: ""
        if (url.isBlank() || key.isBlank()) {
            tvAsrTestResult?.text = "请先填写API地址和Key"
            tvAsrTestResult?.setTextColor(0xFFF44336.toInt())
            tvAsrTestResult?.visibility = View.VISIBLE
            return
        }
        btnTestAsr?.isEnabled = false
        btnTestAsr?.text = "录音中(3秒)..."
        tvAsrTestResult?.visibility = View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
                    withContext(Dispatchers.Main) {
                        tvAsrTestResult?.text = "需要录音权限，请授权后重试"
                        tvAsrTestResult?.setTextColor(0xFFFF9800.toInt())
                        tvAsrTestResult?.visibility = View.VISIBLE
                        btnTestAsr?.isEnabled = true
                        btnTestAsr?.text = "测试语音识别"
                    }
                    return@launch
                }
                com.aicompanion.util.AppLogger.i("ASR_TEST", "开始录音测试ASR")
                val recorder = android.media.MediaRecorder()
                val tempAudio = java.io.File(cacheDir, "asr_test_${System.currentTimeMillis()}.mp4")
                recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                recorder.setOutputFile(tempAudio.absolutePath)
                recorder.prepare()
                recorder.start()
                withContext(Dispatchers.Main) { btnTestAsr?.text = "录音中(3秒)..." }
                delay(3000)
                recorder.stop()
                recorder.release()
                com.aicompanion.util.AppLogger.i("ASR_TEST", "录音完成, 文件大小=${tempAudio.length()}字节")
                withContext(Dispatchers.Main) { btnTestAsr?.text = "识别中..." }
                val preset = com.aicompanion.settings.ServicePresets.findAsrPreset(sm.asrProvider)
                val formatType = preset.formatType
                val fields = com.aicompanion.network.ProviderAdapter.buildAsrRequestFields(formatType, model)
                val audioBytes = tempAudio.readBytes()
                val audioBody = audioBytes.toRequestBody("audio/mp4".toMediaType())
                val multipartBuilder = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("file", "audio.mp4", audioBody)
                fields.forEach { (k, v) -> multipartBuilder.addFormDataPart(k, v) }
                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $key")
                    .post(multipartBuilder.build())
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: "无响应体"
                    com.aicompanion.util.AppLogger.e("ASR_TEST", "ASR测试失败: HTTP ${response.code}, body=$errBody")
                    withContext(Dispatchers.Main) {
                        tvAsrTestResult?.text = "失败: HTTP ${response.code} - ${errBody.take(100)}"
                        tvAsrTestResult?.setTextColor(0xFFF44336.toInt())
                        tvAsrTestResult?.visibility = View.VISIBLE
                        btnTestAsr?.isEnabled = true
                        btnTestAsr?.text = "测试语音识别"
                    }
                    return@launch
                }
                val bodyStr = response.body?.string() ?: ""
                val result = com.aicompanion.network.ProviderAdapter.parseAsrResponse(formatType, bodyStr)
                com.aicompanion.util.AppLogger.i("ASR_TEST", "ASR测试成功: result=$result")
                withContext(Dispatchers.Main) {
                    if (result.isNotBlank()) {
                        tvAsrTestResult?.text = "识别结果: $result"
                        tvAsrTestResult?.setTextColor(0xFF4CAF50.toInt())
                    } else {
                        tvAsrTestResult?.text = "识别结果为空(可能未检测到语音)"
                        tvAsrTestResult?.setTextColor(0xFFFF9800.toInt())
                    }
                    tvAsrTestResult?.visibility = View.VISIBLE
                    btnTestAsr?.isEnabled = true
                    btnTestAsr?.text = "测试语音识别"
                }
                tempAudio.delete()
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("ASR_TEST", "ASR测试异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    tvAsrTestResult?.text = "异常: ${e.message?.take(100)}"
                    tvAsrTestResult?.setTextColor(0xFFF44336.toInt())
                    tvAsrTestResult?.visibility = View.VISIBLE
                    btnTestAsr?.isEnabled = true
                    btnTestAsr?.text = "测试语音识别"
                }
            }
        }
    }

    private fun testImageGenApi() {
        val sm = settingsManager ?: return
        val url = etImageApiUrl?.text?.toString()?.trim() ?: ""
        val key = etImageApiKey?.text?.toString()?.trim() ?: ""
        val secretKey = etImageApiSecretKey?.text?.toString()?.trim() ?: ""
        val model = etImageModel?.text?.toString()?.trim() ?: ""
        if (url.isBlank() || key.isBlank()) {
            tvImageGenTestResult?.text = "请先填写API地址和Key"
            tvImageGenTestResult?.setTextColor(0xFFF44336.toInt())
            tvImageGenTestResult?.visibility = View.VISIBLE
            return
        }
        btnTestImageGen?.isEnabled = false
        btnTestImageGen?.text = "生成中(可能需要30-60秒)..."
        tvImageGenTestResult?.visibility = View.GONE
        ivImageGenPreview?.visibility = View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.aicompanion.util.AppLogger.i("IMAGE_GEN_TEST", "开始测试图片生成: url=$url, model=$model")
                val preset = com.aicompanion.settings.ServicePresets.findImageGenPreset(sm.imageGenProvider)
                val formatType = if (sm.imageGenProvider == "custom") {
                    when {
                        url.contains("dashscope", ignoreCase = true) || url.contains("aliyuncs", ignoreCase = true) -> com.aicompanion.network.ProviderAdapter.FORMAT_ALIYUN_ASYNC
                        url.contains("siliconflow", ignoreCase = true) -> com.aicompanion.network.ProviderAdapter.FORMAT_SILICONFLOW
                        else -> "openai"
                    }
                } else {
                    preset.formatType
                }
                val jsonBody = com.aicompanion.network.ProviderAdapter.buildImageRequest(formatType, model, "一只可爱的猫咪坐在窗台上", 1, "1024x1024")
                val headers = com.aicompanion.network.ProviderAdapter.buildImageHeaders(formatType, key, secretKey)
                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val requestBuilder = okhttp3.Request.Builder().url(url).post(requestBody)
                headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: "无响应体"
                    com.aicompanion.util.AppLogger.e("IMAGE_GEN_TEST", "图片生成测试失败: HTTP ${response.code}, body=$errBody")
                    withContext(Dispatchers.Main) {
                        tvImageGenTestResult?.text = "失败: HTTP ${response.code} - ${errBody.take(100)}"
                        tvImageGenTestResult?.setTextColor(0xFFF44336.toInt())
                        tvImageGenTestResult?.visibility = View.VISIBLE
                        btnTestImageGen?.isEnabled = true
                        btnTestImageGen?.text = "测试图片生成"
                    }
                    return@launch
                }
                val bodyStr = response.body?.string() ?: ""
                val imageUrl = com.aicompanion.network.ProviderAdapter.parseImageResponse(formatType, bodyStr, url, key, secretKey)
                if (imageUrl.isNullOrBlank()) {
                    com.aicompanion.util.AppLogger.e("IMAGE_GEN_TEST", "图片生成测试失败: 无法解析图片URL, body=${bodyStr.take(200)}")
                    withContext(Dispatchers.Main) {
                        tvImageGenTestResult?.text = "失败: 无法解析图片URL"
                        tvImageGenTestResult?.setTextColor(0xFFF44336.toInt())
                        tvImageGenTestResult?.visibility = View.VISIBLE
                        btnTestImageGen?.isEnabled = true
                        btnTestImageGen?.text = "测试图片生成"
                    }
                    return@launch
                }
                com.aicompanion.util.AppLogger.i("IMAGE_GEN_TEST", "图片生成成功: imageUrl=${imageUrl.take(100)}")
                val downloadClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val isLocalFile = imageUrl.startsWith("/") || imageUrl.startsWith("file://")
                val bitmap = if (isLocalFile) {
                    android.graphics.BitmapFactory.decodeFile(imageUrl)
                } else {
                    val dlRequest = okhttp3.Request.Builder().url(imageUrl)
                    if (key.isNotBlank() && !imageUrl.contains("dashscope", ignoreCase = true) && !imageUrl.contains("aliyuncs", ignoreCase = true)) {
                        dlRequest.addHeader("Authorization", "Bearer $key")
                    }
                    val dlResponse = downloadClient.newCall(dlRequest.build()).execute()
                    val bmpBytes = dlResponse.body?.bytes()
                    if (bmpBytes != null) android.graphics.BitmapFactory.decodeByteArray(bmpBytes, 0, bmpBytes.size) else null
                }
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        ivImageGenPreview?.setImageBitmap(bitmap)
                        ivImageGenPreview?.visibility = View.VISIBLE
                        tvImageGenTestResult?.text = "成功! 图片已生成"
                        tvImageGenTestResult?.setTextColor(0xFF4CAF50.toInt())
                    } else {
                        tvImageGenTestResult?.text = "图片URL已获取但下载失败"
                        tvImageGenTestResult?.setTextColor(0xFFFF9800.toInt())
                    }
                    tvImageGenTestResult?.visibility = View.VISIBLE
                    btnTestImageGen?.isEnabled = true
                    btnTestImageGen?.text = "测试图片生成"
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("IMAGE_GEN_TEST", "图片生成测试异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    tvImageGenTestResult?.text = "异常: ${e.message?.take(100)}"
                    tvImageGenTestResult?.setTextColor(0xFFF44336.toInt())
                    tvImageGenTestResult?.visibility = View.VISIBLE
                    btnTestImageGen?.isEnabled = true
                    btnTestImageGen?.text = "测试图片生成"
                }
            }
        }
    }

    private fun testImageRecogApi() {
        val sm = settingsManager ?: return
        val url = etScreenApiUrl?.text?.toString()?.trim() ?: ""
        val model = etScreenModel?.text?.toString()?.trim() ?: ""
        val key = sm.screenApiKey ?: ""
        if (url.isBlank() || key.isBlank()) {
            tvImageRecogTestResult?.text = "请先填写API地址和Key(在屏幕识别设置中)"
            tvImageRecogTestResult?.setTextColor(0xFFF44336.toInt())
            tvImageRecogTestResult?.visibility = View.VISIBLE
            return
        }
        btnTestImageRecog?.isEnabled = false
        btnTestImageRecog?.text = "识别中..."
        tvImageRecogTestResult?.visibility = View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.aicompanion.util.AppLogger.i("IMAGE_RECOG_TEST", "开始测试图片识别: url=$url, model=$model")
                val screenshot = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(screenshot)
                canvas.drawColor(android.graphics.Color.parseColor("#4A90D9"))
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                canvas.drawText("Test", 50f, 110f, paint)
                val stream = java.io.ByteArrayOutputStream()
                screenshot.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                screenshot.recycle()
                val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64"
                val messages = org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("type", "text")
                                put("text", "请简要描述这张图片的内容")
                            })
                            put(org.json.JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", org.json.JSONObject().apply {
                                    put("url", dataUrl)
                                })
                            })
                        })
                    })
                }
                val jsonBody = org.json.JSONObject().apply {
                    put("model", model)
                    put("messages", messages)
                    put("max_tokens", 200)
                }
                val requestBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(300) ?: "无响应体"
                    com.aicompanion.util.AppLogger.e("IMAGE_RECOG_TEST", "图片识别测试失败: HTTP ${response.code}, body=$errBody")
                    withContext(Dispatchers.Main) {
                        tvImageRecogTestResult?.text = "失败: HTTP ${response.code} - ${errBody.take(100)}"
                        tvImageRecogTestResult?.setTextColor(0xFFF44336.toInt())
                        tvImageRecogTestResult?.visibility = View.VISIBLE
                        btnTestImageRecog?.isEnabled = true
                        btnTestImageRecog?.text = "测试图片识别"
                    }
                    return@launch
                }
                val bodyStr = response.body?.string() ?: ""
                val resultJson = org.json.JSONObject(bodyStr)
                val result = resultJson.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
                com.aicompanion.util.AppLogger.i("IMAGE_RECOG_TEST", "图片识别测试成功: result=${result.take(100)}")
                withContext(Dispatchers.Main) {
                    if (result.isNotBlank()) {
                        tvImageRecogTestResult?.text = "识别结果: $result"
                        tvImageRecogTestResult?.setTextColor(0xFF4CAF50.toInt())
                    } else {
                        tvImageRecogTestResult?.text = "识别结果为空"
                        tvImageRecogTestResult?.setTextColor(0xFFFF9800.toInt())
                    }
                    tvImageRecogTestResult?.visibility = View.VISIBLE
                    btnTestImageRecog?.isEnabled = true
                    btnTestImageRecog?.text = "测试图片识别"
                }
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("IMAGE_RECOG_TEST", "图片识别测试异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    tvImageRecogTestResult?.text = "异常: ${e.message?.take(100)}"
                    tvImageRecogTestResult?.setTextColor(0xFFF44336.toInt())
                    tvImageRecogTestResult?.visibility = View.VISIBLE
                    btnTestImageRecog?.isEnabled = true
                    btnTestImageRecog?.text = "测试图片识别"
                }
            }
        }
    }

    private fun setupSpinner() {
        val presets = ServicePresets.llmPresets
        val providers = ServicePresets.getLlmDisplayNames()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerApiProvider?.adapter = adapter

        val savedProvider = settingsManager?.apiProvider ?: "custom"
        spinnerApiProvider?.setSelection(ServicePresets.getLlmIndex(savedProvider))

        spinnerApiProvider?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val preset = presets[position]
                if (isSpinnerInitialized && preset.url.isNotEmpty()) {
                    etChatApiUrl?.setText(preset.url)
                    val currentModel = etChatModel?.text?.toString()?.trim() ?: ""
                    val knownDefaults = ServicePresets.getLlmKnownDefaults()
                    if (currentModel.isEmpty() || currentModel in knownDefaults) {
                        etChatModel?.setText(preset.defaultModel)
                    }
                    tvApiProviderHint?.text = "已自动填充 ${preset.displayName} 配置"
                    tvApiProviderHint?.visibility = android.view.View.VISIBLE
                }
                settingsManager?.apiProvider = preset.id
                updateParamsForProvider(preset.id)
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
            com.aicompanion.voice.TtsManager.ENGINE_AUTO -> {
                layoutEdgeVoice?.visibility = View.VISIBLE
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

        currentFocus?.let { focused ->
            if (focused is TextView) {
                when (focused.id) {
                    R.id.et_chat_api_url -> sm.chatApiUrl = focused.text?.toString() ?: sm.chatApiUrl
                    R.id.et_chat_api_key -> sm.chatApiKey = focused.text?.toString() ?: sm.chatApiKey
                    R.id.et_chat_model -> sm.chatModel = focused.text?.toString() ?: sm.chatModel
                    R.id.et_screen_api_url -> sm.screenApiUrl = focused.text?.toString() ?: sm.screenApiUrl
                    R.id.et_screen_model -> sm.screenModel = focused.text?.toString() ?: sm.screenModel
                    R.id.et_asr_api_url -> sm.asrApiUrl = focused.text?.toString() ?: sm.asrApiUrl
                    R.id.et_asr_api_key -> sm.asrApiKey = focused.text?.toString() ?: sm.asrApiKey
                    R.id.et_asr_model -> sm.asrModel = focused.text?.toString() ?: sm.asrModel
                    R.id.et_tts_api_url -> sm.ttsApiUrl = focused.text?.toString() ?: sm.ttsApiUrl
                    R.id.et_tts_api_key -> sm.ttsApiKey = focused.text?.toString() ?: sm.ttsApiKey
                    R.id.et_tts_model -> sm.ttsModel = focused.text?.toString() ?: sm.ttsModel
                    R.id.et_tts_voice_name -> sm.ttsVoiceName = focused.text?.toString() ?: sm.ttsVoiceName
                    R.id.et_user_id -> sm.userId = focused.text?.toString() ?: sm.userId
                    R.id.et_user_birthday -> sm.userBirthday = focused.text?.toString() ?: sm.userBirthday
                    R.id.et_user_appearance -> sm.userAppearance = focused.text?.toString() ?: sm.userAppearance
                    R.id.et_search_api_url -> sm.searchApiUrl = focused.text?.toString() ?: sm.searchApiUrl
                    R.id.et_search_api_key -> sm.searchApiKey = focused.text?.toString() ?: sm.searchApiKey
                    R.id.et_search_engine_id -> sm.searchEngineId = focused.text?.toString() ?: sm.searchEngineId
                }
            }
        }

        sm.chatApiUrl = findSettingsView<TextView>(R.id.et_chat_api_url)?.text?.toString() ?: sm.chatApiUrl
        sm.chatApiKey = findSettingsView<TextView>(R.id.et_chat_api_key)?.text?.toString() ?: sm.chatApiKey
        sm.chatModel = findSettingsView<TextView>(R.id.et_chat_model)?.text?.toString() ?: sm.chatModel
        sm.screenApiUrl = findSettingsView<TextView>(R.id.et_screen_api_url)?.text?.toString() ?: sm.screenApiUrl
        sm.screenModel = findSettingsView<TextView>(R.id.et_screen_model)?.text?.toString() ?: sm.screenModel
        sm.asrApiUrl = findSettingsView<TextView>(R.id.et_asr_api_url)?.text?.toString() ?: sm.asrApiUrl
        sm.asrApiKey = findSettingsView<TextView>(R.id.et_asr_api_key)?.text?.toString() ?: sm.asrApiKey
        sm.asrModel = findSettingsView<TextView>(R.id.et_asr_model)?.text?.toString() ?: sm.asrModel
        sm.ttsApiUrl = findSettingsView<TextView>(R.id.et_tts_api_url)?.text?.toString() ?: sm.ttsApiUrl
        sm.ttsApiKey = findSettingsView<TextView>(R.id.et_tts_api_key)?.text?.toString() ?: sm.ttsApiKey
        sm.ttsModel = findSettingsView<TextView>(R.id.et_tts_model)?.text?.toString() ?: sm.ttsModel
        sm.ttsVoiceName = findSettingsView<TextView>(R.id.et_tts_voice_name)?.text?.toString() ?: sm.ttsVoiceName
        sm.userId = findSettingsView<TextView>(R.id.et_user_id)?.text?.toString() ?: sm.userId
        sm.userBirthday = findSettingsView<TextView>(R.id.et_user_birthday)?.text?.toString() ?: sm.userBirthday
        sm.userAppearance = findSettingsView<TextView>(R.id.et_user_appearance)?.text?.toString() ?: sm.userAppearance

        sm.screenRecognitionEnabled = findSettingsView<Switch>(R.id.switch_screen_recognition)?.isChecked ?: sm.screenRecognitionEnabled
        sm.simpleScreenMode = findSettingsView<Switch>(R.id.switch_simple_screen_mode)?.isChecked ?: sm.simpleScreenMode
        sm.useChatModelForVision = findSettingsView<Switch>(R.id.switch_chat_model_vision)?.isChecked ?: sm.useChatModelForVision
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
        vwMgr.imageApiSecretKey = findSettingsView<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_api_secret_key)?.text?.toString() ?: vwMgr.imageApiSecretKey
        vwMgr.imageModel = findSettingsView<com.google.android.material.textfield.TextInputEditText>(R.id.et_image_model)?.text?.toString() ?: vwMgr.imageModel

        sm.emotionAnalysisEnabled = findSettingsView<Switch>(R.id.switch_emotion_analysis)?.isChecked ?: sm.emotionAnalysisEnabled

        com.aicompanion.util.AppLogger.enabled = sm.appLoggingEnabled
        com.aicompanion.util.AppLogger.debugVerbose = sm.appDebugVerbose
    }

    private fun setupTtsProviderSpinner() {
        val presets = ServicePresets.ttsPresets
        val names = presets.map { it.displayName }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTtsProvider?.adapter = adapter

        val savedId = settingsManager?.ttsProvider ?: "custom"
        spinnerTtsProvider?.setSelection(ServicePresets.getTtsIndex(savedId))

        spinnerTtsProvider?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = presets[position]
                if (isTtsProviderInitialized) {
                    if (preset.url.isNotEmpty()) {
                        etTtsApiUrl?.setText(preset.url)
                        val currentModel = etTtsModel?.text?.toString()?.trim() ?: ""
                        if (currentModel.isEmpty() || currentModel in ttsKnownDefaults) {
                            etTtsModel?.setText(preset.defaultModel)
                        }
                        val currentVoice = etTtsVoiceName?.text?.toString()?.trim() ?: ""
                        if (currentVoice.isEmpty() || currentVoice in setOf("alloy", "echo", "fable", "onyx", "nova", "shimmer", "longxiaochun", "default", "FunAudioLLM/CosyVoice2-0.5B:alex")) {
                            if (preset.defaultVoice.isNotEmpty()) etTtsVoiceName?.setText(preset.defaultVoice)
                        }
                    }
                }
                settingsManager?.ttsProvider = preset.id
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerTtsProvider?.post { isTtsProviderInitialized = true }
    }

    private fun setupAsrProviderSpinner() {
        val presets = ServicePresets.asrPresets
        val names = presets.map { it.displayName }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAsrProvider?.adapter = adapter

        val savedId = settingsManager?.asrProvider ?: "custom"
        spinnerAsrProvider?.setSelection(ServicePresets.getAsrIndex(savedId))

        spinnerAsrProvider?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = presets[position]
                if (isAsrProviderInitialized) {
                    if (preset.url.isNotEmpty()) {
                        etAsrApiUrl?.setText(preset.url)
                        val currentModel = etAsrModel?.text?.toString()?.trim() ?: ""
                        if (currentModel.isEmpty() || currentModel in asrKnownDefaults) {
                            etAsrModel?.setText(preset.defaultModel)
                        }
                    }
                }
                settingsManager?.asrProvider = preset.id
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerAsrProvider?.post { isAsrProviderInitialized = true }
    }

    private fun setupImageGenProviderSpinner() {
        val presets = ServicePresets.imageGenPresets
        val names = presets.map { it.displayName }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerImageGenProvider?.adapter = adapter

        val savedId = settingsManager?.imageGenProvider ?: "custom"
        spinnerImageGenProvider?.setSelection(ServicePresets.getImageGenIndex(savedId))

        spinnerImageGenProvider?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = presets[position]
                if (isImageGenProviderInitialized) {
                    if (preset.url.isNotEmpty()) {
                        etImageApiUrl?.setText(preset.url)
                        val currentModel = etImageModel?.text?.toString()?.trim() ?: ""
                        if (currentModel.isEmpty() || currentModel in imageGenKnownDefaults) {
                            etImageModel?.setText(preset.defaultModel)
                        }
                    }
                }
                settingsManager?.imageGenProvider = preset.id
                if (preset.id == "aliyun_kling") {
                    tvImageKeyHint?.visibility = View.VISIBLE
                    findSettingsView<com.google.android.material.textfield.TextInputLayout>(R.id.layout_image_api_secret_key)?.visibility = View.VISIBLE
                } else {
                    tvImageKeyHint?.visibility = View.GONE
                    findSettingsView<com.google.android.material.textfield.TextInputLayout>(R.id.layout_image_api_secret_key)?.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerImageGenProvider?.post { isImageGenProviderInitialized = true }
    }

    private fun setupImageRecogProviderSpinner() {
        val presets = ServicePresets.imageRecogPresets
        val names = presets.map { it.displayName }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerImageRecogProvider?.adapter = adapter

        val savedId = settingsManager?.imageRecogProvider ?: "custom"
        spinnerImageRecogProvider?.setSelection(ServicePresets.getImageRecogIndex(savedId))

        spinnerImageRecogProvider?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = presets[position]
                if (isImageRecogProviderInitialized) {
                    if (preset.url.isNotEmpty()) {
                        etScreenApiUrl?.setText(preset.url)
                        val currentModel = etScreenModel?.text?.toString()?.trim() ?: ""
                        if (currentModel.isEmpty() || currentModel in imageRecogKnownDefaults) {
                            etScreenModel?.setText(preset.defaultModel)
                        }
                    }
                }
                settingsManager?.imageRecogProvider = preset.id
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerImageRecogProvider?.post { isImageRecogProviderInitialized = true }
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
