package com.aicompanion.models

/** 数据模型定义: 包含所有核心数据类(角色/消息/好感度/成就/日记等)和枚举(Emotion/Action/TextureQuality/DayCountLevel/DiaryTriggerMode) */

import org.json.JSONArray
import org.json.JSONObject

enum class Emotion { HAPPY, ANGRY, SAD, SURPRISED, TSUNDERE, NEUTRAL }

enum class Action { TAIL_FLICK, EAR_TWITCH, BLUSH, STRETCH, YAWN, IDLE, TAP }

enum class TextureQuality { LOW, MEDIUM, HIGH }

data class CharacterCard(
    val id: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "1.0",
    val avatarPath: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val worldInfoId: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("personality", personality)
        put("scenario", scenario)
        put("first_mes", firstMes)
        put("mes_example", mesExample)
        put("creator_notes", creatorNotes)
        put("system_prompt", systemPrompt)
        put("post_history_instructions", postHistoryInstructions)
        put("alternate_greetings", JSONArray(alternateGreetings))
        put("tags", JSONArray(tags))
        put("creator", creator)
        put("character_version", characterVersion)
        put("avatar_path", avatarPath)
        put("is_active", isActive)
        put("created_at", createdAt)
        put("world_info_id", worldInfoId)
    }

    companion object {
        fun fromJson(json: JSONObject): CharacterCard = CharacterCard(
            id = json.optString("id", ""),
            name = json.optString("name", ""),
            description = json.optString("description", ""),
            personality = json.optString("personality", ""),
            scenario = json.optString("scenario", ""),
            firstMes = json.optString("first_mes", ""),
            mesExample = json.optString("mes_example", ""),
            creatorNotes = json.optString("creator_notes", ""),
            systemPrompt = json.optString("system_prompt", ""),
            postHistoryInstructions = json.optString("post_history_instructions", ""),
            alternateGreetings = json.optJSONArray("alternate_greetings")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            tags = json.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            creator = json.optString("creator", ""),
            characterVersion = json.optString("character_version", "1.0"),
            avatarPath = json.optString("avatar_path", ""),
            isActive = json.optBoolean("is_active", false),
            createdAt = json.optLong("created_at", System.currentTimeMillis()),
            worldInfoId = json.optString("world_info_id", "")
        )

        fun defaultCard(): CharacterCard = CharacterCard(
            id = "default_stardust",
            name = "星尘",
            description = "一只异色瞳黑猫，傲娇毒舌但内心关心主人",
            personality = "傲娇、毒舌、但内心温柔关心主人、偶尔会害羞、喜欢被夸奖",
            scenario = "你是主人的AI桌宠，住在主人的手机里",
            firstMes = "哼，你终于来了？我才没有在等你呢...",
            systemPrompt = "你是「星尘」，一只异色瞳黑猫AI桌宠。性格傲娇毒舌但内心关心主人。说话风格简短自然，偶尔带点小傲娇。用中文回复。",
            tags = listOf("猫", "傲娇", "默认"),
            creator = "AI Companion",
            isActive = true
        )
    }
}

data class WorldInfoEntry(
    val id: String,
    val key: String,
    val keySecondary: String = "",
    val content: String,
    val comment: String = "",
    val constant: Boolean = false,
    val selective: Boolean = false,
    val insertionOrder: Int = 100,
    val enabled: Boolean = true,
    val position: String = "before_char",
    val extensions: JSONObject = JSONObject()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("key", key)
        put("keysecondary", keySecondary)
        put("content", content)
        put("comment", comment)
        put("constant", constant)
        put("selective", selective)
        put("insertionorder", insertionOrder)
        put("enabled", enabled)
        put("position", position)
    }

    companion object {
        fun fromJson(json: JSONObject): WorldInfoEntry = WorldInfoEntry(
            id = json.optString("id", ""),
            key = json.optString("key", ""),
            keySecondary = json.optString("keysecondary", ""),
            content = json.optString("content", ""),
            comment = json.optString("comment", ""),
            constant = json.optBoolean("constant", false),
            selective = json.optBoolean("selective", false),
            insertionOrder = json.optInt("insertionorder", 100),
            enabled = json.optBoolean("enabled", true),
            position = json.optString("position", "before_char")
        )
    }
}

