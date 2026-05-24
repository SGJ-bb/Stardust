package com.aicompanion.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.AppContainer
import com.aicompanion.R
import com.aicompanion.persona.Persona
import com.aicompanion.persona.PersonaManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HomeActivity : AppCompatActivity() {

    private lateinit var personaManager: PersonaManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PersonaAdapter
    private var pendingAvatarPath: String? = null
    private var pendingAvatarIv: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("is_activated", false)) {
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_home)

        personaManager = PersonaManager(this)
        personaManager.load()

        recyclerView = findViewById(R.id.rv_personas)
        adapter = PersonaAdapter(personaManager.getAllPersonas())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add_persona).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            com.aicompanion.anim.AnimeUtils.springScale(it, 0.9f, 1f, 400)
            showAddPersonaDialog()
        }

        val btnMoments = findViewById<View>(R.id.btn_moments_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnMoments)
        btnMoments.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.moments.MomentsActivity::class.java))
        }

        val btnDiary = findViewById<View>(R.id.btn_diary_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnDiary)
        btnDiary.setOnClickListener {
            showPersonaPicker("日记") { personaId ->
                val intent = Intent(this, com.aicompanion.ui.DiaryActivity::class.java)
                intent.putExtra("persona_id", personaId)
                startActivity(intent)
            }
        }

        val btnGroupChat = findViewById<View>(R.id.btn_group_chat_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnGroupChat)
        btnGroupChat.setOnClickListener {
            startActivity(Intent(this, com.aicompanion.groupchat.GroupChatListActivity::class.java))
        }

        val btnVirtualWorld = findViewById<View>(R.id.btn_virtual_world_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnVirtualWorld)
        btnVirtualWorld.setOnClickListener {
            showPersonaPicker("虚拟世界") { personaId ->
                val intent = Intent(this, com.aicompanion.ui.VirtualWorldActivity::class.java)
                startActivity(intent)
            }
        }

        val btnProfile = findViewById<View>(R.id.btn_profile_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnProfile)
        btnProfile.setOnClickListener {
            showPersonaPicker("档案") { personaId ->
                val intent = Intent(this, com.aicompanion.ui.ProfileActivity::class.java)
                intent.putExtra("persona_id", personaId)
                startActivity(intent)
            }
        }

        val btnSettings = findViewById<View>(R.id.btn_settings_entry)
        com.aicompanion.anim.AnimeUtils.setupTouchScale(btnSettings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.btn_export_personas).setOnClickListener {
            exportPersonas()
        }

        findViewById<View>(R.id.btn_import_personas).setOnClickListener {
            importPersonas()
        }

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        recyclerView.post {
            com.aicompanion.anim.AnimeUtils.staggerSlideIn(recyclerView, fromRight = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingAvatarIv = null
        pendingAvatarPath = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri != null && (requestCode == 1001 || requestCode == 1002)) {
                val savedPath = copyAvatarToPersonaDir(uri, requestCode.toString())
                if (savedPath != null) {
                    pendingAvatarPath = savedPath
                    pendingAvatarIv?.let { iv ->
                        loadAvatarIntoView(iv, savedPath)
                        com.aicompanion.anim.AnimeUtils.springScale(iv, 0.8f, 1f, 400)
                    }
                }
            }
            if (requestCode == REQUEST_IMPORT_PERSONAS && uri != null) {
                try {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
                    val result = personaManager.importPersonas(json)
                    refreshList()
                    val msg = if (result.errors.isEmpty()) {
                        "导入成功：${result.imported}个角色"
                    } else {
                        "导入${result.imported}个，跳过${result.skipped}个，错误：${result.errors.joinToString()}"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun copyAvatarToPersonaDir(uri: Uri, suffix: String): String? {
        return try {
            val dir = File(filesDir, "personas/avatars")
            dir.mkdirs()
            val file = File(dir, "avatar_${System.currentTimeMillis()}_$suffix.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(this, "头像保存失败", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun loadAvatarIntoView(iv: ImageView, path: String) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 128, 128)
            opts.inJustDecodeBounds = false
            val bmp = BitmapFactory.decodeFile(path, opts)
            iv.setImageBitmap(bmp)
        } catch (e: Exception) { com.aicompanion.util.AppLogger.e("HomeActivity", "loadAvatarIntoView: ${e.message}") }
    }

    private fun refreshList() {
        adapter.updateData(personaManager.getAllPersonas())
    }

    private data class AIGeneratedPersona(
        val name: String,
        val desc: String,
        val greeting: String,
        val personality: String,
        val speechStyle: String,
        val catchphrases: String,
        val appearance: String,
        val preferences: String,
        val worldSetting: String,
        val worldRelationship: String,
        val worldRules: String,
        val userIdentity: String = "",
        val userAbilities: String = ""
    )

    private fun generatePersonaWithAI(keywords: String): AIGeneratedPersona? {
        val apiClient = com.aicompanion.AppContainer.apiClient ?: return null
        val prompt = buildString {
            append("请为一个AI角色生成完整、详尽的角色设定。\n")
            if (keywords.isNotBlank()) {
                append("用户关键词：$keywords\n")
            } else {
                append("请随机发挥创意，生成一个有趣的角色设定。\n")
            }
            append("\n请严格按照以下JSON格式输出，不要加任何其他文字：\n")
            append("{\n")
            append("  \"name\": \"角色名（2-6个字，有特色）\",\n")
            append("  \"desc\": \"角色简介（20-40字，一句话概括角色身份和特点）\",\n")
            append("  \"greeting\": \"开场白（角色第一次见到用户时说的话，30-60字，体现角色性格）\",\n")
            append("  \"personality\": \"性格描述（40-80字，描述核心性格特征、情感表达方式、内心矛盾）\",\n")
            append("  \"speechStyle\": \"说话风格（30-50字，描述口癖、语气、常用表达方式）\",\n")
            append("  \"catchphrases\": \"口头禅（3-5个，用换行分隔，如：哼\\n才不是呢\\n笨蛋）\",\n")
            append("  \"appearance\": \"外貌描述（30-50字，描述外观特征、穿着、标志性特征）\",\n")
            append("  \"preferences\": \"喜好（喜欢和讨厌的事物，各2-3个，用换行分隔）\",\n")
            append("  \"worldSetting\": \"世界观设定（40-80字，描述角色所处的世界背景、身份地位）\",\n")
            append("  \"worldRelationship\": \"关系设定（30-50字，描述角色与用户的关系、称呼方式）\",\n")
            append("  \"worldRules\": \"行为规则（3条，用换行分隔，描述角色必须遵守的行为准则）\",\n")
            append("  \"userIdentity\": \"用户身份（20-40字，描述用户在这个角色眼中的身份，如：主人、勇者、同班同学）\",\n")
            append("  \"userAbilities\": \"用户能力/特征（20-40字，描述用户拥有的能力或特征，如：会魔法、温柔善良、擅长做饭）\"\n")
            append("}\n")
        }

        return try {
            val response = apiClient.sendSimplePrompt(prompt, "生成角色设定")
            val text = response?.text?.trim() ?: return null
            val jsonStr = extractJson(text) ?: return null
            val json = org.json.JSONObject(jsonStr)
            AIGeneratedPersona(
                name = json.optString("name", "").ifBlank { return null },
                desc = json.optString("desc", ""),
                greeting = json.optString("greeting", ""),
                personality = json.optString("personality", ""),
                speechStyle = json.optString("speechStyle", ""),
                catchphrases = json.optString("catchphrases", ""),
                appearance = json.optString("appearance", ""),
                preferences = json.optString("preferences", ""),
                worldSetting = json.optString("worldSetting", ""),
                worldRelationship = json.optString("worldRelationship", ""),
                worldRules = json.optString("worldRules", ""),
                userIdentity = json.optString("userIdentity", ""),
                userAbilities = json.optString("userAbilities", "")
            )
        } catch (e: Exception) {
            com.aicompanion.util.AppLogger.e("HomeActivity", "generatePersonaWithAI failed: ${e.message}")
            null
        }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun buildAutoPrompt(result: AIGeneratedPersona): String {
        return buildString {
            append("你是「${result.name}」。")
            if (result.desc.isNotBlank()) append("\n简介：${result.desc}")
            if (result.appearance.isNotBlank()) append("\n外貌：${result.appearance}")
            if (result.personality.isNotBlank()) append("\n性格：${result.personality}")
            if (result.speechStyle.isNotBlank()) append("\n说话风格：${result.speechStyle}")
            if (result.catchphrases.isNotBlank()) append("\n常用口头禅：${result.catchphrases}")
            if (result.preferences.isNotBlank()) append("\n喜好：${result.preferences}")
            if (result.worldSetting.isNotBlank()) append("\n世界观设定：${result.worldSetting}")
            if (result.worldRelationship.isNotBlank()) append("\n你和用户的关系：${result.worldRelationship}")
            if (result.worldRules.isNotBlank()) append("\n规则：${result.worldRules}")
            if (result.userIdentity.isNotBlank()) append("\n用户身份：${result.userIdentity}")
            if (result.userAbilities.isNotBlank()) append("\n用户能力/特征：${result.userAbilities}")
        }
    }

    private fun applyTheme() {
        val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
        try {
            val bgColor = try {
                android.graphics.Color.parseColor(scheme.backgroundDark)
            } catch (_: Exception) {
                0xFF0a0a1a.toInt()
            }
            findViewById<View>(R.id.home_root)?.setBackgroundColor(bgColor)
        } catch (e: Exception) { com.aicompanion.util.AppLogger.e("HomeActivity", "applyTheme: ${e.message}") }
    }

    private fun showPersonaPicker(featureName: String, onSelect: (String) -> Unit) {
        val personas = personaManager.getAllPersonas()
        if (personas.isEmpty()) {
            Toast.makeText(this, "暂无角色，请先创建角色", Toast.LENGTH_SHORT).show()
            return
        }
        if (personas.size == 1) {
            onSelect(personas.first().id)
            return
        }

        val sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_persona_picker, null)
        val tvTitle = sheetView.findViewById<TextView>(R.id.tv_picker_title)
        tvTitle?.text = "选择${featureName}的角色"

        val sheet = BottomSheetDialog(this)

        val recycler = sheetView.findViewById<RecyclerView>(R.id.rv_picker_personas)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_persona_picker, parent, false)
                return object : RecyclerView.ViewHolder(itemView) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val p = personas[position]
                val tvName = holder.itemView.findViewById<TextView>(R.id.tv_picker_name)
                val tvDesc = holder.itemView.findViewById<TextView>(R.id.tv_picker_desc)
                val ivAvatar = holder.itemView.findViewById<ImageView>(R.id.iv_picker_avatar)
                tvName?.text = p.name
                tvDesc?.text = p.personality.ifBlank { p.description.ifBlank { "暂无简介" } }
                val avatarPath = p.avatarPath.ifBlank {
                    getSharedPreferences("avatar_data", MODE_PRIVATE).getString("ai_avatar", "") ?: ""
                }
                if (avatarPath.isNotBlank() && File(avatarPath).exists()) {
                    try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(avatarPath, opts)
                        opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 64, 64)
                        opts.inJustDecodeBounds = false
                        ivAvatar?.setImageBitmap(BitmapFactory.decodeFile(avatarPath, opts))
                    } catch (e: Exception) { com.aicompanion.util.AppLogger.e("HomeActivity", "showPersonaPicker: ${e.message}") }
                }
                holder.itemView.setOnClickListener {
                    com.aicompanion.anim.AnimeUtils.pulse(it)
                    onSelect(p.id)
                    sheet.dismiss()
                }
            }
            override fun getItemCount() = personas.size
        }

        sheet.setContentView(sheetView)
        sheet.show()
    }

    private fun showAddPersonaDialog() {
        pendingAvatarPath = null
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_persona, null)
        val etName = view.findViewById<EditText>(R.id.et_persona_name)
        val etDesc = view.findViewById<EditText>(R.id.et_persona_desc)
        val etGreeting = view.findViewById<EditText>(R.id.et_persona_greeting)
        val etUserIdentity = view.findViewById<EditText>(R.id.et_user_identity)
        val etUserAbilities = view.findViewById<EditText>(R.id.et_user_abilities)
        val etPersonality = view.findViewById<EditText>(R.id.et_persona_personality)
        val etSpeech = view.findViewById<EditText>(R.id.et_persona_speech)
        val etCatchphrases = view.findViewById<EditText>(R.id.et_persona_catchphrases)
        val etAppearance = view.findViewById<EditText>(R.id.et_persona_appearance)
        val etPreferences = view.findViewById<EditText>(R.id.et_persona_preferences)
        val etWorldSetting = view.findViewById<EditText>(R.id.et_world_setting)
        val etWorldRelationship = view.findViewById<EditText>(R.id.et_world_relationship)
        val etWorldRules = view.findViewById<EditText>(R.id.et_world_rules)
        val etPrompt = view.findViewById<EditText>(R.id.et_persona_prompt)
        val ivPreview = view.findViewById<ImageView>(R.id.iv_persona_avatar_preview)
        val etKeywords = view.findViewById<EditText>(R.id.et_ai_keywords)
        val btnAiGenerate = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_ai_generate)
        val progressAi = view.findViewById<ProgressBar>(R.id.progress_ai_generate)
        val spinnerGender = view.findViewById<android.widget.Spinner>(R.id.spinner_persona_gender)
        val etUserPersonalityDef = view.findViewById<EditText>(R.id.et_user_personality_def)
        pendingAvatarIv = ivPreview

        if (spinnerGender != null) {
            val genderOptions = arrayOf("未设定", "男", "女")
            val genderAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions)
            genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerGender.adapter = genderAdapter
            val savedGender = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("user_gender", "") ?: ""
            val genderIndex = when (savedGender) {
                "male" -> 1; "female" -> 2; else -> 0
            }
            spinnerGender.setSelection(genderIndex)
        }

        val savedPersonalityDef = getSharedPreferences("companion_settings", MODE_PRIVATE)
            .getString("user_personality_def", "") ?: ""
        etUserPersonalityDef?.setText(savedPersonalityDef)

        view.findViewById<View>(R.id.btn_select_avatar).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, 1001)
        }

        btnAiGenerate.setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val keywords = etKeywords.text.toString().trim()
            btnAiGenerate.isEnabled = false
            btnAiGenerate.text = "生成中..."
            progressAi.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    generatePersonaWithAI(keywords)
                }
                btnAiGenerate.isEnabled = true
                btnAiGenerate.text = "生成"
                progressAi.visibility = View.GONE
                if (result != null) {
                    etName.setText(result.name)
                    etDesc.setText(result.desc)
                    etGreeting.setText(result.greeting)
                    etPersonality.setText(result.personality)
                    etSpeech.setText(result.speechStyle)
                    etCatchphrases.setText(result.catchphrases)
                    etAppearance.setText(result.appearance)
                    etPreferences.setText(result.preferences)
                    etWorldSetting.setText(result.worldSetting)
                    etWorldRelationship.setText(result.worldRelationship)
                    etWorldRules.setText(result.worldRules)
                    etUserIdentity.setText(result.userIdentity)
                    etUserAbilities.setText(result.userAbilities)
                    val autoPrompt = buildAutoPrompt(result)
                    etPrompt.setText(autoPrompt)
                    com.aicompanion.anim.AnimeUtils.springScale(etName)
                    etDesc.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etDesc) }, 50)
                    etGreeting.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etGreeting) }, 100)
                    etPersonality.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etPersonality) }, 150)
                    etSpeech.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etSpeech) }, 200)
                    etCatchphrases.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etCatchphrases) }, 250)
                    etAppearance.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etAppearance) }, 300)
                    etPreferences.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etPreferences) }, 350)
                    etWorldSetting.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etWorldSetting) }, 400)
                    etWorldRelationship.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etWorldRelationship) }, 450)
                    etWorldRules.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etWorldRules) }, 500)
                    Toast.makeText(this@HomeActivity, "✨ 角色设定已生成", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "生成失败，请检查API配置", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)
        sheet.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()

        sheet.setOnShowListener {
            com.aicompanion.anim.AnimeUtils.fadeInScale(ivPreview, 100)
            com.aicompanion.anim.AnimeUtils.slideInFromBottom(etName, 150)
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create_persona).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val name = etName.text.toString().trim()
            if (name.isBlank()) {
                etName.animate().translationXBy(-20f).setDuration(60).withEndAction {
                    etName.animate().translationXBy(40f).setDuration(60).withEndAction {
                        etName.animate().translationXBy(-20f).setDuration(60).withEndAction {
                            etName.animate().translationX(0f).setDuration(100).start()
                        }.start()
                    }.start()
                }.start()
                return@setOnClickListener
            }
            val personaId = java.util.UUID.randomUUID().toString()
            val personality = etPersonality.text.toString().trim()
            val speechStyle = etSpeech.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val greeting = etGreeting.text.toString().trim()
            val userIdentity = etUserIdentity.text.toString().trim()
            val userAbilities = etUserAbilities.text.toString().trim()
            val catchphrases = etCatchphrases.text.toString().trim()
            val appearance = etAppearance.text.toString().trim()
            val preferences = etPreferences.text.toString().trim()
            val worldSetting = etWorldSetting.text.toString().trim()
            val worldRelationship = etWorldRelationship.text.toString().trim()
            val worldRules = etWorldRules.text.toString().trim()
            val prompt = etPrompt.text.toString().trim()

            val persona = Persona(
                id = personaId,
                name = name,
                personality = personality,
                speechStyle = speechStyle,
                prompt = prompt,
                avatarPath = pendingAvatarPath ?: ""
            )
            personaManager.addPersona(persona)

            val personaPrefs = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)
            personaPrefs.edit().apply {
                putString("persona_desc", desc)
                putString("persona_greeting", greeting)
                putString("persona_catchphrases", catchphrases)
                putString("persona_appearance", appearance)
                putString("persona_preferences", preferences)
                putString("world_setting", worldSetting)
                putString("world_relationship", worldRelationship)
                putString("world_rules", worldRules)
                apply()
            }

            val globalPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            globalPrefs.edit().apply {
                putString("global_user_identity", userIdentity)
                putString("global_user_abilities", userAbilities)
                val genderPos = spinnerGender?.selectedItemPosition ?: 0
                putString("user_gender", when (genderPos) { 1 -> "male"; 2 -> "female"; else -> "" })
                apply()
            }

            val userPersonalityDef = etUserPersonalityDef?.text?.toString()?.trim() ?: ""
            getSharedPreferences("companion_settings", MODE_PRIVATE).edit()
                .putString("user_personality_def", userPersonalityDef).apply()

            refreshList()
            sheet.dismiss()
            Toast.makeText(this@HomeActivity, "角色「$name」已创建", Toast.LENGTH_SHORT).show()
        }

        sheet.show()
    }

    private fun showEditPersonaDialog(persona: Persona) {
        pendingAvatarPath = persona.avatarPath
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_persona, null)
        val etName = view.findViewById<EditText>(R.id.et_persona_name)
        val etDesc = view.findViewById<EditText>(R.id.et_persona_desc)
        val etGreeting = view.findViewById<EditText>(R.id.et_persona_greeting)
        val etUserIdentity = view.findViewById<EditText>(R.id.et_user_identity)
        val etUserAbilities = view.findViewById<EditText>(R.id.et_user_abilities)
        val etPersonality = view.findViewById<EditText>(R.id.et_persona_personality)
        val etSpeech = view.findViewById<EditText>(R.id.et_persona_speech)
        val etCatchphrases = view.findViewById<EditText>(R.id.et_persona_catchphrases)
        val etAppearance = view.findViewById<EditText>(R.id.et_persona_appearance)
        val etPreferences = view.findViewById<EditText>(R.id.et_persona_preferences)
        val etWorldSetting = view.findViewById<EditText>(R.id.et_world_setting)
        val etWorldRelationship = view.findViewById<EditText>(R.id.et_world_relationship)
        val etWorldRules = view.findViewById<EditText>(R.id.et_world_rules)
        val etPrompt = view.findViewById<EditText>(R.id.et_persona_prompt)
        val ivPreview = view.findViewById<ImageView>(R.id.iv_persona_avatar_preview)
        val etKeywords = view.findViewById<EditText>(R.id.et_ai_keywords)
        val btnAiGenerate = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_ai_generate)
        val progressAi = view.findViewById<ProgressBar>(R.id.progress_ai_generate)
        val spinnerGender = view.findViewById<android.widget.Spinner>(R.id.spinner_persona_gender)
        val etUserPersonalityDef = view.findViewById<EditText>(R.id.et_user_personality_def)
        pendingAvatarIv = ivPreview

        if (spinnerGender != null) {
            val genderOptions = arrayOf("未设定", "男", "女")
            val genderAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions)
            genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerGender.adapter = genderAdapter
            val savedGender = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("user_gender", "") ?: ""
            val genderIndex = when (savedGender) { "male" -> 1; "female" -> 2; else -> 0 }
            spinnerGender.setSelection(genderIndex)
        }

        val savedPersonalityDef = getSharedPreferences("companion_settings", MODE_PRIVATE)
            .getString("user_personality_def", "") ?: ""
        etUserPersonalityDef?.setText(savedPersonalityDef)

        etName.setText(persona.name)
        etPersonality.setText(persona.personality)
        etSpeech.setText(persona.speechStyle)
        etPrompt.setText(persona.prompt)

        val personaPrefs = getSharedPreferences("persona_data_${persona.id}", MODE_PRIVATE)
        etDesc.setText(personaPrefs.getString("persona_desc", ""))
        etGreeting.setText(personaPrefs.getString("persona_greeting", ""))
        etUserIdentity.setText(getSharedPreferences("app_prefs", MODE_PRIVATE).getString("global_user_identity", ""))
        etUserAbilities.setText(getSharedPreferences("app_prefs", MODE_PRIVATE).getString("global_user_abilities", ""))
        etCatchphrases.setText(personaPrefs.getString("persona_catchphrases", ""))
        etAppearance.setText(personaPrefs.getString("persona_appearance", ""))
        etPreferences.setText(personaPrefs.getString("persona_preferences", ""))
        etWorldSetting.setText(personaPrefs.getString("world_setting", ""))
        etWorldRelationship.setText(personaPrefs.getString("world_relationship", ""))
        etWorldRules.setText(personaPrefs.getString("world_rules", ""))

        if (persona.avatarPath.isNotBlank() && File(persona.avatarPath).exists()) {
            loadAvatarIntoView(ivPreview, persona.avatarPath)
        }

        view.findViewById<View>(R.id.btn_select_avatar).setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, 1002)
        }

        btnAiGenerate.setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val keywords = etKeywords.text.toString().trim()
            btnAiGenerate.isEnabled = false
            btnAiGenerate.text = "生成中..."
            progressAi.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    generatePersonaWithAI(keywords)
                }
                btnAiGenerate.isEnabled = true
                btnAiGenerate.text = "生成"
                progressAi.visibility = View.GONE
                if (result != null) {
                    etName.setText(result.name)
                    etDesc.setText(result.desc)
                    etGreeting.setText(result.greeting)
                    etPersonality.setText(result.personality)
                    etSpeech.setText(result.speechStyle)
                    etCatchphrases.setText(result.catchphrases)
                    etAppearance.setText(result.appearance)
                    etPreferences.setText(result.preferences)
                    etWorldSetting.setText(result.worldSetting)
                    etWorldRelationship.setText(result.worldRelationship)
                    etWorldRules.setText(result.worldRules)
                    etUserIdentity.setText(result.userIdentity)
                    etUserAbilities.setText(result.userAbilities)
                    val autoPrompt = buildAutoPrompt(result)
                    etPrompt.setText(autoPrompt)
                    com.aicompanion.anim.AnimeUtils.springScale(etName)
                    etPersonality.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etPersonality) }, 100)
                    etSpeech.postDelayed({ com.aicompanion.anim.AnimeUtils.springScale(etSpeech) }, 200)
                    Toast.makeText(this@HomeActivity, "✨ 角色设定已生成", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@HomeActivity, "生成失败，请检查API配置", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)
        sheet.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()

        sheet.setOnShowListener {
            com.aicompanion.anim.AnimeUtils.fadeInScale(ivPreview, 100)
            com.aicompanion.anim.AnimeUtils.slideInFromBottom(etName, 150)
        }

        val btnCreate = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create_persona)
        val btnDelete = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_persona)
        btnCreate.text = "保存"
        btnDelete.visibility = View.VISIBLE

        btnDelete.setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            if (persona.id == "default") {
                Toast.makeText(this@HomeActivity, "默认角色不可删除", Toast.LENGTH_SHORT).show()
            } else {
                personaManager.deletePersona(persona.id)
                refreshList()
                sheet.dismiss()
            }
        }

        btnCreate.setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            val name = etName.text.toString().trim()
            if (name.isBlank()) {
                etName.animate().translationXBy(-20f).setDuration(60).withEndAction {
                    etName.animate().translationXBy(40f).setDuration(60).withEndAction {
                        etName.animate().translationXBy(-20f).setDuration(60).withEndAction {
                            etName.animate().translationX(0f).setDuration(100).start()
                        }.start()
                    }.start()
                }.start()
                return@setOnClickListener
            }
            val personality = etPersonality.text.toString().trim()
            val speechStyle = etSpeech.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val greeting = etGreeting.text.toString().trim()
            val userIdentity = etUserIdentity.text.toString().trim()
            val userAbilities = etUserAbilities.text.toString().trim()
            val catchphrases = etCatchphrases.text.toString().trim()
            val appearance = etAppearance.text.toString().trim()
            val preferences = etPreferences.text.toString().trim()
            val worldSetting = etWorldSetting.text.toString().trim()
            val worldRelationship = etWorldRelationship.text.toString().trim()
            val worldRules = etWorldRules.text.toString().trim()
            val prompt = etPrompt.text.toString().trim()

            personaManager.updatePersona(persona.id) {
                it.copy(
                    name = name,
                    personality = personality,
                    speechStyle = speechStyle,
                    prompt = prompt,
                    avatarPath = pendingAvatarPath ?: it.avatarPath
                )
            }

            val editPrefs = getSharedPreferences("persona_data_${persona.id}", MODE_PRIVATE)
            editPrefs.edit().apply {
                putString("persona_desc", desc)
                putString("persona_greeting", greeting)
                putString("persona_catchphrases", catchphrases)
                putString("persona_appearance", appearance)
                putString("persona_preferences", preferences)
                putString("world_setting", worldSetting)
                putString("world_relationship", worldRelationship)
                putString("world_rules", worldRules)
                apply()
            }

            val globalPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            globalPrefs.edit().apply {
                putString("global_user_identity", userIdentity)
                putString("global_user_abilities", userAbilities)
                val genderPos = spinnerGender?.selectedItemPosition ?: 0
                putString("user_gender", when (genderPos) { 1 -> "male"; 2 -> "female"; else -> "" })
                apply()
            }

            val userPersonalityDef = etUserPersonalityDef?.text?.toString()?.trim() ?: ""
            getSharedPreferences("companion_settings", MODE_PRIVATE).edit()
                .putString("user_personality_def", userPersonalityDef).apply()

            refreshList()
            sheet.dismiss()
        }

        sheet.show()
    }

    inner class PersonaAdapter(private var items: List<Persona>) : RecyclerView.Adapter<PersonaAdapter.VH>() {
        private val avatarCache = android.util.LruCache<String, android.graphics.Bitmap>(12)
        private val lastMsgCache = mutableMapOf<String, String>()

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_persona_name)
            val tvDesc: TextView = view.findViewById(R.id.tv_persona_desc)
            val ivAvatar: ImageView = view.findViewById(R.id.iv_persona_avatar)
            val cardAvatar: MaterialCardView = view.findViewById(R.id.card_persona_avatar)
            val tvLastMsg: TextView = view.findViewById(R.id.tv_last_message)
            val btnEdit: View = view.findViewById(R.id.btn_edit_persona)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(this@HomeActivity)
                .inflate(R.layout.item_persona, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val persona = items[position]
            holder.tvName.text = persona.name
            holder.tvDesc.text = persona.personality.ifBlank { persona.description.ifBlank { "暂无简介" } }

            val lastMsg = lastMsgCache[persona.id] ?: getLastMessage(persona.id).also { lastMsgCache[persona.id] = it }
            holder.tvLastMsg.text = lastMsg

            val avatarPath = persona.avatarPath.ifBlank {
                val avatarPrefs = this@HomeActivity.getSharedPreferences("avatar_data", MODE_PRIVATE)
                avatarPrefs.getString("ai_avatar", "") ?: ""
            }
            if (avatarPath.isNotBlank() && File(avatarPath).exists()) {
                try {
                    var bmp = avatarCache.get(avatarPath)
                    if (bmp == null) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(avatarPath, opts)
                        opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, 96, 96)
                        opts.inJustDecodeBounds = false
                        bmp = BitmapFactory.decodeFile(avatarPath, opts)
                        if (bmp != null) avatarCache.put(avatarPath, bmp)
                    }
                    if (bmp != null) holder.ivAvatar.setImageBitmap(bmp)
                } catch (e: Exception) { com.aicompanion.util.AppLogger.e("HomeActivity", "onBindViewHolder: ${e.message}") }
            }

            holder.itemView.setOnClickListener {
                com.aicompanion.anim.AnimeUtils.pulse(holder.itemView)
                personaManager.setActivePersona(persona.id)
                val intent = Intent(this@HomeActivity, MainActivity::class.java).apply {
                    putExtra("persona_id", persona.id)
                }
                startActivity(intent)
            }

            holder.btnEdit.setOnClickListener {
                com.aicompanion.anim.AnimeUtils.pulse(holder.btnEdit)
                showEditPersonaDialog(persona)
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<Persona>) {
            items = newItems
            lastMsgCache.clear()
            notifyDataSetChanged()
        }
    }

    private fun getLastMessage(personaId: String): String {
        val prefsName = personaManager.getChatPrefsName(personaId)
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val json = prefs.getString("messages", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(json)
            if (arr.length() > 0) {
                val last = arr.getJSONObject(arr.length() - 1)
                val text = last.optString("text", "")
                return if (text.length > 30) text.take(30) + "..." else text
            }
        } catch (e: Exception) { com.aicompanion.util.AppLogger.e("HomeActivity", "getLastMessage: ${e.message}") }
        return "暂无消息"
    }

    private fun calculateSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (width > reqW || height > reqH) {
            val halfW = width / 2
            val halfH = height / 2
            while (halfW / inSampleSize >= reqW && halfH / inSampleSize >= reqH) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun exportPersonas() {
        val allPersonas = personaManager.getAllPersonas()
        if (allPersonas.isEmpty()) {
            Toast.makeText(this, "没有可导出的角色", Toast.LENGTH_SHORT).show()
            return
        }
        val names = allPersonas.map { it.name }.toTypedArray()
        val checked = BooleanArray(allPersonas.size) { true }
        AlertDialog.Builder(this)
            .setTitle("选择要导出的角色")
            .setMultiChoiceItems(names, checked) { _, _, _ -> }
            .setPositiveButton("导出") { _, _ ->
                val selected = allPersonas.filterIndexed { i, _ -> checked[i] }
                if (selected.isEmpty()) {
                    Toast.makeText(this, "未选择任何角色", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                doExport(selected)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doExport(selected: List<com.aicompanion.persona.Persona>) {
        try {
            val json = personaManager.exportPersonas(selected)
            val exportDir = File(cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val exportFile = File(exportDir, "personas_export_${System.currentTimeMillis()}.json")
            exportFile.writeText(json)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                exportFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "导出角色设定"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importPersonas() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/json"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择角色设定文件"), REQUEST_IMPORT_PERSONAS)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_IMPORT_PERSONAS = 2001
    }
}
