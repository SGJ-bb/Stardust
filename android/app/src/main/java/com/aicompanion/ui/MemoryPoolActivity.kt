package com.aicompanion.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aicompanion.memory.ContextManager
import com.aicompanion.memory.MemoryEntry
import com.aicompanion.R

class MemoryPoolActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MemoryPoolActivity"
    }

    // 文件级颜色常量
    private val COLOR_GLOBAL_TITLE = 0xFF34d399.toInt() // 跨场景记忆标题
    private val COLOR_CARD_BG = 0x22ffffff.toInt() // 卡片背景
    private val COLOR_BADGE_TEXT = 0xFF1a1a2e.toInt() // 标签文字
    private val COLOR_DELETE_TEXT = 0xFFff6666.toInt() // 删除按钮
    private val COLOR_MEMORY_SUMMARY = 0xFF9c7cff.toInt() // 总结类型
    private val COLOR_MEMORY_DETAIL = 0xFF7dd3fc.toInt() // 细节类型
    private val COLOR_MEMORY_INHERIT = 0xFFfbbf24.toInt() // 继承类型
    private val COLOR_MEMORY_GLOBAL = 0xFF34d399.toInt() // 全局类型
    private val COLOR_MEMORY_DEFAULT = 0xFF808890.toInt() // 默认类型

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
            setBackgroundColor(ContextCompat.getColor(this@MemoryPoolActivity, R.color.bg_base))
        }

        tvStats = TextView(this).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MemoryPoolActivity, R.color.text_secondary))
            setPadding(0, 0, 0, 16)
        }
        container.addView(tvStats)

        refreshList()

        scrollView.addView(container)
        setContentView(scrollView)
        applyTheme()
    }

    private fun applyTheme() {
        try {
            val scheme = com.aicompanion.theme.ThemeManager.getCurrentScheme(this)
            val tbColor = android.graphics.Color.parseColor(scheme.toolbarColor)
            findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.setBackgroundColor(tbColor)
                ?: findViewById<View>(R.id.toolbar_container)?.setBackgroundColor(tbColor)
            com.aicompanion.theme.ThemeManager.applyTheme(this)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "applyTheme error: ${e.message}")
        }
    }

    private fun refreshList() {
        container.removeViews(1, container.childCount - 1)

        val entries = contextManager.memoryPool.getAll()
        val details = contextManager.memoryPool.getAllDetails()
        val globalEntries = contextManager.globalMemoryPool.getAll()
        tvStats.text = contextManager.getSessionStats()

        if (entries.isEmpty() && details.isEmpty() && globalEntries.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "记忆池为空\n\n开始聊天后，AI会自动提取并记录场景、剧情和关键信息\n每2轮对话提取记忆，每10轮对话压缩整理\n\n群聊中的重要信息也会同步到跨场景记忆"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MemoryPoolActivity, R.color.text_muted))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 64, 0, 0)
                alpha = 0.7f
            }
            container.addView(emptyView)
            return
        }

        // 跨场景共享记忆（包含群聊同步过来的记忆）
        if (globalEntries.isNotEmpty()) {
            val globalTitle = TextView(this).apply {
                text = "🌐 跨场景记忆（含群聊同步）"
                textSize = 15f
                setTextColor(COLOR_GLOBAL_TITLE)
                setPadding(0, 8, 0, 8)
            }
            container.addView(globalTitle)
            for (entry in globalEntries) {
                addEntryView(entry, isGlobal = true)
            }
        }

        if (entries.isNotEmpty()) {
            val sectionTitle = TextView(this).apply {
                text = "📝 总结记忆"
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@MemoryPoolActivity, R.color.brand_accent))
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
                setTextColor(ContextCompat.getColor(this@MemoryPoolActivity, R.color.accent_cyan))
                setPadding(0, 16, 0, 8)
            }
            container.addView(detailTitle)
            for (entry in details) {
                addEntryView(entry)
            }
        }
    }

    private fun addEntryView(entry: MemoryEntry, isGlobal: Boolean = false) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_CARD_BG)
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
            text = if (isGlobal) "全局" else entry.category
            textSize = 10f
            setTextColor(COLOR_BADGE_TEXT)
            setBackgroundColor(getCategoryColor(if (isGlobal) "全局" else entry.category))
            setPadding(8, 2, 8, 2)
        }
        headerRow.addView(categoryBadge)

        if (isGlobal) {
            val sceneBadge = TextView(this).apply {
                text = "跨场景"
                textSize = 10f
                setTextColor(COLOR_BADGE_TEXT)
                setBackgroundColor(COLOR_MEMORY_GLOBAL)
                setPadding(8, 2, 8, 2)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 0, 0) }
            }
            headerRow.addView(sceneBadge)
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        headerRow.addView(spacer)

        val deleteBtn = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(COLOR_DELETE_TEXT)
            setPadding(16, 0, 0, 0)
            setOnClickListener {
                if (isGlobal) {
                    contextManager.globalMemoryPool.delete(entry.id)
                } else if (entry.category == "细节") {
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
            setTextColor(ContextCompat.getColor(this@MemoryPoolActivity, R.color.text_primary))
            setPadding(0, 8, 0, 0)
        }
        card.addView(contentText)

        container.addView(card)
    }

    private fun getCategoryColor(category: String): Int = when (category) {
        "总结" -> COLOR_MEMORY_SUMMARY
        "细节" -> COLOR_MEMORY_DETAIL
        "继承" -> COLOR_MEMORY_INHERIT
        "全局" -> COLOR_MEMORY_GLOBAL
        else -> COLOR_MEMORY_DEFAULT
    }

    override fun onResume() {
        super.onResume()
        contextManager.memoryPool.loadFromStorage()
        refreshList()
    }
}
