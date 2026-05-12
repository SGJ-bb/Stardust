from fastapi import APIRouter, HTTPException
from app.models.schemas import ChatRequest, ChatResponse
from app.services.llm_service import LLMService

router = APIRouter(prefix="/api/v1/chat", tags=["chat"])

llm_service = LLMService()


@router.post("/send", response_model=ChatResponse)
async def send_message(request: ChatRequest):
    """发送消息获取AI回复"""
    try:
        response = await llm_service.generate_response(request)
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Request failed: {str(e)}")


@router.post("/voice")
async def send_with_voice(request: ChatRequest):
    """发送消息并获取语音回复"""
    try:
        response = await llm_service.generate_response(request)

        from app.services.voice_service import VoiceService
        voice_service = VoiceService()

        audio_url = await voice_service.synthesize_speech(
            text=response.text,
            emotion=response.emotion,
            user_id=request.user_id
        )

        response.audio_url = audio_url
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Voice request failed: {str(e)}")
