package com.aicompanion.milestone

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

data class Milestone(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val category: String = "general"
)

class MilestoneManager(private val context: Context, private val personaId: String = "default") {
    companion object {
        private const val TAG = "MilestoneManager"
        private const val KEY_MILESTONES = "milestone_list"
        private const val KEY_NOTIFIED_TODAY = "notified_today"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("milestones_$personaId", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun recordMilestone(id: String, title: String, description: String, category: String = "general"): Boolean {
        val existing = loadMilestones()
        if (existing.any { it.id == id }) return false
        val milestone = Milestone(id, title, description, System.currentTimeMillis(), category)
        val updated = existing + milestone
        saveMilestones(updated)
        AppLogger.i(TAG, "Milestone recorded: $title")
        return true
    }

    fun hasMilestone(id: String): Boolean {
        return loadMilestones().any { it.id == id }
    }

    fun loadMilestones(): List<Milestone> {
        val json = prefs.getString(KEY_MILESTONES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    Milestone(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        description = obj.getString("description"),
                        timestamp = obj.getLong("timestamp"),
                        category = obj.getString("category")
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun getTodayAnniversaries(): List<Milestone> {
        val today = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        return loadMilestones().filter { m ->
            val date = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(m.timestamp))
            date == today && m.timestamp < System.currentTimeMillis() - 86400000
        }
    }

    @Synchronized
    fun shouldNotifyAnniversary(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val lastNotified = prefs.getString(KEY_NOTIFIED_TODAY, "")
        if (lastNotified == today) return false
        val anniversaries = getTodayAnniversaries()
        if (anniversaries.isNotEmpty()) {
            prefs.edit().putString(KEY_NOTIFIED_TODAY, today).apply()
            return true
        }
        return false
    }

    fun getAnniversaryMessages(): List<String> {
        return getTodayAnniversaries().map { m ->
            val days = ((System.currentTimeMillis() - m.timestamp) / 86400000).toInt()
            when {
                days >= 365 -> "${m.title}已经${days / 365}周年了呢！时间过得好快~"
                days >= 30 -> "${m.title}已经${days / 30}个月了，一直陪着你哦~"
                else -> "${m.title}已经${days}天了~"
            }
        }
    }

    private fun saveMilestones(milestones: List<Milestone>) {
        val arr = JSONArray()
        milestones.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("title", m.title)
                put("description", m.description)
                put("timestamp", m.timestamp)
                put("category", m.category)
            })
        }
        prefs.edit().putString(KEY_MILESTONES, arr.toString()).apply()
    }
}
