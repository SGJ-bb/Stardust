from fastapi import APIRouter, HTTPException
from typing import Optional
from app.models.schemas import MemoryList, MemoryUpdateRequest
from app.services.memory_service import MemoryService

router = APIRouter(prefix="/api/v1/memory", tags=["memory"])

memory_service = MemoryService()


@router.get("/list/{user_id}", response_model=MemoryList)
async def get_memories(
    user_id: str,
    limit: int = 20,
    category: Optional[str] = None
):
    """获取用户记忆列表"""
    memories = await memory_service.get_user_memories(user_id, limit, category)
    return MemoryList(
        memories=memories,
        total_count=len(memories)
    )


@router.delete("/delete")
async def delete_memory(request: MemoryUpdateRequest):
    """删除记忆（单条或全部）"""
    if request.action == "delete_one":
        if not request.memory_id:
            raise HTTPException(status_code=400, detail="memory_id is required")

        success = await memory_service.delete_memory(request.user_id, request.memory_id)
        if not success:
            raise HTTPException(status_code=500, detail="Failed to delete memory")
        return {"status": "success", "action": "delete_one"}

    elif request.action == "delete_all":
        success = await memory_service.delete_all_memories(request.user_id)
        if not success:
            raise HTTPException(status_code=500, detail="Failed to delete all memories")
        return {"status": "success", "action": "delete_all"}

    else:
        raise HTTPException(status_code=400, detail="Invalid action")


@router.post("/add")
async def add_memory(user_id: str, text: str, category: str = "general"):
    """添加记忆"""
    memory_id = await memory_service.add_memory(
        user_id=user_id,
        text=text,
        metadata={"category": category}
    )
    return {"status": "success", "memory_id": memory_id}


@router.get("/search/{user_id}")
async def search_memories(user_id: str, query: str, limit: int = 5):
    """语义搜索记忆"""
    memories = await memory_service.search_memories(user_id, query, limit)
    return MemoryList(
        memories=memories,
        total_count=len(memories)
    )


@router.get("/daily-card/{user_id}")
async def get_daily_card(user_id: str):
    """生成每日记忆卡片"""
    card = await memory_service.generate_daily_card(user_id)
    return card
