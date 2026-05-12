import json
import time
import logging
from typing import Optional, Dict, Any
from openai import AsyncOpenAI
from app.core.config import settings
from app.models.schemas import (
    ChatRequest, ChatResponse, EmotionEnum, ActionEnum, AppCategoryEnum
)
from app.services.memory_service import MemoryService
from app.services.prompt_builder import PromptBuilder

logger = logging.getLogger(__name__)


class LLMService:
    def __init__(self):
        self.client = AsyncOpenAI(
            api_key=settings.LLM_API_KEY,
            base_url=settings.LLM_BASE_URL
        )
        self.memory_service = MemoryService()
        self.prompt_builder = PromptBuilder()
        self._request_count = {}

    async def generate_response(self, request: ChatRequest) -> ChatResponse:
        start_time = time.time()

        if not self._check_rate_limit(request.user_id):
            return ChatResponse(
                text="今天聊得够多啦，明天再继续吧~",
                emotion=EmotionEnum.TSUNDERE,
                action=ActionEnum.YAWN,
                response_time_ms=0
            )

        memories = await self.memory_service.get_user_memories(request.user_id)
        prompt = self.prompt_builder.build_prompt(
            memories=memories,
            app_category=request.app_category,
            user_message=request.message
        )

        model = self._select_model(request)

        try:
            response = await self.client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": prompt},
                    {"role": "user", "content": request.message or "打招呼"}
                ],
                response_format={"type": "json_object"},
                temperature=0.8,
                max_tokens=300
            )

            content = response.choices[0].message.content
            parsed = json.loads(content)

            return ChatResponse(
                text=parsed.get("text", "..."),
                emotion=EmotionEnum(parsed.get("emotion", "neutral")),
                action=ActionEnum(parsed.get("action", "idle")),
                response_time_ms=(time.time() - start_time) * 1000
            )

        except Exception as e:
            logger.error(f"LLM request failed: {e}")
            return ChatResponse(
                text="嗯...我有点卡住了，稍等一下喵",
                emotion=EmotionEnum.SAD,
                action=ActionEnum.EAR_TWITCH,
                response_time_ms=(time.time() - start_time) * 1000
            )

    def _select_model(self, request: ChatRequest) -> str:
        if request.is_offline_mode:
            return settings.LLM_MODEL_CASUAL
        if request.message and len(request.message) > 50:
            return settings.LLM_MODEL_ADVANCED
        return settings.LLM_MODEL_CASUAL

    def _check_rate_limit(self, user_id: str) -> bool:
        count = self._request_count.get(user_id, 0)
        if count >= settings.DAILY_REQUEST_LIMIT:
            return False
        self._request_count[user_id] = count + 1
        return True

    def reset_daily_count(self, user_id: str):
        self._request_count[user_id] = 0
