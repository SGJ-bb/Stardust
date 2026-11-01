/**
 * 统一后端门面（浏览器 + Tauri 双模式同一实现）
 *
 * 架构说明：全部领域能力由 src/engine/*（TypeScript 引擎，移植自 Android 版）+ IndexedDB
 * 持久层承载；本文件保持与旧版一致的导出函数签名供页面调用。
 * Tauri 桌面独有能力（窗口/自启等）经 desktopInvoke 透传。
 * 旧版（Rust command 直通）备份于 F:\stradust\tauri.ts.facade.bak。
 */

import type {
  ActivationResult,
  PurchaseResult,
  SendMessageParams,
  StreamChatParams,
  CreatePersonaParams,
  UpdatePersonaParams,
  GetMemoriesParams,
  CreateMemoryParams,
  TtsParams,
  SttParams,
  Live2DControlParams,
} from "@/types/tauri";
import type { Persona } from "@/types/persona";
import type { ChatMessage, ChatSession } from "@/types/chat";
import type { Memory, MemoryEntry } from "@/types/memory";
import type { Settings } from "@/types/settings";
import type { GroupChat } from "@/types/group-chat";
import type { Achievement, CheckIn, Growth } from "@/types/achievement";
import type { Diary } from "@/types/diary";
import type { Moment } from "@/types/moments";
import type { CalendarEvent } from "@/types/calendar";
import type { TimeCapsule } from "@/types/capsule";
import type { AlbumEntry } from "@/types/album";
import type { Sticker, StickerPack } from "@/types/sticker";
import type { WorldConfig, WorldState } from "@/types/virtual-world";

import { getAll, getByPersona, getOne, put, remove as dbRemove, kvGet, kvSet, newId } from "@/lib/db";
import { emit } from "@/lib/events";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { sendTurn } from "@/engine/chatEngine";
import * as diaryEngine from "@/engine/diary";
import * as momentsEngine from "@/engine/momentsFeed";
import * as groupEngine from "@/engine/groupchatEngine";
import * as worldEngine from "@/engine/worldEngine";
import * as calendarEngine from "@/engine/calendar";
import * as stickersEngine from "@/engine/stickers";
import * as skinsEngine from "@/engine/skins";
import * as wakeupEngine from "@/engine/wakeup";
import * as achievementsEngine from "@/engine/achievements";
import * as checkinEngine from "@/engine/checkin";
import * as growthEngine from "@/engine/growth";
import * as affectionEngine from "@/engine/affection";
import * as contextEngine from "@/engine/context";
import * as voiceEngine from "@/engine/voice";
import * as ilinkEngine from "@/engine/ilink";
import * as migrationEngine from "@/engine/migration";
import type {
  EngineMessage, MomentRecord, DiaryRecord, GroupRecord, WorldRecord,
  WorldEventRecord, CalendarRecord, CapsuleRecord, AlbumRecord, AchievementRecord,
} from "@/engine/kinds";

/** 检测是否在 Tauri 环境中运行 */
export function isTauri(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

async function desktopRawInvoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const { invoke: tInvoke } = await import("@tauri-apps/api/core");
  return tInvoke<T>(cmd, args);
}

function userName(): string {
  return useSettingsStore.getState().settings.userProfile?.nickname?.trim() || "我";
}

// ═══════════════════ 聊天 ═══════════════════

/** 流式聊天：走引擎全链路（记忆/RAG/工具循环/情绪协议），事件经 sd:chat-stream 总线 */
export async function streamChat(params: StreamChatParams): Promise<void> {
  const { personaId, content, sessionId, attachments } = params;
  let lastLen = 0;
  try {
    const result = await sendTurn(personaId, content, { sessionId, attachments }, {
      onToken: (accumulated) => {
        // 引擎回调为累计文本；总线事件转成增量 token，兼容既有 UI
        const delta = accumulated.slice(lastLen);
        lastLen = accumulated.length;
        if (delta) emit("chat-stream", { personaId, sessionId, type: "content", content: delta });
      },
      onToolCall: (name, args) => emit("chat-stream", { personaId, sessionId, type: "tool_call", toolName: name, content: args }),
      onToolResult: (name, r) => emit("chat-stream", { personaId, sessionId, type: "tool_result", toolName: name, content: r.text }),
    });
    // 兜底：非流式路径未回调时一次性补发
    if (lastLen === 0 && result.message.content) {
      emit("chat-stream", { personaId, sessionId, type: "content", content: result.message.content });
    }
    emit("chat-stream", { personaId, sessionId, type: "emotion", content: result.emotion });
    if (result.suggestions.length > 0) {
      emit("chat-stream", { personaId, sessionId, type: "suggestions", content: JSON.stringify(result.suggestions) });
    }
    emit("chat-stream", { personaId, sessionId, type: "done" });
  } catch (e) {
    emit("chat-stream", { personaId, sessionId, type: "error", content: (e as Error).message });
  }
}

/** 非流式发送（返回完整回复消息） */
export async function sendMessage(params: SendMessageParams): Promise<ChatMessage> {
  const { personaId, content, attachments } = params;
  const result = await sendTurn(personaId, content, { attachments });
  return engineToPageMessage(result.message);
}

export async function getChatHistory(personaId: string, limit = 200, offset = 0): Promise<ChatMessage[]> {
  const all = await getByPersona<EngineMessage>("messages", personaId);
  const sorted = all.sort((a, b) => a.timestamp - b.timestamp);
  return sorted.slice(Math.max(0, sorted.length - limit - offset), Math.max(0, sorted.length - offset)).map(engineToPageMessage);
}

