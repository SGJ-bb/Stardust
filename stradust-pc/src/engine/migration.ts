/**
 * 数据迁移 —— Android migration/DataMigrationManager.kt 的 Web 等价实现。
 *
 * 差异说明：Android 的 migrateIfNeeded 是 SharedPreferences 之间的版本升级
 * （v0→v1 persona 分离、v1→v2 memory_pool/加密 Key 迁移等），依赖 Android 专属存储，
 * Web 端 IndexedDB 结构自建即最新，无需该路径；Web 侧做**全量导出/导入**快照：
 * - exportSnapshot: db.exportAll() + settings 快照，带版本号（对齐 LATEST_VERSION=2）；
 * - importSnapshot: merge/replace 两模式（对齐 db.importAll）；
 * - importAndroidJson: 尽力解析 Android 导出的 chat_history / diaries JSON。
 *   结构读自 ChatHistoryStorage.StoredMessage.toJson 与 DiaryEntry/DiaryManager.exportToJson。
 */

import * as db from "@/lib/db";
import { emit } from "@/lib/events";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { EngineMessage, DiaryRecord, Emotion } from "./kinds";
import { EMOTIONS } from "./kinds";

/** 快照格式版本（对齐 DataMigrationManager.LATEST_VERSION = 2） */
export const SNAPSHOT_VERSION = 2;

export interface SnapshotPayload {
  kind: "stradust-web-snapshot";
  version: number;
  exportedAt: number;
  /** db.exportAll() 的全集合数据 */
  collections: Record<string, db.StoredRecord[]>;
  /** zustand settings 快照 */
  settings: unknown;
}

// ═══════════════════════ 导出 ═══════════════════════

/** 全量导出为 JSON Blob（文件下载用） */
export async function exportSnapshot(): Promise<Blob> {
  const payload: SnapshotPayload = {
    kind: "stradust-web-snapshot",
    version: SNAPSHOT_VERSION,
    exportedAt: Date.now(),
    collections: await db.exportAll(),
    settings: useSettingsStore.getState().settings,
  };
  return new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
}

// ═══════════════════════ 导入 Web 快照 ═══════════════════════

export interface ImportSummary {
  messages: number;
  diaries: number;
  collections: number;
  skipped: string[];
}

/**
 * 导入本应用导出的快照。mode=replace 先清空各集合再写入；merge 按 id upsert。
 * 也接受 Android 聊天/日记导出 JSON（内部转走 importAndroidJson）。
 */
export async function importSnapshot(file: File, mode: "merge" | "replace" = "merge"): Promise<ImportSummary> {
  const text = await file.text();
  let root: Record<string, unknown>;
  try {
    root = JSON.parse(text) as Record<string, unknown>;
  } catch (e) {
    throw new Error(`JSON 解析失败: ${(e as Error).message}`);
  }

  // Android 导出文件识别（无 collections 键但含 diaries/messages/chat_history）
  if (!root.collections && (root.diaries || root.messages || root.chat_history)) {
    const r = await importAndroidJson(text);
    return { messages: r.count, diaries: 0, collections: 0, skipped: [] };
  }

  const collections = root.collections as Record<string, db.StoredRecord[]> | undefined;
  if (!collections) throw new Error("快照缺少 collections 字段，可能不是本应用的导出文件");
  await db.importAll(collections, mode);

  // settings 快照恢复（仅当带 settings 字段；浅合并进当前 settings）
  if (root.settings && typeof root.settings === "object") {
    useSettingsStore.getState().updateSettings(root.settings as Partial<ReturnType<typeof useSettingsStore.getState>["settings"]>);
  }

  emit("data-changed");
  const totalMsgs = collections.messages?.length ?? 0;
  const totalDiaries = collections.diaries?.length ?? 0;
  return {
    messages: totalMsgs,
    diaries: totalDiaries,
    collections: Object.keys(collections).filter((k) => collections[k]?.length).length,
    skipped: [],
  };
}

