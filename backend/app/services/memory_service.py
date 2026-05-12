import httpx
import logging
from typing import List, Optional
from datetime import datetime
from app.core.config import settings
from app.models.schemas import MemoryFact, MemoryList

logger = logging.getLogger(__name__)


class MemoryService:
    def __init__(self):
        self.base_url = settings.MEM0_API_URL
        self.api_key = settings.MEM0_API_KEY
        self._headers = {
            "Authorization": f"Token {self.api_key}",
            "Content-Type": "application/json"
        }

    async def add_memory(self, user_id: str, text: str, metadata: dict = None) -> str:
        """添加用户记忆事实"""
        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    f"{self.base_url}/memories/",
                    headers=self._headers,
                    json={
                        "messages": [{"role": "user", "content": text}],
                        "user_id": user_id,
                        "metadata": metadata or {}
                    }
                )
                response.raise_for_status()
                data = response.json()
                return data.get("id", "")
        except Exception as e:
            logger.error(f"Failed to add memory: {e}")
            return ""

    async def get_user_memories(
        self,
        user_id: str,
        limit: int = 10,
        category: Optional[str] = None
    ) -> List[MemoryFact]:
        """获取用户记忆列表"""
        try:
            async with httpx.AsyncClient() as client:
                params = {
                    "user_id": user_id,
                    "limit": limit
                }
                if category:
                    params["metadata"] = {"category": category}

                response = await client.get(
                    f"{self.base_url}/memories/",
                    headers=self._headers,
                    params=params
                )
                response.raise_for_status()
                data = response.json()

                memories = []
                for item in data.get("results", []):
                    memories.append(MemoryFact(
                        id=item.get("id", ""),
                        user_id=user_id,
                        fact=item.get("memory", ""),
                        timestamp=datetime.fromisoformat(
                            item.get("created_at", datetime.now().isoformat())
                        ),
                        category=item.get("metadata", {}).get("category", "general")
                    ))
                return memories
        except Exception as e:
            logger.error(f"Failed to get memories: {e}")
            return []

    async def delete_memory(self, user_id: str, memory_id: str) -> bool:
        """删除单条记忆"""
        try:
            async with httpx.AsyncClient() as client:
                response = await client.delete(
                    f"{self.base_url}/memories/{memory_id}/",
                    headers=self._headers,
                    json={"user_id": user_id}
                )
                return response.status_code == 200
        except Exception as e:
            logger.error(f"Failed to delete memory: {e}")
            return False

    async def delete_all_memories(self, user_id: str) -> bool:
        """删除用户所有记忆"""
        try:
            async with httpx.AsyncClient() as client:
                response = await client.delete(
                    f"{self.base_url}/memories/",
                    headers=self._headers,
                    json={"user_id": user_id}
                )
                return response.status_code == 200
        except Exception as e:
            logger.error(f"Failed to delete all memories: {e}")
            return False

    async def search_memories(
        self,
        user_id: str,
        query: str,
        limit: int = 5
    ) -> List[MemoryFact]:
        """语义搜索记忆"""
        try:
            async with httpx.AsyncClient() as client:
                response = await client.post(
                    f"{self.base_url}/memories/search/",
                    headers=self._headers,
                    json={
                        "query": query,
                        "user_id": user_id,
                        "limit": limit
                    }
                )
                response.raise_for_status()
                data = response.json()

                memories = []
                for item in data.get("results", []):
                    memories.append(MemoryFact(
                        id=item.get("id", ""),
                        user_id=user_id,
                        fact=item.get("memory", ""),
                        timestamp=datetime.fromisoformat(
                            item.get("created_at", datetime.now().isoformat())
                        ),
                        category=item.get("metadata", {}).get("category", "general")
                    ))
                return memories
        except Exception as e:
            logger.error(f"Failed to search memories: {e}")
            return []

    async def generate_daily_card(self, user_id: str) -> dict:
        """生成每日记忆卡片内容"""
        memories = await self.get_user_memories(user_id, limit=20)

        if not memories:
            return {
                "title": "平凡的一天",
                "content": "今天没有特别的事情发生呢~",
                "memory_count": 0
            }

        highlights = memories[:5]
        return {
            "title": f"今日观察日记 - {datetime.now().strftime('%Y-%m-%d')}",
            "content": "\n".join([f"• {m.fact}" for m in highlights]),
            "memory_count": len(memories)
        }
