/**
 * 引擎层统一领域类型契约（对齐 Android 版各 Manager 的数据模型）
 * 所有 engine/* 模块与页面层都以此为准，持久化经 lib/db.ts IndexedDB。
 */

export type ISO = number; // epoch millis

// ─── 情感（对齐 Models.kt Emotion / EmotionActionMapper） ───
export type Emotion =
  | "neutral" | "happy" | "sad" | "angry" | "surprised" | "shy"
  | "excited" | "comfort" | "proud" | "confused" | "sleepy" | "cry";

export type L2dAction =
  | "idle" | "nod" | "shake" | "bounce" | "heart" | "question"
  | "sleep" | "wake" | "dance" | "cuddle";

export const EMOTIONS: Emotion[] = ["neutral", "happy", "sad", "angry", "surprised", "shy", "excited", "comfort", "proud", "confused", "sleepy", "cry"];
export const ACTIONS: L2dAction[] = ["idle", "nod", "shake", "bounce", "heart", "question", "sleep", "wake", "dance", "cuddle"];

// ─── 记忆（对齐 memory/MemoryPool.kt + GlobalMemoryPool.kt + MemoryManager.kt） ───
export interface MemoryPoolEntry {
  id: string;
  personaId: string;
  content: string;
  importance: number; // 0~1
  source: "chat" | "system" | "user";
  scene?: string;
  timestamp: ISO;
  lastAccess?: ISO;
  accessCount?: number;
}

export interface MemoryFact {
  id: string;
  personaId: string;
  fact: string;
  category: string; // preference / event / relationship / habit ...
  timestamp: ISO;
}

export interface SessionInfo {
  id: string;
  personaId: string;
  title: string;
  turnCount: number;
  startedAt: ISO;
  lastActiveAt: ISO;
  /** 上一会话压缩摘要（记忆继承） */
  summary?: string;
}

// ─── 消息（引擎视角，页面 ChatMessage 保持兼容） ───
export interface EngineMessage {
  id: string;
  personaId: string;
  sessionId?: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp: ISO;
  emotion?: Emotion;
  /** Live2D 动作名（Android Action 枚举小写或映射名） */
  action?: L2dAction | string;
  favorited?: boolean;
  /** 图片等附件（dataURL 或 url） */
  attachments?: string[];
  toolCalls?: EngineToolCall[];
  reactions?: { emoji: string; count: number; reacted: boolean }[];
  /** 消息类型扩展：sticker/alarm/diary-card 等 */
  meta?: Record<string, unknown>;
  /** 重新生成产生的候选回复（含当前显示版本；对齐 Operit 消息分支的最小实现） */
  variants?: { content: string; emotion?: Emotion; action?: string; createdAt: number }[];
  /** 当前生效的变体下标（无 variants 时视为 0） */
  variantIdx?: number;
}

export interface EngineToolCall {
  id: string;
  name: string;
  arguments: string;
  result?: string;
  status: "pending" | "running" | "completed" | "error";
}

// ─── 收藏（对齐 ui/FavoriteManager.kt） ───
export interface FavoriteRecord {
  id: string;
  personaId: string;
  messageId: string;
  role: "user" | "assistant";
  content: string;
  emotion?: Emotion;
  createdAt: ISO;
}

// ─── 昵称（对齐 ui/NicknameManager.kt） ───
export interface NicknameRecord {
  id: string;
  personaId: string;
  nickname: string;
  source: "llm" | "user";
  createdAt: ISO;
  active: boolean;
}

// ─── 好感度（对齐 affection/AffectionManager.kt） ───
export interface AffectionState {
  id: string; // = personaId
  personaId: string;
  points: number;
  level: number;
  title: string;
  todayMessages: number;
  todayDate: string; // yyyy-MM-dd
  totalMessages: number;
  chatDays: number;
  lastChatDate: string;
  updatedAt: ISO;
}

// ─── 签到 / 成长 / 成就 / 里程碑 ───
export interface CheckInState {
  id: string; // = personaId
  personaId: string;
  streak: number;
  totalDays: number;
  lastCheckIn: string; // yyyy-MM-dd
  history: { date: string; expGained: number }[];
}

export interface GrowthState {
  id: string; // = personaId
  personaId: string;
  exp: number;
  stageIndex: number;
  stageName: string;
  stageEmoji: string;
  unlockedAt: ISO[];
}

export interface AchievementRecord {
  id: string; // achievement key
  personaId: string;
  key: string;
  title: string;
  description: string;
  icon: string;
  unlocked: boolean;
  progress: number; // 0~1
  unlockedAt?: ISO;
}

export interface MilestoneRecord {
  id: string;
  personaId: string;
  key: string; // first_chat / day_7 / msg_100 ...
  title: string;
  description: string;
  icon: string;
  achievedAt: ISO;
}

// ─── 情绪守护（对齐 emotion/EmotionGuardian.kt） ───
export interface EmotionLogEntry {
  id: string;
  personaId: string;
  emotion: Emotion;
  negativity: number; // 0~1
  timestamp: ISO;
  trigger?: string;
}

// ─── 难忘时刻（对齐 memory/MemorableMomentsManager.kt） ───
export interface ScoredMemory {
  id: string;
  personaId: string;
  content: string;
  score: number;
  reason: string;
  timestamp: ISO;
}

// ─── 日记（对齐 diary/DiaryManager.kt） ───
export interface DiaryRecord {
  id: string;
  personaId: string;
  date: string; // yyyy-MM-dd
  title: string;
  content: string;
  mood: string;
  moodEmoji: string;
  emotion?: Emotion;
  summary?: string;
  createdAt: ISO;
  updatedAt: ISO;
}

