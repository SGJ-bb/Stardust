from fastapi import APIRouter, HTTPException
from app.models.schemas import ChatRequest, ChatResponse
from app.services.llm_service import LLMService
from app.services.voice_service import VoiceService

router = APIRouter(prefix="/api/v1/chat", tags=["chat"])

llm_service = LLMService()
voice_service = VoiceService()


@router.post("/send", response_model=ChatResponse)
async def send_message(request: ChatRequest):
    try:
        response = await llm_service.generate_response(request)
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail="Request failed")


@router.post("/voice")
async def send_with_voice(request: ChatRequest):
    try:
        response = await llm_service.generate_response(request)

        audio_url = await voice_service.synthesize_speech(
            text=response.text,
            emotion=response.emotion,
            user_id=request.user_id
        )

        return ChatResponse(
            text=response.text,
            emotion=response.emotion,
            action=response.action,
            response_time_ms=response.response_time_ms,
            audio_url=audio_url
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail="Voice request failed")
