package com.aicompanion.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.anim.AnimeUtils
import com.aicompanion.network.ApiClient
import com.aicompanion.persona.PersonaManager
import com.aicompanion.prompt.PromptBuilder
import com.aicompanion.settings.SettingsManager
import com.aicompanion.virtualworld.*
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

// 文件级颜色常量
private val COLOR_SIM_RUNNING = 0xFF64ffda.toInt()      // 推演运行状态颜色（青绿色）
private val COLOR_SIM_PAUSED = 0xFF8899bb.toInt()       // 推演暂停状态颜色（灰蓝色）
private val COLOR_TITLE_TEXT = 0xFFC4B5FD.toInt()      // 标题文字颜色（淡紫色）
private val COLOR_SUBTITLE_TEXT = 0xFF99AACC.toInt()  // 副标题文字颜色（灰蓝色）
private val COLOR_DESC_TEXT = 0xFF8888AA.toInt()      // 描述文字颜色（灰紫色）
private val COLOR_DETAIL_TEXT = 0xFFe0e0f0.toInt()      // 详情文本颜色（浅灰白色）
private const val COLOR_CARD_BACKGROUND = 0x1AFFFFFF   // 卡片背景颜色（半透明白色）
private const val COLOR_RIPPLE_FOREGROUND = 0x33FFFFFF  // Ripple 前景色（半透明白色）

class VirtualWorldActivity : AppCompatActivity() {

    private lateinit var worldManager: VirtualWorldManager
    private lateinit var personaManager: PersonaManager
    private lateinit var storyAdapter: StoryEventAdapter

    private lateinit var tvVirtualDay: TextView
    private lateinit var tvVirtualHour: TextView
    private lateinit var tvTimeRatio: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvWeather: TextView
    private lateinit var tvSimStatus: TextView
    private lateinit var btnStartStop: com.google.android.material.button.MaterialButton
    private lateinit var spinnerTimeRatio: Spinner
    private lateinit var spinnerTickInterval: Spinner
    private lateinit var switchGroupSim: SwitchMaterial
    private lateinit var switchImageGen: SwitchMaterial
    private lateinit var rvStory: RecyclerView
    private lateinit var tvStoryEmpty: TextView
    private lateinit var layoutUploadedImages: LinearLayout
    private lateinit var scrollUploadedImages: android.widget.HorizontalScrollView
    private var tvVwMemoryStats: TextView? = null
    private var tvVwMemoryContent: TextView? = null

    private var isSimRunning = false
    private var simJob: Job? = null

    companion object {
        private const val TAG = "VirtualWorldActivity"
        private const val REQUEST_PICK_WORLD_IMAGE = 3001
        const val EXTRA_WORLD_ID = "world_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_virtual_world)

        val worldId = intent.getStringExtra(EXTRA_WORLD_ID) ?: ""
        worldManager = VirtualWorldManager(this, worldId)
        personaManager = PersonaManager(this)
        personaManager.load()

        initViews()
        loadState()
        setupSpinners()
        setupButtons()
        loadStory()

        animateEntrance()
        applyTheme()
    }

