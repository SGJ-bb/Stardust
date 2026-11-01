/**
 * 聊天引擎 —— 组装 Android ChatViewModel + ApiClient.sendChatWithToolLoop 的完整链路：
 * 输入安全过滤 → 身份/记忆/RAG/世界书/屏幕感知提示词 → 流式 tool 循环
 * → [[emotion]] 协议解析 → Humanizer → 落库 → 好感度/统计/成就/情绪守护后处理 → TTS。
 * 浏览器与 Tauri 双模式同一实现。
 */

import { usePersonaStore } from "@/stores/usePersonaStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { getAll, getByPersona, put, remove as dbRemove, newId } from "@/lib/db";
import { emit } from "@/lib/events";
import type { EngineMessage, Emotion } from "./kinds";
import {
  chatCompletionStream, extractEmotionAction, getActiveProvider,
  type ChatMsg,
} from "./llmClient";
import {
  buildIdentityById, buildChatPrompt,
} from "./promptBuilder";
import { searchRag, buildIndex } from "./rag";
import { getContextBlock, addTurn, incrementTurn, checkMemoryLimit, startNewSession, getInheritedMemory, listMemoryFacts } from "./context";
import { recordEmotion, shouldShowCare, getCareMessage, markCareSent, mapEmotion } from "./emotion";
import { humanize } from "./humanizer";
import { filterContent, getRefusalResponse } from "./safety";
import { processMessage } from "./affection";
import { recordMessage, getStats } from "./stats";
import { checkAndUnlock } from "./achievements";
import { checkAutomaticMilestones } from "./milestones";
import { getActiveNickname } from "./nicknames";
import { predictReplies } from "./predictor";
import { getEnabledToolDefinitions, executeTool, getEmotionHint, type ToolRunResult } from "./plugins";
import { requestToolConfirm } from "./toolConfirm";
import { getScreenAwarenessBlock } from "./screen";
import { speak, isTTSEnabled } from "./voice";
import { getChatDays } from "./stats";

export interface TurnOptions {
  /** 附件图片（dataURL/url）→ 视觉消息 */
  attachments?: string[];
  /** 多会话隔离 */
  sessionId?: string;
  /** 静默模式：群聊/iLink/圈评复用 AI 人设但不写主聊天记录、不触发 TTS */
  silent?: boolean;
  /** silent 模式下自定义人设 ID 覆盖 */
  personaOverrideId?: string;
  /** 额外系统上下文（微信规则等） */
  extraSystem?: string;
  signal?: AbortSignal;
}

export interface TurnCallbacks {
  onToken?: (accumulated: string) => void;
  onToolCall?: (name: string, args: string) => void;
  onToolResult?: (name: string, result: ToolRunResult) => void;
  onEmotion?: (emotion: Emotion, action: string) => void;
}

export interface TurnResult {
  message: EngineMessage;
  emotion: Emotion;
  action: string;
  suggestions: string[];
  blocked: boolean;
  toolEffects: ToolRunResult["sideEffect"][];
}

const MAX_TOOL_ROUNDS = 5;

export async function saveMessage(msg: EngineMessage): Promise<EngineMessage> {
  await put("messages", msg as unknown as { id: string });
  return msg;
}

export async function loadHistory(personaId: string, sessionId?: string, limit = 100): Promise<EngineMessage[]> {
  const all = await getByPersona<EngineMessage & { sessionId?: string }>("messages", personaId);
  const filtered = sessionId ? all.filter((m) => !m.sessionId || m.sessionId === sessionId) : all;
  return filtered.sort((a, b) => a.timestamp - b.timestamp).slice(-limit);
}

/** 人设索引懒构建（首次对话时） */
const ragBuilt = new Set<string>();
async function ensureRagIndex(personaId: string) {
  if (ragBuilt.has(personaId)) return;
  ragBuilt.add(personaId);
  const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
  if (persona) {
    try { await buildIndex(persona as unknown as Parameters<typeof buildIndex>[0]); } catch { /* 索引失败不阻塞聊天 */ }
  }
}

/**
 * 发送一轮对话（主链路）。silent 模式下只走 LLM 不写主记录/不后处理养成。
 */
