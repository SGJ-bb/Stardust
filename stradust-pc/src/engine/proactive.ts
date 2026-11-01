/**
 * 主动搭话引擎 —— 移植自 Android interaction/ProactiveInteractionEngine.kt。
 *
 * 规则忠实照搬：
 * - nagFrequency 概率闸门（shouldTriggerNag: low<0.1 / medium<0.3 / high<0.6 / off=false）；
 * - 间隔阈值（frequent 3min / normal 10min / infrequent 30min，else 10min）；
 * - shouldTriggerInteraction 的 5 分钟无互动阈值；
 * - 各类文案池（idle/frequent/normal/infrequent/驱动力 curiosity/social/autonomy）。
 *
 * Web 等价替代说明：
 * - Android 好感度/驱动力参与决策依赖 SubjectivityEngine（本项目未移植），此处以
 *   engine/affection.ts 的 AffectionState.level 做轻量映射（高好感 → 更亲近的话术提示），
 *   驱动力冷却（60s、30% 概率触发）照搬但无 SubjectivityEngine 时不触发。
 * - settings.rhythm.nagFrequency("low"|"medium"|"high"|"off") → Kotlin getNagFrequency()
 *   映射（HIGH→frequent / LOW→infrequent / MEDIUM,OFF→normal），间隔按映射值取。
 * - LLM 生成通过 setProactiveGenerator 注入（Phase3 接聊天引擎）；未注入时用 Kotlin 原文案池。
 */

import * as db from "@/lib/db";
import { emit } from "@/lib/events";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { getAffection } from "./affection";
import type { ProactiveLogEntry } from "./kinds";

// ─── 文案池（照搬 Kotlin） ───

const IDLE_PHRASES = [
  "主人~ 在忙什么呢？",
  "好无聊喵...",
  "主人已经很久没理我了...",
  "喵？主人还在吗？",
  "我饿了...（其实是想主人了）",
];

const FREQUENT_PHRASES = [
  "主人~在看什么呢？", "喵喵~需要帮忙吗？", "有点无聊诶...",
  "主人不觉得今天天气很好吗？", "我刚刚学到了一个新词！", "想听我讲个笑话吗？",
  "主人今天的任务完成了吗？", "要不要起来活动一下？", "喝口水吧，别太累了~",
];

const NORMAL_PHRASES = [
  "主人今天心情怎么样？", "有什么有趣的事情吗？", "我一直在想主人呢~",
  "主人好久没和我聊天了", "今天过得怎么样呀？", "要不要一起放松一下？",
  "我感觉主人可能需要一个拥抱", "主人辛苦了，休息一下吧",
];

const INFREQUENT_PHRASES = [
  "好久不见！好想你呀~", "主人终于来了！", "最近过得还好吗？",
  "感觉好久没聊天了呢", "今天有什么新鲜事吗？", "主人不在的时候我自己想了很多...",
];

const CURIOSITY_PHRASES = [
  "对了，你之前说的那个事，后来怎么样了？",
  "我一直很好奇，你为什么喜欢那个？",
  "你有没有想过试试别的方法？",
];

const SOCIAL_PHRASES = [
  "你今天过得怎么样？想听你说说~",
  "最近有没有什么开心的事？",
  "我有点想你了...",
];

const AUTONOMY_PHRASES = [
  "我觉得我们可以换个方式聊，不要总是问我问题嘛~",
  "让我也主动说点什么吧！",
];

const pick = <T,>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)];

// ─── 频率映射（照搬 SettingsManager.getNagFrequency + shouldTriggerNag） ───

type NagLevel = "frequent" | "normal" | "infrequent";

function mapRhythmToLevel(rhythm?: "low" | "medium" | "high" | "off"): NagLevel {
  switch (rhythm ?? "medium") {
    case "high": return "frequent";
    case "low": return "infrequent";
    default: return "normal"; // MEDIUM/OFF → normal（OFF 由概率闸门拦截）
  }
}

