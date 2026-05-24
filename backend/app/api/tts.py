import logging
import os
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel
from typing import Optional

from app.services.voice_service import VoiceService
from app.models.schemas import EmotionEnum

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/tts", tags=["tts"])

voice_service = VoiceService()


class TtsRequest(BaseModel):
    model: str = "tts-1"
    input: str
    voice: str = "alloy"
    emotion: Optional[str] = None
    response_format: Optional[str] = None
    speed: Optional[float] = None


def _parse_emotion(emotion_str: Optional[str]) -> EmotionEnum:
    if emotion_str:
        try:
            return EmotionEnum(emotion_str.lower())
        except ValueError:
            logger.warning(f"Unknown emotion: {emotion_str}, using NEUTRAL")
    return EmotionEnum.NEUTRAL


@router.post("/synthesize")
async def synthesize_speech(request: TtsRequest):
    try:
        emotion = _parse_emotion(request.emotion)

        audio_path = await voice_service.synthesize_speech(
            text=request.input,
            emotion=emotion,
            user_id=None,
            use_cloud=True
        )

        if not audio_path or not os.path.exists(audio_path):
            raise HTTPException(
                status_code=500,
                detail="TTS synthesis failed: no audio generated. "
                       "Ensure AZURE_SPEECH_KEY is configured or pyttsx3 is available."
            )

        media_type = "audio/wav" if audio_path.endswith(".wav") else "audio/mpeg"
        return FileResponse(
            audio_path,
            media_type=media_type,
            filename=os.path.basename(audio_path),
            background=None
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"TTS synthesize failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"TTS synthesis failed: {str(e)}")