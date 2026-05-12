import pytest
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fastapi.testclient import TestClient
from main import app


client = TestClient(app)


def test_root():
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert "message" in data
    assert data["message"] == "AI Companion Backend"


def test_health_check():
    response = client.get("/api/v1/health/status")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert "memory_usage_mb" in data
    assert "uptime_seconds" in data


def test_chat_request_validation():
    response = client.post(
        "/api/v1/chat/send",
        json={
            "user_id": "test_user",
            "message": "你好",
            "app_category": "social"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "text" in data
    assert "emotion" in data
    assert "action" in data


def test_memory_list():
    response = client.get("/api/v1/memory/list/test_user")
    assert response.status_code == 200
    data = response.json()
    assert "memories" in data
    assert "total_count" in data


def test_daily_card():
    response = client.get("/api/v1/memory/daily-card/test_user")
    assert response.status_code == 200
    data = response.json()
    assert "title" in data
    assert "content" in data
