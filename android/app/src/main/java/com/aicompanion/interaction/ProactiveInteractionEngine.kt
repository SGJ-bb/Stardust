package com.aicompanion.interaction

import com.aicompanion.models.Emotion
import com.aicompanion.models.Action
import com.aicompanion.settings.SettingsManager

class ProactiveInteractionEngine(private val settingsManager: SettingsManager) {

    private val idlePhrases = listOf(
        "主人~ 在忙什么呢？",
        "好无聊喵...",
        "主人已经很久没理我了...",
        "喵？主人还在吗？",
        "我饿了...（其实是想主人了）"
    )

    private val appSpecificPhrases = mapOf(
        "game" to listOf("又在玩游戏！都不陪我！", "主人玩游戏好厉害喵~", "带我一起玩嘛！"),
        "browser" to listOf("主人在查什么呢？", "网上有什么好玩的？", "小心别看到奇怪的东西哦~"),
        "video" to listOf("在看视频吗？什么类型的？", "带我一起看嘛~", "这个好看吗？"),
        "music" to listOf("在听歌吗？好听吗？", "我也想唱歌喵~", "放首我喜欢的！"),
        "social" to listOf("在聊天吗？和谁呀？", "我也要认识新朋友！", "主人最受欢迎了~"),
        "work" to listOf("主人在工作吗？好辛苦...", "别太累了，休息一下吧", "工作完了陪我玩嘛~")
    )

    private val frequentPhrases = listOf(
        "主人~在看什么呢？", "喵喵~需要帮忙吗？", "有点无聊诶...",
        "主人不觉得今天天气很好吗？", "我刚刚学到了一个新词！", "想听我讲个笑话吗？",
        "主人今天的任务完成了吗？", "要不要起来活动一下？", "喝口水吧，别太累了~"
    )

    private val normalPhrases = listOf(
        "主人今天心情怎么样？", "有什么有趣的事情吗？", "我一直在想主人呢~",
        "主人好久没和我聊天了", "今天过得怎么样呀？", "要不要一起放松一下？",
        "我感觉主人可能需要一个拥抱", "主人辛苦了，休息一下吧"
    )

    private val infrequentPhrases = listOf(
        "好久不见！好想你呀~", "主人终于来了！", "最近过得还好吗？",
        "感觉好久没聊天了呢", "今天有什么新鲜事吗？", "主人不在的时候我自己想了很多..."
    )

    private var lastInteractionTime = System.currentTimeMillis()
    private var lastNagTime = 0L

    fun shouldTriggerInteraction(nagFrequency: String): Boolean {
        if (!settingsManager.shouldTriggerNag()) return false
        val now = System.currentTimeMillis()
        val interval = when (nagFrequency) {
            "frequent" -> 3 * 60 * 1000L
            "normal" -> 10 * 60 * 1000L
            "infrequent" -> 30 * 60 * 1000L
            else -> 10 * 60 * 1000L
        }
        return (now - lastNagTime) > interval
    }

    fun getNagPhrase(nagFrequency: String): String {
        lastNagTime = System.currentTimeMillis()
        val phrases = when (nagFrequency) {
            "frequent" -> frequentPhrases
            "infrequent" -> infrequentPhrases
            else -> normalPhrases
        }
        val idx = (System.currentTimeMillis() % phrases.size).toInt()
        return phrases[idx]
    }

    fun shouldTriggerInteraction(appCategory: String?, screenContent: String?, currentTime: Long): Boolean {
        if (!settingsManager.shouldTriggerNag()) return false
        val timeSinceLastInteraction = currentTime - lastInteractionTime
        return timeSinceLastInteraction > 5 * 60 * 1000L
    }

    fun getIdlePhrase(): Triple<String, Emotion, Action> {
        val phrase = idlePhrases.random()
        val emotion = listOf(Emotion.HAPPY, Emotion.TSUNDERE, Emotion.NEUTRAL).random()
        val action = listOf(Action.EAR_TWITCH, Action.TAIL_FLICK, Action.IDLE).random()
        lastInteractionTime = System.currentTimeMillis()
        return Triple(phrase, emotion, action)
    }

    fun getAppSpecificPhrase(appCategory: String?): Triple<String, Emotion, Action> {
        val phrases = appSpecificPhrases[appCategory] ?: idlePhrases
        val phrase = phrases.random()
        val emotion = when (appCategory) {
            "game" -> Emotion.TSUNDERE
            "work" -> Emotion.SAD
            else -> Emotion.HAPPY
        }
        val action = listOf(Action.EAR_TWITCH, Action.TAIL_FLICK, Action.IDLE).random()
        lastInteractionTime = System.currentTimeMillis()
        return Triple(phrase, emotion, action)
    }
}