function shouldTriggerNag(rhythm?: "low" | "medium" | "high" | "off"): boolean {
  switch (rhythm ?? "medium") {
    case "low": return Math.random() < 0.1;
    case "medium": return Math.random() < 0.3;
    case "high": return Math.random() < 0.6;
    case "off": return false;
  }
}

/** 间隔毫秒（照搬 shouldTriggerInteraction 的 when(nagFrequency)） */
function intervalMs(level: NagLevel): number {
  switch (level) {
    case "frequent": return 3 * 60 * 1000;
    case "infrequent": return 30 * 60 * 1000;
    default: return 10 * 60 * 1000;
  }
}

// ─── 状态 ───

let lastInteractionTime = Date.now(); // 用户最近活跃时刻（notifyUserActivity 重置）
let lastNagTime = 0;                  // 上次搭话时刻
let lastCuriosityAction = 0;
let lastSocialAction = 0;
let lastAutonomyAction = 0;
const ACTION_COOLDOWN_MS = 60 * 1000; // 照搬 60 秒冷却

export type DriveActionType = "curiosity" | "social" | "autonomy";

type ProactiveGenerator = (personaId: string, hint: string) => Promise<string>;
let generator: ProactiveGenerator | null = null;

/** 注入 LLM 生成器（hint 为文案方向引导；Phase3 由聊天引擎接入） */
export function setProactiveGenerator(fn: ProactiveGenerator | null): void {
  generator = fn;
}

/** 用户有交互（发消息/操作）时调用：重置空闲计时（对齐 lastInteractionTime 更新） */
export function notifyUserActivity(): void {
  lastInteractionTime = Date.now();
}

/** 规则判定：本刻是否应触发搭话（导出便于测试与上层复用） */
export function shouldTriggerNow(now = Date.now()): { trigger: boolean; level: NagLevel; reason: string } {
  const rhythm = useSettingsStore.getState().settings.rhythm?.nagFrequency;
  const level = mapRhythmToLevel(rhythm);
  if (!shouldTriggerNag(rhythm)) {
    return { trigger: false, level, reason: `概率闸门未通过(nagFrequency=${rhythm ?? "medium"})` };
  }
  // 双条件：距上次搭话超过间隔 且 用户空闲超过 5 分钟（两条 Kotlin 规则合并）
  const sinceNag = now - lastNagTime;
  const sinceIdle = now - lastInteractionTime;
  if (sinceNag <= intervalMs(level)) {
    return { trigger: false, level, reason: `距上次搭话 ${Math.round(sinceNag / 1000)}s 未达间隔 ${intervalMs(level) / 1000}s` };
  }
  if (sinceIdle <= 5 * 60 * 1000) {
    return { trigger: false, level, reason: "用户仍在活跃（不足5分钟）" };
  }
  return { trigger: true, level, reason: `空闲 ${Math.round(sinceIdle / 60000)} 分钟` };
}

/** 文案方向选择（时间/活跃度/好感度决定 hint；照搬 phrase 池分支 + 好感度亲密度语气） */
async function buildHint(personaId: string, level: NagLevel, hour: number): Promise<string> {
  const parts: string[] = [];
  // 时段方向（对齐 WakeUpReceiver 的时间语义 + 好感度）
  if (hour < 6) parts.push("现在是深夜，关心用户是否还没睡，语气体贴不打扰");
  else if (hour < 12) parts.push("现在是早上，用元气满满的口吻开启新一天");
  else if (hour < 18) parts.push("现在是下午，轻松闲聊问问用户在做什么");
  else parts.push("现在是晚上，温柔地问候用户今天过得怎么样");

  parts.push(`搭话频率档位为「${level === "frequent" ? "频繁黏人" : level === "infrequent" ? "久别重逢" : "自然日常"}」，参考风格：${
    level === "frequent" ? pick(FREQUENT_PHRASES) : level === "infrequent" ? pick(INFREQUENT_PHRASES) : pick(NORMAL_PHRASES)
  }`);

  try {
    const aff = await getAffection(personaId);
    if (aff) {
      parts.push(aff.level >= 5
        ? "你们好感度很高，可以亲密撒娇"
        : aff.level <= 1
          ? "好感度还不高，语气克制友好、不要过度亲昵"
          : "关系正在升温，自然亲切即可");
    }
  } catch { /* 好感度不可用时忽略该维度 */ }

  return parts.join("；");
}

