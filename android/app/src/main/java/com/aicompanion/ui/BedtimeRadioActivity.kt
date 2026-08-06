package com.aicompanion.ui

import com.aicompanion.R
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aicompanion.network.ApiClient
import com.aicompanion.persona.PersonaManager
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import com.aicompanion.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BedtimeRadioActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BedtimeRadio"
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var personaManager: PersonaManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var audioManager: AudioManager

    private var apiClient: ApiClient? = null

    private var isPlaying = false
    private var autoStopMinutes = 30
    private var autoStopRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private var ivMoon: ImageView? = null
    private var tvStatus: TextView? = null
    private var tvContent: TextView? = null
    private var btnPlay: View? = null
    private var spinnerTimer: Spinner? = null

    private var currentMode = "story"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settingsManager = SettingsManager(this)
        personaManager = PersonaManager(this)
        voiceManager = VoiceManager(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        buildUI()
    }

    private fun buildUI() {
        val density = resources.displayMetrics.density
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0A0A2E"))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = "星尘电台"
            setBackgroundColor(android.graphics.Color.parseColor("#0D0D3B"))
            setTitleTextColor(android.graphics.Color.WHITE)
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { finish() }
        }
        rootLayout.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (56 * density).toInt()
        ))

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((32 * density).toInt(), (40 * density).toInt(), (32 * density).toInt(), (32 * density).toInt())
        }

        ivMoon = ImageView(this).apply {
            setImageResource(android.R.drawable.btn_star_big_on)
            setColorFilter(android.graphics.Color.parseColor("#FFE4B5"))
            layoutParams = LinearLayout.LayoutParams((120 * density).toInt(), (120 * density).toInt())
        }
        contentLayout.addView(ivMoon)

        val tvTitle = TextView(this).apply {
            text = "晚安电台"
            setTextColor(android.graphics.Color.parseColor("#FFE4B5"))
            textSize = 24f
            gravity = Gravity.CENTER
        }
        contentLayout.addView(tvTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (16 * density).toInt() })

        tvStatus = TextView(this).apply {
            text = "选择一个模式，让星尘陪你入眠"
            setTextColor(android.graphics.Color.parseColor("#8888AA"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        contentLayout.addView(tvStatus, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * density).toInt() })

        tvContent = TextView(this).apply {
            text = ""
            setTextColor(android.graphics.Color.parseColor("#CCCCEE"))
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1f)
            setPadding(0, (24 * density).toInt(), 0, (24 * density).toInt())
        }
        contentLayout.addView(tvContent, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val modeLabel = TextView(this).apply {
            text = "陪伴模式"
            setTextColor(android.graphics.Color.parseColor("#8888AA"))
            textSize = 12f
        }
        contentLayout.addView(modeLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (16 * density).toInt() })

        val modeGrid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            useDefaultMargins = true
        }

        val modes = listOf(
            "📖 睡前故事" to "story",
            "🌙 晚安对话" to "chat",
            "🎵 助眠白噪音" to "whitenoise",
            "💭 碎碎念" to "ramble"
        )

        modes.forEach { (label, mode) ->
            val btn = TextView(this).apply {
                text = label
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt())
                setBackgroundColor(android.graphics.Color.parseColor("#1A1A4E"))
                setOnClickListener { startMode(mode) }
            }
            modeGrid.addView(btn, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                height = GridLayout.LayoutParams.WRAP_CONTENT
            })
        }
        contentLayout.addView(modeGrid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * density).toInt() })

        val timerLabel = TextView(this).apply {
            text = "定时关闭"
            setTextColor(android.graphics.Color.parseColor("#8888AA"))
            textSize = 12f
        }
        contentLayout.addView(timerLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (24 * density).toInt() })

        spinnerTimer = Spinner(this).apply {
            adapter = ArrayAdapter.createFromResource(this@BedtimeRadioActivity,
                com.aicompanion.R.array.bedtime_timer_options,
                R.layout.spinner_item_dark
            ).also {
                it.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
            }
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    autoStopMinutes = when (position) {
                        0 -> 15; 1 -> 30; 2 -> 45; 3 -> 60; 4 -> 90; else -> 0
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            setSelection(1)
        }
        contentLayout.addView(spinnerTimer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        btnPlay = TextView(this).apply {
            text = "▶ 开始播放"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding((32 * density).toInt(), (16 * density).toInt(), (32 * density).toInt(), (16 * density).toInt())
            setBackgroundColor(android.graphics.Color.parseColor("#667eea"))
            setOnClickListener {
                if (isPlaying) stopPlaying() else startPlaying()
            }
        }
        contentLayout.addView(btnPlay, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (24 * density).toInt() })

        val scrollView = ScrollView(this).apply { addView(contentLayout) }
        rootLayout.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(rootLayout)
    }

    private fun startMode(mode: String) {
        tvStatus?.text = when (mode) {
            "story" -> "📖 睡前故事模式"
            "chat" -> "🌙 晚安对话模式"
            "whitenoise" -> "🎵 白噪音模式"
            "ramble" -> "💭 碎碎念模式"
            else -> "星尘电台"
        }
        currentMode = mode
    }

    private fun startPlaying() {
        isPlaying = true
        (btnPlay as? TextView)?.text = "⏹ 停止播放"
        (btnPlay as? TextView)?.setBackgroundColor(android.graphics.Color.parseColor("#E53935"))
        tvStatus?.text = "正在播放..."

        apiClient = ApiClient(
            settingsManager.chatApiUrl, settingsManager.chatApiKey,
            settingsManager.chatModel, 0.7f, 0.9f,
            settingsManager.llmFrequencyPenalty, settingsManager.llmPresencePenalty,
            200, settingsManager.apiProvider
        )

        @Suppress("DEPRECATION")
        run {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
        }

        if (autoStopMinutes > 0) {
            autoStopRunnable = Runnable { stopPlaying() }
            handler.postDelayed(autoStopRunnable!!, autoStopMinutes * 60 * 1000L)
        }

        generateAndSpeak()
    }

    private fun stopPlaying() {
        isPlaying = false
        (btnPlay as? TextView)?.text = "▶ 开始播放"
        (btnPlay as? TextView)?.setBackgroundColor(android.graphics.Color.parseColor("#667eea"))
        tvStatus?.text = "已停止"
        voiceManager.stopSpeaking()
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        autoStopRunnable = null
        apiClient = null
    }

    private fun generateAndSpeak() {
        if (!isPlaying) return

        val personaId = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("active_persona_id", "default") ?: "default"
        val persona = try { personaManager.getPersona(personaId) } catch (_: Exception) { null }
        val name = persona?.name ?: "星尘"

        val prompt = when (currentMode) {
            "story" -> "请讲一个温柔的睡前小故事，适合入眠，要有美好的结局。故事要简短，200字以内。语气轻柔，像在耳边低语。"
            "chat" -> "用温柔的声音跟用户说晚安，聊聊今天的事，说一些温暖的话帮助入眠。要简短，100字以内。语气要轻柔。"
            "whitenoise" -> "用轻柔的声音描述一个安静的场景，比如下雨的夜晚、海边的微风、森林里的虫鸣，帮助用户放松入眠。100字以内。"
            "ramble" -> "随意说一些轻松的、无关紧要的话，像是在床边轻轻自言自语，帮助用户放松。可以聊聊星星、月亮、明天的期待。100字以内。"
            else -> "说一句温柔的晚安。"
        }

        lifecycleScope.launch {
            try {
                val fullPrompt = buildString {
                    append("你正在陪伴用户入睡。请用非常温柔、轻柔的语气说话，就像在耳边低语一样。语速要慢，声音要轻。\n")
                    append("你的名字是$name。\n")
                    append(prompt)
                }

                val client = apiClient ?: return@launch
                val response = withContext(Dispatchers.IO) {
                    client.sendChat(
                        userId = "bedtime_user",
                        message = fullPrompt,
                        personaName = name,
                        personaPrompt = "你正在陪伴用户入睡，语气温柔轻柔。",
                        emotion = "NEUTRAL",
                        action = "IDLE",
                        memories = emptyList(),
                        appCategory = "bedtime"
                    )
                }

                if (!isPlaying || isFinishing || isDestroyed) return@launch

                val text = response?.text?.replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "")?.trim()
                    ?: "晚安，好梦~"

                tvContent?.text = text
                voiceManager.speak(text, com.aicompanion.models.Emotion.SAD, -0.2f, -0.2f)

                if (isPlaying) {
                    val estimatedMs = (text.length * 300L).coerceIn(8000, 30000)
                    handler.postDelayed({ generateAndSpeak() }, estimatedMs + 5000)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "generateAndSpeak: ${e.message}")
                if (isPlaying) {
                    handler.postDelayed({ generateAndSpeak() }, 15000)
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopPlaying()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            tvStatus?.text = "后台播放中..."
        }
    }
}