// ═══════════════════════ 导入 Android 导出 JSON ═══════════════════════

/** Kotlin StoredMessage.emotion（大写枚举名）→ Web Emotion（小写；TSUNDERE→shy 近似映射） */
function mapKotlinEmotion(e: unknown): Emotion | undefined {
  if (typeof e !== "string" || !e.trim()) return undefined;
  const lower = e.toLowerCase();
  if (lower === "tsundere") return "shy"; // Web 情绪表无傲娇，就近映射到害羞
  return (EMOTIONS as string[]).includes(lower) ? (lower as Emotion) : "neutral";
}

interface AndroidStoredMessage {
  id?: string;
  text?: string;
  time?: string;
  isUser?: boolean;
  emotion?: string;
  timestamp?: number;
  isFavorited?: boolean;
  senderName?: string;
  imageUrls?: string[];
}

function toEngineMessage(m: AndroidStoredMessage, personaId: string): EngineMessage {
  return {
    id: m.id || db.newId("msg"),
    personaId,
    role: m.isUser ? "user" : "assistant",
    content: String(m.text ?? ""),
    timestamp: typeof m.timestamp === "number" ? m.timestamp : Date.now(),
    emotion: mapKotlinEmotion(m.emotion),
    favorited: !!m.isFavorited,
    attachments: Array.isArray(m.imageUrls) ? m.imageUrls.filter((x) => typeof x === "string") : undefined,
    meta: { importedFrom: "android", senderName: m.senderName ?? "", time: m.time ?? "" },
  };
}

interface AndroidDiary {
  date?: string;
  title?: string;
  content?: string;
  mood?: string;
  mood_emoji?: string;
  created_at?: number;
  updated_at?: number;
  // key_memories/tags/plugin_meta/custom_fields/affection_level/message_count/app_version/version
  // 等字段 Web DiaryRecord 无对应槽位 → 跳过（见下方注释）
}

function toDiaryRecord(d: AndroidDiary, personaId: string): DiaryRecord | null {
  if (typeof d.date !== "string" || !d.date.trim()) return null;
  return {
    id: db.newId("diary"),
    personaId,
    date: d.date,
    title: String(d.title ?? ""),
    content: String(d.content ?? ""),
    mood: String(d.mood ?? "normal"),
    moodEmoji: String(d.mood_emoji ?? "😊"),
    createdAt: typeof d.created_at === "number" ? d.created_at : Date.now(),
    updatedAt: typeof d.updated_at === "number" ? d.updated_at : Date.now(),
    // 认不出的键跳过：key_memories(数组)/tags(数组)/plugin_meta/custom_fields(JSON字符串)、
    // affection_level/message_count —— Web DiaryRecord 契约无这些字段
  };
}

function collectMessages(node: unknown, out: AndroidStoredMessage[], depth = 0): void {
  // 兼容三种形态：消息数组本体 / {messages:[...]} / {scope:{id:{messages}}} 嵌套对象
  if (depth > 4 || node == null) return;
  if (Array.isArray(node)) {
    for (const item of node) {
      if (item && typeof item === "object" && ("text" in item || "isUser" in item)) {
        out.push(item as AndroidStoredMessage);
      } else {
        collectMessages(item, out, depth + 1);
      }
    }
    return;
  }
  if (typeof node === "object") {
    const obj = node as Record<string, unknown>;
    if (Array.isArray(obj.messages)) {
      collectMessages(obj.messages, out, depth + 1);
      return;
    }
    if (typeof obj.text === "string") {
      out.push(obj as AndroidStoredMessage);
      return;
    }
    for (const v of Object.values(obj)) collectMessages(v, out, depth + 1);
  }
}

