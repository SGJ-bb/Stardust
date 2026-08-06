/**
 * 角色数据仓储：统一读取/写入角色全部数据，支持完整导入导出。
 *
 * 导出范围：
 * - Persona 人格设定 + CharacterCard 角色卡
 * - 12+ 个 SharedPreferences 的全部键值对（好感度/成就/时刻/里程碑/胶囊/收藏/昵称/统计/记忆/RAG等）
 * - SQLite 聊天记录（全量消息）+ 日记
 * - AI 头像 + 用户头像（base64 编码嵌入 JSON）
 *
 * 设计原则：不修改现有管理器，直接操作 SP 和 SQLite，确保字段零遗漏。
 */
package com.aicompanion.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.aicompanion.character.CharacterCardManager
import com.aicompanion.diary.DiaryEntry
import com.aicompanion.persona.Persona
import com.aicompanion.persona.PersonaManager
import com.aicompanion.storage.ChatHistoryStorage
import com.aicompanion.storage.StoredMessage
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PersonaDataRepository(private val context: Context) {

    companion object {
        private const val TAG = "PersonaDataRepo"
        private const val EXPORT_VERSION = "1.0"
        private const val CHAT_SCOPE = "persona"

        /** 需要导出的 SP 名称模板（{id} 替换为 personaId） */
        private val SP_TEMPLATES = listOf(
            "persona_data_{id}",
            "affection_data_{id}",
            "achievements_{id}",
            "memorable_moments_{id}",
            "milestones_{id}",
            "time_capsules_{id}",
            "favorites_{id}",
            "nickname_data_{id}",
            "persona_stats_{id}",
            "local_memory_{id}",
            "rag_vector_persona_{id}",
            "chat_history_{id}",
        )
    }

    // ─── 导出 ──────────────────────────────────────────

    /** 导出角色完整数据为 JSON 对象 */
    fun exportPersona(personaId: String): JSONObject? {
        return try {
            val pm = PersonaManager(context)
            pm.load()
            val persona = pm.getPersona(personaId)
            if (persona == null) {
                AppLogger.e(TAG, "exportPersona: persona not found: $personaId")
                return null
            }

            val card = CharacterCardManager(context).getAllCards().find { it.id == personaId }
            val worldInfo = card?.let { CharacterCardManager(context).getWorldInfoForCard(it) }
            val spData = exportAllSharedPreferences(personaId)
            val chatMessages = exportChatMessages(personaId)
            val diaries = exportDiaries(personaId)
            val aiAvatarBase64 = persona.avatarPath.takeIf { it.isNotBlank() }
                ?.let { fileToBase64(it) }
            val userAvatarBase64 = exportUserAvatar(personaId)

            JSONObject().apply {
                put("version", EXPORT_VERSION)
                put("exportTime", System.currentTimeMillis())
                put("originalPersonaId", personaId)
                put("persona", persona.toJson())
                if (card != null) put("characterCard", card.toJson())
                if (worldInfo != null) put("worldInfo", worldInfo.toJson())
                put("spData", spData)
                put("chatMessages", chatMessages)
                put("diaries", diaries)
                if (aiAvatarBase64 != null) put("aiAvatarBase64", aiAvatarBase64)
                if (userAvatarBase64 != null) put("userAvatarBase64", userAvatarBase64)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "exportPersona failed: ${e.message}", e)
            null
        }
    }

    /** 导出角色数据到文件，返回文件路径 */
    fun exportToFile(personaId: String, destFile: File): Boolean {
        return try {
            val json = exportPersona(personaId) ?: return false
            destFile.parentFile?.mkdirs()
            destFile.writeText(json.toString(2), Charsets.UTF_8)
            AppLogger.i(TAG, "exportToFile: ${destFile.absolutePath}, size=${destFile.length()}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "exportToFile failed: ${e.message}", e)
            false
        }
    }

    // ─── 导入 ──────────────────────────────────────────

    /**
     * 从 JSON 导入角色数据。
     * @param json 导出的 JSON 对象
     * @param newId 新角色 ID（null 则自动生成 UUID，避免覆盖现有角色）
     * @return 新角色 ID，失败返回 null
     */
    fun importPersona(json: JSONObject, newId: String? = null): String? {
        return try {
            val personaId = newId ?: UUID.randomUUID().toString()

            // 1. 导入 Persona
            val personaJson = json.optJSONObject("persona")
            if (personaJson != null) {
                personaJson.put("id", personaId)
                val persona = Persona.fromJson(personaJson)
                val pm = PersonaManager(context)
                pm.load()
                pm.addPersona(persona)
            }

            // 2. 导入 CharacterCard + WorldInfo
            val cardJson = json.optJSONObject("characterCard")
            if (cardJson != null) {
                cardJson.put("id", personaId)
                val cardMgr = CharacterCardManager(context)

                // 导入 WorldInfo（如果有）
                val worldInfoJson = json.optJSONObject("worldInfo")
                var newWorldInfoId = ""
                if (worldInfoJson != null) {
                    val wi = com.aicompanion.models.WorldInfo.fromJson(worldInfoJson)
                        .copy(id = UUID.randomUUID().toString())
                    cardMgr.addWorldInfo(wi)
                    newWorldInfoId = wi.id
                }

                val card = com.aicompanion.models.CharacterCard.fromJson(cardJson)
                    .copy(isActive = false, worldInfoId = newWorldInfoId)
                cardMgr.addCard(card)
            }

            // 3. 导入 SP 数据（键值对替换 personaId）
            val spData = json.optJSONObject("spData")
            if (spData != null) {
                importAllSharedPreferences(personaId, spData)
            }

            // 4. 导入聊天记录
            val chatMessages = json.optJSONArray("chatMessages")
            if (chatMessages != null) {
                importChatMessages(personaId, chatMessages)
            }

            // 5. 导入日记
            val diaries = json.optJSONArray("diaries")
            if (diaries != null) {
                importDiaries(personaId, diaries)
            }

            // 6. 导入头像
            val aiAvatarBase64 = json.optString("aiAvatarBase64", "")
            if (aiAvatarBase64.isNotBlank()) {
                importAiAvatar(personaId, aiAvatarBase64)
            }
            val userAvatarBase64 = json.optString("userAvatarBase64", "")
            if (userAvatarBase64.isNotBlank()) {
                importUserAvatar(personaId, userAvatarBase64)
            }

            AppLogger.i(TAG, "importPersona: success, newId=$personaId")
            personaId
        } catch (e: Exception) {
            AppLogger.e(TAG, "importPersona failed: ${e.message}", e)
            null
        }
    }

    /** 从文件导入角色 */
    fun importFromFile(file: File, newId: String? = null): String? {
        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            importPersona(json, newId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "importFromFile failed: ${e.message}", e)
            null
        }
    }

    // ─── SP 导出/导入 ──────────────────────────────────

    /** 导出角色相关的所有 SharedPreferences 键值对。
     *  使用模板名（不含 personaId）作为 key，导入时用新 personaId 拼接。 */
    private fun exportAllSharedPreferences(personaId: String): JSONObject {
        val result = JSONObject()
        for (template in SP_TEMPLATES) {
            val spName = template.replace("{id}", personaId)
            try {
                val prefs = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
                val all = prefs.all
                if (all.isEmpty()) continue

                val spJson = JSONObject()
                for ((key, value) in all) {
                    val entry = JSONObject()
                    when (value) {
                        is Int -> { entry.put("type", "int"); entry.put("value", value) }
                        is Long -> { entry.put("type", "long"); entry.put("value", value) }
                        is Float -> { entry.put("type", "float"); entry.put("value", value) }
                        is Boolean -> { entry.put("type", "bool"); entry.put("value", value) }
                        is String -> { entry.put("type", "string"); entry.put("value", value) }
                        is Set<*> -> {
                            entry.put("type", "stringset")
                            val arr = JSONArray()
                            value.filterIsInstance<String>().forEach { arr.put(it) }
                            entry.put("value", arr)
                        }
                        else -> { entry.put("type", "string"); entry.put("value", value.toString()) }
                    }
                    spJson.put(key, entry)
                }
                // 用模板名（如 "persona_data"）作为 key，而非含 personaId 的完整 SP 名
                val templateKey = template.replace("_{id}", "")
                result.put(templateKey, spJson)
            } catch (e: Exception) {
                AppLogger.w(TAG, "export SP $spName failed: ${e.message}")
            }
        }
        return result
    }

    /** 导入 SharedPreferences 键值对（用新 personaId 拼接 SP 名） */
    private fun importAllSharedPreferences(newPersonaId: String, spData: JSONObject) {
        val keys = spData.keys()
        while (keys.hasNext()) {
            val templateKey = keys.next()
            try {
                // 用模板 key + 新 personaId 生成 SP 名
                val newSpName = "${templateKey}_$newPersonaId"

                val spJson = spData.getJSONObject(templateKey)
                val prefs = context.getSharedPreferences(newSpName, Context.MODE_PRIVATE)
                val editor = prefs.edit()

                val entryKeys = spJson.keys()
                while (entryKeys.hasNext()) {
                    val key = entryKeys.next()
                    val entry = spJson.getJSONObject(key)
                    val type = entry.optString("type", "string")
                    when (type) {
                        "int" -> editor.putInt(key, entry.optInt("value"))
                        "long" -> editor.putLong(key, entry.optLong("value"))
                        "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                        "bool" -> editor.putBoolean(key, entry.optBoolean("value"))
                        "string" -> editor.putString(key, entry.optString("value"))
                        "stringset" -> {
                            val arr = entry.optJSONArray("value")
                            val set = mutableSetOf<String>()
                            if (arr != null) {
                                for (i in 0 until arr.length()) set.add(arr.getString(i))
                            }
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.apply()
            } catch (e: Exception) {
                AppLogger.w(TAG, "import SP $templateKey failed: ${e.message}")
            }
        }
    }

    // ─── 聊天记录导出/导入 ─────────────────────────────

    private fun exportChatMessages(personaId: String): JSONArray {
        val arr = JSONArray()
        try {
            val storage = ChatHistoryStorage(context)
            val dates = storage.getDates(CHAT_SCOPE, personaId)
            for (date in dates) {
                val msgs = storage.getMessages(CHAT_SCOPE, personaId, date)
                for (msg in msgs) {
                    arr.put(msg.toJson())
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "exportChatMessages failed: ${e.message}")
        }
        return arr
    }

    private fun importChatMessages(personaId: String, messages: JSONArray) {
        try {
            val storage = ChatHistoryStorage(context)
            val msgs = mutableListOf<StoredMessage>()
            for (i in 0 until messages.length()) {
                val msgJson = messages.getJSONObject(i)
                msgs.add(StoredMessage.fromJson(msgJson))
            }
            if (msgs.isNotEmpty()) {
                storage.addMessages(CHAT_SCOPE, personaId, msgs)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "importChatMessages failed: ${e.message}")
        }
    }

    // ─── 日记导出/导入 ─────────────────────────────────

    private fun exportDiaries(personaId: String): JSONArray {
        val arr = JSONArray()
        try {
            val storage = ChatHistoryStorage(context)
            val diaries = storage.getAllDiaries(personaId)
            for (d in diaries) {
                arr.put(d.toJson())
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "exportDiaries failed: ${e.message}")
        }
        return arr
    }

    private fun importDiaries(personaId: String, diaries: JSONArray) {
        try {
            val storage = ChatHistoryStorage(context)
            for (i in 0 until diaries.length()) {
                val dJson = diaries.getJSONObject(i)
                val entry = DiaryEntry.fromJson(dJson)
                storage.insertDiary(personaId, entry)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "importDiaries failed: ${e.message}")
        }
    }

    // ─── 头像导出/导入 ─────────────────────────────────

    private fun fileToBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLogger.w(TAG, "fileToBase64 failed: ${e.message}")
            null
        }
    }

    private fun exportUserAvatar(personaId: String): String? {
        return try {
            val avatarPrefs = context.getSharedPreferences("avatar_prefs", Context.MODE_PRIVATE)
            val path = avatarPrefs.getString("user_avatar_$personaId", null)
            path?.let { fileToBase64(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun importAiAvatar(personaId: String, base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val dir = File(context.filesDir, "personas/avatars").apply { mkdirs() }
            val destFile = File(dir, "avatar_${personaId}_${System.currentTimeMillis()}.jpg")
            destFile.writeBytes(bytes)

            // 更新 Persona 的 avatarPath
            val pm = PersonaManager(context)
            pm.load()
            pm.updatePersona(personaId) { it.copy(avatarPath = destFile.absolutePath) }

            // 更新 AvatarManager SP
            val avatarPrefs = context.getSharedPreferences("avatar_prefs", Context.MODE_PRIVATE)
            avatarPrefs.edit().putString("ai_avatar_$personaId", destFile.absolutePath).apply()
        } catch (e: Exception) {
            AppLogger.w(TAG, "importAiAvatar failed: ${e.message}")
        }
    }

    private fun importUserAvatar(personaId: String, base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val dir = File(context.filesDir, "personas/avatars").apply { mkdirs() }
            val destFile = File(dir, "user_avatar_${personaId}_${System.currentTimeMillis()}.jpg")
            destFile.writeBytes(bytes)

            val avatarPrefs = context.getSharedPreferences("avatar_prefs", Context.MODE_PRIVATE)
            avatarPrefs.edit().putString("user_avatar_$personaId", destFile.absolutePath).apply()
        } catch (e: Exception) {
            AppLogger.w(TAG, "importUserAvatar failed: ${e.message}")
        }
    }
}
