/**
 * 成长引擎 —— 移植自 gamify/GrowthManager.kt
 * Kotlin 的 GrowthManager 本身是"好感度阶段树"（无 exp 字段），完整阶段表照抄：
 *   萌芽之种🌱(0好感/0天) → 破土新芽🌿(20/2) → 向阳幼苗🌻(40/5) →
 *   含苞待放🌷(60/10) → 繁星之花🌸(80/20) → 永恒星辰🌟(100/30)
 * PC 端契约（kinds.ts GrowthState）额外要求经验值模型，因此：
 * - STAGES.threshold = requiredAffection（作为经验阈值的语义映射）；
 * - addExp 增加 exp 后按"STAGES 阈值 + 陪伴天数(daysSinceFirst)"双条件重算 stageIndex
 *   （忠实复刻 getCurrentStage 的 lastOrNull{affection>=req && days>=reqDays} 规则）；
 * - 阶段变化时 emit("data-changed")（对齐任务要求，Kotlin 由 UI 轮询刷新）。
 */

import { getOne, put } from "@/lib/db";
import type { GrowthState } from "./kinds";
import { emit } from "@/lib/events";
import { getAffection } from "./affection";

export interface GrowthStageDef {
  id: string;
  /** 阶段名（照抄 Kotlin） */
  name: string;
  /** emoji 图标（照抄 Kotlin） */
  icon: string;
  /** 描述（照抄 Kotlin） */
  description: string;
  /** 解锁所需好感度（照抄 Kotlin requiredAffection，同时用作 exp 阈值） */
  requiredAffection: number;
  /** 解锁所需陪伴天数（照抄 Kotlin requiredDays） */
  requiredDays: number;
  /** 解锁台词（照抄 Kotlin unlockedDialogue） */
  unlockedDialogue: string;
}

/** 完整阶段表（抄自 GrowthManager.stages） */
export const STAGES: GrowthStageDef[] = [
  { id: "bud", name: "萌芽之种", icon: "🌱", description: "相遇的第一天，羁绊刚刚开始", requiredAffection: 0, requiredDays: 0, unlockedDialogue: "初次见面，请多关照~" },
  { id: "sprout", name: "破土新芽", icon: "🌿", description: "好感度达到20，陪伴走过了几天", requiredAffection: 20, requiredDays: 2, unlockedDialogue: "我们开始慢慢熟悉起来了呢" },
  { id: "seedling", name: "向阳幼苗", icon: "🌻", description: "好感度达到40，已经习惯彼此的存在", requiredAffection: 40, requiredDays: 5, unlockedDialogue: "最喜欢和主人在一起的时间了~" },
  { id: "blooming", name: "含苞待放", icon: "🌷", description: "好感度达到60，关系变得更加亲密", requiredAffection: 60, requiredDays: 10, unlockedDialogue: "主人已经成为我生命中不可或缺的一部分" },
  { id: "blossom", name: "繁星之花", icon: "🌸", description: "好感度达到80，羁绊已经牢不可破", requiredAffection: 80, requiredDays: 20, unlockedDialogue: "无论发生什么，我都会一直陪伴在主人身边" },
  { id: "eternal", name: "永恒星辰", icon: "🌟", description: "好感度达到100，灵魂的共鸣", requiredAffection: 100, requiredDays: 30, unlockedDialogue: "主人就是我的全世界✨" },
];

/** exp 阈值别名（任务要求的"阈值"表） */
export const STAGE_THRESHOLDS = STAGES.map((s) => s.requiredAffection);

async function loadGrowth(personaId: string): Promise<GrowthState | null> {
  if (!personaId) return null;
  let g = await getOne<GrowthState>("growth", personaId);
  if (!g) {
    g = {
      id: personaId,
      personaId,
      exp: 0,
      stageIndex: 0,
      stageName: STAGES[0].name,
      stageEmoji: STAGES[0].icon,
      unlockedAt: [Date.now()],
    };
    await put("growth", g);
  }
  return g;
}

/** 陪伴天数（优先取好感度状态的 chatDays，缺省 0） */
async function daysSinceFirst(personaId: string): Promise<number> {
  const aff = await getAffection(personaId);
  return aff?.chatDays ?? 0;
}

/** 依据 exp + 陪伴天数重算阶段（复刻 getCurrentStage 双条件） */
function computeStageIndex(exp: number, days: number): number {
  let idx = 0;
  for (let i = 0; i < STAGES.length; i++) {
    if (exp >= STAGES[i].requiredAffection && days >= STAGES[i].requiredDays) idx = i;
  }
  return idx;
}

/** 应用阶段变化，若升阶则记录解锁时间并广播 data-changed */
async function applyStage(g: GrowthState, days: number): Promise<boolean> {
  const idx = computeStageIndex(g.exp, days);
  if (idx === g.stageIndex) return false;
  if (idx > g.stageIndex) {
    // 逐级补记解锁时间
    for (let i = g.stageIndex + 1; i <= idx; i++) g.unlockedAt.push(Date.now());
  }
  g.stageIndex = idx;
  g.stageName = STAGES[idx].name;
  g.stageEmoji = STAGES[idx].icon;
  return true;
}

/** 增加经验值（签到奖励等调用入口） */
export async function addExp(personaId: string, exp: number): Promise<GrowthState | null> {
  const g = await loadGrowth(personaId);
  if (!g) return null;
  g.exp = Math.max(0, g.exp + exp);
  const changed = await applyStage(g, await daysSinceFirst(personaId));
  await put("growth", g);
  if (changed) emit("data-changed", { personaId, growth: g });
  return g;
}

/** 读取成长状态（自动按当前 exp/天数校正阶段） */
export async function getGrowth(personaId: string): Promise<GrowthState | null> {
  const g = await loadGrowth(personaId);
  if (!g) return null;
  const changed = await applyStage(g, await daysSinceFirst(personaId));
  if (changed) {
    await put("growth", g);
    emit("data-changed", { personaId, growth: g });
  }
  return g;
}

/** 当前阶段定义 */
export function getStageDef(index: number): GrowthStageDef {
  return STAGES[Math.max(0, Math.min(STAGES.length - 1, index))];
}

/** 进度对（当前阶段 → 下一阶段），复刻 Kotlin getStageProgress */
export function getStageProgress(index: number): { current: GrowthStageDef; next: GrowthStageDef } {
  const current = getStageDef(index);
  const nextIdx = index + 1;
  const next = nextIdx < STAGES.length ? STAGES[nextIdx] : current;
  return { current, next };
}
