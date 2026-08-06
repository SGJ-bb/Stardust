package com.aicompanion.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aicompanion.models.Emotion
import com.aicompanion.network.ApiClient
import com.aicompanion.persona.PersonaManager
import com.aicompanion.R
import com.aicompanion.settings.SettingsManager
import com.aicompanion.util.AppLogger
import com.aicompanion.voice.LocalAsrManager
import com.aicompanion.voice.TtsManager
import com.aicompanion.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================
// 文件级颜色常量 - 避免硬编码颜色值
// ============================================
private val COLOR_BG_BASE = 0xFF080818.toInt()                    // 主背景色（深蓝黑）
private val COLOR_BG_GRADIENT_START = 0xFF0F0F2E.toInt()           // 渐变背景起始色
private val COLOR_BG_GRADIENT_MID = 0xFF080818.toInt()             // 渐变背景中间色
private val COLOR_BG_GRADIENT_END = 0xFF050510.toInt()             // 渐变背景结束色
private val COLOR_AMBIENT_GLOW_START = 0x1A7C4DFF.toInt()          // 环境光渐变起始（半透明紫色）
private val COLOR_AMBIENT_GLOW_END = 0x00000000.toInt()            // 环境光渐变结束（透明）
private val COLOR_TRANSPARENT = 0x00000000.toInt()                // 完全透明色
private val COLOR_PULSE_RING_BASE = 0x0D81D4FA.toInt()             // 脉冲环基础色（半透明青蓝）
private val COLOR_PULSE_RING_INCREMENT = 0x0581D4FA.toInt()        // 脉冲环颜色增量
private val COLOR_AVATAR_BG = 0xFF1A1A3E.toInt()                   // 头像背景色
private val COLOR_AVATAR_STROKE = 0xFF7C4DFF.toInt()               // 头像描边色（紫色）
private val COLOR_NAME_TEXT = 0xFFFFFFFF.toInt()                   // 名称文字颜色（白色）
private val COLOR_STATUS_DOT = 0xFF4CAF50.toInt()                  // 状态点颜色（绿色）
private val COLOR_STATUS_TEXT = 0xFF81D4FA.toInt()                 // 状态文字颜色（浅蓝）
private val COLOR_DURATION_TEXT = 0xFF666688.toInt()               // 时长文字颜色
private val COLOR_TRANSCRIPT_TEXT = 0xFFCCCCDD.toInt()             // 转录文字颜色
private val COLOR_TRANSCRIPT_BG = 0x0DFFFFFF.toInt()               // 转录背景色（半透明白）
private val COLOR_BUTTON_BG = 0xFF2A2A4A.toInt()                   // 控制按钮背景色
private val COLOR_BUTTON_LABEL = 0xFF8888AA.toInt()                 // 控制按钮标签颜色
private val COLOR_HANGUP_BG = 0xFFD32F2F.toInt()                   // 挂断按钮背景色（红色）
private val COLOR_HANGUP_LABEL = 0xFFCCCCCC.toInt()               // 挂断按钮标签颜色
private val COLOR_MUTED_BG = 0xFFE53935.toInt()                    // 静音激活状态背景色
// VoiceWaveformView 颜色常量
private val COLOR_WAVE_LISTENING_BASE = intArrayOf(0x64, 0xFF, 0xDA)  // 聆听模式波形 RGB
private val COLOR_WAVE_AI_SPEAKING_BASE = intArrayOf(0x7C, 0x4D, 0xFF) // AI说话模式波形 RGB
private val COLOR_WAVE_MUTED = Color.argb(0x40, 0x88, 0x88, 0x88)       // 静音模式波形
private val COLOR_WAVE_IDLE = Color.argb(0x30, 0x55, 0x55, 0x77)       // 默认模式波形

class PhoneCallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PERSONA_ID = "persona_id"
        const val EXTRA_PERSONA_NAME = "persona_name"
        const val EXTRA_SCOPE = "scope"
        const val EXTRA_SCOPE_ID = "scope_id"
        private const val TAG = "PhoneCall"
        private const val REQUEST_RECORD_AUDIO = 2001
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var personaManager: PersonaManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var ttsManager: TtsManager
    private lateinit var asrManager: LocalAsrManager
    private lateinit var audioManager: AudioManager

    private var personaId = ""
    private var personaName = ""
    private var scope = "persona"
    private var scopeId = ""

    private var isListening = false
    private var isAiSpeaking = false
    private var isCallActive = false
    private var callDuration = 0L
    private var callStartTime = 0L
    private var hasAudioPermission = false
    private var asrErrorCount = 0
    private var silenceCount = 0
    private var silenceRunnable: Runnable? = null
    private val SILENCE_TIMEOUT_MS = 8000L
    private val MAX_SILENCE_COUNT = 3

    private var ivAvatar: ImageView? = null
    private var tvName: TextView? = null
    private var tvStatus: TextView? = null
    private var tvDuration: TextView? = null
    private var tvTranscript: TextView? = null
    private var btnMute: View? = null
    private var btnSpeaker: View? = null
    private var btnHangup: View? = null
    private var waveformView: VoiceWaveformView? = null
    private var avatarContainer: FrameLayout? = null

    private val pulseRings = mutableListOf<View>()
    private val pulseAnimators = mutableListOf<ObjectAnimator>()

    private var isMuted = false
    private var isSpeakerOn = true

    private var ttsCompletionRunnable: Runnable? = null

    @Volatile
    private var isDestroyedFlag = false

    @Volatile
    private var isHandlingSilence = false

    private var audioFocusRequest: Any? = null

    private val callHistory = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Boolean>>()

    private val durationRunnable = object : Runnable {
        override fun run() {
            if (isCallActive) {
                callDuration = System.currentTimeMillis() - callStartTime
                val mins = (callDuration / 60000).toInt()
                val secs = ((callDuration % 60000) / 1000).toInt()
                tvDuration?.text = String.format("%02d:%02d", mins, secs)
                tvDuration?.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持屏幕常亮（所有版本）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 锁屏显示 - 使用新 API（API 27+），旧版使用废弃 Flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        personaId = intent.getStringExtra(EXTRA_PERSONA_ID) ?: ""
        personaName = intent.getStringExtra(EXTRA_PERSONA_NAME) ?: "星尘"
        scope = intent.getStringExtra(EXTRA_SCOPE) ?: "persona"
        scopeId = intent.getStringExtra(EXTRA_SCOPE_ID) ?: personaId

        settingsManager = SettingsManager(this)
        personaManager = PersonaManager(this)
        voiceManager = VoiceManager(this)
        ttsManager = TtsManager(this)
        asrManager = LocalAsrManager(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        if (personaId.isBlank()) {
            personaId = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default") ?: "default"
            if (scopeId.isBlank()) scopeId = personaId
        }

        // 名称太短或是默认值时，从 PersonaManager 获取真实名称
        if (personaName.isBlank() || personaName == "星尘" || personaName.length <= 1) {
            try {
                val persona = personaManager.getPersona(personaId)
                if (persona != null) personaName = persona.name
            } catch (_: Exception) {}
        }

        asrManager.setListener(object : com.aicompanion.voice.AsrListener {
            override fun onPartialResult(text: String) {
                cancelSilenceTimer()
                silenceCount = 0
                tvTranscript?.text = "你: $text"
            }
            override fun onFinalResult(text: String) {
                cancelSilenceTimer()
                isListening = false
                asrErrorCount = 0
                if (isHandlingSilence) {
                    isHandlingSilence = false
                }
                if (isCallActive && text.isNotBlank()) processUserSpeech(text)
            }
            override fun onError(error: String) {
                isListening = false
                asrErrorCount++
                if (isCallActive) {
                    if (asrErrorCount >= 3) {
                        tvStatus?.text = "语音识别异常，请在设置中配置云端ASR"
                        tvTranscript?.text = "提示：设置→语音识别→填写API地址和密钥"
                    } else {
                        tvStatus?.text = "通话中"
                        tvStatus?.postDelayed({ startListeningCycle() }, 1500)
                    }
                }
            }
            override fun onReady() {
                tvStatus?.text = "正在聆听..."
            }
            override fun onEndOfSpeech() {
                if (isCallActive && !isAiSpeaking) {
                    tvStatus?.text = "处理中..."
                }
            }
        })

        buildUI()
        checkAndRequestAudioPermission()
    }

    private fun checkAndRequestAudioPermission() {
        hasAudioPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO)) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("需要麦克风权限")
                    .setMessage("语音通话需要使用麦克风来识别你的语音，请授予权限。")
                    .setPositiveButton("授权") { _, _ ->
                        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        tvTranscript?.text = "需要麦克风权限才能进行语音通话"
                        tvStatus?.text = "权限未授予"
                    }
                    .setCancelable(false)
                    .show()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            }
        } else {
            startCall()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            hasAudioPermission = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (hasAudioPermission) {
                startCall()
            } else {
                tvTranscript?.text = "需要麦克风权限才能进行语音通话\n请在系统设置中手动开启"
                tvStatus?.text = "权限未授予"
                tvStatus?.postDelayed({ hangUp() }, 3000)
            }
        }
    }

    private fun buildUI() {
        val density = resources.displayMetrics.density
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG_BASE)
        }

        val bgGradient = View(this).apply {
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(COLOR_BG_GRADIENT_START, COLOR_BG_GRADIENT_MID, COLOR_BG_GRADIENT_END)
            )
            background = drawable
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        rootLayout.addView(bgGradient)

        val ambientGlow = View(this).apply {
            val size = (300 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                topMargin = (30 * density).toInt()
                leftMargin = (-30 * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(COLOR_AMBIENT_GLOW_START, COLOR_AMBIENT_GLOW_END)
                gradientType = GradientDrawable.RADIAL_GRADIENT
                setGradientRadius(150 * density)
            }
        }
        rootLayout.addView(ambientGlow)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding((24 * density).toInt(), (50 * density).toInt(), (24 * density).toInt(), (30 * density).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        avatarContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((200 * density).toInt(), (200 * density).toInt()).apply {
                topMargin = (10 * density).toInt()
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }

        for (i in 0 until 4) {
            val ringSize = (120 + i * 25) * density.toInt()
            val ring = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(ringSize, ringSize).apply {
                    gravity = android.view.Gravity.CENTER
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(COLOR_TRANSPARENT)
                    setStroke((1.5 * density).toInt(), (COLOR_PULSE_RING_BASE + i * COLOR_PULSE_RING_INCREMENT))
                }
                alpha = 0f
            }
            pulseRings.add(ring)
            avatarContainer?.addView(ring)
        }

        ivAvatar = ImageView(this).apply {
            val size = (90 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_AVATAR_BG)
                setStroke((2 * density).toInt(), COLOR_AVATAR_STROKE)
            }
            background = cardBg
            setImageResource(R.drawable.ic_avatar_default_ai)
        }
        avatarContainer?.addView(ivAvatar)

        loadAvatar()
        contentLayout.addView(avatarContainer)

        waveformView = VoiceWaveformView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (60 * density).toInt()).apply {
                topMargin = (16 * density).toInt()
            }
        }
        contentLayout.addView(waveformView)

        tvName = TextView(this).apply {
            text = personaName
            setTextColor(COLOR_NAME_TEXT)
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (12 * density).toInt()
            }
        }
        contentLayout.addView(tvName)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * density).toInt()
            }

            val dot = View(context).apply {
                val dotSize = (6 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginEnd = (6 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(COLOR_STATUS_DOT)
                }
            }
            addView(dot)

            tvStatus = TextView(this@PhoneCallActivity).apply {
                text = "正在接听..."
                setTextColor(COLOR_STATUS_TEXT)
                textSize = 13f
                gravity = android.view.Gravity.CENTER
            }
            addView(tvStatus)
        }
        contentLayout.addView(statusRow)

        tvDuration = TextView(this).apply {
            text = "00:00"
            setTextColor(COLOR_DURATION_TEXT)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * density).toInt()
            }
        }
        contentLayout.addView(tvDuration)

        tvTranscript = TextView(this).apply {
            setTextColor(COLOR_TRANSCRIPT_TEXT)
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            maxLines = 4
            setLineSpacing(6f, 1f)
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply {
                weight = 1f
                topMargin = (16 * density).toInt()
                bottomMargin = (16 * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(COLOR_TRANSCRIPT_BG)
                setCornerRadius(16 * density)
            }
        }
        contentLayout.addView(tvTranscript)

        val controlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (16 * density).toInt()
            }
        }

        btnMute = createControlButton("🎤", "静音", COLOR_BUTTON_BG) { toggleMute() }
        controlsLayout.addView(btnMute)

        val spacer1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), 1)
        }
        controlsLayout.addView(spacer1)

        btnHangup = createHangupButton()
        controlsLayout.addView(btnHangup)

        val spacer2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), 1)
        }
        controlsLayout.addView(spacer2)

        btnSpeaker = createControlButton("🔊", "扬声器", COLOR_BUTTON_BG) { toggleSpeaker() }
        controlsLayout.addView(btnSpeaker)

        contentLayout.addView(controlsLayout)
        rootLayout.addView(contentLayout)
        setContentView(rootLayout)
    }

    private fun createControlButton(icon: String, label: String, bgColor: Int, onClick: () -> Unit): View {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val w = (60 * density).toInt()
            val h = (64 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(w, h)

            val iconTv = TextView(this@PhoneCallActivity).apply {
                text = icon
                textSize = 20f
                gravity = android.view.Gravity.CENTER
            }

            val labelTv = TextView(this@PhoneCallActivity).apply {
                text = label
                setTextColor(COLOR_BUTTON_LABEL)
                textSize = 9f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * density).toInt()
                }
            }

            addView(iconTv)
            addView(labelTv)

            background = GradientDrawable().apply {
                setColor(bgColor)
                setCornerRadius(14 * density)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHangupButton(): View {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val w = (72 * density).toInt()
            val h = (72 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(w, h)

            val iconTv = TextView(this@PhoneCallActivity).apply {
                text = "📞"
                textSize = 24f
                gravity = android.view.Gravity.CENTER
            }

            val labelTv = TextView(this@PhoneCallActivity).apply {
                text = "挂断"
                setTextColor(COLOR_HANGUP_LABEL)
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * density).toInt()
                }
            }

            addView(iconTv)
            addView(labelTv)

            background = GradientDrawable().apply {
                setColor(COLOR_HANGUP_BG)
                setCornerRadius(18 * density)
            }
            elevation = 6 * density
            isClickable = true
            isFocusable = true
            setOnClickListener { hangUp() }

            scaleX = 0f
            scaleY = 0f
            animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator()).setDuration(400).start()
        }
    }

    private fun loadAvatar() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val persona = personaManager.getPersona(personaId)
                if (persona != null && persona.avatarPath.isNotBlank()) {
                    val file = java.io.File(persona.avatarPath)
                    if (file.exists()) {
                        val bmp = decodeSampledBitmap(persona.avatarPath, 256, 256)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) { ivAvatar?.setImageBitmap(bmp) }
                            return@launch
                        }
                    }
                }
                val prefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)
                val path = prefs.getString("persona_avatar_path", "")
                if (!path.isNullOrBlank()) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val bmp = decodeSampledBitmap(path, 256, 256)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) { ivAvatar?.setImageBitmap(bmp) }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "loadAvatar: ${e.message}")
            }
        }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, reqWidth, reqHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (width > reqWidth || height > reqHeight) {
            val halfW = width / 2
            val halfH = height / 2
            while (halfW / inSampleSize >= reqWidth && halfH / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                if (isCallActive) {
                                    if (isAiSpeaking) {
                                        cancelTtsCompletionRunnable()
                                        voiceManager.setOnUtteranceCompleteListener(null)
                                        ttsManager.stopPlayback()
                                        voiceManager.stopSpeaking()
                                        isAiSpeaking = false
                                    }
                                    if (isListening) {
                                        asrManager.cancel()
                                        isListening = false
                                    }
                                }
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                if (isCallActive && !isAiSpeaking && !isMuted && hasAudioPermission) {
                                    startListeningCycle()
                                }
                            }
                        }
                    }
                    .build()
                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                audioManager.requestAudioFocus(
                    { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                if (isCallActive) {
                                    if (isAiSpeaking) {
                                        cancelTtsCompletionRunnable()
                                        voiceManager.setOnUtteranceCompleteListener(null)
                                        ttsManager.stopPlayback()
                                        voiceManager.stopSpeaking()
                                        isAiSpeaking = false
                                    }
                                    if (isListening) {
                                        asrManager.cancel()
                                        isListening = false
                                    }
                                }
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                if (isCallActive && !isAiSpeaking && !isMuted && hasAudioPermission) {
                                    startListeningCycle()
                                }
                            }
                        }
                    },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "requestAudioFocus: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
            } else {
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
        audioFocusRequest = null
    }

    private fun startCall() {
        isCallActive = true
        callStartTime = System.currentTimeMillis()
        tvDuration?.post(durationRunnable)
        tvStatus?.text = "通话中"

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = isSpeakerOn
        requestAudioFocus()

        ivAvatar?.animate()?.scaleX(1.05f)?.scaleY(1.05f)?.setDuration(600)?.withEndAction {
            ivAvatar?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(400)?.start()
        }?.start()

        sendAiGreeting()
    }

    private fun sendAiGreeting() {
        waveformView?.setMode(VoiceWaveformView.MODE_AI_SPEAKING)
        startPulseAnimation()
        lifecycleScope.launch {
            try {
                val greeting = withContext(Dispatchers.IO) {
                    callLlm("用户给你打了电话，请简短地打个招呼回应，就像接电话一样自然。不要超过两句话。")
                }
                if (greeting.isNotBlank() && isCallActive) {
                    tvTranscript?.text = "$personaName: $greeting"
                    speakAi(greeting)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "sendAiGreeting: ${e.message}")
                tvTranscript?.text = "$personaName: 喂？你好呀～"
                speakAi("喂？你好呀～")
            }
        }
    }

    private fun startSilenceTimer() {
        cancelSilenceTimer()
        val runnable = Runnable {
            if (isCallActive && isListening && !isAiSpeaking && !isMuted) {
                silenceCount++
                if (silenceCount <= MAX_SILENCE_COUNT) {
                    handleSilence()
                }
            }
        }
        silenceRunnable = runnable
        tvDuration?.postDelayed(runnable, SILENCE_TIMEOUT_MS)
    }

    private fun cancelSilenceTimer() {
        silenceRunnable?.let { tvDuration?.removeCallbacks(it) }
        silenceRunnable = null
    }

    private fun handleSilence() {
        if (!isCallActive || isAiSpeaking) return
        isHandlingSilence = true
        isListening = false
        asrManager.stopListening()
        val prompts = listOf(
            "用户沉默了一会儿，你主动说点什么来打破沉默，要简短自然，就像打电话时对方不说话你会说的那种话。一句话就好。",
            "用户还是没有说话，再试着说点什么，可以问个问题引导对方说话。一句话就好。",
            "用户似乎在想事情，温柔地说一句表示理解的话。一句话就好。"
        )
        val prompt = prompts[(silenceCount - 1).coerceIn(0, prompts.size - 1)]
        tvStatus?.text = "$personaName 正在说话..."
        waveformView?.setMode(VoiceWaveformView.MODE_AI_SPEAKING)
        startPulseAnimation()
        lifecycleScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { callLlm(prompt) }
                if (reply.isNotBlank() && isCallActive) {
                    tvTranscript?.text = "$personaName: $reply"
                    isHandlingSilence = false
                    speakAi(reply)
                } else if (isCallActive) {
                    startListeningCycle()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "handleSilence: ${e.message}")
                if (isCallActive) startListeningCycle()
            }
        }
    }

    private fun startListeningCycle() {
        if (!isCallActive || isAiSpeaking || isMuted || !hasAudioPermission) return

        isListening = true
        waveformView?.setMode(VoiceWaveformView.MODE_LISTENING)
        asrManager.startListening()
        startSilenceTimer()
    }

    private fun processUserSpeech(userText: String) {
        if (!isCallActive) return

        if (userText.isBlank()) {
            tvStatus?.text = "通话中"
            waveformView?.setMode(VoiceWaveformView.MODE_IDLE)
            startListeningCycle()
            return
        }

        tvTranscript?.text = "你: $userText"
        silenceCount = 0
        tvStatus?.text = "$personaName 正在说话..."
        waveformView?.setMode(VoiceWaveformView.MODE_AI_SPEAKING)
        startPulseAnimation()

        lifecycleScope.launch {
            try {
                val aiReply = withContext(Dispatchers.IO) { callLlm(userText) }
                if (aiReply.isNotBlank() && isCallActive) {
                    tvTranscript?.text = "$personaName: $aiReply"
                    speakAi(aiReply)
                } else if (isCallActive) {
                    tvTranscript?.text = "$personaName: 嗯..."
                    speakAi("嗯")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "processUserSpeech: ${e.message}")
                if (isCallActive) {
                    tvStatus?.text = "通话中"
                    waveformView?.setMode(VoiceWaveformView.MODE_IDLE)
                    startListeningCycle()
                }
            }
        }
    }

    private suspend fun callLlm(userText: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = ApiClient(
                    settingsManager.chatApiUrl, settingsManager.chatApiKey,
                    settingsManager.chatModel, settingsManager.llmTemperature,
                    settingsManager.llmTopP, settingsManager.llmFrequencyPenalty,
                    settingsManager.llmPresencePenalty, settingsManager.llmMaxTokens,
                    settingsManager.apiProvider
                )
                val persona = personaManager.getPersona(personaId)
                val pName = persona?.name ?: this@PhoneCallActivity.personaName
                val pPrompt = buildString {
                    append("你正在和用户进行语音通话。请用简短、口语化的方式回复，就像在打电话一样自然。回复不要超过三句话，保持对话流畅。")
                    if (persona != null) {
                        append("\n你的名字是${persona.name}。")
                        if (persona.personality.isNotBlank()) append("\n你的性格：${persona.personality}")
                    }
                }
                val history = callHistory.takeLast(10).map { (text, isUser) ->
                    isUser to text
                }
                val response = client.sendChat(
                    userId = "phone_user", message = userText,
                    personaName = pName, personaPrompt = pPrompt,
                    emotion = "NEUTRAL", action = "IDLE",
                    memories = emptyList(), appCategory = "phone_call",
                    chatHistory = history
                )
                val replyText = response?.text?.replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "")?.trim() ?: ""
                if (replyText.isNotBlank()) {
                    callHistory.add(userText to true)
                    callHistory.add(replyText to false)
                }
                replyText
            } catch (e: Exception) {
                AppLogger.e(TAG, "callLlm: ${e.message}")
                ""
            }
        }
    }

    private fun speakAi(text: String) {
        if (!isCallActive) return
        isAiSpeaking = true
        tvStatus?.text = "$personaName 正在说话..."
        startPulseAnimation()

        cancelTtsCompletionRunnable()

        val persona = try { personaManager.getPersona(personaId) } catch (_: Exception) { null }
        val personaVoice = persona?.ttsVoice?.takeIf { it.isNotBlank() }
        val personaPitch = persona?.ttsPitch?.takeIf { it != 0f }
        val personaRate = persona?.ttsRate?.takeIf { it != 0f }

        val engineMode = ttsManager.engineMode
        val useLocalTts = engineMode == TtsManager.ENGINE_LOCAL ||
            (engineMode != TtsManager.ENGINE_EDGE && !ttsManager.isCloudConfigured)

        if (useLocalTts) {
            val pitchOffset = (personaPitch ?: 0f) - settingsManager.ttsPitch
            val rateOffset = (personaRate ?: 0f) - settingsManager.ttsRate
            voiceManager.speak(text, Emotion.NEUTRAL, pitchOffset, rateOffset)
            scheduleLocalTtsCompletion(text)
            return
        }

        lifecycleScope.launch {
            try {
                val overrideVoice = personaVoice
                val result = withContext(Dispatchers.IO) {
                    ttsManager.synthesizeWithVoice(text, overrideVoice)
                }
                if (isFinishing || isDestroyedFlag) return@launch
                if (result.success && (result.audioPath != null || result.audioUrl != null)) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyedFlag && isCallActive) {
                            ttsManager.playAudio(result.audioPath, result.audioUrl) {
                                if (!isFinishing && !isDestroyedFlag && isCallActive) {
                                    onAiSpeechComplete()
                                }
                            }
                            scheduleCloudTtsCompletion()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyedFlag && isCallActive) {
                            val pitchOffset = (personaPitch ?: 0f) - settingsManager.ttsPitch
                            val rateOffset = (personaRate ?: 0f) - settingsManager.ttsRate
                            voiceManager.speak(text, Emotion.NEUTRAL, pitchOffset, rateOffset)
                            scheduleLocalTtsCompletion(text)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "speakAi: ${e.message}")
                if (!isFinishing && !isDestroyedFlag && isCallActive) {
                    voiceManager.speak(text)
                    scheduleLocalTtsCompletion(text)
                }
            }
        }
    }

    private fun cancelTtsCompletionRunnable() {
        ttsCompletionRunnable?.let { tvDuration?.removeCallbacks(it) }
        ttsCompletionRunnable = null
    }

    private fun scheduleLocalTtsCompletion(text: String) {
        cancelTtsCompletionRunnable()
        try {
            voiceManager.setOnUtteranceCompleteListener {
                if (isCallActive && isAiSpeaking && !isFinishing && !isDestroyedFlag) {
                    tvDuration?.post {
                        if (isCallActive && !isFinishing && !isDestroyedFlag) {
                            onAiSpeechComplete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "OnUtteranceProgressListener not supported, fallback to estimation")
        }
        val estimatedMs = (text.length * 200L).coerceIn(2000, 30000)
        val runnable = Runnable {
            if (isCallActive && isAiSpeaking && !isFinishing && !isDestroyedFlag) {
                onAiSpeechComplete()
            }
        }
        ttsCompletionRunnable = runnable
        tvDuration?.postDelayed(runnable, estimatedMs)
    }

    private fun scheduleCloudTtsCompletion() {
        cancelTtsCompletionRunnable()
        val runnable = Runnable {
            if (isCallActive && isAiSpeaking && !isFinishing && !isDestroyedFlag) {
                ttsManager.stopPlayback()
                onAiSpeechComplete()
            }
        }
        ttsCompletionRunnable = runnable
        tvDuration?.postDelayed(runnable, 30000)
    }

    private fun onAiSpeechComplete() {
        if (!isCallActive || !isAiSpeaking) return
        cancelTtsCompletionRunnable()
        voiceManager.setOnUtteranceCompleteListener(null)
        isAiSpeaking = false
        silenceCount = 0
        tvStatus?.text = "通话中"
        stopPulseAnimation()
        waveformView?.setMode(VoiceWaveformView.MODE_IDLE)
        startListeningCycle()
    }

    private fun startPulseAnimation() {
        try {
            pulseAnimators.forEach { it.cancel() }
            pulseAnimators.clear()

            pulseRings.forEachIndexed { i, ring ->
                val anim = ObjectAnimator.ofFloat(ring, "alpha", 0f, 0.4f - i * 0.08f, 0f).apply {
                    duration = (1200 + i * 300).toLong()
                    repeatCount = ValueAnimator.INFINITE
                    startDelay = (i * 150).toLong()
                    start()
                }
                pulseAnimators.add(anim)
            }

            val scaleUpX = ObjectAnimator.ofFloat(ivAvatar, "scaleX", 1f, 1.06f)
            val scaleUpY = ObjectAnimator.ofFloat(ivAvatar, "scaleY", 1f, 1.06f)
            val scaleDownX = ObjectAnimator.ofFloat(ivAvatar, "scaleX", 1.06f, 1f)
            val scaleDownY = ObjectAnimator.ofFloat(ivAvatar, "scaleY", 1.06f, 1f)

            val scaleUp = AnimatorSet().apply {
                playTogether(scaleUpX, scaleUpY)
                duration = 800
            }
            val scaleDown = AnimatorSet().apply {
                playTogether(scaleDownX, scaleDownY)
                duration = 800
            }
            val scaleSet = AnimatorSet().apply {
                playSequentially(scaleUp, scaleDown)
            }
            scaleSet.start()
        } catch (e: Exception) {
            AppLogger.e(TAG, "startPulseAnimation: ${e.message}")
        }
    }

    private fun stopPulseAnimation() {
        try {
            pulseAnimators.forEach { it.cancel() }
            pulseAnimators.clear()
            pulseRings.forEach { it.alpha = 0f }
            ivAvatar?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(300)?.start()
        } catch (_: Exception) {}
    }

    private fun toggleMute() {
        isMuted = !isMuted
        val btnLayout = btnMute as? LinearLayout ?: return
        val iconTv = btnLayout.getChildAt(0) as? TextView ?: return
        val labelTv = btnLayout.getChildAt(1) as? TextView ?: return
        val density = resources.displayMetrics.density

        if (isMuted) {
            iconTv.text = "🔇"
            labelTv.text = "已静音"
            btnLayout.background = GradientDrawable().apply {
                setColor(COLOR_MUTED_BG)
                setCornerRadius(14 * density)
            }
            if (isListening) {
                asrManager.stopListening()
                isListening = false
            }
            waveformView?.setMode(VoiceWaveformView.MODE_MUTED)
        } else {
            iconTv.text = "🎤"
            labelTv.text = "静音"
            btnLayout.background = GradientDrawable().apply {
                setColor(COLOR_BUTTON_BG)
                setCornerRadius(14 * density)
            }
            if (isCallActive && !isAiSpeaking) startListeningCycle()
        }
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = isSpeakerOn
        val btnLayout = btnSpeaker as? LinearLayout ?: return
        val iconTv = btnLayout.getChildAt(0) as? TextView ?: return
        val labelTv = btnLayout.getChildAt(1) as? TextView ?: return

        if (isSpeakerOn) {
            iconTv.text = "🔊"
            labelTv.text = "扬声器"
        } else {
            iconTv.text = "🔈"
            labelTv.text = "听筒"
        }
    }

    private fun hangUp() {
        isCallActive = false
        isAiSpeaking = false
        isListening = false
        isHandlingSilence = false

        cancelSilenceTimer()
        cancelTtsCompletionRunnable()
        asrManager.cancel()
        asrManager.cleanup()
        voiceManager.stopSpeaking()
        ttsManager.stopPlayback()

        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        abandonAudioFocus()
        stopPulseAnimation()
        waveformView?.setMode(VoiceWaveformView.MODE_IDLE)
        tvStatus?.text = "通话结束"
        tvDuration?.removeCallbacks(durationRunnable)

        tvStatus?.postDelayed({ finish() }, 800)
    }

    override fun onStop() {
        super.onStop()
        if (isCallActive) {
            asrManager.cancel()
            isListening = false
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (isCallActive && !isAiSpeaking && !isMuted && hasAudioPermission) {
            startListeningCycle()
        }
    }

    override fun onDestroy() {
        isCallActive = false
        isDestroyedFlag = true
        cancelSilenceTimer()
        cancelTtsCompletionRunnable()
        asrManager.cleanup()
        voiceManager.cleanup()
        ttsManager.cleanup()
        stopPulseAnimation()
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
        abandonAudioFocus()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        hangUp()
    }

    class VoiceWaveformView(context: android.content.Context) : View(context) {
        companion object {
            const val MODE_IDLE = 0
            const val MODE_LISTENING = 1
            const val MODE_AI_SPEAKING = 2
            const val MODE_MUTED = 3
        }

        private var mode = MODE_IDLE
        private var phase = 0f
        private val barCount = 40
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val barWidth = 3f * resources.displayMetrics.density
        private val barGap = 2f * resources.displayMetrics.density
        private var isAnimating = false

        fun setMode(m: Int) {
            val changed = mode != m
            mode = m
            if (changed) {
                val shouldAnimate = m == MODE_LISTENING || m == MODE_AI_SPEAKING
                if (shouldAnimate && !isAnimating) {
                    isAnimating = true
                    invalidate()
                } else if (!shouldAnimate) {
                    isAnimating = false
                    invalidate()
                } else {
                    invalidate()
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val totalWidth = barCount * (barWidth + barGap)
            val startX = (w - totalWidth) / 2f
            val centerY = h / 2f

            if (isAnimating) {
                phase += 0.08f
            }

            for (i in 0 until barCount) {
                val x = startX + i * (barWidth + barGap)
                val normalizedPos = (i.toFloat() / barCount - 0.5f) * 2f

                val amplitude = when (mode) {
                    MODE_LISTENING -> {
                        val wave = Math.sin((i + phase * 3).toDouble() * 0.5).toFloat()
                        val envelope = 1f - normalizedPos * normalizedPos
                        (h * 0.35f * envelope * (0.3f + 0.7f * Math.abs(wave))).coerceIn(4f * resources.displayMetrics.density, h * 0.4f)
                    }
                    MODE_AI_SPEAKING -> {
                        val wave1 = Math.sin((i * 0.3 + phase * 2.5).toDouble()).toFloat()
                        val wave2 = Math.sin((i * 0.7 + phase * 1.8).toDouble()).toFloat()
                        val envelope = 1f - normalizedPos * normalizedPos * 0.5f
                        (h * 0.3f * envelope * (0.4f + 0.6f * Math.abs(wave1 + wave2 * 0.5f))).coerceIn(4f * resources.displayMetrics.density, h * 0.4f)
                    }
                    MODE_MUTED -> {
                        3f * resources.displayMetrics.density
                    }
                    else -> {
                        val wave = Math.sin((i * 0.2 + phase * 0.5).toDouble()).toFloat()
                        (h * 0.05f * (0.5f + 0.5f * Math.abs(wave))).coerceIn(2f * resources.displayMetrics.density, h * 0.08f)
                    }
                }

                val color = when (mode) {
                    MODE_LISTENING -> {
                        val alpha = (0.4f + 0.6f * (amplitude / (h * 0.4f))).coerceIn(0f, 1f)
                        Color.argb((alpha * 255).toInt(), COLOR_WAVE_LISTENING_BASE[0], COLOR_WAVE_LISTENING_BASE[1], COLOR_WAVE_LISTENING_BASE[2])
                    }
                    MODE_AI_SPEAKING -> {
                        val alpha = (0.4f + 0.6f * (amplitude / (h * 0.4f))).coerceIn(0f, 1f)
                        Color.argb((alpha * 255).toInt(), COLOR_WAVE_AI_SPEAKING_BASE[0], COLOR_WAVE_AI_SPEAKING_BASE[1], COLOR_WAVE_AI_SPEAKING_BASE[2])
                    }
                    MODE_MUTED -> COLOR_WAVE_MUTED
                    else -> COLOR_WAVE_IDLE
                }

                paint.color = color
                val rect = RectF(x, centerY - amplitude / 2, x + barWidth, centerY + amplitude / 2)
                canvas.drawRoundRect(rect, barWidth / 2, barWidth / 2, paint)
            }

            if (isAnimating) {
                postInvalidateDelayed(30)
            }
        }
    }
}
