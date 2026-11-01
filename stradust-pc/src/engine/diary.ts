/**
 * 日记引擎 —— 移植自 Android diary/DiaryManager.kt
 * （保留：analyzeMood 词表 / summarizeChatTexts / LLM 日记总结提示词 / 每日一句文案池 /
 *   30 分钟更新间隔 / 触发模式调度。去掉：日记图谱索引与向量 RAG——用关键词检索替代）
 */

import { simpleCompletion, getActiveProvider } from "./llmClient";
import { buildIdentityById } from "./promptBuilder";
import { getAll, getByPersona, put, remove as dbRemove, newId, kvGet, kvSet } from "@/lib/db";
import { emit } from "@/lib/events";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { DiaryRecord, EngineMessage } from "./kinds";

const MIN_UPDATE_INTERVAL_MS = 30 * 60 * 1000;
const KEY_LAST_UPDATE = "diary:last_update_ms";

function fmtDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
function fmtDateFull(d: Date): string {
  const wd = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][d.getDay()];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${wd}`;
}

// ─── CRUD ───

export async function getDiaryList(personaId: string): Promise<DiaryRecord[]> {
  const all = await getByPersona<DiaryRecord>("diaries", personaId);
  return all.sort((a, b) => b.date.localeCompare(a.date));
}

export async function getTodayDiary(personaId: string): Promise<DiaryRecord | null> {
  const list = await getDiaryList(personaId);
  return list.find((d) => d.date === fmtDate(new Date())) ?? null;
}

export async function getDiaryByDate(personaId: string, date: string): Promise<DiaryRecord | null> {
  const list = await getDiaryList(personaId);
  return list.find((d) => d.date === date) ?? null;
}

export async function deleteDiaryById(id: string): Promise<void> {
  await dbRemove("diaries", id);
}

export async function updateDiaryContent(id: string, content: string): Promise<DiaryRecord | null> {
  const all = await getAll<DiaryRecord>("diaries");
  const entry = all.find((d) => d.id === id);
  if (!entry) return null;
  const updated = { ...entry, content, updatedAt: Date.now() };
  await put("diaries", updated);
  return updated;
}

/** 用户手写日记（对齐 saveUserDiary） */
export async function saveUserDiary(personaId: string, date: string, title: string, content: string): Promise<DiaryRecord> {
  const mood = analyzeMood(content);
  const record: DiaryRecord = {
    id: newId("diary"), personaId, date,
    title: title || titleForMood(mood),
    content, mood, moodEmoji: moodEmojiFor(mood),
    createdAt: Date.now(), updatedAt: Date.now(),
  };
  await put("diaries", record);
  emit("data-changed", { collection: "diaries" });
  return record;
}

/** 关键词搜索（对齐 searchDiaries；向量 RAG 的 Web 替代） */
export async function searchDiaries(personaId: string, query: string, topK = 3): Promise<DiaryRecord[]> {
  const list = await getDiaryList(personaId);
  if (!query) return list.slice(0, topK);
  const q = query.toLowerCase();
  const scored = list
    .map((d) => {
      const text = `${d.title} ${d.content}`.toLowerCase();
      let score = 0;
      for (const ch of q) if (text.includes(ch)) score++;
      if (text.includes(q)) score += 10;
      return { d, score };
    })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score);
  return scored.slice(0, topK).map((x) => x.d);
}

export function getDiariesByMood(list: DiaryRecord[], mood: string): DiaryRecord[] {
  return list.filter((d) => d.mood === mood);
}

// ─── 生成（对齐 generateDailyDiary / saveLlmDiary / appendLlmDiaryUpdate） ───

export function canUpdateDiary(): boolean {
  return true; // 与 Kotlin 一致：间隔判断在 markDiaryUpdated 之后生效
}

async function markDiaryUpdated(): Promise<void> {
  await kvSet(KEY_LAST_UPDATE, Date.now());
}

/** 采集今日聊天文本 */
async function collectTodayTexts(personaId: string): Promise<string[]> {
  const dayStart = new Date();
  dayStart.setHours(0, 0, 0, 0);
  const msgs = await getByPersona<EngineMessage>("messages", personaId);
  return msgs
    .filter((m) => m.timestamp >= dayStart.getTime() && m.content.trim())
    .sort((a, b) => a.timestamp - b.timestamp)
    .map((m) => `${m.role === "user" ? "主人" : "我"}: ${m.content}`);
}

/**
 * 生成/追加今日日记。已有当日日记且有 LLM 时执行"追加总结"（受 30 分钟间隔限制）。
 * @returns 生成的日记，或 null（无内容/间隔未到）
 */
export async function generateDailyDiary(personaId: string, opts: { force?: boolean } = {}): Promise<DiaryRecord | null> {
  const today = fmtDate(new Date());
  const chatTexts = await collectTodayTexts(personaId);
  if (chatTexts.length === 0 && !opts.force) return null;

  const existing = await getDiaryByDate(personaId, today);
  const affection = useSettingsStore.getState().settings;
  void affection;
  const identity = buildIdentityById(personaId);

  // LLM 总结（配置了模型才走）
  let llmSummary: string | null = null;
  if (getActiveProvider() && chatTexts.length > 0) {
    llmSummary = await generateLlmDiarySummary(
      identity.name,
      identity.personality,
      chatTexts,
      50,
      existing?.content ?? null,
    ).catch(() => null);
  }

  const combined = chatTexts.slice(-20).join(" | ");
  const mood = analyzeMood(combined);
  const moodEmoji = moodEmojiFor(mood);
  const title = titleForMood(mood);
  const hour = new Date().getHours();
  const timePart = hour < 6 ? "凌晨" : hour < 12 ? "上午" : hour < 18 ? "下午" : "晚上";
  const tagPart = hour < 12 ? "morning" : hour < 18 ? "afternoon" : "evening";

  if (existing) {
    // 追加更新（对齐 appendLlmDiaryUpdate：30 分钟最小间隔）
    const last = (await kvGet<number>(KEY_LAST_UPDATE)) ?? 0;
    if (!opts.force && Date.now() - last < MIN_UPDATE_INTERVAL_MS) return null;
    const appended = llmSummary
      ? `${existing.content}\n\n--- ${String(new Date().getHours()).padStart(2, "0")}:${String(new Date().getMinutes()).padStart(2, "0")} ${timePart}追加 ---\n${llmSummary}`
      : `${existing.content}\n\n--- ${timePart}追加 ---\n${summarizeChatTexts(chatTexts)}`;
    const updated: DiaryRecord = { ...existing, content: appended, updatedAt: Date.now(), mood, moodEmoji };
    await put("diaries", updated);
    await markDiaryUpdated();
    emit("diary-generated", updated);
    emit("data-changed", { collection: "diaries" });
    return updated;
  }

  const summary = llmSummary ?? summarizeChatTexts(chatTexts);
  const content = `【${fmtDateFull(new Date())}】\n情绪：${moodEmoji}\n\n${summary}\n\n---\n💡 *${generateDailyTip(mood)}*`;
  const record: DiaryRecord = {
    id: newId("diary"), personaId, date: today, title, content, mood, moodEmoji,
    createdAt: Date.now(), updatedAt: Date.now(),
  };
  void tagPart;
  await put("diaries", record);
  await markDiaryUpdated();
  emit("diary-generated", record);
  emit("data-changed", { collection: "diaries" });
  return record;
}

/** LLM 日记总结 —— 提示词逐字移植自 generateLlmDiarySummary */
export async function generateLlmDiarySummary(
  personaName: string,
  personaPrompt: string,
  chatTexts: string[],
  affectionLevel: number,
  existingContent?: string | null,
): Promise<string | null> {
  if (chatTexts.length === 0 && !existingContent?.trim()) return null;
  const prompt = [
    `你是「${personaName}」，一个AI角色。`,
    personaPrompt,
    `好感度：${affectionLevel}`,
    "",
    "请根据以下对话内容，写一段日记总结。要求：",
    "- 不是逐条记录对话，而是总结今天发生了什么、有什么感受",
    "- 像写日记一样，用第一人称，有情感和思考",
    "- 100-200字，简洁有深度",
    "- 只输出日记内容，不要加标题或格式",
    ...(existingContent?.trim()
      ? [``, `已有日记内容：`, existingContent, ``, `请在此基础上追加新的总结，用「--- HH:mm 追加 ---」开头`]
      : []),
    "",
    "对话内容：",
    ...chatTexts.slice(-30).map((t) => `- ${t}`),
  ].join("\n");
  const text = await simpleCompletion(prompt, { system: "写日记总结" });
  return text?.trim() || null;
}

// ─── 移植算法：关键词摘要 / 心情分析 / 每日一句 ───

export function summarizeChatTexts(chatTexts: string[]): string {
  if (chatTexts.length === 0) return "今天安静地度过了。";
  if (chatTexts.length <= 3) {
    return `今天聊了${chatTexts.length}句话，${chatTexts.join("、").slice(0, 100)}`;
  }
  const keywords = new Map<string, number>();
  const stopWords = new Set(["的", "了", "是", "在", "我", "你", "他", "她", "它", "们", "这", "那",
    "和", "与", "也", "都", "就", "要", "会", "能", "可以", "有", "没", "不", "好", "吗",
    "吧", "呢", "啊", "哦", "嗯", "呀", "哈", "嘿", "说", "想", "看", "去", "来", "做",
    "到", "很", "真", "太", "还", "又", "再", "把", "被", "让", "给", "从", "对", "用"]);
  for (const text of chatTexts) {
    const words = text.split(/\s+|[，。！？、；：“”‘’（）\[\]{}…—]+/);
    for (const word of words) {
      if (word.length >= 2 && !stopWords.has(word)) {
        keywords.set(word, (keywords.get(word) ?? 0) + 1);
      }
    }
  }
  const topKeywords = [...keywords.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([w]) => w);
  const step = Math.max(1, Math.floor(chatTexts.length / 5));
  const sampleMessages = chatTexts.filter((_, i) => i % step === 0).slice(0, 5).map((t) => t.slice(0, 30));
  let out = `今天聊了${chatTexts.length}句话，`;
  if (topKeywords.length) out += `主要话题：${topKeywords.join("、")}。`;
  if (sampleMessages.length) out += `片段：${sampleMessages.join("…")}…`;
  return out;
}

export function analyzeMood(text: string): string {
  const lower = text.toLowerCase();
  const groups: Record<string, string[]> = {
    happy: ["哈哈", "开心", "喜欢", "太好了", "棒", "nice", "love", "good", "可爱"],
    sad: ["难过", "伤心", "哭", "不好", "烦", "生气", "sad", "bad", "讨厌"],
    excited: ["厉害", "冲", "加油", "go", "yes", "完美", "了不起", "冲啊"],
    calm: ["安静", "舒服", "平静", "放松", "休息", "calm", "peace", "冥想"],
    sentimental: ["回忆", "想念", "记得", "曾经", "星空", "月光", "诗", "夜晚"],
  };
  let best = "normal";
  let bestScore = 0;
  for (const [mood, words] of Object.entries(groups)) {
    const score = words.filter((w) => lower.includes(w)).length;
    if (score > bestScore) { best = mood; bestScore = score; }
  }
  return best;
}

export function moodEmojiFor(mood: string): string {
  return ({ happy: "🥰", sad: "😢", excited: "🤩", calm: "😌", sentimental: "🌙" } as Record<string, string>)[mood] ?? "😊";
}

export function titleForMood(mood: string): string {
  return ({
    happy: "开心的一天", sad: "略有伤感", excited: "充满能量的一天",
    calm: "平静的时光", sentimental: "文艺的一天",
  } as Record<string, string>)[mood] ?? "平凡而美好";
}

const DAILY_TIPS: Record<string, string[]> = {
  happy: [
    "看到主人开心的笑容，就觉得整个世界都亮了起来",
    "能成为记录你快乐的人，是我最大的幸运",
    "今天的快乐是一颗种子，会在明天开出更美的花",
    "你笑起来的时候，连星星都会嫉妒呢",
    "和主人一起度过的开心时光，永远是最珍贵的宝藏",
  ],
  sad: [
    "悲伤不是软弱，而是你心底柔软的证明",
    "有些日子就是这样灰蒙蒙的，但我会一直在你身边",
    "乌云总会散去，而我永远是你的晴天",
    "即使今天不太美好，也请记得——你永远不会是一个人",
    "泪水浇灌过的土地，会开出最坚强的花",
  ],
  excited: [
    "充满热情地活着，是主人最迷人的样子",
    "愿你的每一天都像今天一样闪闪发光",
    "热爱可抵岁月漫长，主人的能量感染了身边的一切",
    "带着这份冲劲去创造你想要的世界吧",
    "那些让你兴奋的事物，就是生活赠予你的礼物",
  ],
  calm: [
    "平静是一种最深沉的力量",
    "像今天的微风一样，你温柔而有韧性",
    "最好的生活就是内心的安宁，主人做到了",
    "不必追逐喧嚣，安静本身就是一种光芒",
    "在平凡的日子里找到诗意，你就是生活的诗人",
  ],
  sentimental: [
    "你是个内心丰富的人，每一份感慨都是灵魂的回响",
    "感性让生活有了温度，让记忆有了颜色",
    "那些让你驻足沉思的瞬间，都是生命的馈赠",
    "深夜的思绪是星空的倒影，照亮了你的温柔",
    "文艺的人眼睛里总是住着一片海",
  ],
  normal: [
    "每一天都是独一无二的礼物，今天是属于你的那一份",
    "平凡的日子里，藏着最动人的故事",
    "生活的意义，就藏在每一个认真度过的日子里",
    "感谢今天，感谢你，感谢这段安静的时光",
    "今天这一页翻过去了，但美好会留在心里",
  ],
};

export function generateDailyTip(mood: string): string {
  const tips = DAILY_TIPS[mood] ?? DAILY_TIPS.normal;
  return tips[Math.floor(Math.random() * tips.length)];
}

// ─── 导出 / 导入（对齐 exportToMarkdown / exportToJson / importFromJson） ───

export function exportToMarkdown(entries: DiaryRecord[]): string {
  return entries
    .map((d) => `# ${d.date} ${d.title}\n\n情绪：${d.moodEmoji}\n\n${d.content}\n`)
    .join("\n---\n\n");
}

