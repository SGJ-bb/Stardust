package com.aicompanion.ilink

import android.content.Context
import com.aicompanion.util.AppLogger

class IlinkAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "IlinkAuth"
        private const val PREFS_NAME = "ilink_prefs"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_ILINK_BOT_ID = "ilink_bot_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ILINK_USER_ID = "ilink_user_id"
        private const val KEY_BOUND = "is_bound"
        private const val KEY_ACTIVE_PERSONA_ID = "active_persona_id"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isBound: Boolean
        get() = prefs.getBoolean(KEY_BOUND, false) && (prefs.getString(KEY_BOT_TOKEN, "") ?: "").isNotBlank()

    var botToken: String
        get() = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        private set(value) { prefs.edit().putString(KEY_BOT_TOKEN, value).apply() }

    var ilinkBotId: String
        get() = prefs.getString(KEY_ILINK_BOT_ID, "") ?: ""
        private set(value) { prefs.edit().putString(KEY_ILINK_BOT_ID, value).apply() }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "https://ilinkai.weixin.qq.com") ?: "https://ilinkai.weixin.qq.com"
        private set(value) { prefs.edit().putString(KEY_BASE_URL, value).apply() }

    var ilinkUserId: String
        get() = prefs.getString(KEY_ILINK_USER_ID, "") ?: ""
        private set(value) { prefs.edit().putString(KEY_ILINK_USER_ID, value).apply() }

    var activePersonaId: String
        get() = prefs.getString(KEY_ACTIVE_PERSONA_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_ACTIVE_PERSONA_ID, value).apply() }

    fun saveBinding(status: QrcodeStatus.Confirmed) {
        prefs.edit().apply {
            putString(KEY_BOT_TOKEN, status.botToken)
            putString(KEY_ILINK_BOT_ID, status.ilinkBotId)
            putString(KEY_BASE_URL, status.baseUrl)
            putString(KEY_ILINK_USER_ID, status.ilinkUserId)
            putBoolean(KEY_BOUND, true)
            apply()
        }
        AppLogger.i(TAG, "Binding saved: botId=${status.ilinkBotId}, userId=${status.ilinkUserId}")
    }

    fun clearBinding() {
        prefs.edit().clear().apply()
        AppLogger.i(TAG, "Binding cleared")
    }
}
