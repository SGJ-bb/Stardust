package com.aicompanion.virtualworld

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

data class GraphNode(
    val name: String,
    val x: Float,
    val y: Float,
    val color: Int
)

data class GraphEdge(
    val from: String,
    val to: String,
    val label: String,
    val color: Int
)

class RelationshipGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var nodes = listOf<GraphNode>()
    private var edges = listOf<GraphEdge>()

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = Color.parseColor("#AAAACC")
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D0D2B")
        style = Paint.Style.FILL
    }

    private val nodeColors = intArrayOf(
        Color.parseColor("#667eea"),
        Color.parseColor("#ff6b9d"),
        Color.parseColor("#4fc3f7"),
        Color.parseColor("#FFB347"),
        Color.parseColor("#81C784"),
        Color.parseColor("#CE93D8")
    )

    fun setRelationships(relationsText: String, characterNames: List<String>) {
        val parsedNodes = mutableListOf<GraphNode>()
        val parsedEdges = mutableListOf<GraphEdge>()
        val centerX = if (width > 0) width / 2f else 540f
        val centerY = if (height > 0) height / 2f else 400f
        val radius = Math.min(centerX, centerY) * 0.6f

        characterNames.forEachIndexed { i, name ->
            val angle = (2 * Math.PI * i / characterNames.size.coerceAtLeast(1)) - Math.PI / 2
            val x = centerX + (radius * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle)).toFloat()
            parsedNodes.add(GraphNode(name, x, y, nodeColors[i % nodeColors.size]))
        }

        val lines = relationsText.split("\n", "；", ";").map { it.trim() }.filter { it.isNotBlank() }
        lines.forEach { line ->
            val separators = listOf("和", "与", "跟", "&")
            var from = ""
            var to = ""
            var label = ""
            for (sep in separators) {
                if (line.contains(sep)) {
                    val parts = line.split(sep, limit = 2)
                    if (parts.size == 2) {
                        from = parts[0].trim()
                        val relParts = parts[1].split("：", ":", "是", "的")
                        if (relParts.size >= 2) {
                            to = relParts[0].trim()
                            label = relParts.drop(1).joinToString("").trim()
                        } else {
                            to = relParts[0].trim()
                            label = parts[1].replaceFirst(relParts[0], "").trim()
                        }
                        break
                    }
                }
            }
            if (from.isNotBlank() && to.isNotBlank()) {
                val edgeColor = nodeColors[(characterNames.indexOf(from).coerceAtLeast(0) + 1) % nodeColors.size]
                parsedEdges.add(GraphEdge(from, to, label.ifBlank { "相关" }, edgeColor))
            }
        }

        nodes = parsedNodes
        edges = parsedEdges
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        edges.forEach { edge ->
            val fromNode = nodes.find { it.name == edge.from } ?: return@forEach
            val toNode = nodes.find { it.name == edge.to } ?: return@forEach
            edgePaint.color = edge.color
            edgePaint.alpha = 120
            canvas.drawLine(fromNode.x, fromNode.y, toNode.x, toNode.y, edgePaint)
            val midX = (fromNode.x + toNode.x) / 2
            val midY = (fromNode.y + toNode.y) / 2
            if (edge.label.isNotBlank()) {
                labelPaint.color = edge.color
                labelPaint.alpha = 180
                canvas.drawText(edge.label, midX - labelPaint.measureText(edge.label) / 2, midY - 10f, labelPaint)
            }
        }

        nodes.forEach { node ->
            circlePaint.color = node.color
            circlePaint.alpha = 60
            canvas.drawCircle(node.x, node.y, 50f, circlePaint)
            circlePaint.alpha = 180
            canvas.drawCircle(node.x, node.y, 36f, circlePaint)
            nodePaint.color = Color.WHITE
            val textWidth = nodePaint.measureText(node.name)
            canvas.drawText(node.name, node.x - textWidth / 2, node.y + 12f, nodePaint)
        }
    }
}
