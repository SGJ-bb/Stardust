package com.aicompanion.graph

import com.aicompanion.util.AppLogger

/**
 * 力导向布局算法
 *
 * 使用Verlet积分方法实现稳定的力导向布局。
 * 参考Obsidian的物理引擎设计,适配Android移动端性能。
 *
 * 三种核心力:
 * 1. 中心引力 - 将非中心节点拉向中心
 * 2. 节点排斥力 - 防止节点重叠(库仑力)
 * 3. 连接吸引力 - 连接的节点相互靠近(胡克弹力)
 */
class DiaryGraphLayout(
    private val width: Float,
    private val height: Float
) {
    companion object {
        private const val TAG = "DiaryGraphLayout"

        // 默认物理参数(参考Obsidian推荐配置)
        const val DEFAULT_CENTER_FORCE = 8.0f
        const val DEFAULT_REPEL_FORCE = 50.0f
        const val DEFAULT_LINK_FORCE = 5.0f
        const val DEFAULT_LINK_DISTANCE = 100.0f

        // 收敛参数
        const val DAMPING = 0.85f           // 阻尼系数
        const val MAX_VELOCITY = 30.0f      // 最大速度限制
        const val ALPHA_DECAY = 0.0228f     // 温度衰减率
        const val ALPHA_MIN = 0.001f        // 最小温度(停止阈值)
        const val ALPHA_TARGET = 0.0f       // 目标温度
        const val VELOCITY_DECAY = 0.6f     // 速度衰减
    }

    // 可调参数
    var centerForce: Float = DEFAULT_CENTER_FORCE
    var repelForce: Float = DEFAULT_REPEL_FORCE
    var linkForce: Float = DEFAULT_LINK_FORCE
    var linkDistance: Float = DEFAULT_LINK_DISTANCE

    // 当前温度(模拟退火)
    private var alpha: Float = 1.0f

    // 是否正在运行
    private var isRunning: Boolean = false

    /**
     * 初始化节点位置
     * 将节点随机分布在中心周围
     *
     * @param nodes 节点列表
     */
    fun initializeLayout(nodes: List<GraphNode>) {
        val centerX = width / 2f
        val centerY = height / 2f

        for (node in nodes) {
            if (node.isCenter) {
                // 中心节点放在画布中心
                node.pos = Vector2D(centerX, centerY)
                node.velocity = Vector2D.ZERO
            } else {
                // 其他节点随机分布在中心周围
                val angle = Vector2D.random()
                val radius = 80f + (Math.random() * 120f).toFloat()
                node.pos = Vector2D(
                    centerX + angle.x * radius,
                    centerY + angle.y * radius
                )
                node.velocity = Vector2D.ZERO
            }
        }

        // 重置温度
        alpha = 1.0f
        isRunning = true
    }

    /**
     * 执行一次物理模拟步进
     *
     * @param graph 图谱数据
     * @return true表示仍在模拟(未收敛),false表示已收敛
     */
    fun step(graph: GraphData): Boolean {
        if (!isRunning || alpha < ALPHA_MIN) {
            isRunning = false
            return false
        }

        val nodes = graph.nodes
        val edges = graph.edges
        val centerX = width / 2f
        val centerY = height / 2f

        // 为每个节点计算合力
        for (node in nodes) {
            if (node.isCenter) {
                // 中心节点固定不动
                node.velocity = Vector2D.ZERO
                continue
            }

            var force = Vector2D.ZERO

            // 1. 中心引力(拉向画布中心)
            val toCenter = Vector2D(centerX, centerY) - node.pos
            val centerDist = toCenter.length()
            if (centerDist > 0) {
                force += toCenter.normalize() * centerForce * alpha
            }

            // 2. 节点排斥力(库仑力)
            for (other in nodes) {
                if (other.id == node.id) continue

                val diff = node.pos - other.pos
                var dist = diff.length()

                // 避免除零
                if (dist < 1f) {
                    dist = 1f
                    // 随机推开
                    val pushDir = Vector2D.random()
                    force += pushDir * repelForce * alpha
                } else {
                    // 库仑力: F = k / r²
                    val repel = repelForce * alpha / (dist * dist)
                    force += diff.normalize() * repel
                }
            }

            // 3. 连接吸引力(胡克弹力)
            for (edge in edges) {
                if (!edge.connectsNode(node.id)) continue

                val neighborId = edge.getOtherEnd(node.id) ?: continue
                val neighbor = nodes.find { it.id == neighborId } ?: continue

                val diff = neighbor.pos - node.pos
                val dist = diff.length()

                if (dist > 0) {
                    // 胡克弹力: F = k * (d - d0)
                    val springForce = linkForce * alpha * (dist - linkDistance)
                    force += diff.normalize() * springForce
                }
            }

            // 更新速度(带阻尼)
            node.velocity = (node.velocity + force) * VELOCITY_DECAY
            node.velocity = node.velocity.limit(MAX_VELOCITY)
        }

        // 更新位置
        for (node in nodes) {
            if (node.isCenter) continue

            node.pos += node.velocity

            // 边界约束(防止节点飞出画布)
            val margin = node.size
            node.pos = Vector2D(
                node.pos.x.coerceIn(margin, width - margin),
                node.pos.y.coerceIn(margin, height - margin)
            )
        }

        // 温度衰减
        alpha += (ALPHA_TARGET - alpha) * ALPHA_DECAY

        return alpha > ALPHA_MIN
    }

    /**
     * 执行多次步进直到收敛或达到最大迭代次数
     *
     * @param graph 图谱数据
     * @param maxIterations 最大迭代次数
     */
    fun runUntilConverged(graph: GraphData, maxIterations: Int = 300) {
        var iterations = 0
        while (step(graph) && iterations < maxIterations) {
            iterations++
        }
        AppLogger.d(TAG, "布局收敛: ${iterations}次迭代, alpha=${alpha}")
    }

    /**
     * 重新加热(拖拽节点后调用)
     * 提高温度让布局重新流动
     */
    fun reheat(alpha: Float = 0.5f) {
        this.alpha = this.alpha.coerceAtLeast(alpha)
        isRunning = true
    }

    /**
     * 固定节点位置(拖拽时调用)
     *
     * @param nodeId 节点ID
     * @param newPos 新位置
     */
    fun pinNode(graph: GraphData, nodeId: String, newPos: Vector2D) {
        val node = graph.nodes.find { it.id == nodeId } ?: return
        node.pos = newPos
        node.velocity = Vector2D.ZERO
        reheat(0.3f)  // 重新加热让其他节点调整
    }

    /**
     * 释放固定节点
     */
    fun unpinNode(graph: GraphData, nodeId: String) {
        val node = graph.nodes.find { it.id == nodeId } ?: return
        node.velocity = Vector2D.ZERO
        reheat(0.2f)
    }

    /**
     * 检查布局是否已收敛
     */
    fun isConverged(): Boolean = alpha < ALPHA_MIN

    /**
     * 获取当前温度
     */
    fun getAlpha(): Float = alpha

    /**
     * 停止布局计算
     */
    fun stop() {
        isRunning = false
    }

    /**
     * 应用预设参数
     */
    fun applyPreset(preset: LayoutPreset) {
        centerForce = preset.centerForce
        repelForce = preset.repelForce
        linkForce = preset.linkForce
        linkDistance = preset.linkDistance
        alpha = 1.0f
        isRunning = true
    }
}