export async function getChatSessions(personaId: string): Promise<ChatSession[]> {
  const msgs = await getByPersona<EngineMessage & { sessionId?: string }>("messages", personaId);
  const bySession = new Map<string, EngineMessage[]>();
  for (const m of msgs) {
    const sid = m.sessionId ?? "default";
    bySession.set(sid, [...(bySession.get(sid) ?? []), m]);
  }
  const out: ChatSession[] = [];
  for (const [sid, list] of bySession) {
    list.sort((a, b) => a.timestamp - b.timestamp);
    const last = list[list.length - 1];
    out.push({
      id: sid, personaId,
      title: list.find((m) => m.role === "user")?.content.slice(0, 20) ?? "会话",
      lastMessage: last?.content ?? "",
      lastMessageTime: last?.timestamp ?? Date.now(),
      messageCount: list.length,
      createdAt: list[0]?.timestamp ?? Date.now(),
      updatedAt: last?.timestamp ?? Date.now(),
    });
  }
  return out.sort((a, b) => b.updatedAt - a.updatedAt);
}

export async function deleteMessage(messageId: string): Promise<void> {
  await dbRemove("messages", messageId);
  await dbRemove("favorites", messageId);
}

export async function toggleFavoriteMessage(messageId: string): Promise<void> {
  const all = await getAll<EngineMessage>("messages");
  const msg = all.find((m) => m.id === messageId);
  if (!msg) return;
  await put("messages", { ...msg, favorited: !msg.favorited } as unknown as { id: string });
  if (msg.favorited) {
    await dbRemove("favorites", messageId);
  } else {
    await put("favorites", {
      id: messageId, personaId: msg.personaId, messageId, role: msg.role,
      content: msg.content, emotion: msg.emotion, createdAt: Date.now(),
    } as unknown as { id: string });
  }
  emit("data-changed", { collection: "messages" });
}

/** 收藏消息列表（新增门面） */
export async function listFavoriteMessages(personaId?: string): Promise<ChatMessage[]> {
  const favs = personaId ? await getByPersona<{ messageId: string }>("favorites", personaId) : await getAll<{ messageId: string }>("favorites");
  const ids = new Set(favs.map((f) => f.messageId));
  const all = personaId ? await getByPersona<EngineMessage>("messages", personaId) : await getAll<EngineMessage>("messages");
  return all.filter((m) => ids.has(m.id) || m.favorited).sort((a, b) => b.timestamp - a.timestamp).map(engineToPageMessage);
}

export async function clearChatHistory(personaId: string): Promise<void> {
  const msgs = await getByPersona<EngineMessage>("messages", personaId);
  for (const m of msgs) await dbRemove("messages", m.id);
}

function engineToPageMessage(m: EngineMessage): ChatMessage {
  return {
    id: m.id,
    personaId: m.personaId,
    role: m.role,
    content: m.content,
    timestamp: m.timestamp,
    status: "sent",
    favorited: !!m.favorited,
    attachments: (m.attachments ?? []).map((url, i) => ({
      id: `${m.id}-a${i}`,
      type: url.startsWith("data:image") || /\.(png|jpe?g|gif|webp)/i.test(url) ? "image" as const : "file" as const,
      url, name: `附件${i + 1}`, size: 0, mimeType: "",
    })),
    toolCalls: (m.toolCalls ?? []).map((t) => ({
      id: t.id, name: t.name, arguments: t.arguments,
      status: t.status === "pending" ? "pending" as const : t.status, result: t.result,
    })),
    reactions: (m.reactions ?? []).map((r) => ({ ...r })),
    emotion: m.emotion,
    variants: m.variants?.map((v) => ({ content: v.content, createdAt: v.createdAt })),
    variantIdx: m.variantIdx,
  };
}

// ═══════════════════ 角色 ═══════════════════

export async function getPersonaList(): Promise<Persona[]> {
  return usePersonaStore.getState().personas;
}

export async function getPersona(personaId: string): Promise<Persona> {
  const p = usePersonaStore.getState().personas.find((x) => x.id === personaId);
  if (!p) throw new Error("角色不存在");
  return p;
}

export async function createPersona(params: CreatePersonaParams): Promise<Persona> {
  return usePersonaStore.getState().addPersona(params as unknown as Parameters<ReturnType<typeof usePersonaStore.getState>["addPersona"]>[0]);
}

export async function updatePersona(params: UpdatePersonaParams & { id?: string; personaId?: string }): Promise<Persona> {
  const { id, personaId, ...rest } = params as UpdatePersonaParams & { id?: string; personaId?: string };
  const target = id ?? personaId ?? "";
  usePersonaStore.getState().updatePersona(target, rest);
  return usePersonaStore.getState().personas.find((p) => p.id === target) as Persona;
}

export async function deletePersona(personaId: string): Promise<void> {
  usePersonaStore.getState().deletePersona(personaId);
  await contextEngine.clearPersonaMemory(personaId);
}

// ═══════════════════ 记忆（页面聚合视图） ═══════════════════

export async function getMemories(params: GetMemoriesParams): Promise<Memory[]> {
  const personaId = (params as unknown as { personaId?: string }).personaId ?? "";
  const [facts, pool] = await Promise.all([
    contextEngine.listMemoryFacts(personaId),
    getByPersona<{ id: string; personaId: string; content: string; importance: number; timestamp: number }>("memoryPool", personaId),
  ]);
  const toEntry = (id: string, content: string, ts: number, type: MemoryEntry["type"]): MemoryEntry => ({
    id, personaId, type, content, importance: "medium",
    tags: [], createdAt: ts, lastAccessedAt: ts, accessCount: 1, archived: false,
  });
  const memory: Memory = {
    id: `mem-${personaId}`, personaId,
    shortTerm: pool.slice(-20).map((e) => toEntry(e.id, e.content, e.timestamp, "event")),
    longTerm: facts.filter((f) => f.category !== "detail" && f.category !== "core").map((f) => toEntry(f.id, f.fact, f.timestamp, f.category === "preference" ? "preference" : "fact")),
    core: facts.filter((f) => f.category === "core").map((f) => toEntry(f.id, f.fact, f.timestamp, "relationship")),
    pools: [],
    totalCount: facts.length + pool.length,
    updatedAt: Date.now(),
  };
  return [memory];
}

