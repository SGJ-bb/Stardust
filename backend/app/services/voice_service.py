import logging
import tempfile
import os
from typing import Optional, Dict, Any
from app.core.config import settings
from app.models.schemas import EmotionEnum

logger = logging.getLogger(__name__)


class VoiceService:
    def __init__(self):
        self._cache_dir = tempfile.mkdtemp(prefix="voice_cache_")
        self._cache = {}

    async def synthesize_speech(
        self,
        text: str,
        emotion: EmotionEnum = EmotionEnum.NEUTRAL,
        user_id: Optional[str] = None,
        use_cloud: bool = True
    ) -> Optional[str]:
        """语音合成，返回音频文件路径"""
        cache_key = f"{user_id}_{text}_{emotion.value}"

        if cache_key in self._cache:
            return self._cache[cache_key]

        try:
            if use_cloud and settings.AZURE_SPEECH_KEY:
                audio_path = await self._azure_tts(text, emotion)
            else:
                audio_path = await self._local_tts(text, emotion)

            if audio_path:
                self._cache[cache_key] = audio_path
            return audio_path

        except Exception as e:
            logger.error(f"TTS failed: {e}")
            return None

    async def _azure_tts(self, text: str, emotion: EmotionEnum) -> Optional[str]:
        """Azure 高质量语音合成"""
        try:
            import azure.cognitiveservices.speech as speechsdk

            speech_config = speechsdk.SpeechConfig(
                subscription=settings.AZURE_SPEECH_KEY,
                region=settings.AZURE_SPEECH_REGION
            )

            prosody = self._get_emotion_prosody(emotion)

            ssml = f"""
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="zh-CN">
                <voice name="zh-CN-XiaoxiaoNeural">
                    <prosody rate="{prosody['rate']}" pitch="{prosody['pitch']}">
                        {text}
                    </prosody>
                </voice>
            </speak>
            """

            audio_path = os.path.join(self._cache_dir, f"tts_{hash(text)}.wav")

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
            return await self._local_tts(text, emotion)

    async def _local_tts(self, text: str, emotion: EmotionEnum) -> Optional[str]:
        """本地TTS（使用系统TTS或ChatTTS）"""
        try:
            import pyttsx3

            engine = pyttsx3.init()
            engine.setProperty('rate', 180)

            prosody = self._get_emotion_prosody(emotion)

            rate_str = prosody['rate'].replace('%', '')
            rate_val = int(rate_str) if rate_str else 0
            engine.setProperty('rate', 180 + rate_val)

            audio_path = os.path.join(self._cache_dir, f"local_tts_{hash(text)}.wav")
            engine.save_to_file(text, audio_path)
            engine.runAndWait()

            if os.path.exists(audio_path):
                return audio_path
            return None

        except Exception as e:
            logger.error(f"Local TTS failed: {e}")
            return None

    def _get_emotion_prosody(self, emotion: EmotionEnum) -> Dict[str, str]:
        """根据情感获取语音参数"""
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
        """清理语音缓存"""
        for key in list(self._cache.keys()):
            path = self._cache[key]
            if os.path.exists(path):
                os.remove(path)
        self._cache.clear()
