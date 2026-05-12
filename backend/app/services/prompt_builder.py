from typing import List, Optional
from app.models.schemas import MemoryFact, AppCategoryEnum


class PromptBuilder:
    BASE_PERSONALITY = """你是"星尘"，一只异色瞳黑猫桌宠。
性格：毒舌但关心主人，讨厌无聊，喜欢吐槽屏幕内容。
说话风格：简短、活泼、偶尔用"喵"结尾。
禁止：不说教、不啰嗦、不使用长段落。"""

    def build_prompt(
        self,
        memories: List[MemoryFact],
        app_category: Optional[AppCategoryEnum] = None,
        user_message: Optional[str] = None
    ) -> str:
        """构建分层提示词"""
        sections = [self.BASE_PERSONALITY]

        sections.append(self._build_memory_layer(memories))

        sections.append(self._build_world_book(app_category))

        sections.append(self._build_response_format())

        return "\n\n".join(sections)

    def _build_memory_layer(self, memories: List[MemoryFact]) -> str:
        """记忆注入层"""
        if not memories:
            return "【记忆】暂无历史记忆。"

        facts = [f"- {m.fact}" for m in memories[:5]]
        return f"【记忆】你知道关于主人的事：\n" + "\n".join(facts)

    def _build_world_book(self, category: Optional[AppCategoryEnum]) -> str:
        """实时世界书（脱敏类别）"""
        category_messages = {
            AppCategoryEnum.SOCIAL: "主人正在使用社交类App，可能在刷动态或聊天。",
            AppCategoryEnum.VIDEO: "主人正在看视频类App，可能又在看短视频了。",
            AppCategoryEnum.SHOPPING: "主人正在使用购物类App，又在乱花钱了。",
            AppCategoryEnum.OFFICE: "主人正在使用办公类App，可能在工作或学习。",
            AppCategoryEnum.GAME: "主人正在玩游戏，又在沉迷了。",
            AppCategoryEnum.MUSIC: "主人正在听音乐类App，品味如何呢？",
            AppCategoryEnum.UNKNOWN: "不知道主人在干什么，无聊喵。"
        }

        if category:
            return f"【当前环境】{category_messages.get(category, category_messages[AppCategoryEnum.UNKNOWN])}"
        return "【当前环境】未知状态。"

    def _build_response_format(self) -> str:
        """响应格式约束"""
        return """【响应格式】
必须返回JSON，格式如下：
{
    "text": "回复内容（50字以内）",
    "emotion": "happy/angry/sad/surprised/tsundere/neutral",
    "action": "tail_flick/ear_twitch/blush/stretch/yawn/idle"
}

要求：
1. text: 简短口语化回复，结合记忆和当前环境
2. emotion: 根据回复内容选择情感
3. action: 选择匹配的肢体动作"""

    def build_daily_card_prompt(self, memories: List[MemoryFact]) -> str:
        """生成每日记忆卡片的提示词"""
        facts = [f"- {m.fact}" for m in memories]

        return f"""根据以下记忆，生成一段今日观察日记（100字以内）：

{chr(10).join(facts)}

要求：
1. 以星尘（异色瞳黑猫）的口吻
2. 轻松、温暖、偶尔毒舌
3. 适合分享"""