export async function createMemory(params: CreateMemoryParams): Promise<void> {
  const p = params as unknown as { personaId: string; content?: string; fact?: string; category?: string };
  await contextEngine.addMemoryFact(p.personaId, p.content ?? p.fact ?? "", p.category ?? "fact");
}

export async function deleteMemory(memoryId: string): Promise<void> {
  await contextEngine.removeMemoryFact(memoryId);
}

export async function searchMemories(personaId: string, query: string): Promise<Memory[]> {
  const facts = await contextEngine.searchMemories(personaId, query);
  return [{
    id: `search-${Date.now()}`, personaId,
    shortTerm: [],
    longTerm: facts.map((f) => ({
      id: f.id, personaId, type: "fact" as const, content: f.fact, importance: "medium" as const,
      tags: [f.category], createdAt: f.timestamp, lastAccessedAt: f.timestamp, accessCount: 1, archived: false,
    })),
    core: [], pools: [], totalCount: facts.length, updatedAt: Date.now(),
  }];
}

// ═══════════════════ 语音 ═══════════════════

export async function textToSpeech(params: TtsParams): Promise<string> {
  const url = await voiceEngine.synthesize(params.text, {
    engine: (params.engine as "web" | "cloud" | "auto" | "edge-tts" | undefined) || undefined,
    voiceId: params.voiceId, speed: params.speed, pitch: params.pitch,
  });
  return url ?? "";
}

export async function stopSpeech(): Promise<void> {
  voiceEngine.stopPlayback();
  voiceEngine.stopSpeaking();
}

/** 语音识别：audioPath 为录音 Blob 时走云 ASR */
export async function speechToText(params: SttParams): Promise<string> {
  const audio = (params as unknown as { audioPath?: Blob | string }).audioPath;
  if (audio instanceof Blob) {
    return voiceEngine.cloudAsr(audio);
  }
  return "";
}

let webAsrSession: { stop?: () => void } | null = null;

/** 开始录音识别（Web Speech API；结果经 sd:voice-state 总线回传） */
export async function startRecording(): Promise<void> {
  try {
    const session = voiceEngine.startWebAsr({
      onPartial: (t) => emit("voice-state", { type: "partial", content: t }),
      onFinal: (t) => { emit("voice-state", { type: "final", content: t }); webAsrSession = null; },
      onError: (e) => { emit("voice-state", { type: "error", content: e }); webAsrSession = null; },
      onEnd: () => { webAsrSession = null; },
    });
    webAsrSession = (session ?? null) as { stop?: () => void } | null;
  } catch (e) {
    emit("voice-state", { type: "error", content: (e as Error).message });
  }
}

export async function stopRecording(): Promise<string> {
  webAsrSession?.stop?.();
  webAsrSession = null;
  return "";
}

// ═══════════════════ Live2D（前端 pixi 层直接控制，门面为兼容保留） ═══════════════════

export async function controlLive2D(params: Live2DControlParams): Promise<void> {
  emit("data-changed", { scope: "live2d", ...params });
}

// ═══════════════════ 设置 ═══════════════════

export async function getSettings(): Promise<Settings> {
  return useSettingsStore.getState().settings;
}

export async function saveSettings(settings: Settings): Promise<void> {
  useSettingsStore.getState().setSettings(settings);
}

// ═══════════════════ 群聊 ═══════════════════

export async function getGroupChatList(): Promise<GroupChat[]> {
  const groups = await groupEngine.listGroups();
  return groups.map(engineToPageGroup);
}

export async function getGroupChat(groupId: string): Promise<GroupChat> {
  const g = await groupEngine.getGroup(groupId);
  if (!g) throw new Error("群聊不存在");
  return engineToPageGroup(g);
}

export async function createGroupChat(name: string, memberIds: string[], worldId?: string): Promise<GroupChat> {
  const g = await groupEngine.createGroup(name, memberIds, worldId);
  return engineToPageGroup(g);
}

export async function deleteGroupChat(groupId: string): Promise<void> {
  await groupEngine.deleteGroup(groupId);
}

export async function sendGroupMessage(groupId: string, content: string, speakMode?: "free" | "turn" | "moderator"): Promise<void> {
  const modeMap = { free: "auto", turn: "manual", moderator: "ai_judge" } as const;
  await groupEngine.sendGroupMessage(groupId, content, { speakMode: modeMap[speakMode ?? "free"] });
}

function engineToPageGroup(g: GroupRecord): GroupChat {
  const personas = usePersonaStore.getState().personas;
  return {
    id: g.id, name: g.name, avatar: "", description: "",
    members: g.memberIds.map((pid, i) => {
      const p = personas.find((x) => x.id === pid);
      return { personaId: pid, name: p?.name ?? "未知角色", avatar: p?.avatar ?? "", isAdmin: i === 0, speakWeight: 1 };
    }),
    messages: g.messages.map((m) => ({
      id: m.id, groupId: g.id, personaId: m.senderId, content: m.content,
      timestamp: m.timestamp, isSystem: false,
    })),
    speakMode: "free", topic: "", worldId: g.worldId,
    createdAt: g.createdAt, updatedAt: g.updatedAt,
    lastMessageTime: g.messages[g.messages.length - 1]?.timestamp ?? g.updatedAt,
  };
}

// ═══════════════════ 成就 / 签到 / 成长 ═══════════════════

export async function getAchievements(personaId: string): Promise<Achievement[]> {
  const records = await achievementsEngine.getAllAchievements(personaId);
  const catalog = achievementsEngine.ACHIEVEMENT_CATALOG;
  return records.map((r) => {
    const def = catalog.find((c) => c.key === r.key);
    return engineToPageAchievement(r, def?.target ?? 1, def?.category ?? "chat");
  });
}

