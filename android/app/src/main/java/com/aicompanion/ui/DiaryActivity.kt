/** 日记页: 按日期展示AI自动生成的每日日记 */
package com.aicompanion.ui

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.aicompanion.R
import com.aicompanion.diary.DiaryEntry
import com.aicompanion.diary.DiaryManager

import java.text.SimpleDateFormat
import java.util.*

class DiaryActivity : AppCompatActivity() {

    private lateinit var diaryManager: DiaryManager
    private lateinit var listView: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var layoutSearch: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var btnSearchToggle: ImageButton
    private lateinit var btnExport: ImageButton
    private lateinit var btnImport: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var chipAll: com.google.android.material.chip.Chip
    private lateinit var chipHappy: com.google.android.material.chip.Chip
    private lateinit var chipCalm: com.google.android.material.chip.Chip

    private var isSearchVisible = false
    private var searchQuery = ""
    private var filterMood: String? = null
    private val displayDateFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
    private val parseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val importDiaryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) handleImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        diaryManager = DiaryManager(this, getSharedPreferences("app_prefs", MODE_PRIVATE).getString("active_persona_id", "default") ?: "default")
        initViews()
        setupListeners()
        loadDiaries()
    }

    private fun initViews() {
        listView = findViewById(R.id.listview_diaries)
        tvEmpty = findViewById(R.id.tv_diary_empty)
        layoutSearch = findViewById(R.id.layout_diary_search)
        etSearch = findViewById(R.id.et_search_diary)
        btnSearchToggle = findViewById(R.id.btn_search_diary)
        btnImport = findViewById(R.id.btn_import_diary)
        btnExport = findViewById(R.id.btn_export_diary)
        btnBack = findViewById(R.id.btn_diary_back)
        chipAll = findViewById(R.id.chip_mood_all)
        chipHappy = findViewById(R.id.chip_mood_happy)
        chipCalm = findViewById(R.id.chip_mood_calm)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnSearchToggle.setOnClickListener {
            isSearchVisible = !isSearchVisible
            layoutSearch.visibility = if (isSearchVisible) View.VISIBLE else View.GONE
            if (!isSearchVisible) {
                etSearch.text?.clear()
                searchQuery = ""
                loadDiaries()
            }
        }

        btnImport.setOnClickListener {
            importDiaryLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        btnExport.setOnClickListener { showExportDialog() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                loadDiaries()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        chipAll.setOnClickListener { setMoodFilter(null, chipAll, chipHappy, chipCalm) }
        chipHappy.setOnClickListener { setMoodFilter("happy", chipHappy, chipAll, chipCalm) }
        chipCalm.setOnClickListener { setMoodFilter("calm", chipCalm, chipAll, chipHappy) }
    }

    private fun setMoodFilter(
        mood: String?,
        selected: com.google.android.material.chip.Chip,
        vararg others: com.google.android.material.chip.Chip
    ) {
        selected.isChecked = true
        others.forEach { it.isChecked = false }
        filterMood = mood
        loadDiaries()
    }

    private fun loadDiaries() {
        var diaries = diaryManager.getAllDiaries()

        if (searchQuery.isNotEmpty()) {
            diaries = diaryManager.searchDiaries(searchQuery)
            if (filterMood != null) {
                diaries = diaries.filter { it.mood == filterMood }
            }
        } else if (filterMood != null) {
            diaries = diaryManager.getDiariesByMood(filterMood!!)
        }

        if (diaries.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            listView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            listView.adapter = DiaryAdapter(diaries)
        }
    }

    private fun showExportDialog() {
        val diaries = diaryManager.getAllDiaries()
        if (diaries.isEmpty()) {
            Toast.makeText(this, "还没有日记可以导出", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("Markdown 格式（可读）", "JSON 格式（兼容）")
        android.app.AlertDialog.Builder(this)
            .setTitle("导出日记（共${diaries.size}篇）")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportMarkdown(diaries)
                    1 -> exportJson(diaries)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportMarkdown(diaries: List<DiaryEntry>) {
        val content = diaryManager.exportToMarkdown(diaries)
        val filename = "diary_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.md"
        diaryManager.shareExport(content, filename, "text/markdown")
        Toast.makeText(this, "正在生成 Markdown 导出...", Toast.LENGTH_SHORT).show()
    }

    private fun exportJson(diaries: List<DiaryEntry>) {
        val content = diaryManager.exportToJson(diaries)
        val filename = "diary_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        diaryManager.shareExport(content, filename, "application/json")
        Toast.makeText(this, "正在生成 JSON 导出...", Toast.LENGTH_SHORT).show()
    }

    private fun handleImport(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val jsonContent = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()

            if (jsonContent.isBlank()) {
                Toast.makeText(this, "文件内容为空", Toast.LENGTH_SHORT).show()
                return
            }

            Toast.makeText(this, "正在导入日记...", Toast.LENGTH_SHORT).show()
            val result = diaryManager.importFromJson(jsonContent)

            loadDiaries()

            val msg = buildString {
                append("导入完成：成功 ${result.imported} 篇")
                if (result.skipped > 0) append("，跳过 ${result.skipped} 篇（已存在）")
                if (result.errors.isNotEmpty()) {
                    append("\n错误：${result.errors.take(3).joinToString("；")}")
                    if (result.errors.size > 3) append("...等${result.errors.size}条")
                }
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("导入结果")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private inner class DiaryAdapter(private val diaries: List<DiaryEntry>) : BaseAdapter() {
        override fun getCount(): Int = diaries.size
        override fun getItem(pos: Int): Any = diaries[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.item_diary, parent, false
            )
            val diary = diaries[position]

            val tvMood = view.findViewById<TextView>(R.id.tv_diary_mood)
            val tvDate = view.findViewById<TextView>(R.id.tv_diary_date)
            val tvContent = view.findViewById<TextView>(R.id.tv_diary_content)
            val tvStats = view.findViewById<TextView>(R.id.tv_diary_stats)

            tvMood.text = diary.moodEmoji
            tvDate.text = try {
                val parsed = parseDateFormat.parse(diary.date)
                if (parsed != null) displayDateFormat.format(parsed) else diary.date
            } catch (_: Exception) { diary.date }
            tvContent.text = diary.content.take(120) + if (diary.content.length > 120) "..." else ""
            tvStats.text = "[${diary.messageCount}条消息] 好感度${diary.affectionLevel}"

            tvDate.setTextColor(0xFF8899CC.toInt())
            tvContent.setTextColor(0xFFC9D1D9.toInt())
            tvStats.setTextColor(0xFF667788.toInt())

            view.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1C2128.toInt())
                cornerRadius = 24f
                setStroke(1, 0xFF2A3040.toInt())
                val pad = 24
                setPadding(pad, pad, pad, pad)
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 8, 8) }
            view.layoutParams = params

            view.setOnLongClickListener {
                android.app.AlertDialog.Builder(this@DiaryActivity)
                    .setTitle("删除日记")
                    .setMessage("确定删除 ${diary.date} 的日记吗？")
                    .setPositiveButton("删除") { _, _ ->
                        diaryManager.deleteDiary(diary.date)
                        loadDiaries()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            view.setOnClickListener {
                showDiaryDetail(diary)
            }

            return view
        }
    }

    private fun showDiaryDetail(diary: com.aicompanion.diary.DiaryEntry) {
        val contentView = layoutInflater.inflate(R.layout.dialog_diary_detail, null)
        contentView.findViewById<TextView>(R.id.tv_detail_mood)?.text = "${diary.moodEmoji} ${diary.mood}"
        contentView.findViewById<TextView>(R.id.tv_detail_date)?.text = diary.date
        contentView.findViewById<TextView>(R.id.tv_detail_stats)?.text = "[${diary.messageCount}条消息] 好感度${diary.affectionLevel}"
        contentView.findViewById<TextView>(R.id.tv_detail_tags)?.text = if (diary.tags.isNotEmpty()) "标签: ${diary.tags.joinToString(", ")}" else ""
        contentView.findViewById<TextView>(R.id.tv_detail_content)?.text = diary.content

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        sheet.setContentView(contentView)
        sheet.behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()
        sheet.behavior.isDraggable = true
        sheet.show()
    }
}