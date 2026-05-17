/** 角色信息页: 查看/编辑角色信息, 好感度展示, 角色卡导入导出 */
package com.aicompanion.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.affection.AffectionManager
import com.aicompanion.diary.DiaryManager
import com.aicompanion.gamify.AchievementManager
import com.aicompanion.memory.MemorableMomentsManager
import com.aicompanion.memory.ScoredMemory
import com.aicompanion.persona.PersonaManager
import com.aicompanion.stats.PersonaStatsManager
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_AI_AVATAR = 200
        private const val REQUEST_USER_AVATAR = 201
    }

    private var personaId: String = "default"
    private lateinit var personaManager: PersonaManager
    private lateinit var affectionManager: AffectionManager
    private lateinit var achievementManager: AchievementManager
    private lateinit var momentsManager: MemorableMomentsManager
    private lateinit var diaryManager: DiaryManager
    private lateinit var favoriteManager: FavoriteManager
    private lateinit var statsManager: PersonaStatsManager

    private lateinit var ivAiAvatar: ImageView
    private lateinit var ivUserAvatar: ImageView
    private lateinit var tvAffectionTitle: TextView
    private lateinit var progressAffection: ProgressBar
    private lateinit var tvAffectionValue: TextView
    private lateinit var tvDaysCount: TextView
    private lateinit var tvAchCount: TextView
    private lateinit var tvDiaryCount: TextView
    private lateinit var recyclerAchievements: RecyclerView
    private lateinit var containerMoments: LinearLayout
    private lateinit var tvMomentsEmpty: TextView
    private lateinit var tvAiName: TextView
    private lateinit var tvUserName: TextView
    private lateinit var containerFavorites: LinearLayout
    private lateinit var tvFavoritesEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        personaId = intent.getStringExtra("persona_id")
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("active_persona_id", "default") ?: "default"

        personaManager = PersonaManager(this)
        personaManager.load()

        affectionManager = AffectionManager(this, personaId)
        achievementManager = AchievementManager(this, personaId)
        momentsManager = MemorableMomentsManager(this, personaId)
        diaryManager = DiaryManager(this, personaId)
        favoriteManager = FavoriteManager(this, personaId)
        statsManager = PersonaStatsManager(this, personaId)

        initViews()
        loadData()
        animateEntrance()
    }

    private fun animateEntrance() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar?.let {
            com.aicompanion.anim.AnimeUtils.fadeInScale(it, delay = 50)
        }

        val cardStats = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_stats)
        cardStats?.let {
            it.alpha = 0f
            it.translationY = 30f
            it.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(150).start()
        }

        val statIds = intArrayOf(
            R.id.tv_stat_total_msgs, R.id.tv_stat_chat_days, R.id.tv_stat_streak,
            R.id.tv_stat_user_msgs, R.id.tv_stat_ai_msgs, R.id.tv_stat_stickers,
            R.id.tv_stat_words, R.id.tv_stat_avg_day, R.id.tv_stat_peak_hour
        )
        for ((i, id) in statIds.withIndex()) {
            val tv = findViewById<TextView>(id)
            tv?.let {
                it.alpha = 0f
                it.scaleX = 0.5f
                it.scaleY = 0.5f
                it.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(350).setStartDelay(300L + i * 60L).start()
            }
        }
    }

    private fun initViews() {
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        ivAiAvatar = findViewById(R.id.iv_ai_avatar)
        ivUserAvatar = findViewById(R.id.iv_user_avatar)
        tvAffectionTitle = findViewById(R.id.tv_affection_title)
        progressAffection = findViewById(R.id.progress_affection_profile)
        tvAffectionValue = findViewById(R.id.tv_affection_value)
        tvDaysCount = findViewById(R.id.tv_days_count)
        tvAchCount = findViewById(R.id.tv_ach_count)
        tvDiaryCount = findViewById(R.id.tv_diary_count)
        recyclerAchievements = findViewById(R.id.recycler_achievements)
        containerMoments = findViewById(R.id.container_moments)
        tvMomentsEmpty = findViewById(R.id.tv_moments_empty)
        tvAiName = findViewById(R.id.tv_ai_name_profile)
        tvUserName = findViewById(R.id.tv_user_name_profile)
        containerFavorites = findViewById(R.id.container_favorites)
        tvFavoritesEmpty = findViewById(R.id.tv_favorites_empty)

        recyclerAchievements.layoutManager = LinearLayoutManager(this)

        ivAiAvatar.setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            pickAvatar(REQUEST_AI_AVATAR)
        }
        ivUserAvatar.setOnClickListener {
            com.aicompanion.anim.AnimeUtils.pulse(it)
            pickAvatar(REQUEST_USER_AVATAR)
        }
    }

    private fun loadData() {
        val am = affectionManager
        val affection = am.affectionLevel
        progressAffection.progress = affection
        tvAffectionValue.text = "$affection / 100"
        tvAffectionTitle.text = am.getAffectionTitle()
        tvDaysCount.text = "相伴 ${am.getDaysSinceFirstUse()} 天"

        val persona = personaManager.getPersona(personaId)
        tvAiName.text = persona?.name ?: "星尘"

        val userCall = getSharedPreferences("persona_data_$personaId", MODE_PRIVATE)
            .getString("user_nickname", null)
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("user_call_name", "") ?: ""
        tvUserName.text = userCall.ifEmpty { "主人" }

        val unlocked = achievementManager.getUnlocked()
        tvAchCount.text = "${unlocked.size} 成就"
        recyclerAchievements.adapter = AchievementAdapter(unlocked)

        val diaryCount = diaryManager.getDiaryCount()
        tvDiaryCount.text = "$diaryCount 日记"

        loadAvatars()
        loadMoments()
        loadFavorites()
        loadStats()
    }

    private fun loadStats() {
        val stats = statsManager
        findViewById<TextView>(R.id.tv_stat_total_msgs)?.text = formatNumber(stats.totalMessages)
        findViewById<TextView>(R.id.tv_stat_chat_days)?.text = stats.totalChatDays.toString()
        findViewById<TextView>(R.id.tv_stat_streak)?.text = stats.currentStreak.toString()
        findViewById<TextView>(R.id.tv_stat_user_msgs)?.text = formatNumber(stats.userMessages)
        findViewById<TextView>(R.id.tv_stat_ai_msgs)?.text = formatNumber(stats.aiMessages)
        findViewById<TextView>(R.id.tv_stat_stickers)?.text = formatNumber(stats.stickersSent + stats.stickersReceived)
        val totalWords = stats.totalWordsUser + stats.totalWordsAi
        findViewById<TextView>(R.id.tv_stat_words)?.text = formatNumber(totalWords)
        findViewById<TextView>(R.id.tv_stat_avg_day)?.text = String.format("%.1f", stats.getAvgMessagesPerDay())
        val peakHour = stats.getPeakChatHour()
        findViewById<TextView>(R.id.tv_stat_peak_hour)?.text = if (peakHour >= 0) "${peakHour}:00" else "--"
    }

    private fun formatNumber(n: Long): String {
        return when {
            n >= 10000 -> String.format("%.1fw", n / 10000.0)
            n >= 1000 -> String.format("%.1fk", n / 1000.0)
            else -> n.toString()
        }
    }

    private fun formatNumber(n: Int): String = formatNumber(n.toLong())

    private fun loadAvatars() {
        val persona = personaManager.getPersona(personaId)
        val aiPath = persona?.avatarPath?.ifBlank {
            getSharedPreferences("avatar_data", MODE_PRIVATE).getString("ai_avatar", "") ?: ""
        } ?: ""

        if (aiPath.isNotEmpty() && File(aiPath).exists()) {
            ivAiAvatar.setImageBitmap(BitmapFactory.decodeFile(aiPath))
        }

        val userPath = getSharedPreferences("avatar_data", MODE_PRIVATE)
            .getString("user_avatar", "") ?: ""
        if (userPath.isNotEmpty() && File(userPath).exists()) {
            ivUserAvatar.setImageBitmap(BitmapFactory.decodeFile(userPath))
        }
    }

    private fun pickAvatar(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try { startActivityForResult(intent, requestCode) } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return

        val uri = data.data!!
        try {
            if (requestCode == REQUEST_AI_AVATAR) {
                val dir = File(filesDir, "personas/avatars")
                dir.mkdirs()
                val file = File(dir, "avatar_${personaId}_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                personaManager.updatePersona(personaId) {
                    it.copy(avatarPath = file.absolutePath)
                }
                ivAiAvatar.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            } else {
                val file = File(filesDir, "user_avatar_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                getSharedPreferences("avatar_data", MODE_PRIVATE).edit()
                    .putString("user_avatar", file.absolutePath).apply()
                ivUserAvatar.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "设置头像失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadMoments() {
        val moments = momentsManager.getAll()
        if (moments.isEmpty()) {
            tvMomentsEmpty.visibility = View.VISIBLE
            return
        }
        tvMomentsEmpty.visibility = View.GONE

        containerMoments.removeAllViews()
        moments.forEach { moment ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
                setCardBackgroundColor(0xFF1a1a3e.toInt())
                radius = 24f
                strokeWidth = 1
                strokeColor = 0xFF2a2a5a.toInt()
                cardElevation = 1f
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 16, 20, 16)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            TextView(this).apply {
                text = when (moment.category) {
                    "habit" -> "🕐 习惯"
                    "preference" -> "💗 喜好"
                    "impression" -> "🌟 印象"
                    "detail" -> "📌 细节"
                    else -> "💫 记忆"
                }
                textSize = 12f
                setTextColor(0xFFc4b5fd.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
            }.let { header.addView(it) }

            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }.let { header.addView(it) }

            val scoreLabel = "★★★★★★★★★★".take(moment.score) + "☆☆☆☆☆☆☆☆☆☆".drop(moment.score)
            TextView(this).apply {
                text = scoreLabel
                textSize = 10f
                setTextColor(0xFFff6b9d.toInt())
            }.let { header.addView(it) }

            content.addView(header)

            val tvContent = TextView(this)
            tvContent.text = moment.content
            tvContent.textSize = 14f
            tvContent.setTextColor(0xFFe8e8f0.toInt())
            tvContent.setLineSpacing(4f, 1f)
            tvContent.setPadding(0, 10, 0, 0)
            content.addView(tvContent)

            card.setOnLongClickListener {
                android.app.AlertDialog.Builder(this@ProfileActivity)
                    .setTitle("删除该记忆")
                    .setMessage("确定要删除这条记忆吗？")
                    .setPositiveButton("删除") { _, _ ->
                        momentsManager.deleteMoment(moment.id)
                        loadMoments()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            card.addView(content)
            containerMoments.addView(card)
        }
    }

    private fun loadFavorites() {
        val favorites = favoriteManager.getAll()
        if (favorites.isEmpty()) {
            tvFavoritesEmpty.visibility = View.VISIBLE
            containerFavorites.visibility = View.GONE
            return
        }
        tvFavoritesEmpty.visibility = View.GONE
        containerFavorites.visibility = View.VISIBLE
        containerFavorites.removeAllViews()

        favorites.forEach { msg ->
            val cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_favorite_message, containerFavorites, false)
            val card = cardView as com.google.android.material.card.MaterialCardView

            val tvRole = card.findViewById<TextView>(R.id.tv_fav_role_label)
            val tvTime = card.findViewById<TextView>(R.id.tv_fav_time)
            val tvContent = card.findViewById<TextView>(R.id.tv_fav_content)
            val tvReaction = card.findViewById<TextView>(R.id.tv_fav_reaction)

            tvRole.text = if (msg.isUser) "我" else "AI"
            tvRole.setTextColor(if (msg.isUser) 0xFFff6b9d.toInt() else 0xFFc4b5fd.toInt())
            tvTime.text = msg.time
            tvContent.text = msg.text
            if (msg.reactionEmoji.isNotEmpty()) {
                tvReaction.text = msg.reactionEmoji
                tvReaction.visibility = View.VISIBLE
            }

            card.setOnLongClickListener {
                android.app.AlertDialog.Builder(this)
                    .setTitle("取消收藏")
                    .setMessage("确定要取消收藏这条消息吗？")
                    .setPositiveButton("确定") { _, _ ->
                        favoriteManager.removeFavorite(msg.id)
                        loadFavorites()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            containerFavorites.addView(cardView)
        }
    }

    private inner class AchievementAdapter(private val achievements: List<com.aicompanion.models.Achievement>) :
        RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ach = achievements[position]
            holder.bind(ach)
        }

        override fun getItemCount(): Int = achievements.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tv: TextView = view.findViewById(android.R.id.text1)

            fun bind(ach: com.aicompanion.models.Achievement) {
                tv.text = "${ach.icon}  ${ach.title}"
                tv.setTextColor(0xFFe8e8f0.toInt())
                tv.textSize = 14f
                tv.setPadding(8, 8, 8, 8)
            }
        }
    }
}
