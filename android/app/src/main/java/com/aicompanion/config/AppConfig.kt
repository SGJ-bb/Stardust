package com.aicompanion.config

import android.content.SharedPreferences

/**
 * 应用全局配置
 * 集中管理所有可配置参数
 */
object AppConfig {
    // === Live2D配置 ===
    const val LIVE2D_TOUCH_COOLDOWN_DEFAULT = 3000L
    const val LIVE2D_MODEL_SCALE_MIN = 0.3f
    const val LIVE2D_MODEL_SCALE_MAX = 3.0f
    
    // === 记忆配置 ===
    const val MEMORY_MAX_CHARS_DEFAULT = 650
    const val MEMORY_MAX_CHARS_LARGE_MODEL = 2000
    const val MEMORY_MAX_CHARS_SMALL_MODEL = 400
    const val MEMORY_IMPORTANCE_THRESHOLD = 0.3f
    
    // === 聊天配置 ===
    const val CHAT_MAX_MESSAGES = 500
    const val CHAT_HISTORY_SAVE_COUNT = 100
    const val CHAT_HISTORY_SAVE_FALLBACK = 20
    
    // === 缓存配置 ===
    const val CACHE_TTL_DEFAULT = 5000L  // 5秒
    const val CACHE_TTL_PERSONAS = 5000L
    const val CACHE_TTL_WALLPAPER = 10_000L  // 10秒
    const val CACHE_TTL_AVATAR = 10_000L
    
    // === 语音配置 ===
    const val VOICE_PENDING_QUEUE_MAX = 3
    const val VOICE_PENDING_QUEUE_EXPIRE_MS = 30_000L  // 30秒
    
    // === 网络配置 ===
    const val NETWORK_TIMEOUT_CONNECT_DEFAULT = 15_000L
    const val NETWORK_TIMEOUT_READ_DEFAULT = 30_000L
    const val NETWORK_TIMEOUT_WRITE_DEFAULT = 60_000L
    
    // === 功能面板索引 ===
    const val FEATURE_CHECKIN = 0
    const val FEATURE_ACHIEVEMENT = 1
    const val FEATURE_DIARY = 2
    const val FEATURE_FOCUS_TIMER = 3
    const val FEATURE_MODEL_MANAGER = 4
    const val FEATURE_WALLPAPER = 5
    const val FEATURE_LOG = 6
    const val FEATURE_TUTORIAL = 7
    const val FEATURE_AUTO_OP = 8
    const val FEATURE_AI_DIARY = 9
    const val FEATURE_MEMORY_POOL = 10
    const val FEATURE_NEW_SESSION = 11
    const val FEATURE_EMOJI = 12
    const val FEATURE_SKIN_SHOP = 13
    const val FEATURE_CHAT_HISTORY = 14
    const val FEATURE_ALBUM = 15
    const val FEATURE_CLEAR_CHAT = 99
    
    /**
     * 从SharedPreferences读取配置
     */
    fun getLive2DTouchCooldown(prefs: SharedPreferences): Long {
        return prefs.getLong("live2d_touch_cooldown", LIVE2D_TOUCH_COOLDOWN_DEFAULT)
    }
    
    fun getMemoryMaxChars(prefs: SharedPreferences): Int {
        val modelSize = prefs.getString("model_size", "medium")
        return when (modelSize) {
            "large" -> MEMORY_MAX_CHARS_LARGE_MODEL
            "small" -> MEMORY_MAX_CHARS_SMALL_MODEL
            else -> MEMORY_MAX_CHARS_DEFAULT
        }
    }
}