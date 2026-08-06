package com.aicompanion.ui

import androidx.recyclerview.widget.DiffUtil

/**
 * ChatAdapter 的 DiffUtil.Callback 实现
 *
 * 用于优化 RecyclerView 列表更新，避免全量刷新，只更新发生变化的项
 *
 * 优势：
 * - 性能提升：只更新变化的项，避免全量 notifyDataSetChanged()
 * - 动画流畅：DiffUtil 自动计算插入、删除、移动、更新动画
 * - 线程安全：可在后台线程计算差异，然后在主线程更新 UI
 *
 * 使用方法：
 * ```kotlin
 * fun submitList(newList: List<ChatMessage>) {
 *     val diffResult = DiffUtil.calculateDiff(ChatDiffCallback(messages, newList))
 *     messages = newList.toMutableList()
 *     diffResult.dispatchUpdatesTo(this)
 * }
 * ```
 */
class ChatDiffCallback(
    private val oldList: List<ChatMessage>,
    private val newList: List<ChatMessage>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    /**
     * 判断两个项是否是同一个消息（基于唯一 ID）
     *
     * 使用 ChatMessage.id 作为唯一标识符
     */
    override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos].id == newList[newPos].id
    }

    /**
     * 判断两个项的内容是否完全相同
     *
     * 由于 ChatMessage 是 data class，直接使用 == 比较所有字段
     */
    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos] == newList[newPos]
    }

    /**
     * 获取变化的具体字段（用于部分更新 payload）
     *
     * 返回变化的字段名列表，onBindViewHolder 可根据 payload 进行部分更新
     * 例如：只有文本变化时，只更新文本 TextView，不重新加载图片
     */
    override fun getChangePayload(oldPos: Int, newPos: Int): Any? {
        val oldMsg = oldList[oldPos]
        val newMsg = newList[newPos]

        val changes = mutableListOf<String>()

        if (oldMsg.text != newMsg.text || oldMsg.isPartial != newMsg.isPartial) {
            changes.add("text")
        }
        if (oldMsg.feedback != newMsg.feedback) {
            changes.add("feedback")
        }
        if (oldMsg.reactionEmoji != newMsg.reactionEmoji) {
            changes.add("reaction")
        }
        if (oldMsg.isFavorited != newMsg.isFavorited) {
            changes.add("favorite")
        }
        if (oldMsg.audioPath != newMsg.audioPath || oldMsg.audioUrl != newMsg.audioUrl) {
            changes.add("audio")
        }
        if (oldMsg.stickerPath != newMsg.stickerPath || oldMsg.generatedImagePath != newMsg.generatedImagePath) {
            changes.add("image")
        }

        return if (changes.isEmpty()) null else changes
    }
}