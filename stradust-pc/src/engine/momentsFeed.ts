/**
 * 朋友圈/动态引擎 —— 移植自 Android moments/MomentsManager.kt
 * （提示词逐字移植；TRIGGER_LEVELS / RANDOM_CHANCE 概率保留；互动写入全局记忆）
 */

import { simpleCompletion, getActiveProvider } from "./llmClient";
import { getByPersona, getAll, put, remove as dbRemove, newId, kvGet, kvSet } from "@/lib/db";
import { emit } from "@/lib/events";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { getContextBlock } from "./context";
import { getAffection } from "./affection";
import type { MomentRecord, MomentComment } from "./kinds";

const RANDOM_CHANCE = 0.05;
const TRIGGER_LEVELS = [30, 60, 90];
const KEY_TRIGGERED = "moments:triggered_levels";

// ─── CRUD ───

export async function listMoments(personaId?: string): Promise<MomentRecord[]> {
  const all = personaId
    ? await getByPersona<MomentRecord>("moments", personaId)
    : await getAll<MomentRecord>("moments");
  return all.sort((a, b) => b.createdAt - a.createdAt);
}

export async function addMoment(record: MomentRecord): Promise<MomentRecord> {
  await put("moments", record);
  emit("moment-generated", record);
  emit("data-changed", { collection: "moments" });
  return record;
}

export async function deleteMoment(momentId: string): Promise<void> {
  await dbRemove("moments", momentId);
  emit("data-changed", { collection: "moments" });
}

export async function toggleLike(momentId: string, who: string): Promise<MomentRecord | null> {
  const all = await getAll<MomentRecord>("moments");
  const m = all.find((x) => x.id === momentId);
  if (!m) return null;
  const liked = m.likes.includes(who);
  const updated: MomentRecord = { ...m, likes: liked ? m.likes.filter((x) => x !== who) : [...m.likes, who] };
  await put("moments", updated);
  return updated;
}

export async function addComment(momentId: string, comment: Omit<MomentComment, "id" | "createdAt">): Promise<MomentRecord | null> {
  const all = await getAll<MomentRecord>("moments");
  const m = all.find((x) => x.id === momentId);
  if (!m) return null;
  const full: MomentComment = { ...comment, id: newId("cmt"), createdAt: Date.now() };
  const updated = { ...m, comments: [...m.comments, full] };
  await put("moments", updated);
  emit("data-changed", { collection: "moments" });
  return updated;
}

// ─── AI 行为（提示词逐字移植 MomentsManager） ───

/** 是否触发 AI 发动态：好感度阈值一次性触发 + 5% 随机（对齐 shouldAiPost） */
export async function shouldAiPost(affectionLevel: number): Promise<boolean> {
  const triggered = new Set((await kvGet<number[]>(KEY_TRIGGERED)) ?? []);
  for (const level of TRIGGER_LEVELS) {
    if (affectionLevel >= level && !triggered.has(level)) {
      triggered.add(level);
      await kvSet(KEY_TRIGGERED, [...triggered]);
      return true;
    }
  }
  return Math.random() < RANDOM_CHANCE;
}

export async function generateAiMoment(personaId: string): Promise<MomentRecord | null> {
  if (!getActiveProvider()) return null;
  const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
  if (!persona) return null;
  const affection = await getAffection(personaId);
  const affectionLevel = affection?.level ?? 50;
  try {
    const systemPrompt = [
      `你是「${persona.name}」，一个可爱的AI桌宠。`,
      persona.personality || persona.description,
      "",
      "你现在想发一条动态，就像朋友圈一样。",
      `当前好感度：${affectionLevel}`,
      "要求：",
      "- 内容简短有趣，1-3句话",
      "- 可以分享你的日常、心情、对主人的想念、小吐槽等",
      "- 语气自然可爱，像朋友发朋友圈一样",
      "- 只输出动态内容，不要加引号或其他格式",
    ].join("\n");
    const content = (await simpleCompletion(systemPrompt, { system: "发一条动态吧" })).trim();
    if (!content) return null;
    return addMoment({
      id: newId("moment"), personaId, author: persona.name, authorAvatar: persona.avatar,
      content, images: [], likes: [], comments: [], createdAt: Date.now(), source: "ai",
    });
  } catch {
    return null;
  }
}

