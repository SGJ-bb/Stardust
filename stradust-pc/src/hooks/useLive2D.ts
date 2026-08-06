import { useCallback } from "react";
import { controlLive2D } from "@/lib/tauri";
import { getExpressionName } from "@/lib/constants";
import type { Emotion, Action } from "@/types/emotion";

/** 动作到Live2D动作组名的映射 */
const ACTION_MOTION_MAP: Record<Action, string> = {
  idle: "idle",
  greeting: "greeting",
  wave: "wave",
  nod: "nod",
  shake_head: "shake_head",
  bow: "bow",
  jump: "jump",
  dance: "dance",
  sit: "sit",
  stand: "stand",
  walk: "walk",
  run: "run",
  sleep: "sleep",
  eat: "eat",
  drink: "drink",
  hug: "hug",
  kiss: "kiss",
  pat: "pat",
  poke: "poke",
  think: "think",
};

/**
 * Live2D控制钩子
 * 提供表情/动作切换、模型缩放等操作
 */
export function useLive2D() {
  /** 设置表情 */
  const setExpression = useCallback(async (emotion: Emotion) => {
    const expressionName = getExpressionName(emotion);
    try {
      await controlLive2D({
        action: "expression",
        expressionName,
      });
    } catch (error) {
      console.error("设置Live2D表情失败:", error);
    }
  }, []);

  /** 触发动作（接受动作类型参数） */
  const startMotion = useCallback(async (actionOrEmotion: Action | Emotion, index: number = 0) => {
    const motionGroup = ACTION_MOTION_MAP[actionOrEmotion as Action] ?? actionOrEmotion;
    try {
      await controlLive2D({
        action: "motion",
        motionGroup,
        motionIndex: index,
      });
    } catch (error) {
      console.error("触发Live2D动作失败:", error);
    }
  }, []);

  /** 加载模型 */
  const loadModel = useCallback(async (modelPath: string) => {
    try {
      await controlLive2D({
        action: "load",
        modelPath,
      });
    } catch (error) {
      console.error("加载Live2D模型失败:", error);
    }
  }, []);

  /** 点击交互 */
  const tapModel = useCallback(async (x: number, y: number) => {
    try {
      await controlLive2D({
        action: "tap",
      });
    } catch (error) {
      console.error("Live2D点击交互失败:", error);
    }
  }, []);

  /** 设置缩放 */
  const setModelScale = useCallback(async (scale: number) => {
    try {
      await controlLive2D({
        action: "scale",
        scale,
      });
    } catch (error) {
      console.error("设置Live2D缩放失败:", error);
    }
  }, []);

  return {
    setExpression,
    startMotion,
    loadModel,
    tapModel,
    setModelScale,
  };
}
