/** 记忆管理页: 查看/添加/删除AI的长期记忆条目 */
package com.aicompanion.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.aicompanion.R
import com.aicompanion.memory.MemoryManager
import com.aicompanion.models.MemoryFact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MemoryActivity : AppCompatActivity() {

    private lateinit var memoryManager: MemoryManager
    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var layoutHeader: LinearLayout
    private lateinit var layoutSearch: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var chipAll: com.google.android.material.chip.Chip
    private lateinit var chipToday: com.google.android.material.chip.Chip
    private lateinit var btnSearchToggle: ImageButton
    private lateinit var btnDeleteAll: Button
    private lateinit var btnBack: ImageButton

    private var isSearchVisible = false
    private var showTodayOnly = false
    private var searchQuery = ""
    private var userId = "anonymous"

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)

        memoryManager = MemoryManager(this)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        userId = prefs.getString("user_id", "anonymous") ?: "anonymous"

        initViews()
        setupListeners()
        loadMemories()
    }

    private fun initViews() {
        listView = findViewById(R.id.listview_memories)
        tvEmpty = findViewById(R.id.tv_memory_empty)
        tvCount = findViewById(R.id.tv_memory_count)
        layoutHeader = findViewById(R.id.layout_memory_header)
        layoutSearch = findViewById(R.id.layout_search)
        etSearch = findViewById(R.id.et_search_memory)
        chipAll = findViewById(R.id.chip_all)
        chipToday = findViewById(R.id.chip_today)
        btnSearchToggle = findViewById(R.id.btn_search_toggle)
        btnDeleteAll = findViewById(R.id.btn_delete_all_memories)
        btnBack = findViewById(R.id.btn_memory_back)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnSearchToggle.setOnClickListener {
            isSearchVisible = !isSearchVisible
            layoutSearch.visibility = if (isSearchVisible) View.VISIBLE else View.GONE
            if (!isSearchVisible) {
                etSearch.text?.clear()
                searchQuery = ""
                loadMemories()
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                loadMemories()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        chipAll.setOnClickListener {
            chipAll.isChecked = true
            chipToday.isChecked = false
            showTodayOnly = false
            loadMemories()
        }

        chipToday.setOnClickListener {
            chipToday.isChecked = true
            chipAll.isChecked = false
            showTodayOnly = true
            loadMemories()
        }

        btnDeleteAll.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要清空所有记忆吗？此操作不可撤销。\n本地缓存的记忆和云端记忆都将被删除。")
                .setPositiveButton("确认清空") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        memoryManager.deleteAllMemories(userId)
                        runOnUiThread { loadMemories() }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun loadMemories() {
        val memories = if (searchQuery.isNotEmpty()) {
            memoryManager.searchLocalMemories(searchQuery)
        } else if (showTodayOnly) {
            memoryManager.getTodayMemories()
        } else {
            memoryManager.getLocalMemories()
        }

        if (memories.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            layoutHeader.visibility = View.GONE
        } else {
            listView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            layoutHeader.visibility = View.VISIBLE
            tvCount.text = "共 ${memories.size} 条记忆" + if (showTodayOnly) "（今日）" else ""

            val adapter = MemoryAdapter(memories)
            listView.adapter = adapter
        }
    }

    private inner class MemoryAdapter(private val memories: List<MemoryFact>) : BaseAdapter() {
        override fun getCount(): Int = memories.size
        override fun getItem(position: Int): Any = memories[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(
                android.R.layout.simple_list_item_2, parent, false
            )

            val memory = memories[position]
            val text1 = view.findViewById<TextView>(android.R.id.text1)
            val text2 = view.findViewById<TextView>(android.R.id.text2)

            text1.text = memory.fact
            text1.setTextColor(0xFFC9D1D9.toInt())
            text1.textSize = 14f
            text1.setPadding(32, 24, 32, 4)

            val timeStr = if (memory.timestamp > 0) dateFormat.format(Date(memory.timestamp)) else ""
            val categoryStr = if (memory.category.isNotEmpty()) "[${memory.category}] " else ""
            text2.text = "$categoryStr$timeStr"
            text2.setTextColor(0xFF667788.toInt())
            text2.textSize = 11f
            text2.setPadding(32, 0, 32, 20)

            view.setBackgroundColor(0xFF0D1117.toInt())
            view.setOnLongClickListener {
                android.app.AlertDialog.Builder(this@MemoryActivity)
                    .setTitle("删除这条记忆")
                    .setMessage("\"${memory.fact}\"")
                    .setPositiveButton("删除") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            memoryManager.deleteMemoryBlocking(userId, memory.id)
                            runOnUiThread { loadMemories() }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            return view
        }
    }
}