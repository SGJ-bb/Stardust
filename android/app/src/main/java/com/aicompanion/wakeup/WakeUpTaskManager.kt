package com.aicompanion.wakeup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aicompanion.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class WakeUpTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val isDefault: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("isDefault", isDefault)
    }

    companion object {
        fun fromJson(obj: JSONObject): WakeUpTask = WakeUpTask(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "唤醒任务"),
            description = obj.optString("description", ""),
            hour = obj.optInt("hour", 9),
            minute = obj.optInt("minute", 0),
            enabled = obj.optBoolean("enabled", true),
            isDefault = obj.optBoolean("isDefault", false)
        )
    }
}

class WakeUpTaskManager(private val context: Context) {
    companion object {
        private const val TAG = "WakeUpTaskManager"
        private const val FILE_NAME = "wakeup_tasks.json"
        private const val KEY_INITIALIZED = "wakeup_tasks_initialized"
        private const val PREFS_NAME = "wakeup_task_prefs"
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val tasks = mutableListOf<WakeUpTask>()

    fun load() {
        tasks.clear()
        if (!file.exists()) {
            initDefaults()
            return
        }
        try {
            val json = JSONObject(file.readText())
            val arr = json.optJSONArray("tasks") ?: return
            for (i in 0 until arr.length()) {
                tasks.add(WakeUpTask.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "load failed: ${e.message}")
            initDefaults()
        }
    }

    private fun initDefaults() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        tasks.add(WakeUpTask(
            id = "default_health_sleep",
            name = "健康监督",
            description = "该睡觉啦！早睡早起身体好~明天也要元气满满哦！",
            hour = 23,
            minute = 30,
            enabled = true,
            isDefault = true
        ))
        save()
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
    }

    fun save() {
        try {
            val json = JSONObject().apply {
                put("tasks", JSONArray().apply {
                    tasks.forEach { put(it.toJson()) }
                })
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "save failed: ${e.message}")
        }
    }

    fun getAllTasks(): List<WakeUpTask> = tasks.toList()

    fun addTask(task: WakeUpTask): WakeUpTask {
        tasks.add(task)
        save()
        scheduleAll()
        return task
    }

    fun updateTask(id: String, updater: (WakeUpTask) -> WakeUpTask): WakeUpTask? {
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = updater(tasks[idx])
        tasks[idx] = updated
        save()
        scheduleAll()
        return updated
    }

    fun deleteTask(id: String) {
        tasks.removeAll { it.id == id }
        save()
        scheduleAll()
    }

    fun toggleTask(id: String, enabled: Boolean) {
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            tasks[idx] = tasks[idx].copy(enabled = enabled)
            save()
            scheduleAll()
        }
    }

    fun scheduleAll() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        tasks.filter { it.enabled }.forEach { task ->
            scheduleTask(alarmManager, task)
        }
        cancelOrphanedAlarms(alarmManager)
    }

    private fun scheduleTask(alarmManager: AlarmManager, task: WakeUpTask) {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, task.hour)
            set(java.util.Calendar.MINUTE, task.minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, WakeUpReceiver::class.java).apply {
            action = WakeUpReceiver.ACTION_WAKE_UP
            putExtra("task_id", task.id)
            putExtra("task_name", task.name)
            putExtra("task_description", task.description)
        }

        val requestCode = task.id.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "scheduleTask failed: ${e.message}")
        }
    }

    private fun cancelOrphanedAlarms(alarmManager: AlarmManager) {
        val validIds = tasks.map { it.id }.toSet()
    }

    fun rescheduleOnBoot() {
        load()
        scheduleAll()
    }
}
