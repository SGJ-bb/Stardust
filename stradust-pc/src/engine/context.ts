/**
 * 记忆与上下文管理 —— 移植自 Android memory/：
 *   ContextManager.kt、MemoryPool.kt、GlobalMemoryPool.kt、SessionManager.kt、MemoryManager.kt
 * 持久化：IndexedDB（lib/db.ts）
 *   - memoryPool        → PoolEntry（主记忆条目，含结构化字段；细节条目 category="细节"）
 *   - globalMemoryPool  → PoolEntry（跨场景共享记忆）
 *   - memories          → MemoryFact（长期事实，对齐 MemoryManager）
 *   - sessions          → SessionInfo + meta 标记（当前/历史会话）
 *   - kv                → ctx:{personaId}（ContextManager 计数状态）、pool:{personaId}（整合计数）
 */

import { getByPersona, getOne, kvGet, kvSet, newId, put, remove } from "@/lib/db";
import type { StoredRecord } from "@/lib/db";
import { simpleCompletion } from "./llmClient";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { MemoryFact, SessionInfo } from "./kinds";

// ─── 池内条目（超集对齐 MemoryPool.kt data class MemoryEntry） ───
export interface PoolEntry extends StoredRecord {
  id: string;
  personaId: string;
  content: string;
  category: string; // 总结 / 细节 / 继承 / 角色概要 / 全局 ...
  timestamp: number;
  sourceTurn: number;
  eventTime: string;
  place: string;
  people: string;
  event: string;
  scene: string;
  details: string;
  relationships: string;
  isGlobal: boolean;
  importance: number; // 0~1
}

function emptyPoolEntry(personaId: string, partial: Partial<PoolEntry>): PoolEntry {
  return {
    id: partial.id ?? newId("mem"),
    personaId,
    content: partial.content ?? "",
    category: partial.category ?? "总结",
    timestamp: partial.timestamp ?? Date.now(),
    sourceTurn: partial.sourceTurn ?? 0,
    eventTime: partial.eventTime ?? "",
    place: partial.place ?? "",
    people: partial.people ?? "",
    event: partial.event ?? "",
    scene: partial.scene ?? "",
    details: partial.details ?? "",
    relationships: partial.relationships ?? "",
    isGlobal: partial.isGlobal ?? false,
    importance: partial.importance ?? 1.0,
  };
}

/** 结构化文本（对齐 MemoryEntry.toStructuredText） */
export function structuredText(entry: PoolEntry): string {
  const parts: string[] = [];
  if (entry.eventTime) parts.push(`时间:${entry.eventTime}`);
  if (entry.place) parts.push(`地点:${entry.place}`);
  if (entry.people) parts.push(`人物:${entry.people}`);
  if (entry.event) parts.push(`事件:${entry.event}`);
  if (entry.scene) parts.push(`场景:${entry.scene}`);
  if (entry.details) parts.push(`细节:${entry.details}`);
  if (entry.relationships) parts.push(`关系:${entry.relationships}`);
  return parts.length > 0 ? parts.join(" | ") : entry.content;
}

function lineEntry(entry: PoolEntry, bullet: string): string {
  const structured = structuredText(entry);
  return structured !== entry.content && entry.event ? `${bullet} ${structured}` : `${bullet} ${entry.content}`;
}

// ─── 配置常量（对齐 AppConfig / MemoryPool / GlobalMemoryPool / ContextManager） ───
const MEMORY_MAX_CHARS_DEFAULT = 650; // AppConfig.MEMORY_MAX_CHARS_DEFAULT
const MEMORY_IMPORTANCE_THRESHOLD = 0.3; // AppConfig.MEMORY_IMPORTANCE_THRESHOLD
const CONSOLIDATE_INTERVAL = 10; // MemoryPool.CONSOLIDATE_INTERVAL
const GLOBAL_MAX_ENTRIES = 50; // GlobalMemoryPool.MAX_ENTRIES
const GLOBAL_MAX_CHARS = 2000; // GlobalMemoryPool.MAX_CHARS
const GLOBAL_CONSOLIDATE_INTERVAL = 20; // GlobalMemoryPool.CONSOLIDATE_INTERVAL
const MAX_SESSIONS = 20; // SessionManager.MAX_SESSIONS
const MAX_EVAL_RETRIES = 3; // ContextManager.MAX_EVAL_RETRIES
const SESSION_WARN_CHARS = 1500; // SessionManager.checkMemoryLimit
const SESSION_ABOUT_CHARS = 800;

function contextTurns(): number {
  return useSettingsStore.getState().settings.chatParams?.contextTurns ?? 10;
}

// ─── 轻量内存缓存（读多写少；每次写路径都会回源，保持一致性优先） ───
async function loadPoolEntries(personaId: string): Promise<PoolEntry[]> {
  return (await getByPersona<PoolEntry>("memoryPool", personaId)).sort((a, b) => a.timestamp - b.timestamp);
}
async function loadGlobalEntries(personaId: string): Promise<PoolEntry[]> {
  return (await getByPersona<PoolEntry>("globalMemoryPool", personaId)).sort((a, b) => a.timestamp - b.timestamp);
}