function engineToPageAchievement(r: AchievementRecord, target: number, category: string): Achievement {
  const rarityMap: Record<string, Achievement["rarity"]> = {
    chat: "common", checkin: "rare", affection: "rare", memory: "epic", diary: "epic", hidden: "legendary",
  };
  const mappedCategory: Achievement["category"] =
    ["chat", "memory", "emotion", "social", "special"].includes(category)
      ? (category as Achievement["category"])
      : category === "checkin" || category === "affection" ? "social"
      : category === "diary" || category === "hidden" ? "special"
      : "memory";
  return {
    id: r.id, personaId: r.personaId, title: r.title, description: r.description, icon: r.icon,
    unlocked: r.unlocked, unlockedAt: r.unlockedAt, progress: r.progress,
    target, category: mappedCategory, rarity: rarityMap[category] ?? "common",
  };
}

export async function checkIn(personaId: string): Promise<CheckIn> {
  const s = await checkinEngine.checkIn(personaId);
  return { personaId, streak: s.streak, totalDays: s.totalDays, lastCheckIn: Date.now(), todayCheckedIn: true };
}

export async function getCheckInStatus(personaId: string): Promise<CheckIn> {
  const s = await checkinEngine.getCheckInStatus(personaId);
  return { personaId, streak: s.streak, totalDays: s.totalDays, lastCheckIn: Date.now(), todayCheckedIn: s.checkedInToday };
}

export async function getGrowth(personaId: string): Promise<Growth> {
  const [g, aff, stats, ci, summary, facts] = await Promise.all([
    growthEngine.getGrowth(personaId),
    affectionEngine.getAffection(personaId),
    import("@/engine/stats").then((m) => m.getStats(personaId)),
    checkinEngine.getCheckInStatus(personaId),
    achievementsEngine.getAchievementSummary(personaId),
    contextEngine.listMemoryFacts(personaId),
  ]);
  return {
    personaId,
    favorability: aff?.points ?? 50,
    favorabilityLevel: aff?.level ?? 1,
    totalWords: (stats as unknown as { extra?: { totalTextLength?: number } } | null)?.extra?.totalTextLength ?? 0,
    totalChats: stats?.totalMessages ?? 0,
    totalMemories: facts.length,
    achievementsUnlocked: summary.unlocked,
    checkInDays: ci.totalDays,
  };
}

// ═══════════════════ 日记 ═══════════════════

export async function getDiaryList(personaId: string): Promise<Diary[]> {
  const list = await diaryEngine.getDiaryList(personaId);
  return list.map(engineToPageDiary);
}

export async function createDiary(personaId: string, title: string, content: string, mood: string): Promise<Diary> {
  const d = await diaryEngine.saveUserDiary(personaId, todayStr(), title, content || mood);
  return engineToPageDiary(d);
}

/** LLM 生成/追加今日日记 */
export async function generateDiary(personaId: string, force = false): Promise<Diary | null> {
  const d = await diaryEngine.generateDailyDiary(personaId, { force });
  return d ? engineToPageDiary(d) : null;
}

export async function deleteDiary(diaryId: string): Promise<void> {
  await diaryEngine.deleteDiaryById(diaryId);
}

export async function updateDiary(diaryId: string, content: string): Promise<Diary | null> {
  const d = await diaryEngine.updateDiaryContent(diaryId, content);
  return d ? engineToPageDiary(d) : null;
}

function engineToPageDiary(d: DiaryRecord): Diary {
  return {
    id: d.id, personaId: d.personaId, title: d.title, content: d.content,
    mood: d.mood, weather: "", attachments: [], tags: [d.date],
    createdAt: d.createdAt, updatedAt: d.updatedAt,
  };
}

