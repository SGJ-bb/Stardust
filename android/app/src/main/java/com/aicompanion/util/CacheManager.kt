package com.aicompanion.util

import android.content.Context
import com.aicompanion.ui.home.PersonaCard
import com.aicompanion.ui.diary.DiaryEntry
import com.aicompanion.ui.album.AlbumPhotoData
import com.aicompanion.ui.profile.ProfileData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 缓存管理器：统一管理 MainActivity 中所有首页数据缓存
 *
 * 使用 StateFlow 替代 volatile 变量，提供更安全的线程同步和 Compose 可观察性
 * 支持 TTL（生存时间）机制，避免缓存过期数据
 *
 * 优势：
 * - 线程安全：StateFlow 内置线程同步机制，无需 volatile
 * - Compose 可观察：StateFlow 可直接被 Compose 观察，变化时自动触发重组
 * - 统一管理：所有缓存集中在 CacheManager，便于清理和监控
 * - TTL 支持：支持过期时间，自动失效陈旧数据
 */
class CacheManager(private val context: Context) {

    companion object {
        private const val TAG = "CacheManager"

        // 默认 TTL 配置（毫秒）
        const val DEFAULT_TTL_MS = 5000L         // 默认 5 秒
        const val PERSONAS_TTL_MS = 5000L        // 角色列表 5 秒
        const val WALLPAPER_TTL_MS = 5000L       // 壁纸路径 5 秒
        const val AVATAR_TTL_MS = 5000L          // AI头像路径 5 秒
        const val DAYS_TTL_MS = 86_400_000L      // 相处天数 24 小时（一天内不变）
        const val DIARY_TTL_MS = 5000L           // 日记列表 5 秒
        const val ALBUM_TTL_MS = 5000L           // 相册照片 5 秒
        const val PROFILE_TTL_MS = 5000L         // 个人中心 5 秒
    }

    // === StateFlow 缓存 ===

    /** 角色列表缓存（含好感度、消息数等聚合数据） */
    private val _personasCache = MutableStateFlow<List<PersonaCard>>(emptyList())
    val personasCache: StateFlow<List<PersonaCard>> = _personasCache.asStateFlow()

    /** 壁纸路径缓存 */
    private val _wallpaperCache = MutableStateFlow<String?>(null)
    val wallpaperCache: StateFlow<String?> = _wallpaperCache.asStateFlow()

    /** AI头像路径缓存 */
    private val _aiAvatarCache = MutableStateFlow<String?>(null)
    val aiAvatarCache: StateFlow<String?> = _aiAvatarCache.asStateFlow()

    /** 相处天数缓存 */
    private val _daysCache = MutableStateFlow<Int>(1)
    val daysCache: StateFlow<Int> = _daysCache.asStateFlow()

    /** 天数缓存日期标记（用于判断是否需要更新） */
    private val _daysCacheDate = MutableStateFlow<String>("")
    val daysCacheDate: StateFlow<String> = _daysCacheDate.asStateFlow()

    /** 日记列表缓存 */
    private val _diaryCache = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val diaryCache: StateFlow<List<DiaryEntry>> = _diaryCache.asStateFlow()

    /** 相册照片列表缓存 */
    private val _albumCache = MutableStateFlow<List<AlbumPhotoData>>(emptyList())
    val albumCache: StateFlow<List<AlbumPhotoData>> = _albumCache.asStateFlow()

    /** 个人中心数据缓存 */
    private val _profileCache = MutableStateFlow<ProfileData?>(null)
    val profileCache: StateFlow<ProfileData?> = _profileCache.asStateFlow()

    // === 时间戳管理 ===

    private val cacheTimestamps = ConcurrentHashMap<String, Long>()

    // === 计算锁管理（防止并发重复计算） ===
    // 使用 LinkedHashMap + LRU 淘汰机制，防止锁无限增长导致内存溢出

