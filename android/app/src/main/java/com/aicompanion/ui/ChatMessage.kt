package com.aicompanion.ui

import com.aicompanion.models.Emotion
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val time: String,
    val isUser: Boolean,
    val userMood: String = "",
    var feedback: Int = 0,
    val emotion: Emotion = Emotion.NEUTRAL,
    val timestamp: Long = System.currentTimeMillis(),
    var isPartial: Boolean = false,
    var isFavorited: Boolean = false,
    var reactionEmoji: String = "",
    val stickerPath: String? = null,
    val generatedImagePath: String? = null,
    val imageUrls: List<String> = emptyList(),
    var audioPath: String? = null,
    var audioUrl: String? = null,
    /** 引用回复：被引用的原消息（null 表示非引用消息） */
    val replyTo: ChatMessage? = null,
)
