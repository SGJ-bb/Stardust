/**
 * 表情包引擎 —— 移植自 sticker/StickerManager.kt + StickerModel.kt
 * 复刻规则：
 * - 内置表情目录逐条抄自 assets/builtin_stickers/builtin_metadata.json（14 款，图片已复制到
 *   public/stickers/，dataUrl 用 /stickers/<filename> 引用）；
 * - searchStickersByKeyword：小写包含匹配 description/emotion/tags/id（照抄 Kotlin），
 *   matchSticker 在其基础上加"整句命中 tags → 随机选取"（Kotlin SendStickerPlugin: stickers.random()）；
 * - deleteSticker：builtin_ 前缀拒删（Kotlin id.startsWith("builtin_") return）；
 * - 向量检索路径：Android 用 ApiClient.getEmbedding + 余弦相似(阈值0.3)。本端 llmClient 无 embedding API，
 *   故保留 cosineSimilarity/matchThreshold 算法骨架并以关键词匹配兜底（任务允许的 LLM 兜底为可选）。
 *
 * 用户表情存 db collection "stickers"（StickerRecord），personaId 为空 = 全局。
 */

import { getAll, getOne, newId, put, remove } from "@/lib/db";
import type { StickerRecord } from "./kinds";

/** 内置表情目录（抄自 builtin_metadata.json：id/name/emotion/tags/description/filename） */
export const BUILTIN_STICKERS: { id: string; name: string; emotion: string; tags: string[]; description: string; file: string }[] = [
  { id: "builtin_偷听", name: "偷听", emotion: "好奇", tags: ["偷听", "八卦", "好奇", "窥探"], description: "偷偷听别人说话，好奇心满满", file: "偷听.jpg" },
  { id: "builtin_偷瞄", name: "偷瞄", emotion: "害羞", tags: ["偷瞄", "害羞", "偷偷看", "腼腆"], description: "偷偷瞄一眼，有点害羞", file: "偷瞄.jpg" },
  { id: "builtin_卖萌揣手手", name: "卖萌揣手手", emotion: "可爱", tags: ["卖萌", "揣手", "可爱", "撒娇", "乖巧"], description: "揣着小手卖萌，超级可爱", file: "卖萌揣手手.jpg" },
  { id: "builtin_吐槽", name: "吐槽", emotion: "无语", tags: ["吐槽", "无语", "嫌弃", "不满", "抱怨"], description: "忍不住吐槽，表示不满", file: "吐槽.jpg" },
  { id: "builtin_呆滞", name: "呆滞", emotion: "发呆", tags: ["呆滞", "发呆", "放空", "宕机", "懵"], description: "大脑空白，呆呆地发愣", file: "呆滞.jpg" },
  { id: "builtin_哭泣", name: "哭泣", emotion: "难过", tags: ["哭泣", "难过", "伤心", "委屈", "哭"], description: "伤心地哭泣，需要安慰", file: "哭泣.jpg" },
  { id: "builtin_宕机", name: "宕机", emotion: "崩溃", tags: ["宕机", "崩溃", "死机", "卡住", "当机"], description: "大脑宕机了，完全无法思考", file: "宕机.jpg" },
  { id: "builtin_慌张", name: "慌张", emotion: "慌张", tags: ["慌张", "紧张", "害怕", "慌乱", "着急"], description: "慌慌张张，不知所措", file: "慌张.jpg" },
  { id: "builtin_捂嘴笑", name: "捂嘴笑", emotion: "偷笑", tags: ["捂嘴笑", "偷笑", "坏笑", "窃喜", "开心"], description: "捂着嘴偷笑，心里美滋滋", file: "捂嘴笑.png" },
  { id: "builtin_调侃", name: "调侃", emotion: "调侃", tags: ["调侃", "戏弄", "打趣", "开玩笑", "逗"], description: "调侃别人，带着玩味的笑", file: "调侃.jpg" },
  { id: "builtin_邪恶的笑", name: "邪恶的笑", emotion: "坏笑", tags: ["邪恶", "坏笑", "阴谋", "不怀好意", "腹黑"], description: "邪恶地笑，心里打着坏主意", file: "邪恶的笑.jpg" },
  { id: "builtin_风趣调侃", name: "风趣调侃", emotion: "幽默", tags: ["风趣", "幽默", "调侃", "机智", "搞笑"], description: "风趣地调侃，幽默感十足", file: "风趣调侃.jpg" },
  { id: "builtin_骂别人是猪", name: "骂别人是猪", emotion: "嫌弃", tags: ["骂人", "嫌弃", "吐槽", "笨蛋", "猪"], description: "骂别人是猪，嫌弃的样子", file: "骂别人是猪.jpg" },
  { id: "builtin_鬼迷日眼的笑", name: "鬼迷日眼的笑", emotion: "狡黠", tags: ["鬼迷日眼", "狡黠", "贼笑", "诡异", "不怀好意"], description: "鬼迷日眼地笑，表情诡异又好笑", file: "鬼迷日眼的笑.jpg" },
];

