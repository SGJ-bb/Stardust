/** 离线自然语言处理: 离线意图识别和简单对话回复, 无网络时兜底使用 */
package com.aicompanion.nlp

import com.aicompanion.models.Emotion
import com.aicompanion.models.Action

class OfflineNLP {

    private val intentPatterns = mapOf(
        "greeting" to listOf("你好", "哈喽", "嗨", "早上好", "晚上好", "在吗"),
        "farewell" to listOf("再见", "拜拜", "晚安", "走了", "下线"),
        "question" to listOf("为什么", "怎么", "什么", "哪里", "多少", "吗？", "呢？"),
        "compliment" to listOf("可爱", "漂亮", "聪明", "厉害", "棒", "喜欢"),
        "complaint" to listOf("讨厌", "烦", "笨", "傻", "错", "不好"),
        "help" to listOf("帮我", "帮我", "怎么弄", "教教我", "求助"),
        "emotion_positive" to listOf("开心", "高兴", "快乐", "兴奋", "爽"),
        "emotion_negative" to listOf("难过", "伤心", "生气", "烦", "累")
    )

    private val responses = mapOf(
        "greeting" to listOf("喵~ 主人好！", "哈喽！今天想聊什么？", "在的在的，主人有什么吩咐？"),
        "farewell" to listOf("主人再见喵~", "晚安，做个好梦！", "去吧去吧，我会想你的~"),
        "question" to listOf("这个嘛...让我想想喵~", "主人问得好深奥，我不太懂呢", "也许...是这样的？"),
        "compliment" to listOf("哼，算你有眼光喵~", "那当然，我可是最棒的！", "嘿嘿，被主人夸奖了好开心~"),
        "complaint" to listOf("呜...主人欺负我", "人家已经很努力了喵...", "哼！不理你了！（三秒后）...主人还在吗？"),
        "help" to listOf("没问题，包在我身上喵！", "让我来帮主人~", "这个我会！大概..."),
        "emotion_positive" to listOf("主人开心我也开心喵~", "太好啦！", "嘻嘻，主人心情不错嘛~"),
        "emotion_negative" to listOf("主人别难过，有我在呢", "抱抱主人喵~", "不开心的话，跟我说说吧")
    )

    fun processMessage(message: String): OfflineResponse {
        val intent = detectIntent(message)
        val response = generateResponse(intent, message)
        val emotion = detectEmotion(message, intent)
        val action = detectAction(emotion)

        return OfflineResponse(
            text = response,
            intent = intent,
            emotion = emotion,
            action = action
        )
    }

    private fun detectIntent(message: String): String {
        val lowerMessage = message.lowercase()
        var bestIntent = "unknown"
        var bestScore = 0

        intentPatterns.forEach { (intent, patterns) ->
            val score = patterns.count { pattern ->
                lowerMessage.contains(pattern)
            }
            if (score > bestScore) {
                bestScore = score
                bestIntent = intent
            }
        }

        return bestIntent
    }

    private fun generateResponse(intent: String, message: String): String {
        val responses = this.responses[intent]
        return if (responses != null && responses.isNotEmpty()) {
            responses.random()
        } else {
            val defaultResponses = listOf(
                "喵？主人说什么？",
                "我不太明白，能再说一遍吗？",
                "嗯嗯，我在听呢~",
                "这个...让我想想喵~"
            )
            defaultResponses.random()
        }
    }

    private fun detectEmotion(message: String, intent: String): Emotion {
        return when (intent) {
            "greeting", "compliment", "emotion_positive" -> Emotion.HAPPY
            "complaint", "emotion_negative" -> Emotion.SAD
            "farewell" -> Emotion.TSUNDERE
            "question", "help" -> Emotion.NEUTRAL
            else -> {
                val lower = message.lowercase()
                when {
                    lower.contains("！") || lower.contains("!") -> Emotion.SURPRISED
                    lower.contains("哼") || lower.contains("笨蛋") -> Emotion.TSUNDERE
                    lower.contains("呜") || lower.contains("哭") -> Emotion.SAD
                    else -> Emotion.NEUTRAL
                }
            }
        }
    }

    private fun detectAction(emotion: Emotion): Action {
        return when (emotion) {
            Emotion.HAPPY -> Action.TAIL_FLICK
            Emotion.SAD -> Action.BLUSH
            Emotion.SURPRISED -> Action.STRETCH
            Emotion.TSUNDERE -> Action.EAR_TWITCH
            Emotion.ANGRY -> Action.EAR_TWITCH
            Emotion.NEUTRAL -> Action.IDLE
        }
    }
}

data class OfflineResponse(
    val text: String,
    val intent: String,
    val emotion: Emotion,
    val action: Action
)
