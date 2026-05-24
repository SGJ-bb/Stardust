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
import com.aicompanion.util.AppLogger
import java.io.File

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var aiAvatarOverride: String? = null

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_PET = 2
        private const val VIEW_TYPE_TYPING = 3

        private val avatarCache = LruCache<String, Bitmap>(12)
        private val stickerCache = LruCache<String, Bitmap>(8)

        private val moodEmojis = mapOf(
            "开心" to "😊", "难过" to "😢", "生气" to "😤", "疲惫" to "😴",
            "兴奋" to "🤩", "幸福" to "😍", "焦虑" to "😰", "平静" to "😌"
        )

        private fun loadStickerBitmap(path: String): Bitmap? {
            var bitmap = stickerCache.get(path)
            if (bitmap != null) return bitmap
            return try {
                val file = File(path)
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    bitmap = BitmapFactory.decodeFile(path, options)
                    if (bitmap != null) stickerCache.put(path, bitmap)
                    bitmap
                } else null
            } catch (_: Exception) { null }
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
            } catch (_: Exception) { null }
        }
    }

    private var bubbleUserColor = Color.parseColor("#ff6b9d")
    private var bubbleAIColor = Color.parseColor("#BB4a2a3a")
    private var userGradientColors: List<Int> = listOf(bubbleUserColor)
    private val density: Float = android.content.res.Resources.getSystem().displayMetrics.density

    private var cachedUserAvatarPath: String? = null
    private var cachedAiAvatarPath: String? = null
    private var avatarPathCached = false

    fun cacheAvatarPaths(userPath: String?, aiPath: String?) {
        cachedUserAvatarPath = userPath
        cachedAiAvatarPath = aiPath
        avatarPathCached = true
    }

    private var cachedUserBubbleBg: GradientDrawable? = null
    private var cachedAiBubbleBg: GradientDrawable? = null
    private var cachedUserFrameBg: GradientDrawable? = null
    private var cachedAiFrameBg: GradientDrawable? = null
    private var cachedUserImageBubbleDrawable: android.graphics.drawable.Drawable? = null
    private var cachedAiImageBubbleDrawable: android.graphics.drawable.Drawable? = null
    private var cachedUserAvatarFrameBmp: Bitmap? = null
    private var cachedAiAvatarFrameBmp: Bitmap? = null

    fun cacheSkinSettings(context: android.content.Context) {
        val skin = com.aicompanion.theme.BubbleSkinManager.getActiveSkin(context)
        val imageBubble = com.aicompanion.theme.BubbleSkinManager.getActiveImageBubble(context)
        val userFrame = com.aicompanion.theme.BubbleSkinManager.getActiveUserFrame(context)
        val aiFrame = com.aicompanion.theme.BubbleSkinManager.getActiveAiFrame(context)
        val userImageFrame = com.aicompanion.theme.BubbleSkinManager.getActiveUserImageFrame(context)
        val aiImageFrame = com.aicompanion.theme.BubbleSkinManager.getActiveAiImageFrame(context)

        cachedUserBubbleBg = GradientDrawable().apply {
            setColor(skin.userBgColor)
            cornerRadius = skin.userCornerRadius * density
            if (skin.userStrokeWidth > 0 && skin.userStrokeColor != Color.TRANSPARENT) {
                setStroke(skin.userStrokeWidth.toInt(), skin.userStrokeColor)
            }
        }

        cachedAiBubbleBg = GradientDrawable().apply {
            setColor(skin.aiBgColor)
            cornerRadius = skin.aiCornerRadius * density
            alpha = skin.aiAlpha
            if (skin.aiStrokeWidth > 0 && skin.aiStrokeColor != Color.TRANSPARENT) {
                setStroke(skin.aiStrokeWidth.toInt(), skin.aiStrokeColor)
            }
        }

        cachedUserFrameBg = if (userFrame.strokeWidth > 0 && userFrame.strokeColor != Color.TRANSPARENT) {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(userFrame.strokeWidth.toInt(), userFrame.strokeColor)
            }
        } else null

        cachedAiFrameBg = if (aiFrame.strokeWidth > 0 && aiFrame.strokeColor != Color.TRANSPARENT) {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(aiFrame.strokeWidth.toInt(), aiFrame.strokeColor)
            }
        } else null

        cachedUserImageBubbleDrawable = if (imageBubble != null) {
            createImageBubbleDrawable(context, imageBubble)
        } else null

        cachedAiImageBubbleDrawable = cachedUserImageBubbleDrawable

        cachedUserAvatarFrameBmp = if (userImageFrame != null) {
            loadAvatarFrameBitmap(context, userImageFrame)
        } else null

        cachedAiAvatarFrameBmp = if (aiImageFrame != null) {
            loadAvatarFrameBitmap(context, aiImageFrame)
        } else null
    }

    private fun createImageBubbleDrawable(
        context: android.content.Context,
        imageSkin: com.aicompanion.theme.ImageBubbleSkin
    ): android.graphics.drawable.Drawable? {
        return try {
            val bmp = com.aicompanion.theme.BubbleSkinManager.loadBitmapFromAsset(context, imageSkin.assetFile) ?: return null
            val chunk = bmp.ninePatchChunk
            if (chunk != null && android.graphics.NinePatch.isNinePatchChunk(chunk)) {
                android.graphics.drawable.NinePatchDrawable(context.resources, bmp, chunk, android.graphics.Rect(), null)
            } else {
                com.aicompanion.theme.BubbleSkinManager.createStretchableDrawable(bmp)
            }
        } catch (e: Exception) {
            AppLogger.e("ChatAdapter", "createImageBubbleDrawable: ${e.message}")
            null
        }
    }

    private fun loadAvatarFrameBitmap(
        context: android.content.Context,
        imageFrame: com.aicompanion.theme.ImageAvatarFrame
    ): Bitmap? {
        return try {
            val bmp = com.aicompanion.theme.BubbleSkinManager.loadBitmapFromAsset(context, imageFrame.assetFile) ?: return null
            com.aicompanion.theme.BubbleSkinManager.maskAvatarFrameCenter(bmp, bmp.width / 2f * 0.65f)
        } catch (e: Exception) {
            AppLogger.e("ChatAdapter", "loadAvatarFrameBitmap: ${e.message}")
            null
        }
    }

    var onFeedback: ((Int, Boolean) -> Unit)? = null
    var onDeleteMessage: ((Int) -> Unit)? = null
    var onQuoteMessage: ((Int) -> Unit)? = null
    var onFavoriteMessage: ((Int) -> Unit)? = null
    var onReactionMessage: ((Int, String) -> Unit)? = null
    var onPlayVoice: ((ChatMessage) -> Unit)? = null
    var ttsManager: com.aicompanion.voice.TtsManager? = null

    private var showTyping = false

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
            notifyItemRangeChanged(0, itemCount)
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
                UserViewHolder(view).also { vh ->
                    applyBubbleBackground(vh.bubble, true)
                    applyAvatarToViewHolder(vh.ivAvatar, vh.frameLayout, true)
                }
            }

            VIEW_TYPE_PET -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_pet, parent, false)
                PetViewHolder(view).also { vh ->
                    applyBubbleBackground(vh.bubble, false)
                    applyAvatarToViewHolder(vh.ivAvatar, vh.aiFrameLayout, false)
                }
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_typing_indicator, parent, false)
                TypingViewHolder(view)
            }
        }
    }

    private fun applyBubbleBackground(bubble: View, isUser: Boolean) {
        val imgDrawable = if (isUser) cachedUserImageBubbleDrawable else cachedAiImageBubbleDrawable
        if (imgDrawable != null) {
            bubble.background = imgDrawable.constantState?.newDrawable()?.mutate() ?: imgDrawable
        } else if (isUser && userGradientColors.size >= 2) {
            bubble.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, userGradientColors.toIntArray()).apply {
                cornerRadius = 18f * density
            }
        } else {
            val bg = if (isUser) cachedUserBubbleBg else cachedAiBubbleBg
            if (bg != null) {
                bubble.background = bg.constantState?.newDrawable()?.mutate() ?: bg
            }
        }
    }

    private fun applyAvatarToViewHolder(ivAvatar: ImageView, frameLayout: android.widget.FrameLayout?, isUser: Boolean) {
        val path = if (avatarPathCached) {
            if (isUser) cachedUserAvatarPath else cachedAiAvatarPath
        } else {
            val prefs = ivAvatar.context.getSharedPreferences("avatar_data", 0)
            prefs.getString(if (isUser) "user_avatar" else "ai_avatar", "")
        }
        if (!path.isNullOrEmpty()) {
            loadAvatarBitmap(path)?.let { ivAvatar.setImageBitmap(it) }
        }

        val frameBmp = if (isUser) cachedUserAvatarFrameBmp else cachedAiAvatarFrameBmp
        if (frameBmp != null && frameLayout != null) {
            val size = frameLayout.layoutParams.width
            val overlaySize = (size * 1.25f).toInt()
            val overlayIv = ImageView(ivAvatar.context).apply {
                tag = "frame_overlay"
                layoutParams = android.widget.FrameLayout.LayoutParams(overlaySize, overlaySize).apply {
                    gravity = android.view.Gravity.CENTER
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(frameBmp)
            }
            frameLayout.addView(overlayIv)
            frameLayout.clipChildren = false
            frameLayout.clipToPadding = false
        } else {
            val frameBg = if (isUser) cachedUserFrameBg else cachedAiFrameBg
            if (frameBg != null) {
                ivAvatar.background = frameBg.constantState?.newDrawable()?.mutate() ?: frameBg
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            when (holder) {
                is UserViewHolder -> holder.bind(messages[position], position)
                is PetViewHolder -> holder.bind(messages[position], position)
                is TypingViewHolder -> holder.bind(bubbleAIColor)
            }
        } else {
            if (holder is PetViewHolder && payloads.contains("audio")) {
                holder.updateVoiceButton(messages[position])
            }
        }
    }

    override fun getItemCount(): Int = messages.size + if (showTyping) 1 else 0

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
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
            try { popup.showAtLocation(anchorView, Gravity.CENTER, 0, 0) } catch (e: Exception) { AppLogger.e("ChatAdapter", "showPopupMenu: ${e.message}") }
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
        } catch (e: Exception) { AppLogger.e("ChatAdapter", "showPopupMenu: ${e.message}") }
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
        } catch (e: Exception) { AppLogger.e("ChatAdapter", "showEmojiPicker: ${e.message}") }
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: View = view.findViewById(R.id.bubble_user)
        private val tvMessage: TextView = view.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = view.findViewById(R.id.tv_message_time)
        private val tvMoodLabel: TextView = view.findViewById(R.id.tv_mood_label)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_user_avatar_chat)
        private val tvReaction: TextView = view.findViewById(R.id.tv_reaction_badge)
        val frameLayout = view.findViewById<android.widget.FrameLayout>(R.id.frame_user_avatar)
        private var stickerIv: ImageView? = null
        private var currentPosition = -1

        init {
            bubble.setOnLongClickListener {
                if (currentPosition >= 0 && currentPosition < messages.size) {
                    showPopupMenu(bubble, currentPosition, messages[currentPosition])
                }
                true
            }
        }

        fun bind(message: ChatMessage, position: Int) {
            currentPosition = position

            if (!message.stickerPath.isNullOrEmpty()) {
                try {
                    val file = File(message.stickerPath!!)
                    if (file.exists()) {
                        val bmp = loadStickerBitmap(message.stickerPath!!)
                        tvMessage.visibility = View.GONE
                        if (stickerIv == null) {
                            stickerIv = ImageView(itemView.context).apply {
                                val parent = tvMessage.parent as ViewGroup
                                val index = parent.indexOfChild(tvMessage)
                                val sizePx = (120 * density).toInt()
                                parent.addView(this, index, LinearLayout.LayoutParams(sizePx, sizePx))
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setPadding(0, 4, 0, 4)
                            }
                        }
                        stickerIv?.setImageBitmap(bmp)
                        stickerIv?.visibility = View.VISIBLE
                    }
                } catch (e: Exception) { AppLogger.e("ChatAdapter", "UserViewHolder.bind: ${e.message}") }
            } else {
                tvMessage.text = message.text
                tvMessage.visibility = View.VISIBLE
                stickerIv?.visibility = View.GONE
            }

            tvTime.text = message.time

            if (message.userMood.isNotEmpty()) {
                val emoji = moodEmojis[message.userMood] ?: "💫"
                tvMoodLabel.text = "$emoji ${message.userMood}"
                tvMoodLabel.visibility = View.VISIBLE
            } else {
                tvMoodLabel.visibility = View.GONE
            }

            if (message.reactionEmoji.isNotEmpty()) {
                tvReaction.text = message.reactionEmoji
                tvReaction.visibility = View.VISIBLE
            } else {
                tvReaction.visibility = View.GONE
            }
        }
    }

    inner class PetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: View = view.findViewById(R.id.bubble_pet)
        private val tvMessage: TextView = view.findViewById(R.id.tv_message_text)
        private val tvTime: TextView = view.findViewById(R.id.tv_message_time)
        private val tvEmotion: TextView = view.findViewById(R.id.tv_emotion_label)
        private val btnLike: TextView = view.findViewById(R.id.btn_feedback_like)
        private val btnDislike: TextView = view.findViewById(R.id.btn_feedback_dislike)
        private var currentPosition = -1
        val ivAvatar: ImageView = view.findViewById(R.id.iv_ai_avatar_chat)
        private val tvReaction: TextView = view.findViewById(R.id.tv_reaction_badge)
        val aiFrameLayout = view.findViewById<android.widget.FrameLayout>(R.id.frame_ai_avatar)
        private var stickerIv: ImageView? = null
        private val voiceBtn: ImageView

        init {
            bubble.setOnLongClickListener {
                if (currentPosition >= 0 && currentPosition < messages.size) {
                    showPopupMenu(bubble, currentPosition, messages[currentPosition])
                }
                true
            }

            voiceBtn = ImageView(itemView.context).apply {
                val sizePx = (28 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins((4 * density).toInt(), 0, 0, 0)
                    gravity = Gravity.CENTER_VERTICAL
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                setImageResource(R.drawable.ic_voice)
                setColorFilter(0xFF81D4FA.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
                val outValue = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
                contentDescription = "播放语音"
                visibility = View.GONE
            }
            try {
                val parent = tvTime.parent as? ViewGroup
                if (parent != null) {
                    val timeIndex = parent.indexOfChild(tvTime)
                    parent.addView(voiceBtn, timeIndex)
                }
            } catch (e: Exception) {
                AppLogger.e("ChatAdapter", "PetViewHolder voiceBtn addView: ${e.message}")
            }

            btnLike.setOnClickListener {
                if (currentPosition >= 0) onFeedback?.invoke(currentPosition, true)
                animateFeedback(btnLike, btnDislike, true)
            }
            btnDislike.setOnClickListener {
                if (currentPosition >= 0) onFeedback?.invoke(currentPosition, false)
                animateFeedback(btnDislike, btnLike, false)
            }
        }

        fun bind(message: ChatMessage, position: Int) {
            currentPosition = position

            if (!message.stickerPath.isNullOrEmpty()) {
                try {
                    val file = File(message.stickerPath!!)
                    if (file.exists()) {
                        val bmp = loadStickerBitmap(message.stickerPath!!)
                        tvMessage.visibility = View.GONE
                        if (stickerIv == null) {
                            stickerIv = ImageView(itemView.context).apply {
                                val parent = tvMessage.parent as ViewGroup
                                val index = parent.indexOfChild(tvMessage)
                                val sizePx = (120 * density).toInt()
                                parent.addView(this, index, LinearLayout.LayoutParams(sizePx, sizePx))
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setPadding(0, 4, 0, 4)
                            }
                        }
                        stickerIv?.setImageBitmap(bmp)
                        stickerIv?.visibility = View.VISIBLE
                    }
                } catch (e: Exception) { AppLogger.e("ChatAdapter", "PetViewHolder.bind: ${e.message}") }
            } else {
                tvMessage.text = if (message.isPartial) "${message.text}…" else message.text
                tvMessage.visibility = View.VISIBLE
                stickerIv?.visibility = View.GONE
            }

            tvTime.text = message.time
            tvEmotion.visibility = View.GONE

            if (message.reactionEmoji.isNotEmpty()) {
                tvReaction.text = message.reactionEmoji
                tvReaction.visibility = View.VISIBLE
            } else {
                tvReaction.visibility = View.GONE
            }

            bindVoiceButton(message)

            btnLike.alpha = 0f
            btnDislike.alpha = 0f

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

        fun updateVoiceButton(message: ChatMessage) {
            bindVoiceButton(message)
        }

        private fun bindVoiceButton(message: ChatMessage) {
            val hasAudio = !message.audioPath.isNullOrBlank() || !message.audioUrl.isNullOrBlank()

            if (hasAudio) {
                voiceBtn.visibility = View.VISIBLE

                val isCurrentlyPlaying = ttsManager?.isPlaying == true &&
                    (ttsManager?.playingPath == message.audioPath || ttsManager?.playingPath == message.audioUrl)
                if (isCurrentlyPlaying) {
                    voiceBtn.setColorFilter(0xFF4FC3F7.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
                    voiceBtn.alpha = 1.0f
                } else {
                    voiceBtn.setColorFilter(0xFF81D4FA.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
                    voiceBtn.alpha = 0.7f
                }

                voiceBtn.setOnClickListener {
                    onPlayVoice?.invoke(message)
                }
            } else {
                voiceBtn.visibility = View.GONE
            }
        }

        private fun animateFeedback(active: TextView, inactive: TextView, isLike: Boolean) {
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

        fun bind(color: Int) {
            if (bubble.background is GradientDrawable) {
                (bubble.background as GradientDrawable).setColor(color)
            } else {
                bubble.background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 18f * android.content.res.Resources.getSystem().displayMetrics.density
                }
            }
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
                val animSet = android.view.animation.AnimationSet(true)
                animSet.addAnimation(scaleUp)
                animSet.addAnimation(alphaAnim)
                dot.startAnimation(animSet)
            }
        }
    }
}