export async function sendTurn(
  personaId: string,
  userText: string,
  opts: TurnOptions = {},
  cb: TurnCallbacks = {},
): Promise<TurnResult> {
  const settings = useSettingsStore.getState().settings;
  const sPersonaId = opts.personaOverrideId ?? personaId;
  const persona = usePersonaStore.getState().personas.find((p) => p.id === sPersonaId);
  if (!persona) throw new Error("角色不存在");
  if (!getActiveProvider()) throw new Error("未配置 AI 模型，请先在设置中添加 LLM 提供商");

  // 1) 输入安全过滤（对齐 MainActivity 送 LLM 前过滤）
  const safety = filterContent(userText);
  if (!safety.allowed) {
    const refusal: EngineMessage = {
      id: newId("msg"), personaId, sessionId: opts.sessionId, role: "assistant",
      content: getRefusalResponse(), timestamp: Date.now(), emotion: "sad", action: "idle",
      attachments: [], toolCalls: [], meta: { blocked: true },
    };
    if (!opts.silent) await saveMessage(refusal);
    return { message: refusal, emotion: "sad", action: "idle", suggestions: [], blocked: true, toolEffects: [] };
  }

  if (!opts.silent) {
    await saveMessage({
      id: newId("msg"), personaId, sessionId: opts.sessionId, role: "user",
      content: userText, timestamp: Date.now(), attachments: opts.attachments ?? [], toolCalls: [],
    });
    incrementTurn(personaId).catch(() => undefined);
  }

  ensureRagIndex(sPersonaId).catch(() => undefined);

  // 2) 组装系统提示词（身份 + 记忆 + RAG + 世界书 + 好感度 + 屏幕感知 + 昵称）
  const nickname = await getActiveNickname(sPersonaId).catch(() => null);
  const identity = buildIdentityById(sPersonaId, { nickname: nickname ?? undefined });
  const [contextBlock, ragChunks, memoryFacts, screenBlock, emotionHint] = await Promise.all([
    getContextBlock(sPersonaId, userText).catch(() => ""),
    searchRag(sPersonaId, persona.name, userText).catch(() => [] as string[]),
    listMemoryFacts(sPersonaId).catch(() => []),
    getScreenAwarenessBlock().catch(() => ""),
    getEmotionHint(sPersonaId).catch(() => ""),
  ]);
  const history = opts.silent ? [] : (await loadHistory(personaId, opts.sessionId)).slice(-((settings.chatParams?.contextTurns ?? 10) * 2));
  const affection = await processMessage(sPersonaId, true, { message: userText }).catch(() => null);

  const worldLore = opts.extraSystem ? opts.extraSystem : undefined;
  const systemPrompt = buildChatPrompt(
    identity,
    "neutral", "idle",
    memoryFacts.slice(-10).map((f) => f.fact),
    {
      worldLore: opts.extraSystem ?? undefined,
      userMessage: userText,
      affectionTitle: affection?.title,
      ragChunks,
      contextBlock: [contextBlock, screenBlock ? `[屏幕感知]\n${screenBlock}` : ""].filter(Boolean).join("\n\n") || null,
      now: new Date(),
    },
  );

  // 3) 消息序列：system + 历史 + 本轮（含图片）
  const messages: ChatMsg[] = [{ role: "system", content: systemPrompt }];
  for (const h of history) {
    if (h.role === "user" || h.role === "assistant") {
      messages.push({ role: h.role, content: h.content, images: h.attachments?.length ? h.attachments : undefined, toolCalls: undefined });
    }
  }
  messages.push({ role: "user", content: userText, images: opts.attachments?.length ? opts.attachments : undefined });

  // 4) tool 循环 + 流式
  const tools = getEnabledToolDefinitions();
  const effects: ToolRunResult["sideEffect"][] = [];
  let round = 0;
  let finalText = "";
  const runOnce = async () => {
    const result = await chatCompletionStream(
      messages,
      {
        signal: opts.signal,
        onToken: (t) => cb.onToken?.(t),
        onError: () => undefined,
      },
      { tools: tools.length ? tools : undefined },
    );
    return result;
  };
  let streamResult = await runOnce();
  while (streamResult.toolCalls.length > 0 && round < MAX_TOOL_ROUNDS) {
    round++;
    // 记录 assistant 的 tool_calls 轮
    messages.push({ role: "assistant", content: streamResult.text, toolCalls: streamResult.toolCalls });
    for (const call of streamResult.toolCalls) {
      cb.onToolCall?.(call.name, call.arguments);
      const runResult = await executeTool(call.name, call.arguments, {
        personaId: sPersonaId,
        confirm: (toolName, summary) =>
          requestToolConfirm({ id: `tc-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`, personaId: sPersonaId, toolName, argsSummary: summary }),
      });
      if (runResult.sideEffect) effects.push(runResult.sideEffect);
      cb.onToolResult?.(call.name, runResult);
      messages.push({ role: "tool", content: runResult.text, toolCallId: call.id });
    }
    streamResult = await runOnce();
  }
  finalText = streamResult.text;

  // 5) 解析 [[emotion]] 协议
  const { cleanText, emotion: emoRaw, action } = extractEmotionAction(finalText);
  const emotion = mapEmotion(emoRaw);
  cb.onEmotion?.(emotion, action);

  // 6) Humanizer（对齐 ChatViewModel 输出后处理）
  const shown = settings.aiFeatures?.humanizerEnabled === false ? cleanText : humanize(cleanText, sPersonaId);

  const petMsg: EngineMessage = {
    id: newId("msg"), personaId, sessionId: opts.sessionId, role: "assistant",
    content: shown || cleanText, timestamp: Date.now(), emotion, action: action as EngineMessage["action"],
    attachments: [], toolCalls: streamResult.toolCalls.map((t) => ({ ...t, status: "completed" as const })),
    meta: effects.length ? { effects } : undefined,
  };
  if (!opts.silent) {
    await saveMessage(petMsg);

    // 7) 后处理链（对齐 ChatViewModel/MainActivity）
    addTurn(personaId, userText, shown || cleanText).catch(() => undefined);
    recordMessage(personaId, "user", undefined, userText.length).catch(() => undefined);
    recordMessage(personaId, "assistant", emotion, (shown || cleanText).length).catch(() => undefined);
    recordEmotion(personaId, emotion, undefined, "chat").catch(() => undefined);
    processMessage(personaId, false, { emotion }).catch(() => undefined);

    // 情绪守护：连续负面情绪主动关怀（对齐 EmotionGuardian 消费点）
    (async () => {
      try {
        if (await shouldShowCare(personaId)) {
          const care = await getCareMessage(personaId);
          if (care) {
            await markCareSent(personaId);
            const careMsg: EngineMessage = {
              id: newId("msg"), personaId, sessionId: opts.sessionId, role: "assistant",
              content: care, timestamp: Date.now() + 1, emotion: "comfort", action: "cuddle", attachments: [], toolCalls: [],
            };
            await saveMessage(careMsg);
            emit("chat-stream", { personaId, type: "proactive", content: care });
          }
        }
      } catch { /* ignore */ }
    })();

    // 会话记忆上限 → 新会话继承（对齐 SessionManager.checkMemoryLimit）
    (async () => {
      try {
        if (await checkMemoryLimit(personaId)) {
          await startNewSession(personaId);
          await getInheritedMemory(personaId);
        }
      } catch { /* ignore */ }
    })();

    // 成就/里程碑/统计联动
    (async () => {
      try {
        const stats = await getStats(personaId);
        const days = await getChatDays(personaId);
        if (stats) {
          const unlocked = await checkAndUnlock(personaId, {
            totalMessages: stats.totalMessages,
            chatDays: days,
            affection: affection?.level,
          });
          for (const u of unlocked.filter((x) => x.unlocked)) emit("achievement-unlocked", u);
          await checkAutomaticMilestones(personaId, { totalMessages: stats.totalMessages, chatDays: days });
        }
      } catch { /* ignore */ }
    })();

    // TTS 自动朗读（isTTSEnabled/voice.autoPlay 双开关）
    if (isTTSEnabled() && settings.voice?.autoPlay !== false && shown) {
      speak(stripForTts(shown), { emotion }).catch(() => undefined);
    }
  }

  // 8) 预测用户回复（对齐 ChatPredictor 消费点）
  let suggestions: string[] = [];
  if (!opts.silent) {
    try {
      suggestions = await predictReplies(personaId, [...history.slice(-6).map((h) => ({ role: h.role, content: h.content })), { role: "assistant", content: shown || cleanText }]);
    } catch { /* ignore */ }
  }

  return { message: petMsg, emotion, action, suggestions, blocked: false, toolEffects: effects };
}

