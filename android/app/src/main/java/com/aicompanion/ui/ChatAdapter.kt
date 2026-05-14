/** 聊天消息适配器: 用户/AI消息不同布局, 支持心情头像/时间戳/打字指示器 */
package com.aicompanion.ui

import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import java.io.File

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_PET = 2
        private const val VIEW_TYPE_TYPING = 3
        private const val CORNER_RADIUS_DP = 18f

        fun parseGradientColors(gradientStr: String): List<Int> {
            if (gradientStr.isBlank()) return emptyList()
            val colors = mutableListOf<Int>()
            val regex = "#[0-9a-fA-F]{6}".toRegex()
            regex.findAll(gradientStr).forEach { match ->
                try { colors.add(Color.parseColor(match.value)) }
                catch (_: Exception) {}
            }
            return colors
        }

        fun applyBubbleColor(bubble: View, color: Int, radius: Float) {
            if (bubble.background is GradientDrawable) {
                (bubble.background as GradientDrawable).apply {
                    setColor(color)
                    cornerRadius = radius
                }
            } else {
                bubble.background = createBubbleDrawable(color, radius)
            }
        }

        private fun createBubbleDrawable(color: Int, radius: Float): GradientDrawable {
            return GradientDrawable().apply {
                setColor(color)
                this.cornerRadius = radius
            }
        }

        fun createGradientBubbleDrawable(gradientColors: List<Int>, radius: Float): GradientDrawable {
            if (gradientColors.size < 2) {
                return createBubbleDrawable(gradientColors.firstOrNull() ?: Color.GRAY, radius)
            }
            return GradientDrawable(GradientDrawable.Orientation.TL_BR, gradientColors.toIntArray()).apply {
                this.cornerRadius = radius
            }
        }
    }

    private var bubbleUserColor = Color.parseColor("#ff6b9d")
    private var bubbleAIColor = Color.parseColor("#BB4a2a3a")
    private var userGradientColors: List<Int> = listOf(bubbleUserColor)
    private val cornerRadius: Float

    var onFeedback: ((Int, Boolean) -> Unit)? = null
    var onDeleteMessage: ((Int) -> Unit)? = null
    private var showTyping = false

    init {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        cornerRadius = CORNER_RADIUS_DP * density
    }

    fun setTypingIndicator(show: Boolean) {
        if (showTyping != show) {
            if (show) {
                showTyping = true
                notifyItemInserted(messages.size)
            } else {
                val removePos = messages.size
                showTyping = false
                notifyItemRemoved(removePos)
            }
        }
    }

    fun applyTheme(userColor: Int, aiColor: Int) {
        applyThemeWithGradient(userColor, aiColor, emptyList())
    }

    fun applyThemeWithGradient(userColor: Int, aiColor: Int, gradientColors: List<Int>) {
        if (bubbleUserColor != userColor || bubbleAIColor != aiColor) {
            bubbleUserColor = userColor
            bubbleAIColor = aiColor
            userGradientColors = gradientColors
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (showTyping && position == messages.size) return VIEW_TYPE_TYPING
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_PET
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_user, parent, false)
                UserViewHolder(view)
            }
            VIEW_TYPE_PET -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_pet, parent, false)
                PetViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_typing_indicator, parent, false)
                TypingViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> {
                val message = messages[position]
                if (userGradientColors.size >= 2) {
                    holder.bindGradient(message, userGradientColors, cornerRadius)
                } else {
                    holder.bind(message, bubbleUserColor, cornerRadius)
                }
            }
            is PetViewHolder -> {
                val message = messages[position]
                holder.bind(message, bubbleAIColor, cornerRadius, position)
            }
            is TypingViewHolder -> holder.bind(bubbleAIColor, cornerRadius)
        }
    }

    override fun getItemCount(): Int = messages.size + if (showTyping) 1 else 0

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: View = view.findViewById(R.id.bubble_user)
        private val tvMessage: TextView = view.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = view.findViewById(R.id.tv_message_time)
        private val tvMoodLabel: TextView = view.findViewById(R.id.tv_mood_label)
        private val ivAvatar: ImageView = view.findViewById(R.id.iv_user_avatar_img)

        fun bind(message: ChatMessage, color: Int, radius: Float) {
            tvMessage.text = message.text
            tvTime.text = message.time
            bindMood(message.userMood)
            applyBubbleColor(bubble, color, radius)
            loadUserAvatar()
            bubble.setOnLongClickListener {
                onDeleteMessage?.invoke(adapterPosition)
                true
            }
        }

        fun bindGradient(message: ChatMessage, colors: List<Int>, radius: Float) {
            tvMessage.text = message.text
            tvTime.text = message.time
            bindMood(message.userMood)
            bubble.background = createGradientBubbleDrawable(colors, radius)
            loadUserAvatar()
            bubble.setOnLongClickListener {
                onDeleteMessage?.invoke(adapterPosition)
                true
            }
        }

        private fun loadUserAvatar() {
            try {
                val prefs = itemView.context.getSharedPreferences("avatar_data", 0)
                val path = prefs.getString("user_avatar", "")
                if (!path.isNullOrEmpty()) {
                    val file = File(path)
                    if (file.exists()) {
                        ivAvatar.setImageBitmap(BitmapFactory.decodeFile(path))
                    }
                }
            } catch (_: Exception) {}
        }

        private fun bindMood(mood: String) {
            if (mood.isNotEmpty()) {
                val moodEmojis = mapOf(
                    "开心" to "😊", "难过" to "😢", "生气" to "😤", "疲惫" to "😴",
                    "兴奋" to "🤩", "幸福" to "😍", "焦虑" to "😰", "平静" to "😌"
                )
                val emoji = moodEmojis[mood] ?: "💫"
                tvMoodLabel.text = "$emoji $mood"
                tvMoodLabel.visibility = View.VISIBLE
            } else {
                tvMoodLabel.visibility = View.GONE
            }
        }
    }

    inner class PetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: View = view.findViewById(R.id.bubble_pet)
        private val tvMessage: TextView = view.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = view.findViewById(R.id.tv_message_time)
        private val tvEmotion: TextView = view.findViewById(R.id.tv_emotion_label)
        private val btnLike: TextView = view.findViewById(R.id.btn_feedback_like)
        private val btnDislike: TextView = view.findViewById(R.id.btn_feedback_dislike)
        private var currentPosition = -1
        private val ivAvatar: ImageView = view.findViewById(R.id.iv_ai_avatar_img)

        fun bind(message: ChatMessage, color: Int, radius: Float, position: Int) {
            tvMessage.text = message.text
            tvTime.text = message.time
            tvEmotion.visibility = View.GONE
            currentPosition = position
            applyBubbleColor(bubble, color, radius)
            loadAiAvatar()

            btnLike.alpha = 0f
            btnDislike.alpha = 0f
            btnLike.setOnClickListener {
                onFeedback?.invoke(currentPosition, true)
                animateFeedback(btnLike, btnDislike, true)
            }
            btnDislike.setOnClickListener {
                onFeedback?.invoke(currentPosition, false)
                animateFeedback(btnDislike, btnLike, false)
            }

            bubble.setOnLongClickListener {
                onDeleteMessage?.invoke(currentPosition)
                true
            }

            if (!message.isUser && message.feedback > 0) {
                btnLike.alpha = 1.0f
                btnDislike.alpha = 0f
                btnLike.setTextColor(0xFF4CAF50.toInt())
            } else if (!message.isUser && message.feedback < 0) {
                btnDislike.alpha = 1.0f
                btnLike.alpha = 0f
                btnDislike.setTextColor(0xFFE53935.toInt())
            }
        }

        private fun loadAiAvatar() {
            try {
                val prefs = itemView.context.getSharedPreferences("avatar_data", 0)
                val path = prefs.getString("ai_avatar", "")
                if (!path.isNullOrEmpty()) {
                    val file = File(path)
                    if (file.exists()) {
                        ivAvatar.setImageBitmap(BitmapFactory.decodeFile(path))
                    }
                }
            } catch (_: Exception) {}
        }

        private fun animateFeedback(active: TextView, inactive: TextView, isLike: Boolean) {
            val scaleUp = android.view.animation.ScaleAnimation(
                1f, 1.4f, 1f, 1.4f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 150
                repeatMode = Animation.REVERSE
                repeatCount = 1
            }
            active.startAnimation(scaleUp)
            active.alpha = 1.0f
            active.setTextColor(if (isLike) 0xFF4CAF50.toInt() else 0xFFE53935.toInt())
            inactive.alpha = 0.2f
        }
    }

    class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: View = view.findViewById(R.id.bubble_typing)
        private val dot1: View = view.findViewById(R.id.dot_1)
        private val dot2: View = view.findViewById(R.id.dot_2)
        private val dot3: View = view.findViewById(R.id.dot_3)

        fun bind(color: Int, radius: Float) {
            applyBubbleColor(bubble, color, radius)
            startDotAnimation()
        }

        private fun startDotAnimation() {
            val dots = listOf(dot1, dot2, dot3)
            dots.forEachIndexed { index, dot ->
                val anim = AlphaAnimation(0.2f, 1.0f).apply {
                    duration = 400
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                    startOffset = (index * 200).toLong()
                }
                dot.startAnimation(anim)
            }
        }
    }
}