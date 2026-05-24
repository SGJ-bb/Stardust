package com.aicompanion.groupchat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.affection.AffectionManager
import com.aicompanion.anim.AnimeUtils
import com.aicompanion.diary.DiaryManager
import com.aicompanion.memory.ContextManager
import com.aicompanion.memory.MemoryManager
import com.aicompanion.memory.MemoryPool
import com.aicompanion.network.ApiClient
import com.aicompanion.emotion.EmotionAnalyzer
import com.aicompanion.emotion.EmotionParams
import com.aicompanion.persona.PersonaManager
import com.aicompanion.prompt.PromptBuilder
import com.aicompanion.settings.SettingsManager
import com.aicompanion.predict.ChatPredictor
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.aicompanion.stats.PersonaStatsManager
import com.aicompanion.theme.BubbleSkinManager
import com.aicompanion.ui.ChatBubblePopup
import com.aicompanion.util.AppLogger
import com.aicompanion.ui.VirtualWorldActivity
import com.aicompanion.virtualworld.VirtualWorldManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class GroupChatActivity : AppCompatActivity() {

    private var groupId: String = ""
    private lateinit var groupChatManager: GroupChatManager
    private lateinit var personaManager: PersonaManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroupMessageAdapter
    private var messages = mutableListOf<GroupMessage>()
    private var isProcessing = false
    private var currentSpeakMode = "auto"
    private var chatBubblePopup: ChatBubblePopup? = null

    private lateinit var scrollPredictions: HorizontalScrollView
    private lateinit var layoutPredictions: LinearLayout
    private lateinit var chatPredictor: ChatPredictor

    private val contextManagers = mutableMapOf<String, ContextManager>()
    private val chatStorage by lazy { com.aicompanion.storage.ChatHistoryStorage(this) }
    private val voiceManager by lazy { com.aicompanion.voice.VoiceManager(this) }
    private val ttsManager by lazy { com.aicompanion.voice.TtsManager(this) }
    private val groupContextManager: ContextManager by lazy {
        ContextManager(this, "group_$groupId", "group_$groupId")
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val dir = File(filesDir, "chat_images")
                dir.mkdirs()
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val destFile = File(dir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                val msg = GroupMessage(
                    senderPersonaId = "user",
                    senderName = "我",
                    text = "[图片]",
                    time = time,
                    isUser = true
                )
                messages.add(msg)
                groupChatManager.addMessage(groupId, msg)
                chatStorage.addMessage("group", groupId, com.aicompanion.storage.StoredMessage(
                    id = msg.id, text = msg.text, time = msg.time, isUser = msg.isUser,
                    timestamp = msg.timestamp, senderName = msg.senderName,
                    senderPersonaId = msg.senderPersonaId, emotion = msg.emotion
                ))
                adapter.notifyItemInserted(messages.size - 1)
                recyclerView.scrollToPosition(messages.size - 1)
            } catch (e: Exception) {
                Toast.makeText(this, "图片上传失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val avatarCache = LruCache<String, Bitmap>(12)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_chat)

        groupId = intent.getStringExtra("group_id") ?: ""
        if (groupId.isEmpty()) { finish(); return }

        groupChatManager = GroupChatManager(this)
        groupChatManager.load()
        personaManager = PersonaManager(this)
        personaManager.load()
        settingsManager = SettingsManager(this)
        chatBubblePopup = ChatBubblePopup(this)

        val group = groupChatManager.getGroup(groupId)
        if (group == null) { finish(); return }

        currentSpeakMode = group.speakMode

        recyclerView = findViewById(R.id.rv_group_messages)
        val storedMsgs = chatStorage.getRecentMessages("group", groupId)
        if (storedMsgs.isNotEmpty()) {
            messages = storedMsgs.map { sm ->
                GroupMessage(
                    id = sm.id, senderPersonaId = sm.senderPersonaId,
                    senderName = sm.senderName, text = sm.text,
                    time = sm.time, timestamp = sm.timestamp,
                    isUser = sm.isUser, emotion = sm.emotion
                )
            }.toMutableList()
        } else {
            messages = groupChatManager.getMessages(groupId).toMutableList()
            if (messages.isNotEmpty()) {
                chatStorage.addMessages("group", groupId, messages.map { msg ->
                    com.aicompanion.storage.StoredMessage(
                        id = msg.id, text = msg.text, time = msg.time, isUser = msg.isUser,
                        timestamp = msg.timestamp, senderName = msg.senderName,
                        senderPersonaId = msg.senderPersonaId, emotion = msg.emotion
                    )
                })
            }
        }
        adapter = GroupMessageAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<TextView>(R.id.tv_group_name).text = "👥 ${group.name}"
        findViewById<TextView>(R.id.tv_member_count).text = "${group.memberPersonaIds.size} 位成员"

        updateSpeakModeUI()

        findViewById<View>(R.id.btn_back).setOnClickListener {
            AnimeUtils.pulse(it)
            finish()
        }

        findViewById<TextView>(R.id.btn_speak_mode).setOnClickListener {
            showSpeakModeDialog()
        }

        findViewById<View>(R.id.btn_at).setOnClickListener {
            showAtDialog()
        }

        findViewById<View>(R.id.btn_virtual_world).setOnClickListener {
            AnimeUtils.pulse(it)
            val intent = Intent(this, VirtualWorldActivity::class.java)
            intent.putExtra(VirtualWorldActivity.EXTRA_WORLD_ID, groupId)
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_add_member).setOnClickListener {
            AnimeUtils.pulse(it)
            showAddMemberDialog()
        }

        findViewById<View>(R.id.btn_memory_pool).setOnClickListener {
            AnimeUtils.pulse(it)
            showMemoryPoolDialog()
        }

        findViewById<View>(R.id.btn_chat_history).setOnClickListener {
            AnimeUtils.pulse(it)
            try {
                val intent = Intent(this, com.aicompanion.ui.ChatHistoryActivity::class.java)
                intent.putExtra("scope", "group")
                intent.putExtra("scopeId", groupId)
                intent.putExtra("scopeName", group.name)
                startActivity(intent)
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("GroupChat", "chatHistory: ${e.message}")
            }
        }

        findViewById<View>(R.id.btn_phone_call).setOnClickListener {
            AnimeUtils.pulse(it)
            try {
                val intent = Intent(this, com.aicompanion.ui.PhoneCallActivity::class.java)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_ID, "")
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_PERSONA_NAME, group.name)
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE, "group")
                intent.putExtra(com.aicompanion.ui.PhoneCallActivity.EXTRA_SCOPE_ID, groupId)
                startActivity(intent)
            } catch (e: Exception) {
                com.aicompanion.util.AppLogger.e("GroupChat", "phoneCall: ${e.message}")
            }
        }

        findViewById<View>(R.id.btn_image_upload).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            imagePickerLauncher.launch(intent)
        }

        findViewById<View>(R.id.btn_send).setOnClickListener {
            val et = findViewById<EditText>(R.id.et_message)
            val text = et.text.toString().trim()
            if (text.isEmpty() || isProcessing) return@setOnClickListener
            et.text.clear()
            scrollPredictions.visibility = View.GONE
            sendUserMessage(text)
        }

        setupManualChips(group)
        loadChatBackground()

        scrollPredictions = findViewById(R.id.scroll_predictions)
        layoutPredictions = findViewById(R.id.layout_predictions)
        chatPredictor = ChatPredictor(this, settingsManager)

        if (messages.isNotEmpty()) {
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun getContextManager(personaId: String): ContextManager {
        return contextManagers.getOrPut(personaId) {
            ContextManager(this, personaId, "group_$groupId")
        }
    }

    private fun triggerGroupTts(text: String, emotionStr: String) {
        val emotion = try { com.aicompanion.models.Emotion.valueOf(emotionStr.uppercase()) } catch (_: Exception) { com.aicompanion.models.Emotion.NEUTRAL }
        val engineMode = ttsManager.engineMode

        if (engineMode == com.aicompanion.voice.TtsManager.ENGINE_LOCAL || !ttsManager.isCloudConfigured) {
            voiceManager.speak(text, emotion)
            return
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { ttsManager.synthesize(text, emotion) }
                if (result.success && (result.audioPath != null || result.audioUrl != null)) {
                    ttsManager.playAudio(result.audioPath, result.audioUrl)
                } else if (!result.success) {
                    AppLogger.w("GroupChat", "云端TTS失败，回退本地: ${result.error}")
                    voiceManager.speak(text, emotion)
                }
            } catch (e: Exception) {
                AppLogger.e("GroupChat", "triggerGroupTts: ${e.message}")
                voiceManager.speak(text, emotion)
            }
        }
    }

    private fun showMemoryPoolDialog() {
        val group = groupChatManager.getGroup(groupId) ?: return
        val sb = StringBuilder()

        sb.appendLine("━━━ 🌐 群聊共享记忆池 ━━━")
        val groupCtxMgr = groupContextManager
        val groupPool = groupCtxMgr.memoryPool
        val groupPoolBlock = groupPool.getPoolBlock()
        if (groupPoolBlock.isNotBlank()) {
            sb.appendLine(groupPoolBlock)
        } else {
            sb.appendLine("（空）")
        }
        sb.appendLine(groupCtxMgr.getSessionStats())
        sb.appendLine()

        for (personaId in group.memberPersonaIds) {
            val persona = personaManager.getPersona(personaId) ?: continue
            val ctxMgr = getContextManager(personaId)
            val pool = ctxMgr.memoryPool
            val poolBlock = pool.getPoolBlock()

            sb.appendLine("━━━ ${persona.name} 的记忆池 ━━━")
            if (poolBlock.isNotBlank()) {
                sb.appendLine(poolBlock)
            } else {
                sb.appendLine("（空）")
            }
            sb.appendLine(ctxMgr.getSessionStats())
            sb.appendLine()
        }

        val view = android.widget.ScrollView(this).apply {
            val tv = TextView(this@GroupChatActivity).apply {
                text = sb.toString().trim()
                textSize = 13f
                setTextColor(0xFFe0e0f0.toInt())
                setPadding(40, 32, 40, 32)
            }
            addView(tv)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("🧠 群聊记忆池")
            .setView(view)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun updateSpeakModeUI() {
        val btn = findViewById<TextView>(R.id.btn_speak_mode)
        val manualLayout = findViewById<View>(R.id.layout_manual_select)
        when (currentSpeakMode) {
            "auto" -> {
                btn.text = "🤖 自动"
                manualLayout.visibility = View.GONE
            }
            "ai_judge" -> {
                btn.text = "🧠 AI判定"
                manualLayout.visibility = View.GONE
            }
            "manual" -> {
                btn.text = "👆 手动"
                manualLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun showSpeakModeDialog() {
        val options = arrayOf("🤖 自动 — 所有角色发言", "🧠 AI判定 — AI决定是否说话", "👆 手动 — 你选谁说话")
        val currentIndex = when (currentSpeakMode) {
            "auto" -> 0; "ai_judge" -> 1; "manual" -> 2; else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("发言模式")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                currentSpeakMode = when (which) {
                    0 -> "auto"; 1 -> "ai_judge"; 2 -> "manual"; else -> "auto"
                }
                val group = groupChatManager.getGroup(groupId)
                if (group != null) {
                    groupChatManager.updateGroup(group.copy(speakMode = currentSpeakMode))
                }
                updateSpeakModeUI()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAtDialog() {
        val group = groupChatManager.getGroup(groupId) ?: return
        val names = group.memberPersonaIds.mapNotNull { personaManager.getPersona(it)?.name }
            .toTypedArray()
        if (names.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("@提及")
            .setItems(names) { _, which ->
                val et = findViewById<EditText>(R.id.et_message)
                val current = et.text.toString()
                et.setText("${current}@${names[which]} ")
                et.setSelection(et.text.length)
                et.requestFocus()
            }
            .show()
    }

    private fun showAddMemberDialog() {
        val group = groupChatManager.getGroup(groupId) ?: return
        val allPersonas = personaManager.getAllPersonas()
        if (allPersonas.isEmpty()) {
            Toast.makeText(this, "暂无角色，请先创建角色", Toast.LENGTH_SHORT).show()
            return
        }

        val currentIds = group.memberPersonaIds.toSet()
        val names = allPersonas.map { it.name }.toTypedArray()
        val checked = allPersonas.map { it.id in currentIds }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("➕ 管理群成员")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("确定") { _, _ ->
                val selectedIds = allPersonas.filterIndexed { i, _ -> checked[i] }.map { it.id }
                if (selectedIds.isEmpty()) {
                    Toast.makeText(this, "至少需要一个成员", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updatedGroup = group.copy(memberPersonaIds = selectedIds)
                groupChatManager.updateGroup(updatedGroup)

                val vwManager = VirtualWorldManager(this, groupId)
                val vwConfig = vwManager.config
                vwManager.config = vwConfig.copy(memberPersonaIds = selectedIds)

                setupManualChips(updatedGroup)
                findViewById<TextView>(R.id.tv_member_count).text = "${selectedIds.size} 位成员"
                Toast.makeText(this, "成员已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupManualChips(group: GroupChat) {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_personas)
        chipGroup.removeAllViews()
        for (personaId in group.memberPersonaIds) {
            val persona = personaManager.getPersona(personaId) ?: continue
            val chip = Chip(this).apply {
                text = persona.name
                isCheckable = true
                isChecked = true
                updateChipStyle(this)
                setOnCheckedChangeListener { _, _ -> updateChipStyle(this) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateChipStyle(chip: Chip) {
        if (chip.isChecked) {
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#667eea")
            )
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#667eea")
            )
            chip.chipStrokeWidth = 0f
            chip.setTextColor(android.graphics.Color.WHITE)
        } else {
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#1a1a3e")
            )
            chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#445577")
            )
            chip.chipStrokeWidth = 1.5f
            chip.setTextColor(android.graphics.Color.parseColor("#8899bb"))
        }
    }

    private fun getManualSelectedIds(): Set<String> {
        val group = groupChatManager.getGroup(groupId) ?: return emptySet()
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_personas)
        val selected = mutableSetOf<String>()
        for (i in group.memberPersonaIds.indices) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) {
                selected.add(group.memberPersonaIds[i])
            }
        }
        return selected
    }

    private fun loadChatBackground() {
        val bgPath = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("chat_background", "")
        val iv = findViewById<ImageView>(R.id.iv_group_chat_bg)
        if (!bgPath.isNullOrEmpty()) {
            val file = File(bgPath)
            if (file.exists()) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(bgPath, options)
                    options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1080, 1920)
                    options.inJustDecodeBounds = false
                    val bmp = BitmapFactory.decodeFile(bgPath, options)
                    iv.setImageBitmap(bmp)
                    iv.alpha = 0.3f
                } catch (e: Exception) { AppLogger.e("GroupChat", "loadChatBackground: ${e.message}") }
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSample = 1
        if (height > reqHeight || width > reqWidth) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSample >= reqHeight && halfW / inSample >= reqWidth) {
                inSample *= 2
            }
        }
        return inSample
    }

    private fun sendUserMessage(text: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
        val msg = GroupMessage(
            senderPersonaId = "user",
            senderName = "我",
            text = text,
            time = time,
            isUser = true
        )
        messages.add(msg)
        groupChatManager.addMessage(groupId, msg)
        chatStorage.addMessage("group", groupId, com.aicompanion.storage.StoredMessage(
            id = msg.id, text = msg.text, time = msg.time, isUser = msg.isUser,
            timestamp = msg.timestamp, senderName = msg.senderName,
            senderPersonaId = msg.senderPersonaId, emotion = msg.emotion
        ))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        val group = groupChatManager.getGroup(groupId) ?: return
        triggerAiResponses(group, text, emptySet())
    }

    private fun triggerAiResponses(group: GroupChat, triggerText: String, chainMentions: Set<String>, depth: Int = 0) {
        if (isProcessing && depth == 0) return
        if (depth > 3) return
        if (depth == 0) isProcessing = true

        val mentionedIds = parseMentions(triggerText, group)

        val targetIds = when {
            chainMentions.isNotEmpty() -> chainMentions.toList()
            currentSpeakMode == "manual" -> {
                val manualIds = getManualSelectedIds()
                if (mentionedIds.isNotEmpty()) mentionedIds else manualIds
            }
            currentSpeakMode == "ai_judge" -> {
                if (mentionedIds.isNotEmpty()) mentionedIds else group.memberPersonaIds
            }
            else -> {
                if (mentionedIds.isNotEmpty()) mentionedIds else group.memberPersonaIds
            }
        }

        lifecycleScope.launch {
            val chainQueue = mutableListOf<String>()

            for (personaId in targetIds) {
                val persona = personaManager.getPersona(personaId) ?: continue
                val isMentioned = personaId in mentionedIds || personaId in chainMentions

                val shouldSpeak = when {
                    isMentioned -> true
                    currentSpeakMode == "auto" -> true
                    currentSpeakMode == "manual" -> true
                    currentSpeakMode == "ai_judge" -> {
                        withContext(Dispatchers.IO) {
                            shouldPersonaSpeak(persona, group, triggerText)
                        }
                    }
                    else -> true
                }

                if (!shouldSpeak) continue

                val response = withContext(Dispatchers.IO) {
                    callPersonaLLM(persona, group, triggerText, isMentioned, isChainTrigger = depth > 0)
                }

                if (response != null) {
                    var cleanText = response.text
                        .replace(Regex("\\[\\[emotion:\\w+\\]\\]", RegexOption.IGNORE_CASE), "").trim()
                    val emotionStr = Regex("\\[\\[emotion:(\\w+)\\]\\]", RegexOption.IGNORE_CASE)
                        .find(response.text)?.groupValues?.get(1) ?: "neutral"

                    val otherNames = group.memberPersonaIds
                        .filter { it != personaId }
                        .mapNotNull { personaManager.getPersona(it)?.name }
                    cleanText = PromptBuilder.stripNamePrefix(cleanText, persona.name, otherNames)

                    val aiMentions = parseAiMentions(cleanText, group)
                    cleanText = cleanText.replace(Regex("@[^\\s@]+\\s?"), "").trim()

                    if (cleanText.isNotBlank() && cleanText != "..." && cleanText != "沉默") {
                        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                        val aiMsg = GroupMessage(
                            senderPersonaId = personaId,
                            senderName = persona.name,
                            text = cleanText,
                            time = time,
                            isUser = false,
                            emotion = emotionStr
                        )
                        messages.add(aiMsg)
                        groupChatManager.addMessage(groupId, aiMsg)
                        chatStorage.addMessage("group", groupId, com.aicompanion.storage.StoredMessage(
                            id = aiMsg.id, text = aiMsg.text, time = aiMsg.time, isUser = aiMsg.isUser,
                            timestamp = aiMsg.timestamp, senderName = aiMsg.senderName,
                            senderPersonaId = aiMsg.senderPersonaId, emotion = aiMsg.emotion
                        ))
                        adapter.notifyItemInserted(messages.size - 1)
                        recyclerView.scrollToPosition(messages.size - 1)

                        if (settingsManager.isTTSEnabled) {
                            triggerGroupTts(cleanText, emotionStr)
                        }

                        if (!isFinishing && !isDestroyed) {
                            chatBubblePopup?.show(persona.name, cleanText, persona.avatarPath.ifBlank { null })
                        }

                        try {
                            val statsMgr = PersonaStatsManager(this@GroupChatActivity, personaId)
                            statsMgr.recordAiMessage(cleanText)
                            statsMgr.recordEmotion(emotionStr)
                        } catch (e: Exception) { AppLogger.e("GroupChat", "triggerAiResponses/recordStats: ${e.message}") }

                        try {
                            val affectionMgr = AffectionManager(this@GroupChatActivity, personaId)
                            affectionMgr.addMessage()
                        } catch (e: Exception) { AppLogger.e("GroupChat", "triggerAiResponses/addAffection: ${e.message}") }

                        try {
                            val memMgr = MemoryManager(this@GroupChatActivity, personaId)
                            memMgr.addMemoryFact(cleanText, "群聊对话")
                        } catch (e: Exception) { AppLogger.e("GroupChat", "triggerAiResponses/addMemory: ${e.message}") }

                        try {
                            val ctxMgr = getContextManager(personaId)
                            ctxMgr.addTurn(triggerText, cleanText)

                            groupContextManager.addTurn(triggerText, "${persona.name}：$cleanText")

                            val client = ApiClient(
                                settingsManager.chatApiUrl,
                                settingsManager.chatApiKey,
                                settingsManager.chatModel,
                                settingsManager.llmTemperature,
                                settingsManager.llmTopP,
                                settingsManager.llmFrequencyPenalty,
                                settingsManager.llmPresencePenalty,
                                settingsManager.llmMaxTokens,
                                settingsManager.apiProvider
                            )
                            withContext(Dispatchers.IO) {
                                try {
                                    ctxMgr.evaluateAndUpdateMemory(client)
                                    ctxMgr.memoryPool.saveToStorage()
                                    groupContextManager.evaluateAndUpdateMemory(client)
                                    groupContextManager.memoryPool.saveToStorage()
                                } catch (e: Exception) { AppLogger.e("GroupChat", "triggerAiResponses/evaluateMemory: ${e.message}") }
                            }
                        } catch (e: Exception) { AppLogger.e("GroupChat", "triggerAiResponses/updateContext: ${e.message}") }

                        try {
                            val dm = DiaryManager(this@GroupChatActivity, personaId)
                            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            val existingDiary = dm.getDiaryByDate(today)
                            if (existingDiary == null) {
                                val ctxMgr = getContextManager(personaId)
                                val totalTurns = ctxMgr.sessionManager.currentTurnCount
                                val prefs = getSharedPreferences("diary_trigger_gc_$personaId", MODE_PRIVATE)
                                val lastTriggered = prefs.getInt("last_diary_turns_trigger", 0)
                                if (totalTurns - lastTriggered >= 75 || totalTurns >= 75 && lastTriggered == 0) {
                                    prefs.edit().putInt("last_diary_turns_trigger", totalTurns).apply()
                                    val poolBlock = ctxMgr.memoryPool.getPoolBlock()
                                    val chatTexts = messages.takeLast(30).map { it.text }
                                    if (poolBlock.isNotBlank()) {
                                        val am = AffectionManager(this@GroupChatActivity, personaId)
                                        dm.updateOrGenerateDailyDiary(chatTexts, am.affectionLevel)
                                    }
                                }
                            }
                        } catch (e: Exception) { AppLogger.e("GroupChat", "triggerAiResponses/generateDiary: ${e.message}") }

                        if (aiMentions.isNotEmpty()) {
                            for (mid in aiMentions) {
                                if (mid != personaId) {
                                    chainQueue.add(mid)
                                }
                            }
                        }
                    } else if (aiMentions.isNotEmpty()) {
                        for (mid in aiMentions) {
                            if (mid != personaId) {
                                chainQueue.add(mid)
                            }
                        }
                    }
                }
            }

            if (chainQueue.isNotEmpty() && depth < 3) {
                val lastAiMsg = messages.lastOrNull { !it.isUser }
                val chainText = if (lastAiMsg != null) {
                    "${lastAiMsg.senderName}：${lastAiMsg.text}"
                } else triggerText
                triggerAiResponses(group, chainText, chainQueue.toSet(), depth + 1)
            }

            if (depth == 0) {
                isProcessing = false
                if (!isFinishing && !isDestroyed) {
                    triggerGroupPredictions(group)
                }
            }
        }
    }

    private fun parseMentions(userText: String, group: GroupChat): Set<String> {
        val mentioned = mutableSetOf<String>()

        val atPattern = Regex("@[^\\s@]+")
        val atMatches = atPattern.findAll(userText).map { it.value.removePrefix("@") }.toList()

        for (match in atMatches) {
            for (personaId in group.memberPersonaIds) {
                val persona = personaManager.getPersona(personaId) ?: continue
                if (persona.name == match) {
                    mentioned.add(personaId)
                }
            }
        }

        if (mentioned.isEmpty()) {
            for (personaId in group.memberPersonaIds) {
                val persona = personaManager.getPersona(personaId) ?: continue
                if (userText.contains(persona.name)) {
                    mentioned.add(personaId)
                }
            }
        }

        if (mentioned.isEmpty() && group.memberPersonaIds.size == 1) {
            mentioned.add(group.memberPersonaIds.first())
        }

        return mentioned
    }

    private fun parseAiMentions(text: String, group: GroupChat): Set<String> {
        val mentioned = mutableSetOf<String>()
        val atPattern = Regex("@[^\\s@]+")
        for (match in atPattern.findAll(text).map { it.value.removePrefix("@") }) {
            for (personaId in group.memberPersonaIds) {
                val persona = personaManager.getPersona(personaId) ?: continue
                if (persona.name == match || persona.name.startsWith(match) || match.startsWith(persona.name)) {
                    mentioned.add(personaId)
                }
            }
        }
        return mentioned
    }

    private fun buildNarrativeHistory(personaId: String): String {
        return messages.takeLast(20).map { msg ->
            when {
                msg.isUser -> "[用户说] ${msg.text}"
                msg.senderPersonaId == personaId -> "[你之前说] ${msg.text}"
                else -> "[${msg.senderName}说] ${msg.text}"
            }
        }.joinToString("\n")
    }

    private suspend fun shouldPersonaSpeak(
        persona: com.aicompanion.persona.Persona,
        group: GroupChat,
        userText: String
    ): Boolean {
        val client = ApiClient(
            settingsManager.chatApiUrl,
            settingsManager.chatApiKey,
            settingsManager.chatModel,
            settingsManager.llmTemperature,
            settingsManager.llmTopP,
            settingsManager.llmFrequencyPenalty,
            settingsManager.llmPresencePenalty,
            settingsManager.llmMaxTokens,
            settingsManager.apiProvider
        )

        val recentContext = messages.takeLast(settingsManager.contextTurns).map { msg ->
            if (msg.isUser) "[用户说] ${msg.text}" else "[${msg.senderName}说] ${msg.text}"
        }.joinToString("\n")

        val identity = PromptBuilder.buildIdentity(this, persona.id)
        val prompt = PromptBuilder.buildSilentCheckPrompt(identity, userText, recentContext)

        return try {
            val response = client.sendSimplePrompt(prompt, "只回「说话」或「沉默」")
            val text = response?.text?.trim()?.lowercase() ?: "沉默"
            text.contains("说话") || text.contains("speak") || text.contains("yes")
        } catch (_: Exception) {
            false
        }
    }

    private fun triggerGroupPredictions(group: GroupChat) {
        val recent = messages.takeLast(10).map { msg ->
            if (msg.isUser) "user" to msg.text else msg.senderName to msg.text
        }
        if (recent.isEmpty()) return
        val memberNames = group.memberPersonaIds.mapNotNull { personaManager.getPersona(it)?.name }
        lifecycleScope.launch {
            val predictions = withContext(Dispatchers.IO) {
                chatPredictor.predictGroupChat(
                    recentMessages = recent,
                    memberNames = memberNames
                )
            }
            if (!isFinishing && !isDestroyed) {
                showGroupPredictions(predictions)
            }
        }
    }

    private fun showGroupPredictions(predictions: List<String>) {
        layoutPredictions.removeAllViews()
        if (predictions.isEmpty()) {
            scrollPredictions.visibility = View.GONE
            return
        }
        val dp = resources.displayMetrics.density
        val et = findViewById<EditText>(R.id.et_message)
        for (text in predictions) {
            val tv = TextView(this).apply {
                this.text = text
                setTextColor(0xFFe0e0f0.toInt())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setBackgroundResource(R.drawable.bg_prediction_chip)
                setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (isFinishing || isDestroyed) return@setOnClickListener
                    et.setText(text)
                    et.setSelection(text.length)
                    val sendBtn = findViewById<View>(R.id.btn_send) ?: return@setOnClickListener
                    sendBtn.performClick()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), (6 * dp).toInt())
            layoutPredictions.addView(tv, lp)
        }
        scrollPredictions.visibility = View.VISIBLE
    }

    private fun callPersonaLLM(
        persona: com.aicompanion.persona.Persona,
        group: GroupChat,
        userText: String,
        isMentioned: Boolean,
        isChainTrigger: Boolean = false
    ): com.aicompanion.models.ChatResponse? {
        val narrativeHistory = buildNarrativeHistory(persona.id)

        val otherIds = group.memberPersonaIds.filter { it != persona.id }
        val otherNames = otherIds.mapNotNull { personaManager.getPersona(it)?.name }

        val otherAffections = mutableMapOf<String, Int>()
        for (oid in otherIds) {
            val otherPersona = personaManager.getPersona(oid) ?: continue
            val affMgr = com.aicompanion.affection.AffectionManager(this, oid)
            otherAffections[otherPersona.name] = affMgr.affectionLevel
        }

        val identity = PromptBuilder.buildIdentity(this, persona.id)

        val ctxMgr = getContextManager(persona.id)
        val memoryContext = ctxMgr.getContextBlock()

        val groupContext = PromptBuilder.buildGroupChatPrompt(identity, otherNames, otherAffections, emptyList(), isMentioned, group.relationshipSetting, this)

        val client = ApiClient(
            settingsManager.chatApiUrl,
            settingsManager.chatApiKey,
            settingsManager.chatModel,
            settingsManager.llmTemperature,
            settingsManager.llmTopP,
            settingsManager.llmFrequencyPenalty,
            settingsManager.llmPresencePenalty,
            settingsManager.llmMaxTokens,
            settingsManager.apiProvider
        )

        var emotionParams = EmotionParams()
        if (settingsManager.emotionAnalysisEnabled && settingsManager.chatApiUrl.isNotBlank()) {
            try {
                val analyzer = EmotionAnalyzer(client)
                emotionParams = analyzer.analyzeEmotion(
                    personaName = persona.name,
                    personaPrompt = "${identity.name}：${identity.personality} ${identity.speechStyle}\n${identity.customPrompt}",
                    userMessage = userText,
                    chatHistory = messages.takeLast(6).map { it.isUser to it.text },
                    currentEmotion = "neutral"
                )
            } catch (e: Exception) { AppLogger.e("GroupChat", "callPersonaLLM/analyzeEmotion: ${e.message}") }
        }

        val effectiveTemp = if (settingsManager.emotionAnalysisEnabled) emotionParams.applyToTemperature(settingsManager.llmTemperature) else settingsManager.llmTemperature
        val effectiveTopP = if (settingsManager.emotionAnalysisEnabled) emotionParams.applyToTopP(settingsManager.llmTopP) else settingsManager.llmTopP

        val emotionClient = if (settingsManager.emotionAnalysisEnabled) {
            ApiClient(
                settingsManager.chatApiUrl,
                settingsManager.chatApiKey,
                settingsManager.chatModel,
                effectiveTemp,
                effectiveTopP,
                settingsManager.llmFrequencyPenalty,
                settingsManager.llmPresencePenalty,
                settingsManager.llmMaxTokens,
                settingsManager.apiProvider
            )
        } else {
            client
        }

        val triggerLabel = if (isChainTrigger) {
            "[有人@你] $userText"
        } else {
            "[用户现在说] $userText"
        }

        val fullUserMsg = buildString {
            if (memoryContext.isNotBlank()) {
                appendLine(memoryContext)
                appendLine()
            }
            appendLine(narrativeHistory)
            appendLine()
            append(triggerLabel)
        }

        return try {
            emotionClient.sendSimplePrompt(groupContext, fullUserMsg)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAvatarBitmap(path: String): Bitmap? {
        if (path.isBlank()) return null
        val cached = avatarCache[path]
        if (cached != null && !cached.isRecycled) return cached
        return try {
            val file = File(path)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) avatarCache.put(path, bmp)
                bmp
            } else null
        } catch (_: Exception) { null }
    }

    override fun onDestroy() {
        super.onDestroy()
        contextManagers.clear()
        avatarCache.evictAll()
    }

    inner class GroupMessageAdapter(private val items: MutableList<GroupMessage>) :
        RecyclerView.Adapter<GroupMessageAdapter.VH>() {

        private val aiColors = intArrayOf(
            0xFF9c7cff.toInt(), 0xFF64ffda.toInt(), 0xFF667eea.toInt(),
            0xFFffb347.toInt(), 0xFFe8a0bf.toInt(), 0xFF7fdbda.toInt()
        )
        private var colorIndex = 0
        private val assignedColors = mutableMapOf<String, Int>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cvAvatar: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.cv_avatar)
            val ivAvatar: ImageView = view.findViewById(R.id.iv_avatar)
            val tvSenderName: TextView = view.findViewById(R.id.tv_sender_name)
            val tvMsgTime: TextView = view.findViewById(R.id.tv_msg_time)
            val tvMsgText: TextView = view.findViewById(R.id.tv_msg_text)
            val bubbleContainer: FrameLayout = view.findViewById(R.id.bubble_container)
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group_message, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]

            if (msg.isUser) {
                holder.cvAvatar.visibility = View.VISIBLE
                holder.tvSenderName.text = "我"
                holder.tvSenderName.setTextColor(0xFFff6b9d.toInt())
                loadUserAvatar(holder.ivAvatar, holder.cvAvatar)
            } else {
                holder.cvAvatar.visibility = View.VISIBLE
                holder.tvSenderName.text = msg.senderName
                holder.tvSenderName.setTextColor(getColorForSender(msg.senderPersonaId))
                loadPersonaAvatar(msg.senderPersonaId, holder.ivAvatar, holder.cvAvatar)
            }

            holder.tvMsgTime.text = msg.time
            holder.tvMsgText.text = msg.text

            if (!msg.isUser) {
                applyBubbleSkin(holder.bubbleContainer, msg.senderPersonaId)
            } else {
                holder.bubbleContainer.setBackgroundResource(R.drawable.bg_message_user)
                holder.tvMsgText.setTextColor(0xFFFFFFFF.toInt())
            }
        }

        private fun getColorForSender(senderId: String): Int {
            if (senderId == "user") return 0xFFff6b9d.toInt()
            return assignedColors.getOrPut(senderId) {
                aiColors[colorIndex++ % aiColors.size]
            }
        }

        private fun loadPersonaAvatar(
            personaId: String,
            ivAvatar: ImageView,
            cvAvatar: com.google.android.material.card.MaterialCardView
        ) {
            val persona = personaManager.getPersona(personaId)
            if (persona?.avatarPath.isNullOrBlank()) {
                val defaultAvatar = getSharedPreferences("avatar_data", MODE_PRIVATE)
                    .getString("ai_avatar", "")
                if (!defaultAvatar.isNullOrBlank()) {
                    val bmp = loadAvatarBitmap(defaultAvatar)
                    if (bmp != null) {
                        ivAvatar.setImageBitmap(bmp)
                    } else {
                        ivAvatar.setImageResource(R.drawable.ic_avatar_default_ai)
                    }
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_avatar_default_ai)
                }
            } else {
                val bmp = loadAvatarBitmap(persona!!.avatarPath)
                if (bmp != null) {
                    ivAvatar.setImageBitmap(bmp)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_avatar_default_ai)
                }
            }

            val aiFrame = BubbleSkinManager.getActiveAiFrame(this@GroupChatActivity)
            BubbleSkinManager.applyAvatarFrame(cvAvatar, aiFrame)

            val aiImageFrame = BubbleSkinManager.getActiveAiImageFrame(this@GroupChatActivity)
            val frameAvatar = cvAvatar.parent as? FrameLayout
            if (aiImageFrame != null && frameAvatar != null) {
                BubbleSkinManager.applyImageAvatarFrame(frameAvatar, this@GroupChatActivity, aiImageFrame)
            } else if (frameAvatar != null) {
                BubbleSkinManager.clearImageAvatarFrame(frameAvatar)
            }
        }

        private fun loadUserAvatar(
            ivAvatar: ImageView,
            cvAvatar: com.google.android.material.card.MaterialCardView
        ) {
            val userAvatarPath = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("user_avatar", "")
            if (!userAvatarPath.isNullOrBlank()) {
                val bmp = loadAvatarBitmap(userAvatarPath)
                if (bmp != null) {
                    ivAvatar.setImageBitmap(bmp)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_avatar_default_user)
                }
            } else {
                ivAvatar.setImageResource(R.drawable.ic_avatar_default_user)
            }

            val userFrame = BubbleSkinManager.getActiveUserFrame(this@GroupChatActivity)
            BubbleSkinManager.applyAvatarFrame(cvAvatar, userFrame)

            val userImageFrame = BubbleSkinManager.getActiveUserImageFrame(this@GroupChatActivity)
            val frameAvatar = cvAvatar.parent as? FrameLayout
            if (userImageFrame != null && frameAvatar != null) {
                BubbleSkinManager.applyImageAvatarFrame(frameAvatar, this@GroupChatActivity, userImageFrame)
            } else if (frameAvatar != null) {
                BubbleSkinManager.clearImageAvatarFrame(frameAvatar)
            }
        }

        private fun applyBubbleSkin(bubble: View, personaId: String) {
            val skin = BubbleSkinManager.getActiveSkin(this@GroupChatActivity)
            if (skin.id != "default") {
                BubbleSkinManager.applyBubbleSkin(bubble, skin, false)
            } else {
                bubble.setBackgroundResource(R.drawable.bg_message_pet)
            }

            val imageBubble = BubbleSkinManager.getActiveImageBubble(this@GroupChatActivity)
            if (imageBubble != null) {
                BubbleSkinManager.applyImageBubbleSkin(bubble, this@GroupChatActivity, imageBubble)
            }
        }
    }
}
