package com.aicompanion.character

import android.content.Context
import android.util.Log
import com.aicompanion.models.CharacterCard
import com.aicompanion.models.WorldInfo
import com.aicompanion.models.WorldInfoEntry
import com.aicompanion.models.UserPersona
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CharacterCardManager(private val context: Context) {

    companion object {
        private const val TAG = "CharacterCardManager"
        private const val PREFS_NAME = "character_cards"
        private const val KEY_CARDS = "cards"
        private const val KEY_ACTIVE_CARD = "active_card_id"
        private const val KEY_WORLD_INFOS = "world_infos"
        private const val KEY_USER_PERSONA = "user_persona"
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

    fun getActiveCard(): CharacterCard {
        val cards = getAllCards()
        val activeId = prefs.getString(KEY_ACTIVE_CARD, null)
        return cards.find { it.id == activeId } ?: cards.firstOrNull { it.isActive } ?: cards.first()
    }

    fun setActiveCard(cardId: String) {
        val cards = getAllCards().toMutableList()
        cards.forEach { card ->
            val updated = if (card.id == cardId) card.copy(isActive = true) else card.copy(isActive = false)
            val idx = cards.indexOf(card)
            if (idx >= 0) cards[idx] = updated
        }
        saveCards(cards)
        prefs.edit().putString(KEY_ACTIVE_CARD, cardId).apply()
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
        if (cards.none { it.isActive }) {
            cards[0] = cards[0].copy(isActive = true)
            prefs.edit().putString(KEY_ACTIVE_CARD, cards[0].id).apply()
        }
        saveCards(cards)
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
