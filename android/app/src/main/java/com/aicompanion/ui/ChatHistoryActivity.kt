package com.aicompanion.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aicompanion.storage.ChatHistoryStorage
import com.aicompanion.storage.StoredMessage

class ChatHistoryActivity : AppCompatActivity() {

    private lateinit var chatStorage: ChatHistoryStorage
    private var scope: String = "persona"
    private var scopeId: String = "default"
    private var scopeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatStorage = ChatHistoryStorage(this)
        scope = intent.getStringExtra("scope") ?: "persona"
        scopeId = intent.getStringExtra("scopeId") ?: "default"
        scopeName = intent.getStringExtra("scopeName") ?: scopeId

        title = "💬 $scopeName 的聊天记录"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        showDateList()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showDateList() {
        val dates = chatStorage.getDates(scope, scopeId)
        val stats = chatStorage.getStats(scope, scopeId)
        // 消息总数统计：调用 getMessageCount 显式获取总条数
        val totalCount = chatStorage.getMessageCount(scope, scopeId)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        // 顶部：共 N 条消息（消息总数统计）
        val tvCount = TextView(this).apply {
            text = "共 $totalCount 条消息"
            textSize = 15f
            setTextColor(0xFFE8E8F0.toInt())
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(tvCount)

        val tvStats = TextView(this).apply {
            text = stats
            textSize = 13f
            setTextColor(0xFF8899aa.toInt())
        }
        rootLayout.addView(tvStats)

        // 搜索框区域：EditText + 搜索按钮
        val searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }
        val etSearch = EditText(this).apply {
            hint = "输入关键词搜索消息…"
            textSize = 14f
            setSingleLine(true)
            setPadding(20, 12, 20, 12)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2233.toInt())
                cornerRadius = 24f
            }
            setTextColor(0xFFE8E8F0.toInt())
            setHintTextColor(0xFF667788.toInt())
        }
        val btnSearch = Button(this).apply {
            text = "搜索"
            setTextColor(0xFFE8E8F0.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF6C5CE7.toInt())
                cornerRadius = 24f
            }
            setOnClickListener {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    // 隐藏键盘
                    (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                        ?.hideSoftInputFromWindow(etSearch.windowToken, 0)
                    showSearchResults(query)
                }
            }
        }
        searchLayout.addView(etSearch, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))
        searchLayout.addView(btnSearch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(12, 0, 0, 0) })
        rootLayout.addView(searchLayout)

        if (dates.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "暂无聊天记录"
                textSize = 16f
                setTextColor(0xFF667788.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 100, 0, 0)
            }
            rootLayout.addView(tvEmpty, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        } else {
            val listView = ListView(this).apply {
                dividerHeight = 1
                adapter = DateAdapter(dates)
                setOnItemClickListener { _, _, position, _ ->
                    showMessageList(dates[position])
                }
            }
            rootLayout.addView(listView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }

        val btnDeleteAll = Button(this).apply {
            text = "删除全部聊天记录"
            setTextColor(0xFFFF6B6B.toInt())
            setOnClickListener {
                AlertDialog.Builder(this@ChatHistoryActivity)
                    .setTitle("确认删除")
                    .setMessage("确定要删除 $scopeName 的所有聊天记录吗？此操作不可恢复。")
                    .setPositiveButton("删除") { _, _ ->
                        chatStorage.deleteScope(scope, scopeId)
                        showDateList()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        rootLayout.addView(btnDeleteAll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val scrollView = ScrollView(this).apply { addView(rootLayout) }
        setContentView(scrollView)
    }

    private fun showMessageList(date: String) {
        val msgs = chatStorage.getMessages(scope, scopeId, date)
        title = "💬 $date"

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        val tvDate = TextView(this).apply {
            text = "$date  ·  ${msgs.size}条消息"
            textSize = 13f
            setTextColor(0xFF8899aa.toInt())
        }
        rootLayout.addView(tvDate)

        msgs.forEach { msg ->
            val msgLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val header = TextView(this).apply {
                text = "${msg.time}  ${if (msg.isUser) "👤 你" else "🤖 ${msg.senderName.ifBlank { "AI" }}"}"
                textSize = 11f
                setTextColor(0xFF667788.toInt())
            }
            msgLayout.addView(header)

            val content = TextView(this).apply {
                text = msg.text
                textSize = 14f
                setTextColor(if (msg.isUser) 0xFFE8E8F0.toInt() else 0xFFC4B5FD.toInt())
            }
            msgLayout.addView(content)

            rootLayout.addView(msgLayout)
        }

        val btnDelete = Button(this).apply {
            text = "删除 $date 的记录"
            setTextColor(0xFFFF6B6B.toInt())
            setOnClickListener {
                AlertDialog.Builder(this@ChatHistoryActivity)
                    .setTitle("确认删除")
                    .setMessage("确定要删除 $date 的聊天记录吗？")
                    .setPositiveButton("删除") { _, _ ->
                        chatStorage.deleteDate(scope, scopeId, date)
                        showDateList()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        rootLayout.addView(btnDelete)

        val scrollView = ScrollView(this).apply { addView(rootLayout) }
        setContentView(scrollView)
    }

    /**
     * 搜索结果页：调用 chatHistoryStorage.searchMessages 进行关键词搜索
     * 结果以 RecyclerView/ListView 风格逐条展示
     */
    private fun showSearchResults(query: String) {
        val results = chatStorage.searchMessages(scope, scopeId, query, limit = 50)
        title = "💬 搜索: $query"

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        // 搜索结果统计
        val tvResultStats = TextView(this).apply {
            text = "关键词「$query」 · 共找到 ${results.size} 条结果"
            textSize = 13f
            setTextColor(0xFF8899aa.toInt())
        }
        rootLayout.addView(tvResultStats)

        if (results.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "未找到匹配的消息"
                textSize = 15f
                setTextColor(0xFF667788.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 80, 0, 0)
            }
            rootLayout.addView(tvEmpty, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        } else {
            results.forEach { msg ->
                val msgLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 8, 0, 8)
                }

                val header = TextView(this).apply {
                    // StoredMessage 无 date 字段，从 timestamp 格式化日期
                    val dateStr = if (msg.timestamp > 0) {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date(msg.timestamp))
                    } else ""
                    text = "$dateStr ${msg.time}  ${if (msg.isUser) "👤 你" else "🤖 ${msg.senderName.ifBlank { "AI" }}"}"
                    textSize = 11f
                    setTextColor(0xFF667788.toInt())
                }
                msgLayout.addView(header)

                val content = TextView(this).apply {
                    text = msg.text
                    textSize = 14f
                    setTextColor(if (msg.isUser) 0xFFE8E8F0.toInt() else 0xFFC4B5FD.toInt())
                }
                msgLayout.addView(content)

                rootLayout.addView(msgLayout)
            }
        }

        // 返回按钮：回到日期列表
        val btnBack = Button(this).apply {
            text = "返回聊天记录列表"
            setTextColor(0xFF6C5CE7.toInt())
            setOnClickListener {
                title = "💬 $scopeName 的聊天记录"
                showDateList()
            }
        }
        rootLayout.addView(btnBack, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 16, 0, 0) })

        val scrollView = ScrollView(this).apply { addView(rootLayout) }
        setContentView(scrollView)
    }

    inner class DateAdapter(private val dates: List<String>) : BaseAdapter() {
        override fun getCount() = dates.size
        override fun getItem(position: Int) = dates[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val date = dates[position]
            val msgs = chatStorage.getMessages(scope, scopeId, date)
            val userCount = msgs.count { it.isUser }
            val aiCount = msgs.size - userCount

            return LinearLayout(this@ChatHistoryActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)

                addView(TextView(this@ChatHistoryActivity).apply {
                    text = "📅 $date"
                    textSize = 16f
                    setTextColor(0xFFE8E8F0.toInt())
                })

                addView(TextView(this@ChatHistoryActivity).apply {
                    text = "$userCount 条你的消息  ·  $aiCount 条AI回复"
                    textSize = 12f
                    setTextColor(0xFF8899aa.toInt())
                })
            }
        }
    }
}
