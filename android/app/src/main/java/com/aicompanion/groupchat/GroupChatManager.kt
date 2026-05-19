package com.aicompanion.groupchat

import android.content.Context
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class GroupChat(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val memberPersonaIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageTime: Long = System.currentTimeMillis(),
    val lastMessagePreview: String = "",
    val speakMode: String = "auto",
    val relationshipSetting: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("lastMessageTime", lastMessageTime)
        put("lastMessagePreview", lastMessagePreview)
        put("speakMode", speakMode)
        put("relationshipSetting", relationshipSetting)
        val arr = JSONArray()
        memberPersonaIds.forEach { arr.put(it) }
        put("memberPersonaIds", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): GroupChat = GroupChat(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "群聊"),
            memberPersonaIds = run {
                val arr = obj.optJSONArray("memberPersonaIds") ?: JSONArray()
                (0 until arr.length()).map { arr.getString(it) }
            },
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            lastMessageTime = obj.optLong("lastMessageTime", System.currentTimeMillis()),
            lastMessagePreview = obj.optString("lastMessagePreview", ""),
            speakMode = obj.optString("speakMode", "auto"),
            relationshipSetting = obj.optString("relationshipSetting", "")
        )
    }
}

data class GroupMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderPersonaId: String,
    val senderName: String,
    val text: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUser: Boolean = false,
    val emotion: String = "neutral"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("senderPersonaId", senderPersonaId)
        put("senderName", senderName)
        put("text", text)
        put("time", time)
        put("timestamp", timestamp)
        put("isUser", isUser)
        put("emotion", emotion)
    }

    companion object {
        fun fromJson(obj: JSONObject): GroupMessage = GroupMessage(
            id = obj.optString("id", UUID.randomUUID().toString()),
            senderPersonaId = obj.optString("senderPersonaId", ""),
            senderName = obj.optString("senderName", ""),
            text = obj.optString("text", ""),
            time = obj.optString("time", ""),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            isUser = obj.optBoolean("isUser", false),
            emotion = obj.optString("emotion", "neutral")
        )
    }
}

class GroupChatManager(private val context: Context) {

    companion object {
        private const val TAG = "GroupChatManager"
        private const val PREFS_NAME = "group_chats"
        private const val KEY_GROUPS = "groups"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var groups = mutableListOf<GroupChat>()

    fun load() {
        groups.clear()
        val json = prefs.getString(KEY_GROUPS, null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    groups.add(GroupChat.fromJson(arr.getJSONObject(i)))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "load failed: ${e.message}")
            }
        }
    }

    fun save() {
        val arr = JSONArray()
        groups.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply()
    }

    fun getAllGroups(): List<GroupChat> = groups.toList()

    fun getGroup(id: String): GroupChat? = groups.find { it.id == id }

    fun addGroup(group: GroupChat): GroupChat {
        groups.add(group)
        save()
        return group
    }

    fun deleteGroup(id: String) {
        groups.removeAll { it.id == id }
        save()
        context.getSharedPreferences("group_chat_$id", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun updateGroup(group: GroupChat) {
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) {
            groups[idx] = group
            save()
        }
    }

    fun updateLastMessage(groupId: String, preview: String) {
        val idx = groups.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            groups[idx] = groups[idx].copy(
                lastMessageTime = System.currentTimeMillis(),
                lastMessagePreview = preview
            )
            save()
        }
    }

    fun getMessages(groupId: String): List<GroupMessage> {
        val chatPrefs = context.getSharedPreferences("group_chat_$groupId", Context.MODE_PRIVATE)
        val json = chatPrefs.getString("messages", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { GroupMessage.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    fun addMessage(groupId: String, message: GroupMessage) {
        val messages = getMessages(groupId).toMutableList()
        messages.add(message)
        saveMessages(groupId, messages)
        updateLastMessage(groupId, message.text.take(30))
    }

    fun saveMessages(groupId: String, messages: List<GroupMessage>) {
        val arr = JSONArray()
        messages.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences("group_chat_$groupId", Context.MODE_PRIVATE)
            .edit().putString("messages", arr.toString()).apply()
    }

    fun clearMessages(groupId: String) {
        context.getSharedPreferences("group_chat_$groupId", Context.MODE_PRIVATE)
            .edit().remove("messages").apply()
        updateLastMessage(groupId, "")
    }
}
