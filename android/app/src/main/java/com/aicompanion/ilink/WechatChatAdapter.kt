package com.aicompanion.ilink

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aicompanion.R
import com.aicompanion.storage.StoredMessage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class WechatChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
        private const val TIME_GAP_MINUTES = 5
    }

    private val messages = mutableListOf<StoredMessage>()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    var personaId: String = "default"

    fun setMessages(msgs: List<StoredMessage>) {
        messages.clear()
        messages.addAll(msgs)
        notifyDataSetChanged()
    }

    fun appendMessage(msg: StoredMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun getMessageCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_wechat_msg_user, parent, false))
        } else {
            AiViewHolder(inflater.inflate(R.layout.item_wechat_msg_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val showTime = shouldShowTime(position)

        if (holder is UserViewHolder) {
            bindUserMessage(holder, msg, showTime)
        } else if (holder is AiViewHolder) {
            bindAiMessage(holder, msg, showTime)
        }
    }

    override fun getItemCount() = messages.size

    private fun shouldShowTime(position: Int): Boolean {
        if (position == 0) return true
        val prev = messages[position - 1]
        val curr = messages[position]
        return curr.timestamp - prev.timestamp > TimeUnit.MINUTES.toMillis(TIME_GAP_MINUTES.toLong())
    }

    private fun formatTimeLabel(timestamp: Long): String {
        return timeFmt.format(java.util.Date(timestamp))
    }

    private fun bindUserMessage(holder: UserViewHolder, msg: StoredMessage, showTime: Boolean) {
        loadUserAvatar(holder.ivAvatar)
        if (showTime) {
            holder.tvTimeLabel.visibility = View.VISIBLE
            holder.tvTimeLabel.text = formatTimeLabel(msg.timestamp)
        } else {
            holder.tvTimeLabel.visibility = View.GONE
        }

        // 显示文本，图片消息附加标记
        val displayText = buildString {
            append(msg.text)
            if (msg.imageUrls.isNotEmpty()) {
                if (isNotBlank()) append("\n")
                append("[图片]")
            }
        }
        holder.tvText.text = displayText
        holder.ivImage.visibility = View.GONE
    }

    private fun bindAiMessage(holder: AiViewHolder, msg: StoredMessage, showTime: Boolean) {
        loadAiAvatar(holder.ivAvatar)
        if (showTime) {
            holder.tvTimeLabel.visibility = View.VISIBLE
            holder.tvTimeLabel.text = formatTimeLabel(msg.timestamp)
        } else {
            holder.tvTimeLabel.visibility = View.GONE
        }

        // 显示AI角色名
        if (msg.senderName.isNotBlank()) {
            holder.tvAiName.visibility = View.VISIBLE
            holder.tvAiName.text = msg.senderName
        } else {
            holder.tvAiName.visibility = View.GONE
        }

        // 显示文本，图片消息附加标记
        val displayText = buildString {
            append(msg.text)
            if (msg.imageUrls.isNotEmpty()) {
                if (isNotBlank()) append("\n")
                append("[图片]")
            }
        }
        holder.tvText.text = displayText
        holder.ivImage.visibility = View.GONE
    }

    /** 加载用户头像 */
    private fun loadUserAvatar(ivAvatar: ImageView) {
        val path = com.aicompanion.util.AvatarManager.getUserAvatarPath(ivAvatar.context, personaId)
        if (!path.isNullOrBlank()) {
            try { BitmapFactory.decodeFile(path)?.let { if (!it.isRecycled) ivAvatar.setImageBitmap(it) } }
            catch (_: Exception) {}
        }
    }

    /** 加载AI头像 */
    private fun loadAiAvatar(ivAvatar: ImageView) {
        val path = com.aicompanion.util.AvatarManager.getAiAvatarPath(ivAvatar.context, personaId)
        if (!path.isNullOrBlank()) {
            try { BitmapFactory.decodeFile(path)?.let { if (!it.isRecycled) ivAvatar.setImageBitmap(it) } }
            catch (_: Exception) {}
        }
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimeLabel: TextView = view.findViewById(R.id.tv_time_label)
        val tvText: TextView = view.findViewById(R.id.tv_user_text)
        val ivImage: ImageView = view.findViewById(R.id.iv_user_image)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_user_avatar)
        val bubbleUser: LinearLayout = view.findViewById(R.id.bubble_user)
    }

    inner class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimeLabel: TextView = view.findViewById(R.id.tv_time_label)
        val tvAiName: TextView = view.findViewById(R.id.tv_ai_name)
        val tvText: TextView = view.findViewById(R.id.tv_ai_text)
        val ivImage: ImageView = view.findViewById(R.id.iv_ai_image)
        val ivAvatar: ImageView = view.findViewById(R.id.iv_ai_avatar)
        val bubbleAi: LinearLayout = view.findViewById(R.id.bubble_ai)
    }
}