// ═══════════════════════ 记忆池块渲染（格式对齐 Kotlin） ═══════════════════════

/** [记忆池] 块：按 scene(→place→"其他") 分组，前缀 ◆ / ·（对齐 MemoryPool.getPoolBlock） */
export function renderPoolBlock(entries: PoolEntry[]): string {
  const main = entries.filter((e) => e.category !== "细节");
  if (main.length === 0) return "";
  const groups = new Map<string, PoolEntry[]>();
  for (const entry of main) {
    const key = (entry.scene || entry.place || "其他").slice(0, 20);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(entry);
  }
  let out = "[记忆池]\n";
  for (const [sceneKey, group] of groups) {
    out += `◆ ${sceneKey}\n`;
    for (const entry of group) out += `${lineEntry(entry, "  ·")}\n`;
  }
  return out.trimEnd();
}

/** [细节记忆] 块（对齐 MemoryPool.getDetailBlock） */
export function renderDetailBlock(details: PoolEntry[]): string {
  if (details.length === 0) return "";
  let out = "[细节记忆]\n";
  for (const entry of details) out += `${lineEntry(entry, "  ·")}\n`;
  return out.trimEnd();
}

/** [跨场景共享记忆] 块（对齐 GlobalMemoryPool.getGlobalBlock） */
export function renderGlobalBlock(entries: PoolEntry[]): string {
  if (entries.length === 0) return "";
  let out = "[跨场景共享记忆]\n";
  for (const entry of entries) out += `${lineEntry(entry, "-")}\n`;
  return out.trimEnd();
}

// ═══════════════════════ getContextBlock（对齐 ContextManager.getFullContextBlock） ═══════════════════════

/**
 * 组合上下文块：第一层跨场景共享记忆 → 第二层私聊记忆池(650字上限) → 第四层细节记忆。
 * 第三层虚拟世界记忆池（VirtualWorldMemoryPool）属于 virtualworld 模块，此处未纳入（见报告偏差）。
 * query 预留给向量检索式上下文压缩（Kotlin 版同样不区分 query，仅为接口对齐）。
 */
export async function getContextBlock(personaId: string, query = ""): Promise<string> {
  void query;
  const [globals, pool] = await Promise.all([loadGlobalEntries(personaId), loadPoolEntries(personaId)]);
  const details = pool.filter((e) => e.category === "细节");
  const main = pool.filter((e) => e.category !== "细节");
  const sb: string[] = [];
  const globalBlock = renderGlobalBlock(globals);
  if (globalBlock) sb.push(globalBlock);
  const poolBlock = renderPoolBlock(main);
  if (poolBlock) sb.push(poolBlock);
  const detailBlock = renderDetailBlock(details);
  if (detailBlock) sb.push("", detailBlock);
  return sb.join("\n").trimEnd();
}

// ═══════════════════════ 记忆提取与整合（LLM，Prompt 逐字对齐 MemoryPool.kt） ═══════════════════════

interface EvalAction {
  action: string;
  content?: string;
  old_content_fragment?: string;
  eventTime?: string;
  place?: string;
  people?: string;
  event?: string;
  scene?: string;
  details?: string;
  relationships?: string;
  global?: boolean;
}

function extractJsonArray(text: string): unknown[] | null {
  let cleaned = text.trim().replace(/```(?:json)?\s*/g, "").replace(/```/g, "").trim();
  const start = cleaned.indexOf("[");
  const end = cleaned.lastIndexOf("]");
  if (start >= 0 && end > start) cleaned = cleaned.slice(start, end + 1);
  try {
    const arr = JSON.parse(cleaned);
    return Array.isArray(arr) ? arr : null;
  } catch {
    return null;
  }
}

