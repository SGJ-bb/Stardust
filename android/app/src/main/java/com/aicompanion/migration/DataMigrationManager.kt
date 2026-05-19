package com.aicompanion.migration

import android.content.Context
import com.aicompanion.util.AppLogger

object DataMigrationManager {

    private const val TAG = "DataMigrationManager"
    private const val MIGRATION_PREFS = "migration_prefs"
    private const val KEY_CURRENT_VERSION = "data_version"
    private const val LATEST_VERSION = 2

    fun migrateIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(KEY_CURRENT_VERSION, 0)

        if (currentVersion >= LATEST_VERSION) return

        AppLogger.d(TAG, "Starting migration from v$currentVersion to v$LATEST_VERSION")

        try {
            if (currentVersion < 1) {
                migrateV0toV1(context)
            }
            if (currentVersion < 2) {
                migrateV1toV2(context)
            }

            prefs.edit().putInt(KEY_CURRENT_VERSION, LATEST_VERSION).apply()
            AppLogger.d(TAG, "Migration complete: v$currentVersion -> v$LATEST_VERSION")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Migration failed: ${e.message}")
        }
    }

    private fun migrateV0toV1(context: Context) {
        AppLogger.d(TAG, "Migrating v0 -> v1: persona_data separation")

        val oldPrefs = context.getSharedPreferences("persona_data", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"

        val newPrefs = context.getSharedPreferences("persona_data_$activeId", Context.MODE_PRIVATE)
        val newEditor = newPrefs.edit()

        val keysToMigrate = listOf(
            "persona_name", "persona_desc", "persona_greeting", "persona_personality",
            "persona_speech_style", "persona_catchphrases", "persona_appearance",
            "persona_preferences", "world_setting", "world_relationship", "world_rules",
            "user_nickname"
        )

        var migrated = 0
        for (key in keysToMigrate) {
            val value = oldPrefs.getString(key, null)
            if (value != null && newPrefs.getString(key, null) == null) {
                newEditor.putString(key, value)
                migrated++
            }
        }
        newEditor.apply()

        AppLogger.d(TAG, "Migrated $migrated persona fields from persona_data to persona_data_$activeId")
    }

    private fun migrateV1toV2(context: Context) {
        AppLogger.d(TAG, "Migrating v1 -> v2: memory_pool separation + wakeup sync + api key fix")

        migrateMemoryPool(context)
        syncWakeupSettings(context)
        migrateApiKeyToSecure(context)
        migrateChatHistory(context)
        migrateAffectionData(context)
        migrateStatsData(context)
        migrateAchievementsData(context)
        migrateFavoritesData(context)
        migrateMomentsData(context)
    }

    private fun migrateMemoryPool(context: Context) {
        val oldPrefs = context.getSharedPreferences("memory_pool", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"
        val newPrefs = context.getSharedPreferences("memory_pool_$activeId", Context.MODE_PRIVATE)

        if (newPrefs.getAll().isEmpty()) {
            val entries = oldPrefs.getString("entries", null)
            if (entries != null) {
                try {
                    val arr = org.json.JSONArray(entries)
                    val cleanedArr = org.json.JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val newObj = org.json.JSONObject()
                        newObj.put("id", obj.optString("id", java.util.UUID.randomUUID().toString().take(8)))
                        newObj.put("content", obj.getString("content"))
                        newObj.put("category", obj.optString("category", "其他"))
                        newObj.put("timestamp", obj.optLong("timestamp", System.currentTimeMillis()))
                        newObj.put("sourceTurn", obj.optInt("sourceTurn", 0))
                        cleanedArr.put(newObj)
                    }
                    newPrefs.edit()
                        .putString("entries", cleanedArr.toString())
                        .putInt("turns_since_consolidate", oldPrefs.getInt("turns_since_consolidate", 0))
                        .putInt("total_turns", oldPrefs.getInt("total_turns", 0))
                        .apply()
                    AppLogger.d(TAG, "Migrated memory_pool -> memory_pool_$activeId (${cleanedArr.length()} entries)")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to migrate memory_pool: ${e.message}")
                }
            }
        }
    }

    private fun syncWakeupSettings(context: Context) {
        val settingsPrefs = context.getSharedPreferences("companion_settings", Context.MODE_PRIVATE)
        val wakeupPrefs = context.getSharedPreferences("wakeup_settings", Context.MODE_PRIVATE)

        val editor = settingsPrefs.edit()

        if (!settingsPrefs.contains("wake_enabled")) {
            val enabled = wakeupPrefs.getBoolean("wakeup_enabled", false)
            editor.putBoolean("wake_enabled", enabled)
        }
        if (!settingsPrefs.contains("wake_hour")) {
            val hour = wakeupPrefs.getInt("wakeup_hour", 8)
            editor.putInt("wake_hour", hour)
        }
        if (!settingsPrefs.contains("wake_minute")) {
            val minute = wakeupPrefs.getInt("wakeup_minute", 0)
            editor.putInt("wake_minute", minute)
        }
        if (!settingsPrefs.contains("wake_message")) {
            val message = wakeupPrefs.getString("wake_message", "")
            editor.putString("wake_message", message)
        }

        editor.apply()
        AppLogger.d(TAG, "Synced wakeup_settings -> companion_settings")
    }

    private fun migrateApiKeyToSecure(context: Context) {
        val settingsPrefs = context.getSharedPreferences("companion_settings", Context.MODE_PRIVATE)
        val plainApiKey = settingsPrefs.getString("chat_api_key", null)
        if (plainApiKey.isNullOrBlank()) return

        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context, "companion_secure_prefs", masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            if (securePrefs.getString("chat_api_key", null) == null) {
                securePrefs.edit().putString("chat_api_key", plainApiKey).apply()
                settingsPrefs.edit().remove("chat_api_key").apply()
                AppLogger.d(TAG, "Migrated chat_api_key to encrypted storage")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plaintext storage for API key", e)
            val securePrefs = context.getSharedPreferences("companion_prefs_fallback", Context.MODE_PRIVATE)
            if (securePrefs.getString("chat_api_key", null) == null) {
                securePrefs.edit().putString("chat_api_key", plainApiKey).apply()
                settingsPrefs.edit().remove("chat_api_key").apply()
                AppLogger.d(TAG, "Migrated chat_api_key to fallback storage")
            }
        }
    }

    private fun migrateChatHistory(context: Context) {
        val oldPrefs = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"
        val newPrefs = context.getSharedPreferences("chat_history_$activeId", Context.MODE_PRIVATE)

        if (newPrefs.getAll().isEmpty()) {
            val messages = oldPrefs.getString("messages", null)
            if (messages != null) {
                newPrefs.edit().putString("messages", messages).apply()
                AppLogger.d(TAG, "Migrated chat_history -> chat_history_$activeId")
            }
        }
    }

    private fun migrateAffectionData(context: Context) {
        val oldPrefs = context.getSharedPreferences("affection_data", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"
        val newPrefs = context.getSharedPreferences("affection_data_$activeId", Context.MODE_PRIVATE)

        if (newPrefs.getAll().isEmpty()) {
            val editor = newPrefs.edit()
            for ((key, value) in oldPrefs.getAll()) {
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
            editor.apply()
            AppLogger.d(TAG, "Migrated affection_data -> affection_data_$activeId")
        }
    }

    private fun migrateStatsData(context: Context) {
        val oldPrefs = context.getSharedPreferences("persona_stats", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"
        val newPrefs = context.getSharedPreferences("persona_stats_$activeId", Context.MODE_PRIVATE)

        if (newPrefs.getAll().isEmpty()) {
            val editor = newPrefs.edit()
            for ((key, value) in oldPrefs.getAll()) {
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            }
            editor.apply()
            AppLogger.d(TAG, "Migrated persona_stats -> persona_stats_$activeId")
        }
    }

    private fun migrateAchievementsData(context: Context) {
        val oldPrefs = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"
        val newPrefs = context.getSharedPreferences("achievements_$activeId", Context.MODE_PRIVATE)

        if (newPrefs.getAll().isEmpty()) {
            val editor = newPrefs.edit()
            for ((key, value) in oldPrefs.getAll()) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                }
            }
            editor.apply()
            AppLogger.d(TAG, "Migrated achievements -> achievements_$activeId")
        }
    }

    private fun migrateFavoritesData(context: Context) {
        val oldPrefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"
        val newPrefs = context.getSharedPreferences("favorites_$activeId", Context.MODE_PRIVATE)

        if (newPrefs.getAll().isEmpty()) {
            val messages = oldPrefs.getString("messages", null)
            if (messages != null) {
                newPrefs.edit().putString("messages", messages).apply()
                AppLogger.d(TAG, "Migrated favorites -> favorites_$activeId")
            }
        }
    }

    private fun migrateMomentsData(context: Context) {
        val oldPrefs = context.getSharedPreferences("memorable_moments", Context.MODE_PRIVATE)
        if (oldPrefs.getAll().isEmpty()) return

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val activeId = appPrefs.getString("active_persona_id", "default") ?: "default"
        val newPrefs = context.getSharedPreferences("memorable_moments_$activeId", Context.MODE_PRIVATE)

        if (newPrefs.getAll().isEmpty()) {
            val moments = oldPrefs.getString("moments", null)
            if (moments != null) {
                newPrefs.edit().putString("moments", moments).apply()
                AppLogger.d(TAG, "Migrated memorable_moments -> memorable_moments_$activeId")
            }
        }
    }
}