/**
 * 布局预设参数
 *
 * 针对不同节点规模提供推荐参数配置
 */
data class LayoutPreset(
    val name: String,
    val centerForce: Float,
    val repelForce: Float,
    val linkForce: Float,
    val linkDistance: Float,
    val description: String
) {
    companion object {
        /** 小规模图谱(<30节点): 紧凑布局 */
        val SMALL = LayoutPreset(
            name = "small",
            centerForce = 8.0f,
            repelForce = 50.0f,
            linkForce = 5.0f,
            linkDistance = 100.0f,
            description = "紧凑布局(适合<30节点)"
        )

        /** 中等规模图谱(30-80节点): 平衡布局 */
        val MEDIUM = LayoutPreset(
            name = "medium",
            centerForce = 5.0f,
            repelForce = 80.0f,
            linkForce = 3.0f,
            linkDistance = 120.0f,
            description = "平衡布局(适合30-80节点)"
        )

        /** 大规模图谱(>80节点): 稀疏布局 */
        val LARGE = LayoutPreset(
            name = "large",
            centerForce = 3.0f,
            repelForce = 120.0f,
            linkForce = 2.0f,
            linkDistance = 150.0f,
            description = "稀疏布局(适合>80节点)"
        )

        /**
         * 根据节点数量自动选择预设
         */
        fun autoSelect(nodeCount: Int): LayoutPreset {
            return when {
                nodeCount <= 30 -> SMALL
                nodeCount <= 80 -> MEDIUM
                else -> LARGE
            }
        }
    }
}