function buildEvaluateSystemPrompt(nick: string): string {
  return (
    "你是记忆整理助手。你的任务是将对话内容按【场景】和【事件】进行归纳总结。\n\n" +
    "【核心原则】\n" +
    "- 不要逐句记录对话！要将对话内容抽象为「场景→发生了什么」的结构化记忆\n" +
    "- 将同一场景下发生的多轮对话合并为一条完整的场景记忆\n" +
    "- 每条记忆应该像小说的章节摘要，而不是对话记录\n\n" +
    "【输出格式】只输出JSON数组，不要Markdown包裹\n" +
    "每条JSON格式：\n" +
    '{"action":"add","content":"场景摘要(一句话概括这个场景发生了什么)","eventTime":"时间","place":"地点/场景名","people":"涉及人物","event":"具体事件经过(起因→经过→结果)","scene":"场景类型(如:日常闲聊/情感交流/计划讨论/分享经历/游戏娱乐/学习工作等)","details":"关键细节(具体数字、名称、承诺、决定等)","relationships":"关系变化(如有)"}\n\n' +
    "【action类型】\n" +
    "- add: 新增场景/事件记忆\n" +
    "- update: 更新已有记忆(需加old_content_fragment字段)\n" +
    "- delete: 删除过时记忆(需加old_content_fragment字段)\n" +
    "- detail: 重要事实细节(单独保存，防止遗忘)\n\n" +
    "【重要规则】\n" +
    "- 场景描述要具体：「在咖啡馆聊了项目进度」而不是「聊了一些事情」\n" +
    "- 事件要有完整脉络：「用户提到最近压力大→AI安慰并建议休息→用户表示会试试」而不是零散的三句话\n" +
    "- 同一话题的多轮对话合并为一条：如果用户和AI连续5轮都在讨论旅行计划，只输出1条记忆概括整个讨论\n" +
    "- 只跳过纯寒暄（如「你好」「嗯」「好的」）\n" +
    "- 用户的具体偏好、承诺、决定、数据必须记入details字段\n" +
    '- 跨场景有价值的信息(喜好、习惯、重要事实)加 "global":true\n' +
    "- detail类型的记忆用于保存容易遗忘的具体细节\n" +
    "- 结构化字段可以为空字符串但必须存在\n" +
    `- 提到${nick} 时用「${nick}」称呼`
  );
}

function buildConsolidateSystemPrompt(maxChars: number): string {
  return (
    "你是记忆整合助手。请将以下记忆重新按【场景】归类整理。\n\n" +
    "【要求】\n" +
    "- 将属于同一场景/主题的记忆合并为一条完整的场景概述\n" +
    "- 合并后的每条记忆应包含该场景的所有关键信息：起因、经过、结果、涉及人物、重要细节\n" +
    "- 删除已过时的信息（如已完成的事件、已过期的计划）\n" +
    "- 保留所有跨场景共享的重要信息（用户喜好、习惯、关系变化等）\n" +
    "- 最终输出应像「故事章节目录」，每个场景一段话，而非流水账\n\n" +
    "【格式要求】\n" +
    "- 只输出JSON数组\n" +
    "- 每条记忆必须包含结构化字段:\n" +
    '  {"content":"场景概述","eventTime":"时间","place":"地点","people":"人物","event":"完整事件","scene":"场景分类","details":"关键细节","relationships":"关系变化"}\n' +
    `- 总字数不超过${maxChars}字\n` +
    "- 字段可为空字符串但必须存在"
  );
}

function fmtDateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** 应用 evaluateTurn 的 add/update/delete/detail 动作（对齐 parseEvaluationResult，直接改 entries 数组） */
function applyEvalActions(entries: PoolEntry[], actions: EvalAction[], turnNumber: number, personaId: string): PoolEntry[] {
  const results: PoolEntry[] = [];
  for (const obj of actions) {
    const action = obj.action ?? "";
    switch (action) {
      case "add": {
        const content = (obj.content ?? "").trim();
        if (content) {
          results.push(
            emptyPoolEntry(personaId, {
              content,
              category: "总结",
              sourceTurn: turnNumber,
              eventTime: obj.eventTime ?? "",
              place: obj.place ?? "",
              people: obj.people ?? "",
              event: obj.event ?? "",
              scene: obj.scene ?? "",
              details: obj.details ?? "",
              relationships: obj.relationships ?? "",
              isGlobal: obj.global === true,
            }),
          );
        }
        break;
      }
      case "update": {
        const oldFragment = (obj.old_content_fragment ?? "").trim();
        const newContent = (obj.content ?? "").trim();
        if (newContent) {
          const matched = oldFragment ? entries.find((e) => e.content.toLowerCase().includes(oldFragment.toLowerCase())) : undefined;
          if (matched) {
            const idx = entries.findIndex((e) => e.id === matched.id);
            entries.splice(idx, 1);
            results.push({
              ...matched,
              content: newContent,
              category: "总结",
              timestamp: Date.now(),
              sourceTurn: turnNumber,
              eventTime: obj.eventTime ?? matched.eventTime,
              place: obj.place ?? matched.place,
              people: obj.people ?? matched.people,
              event: obj.event ?? matched.event,
              scene: obj.scene ?? matched.scene,
              details: obj.details ?? matched.details,
              relationships: obj.relationships ?? matched.relationships,
              isGlobal: obj.global ?? matched.isGlobal,
            });
          } else {
            results.push(
              emptyPoolEntry(personaId, {
                content: newContent,
                category: "总结",
                sourceTurn: turnNumber,
                eventTime: obj.eventTime ?? "",
                place: obj.place ?? "",
                people: obj.people ?? "",
                event: obj.event ?? "",
                scene: obj.scene ?? "",
                details: obj.details ?? "",
                relationships: obj.relationships ?? "",
                isGlobal: obj.global === true,
              }),
            );
          }
        }
        break;
      }
      case "delete": {
        const oldFragment = (obj.old_content_fragment ?? "").trim();
        if (oldFragment) {
          const lower = oldFragment.toLowerCase();
          const doomed = entries.filter((e) => e.content.toLowerCase().includes(lower));
          for (const d of doomed) {
            const idx = entries.findIndex((e) => e.id === d.id);
            if (idx >= 0) entries.splice(idx, 1);
            void remove("memoryPool", d.id);
          }
        }
        break;
      }
      case "detail": {
        const content = (obj.content ?? "").trim();
        if (content) {
          results.push(
            emptyPoolEntry(personaId, {
              content,
              category: "细节",
              sourceTurn: turnNumber,
              eventTime: obj.eventTime ?? "",
              place: obj.place ?? "",
              people: obj.people ?? "",
              event: obj.event ?? "",
              scene: obj.scene ?? "",
              details: obj.details ?? "",
              relationships: obj.relationships ?? "",
              isGlobal: obj.global === true,
            }),
          );
        }
        break;
      }
    }
  }
  return results;
}

