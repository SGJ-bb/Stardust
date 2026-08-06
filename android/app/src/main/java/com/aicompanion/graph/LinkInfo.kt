package com.aicompanion.graph

/**
 * 链接信息数据模型
 *
 * 表示日记中的一个链接,包含链接类型、目标、位置等信息。
 * 支持显式链接([[xxx]])和隐式链接(自动提取的实体)。
 *
 * 设计参考: Obsidian Wikilink语法
 */
data class LinkInfo(
    val type: LinkType,           // 链接类型(diary/topic/person/location)
    val target: String,           // 目标ID(如"2024-01-15"、"健康"、"张三")
    val position: Int,            // 在原文中的位置(字符索引)
    val isExplicit: Boolean,      // 是否显式链接([[xxx]]语法)
    val alias: String? = null     // 显示别名(如[[日记:2024-01-15|昨天]])
) {
    /**
     * 生成链接的唯一标识
     * 格式: 类型:目标 (如"日记:2024-01-15")
     */
    fun toLinkId(): String = "${type.prefix}:$target"

    /**
     * 获取显示文本
     * 如果有别名则显示别名,否则显示目标名称
     */
    fun getDisplayText(): String = alias ?: target

    /**
     * 转换为JSON格式字符串(用于持久化)
     */
    fun toJson(): String {
        val aliasPart = if (alias != null) ", \"alias\": \"$alias\"" else ""
        return """{"type": "${type.name}", "target": "$target", "position": $position, "isExplicit": $isExplicit$aliasPart}"""
    }

    companion object {
        /**
         * 从JSON字符串解析LinkInfo
         */
        fun fromJson(json: String): LinkInfo? {
            return try {
                // 简单的JSON解析(生产环境建议使用Gson或Moshi)
                val typeMatch = Regex("\"type\": \"(\\w+)\"").find(json)
                val targetMatch = Regex("\"target\": \"([^\"]+)\"").find(json)
                val positionMatch = Regex("\"position\": (\\d+)").find(json)
                val isExplicitMatch = Regex("\"isExplicit\": (true|false)").find(json)
                val aliasMatch = Regex("\"alias\": \"([^\"]+)\"").find(json)

                LinkInfo(
                    type = LinkType.valueOf(typeMatch?.groupValues?.get(1) ?: "DIARY"),
                    target = targetMatch?.groupValues?.get(1) ?: "",
                    position = positionMatch?.groupValues?.get(1)?.toInt() ?: 0,
                    isExplicit = isExplicitMatch?.groupValues?.get(1)?.toBoolean() ?: true,
                    alias = aliasMatch?.groupValues?.get(1)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 链接类型枚举
 *
 * 定义日记中可链接的实体类型,每种类型对应不同的颜色和跳转行为。
 */
enum class LinkType(val prefix: String, val color: Int, val displayName: String) {
    DIARY("日记", 0xFF2196F3.toInt(), "日记"),      // 蓝色
    TOPIC("主题", 0xFF4CAF50.toInt(), "主题"),      // 绿色
    PERSON("人物", 0xFFFF9800.toInt(), "人物"),     // 橙色
    LOCATION("地点", 0xFF9C27B0.toInt(), "地点"),   // 紫色
    EVENT("事件", 0xFFF44336.toInt(), "事件");      // 红色

    companion object {
        /**
         * 从前缀字符串解析LinkType
         * @param prefix 前缀字符串(如"日记"、"主题")
         * @return 对应的LinkType,如果未找到则返回DIARY
         */
        fun fromPrefix(prefix: String): LinkType {
            return values().find { it.prefix == prefix } ?: DIARY
        }

        /**
         * 从完整ID解析LinkType
         * @param id 完整ID(如"日记:2024-01-15")
         * @return 对应的LinkType
         */
        fun fromId(id: String): LinkType {
            val prefix = id.substringBefore(":", "日记")
            return fromPrefix(prefix)
        }
    }
}