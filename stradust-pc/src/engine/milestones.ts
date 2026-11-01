/**
 * 里程碑引擎 —— 移植自 milestone/MilestoneManager.kt
 * 复刻规则：
 * - recordMilestone：按 id(key) 去重，已存在返回 false（Kotlin @Synchronized）；
 * - 周年纪念日 getTodayAnniversaries：MM-dd 相同且距今 >1 天；
 * - shouldNotifyAnniversary：每天只提醒一次（notified_today 标记）；
 * - 周年文案（照抄 Kotlin getAnniversaryMessages）：≥365天→"X周年了呢！时间过得好快~"、
 *   ≥30天→"X个月了，一直陪着你哦~"、否则→"已经X天了~"。
 * checkAutomaticMilestones 对照 MainActivity 的自动打点逻辑：
 *   first_chat（首次对话）、affection_friend(≥60)、affection_close(≥90)、
 *   day_7/day_30/day_100（陪伴天数）。
 *
 * 偏差点：Android Milestone 有 category 字段而 kinds.ts MilestoneRecord 无 icon/category，
 * 这里 key 存语义 id（first_chat...），icon 由调用方传入（任务签名要求）。
 */

import { getByPersona, put, kvGet, kvSet } from "@/lib/db";
import type { MilestoneRecord } from "./kinds";
import { emit } from "@/lib/events";
import { getAffection } from "./affection";

function fmtMMDD(ts: number): string {
  const d = new Date(ts);
  return `${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** 记录一个里程碑；key 重复时返回 false（去重） */
export async function recordMilestone(
  personaId: string,
  key: string,
  title: string,
  desc: string,
  icon = "🏁",
): Promise<boolean> {
  const existing = await getByPersona<MilestoneRecord>("milestones", personaId);
  if (existing.some((m) => m.key === key)) return false;
  const rec: MilestoneRecord = {
    id: `${personaId}:${key}`,
    personaId,
    key,
    title,
    description: desc,
    icon,
    achievedAt: Date.now(),
  };
  await put("milestones", rec);
  emit("milestone-achieved", rec);
  return true;
}

/** 列出全部里程碑（按达成时间倒序，对齐 Kotlin loadMilestones） */
export async function listMilestones(personaId: string): Promise<MilestoneRecord[]> {
  const all = await getByPersona<MilestoneRecord>("milestones", personaId);
  return all.sort((a, b) => b.achievedAt - a.achievedAt);
}

/** 是否已有某里程碑（Kotlin hasMilestone） */
export async function hasMilestone(personaId: string, key: string): Promise<boolean> {
  const all = await getByPersona<MilestoneRecord>("milestones", personaId);
  return all.some((m) => m.key === key);
}

/** 今日到期周年列表（Kotlin getTodayAnniversaries） */
export async function getTodayAnniversaries(personaId: string): Promise<MilestoneRecord[]> {
  const all = await getByPersona<MilestoneRecord>("milestones", personaId);
  const today = fmtMMDD(Date.now());
  return all.filter(
    (m) => fmtMMDD(m.achievedAt) === today && m.achievedAt < Date.now() - 86400000,
  );
}

/** 周年祝福文案（Kotlin getAnniversaryMessages 逐字照抄规则） */
export async function getAnniversaryMessages(personaId: string): Promise<string[]> {
  const list = await getTodayAnniversaries(personaId);
  return list.map((m) => {
    const days = Math.floor((Date.now() - m.achievedAt) / 86400000);
    if (days >= 365) return `${m.title}已经${Math.floor(days / 365)}周年了呢！时间过得好快~`;
    if (days >= 30) return `${m.title}已经${Math.floor(days / 30)}个月了，一直陪着你哦~`;
    return `${m.title}已经${days}天了~`;
  });
}

/** 今天是否应触发周年提醒（每天仅一次，Kotlin shouldNotifyAnniversary + notified_today） */
export async function shouldNotifyAnniversary(personaId: string): Promise<boolean> {
  const kKey = `milestone-notified-${personaId}`;
  const last = await kvGet<string>(kKey);
  const today = todayStr();
  if (last === today) return false;
  const anniversaries = await getTodayAnniversaries(personaId);
  if (anniversaries.length > 0) {
    await kvSet(kKey, today);
    return true;
  }
  return false;
}

export interface MilestoneAutoContext {
  /** 用户消息总数（首次对话判定用 totalMessages>=1） */
  totalMessages?: number;
  /** 好感度（不传则从 affection.ts 读取） */
  affection?: number;
  /** 陪伴天数（不传则从 affection.ts 读取 chatDays） */
  chatDays?: number;
  /** 语音通话次数（>0 时记 first_call，对齐 MainActivity 第一次通话） */
  callCount?: number;
}

/**
 * 自动里程碑检查（对照 Kotlin MainActivity 的散点打点）：
 * - first_chat：第一次对话（MainActivity L1639）
 * - first_call：第一次通话（L827）
 * - affection_friend ≥60 / affection_close ≥90（updateAffectionDisplay L2331-2332）
 * - day_7 / day_30 / day_100：陪伴满 7/30/100 天（PC 扩展，Kotlin 无对应数值来源）
 */
export async function checkAutomaticMilestones(
  personaId: string,
  ctx: MilestoneAutoContext = {},
): Promise<MilestoneRecord[]> {
  const aff = await getAffection(personaId);
  const totalMessages = ctx.totalMessages ?? aff?.totalMessages ?? 0;
  const affection = ctx.affection ?? aff?.points ?? 0;
  const chatDays = ctx.chatDays ?? aff?.chatDays ?? 0;
  const created: MilestoneRecord[] = [];

  const track = async (key: string, title: string, desc: string, icon: string) => {
    if (await recordMilestone(personaId, key, title, desc, icon)) {
      const all = await getByPersona<MilestoneRecord>("milestones", personaId);
      const rec = all.find((m) => m.key === key);
      if (rec) created.push(rec);
    }
  };

  if (totalMessages >= 1) await track("first_chat", "第一次对话", "和星尘的第一次对话", "💬");
  if ((ctx.callCount ?? 0) >= 1) await track("first_call", "第一次通话", "第一次和星尘打电话", "📞");
  if (affection >= 60) await track("affection_friend", "成为朋友", "好感度达到朋友级别", "🤝");
  if (affection >= 90) await track("affection_close", "亲密伙伴", "好感度达到亲密级别", "💞");
  if (chatDays >= 7) await track("day_7", "七日同行", "相伴走过第7天", "📅");
  if (chatDays >= 30) await track("day_30", "一月之约", "相伴走过第30天", "🌙");
  if (chatDays >= 100) await track("day_100", "百日羁绊", "相伴走过第100天", "🎆");

  return created;
}