/** 单次评估（tick 调用）：命中则生成/选文案 → 存 proactiveLog → emit */
async function evaluate(personaId: string): Promise<void> {
  const now = Date.now();
  const { trigger, level, reason } = shouldTriggerNow(now);
  if (!trigger) return;

  // 驱动力随机行为（SubjectivityEngine 未移植：仅当外部注入 hint 时可用，这里保留冷却骨架）
  let content = "";
  const hint = await buildHint(personaId, level, new Date(now).getHours());
  if (generator) {
    try {
      content = (await generator(personaId, hint)).trim();
    } catch { /* 生成失败落回文案池 */ }
  }
  if (!content) {
    content = level === "frequent" ? pick(FREQUENT_PHRASES) : level === "infrequent" ? pick(INFREQUENT_PHRASES) : pick(NORMAL_PHRASES);
  }

  lastNagTime = now;
  lastInteractionTime = now; // 搭话后重置空闲窗（避免连环轰炸）

  const entry: ProactiveLogEntry = {
    id: db.newId("pro"),
    personaId,
    content,
    reason,
    timestamp: now,
  };
  await db.put("proactiveLog", entry);
  emit("proactive-message", { personaId, content });
  emit("data-changed");
}

// ─── 调度（module 级 handle，start/stop 去重防 HMR 泄漏） ───

let engineTimer: ReturnType<typeof setInterval> | null = null;
let personaIdProvider: (() => string | null) | null = null;

const TICK_MS = 60 * 1000; // 每分钟评估一次（间隔粒度为分钟，足够）

/** 启动引擎；personaIdProvider 返回当前活跃角色 id（null 时空转） */
export function startProactiveEngine(pidProvider: () => string | null): void {
  personaIdProvider = pidProvider;
  if (engineTimer !== null) return;
  engineTimer = setInterval(() => {
    const pid = personaIdProvider?.();
    if (!pid) return;
    if (!useSettingsStore.getState().settings.aiFeatures?.proactiveEnabled && useSettingsStore.getState().settings.aiFeatures) return;
    void evaluate(pid).catch((e) => console.warn("[proactive] evaluate failed:", (e as Error).message));
  }, TICK_MS);
}

export function stopProactiveEngine(): void {
  if (engineTimer !== null) {
    clearInterval(engineTimer);
    engineTimer = null;
  }
}

// ─── 供聊天引擎回写的辅助 API ───

/** 记录一次驱动力类主动行为（冷却骨架照搬：60s per type，30% 概率由调用方判定） */
export function markDriveAction(type: DriveActionType): void {
  const now = Date.now();
  if (type === "curiosity") lastCuriosityAction = now;
  else if (type === "social") lastSocialAction = now;
  else lastAutonomyAction = now;
}

/** 各驱动力是否已过冷却（SubjectivityEngine 移植后可作为候选筛选依据） */
export function driveActionAvailable(type: DriveActionType, now = Date.now()): boolean {
  const last = type === "curiosity" ? lastCuriosityAction : type === "social" ? lastSocialAction : lastAutonomyAction;
  return now - last > ACTION_COOLDOWN_MS;
}

/** 取一条驱动力文案（照搬 getDriveActionPhrase 的池子） */
export function getDriveActionPhrase(type: DriveActionType): string {
  const now = Date.now();
  if (type === "curiosity") { lastCuriosityAction = now; return pick(CURIOSITY_PHRASES); }
  if (type === "social") { lastSocialAction = now; return pick(SOCIAL_PHRASES); }
  lastAutonomyAction = now;
  return pick(AUTONOMY_PHRASES);
}
