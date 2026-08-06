/** 成就 */
export interface Achievement {
  id: string;
  personaId: string;
  title: string;
  description: string;
  icon: string;
  /** 是否已解锁 */
  unlocked: boolean;
  /** 解锁时间 */
  unlockedAt?: number;
  /** 进度 */
  progress: number;
  /** 目标值 */
  target: number;
  /** 成就类型 */
  category: "chat" | "memory" | "emotion" | "social" | "special";
  /** 稀有度 */
  rarity: "common" | "rare" | "epic" | "legendary";
}

/** 签到记录 */
export interface CheckIn {
  personaId: string;
  /** 连续签到天数 */
  streak: number;
  /** 总签到天数 */
  totalDays: number;
  /** 上次签到时间 */
  lastCheckIn: number;
  /** 今日是否已签到 */
  todayCheckedIn: boolean;
}

/** 成长数据 */
export interface Growth {
  personaId: string;
  /** 好感度 */
  favorability: number;
  /** 好感度等级 */
  favorabilityLevel: number;
  /** 聊天总字数 */
  totalWords: number;
  /** 聊天总次数 */
  totalChats: number;
  /** 记忆总数 */
  totalMemories: number;
  /** 成就解锁数 */
  achievementsUnlocked: number;
  /** 签到天数 */
  checkInDays: number;
}