    private val computeLocks = object : LinkedHashMap<String, ReentrantLock>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReentrantLock>?): Boolean {
            return size > 100  // 最多保留100个锁，超过时自动淘汰最久未访问的
        }
    }

    /** 线程安全地获取或创建锁 */
    @Synchronized
    private fun getOrCreateLock(key: String): ReentrantLock {
        return computeLocks.getOrPut(key) { ReentrantLock() }
    }

    // === 协程管理（避免协程泄漏和过度并发） ===

    /** SupervisorJob，用于管理所有子协程 */
    private var supervisorJob = SupervisorJob()

    /** 正在进行的计算任务，用于取消重复请求 */
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /** 获取当前有效的协程作用域（supervisorJob 可能已被取消重建） */
    private fun getScope(): CoroutineScope {
        // 如果 supervisorJob 已完成（被取消），重新创建
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
        }
        return CoroutineScope(supervisorJob + Dispatchers.IO)
    }

    // === 缓存操作方法 ===

    /**
     * 获取或计算缓存值（泛型版本）
     *
     * @param key 缓存键名
     * @param ttlMs TTL 时间（毫秒）
     * @param flow 对应的 MutableStateFlow
     * @param compute 计算新值的 suspend 函数
     */
    fun <T> getOrCompute(
        key: String,
        ttlMs: Long = DEFAULT_TTL_MS,
        flow: MutableStateFlow<T>,
        compute: suspend () -> T
    ) {
        // 第一次检查（无锁）
        val now = System.currentTimeMillis()
        val lastUpdate = cacheTimestamps[key] ?: 0L
        if (now - lastUpdate <= ttlMs) return

        // 获取或创建该key的专用锁（使用线程安全的LRU锁池）
        val lock = getOrCreateLock(key)

        lock.withLock {
            // 第二次检查（有锁，防止竞态）
            val nowLocked = System.currentTimeMillis()
            val lastUpdateLocked = cacheTimestamps[key] ?: 0L
            if (nowLocked - lastUpdateLocked <= ttlMs) return

            // 取消正在进行的旧计算，避免重复计算
            activeJobs[key]?.cancel()

            // 更新时间戳，标记正在计算
            cacheTimestamps[key] = nowLocked

            // 使用共享的协程作用域，避免泄漏
            activeJobs[key] = getScope().launch {
                try {
                    val value = compute()
                    flow.value = value
                    AppLogger.d(TAG, "Cache updated: $key")
                } catch (e: Exception) {
                    // CancellationException 是协程取消的正常信号，不应捕获
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // 失败时重置时间戳，允许下次重新计算
                    cacheTimestamps[key] = 0L
                    AppLogger.e(TAG, "Cache compute failed for $key: ${e.message}")
                } finally {
                    // 任务完成后移除跟踪
                    activeJobs.remove(key)
                }
            }
        }
    }

    /**
     * 强制刷新指定缓存（忽略 TTL）
     */
    fun <T> forceRefresh(
        key: String,
        flow: MutableStateFlow<T>,
        compute: suspend () -> T
    ) {
        // 取消正在进行的旧计算
        activeJobs[key]?.cancel()

        activeJobs[key] = getScope().launch {
            try {
                val value = compute()
                flow.value = value
                cacheTimestamps[key] = System.currentTimeMillis()
                AppLogger.d(TAG, "Cache force refreshed: $key")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.e(TAG, "Cache force refresh failed for $key: ${e.message}")
            } finally {
                activeJobs.remove(key)
            }
        }
    }

    /**
     * 使指定缓存失效（下次访问时重新计算）
     */
    fun invalidate(key: String) {
        cacheTimestamps[key] = 0L
        when (key) {
            "personas" -> _personasCache.value = emptyList()
            "wallpaper" -> _wallpaperCache.value = null
            "aiAvatar" -> _aiAvatarCache.value = null
            "days" -> { _daysCache.value = 1; _daysCacheDate.value = "" }
            "diary" -> _diaryCache.value = emptyList()
            "album" -> _albumCache.value = emptyList()
            "profile" -> _profileCache.value = null
        }
        AppLogger.d(TAG, "Cache invalidated: $key")
    }

    /**
     * 批量使多个缓存失效
     */
    fun invalidateAll(keys: List<String>) {
        keys.forEach { invalidate(it) }
    }

    /**
     * 使所有缓存失效
     */
    fun invalidateAll() {
        invalidate("personas")
        invalidate("wallpaper")
        invalidate("aiAvatar")
        invalidate("days")
        invalidate("diary")
        invalidate("album")
        invalidate("profile")
    }

    /**
     * 清理所有缓存（释放内存，取消所有正在进行的计算）
     */
    fun cleanup() {
        // 取消所有正在进行的计算任务
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()

        // 取消整个协程作用域
        supervisorJob.cancel()
        // getScope() 会自动创建新的 SupervisorJob，无需手动重建

        cacheTimestamps.clear()
        computeLocks.clear()
        _personasCache.value = emptyList()
        _wallpaperCache.value = null
        _aiAvatarCache.value = null
        _daysCache.value = 1
        _daysCacheDate.value = ""
        _diaryCache.value = emptyList()
        _albumCache.value = emptyList()
        _profileCache.value = null
        AppLogger.d(TAG, "All caches cleaned up")
    }

    // === 便捷访问方法 ===

    /** 获取角色列表缓存值 */
    fun getPersonas(): List<PersonaCard> = _personasCache.value

    /** 获取壁纸缓存值 */
    fun getWallpaper(): String? = _wallpaperCache.value

    /** 获取AI头像缓存值 */
    fun getAiAvatar(): String? = _aiAvatarCache.value

    /** 获取天数缓存值 */
    fun getDays(): Int = _daysCache.value

    /** 获取天数日期标记 */
    fun getDaysDate(): String = _daysCacheDate.value

    /** 获取日记列表缓存值 */
    fun getDiary(): List<DiaryEntry> = _diaryCache.value

    /** 获取相册缓存值 */
    fun getAlbum(): List<AlbumPhotoData> = _albumCache.value

    /** 获取个人中心缓存值 */
    fun getProfile(): ProfileData? = _profileCache.value

    // === 直接更新方法（用于外部直接设置缓存值） ===

    /** 直接更新角色列表缓存 */
    fun updatePersonas(value: List<PersonaCard>) {
        _personasCache.value = value
        cacheTimestamps["personas"] = System.currentTimeMillis()
    }

    /** 直接更新壁纸缓存 */
    fun updateWallpaper(value: String?) {
        _wallpaperCache.value = value
        cacheTimestamps["wallpaper"] = System.currentTimeMillis()
    }

    /** 直接更新AI头像缓存 */
    fun updateAiAvatar(value: String?) {
        _aiAvatarCache.value = value
        cacheTimestamps["aiAvatar"] = System.currentTimeMillis()
    }

    /** 直接更新天数缓存 */
    fun updateDays(value: Int, date: String) {
        _daysCache.value = value
        _daysCacheDate.value = date
        cacheTimestamps["days"] = System.currentTimeMillis()
    }

    /** 直接更新日记缓存 */
    fun updateDiary(value: List<DiaryEntry>) {
        _diaryCache.value = value
        cacheTimestamps["diary"] = System.currentTimeMillis()
    }

    /** 直接更新相册缓存 */
    fun updateAlbum(value: List<AlbumPhotoData>) {
        _albumCache.value = value
        cacheTimestamps["album"] = System.currentTimeMillis()
    }

    /** 直接更新个人中心缓存 */
    fun updateProfile(value: ProfileData) {
        _profileCache.value = value
        cacheTimestamps["profile"] = System.currentTimeMillis()
    }

    // === 缓存状态检查 ===

    /** 检查缓存是否过期 */
    fun isExpired(key: String, ttlMs: Long = DEFAULT_TTL_MS): Boolean {
        val lastUpdate = cacheTimestamps[key] ?: 0L
        return System.currentTimeMillis() - lastUpdate > ttlMs
    }

    /** 检查缓存是否有效 */
    fun isValid(key: String, ttlMs: Long = DEFAULT_TTL_MS): Boolean {
        return !isExpired(key, ttlMs)
    }

    /** 获取缓存剩余有效时间（毫秒） */
    fun getRemainingTtl(key: String, ttlMs: Long = DEFAULT_TTL_MS): Long {
        val lastUpdate = cacheTimestamps[key] ?: 0L
        val elapsed = System.currentTimeMillis() - lastUpdate
        return maxOf(0L, ttlMs - elapsed)
    }
}