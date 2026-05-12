from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    APP_NAME: str = "AI Companion Backend"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False

    API_HOST: str = "0.0.0.0"
    API_PORT: int = 8000

    LLM_API_KEY: str = ""
    LLM_BASE_URL: str = "https://api.openai.com/v1"
    LLM_MODEL_CASUAL: str = "gpt-4o-mini"
    LLM_MODEL_ADVANCED: str = "gpt-4o"

    MEM0_API_KEY: str = ""
    MEM0_API_URL: str = "https://api.mem0.ai/v1"

    AZURE_SPEECH_KEY: str = ""
    AZURE_SPEECH_REGION: str = "eastus"

    DATABASE_URL: str = "sqlite:///./companion.db"

    MAX_MEMORY_PER_USER: int = 1000
    DAILY_REQUEST_LIMIT: int = 200
    MEMORY_HARD_LIMIT_MB: int = 280

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
