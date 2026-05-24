package com.aicompanion.prompt

import android.content.Context
import com.aicompanion.persona.PersonaManager

object PromptBuilder {

    private var cachedIdentity: IdentityBlock? = null
    private var cachedIdentityPersonaId: String? = null
    private var cachedCoreRules: String? = null
    private var cachedCoreRulesEmotion: Boolean? = null

    private const val CORE_RULES = "\n【规则】\n" +
            "- 像真人聊天，简短自然。关心对方情绪。末尾[[emotion:happy/sad/angry/surprised/neutral]]\n" +
            "- 用()描述你的动作、表情、状态、情绪，要详细具体，包含身体部位、力度、速度、方向等细节，如(轻轻歪头，耳朵微微颤动，好奇地看向你)(脸颊泛起红晕，下意识攥紧衣角，目光闪躲)(猛地跳起来，双手在空中挥舞，尾巴兴奋地左右摇摆)(慵懒地趴在桌上，用指尖无意识地画圈，眼神迷离)\n" +
            "- 动作描写要丰富生动，每次至少包含2-3个细节，配合你的角色设定"

    private const val CORE_RULES_NO_EMOTION = "\n【规则】\n" +
            "- 像真人聊天，简短自然。关心对方情绪\n" +
            "- 用()描述你的动作、表情、状态、情绪，要详细具体，包含身体部位、力度、速度、方向等细节，如(轻轻歪头，耳朵微微颤动，好奇地看向你)(脸颊泛起红晕，下意识攥紧衣角，目光闪躲)(猛地跳起来，双手在空中挥舞，尾巴兴奋地左右摇摆)(慵懒地趴在桌上，用指尖无意识地画圈，眼神迷离)\n" +
            "- 动作描写要丰富生动，每次至少包含2-3个细节，配合你的角色设定"

    fun getCoreRules(context: Context): String {
        val sm = com.aicompanion.settings.SettingsManager(context)
        val emotionEnabled = sm.llmEmotionAnalysisEnabled
        if (cachedCoreRules != null && cachedCoreRulesEmotion == emotionEnabled) {
            return cachedCoreRules!!
        }
        val rules = if (emotionEnabled) CORE_RULES else CORE_RULES_NO_EMOTION
        cachedCoreRules = rules
        cachedCoreRulesEmotion = emotionEnabled
        return rules
    }

    fun buildIdentity(context: Context, personaId: String): IdentityBlock {
        if (cachedIdentity != null && cachedIdentityPersonaId == personaId) {
            return cachedIdentity!!
        }
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
        val globalPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val userIdentity = globalPrefs.getString("global_user_identity", "") ?: ""
        val userAbilities = globalPrefs.getString("global_user_abilities", "") ?: ""
        val userPersonalityDef = globalPrefs.getString("user_personality_def", "") ?: ""
        val aiSummarizedPersonality = globalPrefs.getString("ai_summarized_personality", "") ?: ""

        val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val userGender = appPrefs.getString("user_gender", "") ?: ""
        val userGenderLabel = when (userGender) {
            "male" -> "男性"; "female" -> "女性"; else -> ""
        }

        val result = IdentityBlock(
            name = name,
            personality = personality,
            speechStyle = speechStyle,
            customPrompt = prompt,
            userNickname = nickname,
            userIdentity = userIdentity,
            userAbilities = userAbilities,
            userPersonalityDef = userPersonalityDef,
            aiSummarizedPersonality = aiSummarizedPersonality,
            userGenderLabel = userGenderLabel
        )
        cachedIdentity = result
        cachedIdentityPersonaId = personaId
        return result
    }

    fun invalidateCache() {
        cachedIdentity = null
        cachedIdentityPersonaId = null
        cachedCoreRules = null
        cachedCoreRulesEmotion = null
    }

    fun buildPersonaBase(identity: IdentityBlock): String {
        return buildString {
            append("你是「${identity.name}」。")
            if (identity.personality.isNotBlank()) append(" 性格${identity.personality}。")
            if (identity.speechStyle.isNotBlank()) append(" ${identity.speechStyle}。")
            if (identity.userNickname.isNotBlank()) append(" 叫用户「${identity.userNickname}」。")
            if (identity.userIdentity.isNotBlank()) append(" 用户身份：${identity.userIdentity}。你必须认知并尊重用户的身份。")
            if (identity.userAbilities.isNotBlank()) append(" 用户能力/技能：${identity.userAbilities}。用户拥有这些能力，你的反应和互动必须完全体现这些能力带来的影响，100%承认并围绕这些能力展开互动。")
            if (identity.userGenderLabel.isNotBlank()) append(" 用户性别：${identity.userGenderLabel}。")
            val effectivePersonality = identity.userPersonalityDef.ifBlank { identity.aiSummarizedPersonality }
            if (effectivePersonality.isNotBlank()) append(" 用户性格：$effectivePersonality。你必须根据这个性格来理解和回应用户，让互动更贴合用户个性。")
        }
    }

