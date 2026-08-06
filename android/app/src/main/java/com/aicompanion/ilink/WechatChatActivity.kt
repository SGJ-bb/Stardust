package com.aicompanion.ilink

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.persona.PersonaManager
import com.aicompanion.storage.ChatHistoryStorage
import com.aicompanion.storage.StoredMessage
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WechatChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WechatChat"
        const val EXTRA_PERSONA_ID = "persona_id"
    }

    private lateinit var chatStorage: ChatHistoryStorage
    private lateinit var personaManager: PersonaManager
    private lateinit var adapter: WechatChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvStats: TextView
    private lateinit var tvTitle: TextView
    private lateinit var layoutDates: LinearLayout
    private lateinit var scrollDates: HorizontalScrollView

    private var scopeId: String = "default"
    private var currentPersonaName: String = "AI"
    private var selectedDate: String = ""
    private var allDates: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wechat_chat)

        chatStorage = ChatHistoryStorage(this)
        personaManager = PersonaManager(this)
        personaManager.load()

        scopeId = intent.getStringExtra(EXTRA_PERSONA_ID)
            ?: personaManager.getActivePersona().id
        currentPersonaName = personaManager.getPersona(scopeId)?.name ?: "AI"

        recyclerView = findViewById(R.id.rv_wechat_messages)
        tvStats = findViewById(R.id.tv_stats)
        tvTitle = findViewById(R.id.tv_title)
        layoutDates = findViewById(R.id.layout_dates)
        scrollDates = findViewById(R.id.scroll_dates)

        adapter = WechatChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter

        tvTitle.text = "${currentPersonaName} · 微信记录"

        findViewById<TextView>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<ImageView>(R.id.btn_calendar).setOnClickListener { showDateSelector() }

        findViewById<TextView>(R.id.btn_clear).setOnClickListener { confirmClear() }

        loadDatesAndMessages()
    }

    override fun onResume() {
        super.onResume()
        loadDatesAndMessages()
    }

    private fun loadDatesAndMessages() {
        allDates = chatStorage.getDates("wechat", scopeId)

        renderDateTabs()

        if (allDates.isEmpty()) {
            adapter.setMessages(emptyList())
            tvStats.text = "暂无微信聊天记录"
            return
        }

        // 默认显示最新日期
        if (selectedDate.isBlank() || !allDates.contains(selectedDate)) {
            selectedDate = allDates.last()
        }

        loadMessagesForDate(selectedDate)
    }

    private fun loadMessagesForDate(date: String) {
        selectedDate = date
        val msgs = chatStorage.getMessages("wechat", scopeId, date)
        adapter.setMessages(msgs)

        val userCount = msgs.count { it.isUser }
        val aiCount = msgs.size - userCount
        tvStats.text = "$date  |  $userCount 条用户消息  |  $aiCount 条AI回复"

        // 滚动到底部
        recyclerView.post {
            if (adapter.itemCount > 0) {
                recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }

        // 更新日期标签选中状态
        updateDateTabSelection()
    }

    private fun renderDateTabs() {
        layoutDates.removeAllViews()

        if (allDates.isEmpty()) return

        val density = resources.displayMetrics.density

        for (date in allDates) {
            val tab = TextView(this).apply {
                text = date.substring(5) // "MM-DD"
                textSize = 13f
                setPadding(
                    (12 * density).toInt(),
                    (6 * density).toInt(),
                    (12 * density).toInt(),
                    (6 * density).toInt()
                )
                setTextColor(0xFF333333.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(0xFFE0E0E0.toInt())
                }
                setOnClickListener {
                    loadMessagesForDate(date)
                }
            }
            layoutDates.addView(tab)
        }

        // 自动滚动到选中日期
        updateDateTabSelection()
    }

    private fun updateDateTabSelection() {
        val density = resources.displayMetrics.density
        var selectedIndex = -1
        for (i in 0 until layoutDates.childCount) {
            val tab = layoutDates.getChildAt(i) as? TextView ?: continue
            val tabDate = allDates.getOrNull(i) ?: continue
            if (tabDate == selectedDate) {
                selectedIndex = i
                tab.setTextColor(0xFFFFFFFF.toInt())
                tab.background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(0xFF07C160.toInt()) // 微信绿
                }
            } else {
                tab.setTextColor(0xFF333333.toInt())
                tab.background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(0xFFE0E0E0.toInt())
                }
            }
        }

        // 自动滚动到选中日期标签
        if (selectedIndex >= 0) {
            scrollDates.post {
                val selectedTab = layoutDates.getChildAt(selectedIndex) ?: return@post
                val tabLeft = selectedTab.left
                val tabRight = selectedTab.right
                val scrollWidth = scrollDates.width
                val scrollX = scrollDates.scrollX
                val targetScroll = (tabLeft + tabRight) / 2 - scrollWidth / 2
                if (targetScroll != scrollX) {
                    scrollDates.smoothScrollTo(targetScroll.coerceAtLeast(0), 0)
                }
            }
        }
    }

    private fun showDateSelector() {
        if (allDates.isEmpty()) {
            Toast.makeText(this, "暂无聊天记录", Toast.LENGTH_SHORT).show()
            return
        }

        val dateDisplay = allDates.map { date ->
            val msgs = chatStorage.getMessages("wechat", scopeId, date)
            val userCount = msgs.count { it.isUser }
            val aiCount = msgs.size - userCount
            "$date  ($userCount+$aiCount 条)"
        }.toTypedArray()

        val checkedItem = allDates.indexOf(selectedDate).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("选择日期")
            .setSingleChoiceItems(dateDisplay, checkedItem) { dialog, which ->
                loadMessagesForDate(allDates[which])
                dialog.dismiss()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun confirmClear() {
        if (selectedDate.isBlank()) {
            // 清空所有
            AlertDialog.Builder(this)
                .setTitle("清空全部微信聊天记录")
                .setMessage("确定要清空 ${currentPersonaName} 的所有微信聊天记录吗？此操作不可恢复。")
                .setPositiveButton("清空") { _, _ ->
                    chatStorage.deleteScope("wechat", scopeId)
                    loadDatesAndMessages()
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("清空 $selectedDate 的记录")
                .setMessage("确定要清空 $selectedDate 的微信聊天记录吗？")
                .setPositiveButton("清空") { _, _ ->
                    chatStorage.deleteDate("wechat", scopeId, selectedDate)
                    selectedDate = ""
                    loadDatesAndMessages()
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
