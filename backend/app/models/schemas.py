from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime
from enum import Enum


class EmotionEnum(str, Enum):
    HAPPY = "happy"
    ANGRY = "angry"
    SAD = "sad"
    SURPRISED = "surprised"
    TSUNDERE = "tsundere"
    NEUTRAL = "neutral"


class ActionEnum(str, Enum):
    TAIL_FLICK = "tail_flick"
    EAR_TWITCH = "ear_twitch"
    BLUSH = "blush"
    STRETCH = "stretch"
    YAWN = "yawn"
    IDLE = "idle"


class AppCategoryEnum(str, Enum):
    SOCIAL = "social"
    VIDEO = "video"
    SHOPPING = "shopping"
    OFFICE = "office"
    GAME = "game"
    MUSIC = "music"
    UNKNOWN = "unknown"


class ChatRequest(BaseModel):
    user_id: str
    message: Optional[str] = None
    app_category: Optional[AppCategoryEnum] = None
    is_offline_mode: bool = False
    battery_level: Optional[float] = None


class ChatResponse(BaseModel):
    text: str
    emotion: EmotionEnum = EmotionEnum.NEUTRAL
    action: ActionEnum = ActionEnum.IDLE
    audio_url: Optional[str] = None
    response_time_ms: float


class MemoryFact(BaseModel):
    id: str
    user_id: str
    fact: str
    timestamp: datetime
    category: str = "general"


class MemoryList(BaseModel):
    memories: List[MemoryFact]
    total_count: int


class MemoryUpdateRequest(BaseModel):
    user_id: str
    memory_id: Optional[str] = None
    action: str = Field(..., description="delete_one or delete_all")


class VoiceToken(BaseModel):
    user_id: str
    locale: str = "zh-CN"


class DailyCard(BaseModel):
    user_id: str
    date: str
    title: str
    content: str
    image_url: Optional[str] = None


class HealthStatus(BaseModel):
    status: str
    memory_usage_mb: float
    active_users: int
    uptime_seconds: float