/** 生成静默纯文本回复（iLink 微信/群聊/圈评复用；不写主聊天，silent 链路） */
export async function generateSilentReply(personaId: string, userText: string, extraSystem?: string, attachments?: string[]): Promise<string> {
  const r = await sendTurn(personaId, userText, { silent: true, personaOverrideId: personaId, extraSystem, attachments });
  return r.message.content;
}

function stripForTts(text: string): string {
  return text.replace(/\[\[emotion:\w+\]\]/gi, "").replace(/【.*?】/g, "").trim();
}

// ─── 重新生成与变体切换（Operit 式消息分支的最小可用实现） ───

/**
 * 为某条 AI 消息重新生成一个候选回复：
 * 取其前面最近一条用户消息为输入，走 silent 链路（不重复计好感/落库），
 * 结果追加为该消息的 variant 并切换显示。
 */
export async function regenerateVariant(personaId: string, messageId: string): Promise<EngineMessage | null> {
  const history = await loadHistory(personaId);
  const idx = history.findIndex((m) => m.id === messageId);
  if (idx < 0) return null;
  const target = history[idx];
  if (target.role !== "assistant") return null;
  let userText = "";
  for (let i = idx - 1; i >= 0; i--) {
    if (history[i].role === "user") { userText = history[i].content; break; }
  }
  if (!userText) return null;

  const r = await sendTurn(personaId, userText, { silent: true });
  const variants = [...(target.variants ?? [{ content: target.content, emotion: target.emotion, action: target.action, createdAt: target.timestamp }]),
    { content: r.message.content, emotion: r.emotion, action: r.action, createdAt: Date.now() }];
  const updated: EngineMessage = { ...target, variants, variantIdx: variants.length - 1, content: r.message.content, emotion: r.emotion, action: r.action };
  await saveMessage(updated);
  emit("data-changed", { collection: "messages", messageId });
  return updated;
}

