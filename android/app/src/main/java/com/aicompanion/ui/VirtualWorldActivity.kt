package com.aicompanion.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.aicompanion.persona.PersonaManager
import com.aicompanion.virtualworld.*
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    private var isSimRunning = false

    companion object {
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

        storyAdapter = StoryEventAdapter()
        rvStory.layoutManager = LinearLayoutManager(this)
        rvStory.adapter = storyAdapter
    }

    private fun loadState() {
        val state = worldManager.state
        val config = worldManager.config

        tvVirtualDay.text = "第${state.dayCount}天"
        tvVirtualHour.text = String.format("%02d:00", state.hourOfDay)
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
        val ratioAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ratioOptions)
        ratioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalOptions)
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
        Toast.makeText(this, "虚拟世界推演已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopSimulation() {
        isSimRunning = false
        worldManager.isRunning = false
        updateSimStatus()
        Toast.makeText(this, "推演已暂停", Toast.LENGTH_SHORT).show()
    }

    private fun updateSimStatus() {
        if (isSimRunning) {
            tvSimStatus.text = "▶ 推演中"
            tvSimStatus.setTextColor(0xFF64ffda.toInt())
            btnStartStop.text = "⏸ 暂停推演"
        } else {
            tvSimStatus.text = "⏸ 已暂停"
            tvSimStatus.setTextColor(0xFF8899bb.toInt())
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
            } else {
                Toast.makeText(this@VirtualWorldActivity, "推演失败，请检查API配置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWorldLoreEditor() {
        val config = worldManager.config
        val scrollView = android.widget.ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 20)
        }

        val fields = listOf(
            Triple("🌍 世界背景", "描述这个世界的整体设定：时代、地理、文明水平、种族...", config.worldBackground),
            Triple("📜 世界规则", "这个世界有什么特殊规则？魔法体系、科技限制、社会制度...", config.worldRules),
            Triple("👥 角色关系", "角色之间的关系：敌对、盟友、师徒、恋人...", config.worldRelations),
            Triple("🎬 初始场景", "故事开始时的场景：地点、时间、正在发生的事...", config.worldScene),
            Triple("✍️ 叙事风格", "希望推演的风格：轻松日常、史诗冒险、黑暗悬疑、治愈温馨...", config.worldStyle)
        )

        val editors = mutableListOf<EditText>()
        for ((label, hint, value) in fields) {
            val tvLabel = TextView(this).apply {
                text = label
                setTextColor(0xFFc4b5fd.toInt())
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            container.addView(tvLabel)

            val et = EditText(this).apply {
                setText(value)
                setPadding(20, 12, 20, 12)
                setTextSize(13f)
                setTextColor(0xFFe8e8f0.toInt())
                setHintTextColor(0xFF556677.toInt())
                this.hint = hint
                setBackgroundColor(0xFF1a1a3e.toInt())
                minLines = 2
                maxLines = 8
            }
            editors.add(et)
            container.addView(et)

            val spacer = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 12)
            }
            container.addView(spacer)
        }

        scrollView.addView(container)

        AlertDialog.Builder(this)
            .setTitle("📝 世界观设定")
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val cfg = worldManager.config
                worldManager.config = cfg.copy(
                    worldBackground = editors[0].text.toString().trim(),
                    worldRules = editors[1].text.toString().trim(),
                    worldRelations = editors[2].text.toString().trim(),
                    worldScene = editors[3].text.toString().trim(),
                    worldStyle = editors[4].text.toString().trim()
                )
                Toast.makeText(this, "世界观已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
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
            try {
                val bmp = BitmapFactory.decodeFile(path)
                imageView.setImageBitmap(bmp)
            } catch (_: Exception) {}
            layoutUploadedImages.addView(imageView)
        }
    }

    private fun showImagePreview(path: String) {
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(24, 24, 24, 24)
        }
        try {
            val bmp = BitmapFactory.decodeFile(path)
            imageView.setImageBitmap(bmp)
        } catch (_: Exception) {}
        AlertDialog.Builder(this)
            .setView(imageView)
            .setPositiveButton("关闭", null)
            .show()
    }

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
            holder.tvTime.text = "第${event.virtualDay}天 ${String.format("%02d", event.virtualHour)}:00"
            holder.tvSpeaker.text = event.speakerName
            holder.tvContent.text = event.content

            if (event.imageUrl.isNotBlank()) {
                val file = File(event.imageUrl)
                if (file.exists()) {
                    try {
                        val bmp = BitmapFactory.decodeFile(event.imageUrl)
                        holder.ivImage.setImageBitmap(bmp)
                        holder.ivImage.visibility = View.VISIBLE
                    } catch (_: Exception) {
                        holder.ivImage.visibility = View.GONE
                    }
                } else {
                    holder.ivImage.visibility = View.GONE
                }
            } else {
                holder.ivImage.visibility = View.GONE
            }

            holder.itemView.alpha = 0f
            holder.itemView.translationY = 20f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((position * 40L).coerceAtMost(400))
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                .start()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTime: TextView = view.findViewById(R.id.tv_event_time)
            val tvSpeaker: TextView = view.findViewById(R.id.tv_event_speaker)
            val tvContent: TextView = view.findViewById(R.id.tv_event_content)
            val ivImage: ImageView = view.findViewById(R.id.iv_event_image)
        }
    }
}
