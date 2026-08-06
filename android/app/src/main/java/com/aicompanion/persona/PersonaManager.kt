package com.aicompanion.persona

import android.content.Context
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Persona(
    val id: String,
    val name: String,
    val prompt: String,
    val avatarPath: String = "",
    val speechStyle: String = "",
    val personality: String = "",
    val description: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val ttsVoice: String = "",
    val ttsPitch: Float = 0f,
    val ttsRate: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("prompt", prompt)
        put("avatarPath", avatarPath)
        put("speechStyle", speechStyle)
        put("personality", personality)
        put("description", description)
        put("isDefault", isDefault)
        put("createdAt", createdAt)
        if (ttsVoice.isNotBlank()) put("ttsVoice", ttsVoice)
        if (ttsPitch != 0f) put("ttsPitch", ttsPitch)
        if (ttsRate != 0f) put("ttsRate", ttsRate)
    }

    companion object {
        fun fromJson(obj: JSONObject): Persona = Persona(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "星尘"),
            prompt = obj.optString("prompt", ""),
            avatarPath = obj.optString("avatarPath", ""),
            speechStyle = obj.optString("speechStyle", ""),
            personality = obj.optString("personality", ""),
            description = obj.optString("description", ""),
            isDefault = obj.optBoolean("isDefault", false),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            ttsVoice = obj.optString("ttsVoice", ""),
            ttsPitch = obj.optDouble("ttsPitch", 0.0).toFloat(),
            ttsRate = obj.optDouble("ttsRate", 0.0).toFloat()
        )
    }
}

class PersonaManager(private val context: Context) {
    companion object {
        private const val TAG = "PersonaManager"
        private const val PERSONA_DIR = "personas"
        private const val INDEX_FILE = "personas_index.json"
        private const val ACTIVE_KEY = "active_persona_id"
        private const val CHAT_PREFS_PREFIX = "chat_history_"
    }

    private val personaDir = File(context.filesDir, PERSONA_DIR).apply { mkdirs() }
    private val indexFile = File(personaDir, INDEX_FILE)
    private val personas = mutableListOf<Persona>()

