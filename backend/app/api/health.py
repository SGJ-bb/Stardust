from fastapi import APIRouter
import time
import psutil
from app.models.schemas import HealthStatus

router = APIRouter(prefix="/api/v1/health", tags=["health"])

start_time = time.time()


@router.get("/status", response_model=HealthStatus)
async def health_check():
    """健康检查接口"""
    process = psutil.Process()
    memory_mb = process.memory_info().rss / 1024 / 1024

    return HealthStatus(
        status="ok",
        memory_usage_mb=memory_mb,
        active_users=0,
        uptime_seconds=time.time() - start_time
    )
