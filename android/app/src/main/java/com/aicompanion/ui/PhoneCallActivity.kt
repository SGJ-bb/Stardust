package com.aicompanion.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        personaId = intent.getStringExtra(EXTRA_PERSONA_ID) ?: ""
        personaName = intent.getStringExtra(EXTRA_PERSONA_NAME) ?: "星尘"
        scope = intent.getStringExtra(EXTRA_SCOPE) ?: "persona"
        scopeId = intent.getStringExtra(EXTRA_SCOPE_ID) ?: personaId

        settingsManager = SettingsManager(this)
        personaManager = PersonaManager(this)
        voiceManager = VoiceManager(this)
        ttsManager = TtsManager(this)
        asrManager = LocalAsrManager(this)

        if (personaId.isBlank()) {
            personaId = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default") ?: "default"
            scopeId = personaId
        }

        if (personaName == "星尘" && personaId.isNotBlank()) {
            try {
                val persona = personaManager.getPersona(personaId)
                if (persona != null) personaName = persona.name
            } catch (_: Exception) {}
        }

        asrManager.setListener(object : com.aicompanion.voice.AsrListener {
            override fun onPartialResult(text: String) {
                tvTranscript?.text = "你: $text"
            }
            override fun onFinalResult(text: String) {
                isListening = false
                asrErrorCount = 0
                if (isCallActive) processUserSpeech(text)
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
            setBackgroundColor(0xFF080818.toInt())
        }

        val bgGradient = View(this).apply {
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0F0F2E.toInt(), 0xFF080818.toInt(), 0xFF050510.toInt())
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
                colors = intArrayOf(0x1A7C4DFF.toInt(), 0x00000000)
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
                    setColor(0x00000000)
                    setStroke((1.5 * density).toInt(), (0x0D81D4FA + i * 0x0581D4FA).toInt())
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
                setColor(0xFF1A1A3E.toInt())
                setStroke((2 * density).toInt(), 0xFF7C4DFF.toInt())
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
            setTextColor(0xFFFFFFFF.toInt())
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
                    setColor(0xFF4CAF50.toInt())
                }
            }
            addView(dot)

            tvStatus = TextView(this@PhoneCallActivity).apply {
                text = "正在接听..."
                setTextColor(0xFF81D4FA.toInt())
                textSize = 13f
                gravity = android.view.Gravity.CENTER
            }
            addView(tvStatus)
        }
        contentLayout.addView(statusRow)

        tvDuration = TextView(this).apply {
            text = "00:00"
            setTextColor(0xFF666688.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * density).toInt()
            }
        }
        contentLayout.addView(tvDuration)

        tvTranscript = TextView(this).apply {
            setTextColor(0xFFCCCCDD.toInt())
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
                setColor(0x0DFFFFFF.toInt())
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

        btnMute = createControlButton("🎤", "静音", 0xFF2A2A4A.toInt()) { toggleMute() }
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

        btnSpeaker = createControlButton("🔊", "扬声器", 0xFF2A2A4A.toInt()) { toggleSpeaker() }
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
                setTextColor(0xFF8888AA.toInt())
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
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * density).toInt()
                }
            }

            addView(iconTv)
            addView(labelTv)

            background = GradientDrawable().apply {
                setColor(0xFFD32F2F.toInt())
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
        try {
            val persona = personaManager.getPersona(personaId)
            if (persona != null && persona.avatarPath.isNotBlank()) {
                val file = java.io.File(persona.avatarPath)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(persona.avatarPath)
                    if (bmp != null) { ivAvatar?.setImageBitmap(bmp); return }
                }
            }
            val prefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)
            val path = prefs.getString("persona_avatar_path", "")
            if (!path.isNullOrBlank()) {
                val file = java.io.File(path)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(path)
                    if (bmp != null) ivAvatar?.setImageBitmap(bmp)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "loadAvatar: ${e.message}")
        }
    }

    private fun startCall() {
        isCallActive = true
        callStartTime = System.currentTimeMillis()
        tvDuration?.post(durationRunnable)
        tvStatus?.text = "通话中"

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
                if (greeting.isNotBlank()) {
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

    private fun startListeningCycle() {
        if (!isCallActive || isAiSpeaking || isMuted || !hasAudioPermission) return

        isListening = true
        waveformView?.setMode(VoiceWaveformView.MODE_LISTENING)
        asrManager.startListening()
    }

    private fun processUserSpeech(userText: String) {
        if (!isCallActive) return

        tvTranscript?.text = "你: $userText"
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
                val response = client.sendChat(
                    userId = "phone_user", message = userText,
                    personaName = pName, personaPrompt = pPrompt,
                    emotion = "NEUTRAL", action = "IDLE",
                    memories = emptyList(), appCategory = "phone_call"
                )
                response?.text?.replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "")?.trim() ?: ""
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

        val engineMode = ttsManager.engineMode
        if (engineMode == TtsManager.ENGINE_LOCAL || !ttsManager.isCloudConfigured) {
            voiceManager.speak(text, Emotion.NEUTRAL)
            waitForLocalTtsCompletion(text)
            return
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { ttsManager.synthesize(text) }
                if (result.success && (result.audioPath != null || result.audioUrl != null)) {
                    withContext(Dispatchers.Main) {
                        ttsManager.playAudio(result.audioPath, result.audioUrl) { onAiSpeechComplete() }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        voiceManager.speak(text)
                        waitForLocalTtsCompletion(text)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "speakAi: ${e.message}")
                voiceManager.speak(text)
                waitForLocalTtsCompletion(text)
            }
        }
    }

    private fun waitForLocalTtsCompletion(text: String) {
        val estimatedMs = (text.length * 200L).coerceIn(1500, 10000)
        tvStatus?.postDelayed({ onAiSpeechComplete() }, estimatedMs)
    }

    private fun onAiSpeechComplete() {
        if (!isCallActive) return
        isAiSpeaking = false
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
                setColor(0xFFE53935.toInt())
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
                setColor(0xFF2A2A4A.toInt())
                setCornerRadius(14 * density)
            }
            if (isCallActive && !isAiSpeaking) startListeningCycle()
        }
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
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

        asrManager.cancel()
        asrManager.cleanup()
        voiceManager.speak("")
        ttsManager.stopPlayback()

        stopPulseAnimation()
        waveformView?.setMode(VoiceWaveformView.MODE_IDLE)
        tvStatus?.text = "通话结束"
        tvStatus?.removeCallbacks(durationRunnable)

        tvStatus?.postDelayed({ finish() }, 800)
    }

    override fun onDestroy() {
        isCallActive = false
        asrManager.cleanup()
        voiceManager.cleanup()
        ttsManager.cleanup()
        stopPulseAnimation()
        super.onDestroy()
    }

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

        fun setMode(m: Int) {
            mode = m
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val totalWidth = barCount * (barWidth + barGap)
            val startX = (w - totalWidth) / 2f
            val centerY = h / 2f

            phase += 0.08f

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
                        Color.argb((alpha * 255).toInt(), 0x64, 0xFF, 0xDA)
                    }
                    MODE_AI_SPEAKING -> {
                        val alpha = (0.4f + 0.6f * (amplitude / (h * 0.4f))).coerceIn(0f, 1f)
                        Color.argb((alpha * 255).toInt(), 0x7C, 0x4D, 0xFF)
                    }
                    MODE_MUTED -> Color.argb(0x40, 0x88, 0x88, 0x88)
                    else -> Color.argb(0x30, 0x55, 0x55, 0x77)
                }

                paint.color = color
                val rect = RectF(x, centerY - amplitude / 2, x + barWidth, centerY + amplitude / 2)
                canvas.drawRoundRect(rect, barWidth / 2, barWidth / 2, paint)
            }

            if (mode != MODE_IDLE || mode == MODE_MUTED) {
                postInvalidateDelayed(30)
            } else {
                postInvalidateDelayed(80)
            }
        }
    }
}
