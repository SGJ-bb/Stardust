import { useCallback, useRef } from "react";
import { useVoiceStore } from "@/stores/useVoiceStore";
import { startRecording, stopRecording, textToSpeech, speechToText } from "@/lib/tauri";

/**
 * 语音交互钩子
 * 提供录音、语音识别、语音合成功能
 */
export function useVoice() {
  const { isRecording, isPlaying, setRecording, setPlaying, setCurrentAudio } = useVoiceStore();
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);

  /** 开始录音 */
  const startVoiceRecording = useCallback(async () => {
    try {
      setRecording(true);
      await startRecording();
    } catch (error) {
      console.error("开始录音失败:", error);
      setRecording(false);
    }
  }, [setRecording]);

  /** 停止录音并识别 */
  const stopVoiceRecording = useCallback(async (): Promise<string | null> => {
    try {
      setRecording(false);
      const audioPath = await stopRecording();
      if (audioPath) {
        const text = await speechToText({ audioPath });
        return text;
      }
      return null;
    } catch (error) {
      console.error("停止录音失败:", error);
      setRecording(false);
      return null;
    }
  }, [setRecording]);

  /** 语音合成 */
  const speak = useCallback(async (text: string, voiceId: string, engine: string = "edge-tts") => {
    try {
      setPlaying(true);
      const audioUrl = await textToSpeech({ text, voiceId, engine });
      setCurrentAudio(audioUrl);
      // 使用Audio对象的ended事件来准确重置播放状态
      const audio = new Audio(audioUrl);
      audio.addEventListener("ended", () => {
        setPlaying(false);
        setCurrentAudio(null);
      });
      audio.addEventListener("error", () => {
        setPlaying(false);
        setCurrentAudio(null);
      });
      await audio.play();
    } catch (error) {
      console.error("语音合成失败:", error);
      setPlaying(false);
    }
  }, [setPlaying, setCurrentAudio]);

  return {
    isRecording,
    isPlaying,
    startVoiceRecording,
    stopVoiceRecording,
    speak,
  };
}