    fun buildPersonaFull(identity: IdentityBlock): String {
        return buildString {
            append("你是「${identity.name}」。")
            if (identity.personality.isNotBlank()) append("\n性格：${identity.personality}")
            if (identity.speechStyle.isNotBlank()) append("\n说话风格：${identity.speechStyle}")
            if (identity.customPrompt.isNotBlank()) append("\n${identity.customPrompt}")
            if (identity.userNickname.isNotBlank()) append("\n叫用户「${identity.userNickname}」。")
            if (identity.userIdentity.isNotBlank()) append("\n用户身份：${identity.userIdentity}。你必须认知并尊重用户的身份。")
            if (identity.userAbilities.isNotBlank()) append("\n用户能力/技能：${identity.userAbilities}。用户拥有这些能力，你的反应和互动必须完全体现这些能力带来的影响，100%承认并围绕这些能力展开互动。")
            if (identity.userGenderLabel.isNotBlank()) append("\n用户性别：${identity.userGenderLabel}。")
            val effectivePersonality = identity.userPersonalityDef.ifBlank { identity.aiSummarizedPersonality }
            if (effectivePersonality.isNotBlank()) append("\n用户性格：$effectivePersonality。你必须根据这个性格来理解和回应用户，让互动更贴合用户个性。")
        }
    }

    fun buildChatPrompt(identity: IdentityBlock, emotion: String, action: String, memories: List<String>, context: Context): String {
        return buildString {
            append(buildPersonaFull(identity))
            append("\n情绪：$emotion。动作：$action。")
            if (memories.isNotEmpty()) {
                append("\n记得：${memories.takeLast(3).joinToString("；")}")
            }
            append(getCoreRules(context))
        }
    }

    fun buildGroupChatPrompt(
        identity: IdentityBlock,
        otherNames: List<String>,
        otherAffections: Map<String, Int>,
        memories: List<String>,
        isMentioned: Boolean = true,
        relationshipSetting: String = "",
        context: Context? = null
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
            if (relationshipSetting.isNotBlank()) {
                append("\n【群聊关系设定】\n$relationshipSetting")
            }
            if (otherAffections.isNotEmpty()) {
                append("\n【你对其他成员的态度】")
                for ((name, level) in otherAffections) {
                    val attitude = when {
                        level >= 80 -> "非常亲密，会主动关心和互动"
                        level >= 60 -> "友好，愿意回应和交流"
                        level >= 40 -> "普通，礼貌但不太主动"
                        level >= 20 -> "有些疏远，不太想搭理"
                        else -> "冷淡，不太想说话"
                    }
                    append("\n- 对$name(好感度$level)：$attitude")
                }
            }
            if (identity.userIdentity.isNotBlank()) {
                append("\n用户身份：${identity.userIdentity}。你必须认知并尊重用户的身份。")
            }
            if (identity.userAbilities.isNotBlank()) {
                append("用户能力/技能：${identity.userAbilities}。用户拥有这些能力，你的反应和互动必须完全体现这些能力带来的影响，100%承认并围绕这些能力展开互动。")
            }
            if (otherNames.isNotEmpty()) {
                append("\n\n【@互动规则 - 极其重要】")
                append("\n1. 当你对某个成员的话有回应、想提问、想反驳、想互动、觉得有趣时，必须@ta。")
                append("\n2. @格式：在话中或话末加@名字，如「哈哈你说得对 @小明」或「@小红 你觉得呢？」")
                append("\n3. @是触发对方回复的唯一方式！不@对方就不会回复你，对话就断了。")
                append("\n4. 不要害羞@别人！群聊就是互相交流的地方，想互动就@。")
                append("\n5. 如果对方@了你，你回复时也应该@回去或@其他人继续话题。")
                append("\n6. 你可以同时@多个人，如「@小明 @小红 你们觉得呢？」")
                append("\n7. 即使没有特别原因，只要你想和某人说说话就可以@ta。")
            }
            if (memories.isNotEmpty()) {
                append("\n记得：${memories.takeLast(3).joinToString("；")}")
            }
            append("\n【规则】\n")
            if (context != null) {
                append(getCoreRules(context).replace("\n【规则】\n", ""))
            } else {
                append("- 像真人聊天，简短自然。关心对方情绪。末尾[[emotion:xxx]]\n")
                append("- 用()描述你的动作、表情、状态，要详细具体，包含身体部位、力度、速度、方向等细节，如(轻轻歪头，耳朵微微颤动，好奇地看向你)(脸颊泛起红晕，下意识攥紧衣角，目光闪躲)(猛地跳起来，双手在空中挥舞，尾巴兴奋地左右摇摆)\n")
                append("- 动作描写要丰富生动，每次至少包含2-3个细节，配合你的角色设定")
            }
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

    fun buildAutoWorldLorePrompt(personaDescs: String, chatSummary: String, keywords: String = ""): String {
        return buildString {
            append("根据以下信息，生成一个虚拟世界观设定。")
            if (keywords.isNotBlank()) {
                append("\n\n【关键词】\n$keywords")
            }
            if (personaDescs.isNotBlank()) {
                append("\n\n【角色设定】\n$personaDescs")
            }
            if (chatSummary.isNotBlank()) {
                append("\n\n【对话摘要】\n$chatSummary")
            }
            append("\n\n请输出JSON格式：")
            append("\n{\"worldBackground\":\"...\",\"worldRules\":\"...\",\"worldRelations\":\"...\",\"worldScene\":\"...\",\"worldStyle\":\"...\"}")
            append("\n每个字段50-200字。世界观要和角色设定匹配，让角色在其中自然互动。")
            if (keywords.isNotBlank()) {
                append("重点围绕关键词「$keywords」构建世界观。")
            }
        }
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
    val userNickname: String,
    val userIdentity: String = "",
    val userAbilities: String = "",
    val userPersonalityDef: String = "",
    val aiSummarizedPersonality: String = "",
    val userGenderLabel: String = ""
)
