package com.aicompanion.graph

/**
 * 图谱节点数据模型
 *
 * 表示关系图谱中的一个节点,包含位置、速度、颜色等信息。
 * 用于力导向布局算法和Canvas渲染。
 *
 * 设计参考: Obsidian Graph View
 */
data class GraphNode(
    val id: String,                       // 节点ID(如"日记:2024-01-15")
    val label: String,                    // 显示标签(如"2024-01-15"或"健康")
    val type: NodeType,                   // 节点类型
    var pos: Vector2D = Vector2D(0f, 0f), // 位置坐标(物理引擎会更新)
    var velocity: Vector2D = Vector2D(0f, 0f), // 运动速度(用于物理引擎)
    var size: Float = 20.0f,              // 节点大小(可根据连接数量动态调整)
    val color: Int = type.color,          // 颜色(可根据类型染色)
    var isCenter: Boolean = false,        // 是否中心节点(当前日记)
    var isHighlighted: Boolean = false,   // 是否高亮(搜索或悬停)
    var alpha: Float = 1.0f               // 透明度(用于淡化非匹配节点)
) {
    /**
     * 检查点击位置是否在节点范围内
     * @param clickPos 点击坐标
     * @return true表示点击在节点范围内
     */
    fun containsPoint(clickPos: Vector2D): Boolean {
        val dist = (pos - clickPos).length()
        return dist <= size
    }

    /**
     * 获取连接数量对应的节点大小
     * 节点大小随连接数量增长,但有上限
     */
    fun getAdjustedSize(connectionCount: Int): Float {
        val minSize = 15f
        val maxSize = 35f
        val growthFactor = 2f
        return (minSize + connectionCount * growthFactor).coerceIn(minSize, maxSize)
    }

    companion object {
        /**
         * 从链接ID创建GraphNode
         * @param id 链接ID(如"日记:2024-01-15")
         * @param label 显示标签(可选,默认从ID提取)
         */
        fun fromLinkId(id: String, label: String? = null): GraphNode {
            val type = NodeType.fromId(id)
            val displayLabel = label ?: id.substringAfter(":")
            return GraphNode(
                id = id,
                label = displayLabel,
                type = type,
                color = type.color
            )
        }
    }
}

/**
 * 节点类型枚举
 *
 * 定义图谱节点的类型,每种类型对应不同的颜色。
 */
enum class NodeType(val color: Int, val displayName: String) {
    DIARY(0xFF2196F3.toInt(), "日记"),      // 蓝色
    TOPIC(0xFF4CAF50.toInt(), "主题"),      // 绿色
    PERSON(0xFFFF9800.toInt(), "人物"),     // 橙色
    LOCATION(0xFF9C27B0.toInt(), "地点"),   // 紫色
    EVENT(0xFFF44336.toInt(), "事件");      // 红色

    companion object {
        /**
         * 从完整ID解析NodeType
         * @param id 完整ID(如"日记:2024-01-15")
         * @return 对应的NodeType
         */
        fun fromId(id: String): NodeType {
            val prefix = id.substringBefore(":", "日记")
            return when (prefix) {
                "日记" -> DIARY
                "主题" -> TOPIC
                "人物" -> PERSON
                "地点" -> LOCATION
                "事件" -> EVENT
                else -> DIARY
            }
        }
    }
}