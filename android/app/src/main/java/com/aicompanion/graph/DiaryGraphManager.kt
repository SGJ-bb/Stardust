package com.aicompanion.graph

import android.content.Context
import com.aicompanion.diary.DiaryEntry
import com.aicompanion.diary.DiaryManager
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 日记关系图谱管理器
 *
 * 负责构建、维护和查询日记之间的链接关系。
 * 提供双向链接索引、本地图谱生成、多维过滤等核心功能。
 *
 * 核心职责:
 * 1. 链接索引构建和持久化
 * 2. 双向链接查询(正向链接+反向链接)
 * 3. 本地图谱数据生成(指定深度)
 * 4. 多维过滤(时间/类型/搜索)
 *
 * 线程安全: 使用ReentrantReadWriteLock保护所有读写操作
 */
class DiaryGraphManager(
    private val context: Context,
    private val personaId: String = "default"
) {
    companion object {
        private const val TAG = "DiaryGraphManager"
        private const val INDEX_FILE_NAME = "diary_graph_index.json"
        private const val MAX_NODES_DEPTH_1 = 30
        private const val MAX_NODES_DEPTH_2 = 80
        private const val MAX_NODES_DEPTH_3 = 150
    }

    // 链接索引: 源ID → (目标ID → 连接信息)
    private val linkIndex = mutableMapOf<String, MutableList<LinkRelation>>()

    // 反向索引: 目标ID → (源ID列表)
    private val backLinkIndex = mutableMapOf<String, MutableList<String>>()

    // 读写锁保护并发访问
    private val lock = ReentrantReadWriteLock()

    // 索引文件
    private val indexFile by lazy {
        File(context.filesDir, "diary_graph/${personaId}_$INDEX_FILE_NAME")
    }

    /**
     * 链接关系信息
     */
    data class LinkRelation(
        val targetId: String,       // 目标节点ID
        val weight: Float = 1.0f,   // 连接权重(出现次数)
        val firstSeen: Long = System.currentTimeMillis(),  // 首次出现时间
        val lastSeen: Long = System.currentTimeMillis()    // 最后出现时间
    )

    /**
     * 初始化: 加载持久化的索引
     */
    fun initialize() {
        lock.write {
            loadIndex()
        }
        AppLogger.i(TAG, "DiaryGraphManager初始化完成, 索引大小: ${linkIndex.size}")
    }

    /**
     * 从日记列表构建完整索引
     * 应在日记数据变更时调用
     *
     * @param diaries 所有日记列表
     */
    suspend fun buildIndexFromDiaries(diaries: List<DiaryEntry>) = withContext(Dispatchers.IO) {
        lock.write {
            // 清空旧索引
            linkIndex.clear()
            backLinkIndex.clear()

            // 遍历所有日记,提取链接
            for (diary in diaries) {
                val sourceId = "${LinkType.DIARY.prefix}:${diary.date}"
                val fullContent = "${diary.title}\n${diary.content}"
                val links = LinkExtractor.extractLinks(fullContent, diary.date)

                for (link in links) {
                    val targetId = link.toLinkId()
                    addLinkInternal(sourceId, targetId, diary.createdAt)
                }
            }

            // 持久化索引
            saveIndex()

            AppLogger.i(TAG, "索引构建完成: ${linkIndex.size}个源, ${linkIndex.values.sumOf { it.size }}条链接")
        }
    }

    /**
     * 更新单个日记的链接索引(增量更新)
     *
     * @param diaryDate 日记日期
     * @param oldContent 旧内容(可选,用于计算差异)
     * @param newContent 新内容
     */
    fun updateDiaryLinks(diaryDate: String, oldContent: String?, newContent: String) {
        val sourceId = "${LinkType.DIARY.prefix}:$diaryDate"

        lock.write {
            // 移除旧链接
            if (oldContent != null) {
                val oldLinks = LinkExtractor.extractLinks(oldContent, diaryDate)
                for (link in oldLinks) {
                    removeLinkInternal(sourceId, link.toLinkId())
                }
            }

            // 添加新链接
            val newLinks = LinkExtractor.extractLinks(newContent, diaryDate)
            for (link in newLinks) {
                addLinkInternal(sourceId, link.toLinkId(), System.currentTimeMillis())
            }

            // 持久化
            saveIndex()
        }

        AppLogger.d(TAG, "日记 $diaryDate 链接更新完成")
    }

    /**
     * 删除日记的链接索引
     *
     * @param diaryDate 日记日期
     */
    fun removeDiaryLinks(diaryDate: String) {
        val sourceId = "${LinkType.DIARY.prefix}:$diaryDate"

        lock.write {
            val targets = linkIndex.remove(sourceId)
            if (targets != null) {
                for (rel in targets) {
                    backLinkIndex[rel.targetId]?.remove(sourceId)
                    if (backLinkIndex[rel.targetId]?.isEmpty() == true) {
                        backLinkIndex.remove(rel.targetId)
                    }
                }
            }
            saveIndex()
        }

        AppLogger.d(TAG, "日记 $diaryDate 链接已删除")
    }

    /**
     * 获取正向链接(当前日记链接了哪些笔记)
     *
     * @param diaryDate 日记日期
     * @return 正向链接列表
     */
    fun getForwardLinks(diaryDate: String): List<LinkRelation> {
        val sourceId = "${LinkType.DIARY.prefix}:$diaryDate"
        return lock.read {
            linkIndex[sourceId]?.toList() ?: emptyList()
        }
    }

    /**
     * 获取反向链接(哪些日记链接了当前笔记)
     *
     * @param nodeId 节点ID(可以是日记/主题/人物等)
     * @return 反向链接的源ID列表
     */
    fun getBackLinks(nodeId: String): List<String> {
        return lock.read {
            backLinkIndex[nodeId]?.toList() ?: emptyList()
        }
    }

    /**
     * 生成本地图谱数据
     *
     * @param centerNodeId 中心节点ID
     * @param depth 图谱深度(1=直接连接, 2=两层, 3=三层)
     * @return 图谱数据(节点+连线)
     */
    fun getLocalGraph(centerNodeId: String, depth: Int = 1): GraphData {
        return lock.read {
            val nodes = mutableListOf<GraphNode>()
            val edges = mutableListOf<GraphEdge>()
            val visited = mutableSetOf<String>()

            // 添加中心节点
            val centerNode = GraphNode.fromLinkId(centerNodeId).apply {
                isCenter = true
                pos = Vector2D(0f, 0f)  // 中心位置
            }
            nodes.add(centerNode)
            visited.add(centerNodeId)

            // BFS遍历构建图谱
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.addLast(Pair(centerNodeId, 0))

            val maxNodes = when (depth) {
                1 -> MAX_NODES_DEPTH_1
                2 -> MAX_NODES_DEPTH_2
                else -> MAX_NODES_DEPTH_3
            }

            while (queue.isNotEmpty() && nodes.size < maxNodes) {
                val (currentId, currentDepth) = queue.removeFirst()

                if (currentDepth >= depth) continue

                // 获取当前节点的所有连接
                val forwardLinks = linkIndex[currentId] ?: emptyList()
                val backLinks = backLinkIndex[currentId] ?: emptyList()

                // 合并所有连接
                val allNeighbors = mutableSetOf<String>()
                for (rel in forwardLinks) {
                    allNeighbors.add(rel.targetId)
                }
                for (backId in backLinks) {
                    allNeighbors.add(backId)
                }

                // 添加邻居节点
                for (neighborId in allNeighbors) {
                    if (neighborId !in visited && nodes.size < maxNodes) {
                        val neighborNode = GraphNode.fromLinkId(neighborId)
                        nodes.add(neighborNode)
                        visited.add(neighborId)

                        // 添加连线
                        val weight = forwardLinks.find { it.targetId == neighborId }?.weight ?: 1.0f
                        edges.add(GraphEdge.createBidirectional(currentId, neighborId, weight))

                        // 加入队列继续BFS
                        queue.addLast(Pair(neighborId, currentDepth + 1))
                    } else if (neighborId in visited) {
                        // 已访问过的节点,只添加边
                        val edge = GraphEdge.createBidirectional(currentId, neighborId)
                        if (edges.none { it.source == edge.source && it.target == edge.target }) {
                            edges.add(edge)
                        }
                    }
                }
            }

            // 根据连接数量调整节点大小
            for (node in nodes) {
                val connCount = edges.count { it.connectsNode(node.id) }
                node.size = node.getAdjustedSize(connCount)
            }

            val metadata = GraphMetadata(
                centerNodeId = centerNodeId,
                depth = depth
            )

            AppLogger.d(TAG, "本地图谱生成: ${nodes.size}节点, ${edges.size}连线, 深度=$depth")

            GraphData(nodes, edges, metadata)
        }
    }

    /**
     * 生成全局图谱数据
     *
     * @param maxNodes 最大节点数(避免过载)
     * @return 图谱数据
     */
    fun getGlobalGraph(maxNodes: Int = 100): GraphData {
        return lock.read {
            val nodes = mutableListOf<GraphNode>()
            val edges = mutableListOf<GraphEdge>()
            val nodeIds = mutableSetOf<String>()

            // 收集所有节点ID
            for ((sourceId, targets) in linkIndex) {
                if (sourceId !in nodeIds && nodes.size < maxNodes) {
                    nodes.add(GraphNode.fromLinkId(sourceId))
                    nodeIds.add(sourceId)
                }
                for (rel in targets) {
                    if (rel.targetId !in nodeIds && nodes.size < maxNodes) {
                        nodes.add(GraphNode.fromLinkId(rel.targetId))
                        nodeIds.add(rel.targetId)
                    }
                }
            }

            // 添加所有边
            for ((sourceId, targets) in linkIndex) {
                for (rel in targets) {
                    if (rel.targetId in nodeIds) {
                        edges.add(GraphEdge.createBidirectional(sourceId, rel.targetId, rel.weight))
                    }
                }
            }

            // 去重边
            val uniqueEdges = edges.distinctBy { "${it.source}→${it.target}" }

            // 调整节点大小
            for (node in nodes) {
                val connCount = uniqueEdges.count { it.connectsNode(node.id) }
                node.size = node.getAdjustedSize(connCount)
            }

            AppLogger.d(TAG, "全局图谱生成: ${nodes.size}节点, ${uniqueEdges.size}连线")

            GraphData(nodes, uniqueEdges)
        }
    }

    /**
     * 获取所有主题节点(用于过滤)
     */
    fun getAllTopics(): List<String> {
        return lock.read {
            linkIndex.values.flatten()
                .map { it.targetId }
                .filter { it.startsWith("${LinkType.TOPIC.prefix}:") }
                .distinct()
        }
    }

    /**
     * 获取所有人物节点
     */
    fun getAllPersons(): List<String> {
        return lock.read {
            linkIndex.values.flatten()
                .map { it.targetId }
                .filter { it.startsWith("${LinkType.PERSON.prefix}:") }
                .distinct()
        }
    }

    /**
     * 获取所有地点节点
     */
    fun getAllLocations(): List<String> {
        return lock.read {
            linkIndex.values.flatten()
                .map { it.targetId }
                .filter { it.startsWith("${LinkType.LOCATION.prefix}:") }
                .distinct()
        }
    }

    /**
     * 获取图谱统计信息
     */
    fun getStats(): GraphStats {
        return lock.read {
            val allNodes = mutableSetOf<String>()
            for ((sourceId, targets) in linkIndex) {
                allNodes.add(sourceId)
                for (rel in targets) {
                    allNodes.add(rel.targetId)
                }
            }

            val nodeTypeDist = mutableMapOf<NodeType, Int>()
            for (id in allNodes) {
                val type = NodeType.fromId(id)
                nodeTypeDist[type] = (nodeTypeDist[type] ?: 0) + 1
            }

            GraphStats(
                nodeCount = allNodes.size,
                edgeCount = linkIndex.values.sumOf { it.size },
                nodeTypeDistribution = nodeTypeDist,
                avgConnections = if (allNodes.isNotEmpty()) {
                    linkIndex.values.sumOf { it.size }.toFloat() / allNodes.size
                } else 0f
            )
        }
    }

    // ─── 内部方法 ──────────────────────────────────────

    private fun addLinkInternal(sourceId: String, targetId: String, timestamp: Long) {
        // 添加正向链接
        val targets = linkIndex.getOrPut(sourceId) { mutableListOf() }
        val existing = targets.find { it.targetId == targetId }
        if (existing != null) {
            // 更新权重和最后出现时间
            val idx = targets.indexOf(existing)
            targets[idx] = existing.copy(
                weight = existing.weight + 1.0f,
                lastSeen = timestamp
            )
        } else {
            targets.add(LinkRelation(
                targetId = targetId,
                weight = 1.0f,
                firstSeen = timestamp,
                lastSeen = timestamp
            ))
        }

        // 添加反向链接
        val backLinks = backLinkIndex.getOrPut(targetId) { mutableListOf() }
        if (sourceId !in backLinks) {
            backLinks.add(sourceId)
        }
    }

    private fun removeLinkInternal(sourceId: String, targetId: String) {
        linkIndex[sourceId]?.removeAll { it.targetId == targetId }
        if (linkIndex[sourceId]?.isEmpty() == true) {
            linkIndex.remove(sourceId)
        }

        backLinkIndex[targetId]?.remove(sourceId)
        if (backLinkIndex[targetId]?.isEmpty() == true) {
            backLinkIndex.remove(targetId)
        }
    }

    // ─── 持久化 ──────────────────────────────────────

    private fun saveIndex() {
        try {
            indexFile.parentFile?.mkdirs()
            val json = JSONObject().apply {
                put("version", 1)
                put("personaId", personaId)
                put("updatedAt", System.currentTimeMillis())

                val linksArray = JSONArray()
                for ((sourceId, targets) in linkIndex) {
                    val linkObj = JSONObject().apply {
                        put("sourceId", sourceId)
                        val targetsArray = JSONArray()
                        for (rel in targets) {
                            val relObj = JSONObject().apply {
                                put("targetId", rel.targetId)
                                put("weight", rel.weight)
                                put("firstSeen", rel.firstSeen)
                                put("lastSeen", rel.lastSeen)
                            }
                            targetsArray.put(relObj)
                        }
                        put("targets", targetsArray)
                    }
                    linksArray.put(linkObj)
                }
                put("links", linksArray)
            }

            indexFile.writeText(json.toString())
            AppLogger.d(TAG, "索引已保存到 ${indexFile.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Graph-Index] 保存日记图谱索引失败: ${e.javaClass.simpleName}: ${e.message} | 链接源数=${linkIndex.size} 反向链接源数=${backLinkIndex.size}")
        }
    }

    private fun loadIndex() {
        if (!indexFile.exists()) {
            AppLogger.i(TAG, "索引文件不存在,将使用空索引")
            return
        }

        try {
            val json = JSONObject(indexFile.readText())
            val linksArray = json.optJSONArray("links") ?: return

            for (i in 0 until linksArray.length()) {
                val linkObj = linksArray.getJSONObject(i)
                val sourceId = linkObj.getString("sourceId")
                val targetsArray = linkObj.getJSONArray("targets")

                val targets = mutableListOf<LinkRelation>()
                for (j in 0 until targetsArray.length()) {
                    val relObj = targetsArray.getJSONObject(j)
                    targets.add(LinkRelation(
                        targetId = relObj.getString("targetId"),
                        weight = relObj.optDouble("weight", 1.0).toFloat(),
                        firstSeen = relObj.optLong("firstSeen", System.currentTimeMillis()),
                        lastSeen = relObj.optLong("lastSeen", System.currentTimeMillis())
                    ))
                }

                linkIndex[sourceId] = targets

                // 构建反向索引
                for (rel in targets) {
                    val backLinks = backLinkIndex.getOrPut(rel.targetId) { mutableListOf() }
                    if (sourceId !in backLinks) {
                        backLinks.add(sourceId)
                    }
                }
            }

            AppLogger.i(TAG, "索引加载完成: ${linkIndex.size}个源")
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Graph-Index] 加载日记图谱索引失败: ${e.javaClass.simpleName}: ${e.message} | 文件=${indexFile.absolutePath}")
            linkIndex.clear()
            backLinkIndex.clear()
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        lock.write {
            saveIndex()
            linkIndex.clear()
            backLinkIndex.clear()
        }
    }
}