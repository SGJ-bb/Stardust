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
            val json = JSONObject(indexFile.readText())
            val arr = json.optJSONArray("personas") ?: return
            for (i in 0 until arr.length()) {
                personas.add(Persona.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "load failed: ${e.message}")
        }
    }

    fun save() {
        try {
            val json = JSONObject().apply {
                put("personas", JSONArray().apply {
                    personas.forEach { put(it.toJson()) }
                })
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
}