/** 细节条目 upsert：内容前20字互相包含即视为同一条（对齐 addDetailEntry） */
async function addOrUpdateDetailEntry(personaId: string, entry: PoolEntry): Promise<void> {
  const details = (await loadPoolEntries(personaId)).filter((e) => e.category === "细节");
  const head = entry.content.slice(0, 20);
  const existing = details.find(
    (d) => d.content.slice(0, 20).toLowerCase().includes(head.toLowerCase()) || entry.content.toLowerCase().includes(d.content.slice(0, 20).toLowerCase()),
  );
  if (existing) await put("memoryPool", { ...entry, id: existing.id });
  else await put("memoryPool", entry);
}

/**
 * 记忆池整合（对齐 MemoryPool.consolidate）：LLM 按场景重写全池；
 * 返回被归档的旧内容（供写入日记），失败时走智能裁剪（低重要性优先删除，全部重要则删最旧）。
 */
export async function consolidatePool(personaId: string): Promise<string> {
  const all = await loadPoolEntries(personaId);
  const details = all.filter((e) => e.category === "细节");
  const main = all.filter((e) => e.category !== "细节");
  const state = await getPoolState(personaId);
  if (main.length === 0 && details.length === 0) {
    await setPoolState(personaId, { turnsSinceLastConsolidate: 0, totalTurns: state.totalTurns });
    return "";
  }
  const fullPool = [renderPoolBlock(main), details.length ? renderDetailBlock(details) : ""].filter(Boolean).join("\n\n");
  try {
    const response = await simpleCompletion(`${fullPool}\n\n请整合以上记忆：`, {
      system: buildConsolidateSystemPrompt(MEMORY_MAX_CHARS_DEFAULT),
      temperature: 0.3,
      maxTokens: 2000,
    });
    const arr = extractJsonArray(response);
    const newEntries: PoolEntry[] = [];
    if (arr) {
      for (const item of arr) {
        if (item && typeof item === "object") {
          const obj = item as Record<string, unknown>;
          const content = String(obj.content ?? "").trim();
          if (content) {
            newEntries.push(
              emptyPoolEntry(personaId, {
                content,
                category: "总结",
                sourceTurn: state.totalTurns,
                eventTime: String(obj.eventTime ?? ""),
                place: String(obj.place ?? ""),
                people: String(obj.people ?? ""),
                event: String(obj.event ?? ""),
                scene: String(obj.scene ?? ""),
                details: String(obj.details ?? ""),
                relationships: String(obj.relationships ?? ""),
              }),
            );
          }
        } else {
          const line = String(item ?? "").trim().replace(/^[-•·*]/, "").trim();
          if (line) newEntries.push(emptyPoolEntry(personaId, { content: line, category: "总结", sourceTurn: state.totalTurns }));
        }
      }
    }
    if (newEntries.length > 0) {
      const archived =
        `[记忆归档·${fmtDateTime(new Date())}]\n` +
        main.map((e) => (structuredText(e) !== e.content && e.event ? `- ${structuredText(e)}` : `- ${e.content}`)).join("\n");
      for (const e of main) await remove("memoryPool", e.id);
      for (const e of details) await remove("memoryPool", e.id);
      for (const e of newEntries) await put("memoryPool", e);
      await setPoolState(personaId, { turnsSinceLastConsolidate: 0, totalTurns: state.totalTurns });
      return archived;
    }
  } catch {
    /* LLM 失败 → 走智能裁剪兜底（同 Kotlin） */
  }
  // 兜底：字符超限时智能裁剪并归档被删内容
  let totalChars = main.reduce((s, e) => s + e.content.length, 0) + details.reduce((s, e) => s + e.content.length, 0);
  if (totalChars > MEMORY_MAX_CHARS_DEFAULT) {
    const removedLines: string[] = [];
    const working = [...main];
    while (totalChars > MEMORY_MAX_CHARS_DEFAULT) {
      if (working.length === 1) {
        const entry = working[0];
        if (entry.content.length > MEMORY_MAX_CHARS_DEFAULT) {
          totalChars -= entry.content.length - MEMORY_MAX_CHARS_DEFAULT;
          await put("memoryPool", { ...entry, content: entry.content.slice(0, MEMORY_MAX_CHARS_DEFAULT) });
        }
        break;
      }
      const least = working.reduce((a, b) => (a.importance <= b.importance ? a : b));
      let victim: PoolEntry;
      if (least.importance < MEMORY_IMPORTANCE_THRESHOLD) {
        victim = least;
      } else {
        victim = working[0]; // 降级：删除最旧的
      }
      working.splice(working.findIndex((e) => e.id === victim.id), 1);
      totalChars -= victim.content.length;
      removedLines.push(`- ${victim.content}`);
      await remove("memoryPool", victim.id);
    }
    await setPoolState(personaId, { turnsSinceLastConsolidate: 0, totalTurns: state.totalTurns });
    return `[记忆归档·${fmtDateTime(new Date())}]\n${removedLines.join("\n")}`;
  }
  await setPoolState(personaId, { turnsSinceLastConsolidate: 0, totalTurns: state.totalTurns });
  return "";
}

