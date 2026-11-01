/**
 * 难忘时刻引擎 —— 移植自 memory/MemorableMomentsManager.kt + network/ApiClient.kt(scoreMemorableMoments)
 * 复刻规则：
 * - LLM 评分提示词逐字照抄 ApiClient.scoreMemorableMoments 的 systemPrompt
 *   （重要性+触动性双标准，1-10 分，仅 ≥8 分记录，分类 habit/preference/impression/detail，
 *   纯 JSON 数组输出）；解析逻辑对齐 parseScoredMoments（剥 markdown 围栏、取 [...] 段）；
 * - 存储上限 50 条、按分数降序、score<8 拒绝入库（Kotlin addMoments）；
 * - 评估时取最近 60 条聊天文本（Kotlin conversationTexts.takeLast(60)）。
 *
 * 偏差点：
 * 1. kinds.ts ScoredMemory 用 reason 字段承载 Kotlin 的 category（habit/preference/...）；
 * 2. Android 的持久化 store 是独立 prefs，PC 端 kv 键 memorable_moments_{personaId}；
 * 3. temperature 0.6 / maxTokens 500 与 Kotlin 请求体一致。
 */

import { kvGet, kvSet, newId } from "@/lib/db";
import type { ScoredMemory } from "./kinds";
import { simpleCompletion } from "./llmClient";
import { usePersonaStore } from "@/stores/usePersonaStore";

const MAX_MOMENTS = 50; // Kotlin moments.size > 50 截断
const MIN_SCORE = 8; // Kotlin score >= 8 才保存

interface MomentItem {
  content: string;
  score: number;
  category: string;
  timestamp: number;
}

function storeKey(personaId: string): string {
  return `memorable_moments_${personaId}`;
}

async function loadMoments(personaId: string): Promise<MomentItem[]> {
  return (await kvGet<MomentItem[]>(storeKey(personaId))) ?? [];
}

async function saveMoments(personaId: string, list: MomentItem[]): Promise<void> {
  await kvSet(storeKey(personaId), list.slice(0, MAX_MOMENTS));
}

/** 构建与 Kotlin 一致的 systemPrompt（personaName 取角色名，prompt 拼性格/说话风格/简介） */
function buildSystemPrompt(personaName: string): string {
  return [
    `你是「${personaName}」，正在回顾你和主人的聊天记录，提取值得铭记的事情。`,
    '请根据聊天内容，找出关于"主人的习惯、喜好、性格、生活方式"等信息。',
    "对每条信息打分（1-10分），评分标准：",
    "- 重要性：这条信息对了解主人有多重要",
    "- 触动性：如果主人看到这条被记住，会有多感动",
    "只有总分≥8分的信息才值得记录。",
    "分类：habit(习惯)、preference(喜好)、impression(印象)、detail(细节)",
    "输出格式为纯JSON数组，不要包含markdown代码块：",
    '[{"content":"主人喜欢在深夜喝热牛奶","score":9,"category":"habit"}]',
  ].join("\n");
}

/** 宽容解析 LLM 返回的 JSON 数组（对齐 Kotlin parseScoredMoments） */
function parseScoredMoments(text: string): { content: string; score: number; category: string }[] {
  try {
    let clean = text.trim().replace(/^```\s*json\s*/i, "").replace(/```$/, "").trim();
    const start = clean.indexOf("[");
    const end = clean.lastIndexOf("]");
    if (start < 0 || end <= start) return [];
    clean = clean.slice(start, end + 1);
    const arr = JSON.parse(clean) as Record<string, unknown>[];
    const results: { content: string; score: number; category: string }[] = [];
    for (const obj of arr) {
      const content = String(obj.content ?? "").trim();
      const score = Number(obj.score ?? 0);
      const category = String(obj.category ?? "detail");
      if (content && score >= MIN_SCORE) results.push({ content, score, category });
    }
    return results;
  } catch {
    return [];
  }
}

export interface EvaluateInput {
  role: "user" | "assistant" | "system" | string;
  content: string;
  timestamp?: number;
}

/**
 * 评估一段对话并入库高分时刻，返回本次新增的 ScoredMemory。
 * LLM 失败时静默返回空数组（Kotlin triggerMomentsScoring 的 catch 行为）。
 */
export async function evaluateAndScore(personaId: string, messages: EvaluateInput[]): Promise<ScoredMemory[]> {
  const texts = messages.filter((m) => m.content.trim()).slice(-60).map((m) => `${m.role === "user" ? "主人" : "AI"}：${m.content}`);
  if (!texts.length) return [];

  const persona = usePersonaStore.getState().getPersonaById(personaId);
  const personaName = persona?.name ?? "星尘";

  let raw = "";
  try {
    raw = await simpleCompletion(`以下是和主人的聊天记录，请提取值得铭记的事情：\n${texts.join("\n")}`, {
      system: buildSystemPrompt(personaName),
      temperature: 0.6,
      maxTokens: 500,
    });
  } catch {
    return []; // 网络/配置失败：与 Kotlin 一致，放弃本轮评分
  }

  const scored = parseScoredMoments(raw);
  if (!scored.length) return [];

  const moments = await loadMoments(personaId);
  const added: ScoredMemory[] = [];
  for (const s of scored) {
    const item: MomentItem = { content: s.content, score: s.score, category: s.category, timestamp: Date.now() };
    moments.unshift(item);
    added.push({ id: newId("mm"), personaId, content: s.content, score: s.score, reason: s.category, timestamp: item.timestamp });
  }
  moments.sort((a, b) => b.score - a.score);
  await saveMoments(personaId, moments);
  return added;
}

/** 直接添加一条已评分时刻（score<8 拒绝，对齐 Kotlin addMoment） */
export async function addMoment(personaId: string, content: string, score: number, category: string): Promise<boolean> {
  if (score < MIN_SCORE) return false;
  const moments = await loadMoments(personaId);
  moments.unshift({ content, score, category, timestamp: Date.now() });
  moments.sort((a, b) => b.score - a.score);
  await saveMoments(personaId, moments);
  return true;
}

/** 取前 k 条最难忘时刻（按分数降序） */
export async function getTopMoments(personaId: string, k = 10): Promise<ScoredMemory[]> {
  const moments = await loadMoments(personaId);
  return moments
    .sort((a, b) => b.score - a.score)
    .slice(0, k)
    .map((m, i) => ({ id: `${personaId}:mm:${i}:${m.timestamp}`, personaId, content: m.content, score: m.score, reason: m.category, timestamp: m.timestamp }));
}

/** 删除一条时刻（按 addMoment/getTopMoments 生成的 timestamp 匹配） */
export async function deleteMoment(personaId: string, timestamp: number): Promise<void> {
  const moments = await loadMoments(personaId);
  await saveMoments(personaId, moments.filter((m) => m.timestamp !== timestamp));
}