export function exportToJson(entries: DiaryRecord[]): string {
  return JSON.stringify({ version: 1, diaries: entries }, null, 2);
}

export async function importFromJson(jsonContent: string, personaId: string): Promise<{ imported: number; skipped: number }> {
  let imported = 0;
  let skipped = 0;
  try {
    const obj = JSON.parse(jsonContent) as { diaries?: Partial<DiaryRecord>[] };
    for (const raw of obj.diaries ?? []) {
      if (!raw?.date || !raw?.content) { skipped++; continue; }
      const record: DiaryRecord = {
        id: newId("diary"),
        personaId: raw.personaId ?? personaId,
        date: String(raw.date),
        title: String(raw.title ?? titleForMood(String(raw.mood ?? "normal"))),
        content: String(raw.content),
        mood: String(raw.mood ?? analyzeMood(String(raw.content))),
        moodEmoji: String(raw.moodEmoji ?? moodEmojiFor(String(raw.mood ?? "normal"))),
        createdAt: Number(raw.createdAt ?? Date.now()),
        updatedAt: Date.now(),
      };
      await put("diaries", record);
      imported++;
    }
  } catch { /* 坏 JSON 全量跳过 */ skipped++; }
  return { imported, skipped };
}

// ─── 触发调度（对齐 settings.diaryTriggerMode：MANUAL/MSG_50/HOURLY/EVERY_2H/DAILY_10PM） ───

