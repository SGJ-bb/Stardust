package com.aicompanion.prompt

import android.content.Context
import com.aicompanion.persona.PersonaManager

object PromptBuilder {

    private const val CORE_RULES = "\n【规则】像真人聊天，简短自然。关心对方情绪。末尾[[emotion:happy/sad/angry/surprised/neutral]]"

    fun buildIdentity(context: Context, personaId: String): IdentityBlock {
        val prefs = context.getSharedPreferences("persona_data_$personaId", android.content.Context.MODE_PRIVATE)
        val pm = PersonaManager(context)
        pm.load()
        val persona = pm.getPersona(personaId)

        val name = persona?.name
            ?: prefs.getString("persona_name", "星尘")
            ?: "星尘"
        val personality = persona?.personality?.ifBlank {
            prefs.getString("persona_personality", "") ?: ""
        } ?: prefs.getString("persona_personality", "") ?: ""
        val speechStyle = persona?.speechStyle?.ifBlank {
            prefs.getString("persona_speech_style", "") ?: ""
        } ?: prefs.getString("persona_speech_style", "") ?: ""
        val prompt = persona?.prompt ?: ""
        val nickname = prefs.getString("user_nickname", "") ?: ""

        return IdentityBlock(
            name = name,
            personality = personality,
            speechStyle = speechStyle,
            customPrompt = prompt,
            userNickname = nickname
        )
    }

    fun buildPersonaBase(identity: IdentityBlock): String {
        return buildString {
            append("你是「${identity.name}」。")
            if (identity.personality.isNotBlank()) append(" 性格${identity.personality}。")
            if (identity.speechStyle.isNotBlank()) append(" ${identity.speechStyle}。")
            if (identity.userNickname.isNotBlank()) append(" 叫用户「${identity.userNickname}」。")
        }
    }

    fun buildPersonaFull(identity: IdentityBlock): String {
        return buildString {
            append("你是「${identity.name}」。")
            if (identity.personality.isNotBlank()) append("\n性格：${identity.personality}")
            if (identity.speechStyle.isNotBlank()) append("\n说话风格：${identity.speechStyle}")
            if (identity.customPrompt.isNotBlank()) append("\n${identity.customPrompt}")
            if (identity.userNickname.isNotBlank()) append("\n叫用户「${identity.userNickname}」。")
        }
    }

    fun buildChatPrompt(identity: IdentityBlock, emotion: String, action: String, memories: List<String>): String {
        return buildString {
            append(buildPersonaFull(identity))
            append("\n情绪：$emotion。动作：$action。")
            if (memories.isNotEmpty()) {
                append("\n记得：${memories.takeLast(3).joinToString("；")}")
            }
            append(CORE_RULES)
        }
    }

    fun buildGroupChatPrompt(
        identity: IdentityBlock,
        otherNames: List<String>,
        memories: List<String>,
        isMentioned: Boolean = true
    ): String {
        return buildString {
            append("你是「${identity.name}」。")
            if (identity.personality.isNotBlank()) append(" 性格${identity.personality}。")
            if (identity.speechStyle.isNotBlank()) append(" ${identity.speechStyle}。")
            append("\n群聊中，成员：${identity.name}(你)")
            for (n in otherNames) append("、$n")
            append("、用户。")
            append("\n你只输出自己想说的话，绝不输出别人说的话。")
            if (otherNames.isNotEmpty()) {
                append("不要出现「${otherNames.joinToString("」「")}：」格式。")
            }
            if (otherNames.isNotEmpty()) {
                append("\n想叫谁回复就@ta，如@${otherNames.first()}。不叫就不加。")
            }
            if (memories.isNotEmpty()) {
                append("\n记得：${memories.takeLast(3).joinToString("；")}")
            }
            append("\n【规则】像真人，1-2句。关心对方。末尾[[emotion:xxx]]。")
            if (!isMentioned) {
                append("不想说就回「沉默」。")
            }
        }
    }

    fun buildNagPrompt(
        identity: IdentityBlock,
        memoryContext: String?,
        appCategory: String?,
        systemAlert: String?
    ): String {
        return buildString {
            append(buildPersonaBase(identity))
            append(" 主动搭话，1-2句，自然不重复。")
            if (!memoryContext.isNullOrBlank()) {
                append("\n[记忆]\n$memoryContext")
            }
            if (systemAlert != null) {
                append("\n提醒：$systemAlert")
            }
            if (appCategory != null && appCategory !in listOf("unknown", "")) {
                val names = mapOf("game" to "玩游戏", "browser" to "浏览网页", "video" to "看视频",
                    "music" to "听音乐", "social" to "社交聊天", "work" to "工作")
                append("\n主人在${names[appCategory] ?: appCategory}。")
            }
            append("\n末尾[[emotion:xxx]]。")
        }
    }

