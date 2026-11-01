/**
 * 群聊引擎 —— 移植自 Android groupchat/GroupChatManager.kt + GroupChatActivity.triggerAiResponses
 * 说话模式 auto/manual/ai_judge、@提及解析与链式触发（depth≤3）、角色名前缀剥离全部保留。
 */

import { getAll, getByPersona, put, remove as dbRemove, newId } from "@/lib/db";
import { emit } from "@/lib/events";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { GroupRecord, GroupMessageRecord } from "./kinds";
import { buildIdentityById, buildSilentCheckPrompt, buildPersonaBase } from "./promptBuilder";
import { simpleCompletion, extractEmotionAction } from "./llmClient";
import { sendTurn } from "./chatEngine";

export type SpeakMode = "auto" | "manual" | "ai_judge";

// ─── 群组 CRUD ───

export async function listGroups(): Promise<GroupRecord[]> {
  const all = await getAll<GroupRecord>("groupChats");
  return all.sort((a, b) => b.updatedAt - a.updatedAt);
}

export async function getGroup(groupId: string): Promise<GroupRecord | null> {
  const all = await getAll<GroupRecord>("groupChats");
  return all.find((g) => g.id === groupId) ?? null;
}

export async function createGroup(name: string, memberIds: string[], worldId?: string): Promise<GroupRecord> {
  const record: GroupRecord = {
    id: newId("group"), name, memberIds, messages: [],
    createdAt: Date.now(), updatedAt: Date.now(), worldId,
  };
  await put("groupChats", record);
  emit("data-changed", { collection: "groupChats" });
  return record;
}

export async function deleteGroup(groupId: string): Promise<void> {
  await dbRemove("groupChats", groupId);
  emit("data-changed", { collection: "groupChats" });
}

export async function updateGroup(group: GroupRecord): Promise<void> {
  await put("groupChats", { ...group, updatedAt: Date.now() });
}

export async function getGroupMessages(groupId: string): Promise<GroupMessageRecord[]> {
  const g = await getGroup(groupId);
  return g?.messages ?? [];
}

// ─── @提及解析（对齐 parseMentions / parseAiMentions） ───

function parseMentions(text: string, group: GroupRecord): Set<string> {
  const out = new Set<string>();
  for (const pid of group.memberIds) {
    const name = usePersonaStore.getState().personas.find((p) => p.id === pid)?.name;
    if (name && text.includes(`@${name}`)) out.add(pid);
  }
  return out;
}

function stripNamePrefix(text: string, name: string, otherNames: string[]): string {
  let t = text.trim();
  const prefixes = [name, ...otherNames];
  for (const p of prefixes) {
    for (const sep of ["：", ":", "", "  "]) {
      const head = `${p}${sep}`;
      if (t.startsWith(head)) { t = t.slice(head.length).trim(); break; }
    }
  }
  return t;
}

async function appendMessage(groupId: string, msg: GroupMessageRecord): Promise<void> {
  const g = await getGroup(groupId);
  if (!g) return;
  await updateGroup({ ...g, messages: [...g.messages, msg] });
  emit("data-changed", { collection: "groupChats", groupId });
}

/** AI 是否该说话（ai_judge 模式，对齐 shouldPersonaSpeak + buildSilentCheckPrompt） */
async function shouldPersonaSpeak(personaId: string, group: GroupRecord, userText: string): Promise<boolean> {
  const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
  if (!persona) return false;
  const recentContext = group.messages.slice(-10)
    .map((m) => (m.senderId === "user" ? `[用户说] ${m.content}` : `[${m.senderName}说] ${m.content}`))
    .join("\n");
  const identity = buildIdentityById(personaId);
  const prompt = buildSilentCheckPrompt(identity, userText, recentContext);
  try {
    const text = (await simpleCompletion(prompt, { system: "只回「说话」或「沉默」" })).trim().toLowerCase();
    return text.includes("说话") || text.includes("speak") || text.includes("yes");
  } catch {
    return false;
  }
}

/**
 * 用户在群里发言 → 触发 AI 成员回复（含链式 @）。
 * @param speakMode auto=全员回复 / manual=按 manualIds / ai_judge=LLM 判断
 */
