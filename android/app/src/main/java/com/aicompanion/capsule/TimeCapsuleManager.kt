package com.aicompanion.capsule

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

data class TimeCapsule(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val openDate: Long,
    var isOpened: Boolean = false,
    val fromSelf: Boolean = true
)

class TimeCapsuleManager(private val context: Context, private val personaId: String = "default") {
    companion object {
        private const val TAG = "TimeCapsuleManager"
        private const val KEY_CAPSULES = "capsule_list"
        private const val KEY_LAST_CHECK = "last_check_date"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("time_capsules_$personaId", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun createCapsule(title: String, content: String, openDate: Long): TimeCapsule {
        val capsule = TimeCapsule(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            content = content,
            createdAt = System.currentTimeMillis(),
            openDate = openDate
        )
        val capsules = loadCapsules().toMutableList()
        capsules.add(capsule)
        saveCapsules(capsules)
        AppLogger.i(TAG, "Capsule created: $title, opens at ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(openDate))}")
        return capsule
    }

    fun loadCapsules(): List<TimeCapsule> {
        val json = prefs.getString(KEY_CAPSULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    TimeCapsule(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        createdAt = obj.getLong("createdAt"),
                        openDate = obj.getLong("openDate"),
                        isOpened = obj.optBoolean("isOpened", false),
                        fromSelf = obj.optBoolean("fromSelf", true)
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun getDueCapsules(): List<TimeCapsule> {
        val now = System.currentTimeMillis()
        return loadCapsules().filter { !it.isOpened && it.openDate <= now }
    }

    @Synchronized
    fun markOpened(capsuleId: String) {
        val capsules = loadCapsules().map {
            if (it.id == capsuleId) it.copy(isOpened = true) else it
        }
        saveCapsules(capsules)
    }

    @Synchronized
    fun deleteCapsule(capsuleId: String) {
        val capsules = loadCapsules().filter { it.id != capsuleId }
        saveCapsules(capsules)
    }

    @Synchronized
    fun checkAndMarkToday(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastCheck = prefs.getString(KEY_LAST_CHECK, "")
        if (lastCheck == today) return false
        prefs.edit().putString(KEY_LAST_CHECK, today).commit()
        return true
    }

    fun getOpeningMessage(capsule: TimeCapsule): String {
        val daysSince = ((System.currentTimeMillis() - capsule.createdAt) / 86400000).toInt()
        val createdDate = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
            .format(java.util.Date(capsule.createdAt))
        return when {
            daysSince >= 365 -> "你还记得吗？${createdDate}，你给自己写了一封信，已经过去${daysSince / 365}年了。让我读给你听：\n\n${capsule.content}"
            daysSince >= 30 -> "这是${createdDate}你写给未来的自己的信，已经${daysSince / 30}个月了：\n\n${capsule.content}"
            else -> "这是${createdDate}你写的时光胶囊，今天终于可以打开了：\n\n${capsule.content}"
        }
    }

    private fun saveCapsules(capsules: List<TimeCapsule>) {
        val arr = JSONArray()
        capsules.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("content", c.content)
                put("createdAt", c.createdAt)
                put("openDate", c.openDate)
                put("isOpened", c.isOpened)
                put("fromSelf", c.fromSelf)
            })
        }
        prefs.edit().putString(KEY_CAPSULES, arr.toString()).apply()
    }
}