function todayStr(): string {
  const n = new Date();
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, "0")}-${String(n.getDate()).padStart(2, "0")}`;
}

// ═══════════════════ 朋友圈 ═══════════════════

export async function getMoments(personaId: string): Promise<Moment[]> {
  const list = await momentsEngine.listMoments(personaId);
  const me = userName();
  return list.map((m) => engineToPageMoment(m, me));
}

export async function publishMoment(personaId: string, content: string, images: string[]): Promise<Moment> {
  const m = await momentsEngine.publishUserMoment(personaId, content, images);
  return engineToPageMoment(m, userName());
}

export async function likeMoment(momentId: string): Promise<void> {
  await momentsEngine.toggleLike(momentId, userName());
  emit("data-changed", { collection: "moments" });
}

export async function commentMoment(momentId: string, content: string): Promise<void> {
  await momentsEngine.userCommentOnMoment(momentId, content);
}

export async function deleteMoment(momentId: string): Promise<void> {
  await momentsEngine.deleteMoment(momentId);
}

/** AI 主动发一条动态 */
export async function generateAiMoment(personaId: string): Promise<Moment | null> {
  const m = await momentsEngine.generateAiMoment(personaId);
  return m ? engineToPageMoment(m, userName()) : null;
}

function engineToPageMoment(m: MomentRecord, me: string): Moment {
  return {
    id: m.id, personaId: m.personaId, content: m.content, images: m.images,
    mood: m.source === "ai" ? "ai" : "user",
    likes: m.likes.length, liked: m.likes.includes(me) || m.likes.includes(m.author),
    comments: m.comments.map((c) => ({
      id: c.id, momentId: m.id, authorId: c.isUser ? "user" : m.personaId,
      authorName: c.author, content: c.content, createdAt: c.createdAt,
    })),
    createdAt: m.createdAt,
  };
}

// ═══════════════════ 日历 ═══════════════════

export async function getCalendarEvents(personaId: string, startTime: number, endTime: number): Promise<CalendarEvent[]> {
  const events = await calendarEngine.listEvents(personaId || undefined);
  return events
    .map(engineToPageEvent)
    .filter((e) => e.startTime >= startTime && e.startTime <= endTime);
}

export async function createCalendarEvent(event: Omit<CalendarEvent, "id" | "createdAt" | "updatedAt"> & { id?: string }): Promise<CalendarEvent> {
  const rec = await calendarEngine.addEvent({
    personaId: event.personaId, date: new Date(event.startTime).toISOString().slice(0, 10),
    type: event.type === "birthday" ? "birthday" : event.recurrence ? "anniversary" : "user",
    title: event.title, recurring: !!event.recurrence,
  });
  return {
    ...engineToPageEvent(rec),
    description: event.description ?? "", allDay: event.allDay ?? false,
    reminderMinutes: event.reminderMinutes ?? 0, color: event.color ?? "",
    startTime: event.startTime, endTime: event.endTime,
    recurrence: event.recurrence, type: event.type,
  };
}

export async function deleteCalendarEvent(eventId: string): Promise<void> {
  await calendarEngine.deleteEvent(eventId);
}

function engineToPageEvent(e: CalendarRecord): CalendarEvent {
  const start = new Date(`${e.date}T09:00:00`).getTime();
  return {
    id: e.id, personaId: e.personaId ?? "", title: e.title, description: "",
    startTime: start, endTime: start + 3600_000, allDay: true,
    recurrence: e.recurring ? "yearly" : undefined, reminderMinutes: 0, color: "",
    type: e.type === "birthday" ? "birthday" : e.type === "anniversary" ? "anniversary" : "custom",
    createdAt: e.createdAt, updatedAt: e.createdAt,
  };
}

/** 今日事件（公历+农历节日，对齐 CalendarManager） */
export async function getTodayCalendarInfo() {
  return calendarEngine.getTodayEventsFull();
}

// ═══════════════════ 表情包 ═══════════════════

export async function getStickerPacks(): Promise<StickerPack[]> {
  const list = await stickersEngine.listStickers();
  return [{
    id: "all", name: "全部表情", cover: list[0]?.dataUrl ?? "",
    stickers: list.map(engineToPageSticker), builtIn: true, createdAt: Date.now(),
  }];
}

export async function importSticker(input: { name: string; tags: string[]; dataUrl: string }): Promise<Sticker> {
  const s = await stickersEngine.addSticker(input);
  return engineToPageSticker(s);
}

/** 旧接口兼容 */
export async function importStickerPack(path: string): Promise<StickerPack> {
  void path;
  const packs = await getStickerPacks();
  return packs[0];
}

export async function deleteStickerById(id: string): Promise<void> {
  await stickersEngine.deleteSticker(id);
}

function engineToPageSticker(s: { id: string; name: string; dataUrl: string; tags: string[]; createdAt: number; useCount: number }): Sticker {
  return {
    id: s.id, name: s.name, path: s.dataUrl, thumbnail: s.dataUrl,
    packId: "all", tags: s.tags, favorited: false, useCount: s.useCount, createdAt: s.createdAt,
  };
}

// ═══════════════════ 时光胶囊 ═══════════════════

export async function getTimeCapsules(personaId: string): Promise<TimeCapsule[]> {
  const all = await getByPersona<CapsuleRecord>("capsules", personaId);
  return all.map((c) => ({
    id: c.id, personaId: c.personaId, title: c.title, content: c.content,
    attachments: c.images, sealedAt: c.createdAt, openAt: c.openAt,
    opened: c.opened, openMessage: c.opened ? c.content : undefined, createdAt: c.createdAt,
  }));
}

export async function createTimeCapsule(personaId: string, title: string, content: string, openAt: number, images: string[] = []): Promise<TimeCapsule> {
  const record: CapsuleRecord = {
    id: newId("capsule"), personaId, title, content, images,
    openAt, createdAt: Date.now(), opened: false,
  };
  await put("capsules", record);
  emit("data-changed", { collection: "capsules" });
  return { id: record.id, personaId, title, content, attachments: images, sealedAt: record.createdAt, openAt, opened: false, createdAt: record.createdAt };
}

export async function openTimeCapsule(capsuleId: string): Promise<TimeCapsule | null> {
  const c = await getOne<CapsuleRecord>("capsules", capsuleId);
  if (!c) return null;
  if (Date.now() < c.openAt) return null;
  const updated = { ...c, opened: true, openedAt: Date.now() };
  await put("capsules", updated);
  return { id: c.id, personaId: c.personaId, title: c.title, content: c.content, attachments: c.images, sealedAt: c.createdAt, openAt: c.openAt, opened: true, openMessage: c.content, createdAt: c.createdAt };
}

export async function deleteTimeCapsule(capsuleId: string): Promise<void> {
  await dbRemove("capsules", capsuleId);
}

// ═══════════════════ 纪念相册 ═══════════════════

export async function getAlbumEntries(personaId: string): Promise<AlbumEntry[]> {
  const all = await getByPersona<AlbumRecord>("albumEntries", personaId);
  return all.map((a) => ({
    id: a.id, personaId: a.personaId, title: a.title, description: a.description,
    imagePath: a.image, date: new Date(a.date).getTime() || a.createdAt, tags: a.tag ? [a.tag] : [], createdAt: a.createdAt,
  }));
}

export async function addAlbumEntry(personaId: string, title: string, imagePath: string, description: string): Promise<AlbumEntry> {
  const record: AlbumRecord = {
    id: newId("album"), personaId, title, description, image: imagePath,
    date: todayStr(), createdAt: Date.now(),
  };
  await put("albumEntries", record);
  emit("data-changed", { collection: "albumEntries" });
  return { id: record.id, personaId, title, description, imagePath, date: Date.now(), tags: [], createdAt: record.createdAt };
}

export async function deleteAlbumEntry(id: string): Promise<void> {
  await dbRemove("albumEntries", id);
}

// ═══════════════════ 虚拟世界 ═══════════════════

export async function getWorldConfig(worldId: string): Promise<WorldConfig | null> {
  const w = await worldEngine.getWorld(worldId);
  return w ? engineToWorldConfig(w) : null;
}

export async function getWorldState(worldId: string): Promise<WorldState> {
  const w = await worldEngine.getWorld(worldId);
  const events = await worldEngine.getStoryEvents(worldId);
  return {
    worldId,
    timeline: w?.state ? `第${w.state.day}天 ${w.state.timeOfDay}` : "",
    location: w?.state?.location ?? "",
    weather: String(w?.state?.flags?.weather ?? ""),
    eventHistory: events.map((e) => ({
      id: e.id, worldId, title: `第${e.day}天 ${e.time}`, description: e.content,
      participants: e.characters, type: "action" as const, timestamp: e.timestamp, consequences: [],
    })),
    updatedAt: w?.updatedAt ?? Date.now(),
  };
}

export async function listWorlds(): Promise<WorldConfig[]> {
  return (await worldEngine.listWorlds()).map(engineToWorldConfig);
}

export async function createWorld(config: Omit<WorldConfig, "id" | "createdAt" | "updatedAt">): Promise<WorldConfig> {
  const w = await worldEngine.createWorld({
    name: config.name, description: config.description,
    lore: [config.lore, config.era, config.geography, config.society, config.system].filter(Boolean).join("\n\n"),
    characters: (config.personaIds ?? []).map((pid) => {
      const p = usePersonaStore.getState().personas.find((x) => x.id === pid);
      return { id: pid, name: p?.name ?? "角色", role: "" };
    }),
    autoTick: true, tickIntervalMin: 60,
  });
  return engineToWorldConfig(w);
}

export async function updateWorldConfig(worldId: string, patch: Partial<WorldConfig>): Promise<void> {
  const w = await worldEngine.getWorld(worldId);
  if (!w) return;
  if (patch.name) w.name = patch.name;
  if (patch.description !== undefined) w.description = patch.description;
  if (patch.lore !== undefined) w.lore = patch.lore;
  await worldEngine.updateWorld(w);
}

export async function updateWorldLore(worldId: string, lore: string): Promise<void> {
  await updateWorldConfig(worldId, { lore });
}

/** 手动推演一次 */
export async function runWorldTick(worldId: string): Promise<string | null> {
  const e = await worldEngine.runWorldTick(worldId, { generateImage: true });
  return e?.content ?? null;
}

/** 世界自动推演开关 */
export async function setWorldAutoTick(worldId: string, enabled: boolean): Promise<void> {
  const w = await worldEngine.getWorld(worldId);
  if (w) await worldEngine.updateWorld({ ...w, autoTick: enabled });
}

/** 世界故事事件 */
export async function getWorldEvents(worldId: string): Promise<WorldEventRecord[]> {
  return worldEngine.getStoryEvents(worldId);
}

export async function deleteWorld(worldId: string): Promise<void> {
  await worldEngine.deleteWorld(worldId);
}

function engineToWorldConfig(w: WorldRecord): WorldConfig {
  return {
    id: w.id, name: w.name, description: w.description, lore: w.lore,
    era: "", geography: "", society: "", system: "", rules: [],
    personaIds: w.characters.map((c) => c.id),
    createdAt: w.createdAt, updatedAt: w.updatedAt,
  };
}

// ═══════════════════ 闹钟 / 电台 ═══════════════════

export async function getAlarms(): Promise<CalendarEvent[]> {
  const alarms = await wakeupEngine.listAlarms();
  return alarms.map((a) => {
    const start = new Date();
    start.setHours(a.hour, a.minute, 0, 0);
    return {
      id: a.id, personaId: a.personaId ?? "", title: a.label, description: "",
      startTime: start.getTime(), endTime: start.getTime() + 300_000, allDay: false,
      recurrence: a.repeatDays.length === 7 ? "daily" : "weekly",
      reminderMinutes: 0, color: "", type: "alarm" as const, createdAt: a.createdAt, updatedAt: a.createdAt,
    };
  });
}

/** 结构化闹钟接口（推荐新代码使用） */
export const alarmsApi = {
  list: () => wakeupEngine.listAlarms(),
  add: (input: { hour: number; minute: number; label?: string; repeatDays?: number[]; personaId?: string }) => wakeupEngine.addAlarm({ label: "", ...input }),
  remove: (id: string) => wakeupEngine.deleteAlarm(id),
  toggle: (id: string, enabled: boolean) => wakeupEngine.toggleAlarm(id, enabled),
};

export async function createAlarm(time: string, label: string, repeat?: string): Promise<CalendarEvent> {
  const [h, m] = time.split(":").map(Number);
  const repeatDays = repeat === "daily" ? [0, 1, 2, 3, 4, 5, 6] : [];
  const a = await wakeupEngine.addAlarm({ hour: h || 0, minute: m || 0, label, repeatDays });
  const start = new Date();
  start.setHours(a.hour, a.minute, 0, 0);
  return {
    id: a.id, personaId: "", title: label, description: "", startTime: start.getTime(), endTime: start.getTime() + 300_000,
    allDay: false, recurrence: repeat as CalendarEvent["recurrence"], reminderMinutes: 0, color: "", type: "alarm",
    createdAt: a.createdAt, updatedAt: a.createdAt,
  };
}

export async function deleteAlarm(alarmId: string): Promise<void> {
  await wakeupEngine.deleteAlarm(alarmId);
}

// ─── 电台（Web 端用 <audio> 直接播放网络电台流） ───

const RADIO_DEFAULT_LIST = [
  { id: "radio-lofi", title: "Lofi 学习电台", url: "https://play.streamafrica.net/lofiradio", cover: "" },
  { id: "radio-calm", title: "轻音乐安抚台", url: "https://strm112.1.fm/calmmeditative_mobile_mp3", cover: "" },
  { id: "radio-jazz", title: "深夜爵士台", url: "https://strm112.1.fm/smoothjazz_mobile_mp3", cover: "" },
  { id: "radio-ambient", title: "助眠白噪音", url: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", cover: "" },
];

export async function getRadioList(): Promise<Array<{ id: string; title: string; url: string; cover: string }>> {
  const custom = await kvGet<Array<{ id: string; title: string; url: string; cover: string }>>("radio-list");
  return custom ?? RADIO_DEFAULT_LIST;
}

let radioAudio: HTMLAudioElement | null = null;

export async function playRadio(radioId: string): Promise<void> {
  const list = await getRadioList();
  const item = list.find((r) => r.id === radioId);
  stopRadio();
  if (!item) return;
  radioAudio = new Audio(item.url);
  radioAudio.crossOrigin = "anonymous";
  try {
    await radioAudio.play();
    emit("data-changed", { scope: "radio", playing: radioId });
  } catch {
    radioAudio = null;
    throw new Error("电台流无法播放（可能被网络策略拦截）");
  }
}

export function stopRadio(): void {
  if (radioAudio) {
    radioAudio.pause();
    radioAudio.src = "";
    radioAudio = null;
    emit("data-changed", { scope: "radio", playing: null });
  }
}

// ═══════════════════ 激活码 ═══════════════════

const ACTIVATION_CODES: Record<string, string> = {
  STARDUST2026: "pro",
  "QIANWEN-FRIEND": "pro",
  WELCOME: "basic",
};

export async function activateCode(code: string): Promise<ActivationResult> {
  const normalized = code.trim().toUpperCase();
  if (ACTIVATION_CODES[normalized]) {
    await kvSet("activation", { tier: ACTIVATION_CODES[normalized], at: Date.now() });
    emit("data-changed", { scope: "activation" });
    return { success: true };
  }
  return { success: false, error: "激活码无效，请检查后重试" };
}

export async function getActivationStatus(): Promise<{ activated: boolean; tier?: string }> {
  const a = await kvGet<{ tier: string; at: number }>("activation");
  return a ? { activated: true, tier: a.tier } : { activated: false };
}

// ═══════════════════ 本地模型 / Live2D 模型管理 ═══════════════════

export interface LocalModelInfo {
  id: string;
  name: string;
  /** Ollama 模型名或内置能力标识 */
  path: string;
  size: number;
  installed: boolean;
  source: "ollama" | "rag" | "web-asr" | "web-tts";
  description: string;
}

/** 本地模型 = Ollama 已装模型 + 内置本地能力（RAG 向量/浏览器语音），Web 等价实现 */
export async function getLocalModels(): Promise<LocalModelInfo[]> {
  const builtin: LocalModelInfo[] = [
    { id: "builtin-rag", name: "TF-IDF 人设检索（内置）", size: 0, installed: true, source: "rag", path: "builtin://tfidf-rag", description: "角色设定向量检索，零依赖本地运行" },
    { id: "builtin-web-asr", name: "浏览器语音识别（内置）", size: 0, installed: !!getSpeechRecognitionCtor(), source: "web-asr", path: "builtin://web-speech-asr", description: "使用浏览器内置语音识别引擎" },
    { id: "builtin-web-tts", name: "浏览器语音合成（内置）", size: 0, installed: typeof window !== "undefined" && "speechSynthesis" in window, source: "web-tts", path: "builtin://web-speech-tts", description: "使用浏览器内置 TTS 引擎" },
  ];
  const provider = useSettingsStore.getState().settings.providers.find((p) => p.provider === "local");
  if (provider?.baseUrl) {
    try {
      const base = provider.baseUrl.replace(/\/+$/, "").replace(/\/v1$/, "");
      const res = await fetch(`${base}/api/tags`, { signal: AbortSignal.timeout(6000) });
      if (res.ok) {
        const data = (await res.json()) as { models?: { name: string; size?: number }[] };
        const ollama: LocalModelInfo[] = (data.models ?? []).map((m) => ({
          id: `ollama-${m.name}`, name: m.name, path: m.name, size: m.size ?? 0, installed: true, source: "ollama", description: "Ollama 本地模型",
        }));
        return [...ollama, ...builtin];
      }
    } catch { /* Ollama 未运行 */ }
  }
  return builtin;
}

function getSpeechRecognitionCtor(): unknown {
  if (typeof window === "undefined") return null;
  return (window as unknown as { SpeechRecognition?: unknown; webkitSpeechRecognition?: unknown }).SpeechRecognition
    ?? (window as unknown as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition;
}

export async function downloadLocalModel(modelId: string): Promise<void> {
  if (!modelId.startsWith("ollama-")) throw new Error("内置能力无需下载");
  const provider = useSettingsStore.getState().settings.providers.find((p) => p.provider === "local");
  if (!provider?.baseUrl) throw new Error("未配置 Ollama 服务地址");
  const base = provider.baseUrl.replace(/\/+$/, "").replace(/\/v1$/, "");
  const res = await fetch(`${base}/api/pull`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model: modelId.replace("ollama-", ""), stream: false }),
    signal: AbortSignal.timeout(600_000),
  });
  if (!res.ok) throw new Error(`Ollama 拉取失败 (${res.status})`);
}

export async function deleteLocalModel(modelId: string): Promise<void> {
  if (!modelId.startsWith("ollama-")) throw new Error("内置能力不可删除");
  const provider = useSettingsStore.getState().settings.providers.find((p) => p.provider === "local");
  if (!provider?.baseUrl) throw new Error("未配置 Ollama 服务地址");
  const base = provider.baseUrl.replace(/\/+$/, "").replace(/\/v1$/, "");
  const res = await fetch(`${base}/api/delete`, {
    method: "DELETE",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model: modelId.replace("ollama-", "") }),
  });
  if (!res.ok) throw new Error(`Ollama 删除失败 (${res.status})`);
}

/** Live2D 模型注册表（前端 pixi 管线；模型文件在 public/models 或用户导入） */
export interface Live2DModelInfo {
  id: string;
  name: string;
  path: string;
  thumbnail: string;
}

export async function getModelList(): Promise<Live2DModelInfo[]> {
  const custom = (await kvGet<Live2DModelInfo[]>("live2d-custom-models")) ?? [];
  const builtin: Live2DModelInfo[] = [
    { id: "hiyori", name: "Hiyori（样例）", path: "/models/hiyori/hiyori_pro_t11.model3.json", thumbnail: "" },
  ];
  return [...builtin, ...custom];
}

/** 导入自定义 Live2D 模型（登记 model3.json 的 URL/路径） */
export async function importModel(pathOrName: string): Promise<void> {
  const list = (await kvGet<Live2DModelInfo[]>("live2d-custom-models")) ?? [];
  if (!list.find((m) => m.path === pathOrName)) {
    await kvSet("live2d-custom-models", [...list, {
      id: newId("l2d"),
      name: pathOrName.split(/[\\/]/).pop()?.replace(/\.model3\.json$/, "") ?? "自定义模型",
      path: pathOrName, thumbnail: "",
    }]);
    emit("data-changed", { collection: "live2d-models" });
  }
}

export async function deleteModel(modelId: string): Promise<void> {
  const custom = (await kvGet<Live2DModelInfo[]>("live2d-custom-models")) ?? [];
  await kvSet("live2d-custom-models", custom.filter((m) => m.id !== modelId));
  emit("data-changed", { collection: "live2d-models" });
}

// ═══════════════════ 皮肤商店 / 气泡皮肤 ═══════════════════

export interface SkinListItem {
  id: string;
  name: string;
  preview: string;
  price: number;
  owned: boolean;
}

export async function getSkinList(): Promise<SkinListItem[]> {
  const skins = skinsEngine.listSkins();
  return Promise.all(skins.map(async (s) => ({
    id: s.id, name: s.name, preview: s.preview, price: s.price,
    owned: await skinsEngine.isSkinOwned(s.id),
  })));
}

export async function purchaseSkin(skinId: string): Promise<PurchaseResult> {
  const ok = await skinsEngine.purchaseSkin(skinId);
  return ok ? { success: true } : { success: false, error: "星尘币不足或已拥有" };
}

export async function equipSkinById(skinId: string): Promise<boolean> {
  return skinsEngine.equipSkin(skinId);
}

export async function getBubbleSkinList() {
  const list = skinsEngine.listBubbleSkins();
  return Promise.all(list.map(async (b) => ({ ...b, owned: await skinsEngine.isBubbleSkinOwned(b.id) })));
}

export async function purchaseBubbleSkin(id: string): Promise<PurchaseResult> {
  const ok = await skinsEngine.purchaseBubbleSkin(id);
  return ok ? { success: true } : { success: false, error: "星尘币不足或已拥有" };
}

export async function equipBubbleSkinById(id: string): Promise<boolean> {
  return skinsEngine.equipBubbleSkin(id);
}

export async function getCoins(): Promise<number> {
  return useSettingsStore.getState().settings.coins ?? 0;
}

// ═══════════════════ 唤醒任务 / iLink / 迁移 / 日程（扩展门面） ═══════════════════

export const wakeTasksApi = {
  list: (personaId: string) => wakeupEngine.listWakeTasks(personaId),
  add: (input: Parameters<typeof wakeupEngine.addWakeTask>[0]) => wakeupEngine.addWakeTask(input),
  remove: (id: string) => wakeupEngine.deleteWakeTask(id),
  toggle: (id: string, enabled: boolean) => wakeupEngine.toggleWakeTask(id, enabled),
};

export const ilinkApi = {
  generateQRCode: () => ilinkEngine.generateQRCode(),
  getQrcodeStatus: (q: string) => ilinkEngine.getQrcodeStatus(q),
  bindBot: (token: string, extra?: Parameters<typeof ilinkEngine.bindBot>[1]) => ilinkEngine.bindBot(token, extra),
  unbind: () => ilinkEngine.unbindBot(),
  getBinding: () => ilinkEngine.getBinding(),
  setBinding: (b: Awaited<ReturnType<typeof ilinkEngine.getBinding>>) => b ? ilinkEngine.setBinding(b) : Promise.resolve(),
  sendMessage: (conversationId: string, text: string, contextToken = "") => ilinkEngine.sendMessage(conversationId, text, contextToken),
  start: () => ilinkEngine.start(),
  stop: () => ilinkEngine.stop(),
  isPolling: () => ilinkEngine.isPolling(),
  listMessages: async (conversationId?: string) => {
    const all = await getAll<import("@/engine/kinds").IlinkMessageRecord>("ilinkMessages");
    const filtered = conversationId ? all.filter((m) => m.conversationId === conversationId) : all;
    return filtered.sort((a, b) => Number(b.timestamp ?? 0) - Number(a.timestamp ?? 0));
  },
};

export const migrationApi = {
  exportSnapshot: () => migrationEngine.exportSnapshot(),
  importSnapshot: (file: File, mode: "merge" | "replace") => migrationEngine.importSnapshot(file, mode),
  importAndroidJson: (json: string, personaId?: string) => migrationEngine.importAndroidJson(json, personaId),
};

export const schedulesApi = {
  list: (personaId: string, includeDone = false) => wakeupEngine.listSchedules(personaId, includeDone),
  add: (personaId: string, timeIso: string, content: string) => wakeupEngine.addSchedule(personaId, timeIso, content),
  complete: (id: string) => wakeupEngine.completeSchedule(id),
};

export const nicknamesFacade = {
  list: (personaId: string) => import("@/engine/nicknames").then((m) => m.listNicknames(personaId)),
  set: (personaId: string, name: string) => import("@/engine/nicknames").then((m) => m.setNickname(personaId, name)),
  remove: (id: string) => import("@/engine/nicknames").then((m) => m.deleteNickname(id)),
};

/** Tauri 桌面独有能力的透传入口（窗口/自启等），浏览器模式抛错由调用方处理 */
export async function desktopInvoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  if (!isTauri()) throw new Error("该功能仅在桌面版可用");
  return desktopRawInvoke<T>(cmd, args);
}
