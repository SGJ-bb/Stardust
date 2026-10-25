package com.aicompanion.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aicompanion.util.AppLogger

/**
 * 加密存储统一入口（companion_secure_prefs 单例）。
 *
 * EncryptedSharedPreferences 创建失败（KeyStore 偶发故障/密钥损坏）时：
 * 1. 回退到明文池 companion_secure_fallback，保证功能可用
 * 2. 冷却期后自动重试；成功时将回退池数据迁入加密池并恢复加密存储
 * 3. 若旧加密数据因密钥损坏不可读，清空后重建（反正已不可恢复）
 */
object SecurePrefsHelper {
    private const val TAG = "SecurePrefs"
    private const val SECURE_POOL = "companion_secure_prefs"
    private const val FALLBACK_POOL = "companion_secure_fallback"
    private const val RETRY_COOLDOWN_MS = 60_000L

    @Volatile private var instance: SharedPreferences? = null
    @Volatile private var lastFailureAt = 0L
    @Volatile private var loggedFailure = false

    /** 当前是否处于回退（非加密）模式 */
    fun isFallbackActive(): Boolean =
        instance == null && System.currentTimeMillis() - lastFailureAt < RETRY_COOLDOWN_MS

    /** 清空加密池与回退池（供"清除全部数据"使用） */
    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        try { instance?.edit()?.clear()?.apply() } catch (_: Exception) {}
        try {
            appContext.getSharedPreferences(SECURE_POOL, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
        try {
            appContext.getSharedPreferences(FALLBACK_POOL, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
    }

    fun get(context: Context): SharedPreferences {
        instance?.let { return it }
        val appContext = context.applicationContext
        if (System.currentTimeMillis() - lastFailureAt < RETRY_COOLDOWN_MS) {
            return fallbackPool(appContext)
        }
        return try {
            val created = createSecure(appContext)
            migrateFallbackInto(appContext, created)
            instance = created
            if (loggedFailure) {
                AppLogger.w(TAG, "EncryptedSharedPreferences 重试成功，已恢复加密存储")
                loggedFailure = false
            }
            created
        } catch (e: Exception) {
            lastFailureAt = System.currentTimeMillis()
            if (!loggedFailure) {
                loggedFailure = true
                AppLogger.e(TAG, "EncryptedSharedPreferences unavailable: ${e.javaClass.simpleName}: ${e.message}（已回退普通存储，稍后自动重试）")
            } else {
                AppLogger.w(TAG, "EncryptedSharedPreferences 仍不可用: ${e.javaClass.simpleName}")
            }
            fallbackPool(appContext)
        }
    }

    private fun createSecure(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            SECURE_POOL,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun fallbackPool(appContext: Context): SharedPreferences =
        appContext.getSharedPreferences(FALLBACK_POOL, Context.MODE_PRIVATE)

    /** 探测加密池旧数据是否仍可读（密钥损坏时读取会抛异常） */
    private fun probeReadable(prefs: SharedPreferences): Boolean = try {
        prefs.all
        true
    } catch (_: Exception) {
        false
    }

    private fun migrateFallbackInto(appContext: Context, target: SharedPreferences) {
        try {
            val fallback = fallbackPool(appContext)
            if (fallback.all.isEmpty()) return
            if (!probeReadable(target)) {
                // 旧密钥数据已不可读，清空防读取崩溃
                target.edit().clear().apply()
            }
            val editor = target.edit()
            for ((k, v) in fallback.all) {
                when (v) {
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(k, v as Set<String>)
                }
            }
            editor.apply()
            fallback.edit().clear().apply()
        } catch (e: Exception) {
            AppLogger.w(TAG, "回退池数据迁移失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
