package com.aicompanion.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
    private lateinit var btnBack: ImageButton
    private lateinit var chipAll: com.google.android.material.chip.Chip
    private lateinit var chipHappy: com.google.android.material.chip.Chip
    private lateinit var chipCalm: com.google.android.material.chip.Chip

    private var isSearchVisible = false
    private var searchQuery = ""
    private var filterMood: String? = null
    private val displayDateFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
    private val parseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        diaryManager = DiaryManager(this)
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

            return view
        }
    }
}