/**
 * 昵称引擎 —— 移植自 ui/NicknameManager.kt + plugin/BuiltinPlugins.kt(NicknamePlugin)
 *           + ui/ChatViewModel.kt(buildNicknameContext)
 * 复刻规则：
 * - 手动昵称优先：setManualNickname 非空时 getActiveNicknames 只返回 [manual]；
 *   否则取发现的昵称去重后前 5 个（Kotlin take(5)）；
 * - 发现列表按时间倒序、保存上限 20（saveAll take(20)）、批量添加去重（addDiscoveredBatch）；
 * - summarizeNicknames 的提示词语义照抄 NicknamePlugin.getDefinition()
 *   （"根据和主人的聊天对话，为主人总结出适合的称呼/昵称列表……每个称呼建议应简洁自然……"），
 *   Kotlin 走 function-calling 提交 nicknames 数组，PC 端用 simpleCompletion 要求输出 JSON 数组等价实现；
 * - 无称呼且消息数 ≥4 时的工具引导提示见 buildNicknameHint（对齐 ChatViewModel.buildNicknameContext）。
 */

import { getByPersona, newId, put, remove } from "@/lib/db";
import type { NicknameRecord } from "./kinds";
import { simpleCompletion } from "./llmClient";

/** 手动昵称标记 source="user"（契约里 source: "llm" | "user"） */

/** 当前生效昵称：手动优先，其次最近发现的一个；都没有返回 null */
export async function getActiveNickname(personaId: string): Promise<string | null> {
  const all = await listNicknames(personaId);
  const manual = all.find((n) => n.source === "user" && n.active);
  if (manual) return manual.nickname;
  const discovered = all.filter((n) => n.source === "llm").sort((a, b) => b.createdAt - a.createdAt);
  return discovered[0]?.nickname ?? null;
}

/** 活跃昵称列表（Kotlin getActiveNicknames：手动→单个；否则去重前5） */
export async function getActiveNicknames(personaId: string): Promise<string[]> {
  const all = await listNicknames(personaId);
  const manual = all.find((n) => n.source === "user" && n.active);
  if (manual) return [manual.nickname];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const n of all.sort((a, b) => b.createdAt - a.createdAt)) {
    if (seen.has(n.nickname)) continue;
    seen.add(n.nickname);
    out.push(n.nickname);
    if (out.length >= 5) break;
  }
  return out;
}

/** 设置/清除手动昵称（空串=清除，对齐 setManualNickname("")）；非空时同时进入发现列表并置为唯一 active */
export async function setNickname(personaId: string, name: string): Promise<void> {
  const all = await listNicknames(personaId);
  const trimmed = name.trim();
  // 取消所有旧的 user 记录（Kotlin manual_nickname 单值语义）
  for (const n of all.filter((x) => x.source === "user")) await remove("nicknames", n.id);
  if (!trimmed) return;
  // addDiscovered(nickname, "manual")：非空时加入发现列表
  const exists = all.find((x) => x.nickname === trimmed);
  if (exists) {
    await put("nicknames", { ...exists, active: true });
  }
  await put("nicknames", {
    id: newId("nick"),
    personaId,
    nickname: trimmed,
    source: "user",
    createdAt: Date.now(),
    active: true,
  } satisfies NicknameRecord);
}

/** LLM 总结用户昵称候选（提示词照抄 NicknamePlugin 定义语义）；失败返回 [] */
export async function summarizeNicknames(personaId: string, recentConversation: string[]): Promise<string[]> {
  const texts = recentConversation.filter(Boolean).slice(-60);
  if (!texts.length) return [];
  let raw = "";
  try {
    raw = await simpleCompletion(
      [
        "根据以下和主人的聊天对话，为主人总结出适合的称呼/昵称列表。",
        "可以通过聊天中观察到的信息（如名字、身份、习惯、性格等）为主人生成多个可选的称呼。",
        "每个称呼建议应简洁自然，可以基于聊天中提到的名字、身份特征或亲密关系来创造。",
        "输出纯JSON字符串数组，不要markdown代码块，例如：[\"小星\",\"阿澈\"]",
        "",
        "聊天记录：",
        texts.join("\n"),
      ].join("\n"),
      { temperature: 0.7, maxTokens: 200 },
    );
  } catch {
    return [];
  }
  const names = parseNicknameArray(raw);
  if (!names.length) return [];
  await addDiscoveredBatch(personaId, names);
  return names;
}

/** 宽容解析 JSON 字符串数组 */
function parseNicknameArray(text: string): string[] {
  try {
    let clean = text.trim().replace(/^```\s*(json)?\s*/i, "").replace(/```$/, "").trim();
    const s = clean.indexOf("[");
    const e = clean.lastIndexOf("]");
    if (s < 0 || e <= s) return [];
    clean = clean.slice(s, e + 1);
    const arr = JSON.parse(clean) as unknown[];
    return arr.map((x) => String(x).trim()).filter(Boolean).slice(0, 10);
  } catch {
    return [];
  }
}

/** 批量添加发现的昵称（Kotlin addDiscoveredBatch：跳过空白与已存在） */
export async function addDiscoveredBatch(personaId: string, nicknames: string[], source: "llm" | "user" = "llm"): Promise<void> {
  const all = await listNicknames(personaId);
  const existing = new Set(all.map((n) => n.nickname));
  for (const name of nicknames) {
    const t = name.trim();
    if (!t || existing.has(t)) continue;
    existing.add(t);
    await put("nicknames", {
      id: newId("nick"),
      personaId,
      nickname: t,
      source,
      createdAt: Date.now(),
      active: false,
    } satisfies NicknameRecord);
  }
  // 保存上限 20（Kotlin saveAll entries.take(20)）：裁掉最旧的非手动记录
  const after = await listNicknames(personaId);
  const llm = after.filter((n) => n.source === "llm").sort((a, b) => b.createdAt - a.createdAt);
  for (const drop of llm.slice(20)) await remove("nicknames", drop.id);
}

/** 列出全部昵称（新→旧） */
export async function listNicknames(personaId: string): Promise<NicknameRecord[]> {
  const all = await getByPersona<NicknameRecord>("nicknames", personaId);
  return all.sort((a, b) => b.createdAt - a.createdAt);
}

/** 删除一条昵称（按记录 id） */
export async function deleteNickname(id: string): Promise<void> {
  await remove("nicknames", id);
}

/**
 * 昵称上下文提示（对齐 ChatViewModel.buildNicknameContext）：
 * 用户尚无称呼且消息数 ≥4 时，提示 AI 可调用 summarize_nicknames 建议称呼。
 */
export async function buildNicknameHint(personaId: string, messageCount: number): Promise<string> {
  const active = await getActiveNicknames(personaId);
  if (active.length > 0) return "";
  if (messageCount >= 4) {
    return "\n\n【提示】你还没有给用户设定称呼。如果你觉得通过聊天已经对用户有了一定了解,可以调用 summarize_nicknames 工具为主人总结出几个合适的称呼。";
  }
  return "";
}
