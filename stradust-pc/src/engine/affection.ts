/**
 * 好感度引擎 —— 移植自 affection/AffectionManager.kt
 * 复刻规则：
 * - 初始好感度 50（Kotlin prefs 默认值），区间 0~100（coerceIn）；
 * - 每日首次活跃：chatDays +1、今日消息清零、+1 好感（updateDailyStats）；
 * - DailyStats（getDailyStats）：todayMessages / todayAffectionChange（Kotlin affection_change_today）；
 * - evaluateUserBehavior 计分（仅用户消息，对齐 MainActivity 调用点）：
 *   冒犯 -2 并终止；HAPPY +1 / ANGRY -1 / 傲娇(TSUNDERE≈shy) +1；好话词表 +1；坏话词表 -2；
 * - decreaseAffection 在好感度 ≥90 时不扣分（Kotlin 保护逻辑，忠实保留）；
 * - 每日好感变动上限 DAILY_CHANGE_CAP（Kotlin 无硬性上限，此处防刷兜底，见偏差说明）。
 *
 * 偏差点（详见交付报告）：
 * 1. Android 好感度是全局 SharedPreferences，本实现按 personaId 存 IndexedDB "affection"；
 * 2. kinds.ts AffectionState.points = Kotlin affectionLevel(0~100)，level = 换算的 1~6 级数字；
 *    契约无 todayAffectionChange 字段 → 用 kv 键 affection_stats_{personaId} 承载每日增量与上限计数；
 * 3. isOffensive（Kotlin 由外部安全过滤传入）以本地冒犯词表近似判定。
 */

import { getOne, put, kvGet, kvSet } from "@/lib/db";
import type { AffectionState } from "./kinds";
import { usePersonaStore } from "@/stores/usePersonaStore";

/** 等级-称号表（完整抄自 AffectionManager.getAffectionTitle 的 when 区间） */
export const AFFECTION_LEVELS: { level: number; min: number; max: number; title: string }[] = [
  { level: 1, min: 0, max: 20, title: "陌生人" },
  { level: 2, min: 21, max: 40, title: "认识的人" },
  { level: 3, min: 41, max: 60, title: "朋友" },
  { level: 4, min: 61, max: 80, title: "好朋友" },
  { level: 5, min: 81, max: 95, title: "亲密伙伴" },
  { level: 6, min: 96, max: 100, title: "最重要的人" },
];

/** 由好感度分值（0~100）查称号 */
export function getAffectionTitle(points: number): string {
  const row = AFFECTION_LEVELS.find((l) => points >= l.min && points <= l.max);
  return row ? row.title : "未知";
}

/** 由好感度分值查等级数字（1~6） */
export function getAffectionLevel(points: number): number {
  const row = AFFECTION_LEVELS.find((l) => points >= l.min && points <= l.max);
  return row ? row.level : 1;
}

/** 好话词表（照抄 Kotlin positiveWords） */
const POSITIVE_WORDS = ["谢谢", "爱你", "可爱", "棒", "喜欢", "好乖"];
/** 坏话词表（照抄 Kotlin negativeWords，兼作 isOffensive 近似判定） */
const NEGATIVE_WORDS = ["滚", "烦", "笨", "傻", "讨厌", "闭嘴", "别吵"];

/** 初始好感度（Kotlin 默认 50） */
const INITIAL_POINTS = 50;
/** 每日好感变动绝对值上限（防刷兜底，0 = 关闭） */
export const DAILY_CHANGE_CAP = 20;

/** 每日统计（kv 承载，对齐 Kotlin DailyStats(affection_change_today)） */
interface DailyCounter {
  date: string; // yyyy-MM-dd
  change: number; // 今日累计好感变化（带符号）
}

function statsKey(personaId: string): string {
  return `affection_stats_${personaId}`;
}

async function loadDaily(personaId: string, today: string): Promise<DailyCounter> {
  const c = await kvGet<DailyCounter>(statsKey(personaId));
  if (!c || c.date !== today) return { date: today, change: 0 };
  return c;
}

/** yyyy-MM-dd（本地时区，对齐 SimpleDateFormat("yyyy-MM-dd")） */
function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** 读取好感度状态；不存在则建档；跨天执行 updateDailyStats（含落库） */
export async function getAffection(personaId: string): Promise<AffectionState | null> {
  if (!personaId) return null;
  const today = todayStr();
  let st = await getOne<AffectionState>("affection", personaId);
  if (!st) {
    // 首日建档：chatDays=1、+1 好感（Kotlin init→updateDailyStats 首次进入即触发换日分支）
    st = {
      id: personaId,
      personaId,
      points: Math.min(100, INITIAL_POINTS + 1),
      level: 1,
      title: "",
      todayMessages: 0,
      todayDate: today,
      totalMessages: 0,
      chatDays: 1,
      lastChatDate: today,
      updatedAt: Date.now(),
    };
    st.level = getAffectionLevel(st.points);
    st.title = getAffectionTitle(st.points);
    await put("affection", st);
    await kvSet(statsKey(personaId), { date: today, change: 1 });
    syncPersona(personaId, st.points);
    return st;
  }
  if (st.lastChatDate !== today) {
    // updateDailyStats：换天 → 天数+1、每日计数清零、+1 好感
    st = {
      ...st,
      chatDays: st.chatDays + 1,
      todayMessages: 0,
      todayDate: today,
      lastChatDate: today,
      points: Math.min(100, st.points + 1),
      updatedAt: Date.now(),
    };
    st.level = getAffectionLevel(st.points);
    st.title = getAffectionTitle(st.points);
    await put("affection", st);
    await kvSet(statsKey(personaId), { date: today, change: 1 });
    syncPersona(personaId, st.points);
  }
  return st;
}

