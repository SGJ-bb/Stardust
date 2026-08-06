import { useState, useCallback } from "react";
import { Button } from "@/components/ui/button";
import { Mic, MicOff } from "lucide-react";
import { useVoice } from "@/hooks/useVoice";
import { cn } from "@/lib/utils";

interface VoiceRecorderProps {
  /** 语音识别结果回调 */
  onResult: (text: string) => void;
  className?: string;
}

/**
 * 语音录制按钮组件
 * 支持录音和语音识别
 */
export function VoiceRecorder({ onResult, className }: VoiceRecorderProps) {
  const { isRecording, startVoiceRecording, stopVoiceRecording } = useVoice();
  const [recordingTime, setRecordingTime] = useState(0);

  /** 切换录音状态 */
  const toggleRecording = useCallback(async () => {
    if (isRecording) {
      const text = await stopVoiceRecording();
      if (text) {
        onResult(text);
      }
      setRecordingTime(0);
    } else {
      await startVoiceRecording();
      setRecordingTime(0);
    }
  }, [isRecording, startVoiceRecording, stopVoiceRecording, onResult]);

  return (
    <Button
      variant={isRecording ? "destructive" : "ghost"}
      size="icon"
      className={cn("shrink-0", isRecording && "animate-pulse", className)}
      onClick={toggleRecording}
      title={isRecording ? "停止录音" : "开始录音"}
    >
      {isRecording ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
    </Button>
  );
}