/** 内置表情转 StickerRecord（owner=builtin → personaId undefined 表示全局） */
function builtinToRecord(b: (typeof BUILTIN_STICKERS)[number]): StickerRecord {
  return {
    id: b.id,
    name: b.name,
    tags: [...b.tags, b.emotion, b.description], // 匹配语料对齐 Kotlin（description+emotion+tags）
    dataUrl: `/stickers/${encodeURIComponent(b.file)}`,
    createdAt: 0,
    useCount: 0,
  };
}

/** 添加用户表情 */
export async function addSticker(input: { name: string; tags: string[]; dataUrl: string; personaId?: string }): Promise<StickerRecord> {
  const rec: StickerRecord = {
    id: newId("stk"),
    personaId: input.personaId || undefined,
    name: input.name,
    tags: input.tags.filter((t) => t.trim()),
    dataUrl: input.dataUrl,
    createdAt: Date.now(),
    useCount: 0,
  };
  await put("stickers", rec);
  return rec;
}

/** 列出表情（personaId 省略 = 全部；含内置；指定时 = 内置 + 该角色 + 全局自定义） */
export async function listStickers(personaId?: string): Promise<StickerRecord[]> {
  const users = await getAll<StickerRecord>("stickers");
  const scoped = !personaId ? users : users.filter((s) => !s.personaId || s.personaId === personaId);
  return [...BUILTIN_STICKERS.map(builtinToRecord), ...scoped];
}

/** 删除表情（builtin_ 拒删，对齐 Kotlin） */
export async function deleteSticker(id: string): Promise<void> {
  if (id.startsWith("builtin_")) return;
  await remove("stickers", id);
}

/** 使用次数 +1 */
export async function incrementUse(id: string): Promise<void> {
  if (id.startsWith("builtin_")) return; // 内置记录不在 stickers store 中，无需计数
  const rec = await getOne<StickerRecord>("stickers", id);
  if (rec) {
    rec.useCount += 1;
    await put("stickers", rec);
  }
}

/** 关键词匹配（照抄 Kotlin searchStickersByKeyword 的 lowercase contains 逻辑） */
function searchByKeyword(stickers: StickerRecord[], keyword: string): StickerRecord[] {
  const kw = keyword.toLowerCase();
  if (!kw) return [];
  return stickers.filter(
    (s) =>
      s.name.toLowerCase().includes(kw) ||
      s.tags.some((t) => t.toLowerCase().includes(kw)) ||
      s.id.toLowerCase().includes(kw) ||
      s.id.replace(/^builtin_/, "").toLowerCase().includes(kw),
  );
}

/** 余弦相似度（算法骨架照抄 Kotlin cosineSimilarity，预留给未来 embedding 支持） */
export function cosineSimilarity(a: number[], b: number[]): number {
  if (a.length !== b.length || a.length === 0) return 0;
  let dot = 0, normA = 0, normB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  const denom = Math.sqrt(normA) * Math.sqrt(normB);
  return denom > 0 ? dot / denom : 0;
}

/** 相似度阈值（Kotlin sim >= 0.3f） */
export const STICKER_MATCH_THRESHOLD = 0.3;

/**
 * 从文本匹配一个表情（AI 发表情入口）。
 * 策略（忠实 Kotlin 变体）：
 * 1. 逐个 tag/emotion 词在文本中出现即候选（等价 searchStickersByKeyword 反向：文本→表情元数据）；
 * 2. 命中多个时随机取一个（Kotlin stickers.random()）；
 * 3. 无任何命中返回 null（调用方可用 llmClient 做情感推断后重试，参考 SendStickerPlugin 的 emotion 参数）。
 */
export async function matchSticker(text: string, personaId?: string): Promise<StickerRecord | null> {
  if (!text.trim()) return null;
  const lower = text.toLowerCase();
  const pool = await listStickers(personaId);
  // 正向匹配：表情 tags/name 中的词出现在用户消息里
  const hits = pool.filter((s) => {
    const words = [s.name, ...s.tags, s.id.replace(/^builtin_/, "")];
    return words.some((w) => w && lower.includes(w.toLowerCase()));
  });
  if (hits.length) return hits[Math.floor(Math.random() * hits.length)];
  // 反向匹配：把整段文本当关键词（Kotlin searchStickersByKeyword(emotion) 语义）
  const rev = searchByKeyword(pool, text.trim());
  if (rev.length) return rev[Math.floor(Math.random() * rev.length)];
  return null;
}