/** 切换某条 AI 消息显示的变体 */
export async function switchVariant(personaId: string, messageId: string, variantIdx: number): Promise<EngineMessage | null> {
  const history = await loadHistory(personaId);
  const target = history.find((m) => m.id === messageId);
  if (!target?.variants?.length) return null;
  const i = Math.max(0, Math.min(variantIdx, target.variants.length - 1));
  const v = target.variants[i];
  const updated: EngineMessage = { ...target, variantIdx: i, content: v.content, emotion: v.emotion, action: v.action };
  await saveMessage(updated);
  emit("data-changed", { collection: "messages", messageId });
  return updated;
}

// ─── 消息管理 API（收藏/删除/清空，对齐 FavoriteManager / ChatHistoryStorage） ───

export async function deleteMessageById(messageId: string): Promise<void> {
  await dbRemove("messages", messageId);
  await dbRemove("favorites", messageId);
}

export async function toggleFavorite(personaId: string, messageId: string): Promise<boolean> {
  const all = await getByPersona<{ id: string; content: string; role: string; emotion?: Emotion; timestamp: number }>("favorites", personaId);
  const exists = all.find((f) => f.id === messageId);
  if (exists) {
    await dbRemove("favorites", messageId);
    return false;
  }
  const msg = await getByPersona<EngineMessage>("messages", personaId).then((ms) => ms.find((m) => m.id === messageId));
  if (!msg) return false;
  await put("favorites", {
    id: messageId, personaId, messageId, role: msg.role, content: msg.content, emotion: msg.emotion, createdAt: Date.now(),
  } as unknown as { id: string });
  // 同步 messages 上的 favorited 标记
  await put("messages", { ...msg, favorited: true } as unknown as { id: string });
  return true;
}

export async function listFavorites(personaId?: string): Promise<unknown[]> {
  return personaId ? getByPersona("favorites", personaId) : getAll("favorites");
}

export async function clearHistory(personaId: string): Promise<void> {
  const msgs = await getByPersona<EngineMessage>("messages", personaId);
  for (const m of msgs) await dbRemove("messages", m.id);
}