    fun buildDiaryPrompt(identity: IdentityBlock, moodCn: String, moodEmoji: String, affectionLevel: Int, isUpdate: Boolean): String {
        return buildString {
            append(buildPersonaBase(identity))
            append("\n写日记。情绪：$moodCn $moodEmoji。好感度：$affectionLevel。")
            if (isUpdate) {
                append("追加模式，用「--- HH:mm 追加 ---」开头。")
            } else {
                append("用「【yyyy年M月d日 EEEE】」开头，第一行：情绪：$moodEmoji。")
            }
            append("200-400字，像朋友倾诉。末尾写💡*今日小贴士*。")
        }
    }

    fun buildMomentPrompt(identity: IdentityBlock, affectionLevel: Int): String {
        return buildString {
            append(buildPersonaBase(identity))
            append(" 发朋友圈动态。好感度：$affectionLevel。1-3句，自然有趣。只输出内容。")
        }
    }

    fun buildMomentReplyPrompt(identity: IdentityBlock, momentContent: String, commentContent: String): String {
        return buildString {
            append(buildPersonaBase(identity))
            append("\n你发了：「$momentContent」")
            append("\n用户评论：「$commentContent」")
            append("\n回复1-2句，自然可爱。只输出内容。")
        }
    }

    fun buildSilentCheckPrompt(identity: IdentityBlock, userText: String, recentContext: String): String {
        return buildString {
            append("你是「${identity.name}」，独立个体。")
            if (identity.personality.isNotBlank()) append(" 性格${identity.personality}。")
            append("\n群聊中用户说：「$userText」")
            append("\n最近：\n$recentContext")
            append("\n用户没叫你。该说话吗？相关或有强烈想法回「说话」，无关回「沉默」。只回两字。")
        }
    }

    fun buildWakeUpPrompt(identity: IdentityBlock, timeHint: String): String {
        return buildString {
            append(buildPersonaBase(identity))
            append(" ${timeHint}想找用户说话。1-2句，自然，不用自我介绍。")
        }
    }

    fun buildAutoOpPrompt(): String {
        return "手机操作专家。根据屏幕内容返回JSON操作步骤。" +
                "[{\"action\":\"click|back|home|scroll|wait\",\"text\":\"按钮\",\"index\":0,\"direction\":\"forward\",\"ms\":1000}]" +
                "最多10步。"
    }

    fun buildMemoryConsolidatePrompt(maxChars: Int): String {
        return "整理记忆，保留重要信息(场景/剧情/关系/喜好)，合并重复，删除过时。" +
                "格式：- [分类] 内容。分类：场景/剧情/喜好/习惯/事实/事件/计划/其他。不超过${maxChars}字。"
    }

    fun buildMemoryExtractPrompt(nick: String): String {
        return "提取对话中值得记住的信息(场景/剧情/关系/喜好)。只输出JSON数组，无更新输出[]。" +
                "分类：场景/剧情/喜好/习惯/事实/事件/计划/其他。提到${nick}用「${nick}」。"
    }

    fun buildScoreMomentsPrompt(personaName: String): String {
        return "你是「$personaName」，回顾聊天提取值得铭记的事。" +
                "打分1-10(重要性+触动性)，≥8分记录。分类：habit/preference/impression/detail。" +
                "输出JSON数组：[{\"content\":\"...\",\"score\":9,\"category\":\"habit\"}]"
    }

    fun stripNamePrefix(text: String, ownName: String, otherNames: List<String>): String {
        var result = text
        for (name in otherNames) {
            result = result.replace(Regex("^[\\[【]?${Regex.escape(name)}[\\]】]?[：:]\\s*"), "")
        }
        result = result.replace(Regex("^[\\[【]?${Regex.escape(ownName)}[\\]】]?[：:]\\s*"), "")
        return result.trim()
    }
}

data class IdentityBlock(
    val name: String,
    val personality: String,
    val speechStyle: String,
    val customPrompt: String,
    val userNickname: String
)