// ─── 池状态（kv） ───
interface PoolState {
  turnsSinceLastConsolidate: number;
  totalTurns: number;
}
async function getPoolState(personaId: string): Promise<PoolState> {
  return (await kvGet<PoolState>(`pool:${personaId}`)) ?? { turnsSinceLastConsolidate: 0, totalTurns: 0 };
}
async function setPoolState(personaId: string, s: PoolState): Promise<void> {
  await kvSet(`pool:${personaId}`, s);
}

// ═══════════════════════ 全局记忆池（对齐 GlobalMemoryPool.kt） ═══════════════════════

interface GlobalState {
  writeCount: number;
}
async function getGlobalState(personaId: string): Promise<GlobalState> {
  return (await kvGet<GlobalState>(`gpool:${personaId}`)) ?? { writeCount: 0 };
}
async function setGlobalState(personaId: string, s: GlobalState): Promise<void> {
  await kvSet(`gpool:${personaId}`, s);
}

/** 从场景吸收 isGlobal=true 的条目（对齐 addFromScene：完全相同内容则覆盖，否则追加） */
async function addFromScene(personaId: string, newEntries: PoolEntry[]): Promise<void> {
  const globals = await loadGlobalEntries(personaId);
  for (const entry of newEntries) {
    if (!entry.isGlobal) continue;
    const existing = globals.find((g) => g.content === entry.content);
    if (existing) await put("globalMemoryPool", { ...entry, id: existing.id, isGlobal: true });
    else await put("globalMemoryPool", emptyPoolEntry(personaId, { ...entry, isGlobal: true }));
  }
  const after = await loadGlobalEntries(personaId);
  await trimGlobalToLimit(after);
  const st = await getGlobalState(personaId);
  await setGlobalState(personaId, { writeCount: st.writeCount + newEntries.length });
}

async function trimGlobalToLimit(entries: PoolEntry[]): Promise<void> {
  let list = [...entries];
  let chars = list.reduce((s, e) => s + e.content.length, 0);
  while ((chars > GLOBAL_MAX_CHARS || list.length > GLOBAL_MAX_ENTRIES) && list.length > 1) {
    const removed = list.shift()!;
    chars -= removed.content.length;
    await remove("globalMemoryPool", removed.id);
  }
}

/** 全局池整合（对齐 GlobalMemoryPool.consolidate：纯文本输出，每行 - 开头） */
async function consolidateGlobal(personaId: string): Promise<boolean> {
  const entries = await loadGlobalEntries(personaId);
  if (entries.length === 0) {
    await setGlobalState(personaId, { writeCount: 0 });
    return false;
  }
  const block = renderGlobalBlock(entries);
  const systemPrompt =
    "你是记忆总结助手。请将以下跨场景共享记忆重新整理总结。\n" +
    "要求：\n" +
    "- 简短精炼，保留所有重要细节\n" +
    "- 合并重复内容，删除过时信息\n" +
    "- 只保留跨场景有价值的信息（用户喜好、事实、关系、重要决定等）\n" +
    "- 删除场景特定的事件描述（如'在群里讨论了xxx'）\n" +
    `- 总字数不超过${GLOBAL_MAX_CHARS}字\n` +
    "- 每条记忆一行，以 - 开头\n" +
    "- 只输出纯文本，不要JSON";
  try {
    const response = await simpleCompletion(`${block}\n\n请整理以上记忆：`, { system: systemPrompt, temperature: 0.3, maxTokens: 1000 });
    const lines = response
      .split("\n")
      .map((l) => l.trim())
      .filter((l) => l && !l.startsWith("[跨场景"))
      .map((l) => l.replace(/^[-•·*]\s*/, "").trim())
      .filter(Boolean);
    if (lines.length > 0) {
      for (const e of entries) await remove("globalMemoryPool", e.id);
      for (const line of lines) await put("globalMemoryPool", emptyPoolEntry(personaId, { content: line, category: "全局", isGlobal: true }));
      await setGlobalState(personaId, { writeCount: 0 });
      return true;
    }
  } catch {
    /* 失败走裁剪兜底 */
  }
  const after = await loadGlobalEntries(personaId);
  const chars = after.reduce((s, e) => s + e.content.length, 0);
  if (chars > GLOBAL_MAX_CHARS) await trimGlobalToLimit(after);
  await setGlobalState(personaId, { writeCount: 0 });
  return false;
}

