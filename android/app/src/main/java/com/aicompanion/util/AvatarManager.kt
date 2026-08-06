package com.aicompanion.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

/**
 * 统一头像管理器
 *
 * 解决问题：
 * - 消除 4 套并行头像路径体系（Persona模型 / avatar_data SP / ChatAdapter缓存 / MainActivity缓存）
 * - 消除 17 处散弹式 SharedPreferences("avatar_data") 调用
 * - 消除 31 处散弹式文件复制代码
 * - 提供统一的头像读写/缓存/显示接口
 */
object AvatarManager {

    private const val TAG = "AvatarManager"
    private const val PREFS_NAME = "avatar_data"

    /** 头像缓存 (8MB LRU) */
    private val bitmapCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** 预加载线程池（单例，避免每次调用都创建新线程池） */
    private val preloadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "avatar-preload").apply { isDaemon = true }
    }

    // ==================== SharedPreferences 访问 ====================

    /** 获取 avatar_data SharedPreferences（全局唯一入口） */
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== 读取接口 ====================

    /** 获取 AI 头像路径（统一来源：优先 Persona.avatarPath > SP.ai_avatar_{personaId}） */
    fun getAiAvatarPath(context: Context, personaId: String = "", personaAvatarPath: String? = null): String {
        return if (!personaAvatarPath.isNullOrBlank()) {
            personaAvatarPath
        } else {
            val key = "ai_avatar_${if (personaId.isNotBlank()) personaId else "default"}"
            prefs(context).getString(key, "") ?: ""
        }
    }

    /** 获取用户头像路径（按角色隔离） */
    fun getUserAvatarPath(context: Context, personaId: String = ""): String {
        val key = "user_avatar_${if (personaId.isNotBlank()) personaId else "default"}"
        return prefs(context).getString(key, "") ?: ""
    }

    // ==================== 写入接口 ====================

    /** 保存 AI 头像路径（按角色隔离） */
    fun saveAiAvatarPath(context: Context, personaId: String, path: String) {
        val key = "ai_avatar_${if (personaId.isNotBlank()) personaId else "default"}"
        prefs(context).edit().putString(key, path).apply()
    }

    /** 保存用户头像路径（按角色隔离） */
    fun saveUserAvatarPath(context: Context, personaId: String, path: String) {
        val key = "user_avatar_${if (personaId.isNotBlank()) personaId else "default"}"
        prefs(context).edit().putString(key, path).apply()
    }

    // ==================== 文件复制（统一消除31处散弹） ====================

    /**
     * 将 content Uri 复制为目标文件
     * 替代项目中 31 处 input.copyTo(output) 散弹代码
     */
    fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            // takePersistableUriPermission 对 PickVisualMedia URI 会失败，但复制仍可进行
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "copyUriToFile失败: ${e.message}")
            false
        }
    }

    /** 从 Uri 保存头像并返回保存后的文件路径 */
    fun saveAvatarFromUri(
        context: Context,
        uri: Uri,
        target: String,       // "ai" 或 "user"
        personaId: String = "default"
    ): String? {
        val timeStamp = System.currentTimeMillis()
        val destFile = if (target == "ai") {
            val dir = File(context.filesDir, "personas/avatars")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "avatar_${personaId}_${timeStamp}.jpg")
        } else {
            File(context.filesDir, "user_avatar_${timeStamp}.jpg")
        }

        return if (copyUriToFile(context, uri, destFile)) {
            destFile.absolutePath
        } else {
            null
        }
    }

    // ==================== Bitmap 缓存与加载 ====================

    /** 从路径加载 Bitmap（带 LRU 缓存） */
    fun loadBitmap(path: String): Bitmap? {
        val cached = bitmapCache.get(path)
        if (cached != null && !cached.isRecycled) return cached
        if (cached != null && cached.isRecycled) bitmapCache.remove(path)

        return try {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(path, options)?.also { bitmapCache.put(path, it) }
        } catch (_: Exception) {
            null
        }
    }

    /** 将 Bitmap 设置到 ImageView（圆形裁剪） */
    fun applyToImageView(imageView: ImageView, path: String?) {
        if (!path.isNullOrEmpty()) {
            loadBitmap(path)?.let { bmp ->
                if (!bmp.isRecycled) imageView.setImageBitmap(bmp)
            }
        }
        imageView.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                val size = minOf(view.width, view.height)
                outline.setOval(0, 0, size, size)
            }
        }
        imageView.clipToOutline = true
    }

    /** 预加载头像到缓存（异步） */
    fun preload(path: String) {
        if (bitmapCache.get(path) != null) return
        preloadExecutor.execute {
            try { loadBitmap(path) } catch (_: Exception) {}
        }
    }

    // ==================== 清理 ====================

    /** 清除指定路径的缓存 */
    fun evictCache(path: String?) {
        if (path != null) {
            bitmapCache.remove(path)
        }
    }

    /** 清除所有缓存 */
    fun clearCache() {
        bitmapCache.evictAll()
    }
}