    fun load() {
        personas.clear()
        if (!indexFile.exists()) {
            // 索引文件不存在,尝试从SharedPreferences恢复角色数据
            val recovered = recoverFromSharedPreferences()
            if (recovered.isNotEmpty()) {
                personas.addAll(recovered)
                save()
                AppLogger.i(TAG, "[Persona-Load] 从SharedPreferences恢复了${recovered.size}个角色")
                return
            }
            // 恢复失败,创建默认角色
            personas.add(Persona(
                id = "default",
                name = "星尘",
                prompt = "你叫星尘，性格活泼、有点小傲娇，喜欢和主人聊天。",
                isDefault = true,
                createdAt = System.currentTimeMillis()
            ))
            save()
            return
        }
        try {
            val text = indexFile.readText()
            val json = JSONObject(text)
            val arr = json.optJSONArray("personas")
            if (arr == null) {
                // JSON格式不正确,尝试从SharedPreferences恢复
                val recovered = recoverFromSharedPreferences()
                if (recovered.isNotEmpty()) {
                    personas.addAll(recovered)
                    save()
                    AppLogger.i(TAG, "[Persona-Load] 索引格式异常,从SharedPreferences恢复了${recovered.size}个角色")
                    return
                }
                // 恢复失败,创建默认角色(不覆盖原文件)
                personas.add(Persona(
                    id = "default",
                    name = "星尘",
                    prompt = "你叫星尘，性格活泼、有点小傲娇，喜欢和主人聊天。",
                    isDefault = true,
                    createdAt = System.currentTimeMillis()
                ))
                AppLogger.w(TAG, "[Persona-Load] 索引格式异常且无法恢复,使用默认角色(未覆盖原文件)")
                return
            }
            for (i in 0 until arr.length()) {
                personas.add(Persona.fromJson(arr.getJSONObject(i)))
            }
            AppLogger.i(TAG, "[Persona-Load] 成功加载${personas.size}个角色")

            // 关键修复：总是尝试从SharedPreferences合并恢复丢失的角色
            // 不再限制 personas.size <= 1，因为索引文件可能包含部分角色（如只有默认角色），
            // 但用户之前创建的角色数据仍存在于 SharedPreferences 中。
            // 此逻辑会扫描 persona_data_*.xml，将 JSON 中缺失的角色合并回来。
            try {
                val recovered = recoverFromSharedPreferences()
                if (recovered.isNotEmpty()) {
                    val existingIds = personas.map { it.id }.toSet()
                    val toAdd = recovered.filter { it.id !in existingIds }
                    if (toAdd.isNotEmpty()) {
                        personas.addAll(toAdd)
                        save()
                        AppLogger.i(TAG, "[Persona-Load] 从SharedPreferences合并恢复了${toAdd.size}个丢失的角色(现有${existingIds.size}个)")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "[Persona-Load] 合并恢复角色失败: ${e.message}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Persona-Load] 加载失败,尝试备份: ${e.javaClass.simpleName}: ${e.message}")
            val backup = File(personaDir, "personas_index.bak")
            if (backup.exists()) {
                try {
                    val text = backup.readText()
                    val json = JSONObject(text)
                    val arr = json.optJSONArray("personas")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            personas.add(Persona.fromJson(arr.getJSONObject(i)))
                        }
                        save()
                        AppLogger.i(TAG, "[Persona-Load] 从备份恢复了${personas.size}个角色")
                        return
                    }
                } catch (e2: Exception) {
                    AppLogger.e(TAG, "[Persona-Load] 备份恢复也失败: ${e2.javaClass.simpleName}: ${e2.message}")
                }
            }
            // 尝试从SharedPreferences恢复
            val recovered = recoverFromSharedPreferences()
            if (recovered.isNotEmpty()) {
                personas.addAll(recovered)
                save()
                AppLogger.i(TAG, "[Persona-Load] 从SharedPreferences恢复了${recovered.size}个角色")
                return
            }
            // 所有恢复方式都失败,创建默认角色
            personas.add(Persona(
                id = "default",
                name = "星尘",
                prompt = "你叫星尘，性格活泼、有点小傲娇，喜欢和主人聊天。",
                isDefault = true,
                createdAt = System.currentTimeMillis()
            ))
            save()
            AppLogger.w(TAG, "[Persona-Load] 所有恢复方式失败,创建默认角色")
        }
    }