// ═══════════════════════ 会话管理（对齐 SessionManager.kt，per-persona） ═══════════════════════

interface SessionRecord extends SessionInfo, StoredRecord {
  /** 是否为该角色当前会话 */
  current?: boolean;
  /** 继承自哪个会话 */
  inheritedFrom?: string;
}

async function loadSessions(personaId: string): Promise<SessionRecord[]> {
  return getByPersona<SessionRecord>("sessions", personaId);
}

/** 获取（或惰性创建）当前会话 */
export async function getCurrentSession(personaId: string): Promise<SessionInfo> {
  const sessions = await loadSessions(personaId);
  const current = sessions.find((s) => s.current) ?? sessions.sort((a, b) => b.startedAt - a.startedAt)[0];
  if (current) return current;
  const fresh: SessionRecord = {
    id: newId("sess"),
    personaId,
    title: "会话",
    turnCount: 0,
    startedAt: Date.now(),
    lastActiveAt: Date.now(),
    current: true,
  };
  await put("sessions", fresh);
  return fresh;
}

/** 轮次+1（对齐 incrementTurn：同时刷新最后活跃时间） */
export async function incrementTurn(personaId: string): Promise<void> {
  const cur = await getCurrentSession(personaId);
  await put("sessions", { ...(cur as SessionRecord), turnCount: cur.turnCount + 1, lastActiveAt: Date.now(), current: true });
}

/**
 * 记忆量检查（对齐 checkMemoryLimit）：
 * >1500字 → 返回 true 并提示开新会话；>800字 → 返回 false 但提示即将自动整理。
 */
export async function checkMemoryLimit(personaId: string): Promise<boolean> {
  const entries = await loadPoolEntries(personaId);
  const charCount = entries.reduce((s, e) => s + e.content.length, 0);
  if (charCount > SESSION_WARN_CHARS) return true;
  return false;
}

/** 会话预警文案（对齐 onSessionWarning 的消息内容，供 UI 展示） */
export async function getSessionWarning(personaId: string): Promise<string | null> {
  const entries = await loadPoolEntries(personaId);
  const charCount = entries.reduce((s, e) => s + e.content.length, 0);
  if (charCount > SESSION_WARN_CHARS) return `记忆池已达${charCount}字，建议开启新会话`;
  if (charCount > SESSION_ABOUT_CHARS) return `记忆池已达${charCount}字，即将自动整理`;
  return null;
}

/**
 * 开启新会话（对齐 createNewSession）：归档当前记忆池为继承快照，
 * 触发记忆池整合，会话列表上限 20 条。返回新会话。
 * diaryCallback 对应 Kotlin 把池块/归档写入日记的钩子。
 */
export async function startNewSession(personaId: string, diaryCallback?: (poolBlock: string) => void | Promise<void>): Promise<SessionInfo> {
  const poolEntries = await loadPoolEntries(personaId);
  const poolBlock = renderPoolBlock(poolEntries.filter((e) => e.category !== "细节"));
  if (poolBlock && diaryCallback) await diaryCallback(poolBlock);
  const archived = await consolidatePool(personaId);
  if (archived && diaryCallback) await diaryCallback(archived);

  const compressed = renderPoolBlock((await loadPoolEntries(personaId)).filter((e) => e.category !== "细节"));
  const old = await getCurrentSession(personaId);
  await put("sessions", { ...(old as SessionRecord), current: false });
  const fresh: SessionRecord = {
    id: newId("sess"),
    personaId,
    title: `会话 ${new Date().toLocaleDateString("zh-CN")}`,
    turnCount: 0,
    startedAt: Date.now(),
    lastActiveAt: Date.now(),
    summary: compressed,
    current: true,
    inheritedFrom: old.id,
  };
  await put("sessions", fresh);
  // 修剪历史会话至上限
  const sessions = (await loadSessions(personaId)).sort((a, b) => b.startedAt - a.startedAt);
  for (const stale of sessions.slice(MAX_SESSIONS)) await remove("sessions", stale.id);
  return fresh;
}

