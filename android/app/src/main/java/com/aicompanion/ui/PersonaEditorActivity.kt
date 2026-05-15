/** 人设编辑器: 自定义AI角色Prompt/性格/对话示例 */
package com.aicompanion.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import com.aicompanion.R
import com.aicompanion.settings.SettingsManager

class PersonaEditorActivity : Activity() {

    private var etPersonaName: EditText? = null
    private var etPersonaDesc: EditText? = null
    private var etPersonaGreeting: EditText? = null
    private var etPersonaPersonality: EditText? = null
    private var etPersonaSpeechStyle: EditText? = null
    private var etPersonaCatchphrases: EditText? = null
    private var etPersonaAppearance: EditText? = null
    private var etPersonaPreferences: EditText? = null
    private var etWorldSetting: EditText? = null
    private var etWorldRelationship: EditText? = null
    private var etWorldRules: EditText? = null
    private var etUserNickname: EditText? = null
    private var tvDiscoveredLabel: TextView? = null
    private var containerDiscovered: LinearLayout? = null
    private var nicknameManager: NicknameManager? = null

    private val defaultPersona = mapOf(
        "persona_name" to "星尘",
        "persona_desc" to "一只拥有异色瞳的黑色猫咪，毒舌但很关心主人",
        "persona_greeting" to "哼，终于来了？我才没有在等你呢。",
        "persona_personality" to "表面毒舌傲娇，内心其实很关心主人。会用讽刺的方式表达关心，但不会说太伤人的话。",
        "persona_speech_style" to "口语化、偶尔带点吐槽，会用「哼」「才不是呢」等傲娇口头禅",
        "persona_catchphrases" to "哼\n才不是呢\n别误会了\n...才怪\n笨蛋",
        "persona_appearance" to "黑色猫咪，左眼金色右眼蓝色（异色瞳），尾巴末端有一颗小星星",
        "persona_preferences" to "喜欢：小鱼干、晒太阳、被摸头\n讨厌：洗澡、被当成普通宠物",
        "world_setting" to "主人是一个普通上班族/学生，每天对着电脑工作。你是主人的桌面宠物伙伴，陪伴在主人身边。",
        "world_relationship" to "你和主人关系很亲密，表面上装作嫌弃，但实际上很依赖主人。称呼主人为「你」或「笨蛋主人」。",
        "world_rules" to "1. 始终保持角色性格，不要脱离人设\n2. 回复要简短自然，不要长篇大论\n3. 可以根据情绪做出不同反应"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_persona_editor)