    /**
     * 从SharedPreferences恢复角色数据
     *
     * 扫描所有 persona_data_* SharedPreferences,
     * 如果包含 persona_name 字段,则认为是一个有效的角色。
     * 用于 personas_index.json 丢失或损坏时的数据恢复。
     */
    private fun recoverFromSharedPreferences(): List<Persona> {
        val recovered = mutableListOf<Persona>()
        try {
            // 扫描 filesDir 下的 SharedPreferences 文件
            val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
            if (!sharedPrefsDir.exists()) return emptyList()

            val prefsFiles = sharedPrefsDir.listFiles { f ->
                f.name.startsWith("persona_data_") && f.name.endsWith(".xml")
            } ?: return emptyList()

            for (file in prefsFiles) {
                val personaId = file.name.removePrefix("persona_data_").removeSuffix(".xml")
                val prefs = context.getSharedPreferences("persona_data_$personaId", Context.MODE_PRIVATE)
                val name = prefs.getString("persona_name", null) ?: continue

                // 从 SharedPreferences 恢复所有可恢复字段
                val desc = prefs.getString("persona_desc", "") ?: ""
                val personality = prefs.getString("persona_personality", "") ?: ""
                val speechStyle = prefs.getString("persona_speech_style", "") ?: ""
                val appearance = prefs.getString("persona_appearance", "") ?: ""
                val catchphrases = prefs.getString("persona_catchphrases", "") ?: ""
                val preferences = prefs.getString("persona_preferences", "") ?: ""
                val worldSetting = prefs.getString("world_setting", "") ?: ""
                val worldRelationship = prefs.getString("world_relationship", "") ?: ""
                val worldRules = prefs.getString("world_rules", "") ?: ""
                val nickname = prefs.getString("user_nickname", "") ?: ""
                val freeMode = prefs.getString("free_mode", "") ?: ""
                val avatarPath = prefs.getString("persona_avatar_path", "") ?: ""

                // 重新构建 prompt（与 PersonaEditorActivity.savePersona 逻辑一致）
                val prompt = buildString {
                    append("你是「${name.ifBlank { "星尘" }}」。")
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
                    if (freeMode.isNotBlank()) append("\n\n自定义指令：\n$freeMode")
                }

                val persona = Persona(
                    id = personaId,
                    name = name,
                    prompt = prompt,
                    avatarPath = avatarPath,
                    speechStyle = speechStyle,
                    personality = personality,
                    description = desc,
                    isDefault = personaId == "default",
                    createdAt = System.currentTimeMillis()
                )
                recovered.add(persona)
                AppLogger.i(TAG, "[Persona-Recover] 恢复角色: id=$personaId name=$name avatar=${avatarPath.isNotEmpty()}")
            }

            // 确保默认角色存在
            if (recovered.none { it.id == "default" }) {
                recovered.add(Persona(
                    id = "default",
                    name = "星尘",
                    prompt = "你叫星尘，性格活泼、有点小傲娇，喜欢和主人聊天。",
                    isDefault = true,
                    createdAt = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Persona-Recover] 从SharedPreferences恢复失败: ${e.javaClass.simpleName}: ${e.message}")
        }
        return recovered
    }

    fun save() {
        try {
            val json = JSONObject().apply {
                put("personas", JSONArray().apply {
                    personas.forEach { put(it.toJson()) }
                })
            }
            val backup = File(personaDir, "personas_index.bak")
            if (indexFile.exists()) {
                try { indexFile.copyTo(backup, overwrite = true) } catch (_: Exception) {}
            }
            indexFile.writeText(json.toString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "save failed: ${e.message}")
        }
    }

    fun getAllPersonas(): List<Persona> = personas.toList()

    fun getPersona(id: String): Persona? = personas.find { it.id == id }

    fun getActivePersona(): Persona {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = prefs.getString(ACTIVE_KEY, "default") ?: "default"
        return personas.find { it.id == activeId } ?: personas.firstOrNull() ?: Persona(
            id = "default", name = "星尘", prompt = "", isDefault = true
        )
    }

    fun setActivePersona(id: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(ACTIVE_KEY, id).apply()
        // Auto-sync persona name to persona_data SP so UI can read it without PersonaManager
        val persona = getPersona(id)
        if (persona != null) {
            val personaPrefs = context.getSharedPreferences("persona_data_$id", Context.MODE_PRIVATE)
            if (personaPrefs.getString("persona_name", null).isNullOrBlank()) {
                personaPrefs.edit().putString("persona_name", persona.name).apply()
            }
        }
        com.aicompanion.AppContainer.onPersonaChanged()
    }

    fun addPersona(persona: Persona): Persona {
        val p = if (persona.id.isBlank()) persona.copy(id = UUID.randomUUID().toString()) else persona
        personas.add(p)
        save()
        return p
    }

    fun updatePersona(id: String, updater: (Persona) -> Persona): Persona? {
        val idx = personas.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = updater(personas[idx])
        personas[idx] = updated
        save()
        return updated
    }

    fun deletePersona(id: String): Boolean {
        if (id == "default") return false
        val activeId = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString(ACTIVE_KEY, "default") ?: "default"
        if (id == activeId) return false
        val persona = getPersona(id)
        if (persona?.avatarPath?.isNotBlank() == true) {
            try { File(persona.avatarPath).delete() } catch (_: Exception) {}
        }
        personas.removeAll { it.id == id }
        context.getSharedPreferences("$CHAT_PREFS_PREFIX$id", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("local_memory_$id", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("affection_data_$id", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("rag_vector_persona_$id", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("achievements_$id", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("favorites_$id", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("memorable_moments_$id", Context.MODE_PRIVATE).edit().clear().apply()
        // 关键修复：清理 persona_data_$id，否则角色恢复逻辑会扫描 persona_data_*.xml 将已删除的角色恢复
        context.getSharedPreferences("persona_data_$id", Context.MODE_PRIVATE).edit().clear().apply()
        val diaryDir = File(File(context.filesDir, "diaries"), id)
        diaryDir.deleteRecursively()
        // 同步删除该角色的聊天历史JSON文件（修复：删除角色后重进不会恢复聊天记录）
        try {
            com.aicompanion.storage.ChatHistoryStorage(context).deleteScope("persona", id)
        } catch (_: Exception) {}
        save()
        return true
    }

    fun getChatPrefsName(personaId: String): String = "$CHAT_PREFS_PREFIX$personaId"

    fun getPersonaDir(personaId: String): File = File(personaDir, personaId).apply { mkdirs() }

    private val EXPORT_SAFE_KEYS = setOf(
        "persona_name", "persona_desc", "persona_greeting", "persona_personality",
        "persona_speech_style", "persona_catchphrases", "persona_appearance",
        "persona_preferences", "persona_avatar_path", "world_setting",
        "world_relationship", "world_rules", "user_nickname",
        "personality_evolution_enabled"
    )

    private val GLOBAL_EXPORT_SAFE_KEYS = setOf(
        "global_user_identity", "global_user_abilities", "active_persona_id"
    )

    fun exportAllPersonas(): String = exportPersonas(personas)

    fun exportPersonas(selected: List<Persona>): String {
        val json = JSONObject().apply {
            put("export_version", 1)
            put("export_time", System.currentTimeMillis())
            val arr = JSONArray()
            for (p in selected) {
                val pObj = p.toJson()
                val prefs = context.getSharedPreferences("persona_data_${p.id}", Context.MODE_PRIVATE)
                val extObj = JSONObject()
                for ((key, value) in prefs.all) {
                    if (key !in EXPORT_SAFE_KEYS) continue
                    if (value is String) extObj.put(key, value)
                    else if (value is Int) extObj.put(key, value)
                    else if (value is Boolean) extObj.put(key, value)
                    else if (value is Float) extObj.put(key, value)
                    else if (value is Long) extObj.put(key, value)
                }
                pObj.put("extended_data", extObj)
                arr.put(pObj)
            }
            put("personas", arr)
            val globalPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val globalObj = JSONObject()
            for ((key, value) in globalPrefs.all) {
                if (key !in GLOBAL_EXPORT_SAFE_KEYS) continue
                if (value is String) globalObj.put(key, value)
            }
            put("global_prefs", globalObj)
        }
        return json.toString(2)
    }

    data class ImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

    fun importPersonas(jsonStr: String): ImportResult {
        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("personas") ?: return ImportResult(0, 0, listOf("无效格式"))
            for (i in 0 until arr.length()) {
                try {
                    val pObj = arr.getJSONObject(i)
                    val persona = Persona.fromJson(pObj)
                    if (persona.name.isBlank()) {
                        skipped++
                        continue
                    }
                    val existing = getPersona(persona.id)
                    if (existing != null) {
                        updatePersona(persona.id) { persona }
                    } else {
                        addPersona(persona)
                    }
                    val extObj = pObj.optJSONObject("extended_data")
                    if (extObj != null) {
                        val prefs = context.getSharedPreferences("persona_data_${persona.id}", Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        val keys = extObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = extObj.get(key)
                            when (value) {
                                is String -> editor.putString(key, value)
                                is Int -> editor.putInt(key, value)
                                is Boolean -> editor.putBoolean(key, value)
                                is Float -> editor.putFloat(key, value)
                                is Long -> editor.putLong(key, value)
                            }
                        }
                        editor.apply()
                    }
                    imported++
                } catch (e: Exception) {
                    errors.add("第${i + 1}条导入失败: ${e.message}")
                }
            }
            val globalObj = root.optJSONObject("global_prefs")
            if (globalObj != null) {
                val globalPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val editor = globalPrefs.edit()
                if (globalObj.has("global_user_identity")) editor.putString("global_user_identity", globalObj.getString("global_user_identity"))
                if (globalObj.has("global_user_abilities")) editor.putString("global_user_abilities", globalObj.getString("global_user_abilities"))
                editor.apply()
            }
        } catch (e: Exception) {
            errors.add("JSON解析失败: ${e.message}")
        }
        return ImportResult(imported, skipped, errors)
    }
}