/** 上一会话的记忆快照（对齐 getInheritedMemory；首访时自动注入记忆池，类别"继承"） */
export async function getInheritedMemory(personaId: string): Promise<string> {
  const cur = (await getCurrentSession(personaId)) as SessionRecord;
  const inherited = cur.summary ?? "";
  if (inherited) {
    const entries = await loadPoolEntries(personaId);
    if (entries.length === 0) {
      await put("memoryPool", emptyPoolEntry(personaId, { content: inherited, category: "继承", sourceTurn: 0 }));
    }
  }
  return inherited;
}

// ═══════════════════════ 长期事实（对齐 MemoryManager.kt，存 memories 集合） ═══════════════════════

/** 添加长期事实（去重、上限 200 条——对齐 addMemoryFact） */
export async function addMemoryFact(personaId: string, fact: string, category = ""): Promise<void> {
  const trimmed = fact.trim();
  if (!trimmed) return;
  const existing = await getByPersona<MemoryFact & StoredRecord>("memories", personaId);
  if (existing.some((m) => m.fact.toLowerCase() === trimmed.toLowerCase())) return;
  await put("memories", {
    id: `mem_${Date.now()}_${hashStr(trimmed)}`,
    personaId,
    fact: trimmed,
    timestamp: Date.now(),
    category,
  } satisfies MemoryFact & StoredRecord);
  if (existing.length + 1 > 200) {
    const sorted = [...existing].sort((a, b) => b.timestamp - a.timestamp);
    for (const stale of sorted.slice(200)) await remove("memories", stale.id);
  }
}

function hashStr(s: string): string {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return (h >>> 0).toString(36);
}

/** 列出长期事实（时间倒序，对齐 getLocalMemories） */
export async function listMemoryFacts(personaId: string): Promise<MemoryFact[]> {
  const list = await getByPersona<MemoryFact>("memories", personaId);
  return list.sort((a, b) => b.timestamp - a.timestamp);
}

/** 删除一条长期事实 */
export async function removeMemoryFact(id: string): Promise<void> {
  await remove("memories", id);
}

/** 关键词检索长期事实（fact/category 包含匹配，大小写不敏感——对齐 searchLocalMemories） */
export async function searchMemories(personaId: string, query: string): Promise<MemoryFact[]> {
  const all = await listMemoryFacts(personaId);
  if (!query) return all;
  const lower = query.toLowerCase();
  return all.filter((m) => m.fact.toLowerCase().includes(lower) || m.category.toLowerCase().includes(lower));
}

// ═══════════════════════ addTurn（对齐 ContextManager.addTurn + evaluateAndUpdateMemory） ═══════════════════════

interface CtxState {
  turnsSinceLastEval: number;
  totalTurns: number;
  evalFailCount: number;
  pendingUser: string;
  pendingAi: string;
}
async function getCtxState(personaId: string): Promise<CtxState> {
  return (
    (await kvGet<CtxState>(`ctx:${personaId}`)) ?? {
      turnsSinceLastEval: 0,
      totalTurns: 0,
      evalFailCount: 0,
      pendingUser: "",
      pendingAi: "",
    }
  );
}

/**
 * 记录一轮对话：
 * 1. 轮次计数（session + pool）；
 * 2. 首次调用时把上一会话继承记忆注入池（applyInheritedMemory）；
 * 3. 每满 contextTurns 轮触发一次 LLM 记忆提取（evaluateTurn），应用 add/update/delete/detail，
 *    isGlobal 条目进全局池并按需整合；主池按需整合。
 * 原始近期轮次由消息存储（messages 集合）提供，本模块不再单独维护 rawTurns。
 */
export async function addTurn(personaId: string, userText: string, petText: string): Promise<void> {
  if (!userText.trim() || !petText.trim()) return;
  await getInheritedMemory(personaId); // 若池为空且有继承快照则注入（幂等）
  await incrementTurn(personaId);
  const state = await getCtxState(personaId);
  const poolState = await getPoolState(personaId);
  await setPoolState(personaId, { ...poolState, turnsSinceLastConsolidate: poolState.turnsSinceLastConsolidate + 1, totalTurns: poolState.totalTurns + 1 });
  const turns = contextTurns();
  await setCtxState(personaId, {
    ...state,
    turnsSinceLastEval: state.turnsSinceLastEval + 1,
    totalTurns: state.totalTurns + 1,
    pendingUser: state.pendingUser ? `${state.pendingUser}\n\n用户: ${userText}\nAI: ${petText}` : `用户: ${userText}\nAI: ${petText}`,
  });
  if (state.turnsSinceLastEval + 1 >= turns) {
    await evaluateAndUpdateMemory(personaId);
  }
}

