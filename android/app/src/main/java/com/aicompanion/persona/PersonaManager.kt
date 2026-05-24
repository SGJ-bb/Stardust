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
    val createdAt: Long = System.currentTimeMillis()
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
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
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
            val arr = json.optJSONArray("personas") ?: return
            for (i in 0 until arr.length()) {
                personas.add(Persona.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "load failed, attempting backup: ${e.message}")
            val backup = File(personaDir, "personas_index.bak")
            if (backup.exists()) {
                try {
                    val text = backup.readText()
                    val json = JSONObject(text)
                    val arr = json.optJSONArray("personas") ?: return
                    for (i in 0 until arr.length()) {
                        personas.add(Persona.fromJson(arr.getJSONObject(i)))
                    }
                    save()
                    AppLogger.d(TAG, "restored from backup")
                    return
                } catch (e2: Exception) {
                    AppLogger.e(TAG, "backup restore also failed: ${e2.message}")
                }
            }
            personas.add(Persona(
                id = "default",
                name = "星尘",
                prompt = "你叫星尘，性格活泼、有点小傲娇，喜欢和主人聊天。",
                isDefault = true,
                createdAt = System.currentTimeMillis()
            ))
            save()
        }
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

    fun deletePersona(id: String) {
        if (id == "default") return
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
        val diaryDir = File(File(context.filesDir, "diaries"), id)
        diaryDir.deleteRecursively()
        save()
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
