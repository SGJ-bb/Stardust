package com.aicompanion.models

object EmotionActionMapper {

    fun getDefaultAction(emotion: Emotion): Action {
        return when (emotion) {
            Emotion.HAPPY -> Action.TAIL_FLICK
            Emotion.ANGRY -> Action.EAR_TWITCH
            Emotion.SAD -> Action.BLUSH
            Emotion.SURPRISED -> Action.STRETCH
            Emotion.TSUNDERE -> Action.EAR_TWITCH
            Emotion.NEUTRAL -> Action.IDLE
        }
    }

    fun getEmotionFromText(text: String): Emotion {
        val lower = text.lowercase()
        return when {
            lower.contains("开心") || lower.contains("高兴") || lower.contains("哈哈") -> Emotion.HAPPY
            lower.contains("生气") || lower.contains("愤怒") || lower.contains("哼") -> Emotion.ANGRY
            lower.contains("伤心") || lower.contains("难过") || lower.contains("哭") -> Emotion.SAD
            lower.contains("惊讶") || lower.contains("啊") || lower.contains("哇") -> Emotion.SURPRISED
            lower.contains("傲娇") || lower.contains("笨蛋") || lower.contains("哼") -> Emotion.TSUNDERE
            else -> Emotion.NEUTRAL
        }
    }
}
