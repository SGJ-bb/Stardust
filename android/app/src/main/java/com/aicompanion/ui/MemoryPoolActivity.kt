package com.aicompanion.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aicompanion.memory.ContextManager
import com.aicompanion.memory.MemoryEntry

class MemoryPoolActivity : AppCompatActivity() {

    private lateinit var contextManager: ContextManager
    private lateinit var container: LinearLayout
    private lateinit var tvStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val personaId = intent.getStringExtra("persona_id")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default")
            ?: "default"

        contextManager = ContextManager(this, personaId)

        val scrollView = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 32)
            setBackgroundColor(0xFF0a0a1a.toInt())
        }

        tvStats = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFaabbdd.toInt())
            setPadding(0, 0, 0, 16)
        }
        container.addView(tvStats)

        refreshList()

        scrollView.addView(container)
        setContentView(scrollView)
    }

    private fun refreshList() {
        container.removeViews(1, container.childCount - 1)

        val entries = contextManager.memoryPool.getAll()
        tvStats.text = contextManager.getSessionStats()

        if (entries.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "记忆池为空\n\n开始聊天后，AI会自动提取并记录场景、剧情和关键信息\n每10轮对话会自动整理，不超过1000字"
                textSize = 14f
                setTextColor(0xFF667788.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 64, 0, 0)
                alpha = 0.7f
            }
            container.addView(emptyView)
            return
        }

        val grouped = entries.groupBy { it.category }
        val categoryOrder = listOf("场景", "剧情", "喜好", "习惯", "事实", "事件", "计划", "继承", "其他")

        for (cat in categoryOrder) {
            val group = grouped[cat] ?: continue
            if (group.isEmpty()) continue

            val catHeader = TextView(this).apply {
                text = "▸ $cat (${group.size}条)"
                textSize = 13f
                setTextColor(0xFFc4b5fd.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 8)
            }
            container.addView(catHeader)

            for (entry in group) {
                addEntryView(entry)
            }
        }

        val uncategorized = entries.filter { it.category !in categoryOrder }
        if (uncategorized.isNotEmpty()) {
            val otherHeader = TextView(this).apply {
                text = "▸ 其他 (${uncategorized.size}条)"
                textSize = 13f
                setTextColor(0xFFc4b5fd.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 8)
            }
            container.addView(otherHeader)
            for (entry in uncategorized) {
                addEntryView(entry)
            }
        }
    }

    private fun addEntryView(entry: MemoryEntry) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x22ffffff)
            setPadding(16, 12, 16, 12)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            layoutParams = params
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val categoryBadge = TextView(this).apply {
            text = entry.category
            textSize = 10f
            setTextColor(0xFF1a1a2e.toInt())
            setBackgroundColor(getCategoryColor(entry.category))
            setPadding(8, 2, 8, 2)
        }
        headerRow.addView(categoryBadge)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        headerRow.addView(spacer)

        val deleteBtn = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(0xFFff6666.toInt())
            setPadding(16, 0, 0, 0)
            setOnClickListener {
                contextManager.memoryPool.delete(entry.id)
                refreshList()
            }
        }
        headerRow.addView(deleteBtn)

        card.addView(headerRow)

        val contentText = TextView(this).apply {
            text = entry.content
            textSize = 14f
            setTextColor(0xFFe8e8f0.toInt())
            setPadding(0, 8, 0, 0)
        }
        card.addView(contentText)

        container.addView(card)
    }

    private fun getCategoryColor(category: String): Int = when (category) {
        "场景" -> 0xFF64ffda.toInt()
        "剧情" -> 0xFF9c7cff.toInt()
        "喜好" -> 0xFF7ec8a0.toInt()
        "习惯" -> 0xFF7eb8e0.toInt()
        "事实" -> 0xFFe0c070.toInt()
        "事件" -> 0xFFe088a0.toInt()
        "计划" -> 0xFFc0a0e0.toInt()
        "继承" -> 0xFF90c0d0.toInt()
        else -> 0xFF808890.toInt()
    }

    override fun onResume() {
        super.onResume()
        contextManager.memoryPool.saveToStorage()
        refreshList()
    }
}