/** 手动触发记忆评估（内部由 addTurn 周期性调用，也可外部强制） */
export async function evaluateAndUpdateMemory(personaId: string): Promise<void> {
  const state = await getCtxState(personaId);
  if (!state.pendingUser) {
    await setCtxState(personaId, { ...state, turnsSinceLastEval: 0, evalFailCount: 0 });
    return;
  }
  const nick = useSettingsStore.getState().settings.userProfile?.nickname?.trim() || "用户";
  const poolEntries = await loadPoolEntries(personaId);
  const poolBlock = poolEntries.length ? renderPoolBlock(poolEntries.filter((e) => e.category !== "细节")) : "（空）";
  const userContent = `[当前记忆池]\n${poolBlock}\n\n[近期对话]\n${state.pendingUser}\n\n输出JSON数组：`;
  try {
    const response = await simpleCompletion(userContent, {
      system: buildEvaluateSystemPrompt(nick),
      temperature: 0.3,
      maxTokens: 2000,
    });
    const arr = extractJsonArray(response);
    if (!arr || arr.length === 0) {
      const fails = state.evalFailCount + 1;
      await setCtxState(personaId, {
        ...state,
        evalFailCount: fails,
        turnsSinceLastEval: fails >= MAX_EVAL_RETRIES ? 0 : state.turnsSinceLastEval,
        pendingUser: fails >= MAX_EVAL_RETRIES ? "" : state.pendingUser,
      });
      return;
    }
    const working = poolEntries.filter((e) => e.category !== "细节").map((e) => ({ ...e }));
    const results = applyEvalActions(working, arr as EvalAction[], state.totalTurns + 1, personaId);
    // 落盘：working 中被删的移除、新增/更新的 put
    const keepIds = new Set(working.map((e) => e.id));
    for (const e of poolEntries) {
      if (e.category !== "细节" && !keepIds.has(e.id) && !results.some((r) => r.id === e.id)) {
        await remove("memoryPool", e.id);
      }
    }
    for (const e of working) await put("memoryPool", e);
    const globalNew: PoolEntry[] = [];
    for (const r of results) {
      if (r.category === "细节") {
        await addOrUpdateDetailEntry(personaId, r);
      } else {
        await put("memoryPool", r);
        if (r.isGlobal) globalNew.push(r);
      }
    }
    if (globalNew.length > 0) {
      await addFromScene(personaId, globalNew);
      const gs = await getGlobalState(personaId);
      if (gs.writeCount >= GLOBAL_CONSOLIDATE_INTERVAL) await consolidateGlobal(personaId);
    }
    const poolState = await getPoolState(personaId);
    if (poolState.turnsSinceLastConsolidate >= CONSOLIDATE_INTERVAL) await consolidatePool(personaId);
    await setCtxState(personaId, { ...state, turnsSinceLastEval: 0, evalFailCount: 0, pendingUser: "" });
  } catch {
    const fails = state.evalFailCount + 1;
    await setCtxState(personaId, {
      ...state,
      evalFailCount: fails,
      turnsSinceLastEval: fails >= MAX_EVAL_RETRIES ? 0 : state.turnsSinceLastEval,
      pendingUser: fails >= MAX_EVAL_RETRIES ? "" : state.pendingUser,
    });
  }
}

async function setCtxState(personaId: string, s: CtxState): Promise<void> {
  await kvSet(`ctx:${personaId}`, s);
}

/** 是否到达下次记忆评估点（对齐 shouldEvaluate） */
export async function shouldEvaluate(personaId: string): Promise<boolean> {
  const state = await getCtxState(personaId);
  return state.turnsSinceLastEval >= contextTurns();
}

/** 清空某角色的全部记忆池与会话状态（调试/重置用） */
export async function clearPersonaMemory(personaId: string): Promise<void> {
  const [pool, globals, sessions] = await Promise.all([loadPoolEntries(personaId), loadGlobalEntries(personaId), loadSessions(personaId)]);
  for (const e of pool) await remove("memoryPool", e.id);
  for (const e of globals) await remove("globalMemoryPool", e.id);
  for (const s of sessions) await remove("sessions", s.id);
  await kvSet(`ctx:${personaId}`, { turnsSinceLastEval: 0, totalTurns: 0, evalFailCount: 0, pendingUser: "", pendingAi: "" } satisfies CtxState);
  await kvSet(`pool:${personaId}`, { turnsSinceLastConsolidate: 0, totalTurns: 0 } satisfies PoolState);
  await kvSet(`gpool:${personaId}`, { writeCount: 0 } satisfies GlobalState);
}

/** 会话统计文本（对齐 getSessionStats） */
export async function getSessionStats(personaId: string): Promise<string> {
  const [cur, state, pool] = await Promise.all([getCurrentSession(personaId), getCtxState(personaId), loadPoolEntries(personaId)]);
  const remaining = Math.max(0, contextTurns() - state.turnsSinceLastEval);
  const failInfo = state.evalFailCount > 0 ? ` [评估失败${state.evalFailCount}次]` : "";
  const chars = pool.reduce((s, e) => s + e.content.length, 0);
  return `会话 #${cur.id.slice(0, 6)} | 轮次: ${cur.turnCount} | 共${pool.length}条记忆 | ${chars}字 | 下次记忆提取: ${remaining}轮后${failInfo}`;
}
