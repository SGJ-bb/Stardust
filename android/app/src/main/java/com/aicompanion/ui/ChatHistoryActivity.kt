package com.aicompanion.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
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

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        val tvStats = TextView(this).apply {
            text = stats
            textSize = 13f
            setTextColor(0xFF8899aa.toInt())
        }
        rootLayout.addView(tvStats)

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
