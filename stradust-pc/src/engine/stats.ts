/**
 * 角色统计引擎 —— 移植自 stats/PersonaStatsManager.kt
 * 复刻规则：
 * - recordUserMessage/recordAiMessage → recordMessage(personaId, role)：
 *   消息计数 +1、字数累加（totalWords 存 kv）、recordChatDay、recordHour；
 * - recordChatDay：yyyyMMdd 日期集合去重计天，同时 updateStreak
 *   （今天或昨天有记录才延续连击，向前回溯最多 365 天）；
 * - recordEmotion → emotionCounts 计数 + moodTrend（保留最近 100）；
 * - recordHour：hour_map 分布 + peak_chat_hour 取最大值；
 * - firstChatTime/lastChatTime 维护。
 * 主体存 db collection "stats"（kinds.ts PersonaStats），扩展字段（字数/小时分布/心情趋势/
 * streak/表情数）存 kv：persona_stats_ext_{personaId}。
 */

import { getOne, put, kvGet, kvSet } from "@/lib/db";
import type { Emotion, PersonaStats } from "./kinds";

interface StatsExt {
  chatDaysSet: string[]; // yyyyMMdd 集合（Kotlin StringSet）
  hourMap: Record<string, number>; // 小时分布
  peakChatHour: number;
  moodTrend: string[]; // 最近 100 条情绪流
  totalWordsUser: number;
  totalWordsAi: number;
  stickersSent: number;
  stickersReceived: number;
  currentStreak: number;
  longestStreak: number;
}

const DEFAULT_EXT: StatsExt = {
  chatDaysSet: [],
  hourMap: {},
  peakChatHour: -1,
  moodTrend: [],
  totalWordsUser: 0,
  totalWordsAi: 0,
  stickersSent: 0,
  stickersReceived: 0,
  currentStreak: 0,
  longestStreak: 0,
};

function extKey(personaId: string): string {
  return `persona_stats_ext_${personaId}`;
}

async function loadExt(personaId: string): Promise<StatsExt> {
  return { ...DEFAULT_EXT, ...((await kvGet<Partial<StatsExt>>(extKey(personaId))) ?? {}) };
}

function dayStr(ts: number): string {
  const d = new Date(ts);
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
}

function shiftDayStr(dateStr: string, days: number): string {
  const y = Number(dateStr.slice(0, 4));
  const m = Number(dateStr.slice(4, 6));
  const d = Number(dateStr.slice(6, 8));
  return dayStr(new Date(y, m - 1, d + days).getTime());
}

/** 重算连击（Kotlin updateStreak：起点须为今天或昨天，逐日回溯封顶 365） */
function updateStreak(ext: StatsExt): void {
  const set = new Set(ext.chatDaysSet);
  if (!set.size) { ext.currentStreak = 0; return; }
  const sorted = [...set].sort().reverse();
  const today = dayStr(Date.now());
  let cursor: string | null = null;
  if (sorted[0] === today) cursor = today;
  else if (sorted[0] === shiftDayStr(today, -1)) cursor = shiftDayStr(today, -1);
  if (!cursor) { ext.currentStreak = 0; return; }
  let streak = 0;
  while (streak < 365 && set.has(cursor as string)) {
    streak += 1;
    cursor = shiftDayStr(cursor as string, -1);
  }
  ext.currentStreak = streak;
  if (streak > ext.longestStreak) ext.longestStreak = streak;
}

/** 记录一条消息（role: user/assistant；system 忽略，对齐 Kotlin 只记用户与 AI） */
export async function recordMessage(personaId: string, role: "user" | "assistant" | "system", emotion?: Emotion | string, textLen = 0): Promise<void> {
  if (!personaId || role === "system") return;
  const now = Date.now();
  const base = (await getOne<PersonaStats>("stats", personaId)) ?? {
    id: personaId,
    personaId,
    totalMessages: 0,
    userMessages: 0,
    petMessages: 0,
    chatDays: 0,
    emotionCounts: {},
    firstChatAt: now,
    lastChatAt: now,
  };
  const ext = await loadExt(personaId);

  if (role === "user") {
    base.userMessages += 1;
    ext.totalWordsUser += textLen;
  } else {
    base.petMessages += 1;
    ext.totalWordsAi += textLen;
  }
  base.totalMessages += 1;
  if (!base.firstChatAt) base.firstChatAt = now;
  base.lastChatAt = now;

  // recordChatDay：新日期入集合
  const day = dayStr(now);
  if (!ext.chatDaysSet.includes(day)) {
    ext.chatDaysSet.push(day);
    updateStreak(ext);
  }
  base.chatDays = ext.chatDaysSet.length;

  // recordHour：小时分布 + 峰值
  const hour = String(new Date(now).getHours());
  ext.hourMap[hour] = (ext.hourMap[hour] ?? 0) + 1;
  let peak = -1;
  let peakVal = -1;
  for (const [h, v] of Object.entries(ext.hourMap)) {
    if (v > peakVal) { peakVal = v; peak = Number(h); }
  }
  ext.peakChatHour = peak;

  // recordEmotion：计数 + 心情趋势（≤100）
  if (emotion) {
    const key = String(emotion) as Emotion;
    base.emotionCounts[key] = (base.emotionCounts[key] ?? 0) + 1;
    ext.moodTrend.push(key);
    if (ext.moodTrend.length > 100) ext.moodTrend = ext.moodTrend.slice(-100);
  }

  await put("stats", base);
  await kvSet(extKey(personaId), ext);
}

/** 读取统计（含扩展字段的组合视图） */
export async function getStats(personaId: string): Promise<(PersonaStats & { extra: StatsExt }) | null> {
  const base = await getOne<PersonaStats>("stats", personaId);
  if (!base) return null;
  return { ...base, extra: await loadExt(personaId) };
}

/** 聊天天数（Kotlin totalChatDays = daysSet.size） */
export async function getChatDays(personaId: string): Promise<number> {
  const ext = await loadExt(personaId);
  return ext.chatDaysSet.length;
}

/** 距首次聊天的天数（Kotlin getDaysSinceFirstChat） */
export async function getDaysSinceFirstChat(personaId: string): Promise<number> {
  const base = await getOne<PersonaStats>("stats", personaId);
  if (!base?.firstChatAt) return 0;
  return Math.floor((Date.now() - base.firstChatAt) / 86400000);
}

/** 当前/最长连击（Kotlin currentStreak/longestStreak） */
export async function getStreaks(personaId: string): Promise<{ current: number; longest: number }> {
  const ext = await loadExt(personaId);
  return { current: ext.currentStreak, longest: ext.longestStreak };
}

/** 高频聊天时段（Kotlin getPeakChatHour） */
export async function getPeakChatHour(personaId: string): Promise<number> {
  const ext = await loadExt(personaId);
  return ext.peakChatHour;
}

/** 最近心情走势（Kotlin getRecentMoodTrend limit=20） */
export async function getRecentMoodTrend(personaId: string, limit = 20): Promise<string[]> {
  const ext = await loadExt(personaId);
  return ext.moodTrend.slice(-limit);
}

/** 记录表情包收发（Kotlin recordStickerSent/Received） */
export async function recordStickerEvent(personaId: string, dir: "sent" | "received"): Promise<void> {
  const ext = await loadExt(personaId);
  if (dir === "sent") ext.stickersSent += 1;
  else ext.stickersReceived += 1;
  await kvSet(extKey(personaId), ext);
}
