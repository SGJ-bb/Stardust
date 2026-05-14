/** 成就页: 展示已解锁和未解锁的成就列表及进度 */
package com.aicompanion.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.aicompanion.R
import com.aicompanion.gamify.AchievementManager
import com.aicompanion.models.Achievement

class AchievementActivity : Activity() {

    private lateinit var achievementManager: AchievementManager
    private lateinit var listView: ListView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var chipAll: com.google.android.material.chip.Chip
    private lateinit var chipChat: com.google.android.material.chip.Chip
    private lateinit var chipCheckIn: com.google.android.material.chip.Chip
    private lateinit var chipAffection: com.google.android.material.chip.Chip
    private lateinit var chipFeedback: com.google.android.material.chip.Chip
    private lateinit var chipPomodoro: com.google.android.material.chip.Chip

    private var currentFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievement)

        achievementManager = AchievementManager(this)
        initViews()
        setupListeners()
        loadAchievements()
    }

    private fun initViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        listView = findViewById(R.id.listview_achievements)
        tvProgress = findViewById(R.id.tv_achievement_progress)
        progressBar = findViewById(R.id.progress_achievement)
        tvEmpty = findViewById(R.id.tv_achievement_empty)
        chipAll = findViewById(R.id.chip_cat_all)
        chipChat = findViewById(R.id.chip_cat_chat)
        chipCheckIn = findViewById(R.id.chip_cat_checkin)
        chipAffection = findViewById(R.id.chip_cat_affection)
        chipFeedback = findViewById(R.id.chip_cat_feedback)
        chipPomodoro = findViewById(R.id.chip_cat_pomodoro)
    }

    private fun setupListeners() {
        chipAll.setOnClickListener { setFilter(null, chipAll, chipChat, chipCheckIn, chipAffection, chipFeedback, chipPomodoro) }
        chipChat.setOnClickListener { setFilter("chat", chipChat, chipAll, chipCheckIn, chipAffection, chipFeedback, chipPomodoro) }
        chipCheckIn.setOnClickListener { setFilter("checkin", chipCheckIn, chipAll, chipChat, chipAffection, chipFeedback, chipPomodoro) }
        chipAffection.setOnClickListener { setFilter("affection", chipAffection, chipAll, chipChat, chipCheckIn, chipFeedback, chipPomodoro) }
        chipFeedback.setOnClickListener { setFilter("feedback", chipFeedback, chipAll, chipChat, chipCheckIn, chipAffection, chipPomodoro) }
        chipPomodoro.setOnClickListener { setFilter("pomodoro", chipPomodoro, chipAll, chipChat, chipCheckIn, chipAffection, chipFeedback) }
    }

    private fun setFilter(
        filter: String?,
        selected: com.google.android.material.chip.Chip,
        vararg others: com.google.android.material.chip.Chip
    ) {
        selected.isChecked = true
        others.forEach { it.isChecked = false }
        currentFilter = filter
        loadAchievements()
    }

    private fun loadAchievements() {
        var achievements = if (currentFilter != null) {
            achievementManager.getByCategory(currentFilter!!)
        } else {
            achievementManager.getAchievements()
        }

        val sorted = achievements.sortedWith(compareByDescending<Achievement> { it.unlocked }.thenByDescending { it.progress })

        if (sorted.isEmpty()) {
            listView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            listView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            listView.adapter = AchievementAdapter(sorted)
        }

        val total = achievementManager.totalCount
        val unlocked = achievementManager.unlockedCount
        tvProgress.text = "已解锁 $unlocked/$total"
        progressBar.max = total
        progressBar.progress = unlocked
    }

    private inner class AchievementAdapter(private val achievements: List<Achievement>) : BaseAdapter() {
        override fun getCount(): Int = achievements.size
        override fun getItem(pos: Int): Any = achievements[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.item_achievement, parent, false
            )
            val ach = achievements[position]

            val tvIcon = view.findViewById<TextView>(R.id.tv_achievement_icon)
            val tvTitle = view.findViewById<TextView>(R.id.tv_achievement_title)
            val tvDesc = view.findViewById<TextView>(R.id.tv_achievement_desc)
            val progressItem = view.findViewById<ProgressBar>(R.id.progress_achievement_item)
            val tvStatus = view.findViewById<TextView>(R.id.tv_achievement_status)

            tvIcon.text = ach.icon
            tvTitle.text = ach.title
            tvDesc.text = ach.description

            if (ach.unlocked) {
                tvIcon.alpha = 1.0f
                tvTitle.setTextColor(0xFFF0C060.toInt())
                tvDesc.setTextColor(0xFF8899AA.toInt())
                progressItem.progressTintList = android.content.res.ColorStateList.valueOf(0xFFF0C060.toInt())
                progressItem.progress = 100
                tvStatus.text = "✅ 已解锁"
                tvStatus.setTextColor(0xFFF0C060.toInt())
            } else {
                tvIcon.alpha = 0.4f
                tvTitle.setTextColor(0xFF667788.toInt())
                tvDesc.setTextColor(0xFF556677.toInt())
                progressItem.progressTintList = android.content.res.ColorStateList.valueOf(0xFF667788.toInt())
                val pct = if (ach.unlockCondition > 0) (ach.progress * 100 / ach.unlockCondition) else 0
                progressItem.progress = pct
                tvStatus.text = "${ach.progress}/${ach.unlockCondition}"
                tvStatus.setTextColor(0xFF667788.toInt())
            }

            view.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1C2128.toInt())
                cornerRadius = 20f
                setStroke(1, 0xFF2A3040.toInt())
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 8, 8) }
            view.layoutParams = params

            return view
        }
    }
}