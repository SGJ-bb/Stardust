import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { EMOTION_LABELS, ACTION_LABELS } from "@/lib/constants";
import type { Emotion, Action } from "@/types/emotion";
import { useLive2D } from "@/hooks/useLive2D";
import { cn } from "@/lib/utils";

/**
 * Live2D控制面板组件
 * 提供表情/动作切换功能
 */
export function Live2DController() {
  const { setExpression, startMotion } = useLive2D();
  const [selectedEmotion, setSelectedEmotion] = useState<Emotion>("neutral");
  const [selectedAction, setSelectedAction] = useState<Action>("idle");

  const emotions: Emotion[] = [
    "neutral", "happy", "sad", "angry", "surprised",
    "shy", "love", "thinking", "excited", "worried", "tired", "proud",
  ];

  const actions: Action[] = [
    "idle", "greeting", "wave", "nod", "shake_head",
    "bow", "jump", "dance", "sit", "stand", "walk",
    "hug", "kiss", "pat", "poke", "think",
  ];

  return (
    <div className="space-y-4 p-4">
      {/* 表情切换 */}
      <div>
        <h4 className="mb-2 text-sm font-medium text-[var(--color-card-foreground)]">表情</h4>
        <div className="grid grid-cols-4 gap-2">
          {emotions.map((emotion) => (
            <Button
              key={emotion}
              variant={selectedEmotion === emotion ? "default" : "outline"}
              size="sm"
              onClick={() => {
                setSelectedEmotion(emotion);
                setExpression(emotion);
              }}
              className={cn("text-xs")}
            >
              {EMOTION_LABELS[emotion]}
            </Button>
          ))}
        </div>
      </div>

      {/* 动作切换 */}
      <div>
        <h4 className="mb-2 text-sm font-medium text-[var(--color-card-foreground)]">动作</h4>
        <div className="grid grid-cols-4 gap-2">
          {actions.map((action) => (
            <Button
              key={action}
              variant={selectedAction === action ? "default" : "outline"}
              size="sm"
              onClick={() => {
                setSelectedAction(action);
                startMotion(action);
              }}
              className={cn("text-xs")}
            >
              {ACTION_LABELS[action]}
            </Button>
          ))}
        </div>
      </div>
    </div>
  );
}
