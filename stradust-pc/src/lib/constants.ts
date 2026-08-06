import type { Emotion, Action, EmotionActionMap, EmotionState } from "@/types/emotion";

/** 情感-动作映射表 */
export const EMOTION_ACTION_MAP: EmotionActionMap[] = [
  { emotion: "neutral", actions: ["idle"], expressionName: "neutral", motionGroup: "idle" },
  { emotion: "happy", actions: ["jump", "dance"], expressionName: "happy", motionGroup: "happy" },
  { emotion: "sad", actions: ["sit", "bow"], expressionName: "sad", motionGroup: "sad" },
  { emotion: "angry", actions: ["stand"], expressionName: "angry", motionGroup: "angry" },
  { emotion: "surprised", actions: ["jump"], expressionName: "surprised", motionGroup: "surprised" },
  { emotion: "shy", actions: ["sit", "bow"], expressionName: "shy", motionGroup: "shy" },
  { emotion: "love", actions: ["hug", "kiss"], expressionName: "love", motionGroup: "love" },
  { emotion: "thinking", actions: ["think", "sit"], expressionName: "thinking", motionGroup: "thinking" },
  { emotion: "excited", actions: ["jump", "dance", "run"], expressionName: "excited", motionGroup: "excited" },
  { emotion: "worried", actions: ["walk", "sit"], expressionName: "worried", motionGroup: "worried" },
  { emotion: "tired", actions: ["sleep", "sit"], expressionName: "tired", motionGroup: "tired" },
  { emotion: "proud", actions: ["stand", "nod"], expressionName: "proud", motionGroup: "proud" },
];

/** 根据情感获取映射 */
export function getEmotionActionMap(emotion: Emotion): EmotionActionMap {
  return EMOTION_ACTION_MAP.find((m) => m.emotion === emotion) ?? EMOTION_ACTION_MAP[0];
}

/** 根据情感获取Live2D表情名 */
export function getExpressionName(emotion: Emotion): string {
  return getEmotionActionMap(emotion).expressionName;
}

/** 根据情感获取动作组名 */
export function getMotionGroup(emotion: Emotion): string {
  return getEmotionActionMap(emotion).motionGroup;
}

/** 情感中文名映射 */
export const EMOTION_LABELS: Record<Emotion, string> = {
  neutral: "平静",
  happy: "开心",
  sad: "难过",
  angry: "生气",
  surprised: "惊讶",
  shy: "害羞",
  love: "喜爱",
  thinking: "思考",
  excited: "兴奋",
  worried: "担心",
  tired: "疲惫",
  proud: "骄傲",
};

/** 动作中文名映射 */
export const ACTION_LABELS: Record<Action, string> = {
  idle: "待机",
  greeting: "打招呼",
  wave: "挥手",
  nod: "点头",
  shake_head: "摇头",
  bow: "鞠躬",
  jump: "跳跃",
  dance: "跳舞",
  sit: "坐下",
  stand: "站立",
  walk: "行走",
  run: "奔跑",
  sleep: "睡觉",
  eat: "吃东西",
  drink: "喝水",
  hug: "拥抱",
  kiss: "亲吻",
  pat: "摸头",
  poke: "戳",
  think: "思考",
};

/** 默认情感状态 */
export const DEFAULT_EMOTION_STATE: EmotionState = {
  current: "neutral",
  intensity: 0.5,
  duration: 5000,
  trigger: "system",
  timestamp: Date.now(),
};
