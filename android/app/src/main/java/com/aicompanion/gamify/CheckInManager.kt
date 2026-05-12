package com.aicompanion.gamify

import android.content.Context
import com.aicompanion.models.CheckInRecord
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class CheckInManager(context: Context) {

    private val prefs = context.getSharedPreferences("checkin_data", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val currentStreak: Int get() = prefs.getInt("current_streak", 0)
    private var currentStreakInternal: Int = currentStreak
        set(value) {
            prefs.edit().putInt("current_streak", value).apply()
            field = value
        }

    val totalCheckIns: Int get() = prefs.getInt("total_checkins", 0)
    private var totalCheckInsInternal: Int = totalCheckIns
        set(value) {
            prefs.edit().putInt("total_checkins", value).apply()
            field = value
        }

    val lastCheckInDate: String? get() {
        val stored = prefs.getString("last_checkin_date", null)
        return if (stored.isNullOrEmpty()) null else stored
    }

    fun checkIn(): CheckInResult {
        val today = dateFormat.format(Date())
        val lastDate = lastCheckInDate
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))

        if (lastDate == today) {
            return CheckInResult.AlreadyCheckedIn(today, currentStreak)
        }

        currentStreakInternal = when {
            lastDate == null -> 1
            lastDate == yesterday -> currentStreak + 1
            else -> 1
        }

        prefs.edit().putString("last_checkin_date", today).apply()
        totalCheckInsInternal += 1

        addRecord(CheckInRecord(today, currentStreakInternal))

        val bonus = calculateBonus(currentStreakInternal)
        return CheckInResult.Success(today, currentStreakInternal, bonus, totalCheckInsInternal)
    }

    fun isCheckedInToday(): Boolean = lastCheckInDate == dateFormat.format(Date())

    fun getHistory(): List<CheckInRecord> {
        val json = prefs.getString("checkin_history", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try { CheckInRecord.fromJson(arr.getJSONObject(i)) } catch (_: Exception) { null }
            }.sortedByDescending { it.date }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addRecord(record: CheckInRecord) {
        val history = getHistory().toMutableList()
        history.removeAll { it.date == record.date }
        history.add(0, record)
        val arr = JSONArray()
        history.take(100).forEach { arr.put(it.toJson()) }
        prefs.edit().putString("checkin_history", arr.toString()).apply()
    }

    private fun calculateBonus(streak: Int): Int = when {
        streak <= 1 -> 0
        streak == 2 -> 1
        streak == 3 -> 2
        streak % 7 == 0 -> 5
        streak % 5 == 0 -> 3
        else -> 0
    }

    sealed class CheckInResult {
        data class Success(
            val date: String,
            val streak: Int,
            val bonusAffection: Int,
            val totalCheckIns: Int
        ) : CheckInResult()

        data class AlreadyCheckedIn(
            val date: String,
            val currentStreak: Int
        ) : CheckInResult()
    }
}