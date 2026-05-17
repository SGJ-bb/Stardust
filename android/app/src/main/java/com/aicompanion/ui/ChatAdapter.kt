package com.aicompanion.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import java.io.File

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var aiAvatarOverride: String? = null

    private val animatedPositions = mutableSetOf<Int>()

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_PET = 2
        private const val VIEW_TYPE_TYPING = 3
        private const val CORNER_RADIUS_DP = 18f

        private val avatarCache = LruCache<String, Bitmap>(12)

        fun parseGradientColors(gradientStr: String): List<Int> {
            if (gradientStr.isBlank()) return emptyList()
            val colors = mutableListOf<Int>()
            val regex = "#[0-9a-fA-F]{6}".toRegex()
            regex.findAll(gradientStr).forEach { match ->
                try {
                    colors.add(Color.parseColor(match.value))
                } catch (_: Exception) {
                }
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

        fun applyBubbleWithSkin(bubble: View, skin: com.aicompanion.theme.BubbleSkin, isUser: Boolean) {
            com.aicompanion.theme.BubbleSkinManager.applyBubbleSkin(bubble, skin, isUser)
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

        private fun loadAvatarBitmap(path: String): Bitmap? {
            var bitmap = avatarCache.get(path)
            if (bitmap != null) return bitmap
            return try {
                val file = File(path)
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    bitmap = BitmapFactory.decodeFile(path, options)
                    if (bitmap != null) avatarCache.put(path, bitmap)
                    bitmap
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private var bubbleUserColor = Color.parseColor("#ff6b9d")
    private var bubbleAIColor = Color.parseColor("#BB4a2a3a")
    private var userGradientColors: List<Int> = listOf(bubbleUserColor)
    private val cornerRadius: Float
    private var cachedGradientDrawable: GradientDrawable? = null
    private var cachedGradientColors: List<Int>? = null

    var onFeedback: ((Int, Boolean) -> Unit)? = null
    var onDeleteMessage: ((Int) -> Unit)? = null
    var onQuoteMessage: ((Int) -> Unit)? = null
    var onFavoriteMessage: ((Int) -> Unit)? = null
    var onReactionMessage: ((Int, String) -> Unit)? = null

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

    fun updateLastPetMessage(text: String, isPartial: Boolean = false) {
        val lastPetIndex = messages.indexOfLast { !it.isUser }
        if (lastPetIndex >= 0) {
            messages[lastPetIndex] = messages[lastPetIndex].copy(text = text, isPartial = isPartial)
            notifyItemChanged(lastPetIndex)
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
            cachedGradientDrawable = null
            cachedGradientColors = null
            animatedPositions.clear()
            notifyDataSetChanged()
        }
    }

    private fun resolvePosition(messageId: String): Int {
        return messages.indexOfFirst { it.id == messageId }
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
                    holder.bindGradient(message, userGradientColors, cornerRadius, position)
                } else {
                    holder.bind(message, bubbleUserColor, cornerRadius, position)
                }
            }

            is PetViewHolder -> {
                val message = messages[position]
                holder.bind(message, bubbleAIColor, cornerRadius, position)
            }

            is TypingViewHolder -> holder.bind(bubbleAIColor, cornerRadius)
        }

        if (!animatedPositions.contains(position)) {
            animatedPositions.add(position)
            val isUser = position < messages.size && messages[position].isUser
            com.aicompanion.anim.AnimeUtils.chatMessageIn(holder.itemView, isUser, position)
        }
    }

    override fun getItemCount(): Int = messages.size + if (showTyping) 1 else 0

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        com.aicompanion.anim.AnimeUtils.clearAnimations(holder.itemView)
        if (holder is TypingViewHolder) {
            holder.clearAnimations()
        }
    }

    private fun safeGetLocation(view: View): IntArray? {
        return try {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            loc
        } catch (_: Exception) {
            null
        }
    }

    private fun showPopupMenu(anchorView: View, position: Int, message: ChatMessage) {
        val messageId = message.id
        val context = anchorView.context
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_chat_action, null)

        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = 12f
            isOutsideTouchable = true
            isFocusable = true
        }

        val favIcon = popupView.findViewById<TextView>(R.id.tv_favorite_icon)
        val favLabel = popupView.findViewById<TextView>(R.id.tv_favorite_label)
        if (message.isFavorited) {
            favIcon.text = "⭐"
            favLabel.text = "取消收藏"
            favLabel.setTextColor(0xFFFFB347.toInt())
        } else {
            favIcon.text = "☆"
            favLabel.text = "收藏"
            favLabel.setTextColor(0xFFe0e0f0.toInt())
        }

        popupView.findViewById<View>(R.id.action_favorite).setOnClickListener {
            val pos = resolvePosition(messageId)
            if (pos >= 0) onFavoriteMessage?.invoke(pos)
            popup.dismiss()
        }

        popupView.findViewById<View>(R.id.action_reaction).setOnClickListener {
            popup.dismiss()
            val pos = resolvePosition(messageId)
            if (pos >= 0) showEmojiPicker(anchorView, pos, messageId)
        }

        popupView.findViewById<View>(R.id.action_delete).setOnClickListener {
            popup.dismiss()
            android.app.AlertDialog.Builder(context)
                .setTitle("删除消息")
                .setMessage("确定删除这条消息吗？")
                .setPositiveButton("删除") { _, _ ->
                    val pos = resolvePosition(messageId)
                    if (pos >= 0) onDeleteMessage?.invoke(pos)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        popupView.findViewById<View>(R.id.action_quote).setOnClickListener {
            val pos = resolvePosition(messageId)
            if (pos >= 0) onQuoteMessage?.invoke(pos)
            popup.dismiss()
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val anchorLoc = safeGetLocation(anchorView) ?: run {
            try { popup.showAtLocation(anchorView, Gravity.CENTER, 0, 0) } catch (_: Exception) {}
            return
        }
        val anchorCenterX = anchorLoc[0] + anchorView.width / 2
        val anchorTop = anchorLoc[1]

        val popupW = popupView.measuredWidth
        val popupH = popupView.measuredHeight

        val screenW = context.resources.displayMetrics.widthPixels
        val x = (anchorCenterX - popupW / 2).coerceIn(8, screenW - popupW - 8)
        val y = anchorTop - popupH - 8

        try {
            popup.showAtLocation(anchorView, Gravity.TOP or Gravity.START, x, y)
        } catch (_: Exception) {}
    }

    private fun showEmojiPicker(anchorView: View, position: Int, messageId: String) {
        val anchorLoc = safeGetLocation(anchorView)
        if (anchorLoc == null) return

        val context = anchorView.context
        val pickerView = LayoutInflater.from(context).inflate(R.layout.popup_emoji_reaction, null)

        val popup = PopupWindow(
            pickerView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = 12f
            isOutsideTouchable = true
            isFocusable = true
        }

        val emojiMap = mapOf(
            R.id.emoji_love to "❤️",
            R.id.emoji_laugh to "😂",
            R.id.emoji_wow to "😮",
            R.id.emoji_cry to "😢",
            R.id.emoji_angry to "😡",
            R.id.emoji_thumbsup to "👍",
            R.id.emoji_clap to "👏",
            R.id.emoji_party to "🎉",
            R.id.emoji_remove to ""
        )

        emojiMap.forEach { (viewId, emoji) ->
            pickerView.findViewById<View>(viewId).setOnClickListener {
                val pos = resolvePosition(messageId)
                if (pos >= 0) onReactionMessage?.invoke(pos, emoji)
                popup.dismiss()
            }
        }

        pickerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val anchorCenterX = anchorLoc[0] + anchorView.width / 2
        val anchorTop = anchorLoc[1]

        val popupW = pickerView.measuredWidth
        val popupH = pickerView.measuredHeight

        val screenW = context.resources.displayMetrics.widthPixels
        val x = (anchorCenterX - popupW / 2).coerceIn(8, screenW - popupW - 8)
        val y = anchorTop - popupH - 8

        try {
            popup.showAtLocation(anchorView, Gravity.TOP or Gravity.START, x, y)
        } catch (_: Exception) {}
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: View = view.findViewById(R.id.bubble_user)
        private val tvMessage: TextView = view.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = view.findViewById(R.id.tv_message_time)
        private val tvMoodLabel: TextView = view.findViewById(R.id.tv_mood_label)
        private val ivAvatar: ImageView = view.findViewById(R.id.iv_user_avatar_img)
        private val tvReaction: TextView = view.findViewById(R.id.tv_reaction_badge)
        private var currentPosition = -1

        fun bind(message: ChatMessage, color: Int, radius: Float, position: Int) {
            currentPosition = position
            tvMessage.text = message.text
            if (!message.stickerPath.isNullOrEmpty()) {
                try {
                    val file = java.io.File(message.stickerPath!!)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(message.stickerPath, BitmapFactory.Options().apply { inSampleSize = 2 })
                        tvMessage.visibility = View.GONE
                        val stickerIv = itemView.findViewWithTag<ImageView>("sticker_iv") ?: ImageView(itemView.context).apply {
                            tag = "sticker_iv"
                            val parent = tvMessage.parent as ViewGroup
                            val index = parent.indexOfChild(tvMessage)
                            val density = itemView.context.resources.displayMetrics.density
                            val sizePx = (120 * density).toInt()
                            val lp = LinearLayout.LayoutParams(sizePx, sizePx)
                            parent.addView(this, index, lp)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setPadding(0, 4, 0, 4)
                        }
                        stickerIv.setImageBitmap(bmp)
                        stickerIv.visibility = View.VISIBLE
                    }
                } catch (_: Exception) {}
            } else {
                tvMessage.visibility = View.VISIBLE
                val stickerIv = itemView.findViewWithTag<ImageView>("sticker_iv")
                stickerIv?.visibility = View.GONE
            }
            tvTime.text = message.time
            bindMood(message.userMood)
            bindReaction(message)
            applyBubbleColor(bubble, color, radius)
            loadUserAvatar()
            val imageBubble = com.aicompanion.theme.BubbleSkinManager.getActiveImageBubble(itemView.context)
            if (imageBubble != null) {
                com.aicompanion.theme.BubbleSkinManager.applyImageBubbleSkin(bubble, itemView.context, imageBubble)
            } else {
                val userSkin = com.aicompanion.theme.BubbleSkinManager.getActiveSkin(itemView.context)
                com.aicompanion.theme.BubbleSkinManager.applyBubbleSkin(bubble, userSkin, true)
            }
            val userImageFrame = com.aicompanion.theme.BubbleSkinManager.getActiveUserImageFrame(itemView.context)
            val userAvatarCard = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.iv_user_avatar_chat)
            val userFrameLayout = itemView.findViewById<android.widget.FrameLayout>(R.id.frame_user_avatar)
            if (userImageFrame != null && userFrameLayout != null) {
                com.aicompanion.theme.BubbleSkinManager.applyImageAvatarFrame(userFrameLayout, itemView.context, userImageFrame)
            } else {
                userFrameLayout?.let { com.aicompanion.theme.BubbleSkinManager.clearImageAvatarFrame(it) }
                val userFrame = com.aicompanion.theme.BubbleSkinManager.getActiveUserFrame(itemView.context)
                if (userAvatarCard != null) com.aicompanion.theme.BubbleSkinManager.applyAvatarFrame(userAvatarCard, userFrame)
            }
            bubble.setOnLongClickListener {
                showPopupMenu(bubble, currentPosition, message)
                true
            }
        }

        fun bindGradient(message: ChatMessage, colors: List<Int>, radius: Float, position: Int) {
            currentPosition = position
            tvMessage.text = message.text
            if (!message.stickerPath.isNullOrEmpty()) {
                try {
                    val file = java.io.File(message.stickerPath!!)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(message.stickerPath, BitmapFactory.Options().apply { inSampleSize = 2 })
                        tvMessage.visibility = View.GONE
                        val stickerIv = itemView.findViewWithTag<ImageView>("sticker_iv") ?: ImageView(itemView.context).apply {
                            tag = "sticker_iv"
                            val parent = tvMessage.parent as ViewGroup
                            val index = parent.indexOfChild(tvMessage)
                            val density = itemView.context.resources.displayMetrics.density
                            val sizePx = (120 * density).toInt()
                            val lp = LinearLayout.LayoutParams(sizePx, sizePx)
                            parent.addView(this, index, lp)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setPadding(0, 4, 0, 4)
                        }
                        stickerIv.setImageBitmap(bmp)
                        stickerIv.visibility = View.VISIBLE
                    }
                } catch (_: Exception) {}
            } else {
                tvMessage.visibility = View.VISIBLE
                val stickerIv = itemView.findViewWithTag<ImageView>("sticker_iv")
                stickerIv?.visibility = View.GONE
            }
            tvTime.text = message.time
            bindMood(message.userMood)
            bindReaction(message)
            if (cachedGradientDrawable != null && cachedGradientColors == colors) {
                bubble.background = cachedGradientDrawable
            } else {
                cachedGradientDrawable = createGradientBubbleDrawable(colors, radius)
                cachedGradientColors = colors
                bubble.background = cachedGradientDrawable
            }
            loadUserAvatar()
            val gradientSkin = com.aicompanion.theme.BubbleSkinManager.getActiveSkin(itemView.context)
            com.aicompanion.theme.BubbleSkinManager.applyBubbleSkin(bubble, gradientSkin, true)
            val gradUserFrame = com.aicompanion.theme.BubbleSkinManager.getActiveUserFrame(itemView.context)
            val gradUserAvatarCard = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.iv_user_avatar_chat)
            if (gradUserAvatarCard != null) com.aicompanion.theme.BubbleSkinManager.applyAvatarFrame(gradUserAvatarCard, gradUserFrame)
            bubble.setOnLongClickListener {
                showPopupMenu(bubble, currentPosition, message)
                true
            }
        }

        private fun bindReaction(message: ChatMessage) {
            if (message.reactionEmoji.isNotEmpty()) {
                tvReaction.text = message.reactionEmoji
                tvReaction.visibility = View.VISIBLE
            } else {
                tvReaction.visibility = View.GONE
            }
        }

        private fun loadUserAvatar() {
            try {
                val prefs = itemView.context.getSharedPreferences("avatar_data", 0)
                val path = prefs.getString("user_avatar", "")
                if (!path.isNullOrEmpty()) {
                    loadAvatarBitmap(path)?.let { ivAvatar.setImageBitmap(it) }
                }
            } catch (_: Exception) {
            }
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
        private val tvReaction: TextView = view.findViewById(R.id.tv_reaction_badge)

        fun bind(message: ChatMessage, color: Int, radius: Float, position: Int) {
            tvMessage.text = if (message.isPartial) "${message.text}…" else message.text
            if (!message.stickerPath.isNullOrEmpty()) {
                try {
                    val file = java.io.File(message.stickerPath!!)
                    if (file.exists()) {
                        val bmp = BitmapFactory.decodeFile(message.stickerPath, BitmapFactory.Options().apply { inSampleSize = 2 })
                        tvMessage.visibility = View.GONE
                        val stickerIv = itemView.findViewWithTag<ImageView>("sticker_iv") ?: ImageView(itemView.context).apply {
                            tag = "sticker_iv"
                            val parent = tvMessage.parent as ViewGroup
                            val index = parent.indexOfChild(tvMessage)
                            val density = itemView.context.resources.displayMetrics.density
                            val sizePx = (120 * density).toInt()
                            val lp = LinearLayout.LayoutParams(sizePx, sizePx)
                            parent.addView(this, index, lp)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setPadding(0, 4, 0, 4)
                        }
                        stickerIv.setImageBitmap(bmp)
                        stickerIv.visibility = View.VISIBLE
                    }
                } catch (_: Exception) {}
            } else {
                tvMessage.visibility = View.VISIBLE
                val stickerIv = itemView.findViewWithTag<ImageView>("sticker_iv")
                stickerIv?.visibility = View.GONE
            }
            tvTime.text = message.time
            tvEmotion.visibility = View.GONE
            currentPosition = position
            bindReaction(message)
            applyBubbleColor(bubble, color, radius)
            loadAiAvatar()

            val imageBubble = com.aicompanion.theme.BubbleSkinManager.getActiveImageBubble(itemView.context)
            if (imageBubble != null) {
                com.aicompanion.theme.BubbleSkinManager.applyImageBubbleSkin(bubble, itemView.context, imageBubble)
            } else {
                val skin = com.aicompanion.theme.BubbleSkinManager.getActiveSkin(itemView.context)
                com.aicompanion.theme.BubbleSkinManager.applyBubbleSkin(bubble, skin, false)
            }
            val aiImageFrame = com.aicompanion.theme.BubbleSkinManager.getActiveAiImageFrame(itemView.context)
            val aiAvatarCard = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.iv_ai_avatar_chat)
            val aiFrameLayout = itemView.findViewById<android.widget.FrameLayout>(R.id.frame_ai_avatar)
            if (aiImageFrame != null && aiFrameLayout != null) {
                com.aicompanion.theme.BubbleSkinManager.applyImageAvatarFrame(aiFrameLayout, itemView.context, aiImageFrame)
            } else {
                aiFrameLayout?.let { com.aicompanion.theme.BubbleSkinManager.clearImageAvatarFrame(it) }
                val aiFrame = com.aicompanion.theme.BubbleSkinManager.getActiveAiFrame(itemView.context)
                if (aiAvatarCard != null) com.aicompanion.theme.BubbleSkinManager.applyAvatarFrame(aiAvatarCard, aiFrame)
            }

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
                showPopupMenu(bubble, currentPosition, message)
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

        private fun bindReaction(message: ChatMessage) {
            if (message.reactionEmoji.isNotEmpty()) {
                tvReaction.text = message.reactionEmoji
                tvReaction.visibility = View.VISIBLE
            } else {
                tvReaction.visibility = View.GONE
            }
        }

        private fun loadAiAvatar() {
            try {
                val overridePath = this@ChatAdapter.aiAvatarOverride
                if (!overridePath.isNullOrEmpty() && java.io.File(overridePath).exists()) {
                    loadAvatarBitmap(overridePath)?.let { ivAvatar.setImageBitmap(it) }
                    return
                }
                val prefs = itemView.context.getSharedPreferences("avatar_data", 0)
                val path = prefs.getString("ai_avatar", "")
                if (!path.isNullOrEmpty()) {
                    loadAvatarBitmap(path)?.let { ivAvatar.setImageBitmap(it) }
                }
            } catch (_: Exception) {
            }
        }

        private fun animateFeedback(active: TextView, inactive: TextView, isLike: Boolean) {
            active.animate()
                .scaleX(1.4f)
                .scaleY(1.4f)
                .setDuration(150)
                .setInterpolator(com.aicompanion.anim.AnimeInterpolators.easeOutBack)
                .withEndAction {
                    active.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(com.aicompanion.anim.AnimeInterpolators.easeOutElastic)
                        .start()
                }
                .start()
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

        fun clearAnimations() {
            dot1.clearAnimation()
            dot2.clearAnimation()
            dot3.clearAnimation()
        }

        private fun startDotAnimation() {
            val dots = listOf(dot1, dot2, dot3)
            dots.forEachIndexed { index, dot ->
                dot.clearAnimation()
                val scaleUp = android.view.animation.ScaleAnimation(
                    0.5f, 1.2f, 0.5f, 1.2f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 300
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                    startOffset = (index * 180).toLong()
                    interpolator = android.view.animation.OvershootInterpolator(1.5f)
                }
                val alphaAnim = AlphaAnimation(0.2f, 1.0f).apply {
                    duration = 500
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                    startOffset = (index * 180).toLong()
                }
                dot.startAnimation(scaleUp)
                dot.startAnimation(alphaAnim)
            }
        }
    }
}