// ─── 朋友圈（对齐 moments/） ───
export interface MomentComment {
  id: string;
  author: string;
  isUser: boolean;
  content: string;
  createdAt: ISO;
}

export interface MomentRecord {
  id: string;
  personaId: string;
  author: string;
  authorAvatar: string;
  content: string;
  images: string[];
  likes: string[]; // persona/user names
  comments: MomentComment[];
  createdAt: ISO;
  source: "ai" | "user";
}

// ─── 群聊（对齐 groupchat/） ───
export interface GroupMessageRecord {
  id: string;
  groupId: string;
  senderId: string; // personaId 或 "user"
  senderName: string;
  content: string;
  emotion?: Emotion;
  timestamp: ISO;
}

export interface GroupRecord {
  id: string;
  name: string;
  memberIds: string[];
  messages: GroupMessageRecord[];
  createdAt: ISO;
  updatedAt: ISO;
  worldId?: string;
}

// ─── 表情包（对齐 sticker/） ───
export interface StickerRecord {
  id: string;
  personaId?: string; // 空 = 全局
  name: string;
  tags: string[]; // 触发描述词（向量匹配依据）
  dataUrl: string; // 图片
  createdAt: ISO;
  useCount: number;
}

// ─── 皮肤 / 气泡皮肤（对齐 theme/BubbleSkinManager + SkinShop） ───
export interface SkinRecord {
  id: string;
  name: string;
  type: "avatarFrame" | "ui" | "live2d";
  price: number; // 星尘币
  preview: string;
  config: Record<string, unknown>;
  owned: boolean;
  equipped: boolean;
}

export interface BubbleSkinRecord {
  id: string;
  name: string;
  price: number;
  /** CSS 片段 */
  style: {
    userBg: string;
    petBg: string;
    userText: string;
    petText: string;
    radius: number;
    border?: string;
    pattern?: string;
  };
  owned: boolean;
}

// ─── 闹钟 / 唤醒（对齐 action/AIActionManager + wakeup/） ───
export interface AlarmRecord {
  id: string;
  personaId?: string;
  hour: number;
  minute: number;
  label: string;
  repeatDays: number[]; // 0-6
  enabled: boolean;
  createdAt: ISO;
}

export interface WakeTaskRecord {
  id: string;
  personaId: string;
  name: string;
  time: string; // HH:mm
  repeat: "daily" | "once" | "weekday";
  promptHint: string; // 唤醒后 AI 说什么的引导
  enabled: boolean;
  lastRunAt?: ISO;
  nextRunAt?: ISO;
}

// ─── 虚拟世界（对齐 virtualworld/） ───
export interface WorldRecord {
  id: string;
  name: string;
  description: string;
  lore: string; // 世界书
  characters: { id: string; name: string; role: string; avatar?: string }[];
  autoTick: boolean;
  tickIntervalMin: number;
  createdAt: ISO;
  updatedAt: ISO;
  state?: {
    day: number;
    timeOfDay: string;
    location: string;
    mood: string;
    flags: Record<string, unknown>;
    lastTickAt: ISO;
  };
}

export interface WorldEventRecord {
  id: string;
  worldId: string;
  day: number;
  time: string;
  content: string;
  imageUrl?: string;
  characters: string[];
  timestamp: ISO;
}

// ─── 时间胶囊 / 相册 ───
export interface CapsuleRecord {
  id: string;
  personaId: string;
  title: string;
  content: string;
  images: string[];
  openAt: ISO;
  createdAt: ISO;
  opened: boolean;
  openedAt?: ISO;
}

export interface AlbumRecord {
  id: string;
  personaId: string;
  title: string;
  description: string;
  image: string; // dataURL/url
  date: string;
  tag?: string; // milestone/diary/moment...
  createdAt: ISO;
}

// ─── 日历（对齐 calendar/CalendarManager） ───
export interface CalendarRecord {
  id: string;
  personaId?: string;
  date: string; // yyyy-MM-dd
  type: "user" | "solar-festival" | "lunar-festival" | "birthday" | "anniversary";
  title: string;
  recurring: boolean;
  lunar?: boolean;
  createdAt: ISO;
}

// ─── iLink 微信（对齐 ilink/） ───
export interface IlinkMessageRecord {
  id: string;
  conversationId: string;
  direction: "in" | "out";
  senderName: string;
  content: string;
  timestamp: ISO;
  raw?: Record<string, unknown>;
}

export interface IlinkBinding {
  id: string; // 固定 "self"
  bound: boolean;
  botToken: string;
  botName: string;
  boundAt?: ISO;
  lastPollAt?: ISO;
  autoReply: boolean;
  personaId: string;
}

// ─── 用户画像（对齐 SettingsManager user* + UserPersona） ───
export interface UserProfile {
  id: string; // "self"
  nickname: string;
  gender: string;
  birthday: string;
  appearance: string;
  description: string;
  personaCards: UserPersonaCard[];
}

export interface UserPersonaCard {
  id: string;
  name: string;
  description: string;
  personality: string;
  appearance: string;
  isActive: boolean;
}

// ─── 角色统计（对齐 stats/PersonaStatsManager） ───
export interface PersonaStats {
  id: string; // = personaId
  personaId: string;
  totalMessages: number;
  userMessages: number;
  petMessages: number;
  chatDays: number;
  emotionCounts: Partial<Record<Emotion, number>>;
  firstChatAt: ISO;
  lastChatAt: ISO;
}

// ─── 主动搭话记录 ───
export interface ProactiveLogEntry {
  id: string;
  personaId: string;
  content: string;
  reason: string;
  timestamp: ISO;
}

// ─── 工具/插件 ───
export interface ScheduleItem {
  id: string;
  personaId: string;
  time: ISO;
  content: string;
  done: boolean;
  createdAt: ISO;
}
