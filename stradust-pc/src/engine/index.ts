/**
 * 引擎启动器 —— 所有调度器/回包注入的统一初始化入口。
 * App 挂载时调用 initEngines()；卸载时 disposeEngines()。
 */

import { emit } from "@/lib/events";
import { useChatStore } from "@/stores/useChatStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { newId } from "@/lib/db";
import type { EngineMessage } from "./kinds";
import { sendTurn, saveMessage } from "./chatEngine";
import { buildIdentityById, buildWakeUpPrompt } from "./promptBuilder";
import { simpleCompletion } from "./llmClient";
import { setReplyGenerator as setIlinkReply, start as startIlink, stop as stopIlink } from "./ilink";
import { setProactiveGenerator, startProactiveEngine, stopProactiveEngine } from "./proactive";
import { setAlarmHandler, startAlarmScheduler, stopAlarmScheduler, startWakeScheduler, stopWakeScheduler, startScheduleScheduler, stopScheduleScheduler, notify } from "./wakeup";
import { startDiaryScheduler, stopDiaryScheduler } from "./diary";
import { startMomentsHeartbeat, stopMomentsHeartbeat } from "./momentsFeed";
import { startWorldAutoTick, stopWorldAutoTick } from "./worldEngine";
import { speak, isTTSEnabled } from "./voice";
import { mapEmotion } from "./emotion";

let initialized = false;

async function putChatMessage(personaId: string, content: string, emotionStr: string, meta: Record<string, unknown>): Promise<void> {
  const emotion = mapEmotion(emotionStr);
  const msg: EngineMessage = {
    id: newId("msg"), personaId, role: "assistant", content,
    timestamp: Date.now(), emotion, action: "idle", attachments: [], toolCalls: [], meta,
  };
  await saveMessage(msg);
  emit("chat-stream", { personaId, type: "proactive", content });
}

function currentPersonaId(): string | null {
  const cur = useChatStore.getState().currentPersonaId;
  if (cur) return cur;
  const personas = usePersonaStore.getState().personas;
  return personas[0]?.id ?? null;
}

/** iLink 微信专属规则（对齐 IlinkPollingService.generateAiReply 的 emoji 规则） */
const ILINK_EXTRA_SYSTEM = [
  "【微信聊天场景】用户通过微信给你发消息。",
  "- 回复要短，像微信聊天，1-3句",
  "- 可以使用 emoji 表情增加亲切感",
  "- 不要用 markdown 格式",
].join("\n");

export function initEngines(): void {
  if (initialized || typeof window === "undefined") return;
  initialized = true;

  // 1) iLink 自动回复生成器
  setIlinkReply(async (userText, _convId) => {
    const personaId = currentPersonaId();
    if (!personaId) return "（还没设置聊天角色哦）";
    const r = await sendTurn(personaId, userText, { silent: true, extraSystem: ILINK_EXTRA_SYSTEM });
    return r.message.content;
  });
  (async () => {
    const { getBinding } = await import("./ilink");
    const b = await getBinding();
    if (b?.bound) startIlink();
  })();

  // 2) 主动搭话生成器：LLM 按 hint 生成并写入聊天
  setProactiveGenerator(async (personaId, hint) => {
    const identity = buildIdentityById(personaId);
    const prompt = buildWakeUpPrompt(identity, hint);
    try {
      const text = await simpleCompletion(prompt, { system: "主动搭话" });
      const { cleanText, emotion, action } = await import("./llmClient").then(({ extractEmotionAction }) => extractEmotionAction(text));
      const msg: EngineMessage = {
        id: newId("msg"), personaId, role: "assistant",
        content: cleanText || text, timestamp: Date.now(),
        emotion: mapEmotion(emotion), action: action as EngineMessage["action"],
        attachments: [], toolCalls: [], meta: { proactive: true, hint },
      };
      await saveMessage(msg);
      emit("chat-stream", { personaId, type: "proactive", content: msg.content });
      const s = useSettingsStore.getState().settings;
      if (isTTSEnabled() && s.voice?.autoPlay !== false) speak(msg.content, { emotion: mapEmotion(emotion) }).catch(() => undefined);
      return msg.content;
    } catch {
      return "在忙什么呢？突然有点想你了。";
    }
  });
  startProactiveEngine(currentPersonaId);

  // 3) 闹钟/唤醒到点处理
  setAlarmHandler(async (alarm) => {
    await notify("⏰ 提醒", alarm.label);
    const personaId = currentPersonaId();
    if (personaId) {
      await putChatMessage(personaId, `⏰ 提醒时间到啦：${alarm.label}`, "excited", { alarm: alarm.id });
      speak(`提醒时间到啦：${alarm.label}`).catch(() => undefined);
    }
  });
  startAlarmScheduler();
  startWakeScheduler();
  startScheduleScheduler();
  window.addEventListener("wake-fired", ((e: CustomEvent<{ name?: string; promptHint?: string }>) => {
    const personaId = currentPersonaId();
    if (!personaId) return;
    const hint = e.detail?.promptHint || e.detail?.name || "新的一天开始";
    emit("wake-fired", e.detail);
    notify("🌤 早安提醒", hint).catch(() => undefined);
    sendTurn(personaId, hint, { silent: true }).then((r) => {
      putChatMessage(personaId, r.message.content, String(r.emotion), { wake: true });
    }).catch(() => undefined);
  }) as EventListener);

  // 4) 生活节奏调度器
  startDiaryScheduler(currentPersonaId);
  startMomentsHeartbeat(currentPersonaId);
  startWorldAutoTick();
}

export function disposeEngines(): void {
  if (!initialized) return;
  initialized = false;
  stopIlink();
  stopProactiveEngine();
  stopAlarmScheduler();
  stopWakeScheduler();
  stopScheduleScheduler();
  stopDiaryScheduler();
  stopMomentsHeartbeat();
  stopWorldAutoTick();
}
