package com.aicompanion.graph

/**
 * 图谱连线数据模型
 *
 * 表示关系图谱中的一条连线,连接两个节点。
 * 包含权重、颜色、粗细等可视化属性。
 */
data class GraphEdge(
    val source: String,                   // 源节点ID
    val target: String,                   // 目标节点ID
    val weight: Float = 1.0f,             // 连接权重(可表示连接次数)
    var color: Int = 0xFFBDBDBD.toInt(),  // 连线颜色(默认灰色)
    var thickness: Float = 2.0f           // 连线粗细(可根据权重动态调整)
) {
    /**
     * 获取连接ID
     * 格式: source→target
     */
    fun getEdgeId(): String = "${source}→$target"

    /**
     * 检查连线是否连接指定节点
     * @param nodeId 节点ID
     * @return true表示连线连接了该节点
     */
    fun connectsNode(nodeId: String): Boolean {
        return source == nodeId || target == nodeId
    }

    /**
     * 获取连接的另一端节点
     * @param oneEnd 一端节点ID
     * @return 另一端节点ID,如果oneEnd不在连接中则返回null
     */
    fun getOtherEnd(oneEnd: String): String? {
        return when (oneEnd) {
            source -> target
            target -> source
            else -> null
        }
    }

    /**
     * 根据权重调整连线粗细
     * 粗细随权重增长,但有上限
     */
    fun getAdjustedThickness(): Float {
        val minThickness = 1f
        val maxThickness = 5f
        return (minThickness + weight * 0.5f).coerceIn(minThickness, maxThickness)
    }

    companion object {
        /**
         * 创建双向连线(实际上是两条单向连线)
         * Obsidian的双向链接在图谱中显示为一条连线
         */
        fun createBidirectional(node1: String, node2: String, weight: Float = 1.0f): GraphEdge {
            // 按字典序排序,确保node1→node2和node2→node1是同一条边
            val (smaller, larger) = if (node1 < node2) Pair(node1, node2) else Pair(node2, node1)
            return GraphEdge(smaller, larger, weight)
        }
    }
}