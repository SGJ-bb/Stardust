package com.aicompanion.graph

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.aicompanion.util.AppLogger
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 日记关系图谱视图
 *
 * 自定义View,使用Canvas渲染图谱节点和连线。
 * 支持缩放、平移、点击、悬停、拖拽等交互。
 *
 * 性能优化:
 * - 视口裁剪(只渲染可见区域)
 * - 批量绘制(减少Canvas状态切换)
 * - requestAnimationFrame合并(避免重复重绘)
 */
class DiaryGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DiaryGraphView"
        private const val TOUCH_SLOP = 8f  // 点击判定阈值(dp)
        private const val MAX_SCALE = 3.0f
        private const val MIN_SCALE = 0.3f
    }

    // 图谱数据
    private var graphData: GraphData = GraphData.empty()

    // 布局算法
    private var layout: DiaryGraphLayout? = null

    // 视口变换(缩放和平移)
    private var scale = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f

    // 交互状态
    private var draggedNode: GraphNode? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDragging = false
    private var isPanning = false

    // 绘制对象
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val nodeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFBDBDBD.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }
    private val highlightedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFF9800.toInt()
    }

    // 回调接口
    var onNodeClickListener: ((GraphNode) -> Unit)? = null
    var onNodeLongClickListener: ((GraphNode) -> Unit)? = null
    var onNodeHoverListener: ((GraphNode?) -> Unit)? = null

    // 悬停的节点
    private var hoveredNode: GraphNode? = null

    // 长按检测
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var potentialLongPressNode: GraphNode? = null
    private var longPressTriggered = false

    /**
     * 设置图谱数据
     */
    fun setGraphData(data: GraphData) {
        graphData = data
        if (data.nodes.isNotEmpty()) {
            // 初始化布局
            layout = DiaryGraphLayout(width.toFloat(), height.toFloat()).apply {
                val preset = LayoutPreset.autoSelect(data.nodes.size)
                applyPreset(preset)
                initializeLayout(data.nodes)
            }
            // 预计算布局
            layout?.runUntilConverged(data)
        }
        invalidate()
    }

    /**
     * 更新布局(单步)
     */
    fun updateLayout() {
        if (layout != null && !layout!!.isConverged()) {
            layout?.step(graphData)
            invalidate()
        }
    }

    /**
     * 重置视图(缩放和偏移)
     */
    fun resetView() {
        scale = 1.0f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (graphData.nodes.isEmpty()) return

        // 应用视口变换
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(offsetX, offsetY)

        // 先绘制连线
        drawEdges(canvas)

        // 再绘制节点
        drawNodes(canvas)

        canvas.restore()
    }

    /**
     * 绘制所有连线
     */
    private fun drawEdges(canvas: Canvas) {
        val viewport = RectF(
            -offsetX / scale,
            -offsetY / scale,
            (width / scale) - (offsetX / scale),
            (height / scale) - (offsetY / scale)
        )

        // 构建节点ID到节点的映射(避免O(n)查找)
        val nodeMap = graphData.nodes.associateBy { it.id }

        for (edge in graphData.edges) {
            val sourceNode = nodeMap[edge.source] ?: continue
            val targetNode = nodeMap[edge.target] ?: continue

            // 视口裁剪:只绘制可见区域的连线
            if (!isEdgeVisible(sourceNode.pos, targetNode.pos, viewport)) continue

            // 高亮悬停节点的连线
            val isHighlighted = hoveredNode != null &&
                edge.connectsNode(hoveredNode!!.id)

            edgePaint.color = if (isHighlighted) {
                0xFFFF9800.toInt()
            } else {
                edge.color
            }
            edgePaint.alpha = if (isHighlighted) 255 else (180 * edge.weight.coerceIn(0.5f, 2f)).toInt().coerceIn(80, 255)
            edgePaint.strokeWidth = edge.getAdjustedThickness() * (if (isHighlighted) 1.5f else 1f)

            canvas.drawLine(
                sourceNode.pos.x, sourceNode.pos.y,
                targetNode.pos.x, targetNode.pos.y,
                edgePaint
            )
        }
    }

    /**
     * 绘制所有节点
     */
    private fun drawNodes(canvas: Canvas) {
        for (node in graphData.nodes) {
            // 视口裁剪
            if (!isNodeVisible(node)) continue

            // 设置透明度
            val alpha = (node.alpha * 255).toInt().coerceIn(0, 255)

            // 绘制节点圆形
            nodePaint.color = node.color
            nodePaint.alpha = alpha

            // 中心节点更大
            val radius = if (node.isCenter) node.size * 1.5f else node.size
            canvas.drawCircle(node.pos.x, node.pos.y, radius, nodePaint)

            // 绘制边框(悬停或中心节点)
            if (node.isCenter || node == hoveredNode || node.isHighlighted) {
                nodeBorderPaint.alpha = alpha
                nodeBorderPaint.strokeWidth = if (node.isCenter) 4f else 3f
                nodeBorderPaint.color = if (node.isHighlighted) 0xFFFF9800.toInt() else Color.WHITE
                canvas.drawCircle(node.pos.x, node.pos.y, radius, nodeBorderPaint)
            }

            // 绘制标签文字
            textPaint.alpha = alpha
            textPaint.textSize = if (node.isCenter) 32f else 24f
            canvas.drawText(
                node.label,
                node.pos.x,
                node.pos.y + radius + 20f,
                textPaint
            )
        }
    }

    /**
     * 检查节点是否在可见区域内
     */
    private fun isNodeVisible(node: GraphNode): Boolean {
        val margin = node.size + 50f
        return node.pos.x >= -margin &&
               node.pos.x <= width / scale + margin &&
               node.pos.y >= -margin &&
               node.pos.y <= height / scale + margin
    }

    /**
     * 检查连线是否在可见区域内
     */
    private fun isEdgeVisible(p1: Vector2D, p2: Vector2D, viewport: RectF): Boolean {
        // 简单判断:两端都在视口外则跳过
        val p1Visible = viewport.contains(p1.x, p1.y)
        val p2Visible = viewport.contains(p2.x, p2.y)
        return p1Visible || p2Visible
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                isPanning = false
                longPressTriggered = false

                // 检测点击的节点
                val touchedNode = findNodeAt(event.x, event.y)
                if (touchedNode != null) {
                    draggedNode = touchedNode
                    // 启动长按检测
                    potentialLongPressNode = touchedNode
                    longPressRunnable = Runnable {
                        if (potentialLongPressNode != null && !isDragging) {
                            longPressTriggered = true
                            onNodeLongClickListener?.invoke(potentialLongPressNode!!)
                            AppLogger.d(TAG, "节点长按: ${potentialLongPressNode!!.id}")
                        }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, 500)  // 500ms长按阈值
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY

                if (abs(event.x - touchStartX) > TOUCH_SLOP ||
                    abs(event.y - touchStartY) > TOUCH_SLOP) {
                    isDragging = true
                }

                if (draggedNode != null && isDragging) {
                    // 拖拽节点
                    val newPos = screenToWorld(event.x, event.y)
                    layout?.pinNode(graphData, draggedNode!!.id, newPos)
                    invalidate()
                } else if (isDragging) {
                    // 平移画布
                    isPanning = true
                    offsetX += dx / scale
                    offsetY += dy / scale
                    invalidate()
                }

                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_UP -> {
                // 取消长按检测
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                longPressRunnable = null
                potentialLongPressNode = null

                val moved = abs(event.x - touchStartX) > TOUCH_SLOP ||
                           abs(event.y - touchStartY) > TOUCH_SLOP

                if (!moved && !longPressTriggered) {
                    // 点击事件(非长按)
                    val clickedNode = findNodeAt(event.x, event.y)
                    if (clickedNode != null) {
                        onNodeClickListener?.invoke(clickedNode)
                        AppLogger.d(TAG, "节点点击: ${clickedNode.id}")
                    }
                }

                if (draggedNode != null) {
                    layout?.unpinNode(graphData, draggedNode!!.id)
                    draggedNode = null
                }

                isDragging = false
                isPanning = false
                longPressTriggered = false
            }

            MotionEvent.ACTION_CANCEL -> {
                // 取消长按检测
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                longPressRunnable = null
                potentialLongPressNode = null
                draggedNode = null
                isDragging = false
                isPanning = false
                longPressTriggered = false
            }
        }

        return true
    }

    /**
     * 查找点击位置对应的节点
     */
    private fun findNodeAt(screenX: Float, screenY: Float): GraphNode? {
        val worldPos = screenToWorld(screenX, screenY)

        // 从后往前遍历(后绘制的节点在上方)
        for (node in graphData.nodes.reversed()) {
            val radius = if (node.isCenter) node.size * 1.5f else node.size
            val dist = sqrt(
                (node.pos.x - worldPos.x) * (node.pos.x - worldPos.x) +
                (node.pos.y - worldPos.y) * (node.pos.y - worldPos.y)
            )
            if (dist <= radius + 10f) {  // 10f为点击容差
                return node
            }
        }
        return null
    }

    /**
     * 屏幕坐标转世界坐标
     */
    private fun screenToWorld(screenX: Float, screenY: Float): Vector2D {
        return Vector2D(
            (screenX / scale) - offsetX,
            (screenY / scale) - offsetY
        )
    }

    /**
     * 缩放(通过双指缩放手势)
     */
    fun zoom(factor: Float, focusX: Float, focusY: Float) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val scaleChange = newScale / scale

        // 保持焦点位置不变
        offsetX = focusX / scale - (focusX / scale - offsetX) * scaleChange
        offsetY = focusY / scale - (focusY / scale - offsetY) * scaleChange

        scale = newScale
        invalidate()
    }

    /**
     * 设置悬停节点
     */
    fun setHoveredNode(node: GraphNode?) {
        hoveredNode = node
        onNodeHoverListener?.invoke(node)
        invalidate()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        potentialLongPressNode = null
        layout?.stop()
        graphData = GraphData.empty()
    }
}