/**
 * 尽力解析 Android 导出的 chat_history / diary JSON：
 * - DiaryManager.exportToJson：{export_version, app_version, diaries:[DiaryEntry...]}
 *   （或单条 DiaryEntry 对象，对齐 Kotlin importFromJson 的单条回退）；
 * - ChatHistoryStorage.StoredMessage.toJson：{id,text,time,isUser,emotion,timestamp,...}
 *   的消息数组、{messages:[...]} 包裹、或旧版 SharedPreferences dump 里的 JSON 字符串；
 * - DataMigrationManager 风格的 {key: value} prefs dump 中的 "messages"/"diaries" 字符串值。
 * 认不出的键一律跳过并计入 skipped；personaId 归入 "default"（Android 导出无角色归属信息时）。
 */
export async function importAndroidJson(json: string, personaId = "default"): Promise<{ count: number }> {
  let root: unknown;
  try {
    root = JSON.parse(json);
  } catch (e) {
    throw new Error(`JSON 解析失败: ${(e as Error).message}`);
  }
  const skipped: string[] = [];
  let count = 0;

  const obj = (root && typeof root === "object" && !Array.isArray(root)) ? root as Record<string, unknown> : null;

  // ── 日记 ──
  const diaryItems: AndroidDiary[] = [];
  if (obj) {
    if (Array.isArray(obj.diaries)) diaryItems.push(...(obj.diaries as AndroidDiary[]));
    else if (typeof obj.date === "string" && typeof obj.content === "string") diaryItems.push(obj as AndroidDiary); // 单条 DiaryEntry
    // prefs dump 中 diaries 可能是 JSON 字符串
    if (typeof obj.diaries === "string") {
      try {
        const inner = JSON.parse(obj.diaries);
        if (Array.isArray(inner)) diaryItems.push(...inner);
      } catch { skipped.push("diaries(无法解析的字符串)"); }
    }
  }
  const existingDiaries = await db.getAll<DiaryRecord>("diaries");
  const diaryKeyOf = (r: { personaId: string; date: string }) => `${r.personaId}|${r.date}`;
  const seenDiary = new Set(existingDiaries.map(diaryKeyOf));
  for (const d of diaryItems) {
    const rec = toDiaryRecord(d, personaId);
    if (!rec) continue;
    const key = diaryKeyOf(rec);
    if (seenDiary.has(key)) { skipped.push(`diary:${rec.date}(同日已存在)`); continue; }
    seenDiary.add(key);
    await db.put("diaries", rec);
    count++;
  }

  // ── 聊天记录 ──
  const msgNodes: unknown[] = [];
  if (Array.isArray(root)) msgNodes.push(root);
  if (obj) {
    for (const key of ["messages", "chat_history", "chatHistory"]) {
      if (obj[key] != null) msgNodes.push(obj[key]);
      // prefs dump：值是 JSON 字符串
      if (typeof obj[key] === "string") {
        try { msgNodes.push(JSON.parse(obj[key] as string)); } catch { skipped.push(`${key}(无法解析的字符串)`); }
      }
    }
  }
  const rawMessages: AndroidStoredMessage[] = [];
  for (const n of msgNodes) collectMessages(n, rawMessages);
  const existingMsgIds = new Set((await db.getAll<EngineMessage>("messages")).map((m) => m.id));
  for (const m of rawMessages) {
    if (typeof m.text !== "string" || !m.text.trim()) continue;
    const rec = toEngineMessage(m, personaId);
    if (existingMsgIds.has(rec.id)) {
      rec.id = db.newId("msg"); // id 冲突时重新发号，避免覆盖
    }
    existingMsgIds.add(rec.id);
    await db.put("messages", rec);
    count++;
  }

  if (obj) {
    // 记录被跳过的顶层未知键（辅助排查；不影响导入）
    const knownKeys = new Set(["diaries", "messages", "chat_history", "chatHistory", "export_version", "app_version", "export_time", "export_date", "total_count"]);
    for (const k of Object.keys(obj)) {
      if (!knownKeys.has(k)) skipped.push(`key:${k}`);
    }
  }

  if (count > 0) emit("data-changed");
  if (skipped.length) console.info("[migration] importAndroidJson skipped:", skipped);
  return { count };
}