    private fun applyTheme() {
        try {
            val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
            val tbColor = android.graphics.Color.parseColor(scheme.toolbarColor)
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.setBackgroundColor(tbColor)
                ?: findViewById<View>(R.id.toolbar_container)?.setBackgroundColor(tbColor)
            com.aicompanion.theme.ThemeManager.applyTheme(this)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "applyTheme error: ${e.message}")
        }
    }

    private fun animateEntrance() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar?.let { AnimeUtils.fadeInScale(it, delay = 50) }

        val content = findViewById<LinearLayout>(R.id.layout_content) ?: return
        val count = content.childCount
        for (i in 0 until count) {
            val child = content.getChildAt(i)
            child.alpha = 0f
            child.translationY = 30f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay((80L * i).coerceAtMost(600))
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                .start()
        }
    }

    private fun initViews() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        tvVirtualDay = findViewById(R.id.tv_virtual_day)
        tvVirtualHour = findViewById(R.id.tv_virtual_hour)
        tvTimeRatio = findViewById(R.id.tv_time_ratio)
        tvLocation = findViewById(R.id.tv_location)
        tvWeather = findViewById(R.id.tv_weather)
        tvSimStatus = findViewById(R.id.tv_simulation_status)
        btnStartStop = findViewById(R.id.btn_start_stop)
        spinnerTimeRatio = findViewById(R.id.spinner_time_ratio)
        spinnerTickInterval = findViewById(R.id.spinner_tick_interval)
        switchGroupSim = findViewById(R.id.switch_group_sim)
        switchImageGen = findViewById(R.id.switch_image_gen)
        rvStory = findViewById(R.id.rv_story)
        tvStoryEmpty = findViewById(R.id.tv_story_empty)
        layoutUploadedImages = findViewById(R.id.layout_uploaded_images)
        scrollUploadedImages = findViewById(R.id.scroll_uploaded_images)
        tvVwMemoryStats = findViewById(R.id.tv_vw_memory_stats)
        tvVwMemoryContent = findViewById(R.id.tv_vw_memory_content)

        tvVwMemoryContent?.setOnClickListener { showMemoryDetailDialog() }
        tvVwMemoryContent?.isClickable = true
        tvVwMemoryContent?.focusable = View.FOCUSABLE
        tvVwMemoryContent?.foreground = RippleDrawable(
            ColorStateList.valueOf(COLOR_RIPPLE_FOREGROUND), null, null)

        storyAdapter = StoryEventAdapter()
        rvStory.layoutManager = LinearLayoutManager(this)
        rvStory.adapter = storyAdapter
    }

    private fun loadState() {
        val state = worldManager.state
        val config = worldManager.config

        tvVirtualDay.text = "第${state.dayCount}天"
        tvVirtualHour.text = String.format("%02d:%02d", state.hourOfDay, state.minuteOfHour)
        tvTimeRatio.text = "${config.timeRatio}x"
        tvLocation.text = "📍 ${state.currentLocation}"
        tvWeather.text = getWeatherEmoji(state.currentWeather) + " " + state.currentWeather

        isSimRunning = worldManager.isRunning
        updateSimStatus()

        switchGroupSim.isChecked = config.isGroupSimulation
        switchImageGen.isChecked = config.imageGenEnabled

        switchGroupSim.setOnCheckedChangeListener { _, isChecked ->
            val cfg = worldManager.config
            worldManager.config = cfg.copy(isGroupSimulation = isChecked)
        }

        switchImageGen.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !worldManager.hasImageModelConfigured()) {
                Toast.makeText(this, "请先配置图片生成API", Toast.LENGTH_LONG).show()
                switchImageGen.isChecked = false
                return@setOnCheckedChangeListener
            }
            val cfg = worldManager.config
            worldManager.config = cfg.copy(imageGenEnabled = isChecked)
        }
    }

    private fun getWeatherEmoji(weather: String): String {
        return when {
            weather.contains("晴") -> "☀️"
            weather.contains("云") || weather.contains("阴") -> "☁️"
            weather.contains("雨") -> "🌧️"
            weather.contains("雪") -> "❄️"
            weather.contains("风") -> "💨"
            weather.contains("雷") -> "⛈️"
            weather.contains("雾") -> "🌫️"
            else -> "🌤️"
        }
    }

    private fun setupSpinners() {
        val config = worldManager.config

        val ratioOptions = arrayOf("1x (同步现实)", "5x (1天=5天)", "10x (1天=10天)", "24x (1天=24天)")
        val ratioValues = intArrayOf(1, 5, 10, 24)
        val ratioAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, ratioOptions)
        ratioAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerTimeRatio.adapter = ratioAdapter
        val currentRatioIdx = ratioValues.indexOf(config.timeRatio).coerceAtLeast(0)
        spinnerTimeRatio.setSelection(currentRatioIdx)
        spinnerTimeRatio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cfg = worldManager.config
                worldManager.config = cfg.copy(timeRatio = ratioValues[position])
                tvTimeRatio.text = "${ratioValues[position]}x"
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val intervalOptions = arrayOf("每1分钟", "每5分钟", "每15分钟", "每30分钟", "每1小时", "每3小时", "每6小时")
        val intervalValues = intArrayOf(1, 5, 15, 30, 60, 180, 360)
        val intervalAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, intervalOptions)
        intervalAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerTickInterval.adapter = intervalAdapter
        val currentIntervalIdx = intervalValues.indexOf(config.tickIntervalMinutes).coerceAtLeast(0)
        spinnerTickInterval.setSelection(currentIntervalIdx)
        spinnerTickInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cfg = worldManager.config
                worldManager.config = cfg.copy(tickIntervalMinutes = intervalValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        btnStartStop.setOnClickListener {
            AnimeUtils.pulse(it)
            if (isSimRunning) {
                stopSimulation()
            } else {
                startSimulation()
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_tick_now).setOnClickListener {
            AnimeUtils.pulse(it)
            runSingleTick()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_edit_world).setOnClickListener {
            showWorldLoreEditor()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_select_personas).setOnClickListener {
            showPersonaSelector()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_upload_image).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PICK_WORLD_IMAGE)
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reset_world).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("重置虚拟世界")
                .setMessage("确定要重置吗？所有推演记录和世界状态将被清除。")
                .setPositiveButton("重置") { _, _ ->
                    worldManager.resetWorld()
                    loadState()
                    loadStory()
                    Toast.makeText(this, "世界已重置", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_tick_index)?.setOnClickListener {
            AnimeUtils.pulse(it)
            showTickIndexDialog()
        }
    }

    private fun startSimulation() {
        if (!worldManager.hasChatModelConfigured()) {
            Toast.makeText(this, "请先在设置中配置聊天API", Toast.LENGTH_LONG).show()
            return
        }

        val config = worldManager.config
        if (config.getFullLore().isBlank()) {
            Toast.makeText(this, "请先编辑世界观设定", Toast.LENGTH_LONG).show()
            return
        }

        val memberIds = if (config.memberPersonaIds.isEmpty()) {
            val worldId = worldManager.currentWorldId
            if (worldId.isNotBlank()) {
                val gcManager = com.aicompanion.groupchat.GroupChatManager(this)
                gcManager.load()
                val group = gcManager.getGroup(worldId)
                group?.memberPersonaIds ?: run {
                    val activeId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getString("active_persona_id", "default") ?: "default"
                    listOf(activeId)
                }
            } else {
                val activeId = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getString("active_persona_id", "default") ?: "default"
                listOf(activeId)
            }
        } else config.memberPersonaIds

        if (memberIds != config.memberPersonaIds) {
            worldManager.config = config.copy(memberPersonaIds = memberIds)
        }

        isSimRunning = true
        worldManager.isRunning = true
        worldManager.isEnabled = true
        if (worldManager.lastTickTime == 0L) {
            worldManager.lastTickTime = System.currentTimeMillis()
        }
        updateSimStatus()
        startAutoTickLoop()
        Toast.makeText(this, "虚拟世界推演已启动", Toast.LENGTH_SHORT).show()
    }

    private fun startAutoTickLoop() {
        simJob?.cancel()
        simJob = lifecycleScope.launch {
            while (isSimRunning) {
                delay(10_000L)
                if (!isSimRunning) break
                if (!worldManager.shouldTick()) continue

                val event = withContext(Dispatchers.IO) {
                    worldManager.runSimulationTick()
                }
                if (event != null) {
                    withContext(Dispatchers.Main) {
                        loadState()
                        loadStory()
                    }
                    publishEventToGroupChat(event)
                }
            }
        }
    }

    private fun stopSimulation() {
        isSimRunning = false
        worldManager.isRunning = false
        simJob?.cancel()
        simJob = null
        updateSimStatus()
        Toast.makeText(this, "推演已暂停", Toast.LENGTH_SHORT).show()
    }

    private fun updateSimStatus() {
        if (isSimRunning) {
            tvSimStatus.text = "▶ 推演中"
            tvSimStatus.setTextColor(COLOR_SIM_RUNNING)
            btnStartStop.text = "⏸ 暂停推演"
        } else {
            tvSimStatus.text = "⏸ 已暂停"
            tvSimStatus.setTextColor(COLOR_SIM_PAUSED)
            btnStartStop.text = "▶ 开始推演"
        }
        tvSimStatus.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).withEndAction {
            tvSimStatus.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    private fun runSingleTick() {
        if (!worldManager.hasChatModelConfigured()) {
            Toast.makeText(this, "请先配置聊天API", Toast.LENGTH_LONG).show()
            return
        }

        val config = worldManager.config
        if (config.getFullLore().isBlank()) {
            Toast.makeText(this, "请先编辑世界观", Toast.LENGTH_LONG).show()
            return
        }

        btnStartStop.isEnabled = false
        lifecycleScope.launch {
            val event = withContext(Dispatchers.IO) {
                worldManager.runSimulationTick()
            }
            btnStartStop.isEnabled = true
            if (event != null) {
                loadState()
                loadStory()
                publishEventToGroupChat(event)
            } else {
                Toast.makeText(this@VirtualWorldActivity, "推演失败，请检查API配置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWorldLoreEditor() {
        val config = worldManager.config
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_world_lore_editor, null)

        val etBackground = view.findViewById<EditText>(R.id.et_world_background)
        val etRules = view.findViewById<EditText>(R.id.et_world_rules)
        val etRelations = view.findViewById<EditText>(R.id.et_world_relations)
        val etScene = view.findViewById<EditText>(R.id.et_world_scene)
        val etStyle = view.findViewById<EditText>(R.id.et_world_style)

        etBackground.setText(config.worldBackground)
        etRules.setText(config.worldRules)
        etRelations.setText(config.worldRelations)
        etScene.setText(config.worldScene)
        etStyle.setText(config.worldStyle)

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        sheet.setContentView(view)
        sheet.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()

        val btnAutoGen = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_auto_generate)
        val progressGen = view.findViewById<ProgressBar>(R.id.progress_world_generate)
        btnAutoGen.setOnClickListener {
            AnimeUtils.pulse(it)
            val keywords = view.findViewById<EditText>(R.id.et_world_keywords).text.toString().trim()
            btnAutoGen.isEnabled = false
            btnAutoGen.text = "生成中..."
            progressGen.visibility = View.VISIBLE
            lifecycleScope.launch {
                autoGenerateWorldLore(etBackground, etRules, etRelations, etScene, etStyle, keywords)
                btnAutoGen.isEnabled = true
                btnAutoGen.text = "🪄 AI自动生成世界观"
                progressGen.visibility = View.GONE
            }
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_lore).setOnClickListener {
            AnimeUtils.pulse(it)
            val cfg = worldManager.config
            worldManager.config = cfg.copy(
                worldBackground = etBackground.text.toString().trim(),
                worldRules = etRules.text.toString().trim(),
                worldRelations = etRelations.text.toString().trim(),
                worldScene = etScene.text.toString().trim(),
                worldStyle = etStyle.text.toString().trim()
            )
            Toast.makeText(this, "世界观已保存", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }

        sheet.setOnShowListener {
            val sections = listOf(
                view.findViewById<View>(R.id.section_background),
                view.findViewById<View>(R.id.section_rules),
                view.findViewById<View>(R.id.section_relations),
                view.findViewById<View>(R.id.section_scene),
                view.findViewById<View>(R.id.section_style)
            )
            sections.forEachIndexed { i, section ->
                AnimeUtils.slideInFromBottom(section, (i + 1) * 80L)
            }
        }

        sheet.show()
    }

    private suspend fun autoGenerateWorldLore(
        etBackground: EditText,
        etRules: EditText,
        etRelations: EditText,
        etScene: EditText,
        etStyle: EditText,
        keywords: String = ""
    ) {
        val config = worldManager.config
        val personaManager = PersonaManager(this)
        personaManager.load()

        val personaDescs = config.memberPersonaIds.mapNotNull { pid ->
            personaManager.getPersona(pid) ?: return@mapNotNull null
            val identity = PromptBuilder.buildIdentity(this, pid)
            val prefs = getSharedPreferences("persona_data_$pid", MODE_PRIVATE)
            buildString {
                append("「${identity.name}」性格${identity.personality}。${identity.speechStyle}。")
                prefs.getString("persona_appearance", "")?.takeIf { it.isNotBlank() }?.let { append(" 外貌：$it。") }
                prefs.getString("persona_preferences", "")?.takeIf { it.isNotBlank() }?.let { append(" 喜好：$it。") }
                prefs.getString("world_setting", "")?.takeIf { it.isNotBlank() }?.let { append(" 世界观：$it。") }
                prefs.getString("world_relationship", "")?.takeIf { it.isNotBlank() }?.let { append(" 关系：$it。") }
            }
        }.joinToString("\n")

        val allPersonaDescs = if (personaDescs.isBlank()) {
            personaManager.getAllPersonas().take(5).mapNotNull { p ->
                val prefs = getSharedPreferences("persona_data_${p.id}", MODE_PRIVATE)
                buildString {
                    append("「${p.name}」性格${p.personality}。${p.speechStyle}。")
                    prefs.getString("persona_appearance", "")?.takeIf { it.isNotBlank() }?.let { append(" 外貌：$it。") }
                    prefs.getString("persona_preferences", "")?.takeIf { it.isNotBlank() }?.let { append(" 喜好：$it。") }
                    prefs.getString("world_setting", "")?.takeIf { it.isNotBlank() }?.let { append(" 世界观：$it。") }
                    prefs.getString("world_relationship", "")?.takeIf { it.isNotBlank() }?.let { append(" 关系：$it。") }
                }.takeIf { it.isNotBlank() }
            }.joinToString("\n")
        } else {
            personaDescs
        }

        val chatSummary = buildChatSummary()

        if (allPersonaDescs.isBlank() && chatSummary.isBlank() && keywords.isBlank()) {
            Toast.makeText(this, "请输入关键词或添加角色", Toast.LENGTH_SHORT).show()
            return
        }

        val sm = SettingsManager(this)
        val client = ApiClient(sm.chatApiUrl, sm.chatApiKey, sm.chatModel,
            sm.llmTemperature, sm.llmTopP, sm.llmFrequencyPenalty, sm.llmPresencePenalty, sm.llmMaxTokens,
            sm.apiProvider)
        val prompt = PromptBuilder.buildAutoWorldLorePrompt(allPersonaDescs, chatSummary, keywords)

        try {
            val response = withContext(Dispatchers.IO) { client.sendSimplePrompt(prompt, "生成世界观") }
            if (response != null && response.text.isNotBlank()) {
                var text = response.text.trim()
                text = text.replace(Regex("```(?:json)?\\s*"), "").replace("```", "").trim()
                val bracketStart = text.indexOf('{')
                val bracketEnd = text.lastIndexOf('}')
                if (bracketStart >= 0 && bracketEnd > bracketStart) {
                    text = text.substring(bracketStart, bracketEnd + 1)
                }
                val json = org.json.JSONObject(text)
                etBackground.setText(json.optString("worldBackground", ""))
                etRules.setText(json.optString("worldRules", ""))
                etRelations.setText(json.optString("worldRelations", ""))
                etScene.setText(json.optString("worldScene", ""))
                etStyle.setText(json.optString("worldStyle", ""))
                Toast.makeText(this, "世界观已自动生成", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "生成失败，请检查API配置", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "生成失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPersonaSelector() {
        val personas = personaManager.getAllPersonas()
        if (personas.isEmpty()) {
            Toast.makeText(this, "暂无角色，请先创建角色", Toast.LENGTH_SHORT).show()
            return
        }

        val config = worldManager.config
        val names = personas.map { it.name }.toTypedArray()
        val checked = personas.map { it.id in config.memberPersonaIds }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("👥 选择参与推演的角色")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("确定") { _, _ ->
                val selectedIds = personas.filterIndexed { i, _ -> checked[i] }.map { it.id }
                worldManager.config = config.copy(memberPersonaIds = selectedIds)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTickIndexDialog() {
        val indices = worldManager.getTickIndexList()
        if (indices.isEmpty()) {
            Toast.makeText(this, "暂无推演记录", Toast.LENGTH_SHORT).show()
            return
        }

        val items = indices.reversed().map { idx ->
            "第${idx.tickIndex}轮 | 第${idx.virtualDay}天 ${String.format("%02d:%02d", idx.virtualHour, idx.virtualMinute)} | ${idx.summary.ifBlank { idx.eventType }}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("📋 推演索引（共${indices.size}轮）")
            .setItems(items) { _, which ->
                val reversedIndices = indices.reversed()
                val selectedIdx = reversedIndices[which]
                val events = worldManager.getEventsByTickIndex(selectedIdx.tickIndex)
                if (events.isNotEmpty()) {
                    val detail = events.joinToString("\n\n") { ev ->
                        "[第${ev.virtualDay}天 ${String.format("%02d:%02d", ev.virtualHour, ev.virtualMinute)}] ${ev.speakerName}：${ev.content}"
                    }
                    val scrollView = android.widget.ScrollView(this)
                    val tv = TextView(this).apply {
                        text = detail
                        textSize = 13f
                        setTextColor(COLOR_DETAIL_TEXT)
                        setPadding(40, 32, 40, 32)
                    }
                    scrollView.addView(tv)
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("第${selectedIdx.tickIndex}轮推演详情")
                        .setView(scrollView)
                        .setPositiveButton("关闭", null)
                        .show()
                } else {
                    Toast.makeText(this, "该轮无事件记录", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun loadStory() {
        val events = worldManager.getStoryEvents()
        if (events.isEmpty()) {
            tvStoryEmpty.visibility = View.VISIBLE
            rvStory.visibility = View.GONE
        } else {
            tvStoryEmpty.visibility = View.GONE
            rvStory.visibility = View.VISIBLE
            storyAdapter.updateEvents(events.reversed())
            if (events.isNotEmpty()) {
                rvStory.scrollToPosition(0)
            }
        }
        loadVwMemoryPool()
    }

    private fun loadVwMemoryPool() {
        try {
            val pool = worldManager.vwMemoryPool
            val stats = pool.getStats()
            tvVwMemoryStats?.text = stats

            if (pool.isEmpty) {
                tvVwMemoryContent?.text = "暂无记忆，开始推演后将自动积累..."
            } else {
                tvVwMemoryContent?.text = pool.getVwBlock()
            }
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.w(TAG, "加载VW记忆池失败: ${e.message}")
            tvVwMemoryContent?.text = "记忆池加载失败"
        }
    }

    private fun showMemoryDetailDialog() {
        val pool = worldManager.vwMemoryPool
        if (pool.isEmpty) {
            Toast.makeText(this, "暂无记忆", Toast.LENGTH_SHORT).show()
            return
        }

        val allEntries = pool.getAllEntries().reversed()

        val grouped = allEntries.groupBy { it.location.ifBlank { "未知地点" } }

        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        for ((loc, locEntries) in grouped) {
            TextView(this).apply {
                text = "📍 $loc (${locEntries.size}条)"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(COLOR_TITLE_TEXT)
                setPadding(0, 24, 0, 12)
            }.let { container.addView(it) }

            for (entry in locEntries) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 16, 20, 16)
                    setBackgroundColor(COLOR_CARD_BACKGROUND)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 4, 0, 4) }
                }

                TextView(this).apply {
                    text = "🕐 第${entry.day}天 ${String.format("%02d", entry.hour)}时"
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(COLOR_SUBTITLE_TEXT)
                }.let { card.addView(it) }

                if (entry.weather.isNotBlank() || entry.mood.isNotBlank()) {
                    TextView(this).apply {
                        text = buildString {
                            if (entry.weather.isNotBlank()) append("🌤 ${entry.weather}  ")
                            if (entry.mood.isNotBlank()) append("💫 ${entry.mood}")
                        }
                        textSize = 12f
                        setTextColor(COLOR_DESC_TEXT)
                        setPadding(0, 4, 0, 0)
                    }.let { card.addView(it) }
                }

                TextView(this).apply {
                    text = entry.fullContent.ifBlank { entry.summary }
                    textSize = 13f
                    setTextColor(COLOR_DETAIL_TEXT)
                    setLineSpacing(4f, 1f)
                    setPadding(0, 8, 0, 0)
                }.let { card.addView(it) }

                container.addView(card)
            }
        }

        scrollView.addView(container)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("🧠 世界记忆池详情 (${allEntries.size}条)")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun refreshUploadedImages() {
        layoutUploadedImages.removeAllViews()
        val images = worldManager.config.uploadedImages
        if (images.isEmpty()) {
            scrollUploadedImages.visibility = View.GONE
            return
        }
        scrollUploadedImages.visibility = View.VISIBLE
        for (path in images) {
            val file = File(path)
            if (!file.exists()) continue
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(160, 160).apply {
                    marginEnd = 8
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener {
                    AlertDialog.Builder(this@VirtualWorldActivity)
                        .setTitle("图片管理")
                        .setItems(arrayOf("查看大图", "删除图片")) { _, which ->
                            when (which) {
                                0 -> showImagePreview(path)
                                1 -> {
                                    worldManager.removeUploadedImage(path)
                                    refreshUploadedImages()
                                    Toast.makeText(this@VirtualWorldActivity, "图片已删除", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .show()
                }
            }
            // 在 IO 线程解码图片，避免阻塞主线程
            lifecycleScope.launch {
                try {
                    val bmp = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(path)
                    }
                    imageView.setImageBitmap(bmp)
                } catch (_: Exception) {}
            }
            layoutUploadedImages.addView(imageView)
        }
    }

    private fun showImagePreview(path: String) {
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(24, 24, 24, 24)
        }
        // 在 IO 线程解码图片，避免阻塞主线程
        lifecycleScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(path)
                }
                imageView.setImageBitmap(bmp)
            } catch (_: Exception) {}
        }
        AlertDialog.Builder(this)
            .setView(imageView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun publishEventToGroupChat(event: StoryEvent) {
        val worldId = worldManager.currentWorldId
        if (worldId.isBlank()) return

        val gcManager = com.aicompanion.groupchat.GroupChatManager(this)
        gcManager.load()
        if (gcManager.getGroup(worldId) == null) return

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
        val senderId = worldManager.config.memberPersonaIds.firstOrNull { pid ->
            personaManager.getPersona(pid)?.name == event.speakerName
        } ?: "narrator"

        val displayText = buildString {
            append("[第${event.virtualDay}天${String.format("%02d", event.virtualHour)}:00] ")
            append(event.content)
        }

        val msg = com.aicompanion.groupchat.GroupMessage(
            senderPersonaId = senderId,
            senderName = event.speakerName,
            text = displayText,
            time = time,
            isUser = false,
            emotion = "neutral"
        )
        gcManager.addMessage(worldId, msg)
    }

    private fun buildChatSummary(): String {
        val worldId = worldManager.currentWorldId
        if (worldId.isBlank()) return ""
        val gcManager = com.aicompanion.groupchat.GroupChatManager(this)
        gcManager.load()
        val msgs = gcManager.getMessages(worldId)
        return msgs.takeLast(20).map { msg ->
            if (msg.isUser) "用户：${msg.text}" else "${msg.senderName}：${msg.text}"
        }.joinToString("\n")
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_WORLD_IMAGE && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            val savedPath = worldManager.saveUploadedImage(uri)
            if (savedPath != null) {
                refreshUploadedImages()
                Toast.makeText(this, "图片已上传", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "图片上传失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUploadedImages()
        loadState()
        loadStory()
    }

    override fun onDestroy() {
        simJob?.cancel()
        simJob = null
        super.onDestroy()
    }

    inner class StoryEventAdapter : RecyclerView.Adapter<StoryEventAdapter.VH>() {

        private var events = listOf<StoryEvent>()

        fun updateEvents(newEvents: List<StoryEvent>) {
            events = newEvents
            notifyDataSetChanged()
        }

        override fun getItemCount() = events.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_story_event, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val event = events[position]
            holder.tvTime.text = "第${event.virtualDay}天 ${String.format("%02d:%02d", event.virtualHour, event.virtualMinute)}"
            holder.tvSpeaker.text = event.speakerName
            holder.tvContent.text = event.content

            // 点击事件内容查看完整详情
            holder.itemView.setOnClickListener {
                val clickedEvent = events[position]
                val detailText = buildString {
                    appendLine("【时间】第${clickedEvent.virtualDay}天 ${String.format("%02d:%02d", clickedEvent.virtualHour, clickedEvent.virtualMinute)}")
                    appendLine("【说话人】${clickedEvent.speakerName}")
                    appendLine("【类型】${clickedEvent.eventType}")
                    appendLine()
                    appendLine("【完整内容】")
                    append(clickedEvent.content)
                    if (clickedEvent.imageUrl.isNotBlank()) {
                        appendLine()
                        appendLine("【配图】${clickedEvent.imageUrl}")
                    }
                }

                val scrollView = android.widget.ScrollView(this@VirtualWorldActivity)
                val textView = android.widget.TextView(this@VirtualWorldActivity).apply {
                    text = detailText
                    textSize = 14f
                    setTextColor(COLOR_DETAIL_TEXT)
                    setPadding(48, 40, 48, 40)
                    setLineSpacing(0f, 1.4f)
                }
                scrollView.addView(textView)

                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@VirtualWorldActivity)
                    .setTitle("第${clickedEvent.virtualDay}天${clickedEvent.virtualHour}时 · 详情")
                    .setView(scrollView)
                    .setPositiveButton("关闭", null)
                    .show()
            }

            if (event.imageUrl.isNotBlank()) {
                val file = File(event.imageUrl)
                if (file.exists()) {
                    holder.ivImage.visibility = View.VISIBLE
                    // 在 IO 线程解码图片，避免阻塞主线程
                    lifecycleScope.launch {
                        try {
                            val bmp = withContext(Dispatchers.IO) {
                                BitmapFactory.decodeFile(event.imageUrl)
                            }
                            // 验证 holder 仍显示同一事件，避免视图复用问题
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                                events.getOrNull(holder.bindingAdapterPosition)?.imageUrl == event.imageUrl) {
                                holder.ivImage.setImageBitmap(bmp)
                            }
                        } catch (_: Exception) {
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                                events.getOrNull(holder.bindingAdapterPosition)?.imageUrl == event.imageUrl) {
                                holder.ivImage.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    holder.ivImage.visibility = View.GONE
                }
            } else {
                holder.ivImage.visibility = View.GONE
            }

            holder.itemView.alpha = 0f
            holder.itemView.translationY = 20f
            holder.itemView.scaleX = 0.8f
            holder.itemView.scaleY = 0.8f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setStartDelay((position * 50L).coerceAtMost(500))
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                .start()

            val dot = holder.timelineDot
            dot.scaleX = 1f
            dot.scaleY = 1f
            dot.animate()
                .scaleX(1.8f)
                .scaleY(1.8f)
                .setDuration(300)
                .setStartDelay((position * 50L).coerceAtMost(500) + 200)
                .withEndAction {
                    dot.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTime: TextView = view.findViewById(R.id.tv_event_time)
            val tvSpeaker: TextView = view.findViewById(R.id.tv_event_speaker)
            val tvContent: TextView = view.findViewById(R.id.tv_event_content)
            val ivImage: ImageView = view.findViewById(R.id.iv_event_image)
            val timelineDot: View = view.findViewById(R.id.timeline_dot)
        }
    }
}