export async function sendGroupMessage(
  groupId: string,
  userText: string,
  opts: { speakMode?: SpeakMode; manualIds?: string[]; personaOverrideId?: string } = {},
): Promise<void> {
  const group = await getGroup(groupId);
  if (!group) throw new Error("群聊不存在");
  const userName = getUserName();

  await appendMessage(groupId, {
    id: newId("gmsg"), groupId, senderId: "user", senderName: userName,
    content: userText, timestamp: Date.now(),
  });

  const speakMode = opts.speakMode ?? "auto";
  await triggerAiResponses(groupId, userText, speakMode, opts.manualIds ?? [], new Set<string>(), 0, opts.personaOverrideId);
}

async function triggerAiResponses(
  groupId: string,
  triggerText: string,
  speakMode: SpeakMode,
  manualIds: string[],
  chainMentions: Set<string>,
  depth: number,
  fallbackPersonaId?: string,
): Promise<void> {
  if (depth > 3) return;
  const group = await getGroup(groupId);
  if (!group) return;
  const mentionedIds = parseMentions(triggerText, group);

  const targetIds = chainMentions.size > 0
    ? [...chainMentions]
    : speakMode === "manual"
      ? (mentionedIds.size ? [...mentionedIds] : manualIds)
      : mentionedIds.size ? [...mentionedIds] : group.memberIds;

  const chainQueue: string[] = [];
  for (const personaId of targetIds) {
    const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
    if (!persona) continue;
    const isMentioned = mentionedIds.has(personaId) || chainMentions.has(personaId);
    if (!isMentioned && speakMode === "ai_judge") {
      const ok = await shouldPersonaSpeak(personaId, group, triggerText);
      if (!ok) continue;
    }

    // 群聊叙事上下文（对齐 buildNarrativeHistory）
    const otherIds = group.memberIds.filter((x) => x !== personaId);
    const otherNames = otherIds
      .map((x) => usePersonaStore.getState().personas.find((p) => p.id === x)?.name)
      .filter((x): x is string => !!x);
    const narrative = group.messages.slice(-20).map((m) =>
      m.senderId === "user" ? `[用户说] ${m.content}` : m.senderId === personaId ? `[你之前说] ${m.content}` : `[${m.senderName}说] ${m.content}`,
    ).join("\n");
    const extraSystem = [
      `【群聊「${group.name}」】成员：${otherNames.join("、")}、用户。`,
      isMentioned ? "用户直接@了你，你必须回应。" : "",
      "你可以用 @成员名 来点名其他角色接话（系统会自动触发其回复，可自然沉默输出「……」表示不说话）。",
      narrative ? `\n【群聊历史】\n${narrative}` : "",
    ].filter(Boolean).join("\n");

    let response: string | null = null;
    try {
      const r = await sendTurn(personaId, triggerText, { silent: true, personaOverrideId: personaId, extraSystem }, {});
      response = r.message.content;
    } catch {
      try {
        const identity = buildIdentityById(personaId);
        const text = await simpleCompletion(
          `${buildPersonaBase(identity)}\n\n${extraSystem}\n\n用户说：「${triggerText}」\n请以你的身份简短回应（1-2句），末尾可加[[emotion:xxx]]。`,
          { system: persona.name },
        );
        response = text;
      } catch { response = null; }
    }
    if (!response) continue;

    const { cleanText, emotion } = extractEmotionAction(response);
    let clean = stripNamePrefix(cleanText, persona.name, otherNames);
    const aiMentions = parseMentions(clean, group);
    clean = clean.replace(/@[^\s@]+\s?/g, "").trim();
    if (!clean || clean === "..." || clean === "沉默") continue;

    await appendMessage(groupId, {
      id: newId("gmsg"), groupId, senderId: personaId, senderName: persona.name,
      content: clean, emotion: emotion as GroupMessageRecord["emotion"], timestamp: Date.now(),
    });
    chainQueue.push(...[...aiMentions].filter((x) => !chainMentions.has(x)));
  }

  // 链式回复（对齐 Kotlin depth+1 递归）
  for (const nextId of chainQueue) {
    if (depth + 1 > 3) break;
    await triggerAiResponses(groupId, triggerText, speakMode, manualIds, new Set([nextId]), depth + 1, fallbackPersonaId);
  }
}

function getUserName(): string {
  return useSettingsStore.getState().settings.userProfile?.nickname?.trim() || "我";
}
