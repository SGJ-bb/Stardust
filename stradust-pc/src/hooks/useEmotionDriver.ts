/**
 * 情绪驱动 Hook —— 对齐 Android ChatViewModel → Live2DWebView.setEmotion/triggerAction。
 * 监听统一引擎 chat-stream 的 emotion/proactive 事件，驱动 Live2D 表情与动作。
 */

import { useEffect } from "react";
import { controlLive2D } from "@/lib/tauri";
import { on } from "@/lib/events";
import { getExpressionName } from "@/lib/constants";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { Emotion } from "@/types/emotion";

/** 引擎情绪（kinds.Emotion/LLM 标签）→ 页面 Emotion 映射 */
const EMOTION_MAP: Record<string, Emotion> = {
  neutral: "neutral", happy: "happy", sad: "sad", angry: "angry", surprised: "surprised",
  shy: "shy", excited: "excited", comfort: "worried", proud: "proud", confused: "thinking",
  sleepy: "tired", cry: "sad", tsundere: "shy", love: "love", thinking: "thinking",
  worried: "worried", tired: "tired",
};

const EMOTION_MOTION: Record<string, string> = {
  happy: "happy", sad: "sad", angry: "angry", surprised: "surprise", excited: "happy",
  shy: "shy", comfort: "sad", proud: "happy", confused: "idle", sleepy: "sleep", cry: "sad",
  neutral: "idle", tsundere: "shy",
};

export function useEmotionDriver(): void {
  useEffect(() => {
    if (useSettingsStore.getState().settings.aiFeatures?.live2dEnabled === false) return;
    const off = on<{ type?: string; content?: string; personaId?: string }>("chat-stream", (e) => {
      if (!e?.type) return;
      if (e.type === "emotion" && e.content) {
        const emotion = EMOTION_MAP[e.content.toLowerCase()] ?? "neutral";
        controlLive2D({
          action: "expression",
          expressionName: getExpressionName(emotion),
        } as Parameters<typeof controlLive2D>[0] & { expressionName: string }).catch(() => undefined);
        controlLive2D({
          action: "motion",
          motionGroup: EMOTION_MOTION[e.content.toLowerCase()] ?? "idle",
          motionIndex: 0,
        } as Parameters<typeof controlLive2D>[0] & { motionGroup: string; motionIndex: number }).catch(() => undefined);
      }
    });
    return off;
  }, []);
}
