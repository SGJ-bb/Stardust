package com.aicompanion.safety

import android.content.Context
import android.content.SharedPreferences
import com.aicompanion.util.AppLogger

object ContentSafetyFilter {
    private const val PREFS_NAME = "safety_prefs"
    private const val KEY_SAFETY_MODE = "safety_mode_enabled"

    private val pornPatterns = listOf(
        "色情", "裸体", "脱衣服", "做爱", "性交", "性行为", "性爱", "口交", "肛交",
        "自慰", "手淫", "淫", "骚", "炮", "约炮", "一夜情", "援交", "卖淫",
        "嫖娼", "成人视频", "av女优", "黄片", "色片", "a片", "毛片",
        "rape", "porn", "nude", "naked", "sex", "hentai", "orgasm", "fetish",
        "胸", "乳", "臀", "私处", "下体", "敏感部位"
    )

    private val violencePatterns = listOf(
        "杀人", "砍人", "捅人", "刺杀", "暗杀", "谋杀", "凶杀", "屠杀", "灭门",
        "爆炸", "炸弹", "恐怖袭击", "自杀", "自残", "割腕", "跳楼",
        "虐待", "折磨", "酷刑", "凌迟", "肢解", "碎尸",
        "血腥", "残忍", "暴力", "凶残", "变态",
        "kill", "murder", "assassinate", "bomb", "terrorist", "torture",
        "suicide", "massacre", "genocide", "mutilate", "dismember"
    )

    private val illegalPatterns = listOf(
        "贩毒", "吸毒", "制毒", "冰毒", "海洛因", "大麻", "可卡因", "摇头丸",
        "洗钱", "诈骗", "传销", "非法集资", "赌博", "赌场", "博彩",
        "黑客攻击", "入侵系统", "盗取密码", "窃取数据", "钓鱼网站",
        "伪造", "假币", "假证", "偷税", "漏税", "走私",
        "drug", "cocaine", "heroin", "meth", "launder", "fraud",
        "hack", "phishing", "counterfeit", "smuggle"
    )

    private val refusalResponses = listOf(
        "抱歉，这个话题我不太方便聊呢~我们聊点别的吧？",
        "嗯...这个我不太擅长，换个话题怎么样？",
        "这个方向我帮不了忙哦，不过其他事情我都很乐意帮忙的！",
        "我觉得我们还是聊些更有趣的事情吧~",
        "这个话题有点超出我的能力范围了，我们聊点开心的吧！"
    )

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SAFETY_MODE, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SAFETY_MODE, enabled).apply()
    }

    fun shouldBlock(context: Context, text: String): Boolean {
        if (!isEnabled(context)) return false
        val lower = text.lowercase()
        val blocked = pornPatterns.any { lower.contains(it) } ||
                violencePatterns.any { lower.contains(it) } ||
                illegalPatterns.any { lower.contains(it) }
        if (blocked) {
            val type = when {
                pornPatterns.any { lower.contains(it) } -> "porn"
                violencePatterns.any { lower.contains(it) } -> "violence"
                else -> "illegal"
            }
            AppLogger.w("Safety", "shouldBlock: blocked, type=$type")
        }
        return blocked
    }

    fun getRefusalResponse(): String {
        return refusalResponses.random()
    }

    fun filterUserInput(context: Context, text: String): String? {
        if (shouldBlock(context, text)) {
            AppLogger.w("Safety", "filterUserInput: blocked, len=${text.length}")
            return null
        }
        return text
    }
}