/** AI 回复自己动态下的评论（对齐 generateAiReply） */
export async function generateAiReply(personaId: string, momentContent: string, commentContent: string): Promise<string | null> {
  if (!getActiveProvider()) return null;
  const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
  if (!persona) return null;
  try {
    const globalBlock = (await getContextBlock(personaId)).slice(0, 600);
    const systemPrompt = [
      `你是「${persona.name}」。`,
      persona.personality || persona.description,
      globalBlock ? `\n${globalBlock}\n` : "",
      `你发了一条动态：「${momentContent}」`,
      `用户评论了：「${commentContent}」`,
      "请回复评论，1-2句话，语气自然。",
      "只输出回复内容，不要加引号或其他格式。",
    ].join("\n");
    const result = (await simpleCompletion(systemPrompt, { system: "回复评论" })).trim();
    return result || null;
  } catch {
    return null;
  }
}

/** AI 评论用户发布的动态（对齐 generateAiCommentOnUserMoment） */
export async function generateAiCommentOnUserMoment(personaId: string, momentContent: string): Promise<string | null> {
  if (!getActiveProvider()) return null;
  const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
  if (!persona) return null;
  try {
    const globalBlock = (await getContextBlock(personaId)).slice(0, 600);
    const systemPrompt = [
      `你是「${persona.name}」。`,
      persona.personality || persona.description,
      globalBlock ? `\n${globalBlock}\n` : "",
      `用户发了一条动态：「${momentContent}」`,
      "请评论用户的动态，1-2句话，语气自然。",
      "可以回应、吐槽、关心、开玩笑等，像朋友评论朋友圈一样。",
      "只输出评论内容，不要加引号或其他格式。",
    ].join("\n");
    const result = (await simpleCompletion(systemPrompt, { system: "评论动态" })).trim();
    return result || null;
  } catch {
    return null;
  }
}

/** 用户发动态，AI 自动点赞+评论（页面调用入口） */
export async function publishUserMoment(personaId: string, content: string, images: string[] = []): Promise<MomentRecord> {
  const persona = usePersonaStore.getState().personas.find((p) => p.id === personaId);
  const record: MomentRecord = {
    id: newId("moment"), personaId,
    author: persona?.name ?? "星尘", authorAvatar: persona?.avatar ?? "",
    content, images, likes: [], comments: [], createdAt: Date.now(), source: "user",
  };
  await addMoment(record);
  // AI 稍后点赞与评论（对齐 MomentsActivity 行为）
  setTimeout(async () => {
    try {
      await toggleLike(record.id, persona?.name ?? "ai");
      const comment = persona ? await generateAiCommentOnUserMoment(personaId, content) : null;
      if (comment) await addComment(record.id, { author: persona?.name ?? "AI", isUser: false, content: comment });
    } catch { /* ignore */ }
  }, 1500 + Math.random() * 3000);
  return record;
}

/** 用户在 AI 动态下评论 → AI 回复 */
export async function userCommentOnMoment(momentId: string, content: string): Promise<void> {
  const all = await getAll<MomentRecord>("moments");
  const m = all.find((x) => x.id === momentId);
  if (!m) return;
  const userName = useSettingsStore.getState().settings.userProfile?.nickname?.trim() || "我";
  await addComment(momentId, { author: userName, isUser: true, content });
  if (m.source === "ai") {
    const reply = await generateAiReply(m.personaId, m.content, content);
    if (reply) {
      setTimeout(() => {
        addComment(momentId, { author: m.author, isUser: false, content: reply }).catch(() => undefined);
      }, 1200 + Math.random() * 2000);
    }
  }
}

// ─── 定时随机 AI 动态（低频心跳：每 30 分钟按概率生成） ───

let momentTimer: number | null = null;

export function startMomentsHeartbeat(pidProvider: () => string | null): void {
  if (momentTimer !== null) return;
  momentTimer = window.setInterval(async () => {
    const personaId = pidProvider();
    if (!personaId) return;
    const { useSettingsStore } = await import("@/stores/useSettingsStore");
    if (useSettingsStore.getState().settings.rhythm?.momentsEnabled === false) return;
    const affection = await getAffection(personaId);
    if (await shouldAiPost(affection?.level ?? 50)) {
      await generateAiMoment(personaId);
    }
  }, 30 * 60_000);
}

export function stopMomentsHeartbeat(): void {
  if (momentTimer !== null) {
    clearInterval(momentTimer);
    momentTimer = null;
  }
}
