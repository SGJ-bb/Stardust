/**
 * 成就引擎 —— 移植自 gamify/AchievementManager.kt
 * 完整成就目录（key/标题/描述/图标/分类/目标值）逐条抄自 allAchievements。
 * 复刻规则：
 * - updateProgress(type, value)：同类别未解锁成就 progress = max(progress, value)，
 *   progress >= unlockCondition 即解锁（可级联解锁多个，如 chat_10/chat_50 同时达标）；
 * - unlockAchievement(id)：直接解锁指定成就；
 * - 目录以模板为准合并存档（Kotlin loadAchievements 的 template 兜底逻辑）。
 *
 * 偏差点：
 * 1. kinds.ts AchievementRecord.progress 为 0~1，故存储时 progress = min(1, raw/target)；
 * 2. late_night / early_bird / egg_* 在 Kotlin 属 hidden 类、由外部事件触发，
 *    checkAutomaticMilestones 之外通过 ctx.hours / ctx.eggs 或直接 unlockAchievement 传入；
 * 3. Android 成就全局单例 → PC 按 personaId 独立（id = `${personaId}:${key}`）。
 */

import { getByPersona, getOne, put, putAll } from "@/lib/db";
import type { AchievementRecord } from "./kinds";
import { emit } from "@/lib/events";

export interface AchievementDef {
  key: string;
  title: string;
  description: string;
  icon: string;
  category: string;
  target: number;
}

/** 完整成就目录（逐条抄自 AchievementManager.allAchievements） */
export const ACHIEVEMENT_CATALOG: AchievementDef[] = [
  { key: "first_chat", title: "初次对话", description: "发送第一条消息", icon: "💬", category: "chat", target: 1 },
  { key: "chat_10", title: "健谈伙伴", description: "累计发送10条消息", icon: "🗣", category: "chat", target: 10 },
  { key: "chat_50", title: "话痨达人", description: "累计发送50条消息", icon: "📢", category: "chat", target: 50 },
  { key: "chat_100", title: "无话不说", description: "累计发送100条消息", icon: "🌟", category: "chat", target: 100 },
  { key: "streak_3", title: "三日之约", description: "连续打卡3天", icon: "🔥", category: "checkin", target: 3 },
  { key: "streak_7", title: "七日同行", description: "连续打卡7天", icon: "🏅", category: "checkin", target: 7 },
  { key: "streak_30", title: "月之羁绊", description: "连续打卡30天", icon: "👑", category: "checkin", target: 30 },
  { key: "affection_30", title: "初见好感", description: "好感度达到30", icon: "❤", category: "affection", target: 30 },
  { key: "affection_60", title: "亲密伙伴", description: "好感度达到60", icon: "💕", category: "affection", target: 60 },
  { key: "affection_90", title: "灵魂羁绊", description: "好感度达到90", icon: "💖", category: "affection", target: 90 },
  { key: "memory_3", title: "回忆初现", description: "累积3条记忆", icon: "📝", category: "memory", target: 3 },
  { key: "memory_10", title: "记忆之书", description: "累积10条记忆", icon: "📚", category: "memory", target: 10 },
  { key: "diary_5", title: "生活记录者", description: "生成5篇日记", icon: "📔", category: "diary", target: 5 },
  { key: "diary_20", title: "回忆史官", description: "生成20篇日记", icon: "📕", category: "diary", target: 20 },
  { key: "egg_1", title: "彩蛋猎人", description: "触发一个隐藏彩蛋", icon: "🥚", category: "hidden", target: 1 },
  { key: "egg_3", title: "秘密探索者", description: "触发三个隐藏彩蛋", icon: "🔮", category: "hidden", target: 3 },
  { key: "late_night", title: "夜猫子", description: "在凌晨和AI聊天", icon: "🦉", category: "hidden", target: 1 },
  { key: "early_bird", title: "早起鸟", description: "早上6点前和AI打招呼", icon: "🌅", category: "hidden", target: 1 },
  { key: "feedback_5", title: "品味鉴定师", description: "给AI的回复点赞5次", icon: "👍", category: "feedback", target: 5 },
  { key: "feedback_20", title: "最佳拍档", description: "给AI的回复点赞20次", icon: "🤝", category: "feedback", target: 20 },
  { key: "pomodoro_1", title: "专注新人", description: "完成1个番茄钟", icon: "🍅", category: "pomodoro", target: 1 },
  { key: "pomodoro_5", title: "效率大师", description: "完成5个番茄钟", icon: "⏱", category: "pomodoro", target: 5 },
  { key: "pomodoro_15", title: "心流之王", description: "完成15个番茄钟", icon: "🧠", category: "pomodoro", target: 15 },
];

