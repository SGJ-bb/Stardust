/** 应用分类器: 根据应用包名自动分类(社交/游戏/学习/购物等), 辅助AI理解当前场景 */
package com.aicompanion.screen

object AppCategoryClassifier {

    fun classify(packageName: String): String {
        val lower = packageName.lowercase()
        return when {
            lower.contains("game") || lower.contains("play") -> "game"
            lower.contains("browser") || lower.contains("chrome") -> "browser"
            lower.contains("video") || lower.contains("youtube") || lower.contains("bilibili") -> "video"
            lower.contains("music") || lower.contains("spotify") -> "music"
            lower.contains("social") || lower.contains("wechat") || lower.contains("qq") -> "social"
            lower.contains("work") || lower.contains("office") || lower.contains("doc") -> "work"
            else -> "unknown"
        }
    }
}
