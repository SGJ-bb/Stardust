/** 角色卡片管理器: 角色卡的创建/加载/保存/序列化/反序列化 */
package com.aicompanion.character

import android.content.Context
import android.util.Log
import com.aicompanion.models.CharacterCard
import com.aicompanion.models.WorldInfo
import com.aicompanion.models.WorldInfoEntry
import com.aicompanion.models.UserPersona
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class CharacterCardManager(private val context: Context) {

    companion object {
        private const val TAG = "CharacterCardManager"
        private const val PREFS_NAME = "character_cards"
        private const val KEY_CARDS = "cards"
        private const val KEY_WORLD_INFOS = "world_infos"
        private const val KEY_USER_PERSONA = "user_persona"
        private const val KEY_LEGACY_MIGRATED = "legacy_personas_migrated"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllCards(): List<CharacterCard> {
        val json = prefs.getString(KEY_CARDS, null) ?: return listOf(CharacterCard.defaultCard())
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { CharacterCard.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cards", e)
            listOf(CharacterCard.defaultCard())
        }
    }

    /**
     * 兜底迁移：扫描旧版 persona_data_* SharedPreferences，将旧角色导入为 CharacterCard
     *
     * 适用场景：
     * - 用户在旧版 PersonaEditorActivity 创建过角色，数据存在 persona_data_{id} SharedPreferences
     * - PersonaManager 索引文件丢失或损坏，导致 migratePersonasToCharacterCards() 通过 PersonaManager 拿不到数据
     * - CharacterCardManager 中只有默认卡，没有任何用户卡
     *
     * 字段映射（参照 PersonaManager.recoverFromSharedPreferences）：
     * - persona_name → name
     * - persona_desc → description
     * - persona_personality → personality
     * - persona_speech_style → 拼入 systemPrompt
     * - persona_appearance → 拼入 systemPrompt
     * - persona_catchphrases → 拼入 systemPrompt
     * - persona_preferences → 拼入 systemPrompt
     * - world_setting / world_relationship / world_rules → 拼入 scenario
     * - user_nickname → 拼入 systemPrompt
     * - free_mode → 拼入 postHistoryInstructions
     * - persona_avatar_path → avatarPath
     *
     * @return 实际迁移的角色数量
     */
    fun migrateFromLegacyPersonas(): Int {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            // 即使已标记迁移，如果没有任何用户卡，仍要尝试（修复之前的 bug）
            val hasUserCards = getAllCards().any { it.id != "default_stardust" && it.id != "default" }
            if (hasUserCards) {
                AppLogger.i(TAG, "[Legacy-Migrate] 已迁移且存在用户卡，跳过")
                return 0
            }
            AppLogger.w(TAG, "[Legacy-Migrate] 标记已迁移但无用户卡，重新扫描")
        }

        val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
        if (!sharedPrefsDir.exists()) {
            AppLogger.i(TAG, "[Legacy-Migrate] shared_prefs 目录不存在，无旧数据可迁移")
            prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
            return 0
        }

        val prefsFiles = sharedPrefsDir.listFiles { f ->
            f.name.startsWith("persona_data_") && f.name.endsWith(".xml")
        } ?: run {
            AppLogger.i(TAG, "[Legacy-Migrate] 未找到 persona_data_* 文件")
            prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
            return 0
        }

        val existingCards = getAllCards()
        val existingIds = existingCards.map { it.id }.toMutableSet()
        val existingNames = existingCards.map { it.name }.toMutableSet()
        val toAdd = mutableListOf<CharacterCard>()

        for (file in prefsFiles) {
            val personaId = file.name.removePrefix("persona_data_").removeSuffix(".xml")
            val personaPrefs = context.getSharedPreferences("persona_data_$personaId", Context.MODE_PRIVATE)
            val name = personaPrefs.getString("persona_name", null) ?: continue
            // 跳过默认星尘（CharacterCardManager 已有 default_stardust）
            if (personaId == "default" && name == "星尘") continue
            // 去重：id 或 name 已存在则跳过
            if (personaId in existingIds || name in existingNames) {
                AppLogger.d(TAG, "[Legacy-Migrate] 跳过已存在: id=$personaId name=$name")
                continue
            }

            val desc = personaPrefs.getString("persona_desc", "") ?: ""
            val personality = personaPrefs.getString("persona_personality", "") ?: ""
            val speechStyle = personaPrefs.getString("persona_speech_style", "") ?: ""
            val appearance = personaPrefs.getString("persona_appearance", "") ?: ""
            val catchphrases = personaPrefs.getString("persona_catchphrases", "") ?: ""
            val preferences = personaPrefs.getString("persona_preferences", "") ?: ""
            val worldSetting = personaPrefs.getString("world_setting", "") ?: ""
            val worldRelationship = personaPrefs.getString("world_relationship", "") ?: ""
            val worldRules = personaPrefs.getString("world_rules", "") ?: ""
            val nickname = personaPrefs.getString("user_nickname", "") ?: ""
            val freeMode = personaPrefs.getString("free_mode", "") ?: ""
            val avatarPath = personaPrefs.getString("persona_avatar_path", "") ?: ""
            val greeting = personaPrefs.getString("persona_greeting", "") ?: ""

            // 构建 systemPrompt（与 PersonaManager.recoverFromSharedPreferences 一致）
            val systemPrompt = buildString {
                append("你是「$name」。")
                if (desc.isNotBlank()) append("\n简介：$desc")
                if (appearance.isNotBlank()) append("\n外貌：$appearance")
                if (personality.isNotBlank()) append("\n性格：$personality")
                if (speechStyle.isNotBlank()) append("\n说话风格：$speechStyle")
                if (catchphrases.isNotBlank()) append("\n常用口头禅：$catchphrases")
                if (preferences.isNotBlank()) append("\n喜好：$preferences")
                if (nickname.isNotBlank()) append("\n你称呼用户为「$nickname」。")
            }

            // scenario 包含世界观设定
            val scenario = buildString {
                if (worldSetting.isNotBlank()) append("世界观设定：$worldSetting")
                if (worldRelationship.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("你和用户的关系：$worldRelationship")
                }
                if (worldRules.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("规则：$worldRules")
                }
            }

            val card = CharacterCard(
                id = personaId,
                name = name,
                description = desc,
                personality = personality,
                scenario = scenario,
                firstMes = greeting,
                mesExample = "",
                creatorNotes = "",
                systemPrompt = systemPrompt,
                postHistoryInstructions = freeMode,
                alternateGreetings = emptyList(),
                tags = listOf("migrated"),
                creator = "legacy_migration",
                characterVersion = "1.0",
                avatarPath = avatarPath,
                isActive = false,
                createdAt = System.currentTimeMillis(),
                worldInfoId = ""
            )
            toAdd.add(card)
            existingIds += personaId
            existingNames += name
            AppLogger.i(TAG, "[Legacy-Migrate] 准备迁移: id=$personaId name=$name avatar=${avatarPath.isNotEmpty()}")
        }

        if (toAdd.isEmpty()) {
            AppLogger.i(TAG, "[Legacy-Migrate] 无可迁移角色")
            prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
            return 0
        }

        // 批量添加
        val cards = getAllCards().toMutableList()
        cards.addAll(toAdd)
        saveCards(cards)
        AppLogger.i(TAG, "[Legacy-Migrate] 成功迁移 ${toAdd.size} 个角色")
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
        return toAdd.size
    }

    fun addCard(card: CharacterCard): CharacterCard {
        val cards = getAllCards().toMutableList()
        val newCard = if (card.id.isBlank()) card.copy(id = UUID.randomUUID().toString()) else card
        cards.add(newCard)
        saveCards(cards)
        return newCard
    }

    fun updateCard(card: CharacterCard) {
        val cards = getAllCards().toMutableList()
        val idx = cards.indexOfFirst { it.id == card.id }
        if (idx >= 0) {
            cards[idx] = card
            saveCards(cards)
        }
    }

    fun deleteCard(cardId: String) {
        val cards = getAllCards().toMutableList()
        if (cards.size <= 1) return
        cards.removeAll { it.id == cardId }
        saveCards(cards)
        // 清理 persona_data_{cardId}，否则 recoverFromSharedPreferences 会恢复已删除角色
        context.getSharedPreferences("persona_data_$cardId", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun importFromJson(jsonStr: String): CharacterCard? {
        return try {
            val json = JSONObject(jsonStr)
            val card = CharacterCard(
                id = UUID.randomUUID().toString(),
                name = json.optString("name", json.optString("char_name", "Unnamed")),
                description = json.optString("description", json.optString("char_description", "")),
                personality = json.optString("personality", json.optString("char_personality", "")),
                scenario = json.optString("scenario", ""),
                firstMes = json.optString("first_mes", json.optString("first_message", json.optString("greeting", ""))),
                mesExample = json.optString("mes_example", json.optString("example_dialogue", "")),
                creatorNotes = json.optString("creator_notes", ""),
                systemPrompt = json.optString("system_prompt", ""),
                postHistoryInstructions = json.optString("post_history_instructions", ""),
                alternateGreetings = json.optJSONArray("alternate_greetings")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                tags = json.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                creator = json.optString("creator", ""),
                characterVersion = json.optString("character_version", "1.0"),
                isActive = false
            )
            addCard(card)
            card
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            null
        }
    }

    fun exportCard(cardId: String): String? {
        val card = getAllCards().find { it.id == cardId } ?: return null
        return card.toJson().toString(2)
    }

    private fun saveCards(cards: List<CharacterCard>) {
        val arr = JSONArray()
        cards.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_CARDS, arr.toString()).apply()
    }

    fun getAllWorldInfos(): List<WorldInfo> {
        val json = prefs.getString(KEY_WORLD_INFOS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { WorldInfo.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getWorldInfoForCard(card: CharacterCard): WorldInfo? {
        if (card.worldInfoId.isBlank()) return null
        return getAllWorldInfos().find { it.id == card.worldInfoId }
    }

    fun addWorldInfo(worldInfo: WorldInfo): WorldInfo {
        val wis = getAllWorldInfos().toMutableList()
        val newWi = if (worldInfo.id.isBlank()) worldInfo.copy(id = UUID.randomUUID().toString()) else worldInfo
        wis.add(newWi)
        saveWorldInfos(wis)
        return newWi
    }

    fun updateWorldInfo(worldInfo: WorldInfo) {
        val wis = getAllWorldInfos().toMutableList()
        val idx = wis.indexOfFirst { it.id == worldInfo.id }
        if (idx >= 0) {
            wis[idx] = worldInfo
            saveWorldInfos(wis)
        }
    }

    fun deleteWorldInfo(id: String) {
        val wis = getAllWorldInfos().toMutableList()
        wis.removeAll { it.id == id }
        saveWorldInfos(wis)
    }

    fun addEntryToWorldInfo(worldInfoId: String, entry: WorldInfoEntry): WorldInfo? {
        val wis = getAllWorldInfos().toMutableList()
        val idx = wis.indexOfFirst { it.id == worldInfoId }
        if (idx < 0) return null
        val newEntry = if (entry.id.isBlank()) entry.copy(id = UUID.randomUUID().toString()) else entry
        val updated = wis[idx].copy(entries = wis[idx].entries + newEntry)
        wis[idx] = updated
        saveWorldInfos(wis)
        return updated
    }

    fun removeEntryFromWorldInfo(worldInfoId: String, entryId: String): WorldInfo? {
        val wis = getAllWorldInfos().toMutableList()
        val idx = wis.indexOfFirst { it.id == worldInfoId }
        if (idx < 0) return null
        val updated = wis[idx].copy(entries = wis[idx].entries.filter { it.id != entryId })
        wis[idx] = updated
        saveWorldInfos(wis)
        return updated
    }

    fun getActivatedWorldInfoEntries(card: CharacterCard, chatMessage: String): List<WorldInfoEntry> {
        val wi = getWorldInfoForCard(card) ?: return emptyList()
        val messageLower = chatMessage.lowercase()
        return wi.entries.filter { entry ->
            if (!entry.enabled) return@filter false
            if (entry.constant) return@filter true
            val keys = entry.key.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            keys.any { key -> messageLower.contains(key) }
        }.sortedBy { it.insertionOrder }
    }

    private fun saveWorldInfos(wis: List<WorldInfo>) {
        val arr = JSONArray()
        wis.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_WORLD_INFOS, arr.toString()).apply()
    }

    fun getUserPersona(): UserPersona {
        val json = prefs.getString(KEY_USER_PERSONA, null) ?: return UserPersona()
        return try {
            UserPersona.fromJson(JSONObject(json))
        } catch (e: Exception) {
            UserPersona()
        }
    }

    fun saveUserPersona(persona: UserPersona) {
        prefs.edit().putString(KEY_USER_PERSONA, persona.toJson().toString()).apply()
    }

    fun buildSystemPrompt(card: CharacterCard, userPersona: UserPersona, worldInfoEntries: List<WorldInfoEntry>, memories: List<String>, emotion: String, action: String): String {
        return buildString {
            if (card.systemPrompt.isNotBlank()) {
                append(card.systemPrompt)
            } else {
                append("你是「${card.name}」，一个AI角色。")
                if (card.description.isNotBlank()) append("\n角色描述：${card.description}")
                if (card.personality.isNotBlank()) append("\n性格特征：${card.personality}")
            }

            if (card.scenario.isNotBlank()) {
                append("\n场景设定：${card.scenario}")
            }

            if (worldInfoEntries.isNotEmpty()) {
                append("\n\n[世界信息]")
                worldInfoEntries.forEach { entry ->
                    if (entry.comment.isNotBlank()) append("\n## ${entry.comment}")
                    append("\n${entry.content}")
                }
            }

            if (userPersona.name.isNotBlank() || userPersona.description.isNotBlank() || userPersona.personality.isNotBlank()) {
                append("\n\n[用户信息]")
                if (userPersona.name.isNotBlank()) append("\n用户名字：${userPersona.name}")
                if (userPersona.description.isNotBlank()) append("\n用户描述：${userPersona.description}")
                if (userPersona.personality.isNotBlank()) append("\n用户性格：${userPersona.personality}")
                if (userPersona.appearance.isNotBlank()) append("\n用户外貌：${userPersona.appearance}")
            }

            append("\n\n你的当前情绪：$emotion。你的当前动作：$action。")

            if (memories.isNotEmpty()) {
                append("\n你记得这些关于用户的事：${memories.takeLast(3).joinToString("；")}")
            }

            append("\n\n规则：用自然的中文回复，像朋友一样聊天。保持在2-4句话以内。")
            append("如果用户表达了情绪，请根据用户的情绪给予适当的情感回应和安慰。")
            append("在回复末尾 [[emotion:xxx]] 处标注你的当前情绪（从 happy/sad/angry/surprised/neutral 中选一个）。")

            if (card.postHistoryInstructions.isNotBlank()) {
                append("\n\n[附加指令] ${card.postHistoryInstructions}")
            }
        }
    }

    fun buildFirstMessage(card: CharacterCard): String {
        return card.firstMes.ifBlank { "你好！我是${card.name}，很高兴见到你~" }
    }
}
