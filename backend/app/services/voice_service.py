import asyncio
import logging
import tempfile
import os
import hashlib
from typing import Optional, Dict, Any
from app.core.config import settings
from app.models.schemas import EmotionEnum

logger = logging.getLogger(__name__)

MAX_CACHE_SIZE = 200


class VoiceService:
    def __init__(self):
        self._cache_dir = tempfile.mkdtemp(prefix="voice_cache_")
        self._cache: Dict[str, str] = {}

    def _stable_hash(self, text: str) -> str:
        return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]

    def _escape_ssml(self, text: str) -> str:
        return (
            text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "&quot;")
            .replace("'", "&apos;")
        )

    async def synthesize_speech(
        self,
        text: str,
        emotion: EmotionEnum = EmotionEnum.NEUTRAL,
        user_id: Optional[str] = None,
        use_cloud: bool = True
    ) -> Optional[str]:
        cache_key = f"{user_id}_{text}_{emotion.value}"

        if cache_key in self._cache:
            return self._cache[cache_key]

        try:
            if use_cloud and settings.AZURE_SPEECH_KEY:
                audio_path = await asyncio.to_thread(
                    self._azure_tts_sync, text, emotion
                )
            else:
                audio_path = await asyncio.to_thread(
                    self._local_tts_sync, text, emotion
                )

            if audio_path:
                if len(self._cache) >= MAX_CACHE_SIZE:
                    oldest_key = next(iter(self._cache))
                    old_path = self._cache.pop(oldest_key)
                    try:
                        if os.path.exists(old_path):
                            os.remove(old_path)
                    except OSError:
                        pass
                self._cache[cache_key] = audio_path
            return audio_path

        except Exception as e:
            logger.error(f"TTS failed: {e}")
            return None

    def _azure_tts_sync(self, text: str, emotion: EmotionEnum) -> Optional[str]:
        try:
            import azure.cognitiveservices.speech as speechsdk

            speech_config = speechsdk.SpeechConfig(
                subscription=settings.AZURE_SPEECH_KEY,
                region=settings.AZURE_SPEECH_REGION
            )

            prosody = self._get_emotion_prosody(emotion)
            escaped_text = self._escape_ssml(text)

            ssml = (
                '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="zh-CN">'
                '<voice name="zh-CN-XiaoxiaoNeural">'
                f'<prosody rate="{prosody["rate"]}" pitch="{prosody["pitch"]}">'
                f'{escaped_text}'
                '</prosody>'
                '</voice>'
                '</speak>'
            )

            audio_path = os.path.join(self._cache_dir, f"tts_{self._stable_hash(text)}.wav")

            audio_config = speechsdk.audio.AudioOutputConfig(filename=audio_path)
            synthesizer = speechsdk.SpeechSynthesizer(
                speech_config=speech_config,
                audio_config=audio_config
            )

            result = synthesizer.speak_ssml_async(ssml).get()

            if result.reason == speechsdk.ResultReason.SynthesizingAudioCompleted:
                return audio_path
            else:
                logger.warning(f"Azure TTS failed: {result.reason}")
                return None

        except ImportError:
            logger.warning("Azure Speech SDK not installed, falling back to local TTS")
            return self._local_tts_sync(text, emotion)

    def _local_tts_sync(self, text: str, emotion: EmotionEnum) -> Optional[str]:
        try:
            import pyttsx3

            engine = pyttsx3.init()
            engine.setProperty('rate', 180)

            prosody = self._get_emotion_prosody(emotion)

            rate_str = prosody['rate'].replace('%', '')
            rate_val = int(rate_str) if rate_str else 0
            engine.setProperty('rate', 180 + rate_val)

            audio_path = os.path.join(self._cache_dir, f"local_tts_{self._stable_hash(text)}.wav")
            engine.save_to_file(text, audio_path)
            engine.runAndWait()

            if os.path.exists(audio_path):
                return audio_path
            return None

        except Exception as e:
            logger.error(f"Local TTS failed: {e}")
            return None

    def _get_emotion_prosody(self, emotion: EmotionEnum) -> Dict[str, str]:
        prosody_map = {
            EmotionEnum.HAPPY: {"rate": "+10%", "pitch": "+5%"},
            EmotionEnum.ANGRY: {"rate": "+20%", "pitch": "+15%"},
            EmotionEnum.SAD: {"rate": "-15%", "pitch": "-10%"},
            EmotionEnum.SURPRISED: {"rate": "+25%", "pitch": "+20%"},
            EmotionEnum.TSUNDERE: {"rate": "+5%", "pitch": "+10%"},
            EmotionEnum.NEUTRAL: {"rate": "+0%", "pitch": "+0%"}
        }
        return prosody_map.get(emotion, prosody_map[EmotionEnum.NEUTRAL])

    def clear_cache(self):
        for key in list(self._cache.keys()):
            path = self._cache[key]
            if os.path.exists(path):
                os.remove(path)
        self._cache.clear()