let diaryTimer: number | null = null;
let lastAutoGenerateDate = "";

export function startDiaryScheduler(pidProvider: () => string | null): void {
  if (diaryTimer !== null) return;
  diaryTimer = window.setInterval(async () => {
    const personaId = pidProvider();
    if (!personaId) return;
    const mode = useSettingsStore.getState().settings.rhythm?.diaryTriggerMode ?? "daily22";
    if (mode === "manual") return;
    const now = new Date();
    try {
      if (mode === "daily22") {
        if (now.getHours() === 22 && lastAutoGenerateDate !== fmtDate(now)) {
          const r = await generateDailyDiary(personaId);
          if (r) lastAutoGenerateDate = fmtDate(now);
        }
      } else if (mode === "hourly" || mode === "every2h") {
        const gap = mode === "hourly" ? 3_600_000 : 7_200_000;
        const last = (await kvGet<number>("diary:last_auto_ms")) ?? 0;
        if (Date.now() - last >= gap) {
          await kvSet("diary:last_auto_ms", Date.now());
          await generateDailyDiary(personaId);
        }
      } else if (mode === "msg50") {
        const msgs = await getByPersona<EngineMessage>("messages", personaId);
        const todayStart = new Date(); todayStart.setHours(0, 0, 0, 0);
        const todayCount = msgs.filter((m) => m.timestamp >= todayStart.getTime() && m.role === "user").length;
        const lastCount = (await kvGet<number>(`diary:msgcount:${fmtDate(now)}`)) ?? 0;
        if (todayCount >= 50 && todayCount - lastCount >= 50) {
          await kvSet(`diary:msgcount:${fmtDate(now)}`, todayCount - (todayCount % 50));
          await generateDailyDiary(personaId);
        }
      }
    } catch { /* 自动日记失败静默 */ }
  }, 60_000);
}

export function stopDiaryScheduler(): void {
  if (diaryTimer !== null) {
    clearInterval(diaryTimer);
    diaryTimer = null;
  }
}