/** checkAndUnlock 的上下文（各来源统计，缺省项跳过） */
export interface AchievementContext {
  totalMessages?: number; // chat 类
  streak?: number; // checkin 类（连续签到）
  affection?: number; // affection 类
  memories?: number; // memory 类
  diaries?: number; // diary 类
  eggs?: number; // hidden 彩蛋类
  feedbackLikes?: number; // feedback 类
  pomodoros?: number; // pomodoro 类
  /** 当前小时（用于 late_night[0-5] / early_bird[<6] 判定） */
  hours?: number;
  chatDays?: number; // 预留：天数维度
}

function recId(personaId: string, key: string): string {
  return `${personaId}:${key}`;
}

/** 加载全部成就（模板 ∪ 存档），首次访问自动建档 */
export async function getAllAchievements(personaId: string): Promise<AchievementRecord[]> {
  const stored = await getByPersona<AchievementRecord>("achievements", personaId);
  const byKey = new Map(stored.map((r) => [r.key, r]));
  const merged: AchievementRecord[] = [];
  for (const def of ACHIEVEMENT_CATALOG) {
    const exist = byKey.get(def.key);
    if (exist) {
      merged.push(exist);
    } else {
      const fresh: AchievementRecord = {
        id: recId(personaId, def.key),
        personaId,
        key: def.key,
        title: def.title,
        description: def.description,
        icon: def.icon,
        unlocked: false,
        progress: 0,
      };
      merged.push(fresh);
    }
  }
  // 惰性建档（仅新记录批量落库，避免每次全量写）
  const missing = merged.filter((m) => !byKey.has(m.key));
  if (missing.length) await putAll("achievements", missing);
  return merged;
}

/** 类别 → ctx 取值函数（对齐 Kotlin updateProgress 的 when(type)） */
function valueForCategory(category: string, ctx: AchievementContext): number | undefined {
  switch (category) {
    case "chat": return ctx.totalMessages;
    case "checkin": return ctx.streak;
    case "affection": return ctx.affection;
    case "memory": return ctx.memories;
    case "diary": return ctx.diaries;
    case "feedback": return ctx.feedbackLikes;
    case "pomodoro": return ctx.pomodoros;
    case "hidden": return ctx.eggs;
    default: return undefined;
  }
}

/**
 * 按上下文检查并解锁成就，返回本次新解锁的记录。
 * 每个新解锁 emit("achievement-unlocked", record)。
 */
export async function checkAndUnlock(personaId: string, ctx: AchievementContext): Promise<AchievementRecord[]> {
  const all = await getAllAchievements(personaId);
  const newly: AchievementRecord[] = [];

  for (const rec of all) {
    if (rec.unlocked) continue;
    const def = ACHIEVEMENT_CATALOG.find((d) => d.key === rec.key);
    if (!def) continue;
    let value = valueForCategory(def.category, ctx);

    // 时间型隐藏成就：hours 命中即视为达成（target=1）
    if (def.key === "late_night" && ctx.hours !== undefined && ctx.hours >= 0 && ctx.hours < 6) value = 1;
    if (def.key === "early_bird" && ctx.hours !== undefined && ctx.hours >= 0 && ctx.hours < 6) value = Math.max(value ?? 0, 1);
    if (value === undefined) continue;

    const rawProgress = Math.max(rec.progress * def.target, value); // 存的是 0~1，还原比较
    rec.progress = Math.min(1, rawProgress / def.target);
    if (rawProgress >= def.target) {
      rec.unlocked = true;
      rec.unlockedAt = Date.now();
      rec.progress = 1;
      newly.push(rec);
    }
    await put("achievements", rec);
  }

  for (const rec of newly) emit("achievement-unlocked", rec);
  return newly;
}

/** 直接解锁指定成就（Kotlin unlockAchievement） */
export async function unlockAchievement(personaId: string, key: string): Promise<AchievementRecord | null> {
  const rec = await getOne<AchievementRecord>("achievements", recId(personaId, key));
  if (!rec || rec.unlocked) return null;
  rec.unlocked = true;
  rec.unlockedAt = Date.now();
  rec.progress = 1;
  await put("achievements", rec);
  emit("achievement-unlocked", rec);
  return rec;
}

/** 已解锁数 / 总数（Kotlin unlockedCount / totalCount） */
export async function getAchievementSummary(personaId: string): Promise<{ unlocked: number; total: number }> {
  const all = await getAllAchievements(personaId);
  return { unlocked: all.filter((a) => a.unlocked).length, total: all.length };
}