data class WorldInfo(
    val id: String,
    val name: String,
    val entries: List<WorldInfoEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("entries", JSONArray(entries.map { it.toJson() }))
        put("created_at", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): WorldInfo = WorldInfo(
            id = json.optString("id", ""),
            name = json.optString("name", ""),
            entries = json.optJSONArray("entries")?.let { arr ->
                (0 until arr.length()).map { WorldInfoEntry.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            createdAt = json.optLong("created_at", System.currentTimeMillis())
        )
    }
}

data class UserPersona(
    val id: String = "default",
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val appearance: String = "",
    val isActive: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("personality", personality)
        put("appearance", appearance)
        put("is_active", isActive)
    }

    companion object {
        fun fromJson(json: JSONObject): UserPersona = UserPersona(
            id = json.optString("id", "default"),
            name = json.optString("name", ""),
            description = json.optString("description", ""),
            personality = json.optString("personality", ""),
            appearance = json.optString("appearance", ""),
            isActive = json.optBoolean("is_active", true)
        )
    }
}

data class ChatResponse(
    val text: String,
    val emotion: Emotion,
    val action: Action,
    val audioUrl: String? = null,
    val errorMessage: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val reasoningContent: String? = null
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class MemoryFact(
    val id: String,
    val userId: String,
    val fact: String,
    val timestamp: Long,
    val category: String
)

data class Live2DModel(
    val id: String,
    val name: String,
    val description: String,
    val modelPath: String,
    val texturePath: String,
    val physicsPath: String = "",
    val motionPath: String = "",
    val version: String,
    val sizeMB: Float = 0f,
    val isActive: Boolean = false,
    val textureQuality: TextureQuality = TextureQuality.HIGH,
    val fps: Int = 30
)

data class AppCategory(
    val packageName: String,
    val category: String,
    val confidence: Float
)

data class DailyCardData(
    val title: String,
    val content: String
)

data class CheckInRecord(
    val date: String,
    val streak: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("streak", streak)
    }

    companion object {
        fun fromJson(json: JSONObject): CheckInRecord = CheckInRecord(
            date = json.optString("date", ""),
            streak = json.optInt("streak", 0)
        )
    }
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val category: String,
    val unlockCondition: Int,
    var progress: Int = 0,
    var unlocked: Boolean = false,
    var unlockedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("icon", icon)
        put("category", category)
        put("unlockCondition", unlockCondition)
        put("progress", progress)
        put("unlocked", unlocked)
        put("unlockedAt", unlockedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Achievement = Achievement(
            id = json.optString("id", ""),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            icon = json.optString("icon", "🏆"),
            category = json.optString("category", "general"),
            unlockCondition = json.optInt("unlockCondition", 0),
            progress = json.optInt("progress", 0),
            unlocked = json.optBoolean("unlocked", false),
            unlockedAt = json.optLong("unlockedAt", 0L)
        )
    }
}

data class GrowthNode(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val requiredAffection: Int,
    val unlocked: Boolean = false,
    val children: List<GrowthNode> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("icon", icon)
        put("description", description)
        put("requiredAffection", requiredAffection)
        put("unlocked", unlocked)
    }

    companion object {
        fun fromJson(json: JSONObject): GrowthNode = GrowthNode(
            id = json.optString("id", ""),
            name = json.optString("name", ""),
            icon = json.optString("icon", "🌱"),
            description = json.optString("description", ""),
            requiredAffection = json.optInt("requiredAffection", 0),
            unlocked = json.optBoolean("unlocked", false)
        )
    }
}