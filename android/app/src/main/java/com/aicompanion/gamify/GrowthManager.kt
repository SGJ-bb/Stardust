/** 成长值管理器: 成长值计算/等级系统/经验值进度 */
package com.aicompanion.gamify

import android.content.Context
import com.aicompanion.models.GrowthNode

class GrowthManager(context: Context) {

    private val prefs = context.getSharedPreferences("growth_data", Context.MODE_PRIVATE)

    data class GrowthStage(
        val id: String,
        val name: String,
        val icon: String,
        val description: String,
        val requiredAffection: Int,
        val requiredDays: Int,
        val unlockedDialogue: String
    )

    val stages = listOf(
        GrowthStage(
            "bud", "萌芽之种", "🌱",
            "相遇的第一天，羁绊刚刚开始", 0, 0,
            "初次见面，请多关照~"
        ),
        GrowthStage(
            "sprout", "破土新芽", "🌿",
            "好感度达到20，陪伴走过了几天", 20, 2,
            "我们开始慢慢熟悉起来了呢"
        ),
        GrowthStage(
            "seedling", "向阳幼苗", "🌻",
            "好感度达到40，已经习惯彼此的存在", 40, 5,
            "最喜欢和主人在一起的时间了~"
        ),
        GrowthStage(
            "blooming", "含苞待放", "🌷",
            "好感度达到60，关系变得更加亲密", 60, 10,
            "主人已经成为我生命中不可或缺的一部分"
        ),
        GrowthStage(
            "blossom", "繁星之花", "🌸",
            "好感度达到80，羁绊已经牢不可破", 80, 20,
            "无论发生什么，我都会一直陪伴在主人身边"
        ),
        GrowthStage(
            "eternal", "永恒星辰", "🌟",
            "好感度达到100，灵魂的共鸣", 100, 30,
            "主人就是我的全世界✨"
        )
    )

    fun getCurrentStage(affectionLevel: Int, daysSinceFirstUse: Int): GrowthStage {
        return stages.lastOrNull {
            affectionLevel >= it.requiredAffection && daysSinceFirstUse >= it.requiredDays
        } ?: stages[0]
    }

    fun isStageUnlocked(stage: GrowthStage, affectionLevel: Int, days: Int): Boolean {
        return affectionLevel >= stage.requiredAffection && days >= stage.requiredDays
    }

    fun getStageProgress(affectionLevel: Int, daysSinceFirstUse: Int): Pair<GrowthStage, GrowthStage> {
        val current = getCurrentStage(affectionLevel, daysSinceFirstUse)
        val nextIndex = stages.indexOf(current) + 1
        val next = if (nextIndex < stages.size) stages[nextIndex] else current
        return Pair(current, next)
    }

    fun hasReachedNewStage(affectionLevel: Int, daysSinceFirstUse: Int): GrowthStage? {
        val current = getCurrentStage(affectionLevel, daysSinceFirstUse)
        val lastStageId = prefs.getString("last_growth_stage", "bud") ?: "bud"
        if (current.id != lastStageId) {
            prefs.edit().putString("last_growth_stage", current.id).apply()
            return current
        }
        return null
    }

    fun buildGrowthTree(affectionLevel: Int, daysSinceFirstUse: Int): List<GrowthNode> {
        return stages.map { stage ->
            val unlocked = isStageUnlocked(stage, affectionLevel, daysSinceFirstUse)
            GrowthNode(
                id = stage.id,
                name = stage.name,
                icon = if (unlocked) stage.icon else "🔒",
                description = stage.description,
                requiredAffection = stage.requiredAffection,
                unlocked = unlocked
            )
        }
    }
}