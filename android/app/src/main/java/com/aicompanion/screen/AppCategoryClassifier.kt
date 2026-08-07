/**
 * 应用分类器: 根据应用包名自动分类(社交/视频/购物/理财等), 辅助AI理解当前场景
 *
 * 三级查询：
 * 1. 内置映射表（80+ 主流国产 App，见 AppCategoryMapping）
 * 2. 用户自定义映射（SharedPreferences "app_category_overrides"）
 * 3. 关键字兜底匹配（包名包含字符串，旧逻辑保留作为 fallback）
 *
 * 结果缓存在内存 Map，避免重复查找
 */
package com.aicompanion.screen

import android.content.Context
import android.content.SharedPreferences

object AppCategoryClassifier {

    private const val PREFS_NAME = "app_category_overrides"
    private const val KEY_PREFIX = "pkg_"
    private const val CACHE_LIMIT = 200

    /** 内存缓存：包名→类别，避免重复查表 */
    private val cache = android.util.LruCache<String, String>(CACHE_LIMIT)

    /** 用户自定义映射（延迟初始化，需先调用 init(context)） */
    private var userOverrides: SharedPreferences? = null

    /** 初始化用户自定义映射存储（建议在 AppContainer 中调用一次） */
    fun init(context: Context) {
        if (userOverrides != null) return
        userOverrides = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 分类入口：按 三级查询 依次匹配
     * @param packageName 应用包名
     * @return 类别字符串（如 "social"/"video"/"unknown"）
     */
    fun classify(packageName: String): String {
        if (packageName.isBlank()) return AppCategoryMapping.UNKNOWN

        // 1. 内存缓存
        cache.get(packageName)?.let { return it }

        // 2. 内置映射（精确匹配）
        AppCategoryMapping.BUILTIN[packageName]?.let {
            cache.put(packageName, it)
            return it
        }

        // 3. 用户自定义映射
        userOverrides?.getString(KEY_PREFIX + packageName, null)?.let {
            cache.put(packageName, it)
            return it
        }

        // 4. 关键字兜底匹配（旧逻辑，处理未收录的 App）
        val fallback = fallbackClassify(packageName)
        cache.put(packageName, fallback)
        return fallback
    }

    /** 旧版关键字匹配，作为兜底 */
    private fun fallbackClassify(packageName: String): String {
        val lower = packageName.lowercase()
        return when {
            lower.contains("game") || lower.contains("play") -> AppCategoryMapping.GAME
            lower.contains("browser") || lower.contains("chrome") -> AppCategoryMapping.BROWSER
            lower.contains("video") || lower.contains("youtube") || lower.contains("bilibili") -> AppCategoryMapping.VIDEO
            lower.contains("music") || lower.contains("spotify") -> AppCategoryMapping.MUSIC
            lower.contains("social") || lower.contains("wechat") || lower.contains("qq") -> AppCategoryMapping.SOCIAL
            lower.contains("work") || lower.contains("office") || lower.contains("doc") -> AppCategoryMapping.WORK
            else -> AppCategoryMapping.UNKNOWN
        }
    }

    /** 设置用户自定义映射（供设置界面调用） */
    fun setUserOverride(packageName: String, category: String) {
        userOverrides?.edit()?.putString(KEY_PREFIX + packageName, category)?.apply()
        cache.put(packageName, category)
    }

    /** 删除用户自定义映射（恢复为内置/兜底） */
    fun removeUserOverride(packageName: String) {
        userOverrides?.edit()?.remove(KEY_PREFIX + packageName)?.apply()
        cache.remove(packageName)
    }

    /** 获取用户所有自定义映射（供设置界面展示） */
    fun getAllUserOverrides(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        userOverrides?.all?.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String) {
                result[key.removePrefix(KEY_PREFIX)] = value
            }
        }
        return result
    }

    /** 清空内存缓存（用户修改映射后可调用） */
    fun clearCache() {
        cache.evictAll()
    }
}