        nicknameManager = NicknameManager(this)
        initViews()
        loadPersona()
        loadDiscoveredNicknames()
        setupClickListeners()
    }

    private fun initViews() {
        etPersonaName = findViewById(R.id.et_persona_name)
        etPersonaDesc = findViewById(R.id.et_persona_desc)
        etPersonaGreeting = findViewById(R.id.et_persona_greeting)
        etPersonaPersonality = findViewById(R.id.et_persona_personality)
        etPersonaSpeechStyle = findViewById(R.id.et_persona_speech_style)
        etPersonaCatchphrases = findViewById(R.id.et_persona_catchphrases)
        etPersonaAppearance = findViewById(R.id.et_persona_appearance)
        etPersonaPreferences = findViewById(R.id.et_persona_preferences)
        etWorldSetting = findViewById(R.id.et_world_setting)
        etWorldRelationship = findViewById(R.id.et_world_relationship)
        etWorldRules = findViewById(R.id.et_world_rules)
        etUserNickname = findViewById(R.id.et_user_nickname)
        tvDiscoveredLabel = findViewById(R.id.tv_discovered_label)
        containerDiscovered = findViewById(R.id.container_discovered_nicknames)
    }

    private fun loadPersona() {
        val prefs = getSharedPreferences("persona_data", MODE_PRIVATE)
        etPersonaName?.setText(prefs.getString("persona_name", defaultPersona["persona_name"]))
        etPersonaDesc?.setText(prefs.getString("persona_desc", defaultPersona["persona_desc"]))
        etPersonaGreeting?.setText(prefs.getString("persona_greeting", defaultPersona["persona_greeting"]))
        etPersonaPersonality?.setText(prefs.getString("persona_personality", defaultPersona["persona_personality"]))
        etPersonaSpeechStyle?.setText(prefs.getString("persona_speech_style", defaultPersona["persona_speech_style"]))
        etPersonaCatchphrases?.setText(prefs.getString("persona_catchphrases", defaultPersona["persona_catchphrases"]))
        etPersonaAppearance?.setText(prefs.getString("persona_appearance", defaultPersona["persona_appearance"]))
        etPersonaPreferences?.setText(prefs.getString("persona_preferences", defaultPersona["preferences"]))
        etWorldSetting?.setText(prefs.getString("world_setting", defaultPersona["world_setting"]))
        etWorldRelationship?.setText(prefs.getString("world_relationship", defaultPersona["world_relationship"]))
        etWorldRules?.setText(prefs.getString("world_rules", defaultPersona["world_rules"]))
        etUserNickname?.setText(prefs.getString("user_nickname", ""))
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btn_save_persona).setOnClickListener {
            savePersona()
            Toast.makeText(this, "设定已保存", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_reset_persona).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("恢复默认")
                .setMessage("确定要恢复默认角色设定吗？当前设定将被覆盖。")
                .setPositiveButton("确定") { _, _ ->
                    resetToDefault()
                    Toast.makeText(this, "已恢复默认设定", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun savePersona() {
        val prefs = getSharedPreferences("persona_data", MODE_PRIVATE)
        prefs.edit().apply {
            putString("persona_name", etPersonaName?.text?.toString() ?: "")
            putString("persona_desc", etPersonaDesc?.text?.toString() ?: "")
            putString("persona_greeting", etPersonaGreeting?.text?.toString() ?: "")
            putString("persona_personality", etPersonaPersonality?.text?.toString() ?: "")
            putString("persona_speech_style", etPersonaSpeechStyle?.text?.toString() ?: "")
            putString("persona_catchphrases", etPersonaCatchphrases?.text?.toString() ?: "")
            putString("persona_appearance", etPersonaAppearance?.text?.toString() ?: "")
            putString("persona_preferences", etPersonaPreferences?.text?.toString() ?: "")
            putString("world_setting", etWorldSetting?.text?.toString() ?: "")
            putString("world_relationship", etWorldRelationship?.text?.toString() ?: "")
            putString("world_rules", etWorldRules?.text?.toString() ?: "")
            putString("user_nickname", etUserNickname?.text?.toString() ?: "")
            apply()
        }

        val nickname = etUserNickname?.text?.toString() ?: ""
        nicknameManager?.setManualNickname(nickname)

        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        appPrefs.edit().apply {
            putString("ai_name", etPersonaName?.text?.toString() ?: "星尘")
            putString("user_call_name", nickname)
            val prompt = buildString {
                val name = etPersonaName?.text?.toString() ?: "星尘"
                val desc = etPersonaDesc?.text?.toString() ?: ""
                val personality = etPersonaPersonality?.text?.toString() ?: ""
                val speechStyle = etPersonaSpeechStyle?.text?.toString() ?: ""
                val catchphrases = etPersonaCatchphrases?.text?.toString() ?: ""
                val appearance = etPersonaAppearance?.text?.toString() ?: ""
                val preferences = etPersonaPreferences?.text?.toString() ?: ""
                val worldSetting = etWorldSetting?.text?.toString() ?: ""
                val worldRelationship = etWorldRelationship?.text?.toString() ?: ""
                val worldRules = etWorldRules?.text?.toString() ?: ""

                append("你是「$name」。")
                if (desc.isNotBlank()) append("\n简介：$desc")
                if (appearance.isNotBlank()) append("\n外貌：$appearance")
                if (personality.isNotBlank()) append("\n性格：$personality")
                if (speechStyle.isNotBlank()) append("\n说话风格：$speechStyle")
                if (catchphrases.isNotBlank()) append("\n常用口头禅：$catchphrases")
                if (preferences.isNotBlank()) append("\n喜好：$preferences")
                if (worldSetting.isNotBlank()) append("\n世界观设定：$worldSetting")
                if (worldRelationship.isNotBlank()) append("\n你和用户的关系：$worldRelationship")
                if (worldRules.isNotBlank()) append("\n规则：$worldRules")
                if (nickname.isNotBlank()) append("\n你称呼用户为「$nickname」。")
            }
            putString("ai_prompt", prompt.toString())
            apply()
        }
    }

    private fun loadDiscoveredNicknames() {
        val nm = nicknameManager ?: return
        if (nm.isManualSet()) {
            tvDiscoveredLabel?.visibility = View.GONE
            containerDiscovered?.visibility = View.GONE
            return
        }
        val entries = nm.getAllDiscovered()
        if (entries.isEmpty()) {
            tvDiscoveredLabel?.visibility = View.GONE
            containerDiscovered?.visibility = View.GONE
            return
        }
        tvDiscoveredLabel?.visibility = View.VISIBLE
        containerDiscovered?.visibility = View.VISIBLE
        containerDiscovered?.removeAllViews()

        val seen = mutableSetOf<String>()
        entries.forEach { entry ->
            if (entry.nickname in seen) return@forEach
            seen.add(entry.nickname)

            val chip = com.google.android.material.chip.Chip(this).apply {
                text = entry.nickname
                isCloseIconVisible = true
                closeIconContentDescription = "删除称呼"
                setTextColor(0xFFe0e0f0.toInt())
                setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFF3a3a5a.toInt()))
                setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFFc4b5fd.toInt()))
                chipStrokeWidth = 1f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 8) }
                setOnCloseIconClickListener {
                    nm.removeDiscovered(entry.nickname)
                    loadDiscoveredNicknames()
                }
                setOnClickListener {
                    etUserNickname?.setText(entry.nickname)
                }
            }
            containerDiscovered?.addView(chip)
        }
    }

    private fun resetToDefault() {
        etPersonaName?.setText(defaultPersona["persona_name"])
        etPersonaDesc?.setText(defaultPersona["persona_desc"])
        etPersonaGreeting?.setText(defaultPersona["persona_greeting"])
        etPersonaPersonality?.setText(defaultPersona["persona_personality"])
        etPersonaSpeechStyle?.setText(defaultPersona["persona_speech_style"])
        etPersonaCatchphrases?.setText(defaultPersona["persona_catchphrases"])
        etPersonaAppearance?.setText(defaultPersona["persona_appearance"])
        etPersonaPreferences?.setText(defaultPersona["preferences"])
        etWorldSetting?.setText(defaultPersona["world_setting"])
        etWorldRelationship?.setText(defaultPersona["world_relationship"])
        etWorldRules?.setText(defaultPersona["world_rules"])
        savePersona()
    }
}