/** 同步 persona 上的好感度字段（favorability/favorabilityLevel/favorabilityTitle） */
function syncPersona(personaId: string, points: number): void {
  try {
    const store = usePersonaStore.getState();
    const p = store.getPersonaById(personaId);
    if (p) {
      // 好感度字段不在 PersonaCreateParams 内，但 store 的合并逻辑支持任意 Persona 字段 → 显式转型
      store.updatePersona(personaId, {
        favorability: points,
        favorabilityLevel: getAffectionLevel(points),
        favorabilityTitle: getAffectionTitle(points),
      } as never);
    }
  } catch {
    /* persona store 不可用时静默跳过 */
  }
}

/**
 * 应用一次好感度增减（含 ≥90 不降保护、每日上限、clamp 0~100）。
 * 返回实际生效的变化量（可能因保护/上限被削减为 0）。
 */
async function applyDelta(state: AffectionState, amount: number): Promise<number> {
  if (amount === 0) return 0;
  // Kotlin decreaseAffection：≥90 时拒绝下降
  if (amount < 0 && state.points >= 90) return 0;
  const daily = await loadDaily(state.personaId, state.todayDate || todayStr());
  let applied = amount;
  if (DAILY_CHANGE_CAP > 0) {
    // 每日上限：|今日累计变化| 不超过 DAILY_CHANGE_CAP（正向到顶不再加，负向到底不再减）
    applied = Math.min(applied, DAILY_CHANGE_CAP - daily.change);
    applied = Math.max(applied, -DAILY_CHANGE_CAP - daily.change);
  }
  if (applied === 0) return 0;
  const before = state.points;
  state.points = Math.max(0, Math.min(100, state.points + applied));
  const actual = state.points - before;
  if (actual !== 0) {
    daily.change += actual;
    await kvSet(statsKey(state.personaId), daily);
    state.level = getAffectionLevel(state.points);
    state.title = getAffectionTitle(state.points);
  }
  return actual;
}

/** 手动增减好感度（点赞等外部事件入口，Kotlin addAffection/decreaseAffection） */
export async function addAffection(personaId: string, amount: number, _reason = ""): Promise<AffectionState | null> {
  const state = await getAffection(personaId);
  if (!state) return null;
  await applyDelta(state, amount);
  state.updatedAt = Date.now();
  await put("affection", state);
  syncPersona(personaId, state.points);
  return state;
}

/** 每日统计（Kotlin getDailyStats → DailyStats(messagesToday, affectionChange)） */
export async function getDailyStats(personaId: string): Promise<{ messagesToday: number; affectionChange: number } | null> {
  const state = await getAffection(personaId);
  if (!state) return null;
  const daily = await loadDaily(personaId, state.todayDate || todayStr());
  return { messagesToday: state.todayMessages, affectionChange: daily.change };
}

/**
 * 处理一条消息的好感度计分（任务签名 processMessage）。
 * 仅当 isUser=true 时计分（对齐 Android：AI 消息不影响好感度）。
 * emotion 为 PC 端 Emotion 名（happy/sad/angry/shy…），缺省 neutral。
 */
export async function processMessage(
  personaId: string,
  isUser: boolean,
  opts?: { message?: string; emotion?: string; isOffensive?: boolean },
): Promise<AffectionState | null> {
  const state = await getAffection(personaId);
  if (!state) return null;
  if (!isUser) {
    await put("affection", { ...state, updatedAt: Date.now() });
    return state;
  }

  // DailyStats：消息计数（Kotlin addMessage）
  const today = todayStr();
  state.todayMessages += 1;
  state.totalMessages += 1;
  state.lastChatDate = today;
  state.todayDate = today;

  const msg = (opts?.message ?? "").toLowerCase();
  const emotion = opts?.emotion ?? "neutral";
  const offensive = opts?.isOffensive ?? NEGATIVE_WORDS.some((w) => msg.includes(w.toLowerCase()));

  if (offensive) {
    // Kotlin isOffensive 分支：-2 后直接 return
    await applyDelta(state, -2);
  } else {
    switch (emotion) {
      case "happy":
      case "excited":
      case "proud":
        await applyDelta(state, 1); // 用户心情好
        break;
      case "angry":
        await applyDelta(state, -1); // 用户对AI生气
        break;
      case "shy":
        await applyDelta(state, 1); // 傲娇互动（Android TSUNDERE → PC shy 近似）
        break;
      default:
        break; // sad/surprised/neutral 等不加分（Kotlin 空分支）
    }
    if (POSITIVE_WORDS.some((w) => msg.includes(w.toLowerCase()))) await applyDelta(state, 1);
    if (NEGATIVE_WORDS.some((w) => msg.includes(w.toLowerCase()))) await applyDelta(state, -2);
  }

  state.updatedAt = Date.now();
  await put("affection", state);
  syncPersona(personaId, state.points);
  return state;
}
