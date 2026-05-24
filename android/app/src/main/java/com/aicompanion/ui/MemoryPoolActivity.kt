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
        val details = contextManager.memoryPool.getAllDetails()
        tvStats.text = contextManager.getSessionStats()

        if (entries.isEmpty() && details.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "记忆池为空\n\n开始聊天后，AI会自动提取并记录场景、剧情和关键信息\n每2轮对话提取记忆，每10轮对话压缩整理"
                textSize = 14f
                setTextColor(0xFF667788.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 64, 0, 0)
                alpha = 0.7f
            }
            container.addView(emptyView)
            return
        }

        if (entries.isNotEmpty()) {
            val sectionTitle = TextView(this).apply {
                text = "📝 总结记忆"
                textSize = 15f
                setTextColor(0xFFc4b5fd.toInt())
                setPadding(0, 8, 0, 8)
            }
            container.addView(sectionTitle)
            for (entry in entries) {
                addEntryView(entry)
            }
        }

        if (details.isNotEmpty()) {
            val detailTitle = TextView(this).apply {
                text = "🔍 细节记忆"
                textSize = 15f
                setTextColor(0xFF7dd3fc.toInt())
                setPadding(0, 16, 0, 8)
            }
            container.addView(detailTitle)
            for (entry in details) {
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
                if (entry.category == "细节") {
                    contextManager.memoryPool.deleteDetailEntry(entry.id)
                } else {
                    contextManager.memoryPool.delete(entry.id)
                }
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
        "总结" -> 0xFF9c7cff.toInt()
        "细节" -> 0xFF7dd3fc.toInt()
        "继承" -> 0xFFfbbf24.toInt()
        else -> 0xFF808890.toInt()
    }

    override fun onResume() {
        super.onResume()
        contextManager.memoryPool.loadFromStorage()
        refreshList()
    }
}
