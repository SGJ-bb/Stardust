package com.aicompanion.graph

/**
 * 图谱数据模型
 *
 * 包含节点列表、连线列表和可选的元数据。
 * 用于传递完整的图谱数据给渲染层。
 */
data class GraphData(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val metadata: GraphMetadata? = null
) {
    /**
     * 获取指定节点的所有连接
     * @param nodeId 节点ID
     * @return 连接到该节点的所有连线列表
     */
    fun getNodeEdges(nodeId: String): List<GraphEdge> {
        return edges.filter { it.connectsNode(nodeId) }
    }

    /**
     * 获取指定节点的连接数量
     * @param nodeId 节点ID
     * @return 连接数量
     */
    fun getNodeConnectionCount(nodeId: String): Int {
        return getNodeEdges(nodeId).size
    }

    /**
     * 获取指定节点的所有邻居节点ID
     * @param nodeId 节点ID
     * @return 邻居节点ID列表
     */
    fun getNeighbors(nodeId: String): List<String> {
        return getNodeEdges(nodeId).mapNotNull { it.getOtherEnd(nodeId) }
    }

    /**
     * 获取中心节点
     * @return 中心节点,如果没有则返回null
     */
    fun getCenterNode(): GraphNode? {
        return nodes.find { it.isCenter }
    }

    /**
     * 过滤节点类型
     * @param types 允许的节点类型列表
     * @return 过滤后的图谱数据
     */
    fun filterByNodeTypes(types: List<NodeType>): GraphData {
        val filteredNodes = nodes.filter { it.type in types }
        val filteredNodeIds = filteredNodes.map { it.id }.toSet()
        val filteredEdges = edges.filter {
            it.source in filteredNodeIds && it.target in filteredNodeIds
        }
        return GraphData(filteredNodes, filteredEdges, metadata)
    }

    /**
     * 搜索匹配节点
     * @param query 搜索关键词
     * @return 高亮匹配节点的图谱数据
     */
    fun searchHighlight(query: String): GraphData {
        if (query.isBlank()) return this

        val highlightedNodes = nodes.map { node ->
            val isMatch = node.label.contains(query, ignoreCase = true)
            node.copy(
                isHighlighted = isMatch,
                alpha = if (isMatch) 1.0f else 0.3f  // 匹配节点不透明,其他节点淡化
            )
        }

        return GraphData(highlightedNodes, edges, metadata)
    }

    /**
     * 获取图谱统计信息
     */
    fun getStats(): GraphStats {
        return GraphStats(
            nodeCount = nodes.size,
            edgeCount = edges.size,
            nodeTypeDistribution = nodes.groupingBy { it.type }.eachCount(),
            avgConnections = if (nodes.isNotEmpty()) edges.size * 2f / nodes.size else 0f
        )
    }

    companion object {
        /**
         * 创建空图谱
         */
        fun empty(): GraphData = GraphData(emptyList(), emptyList(), null)
    }
}

/**
 * 图谱元数据
 *
 * 包含图谱的附加信息,如时间范围、数据来源等。
 */
data class GraphMetadata(
    val centerNodeId: String? = null,     // 中心节点ID
    val depth: Int = 1,                   // 图谱深度
    val timeRangeStart: Long? = null,     // 时间范围起始(时间戳)
    val timeRangeEnd: Long? = null,       // 时间范围结束(时间戳)
    val generatedAt: Long = System.currentTimeMillis()  // 生成时间
)

/**
 * 图谱统计信息
 */
data class GraphStats(
    val nodeCount: Int,                             // 节点数量
    val edgeCount: Int,                             // 连线数量
    val nodeTypeDistribution: Map<NodeType, Int>,   // 节点类型分布
    val avgConnections: Float                       // 平均连接数
)