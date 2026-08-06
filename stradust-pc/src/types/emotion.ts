/** 情感类型 */
export type Emotion =
  | "neutral"
  | "happy"
  | "sad"
  | "angry"
  | "surprised"
  | "shy"
  | "love"
  | "thinking"
  | "excited"
  | "worried"
  | "tired"
  | "proud";

/** 动作类型 */
export type Action =
  | "idle"
  | "greeting"
  | "wave"
  | "nod"
  | "shake_head"
  | "bow"
  | "jump"
  | "dance"
  | "sit"
  | "stand"
  | "walk"
  | "run"
  | "sleep"
  | "eat"
  | "drink"
  | "hug"
  | "kiss"
  | "pat"
  | "poke"
  | "think";

/** 情感-动作映射 */
export interface EmotionActionMap {
  emotion: Emotion;
  actions: Action[];
  /** 对应的Live2D表情名 */
  expressionName: string;
  /** 对应的Live2D动作组名 */
  motionGroup: string;
}

/** 情感状态 */
export interface EmotionState {
  current: Emotion;
  intensity: number;
  /** 情感持续时间(ms) */
  duration: number;
  /** 触发原因 */
  trigger: string;
  timestamp: number;
}
