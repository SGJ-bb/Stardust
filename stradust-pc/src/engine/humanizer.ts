/**
 * 拟人化 —— 移植自 Android humanizer/Humanizer.kt
 * Kotlin 版输出 HumanizedChunk 列表（文本片段 + 打字延迟）供逐段渲染；
 * Web 版按任务契约提供 humanize(text, personaId?)：把思考前缀、错别字自我纠正、
 * 停顿节奏与偶发闲聊插入折叠回一段可流式展示的文本。
 * 尊重 settings.aiFeatures.humanizerEnabled 开关；分句/词表逻辑逐行对齐 Kotlin。
 */

import { useSettingsStore } from "@/stores/useSettingsStore";

// ─── 词表（逐字对齐 Humanizer.Companion） ───
export const THINKING_PREFIXES = ["嗯…", "让我想想…", "emmm…", "唔…", "这个嘛…"];

/** [触发词, 自我纠正话术, 概率]（对齐 TYPO_PATTERNS） */
const TYPO_PATTERNS: [string, string, number][] = [
  ["明天早上", "哦不，是明天下午", 0.04],
  ["我觉得", "不对，我重新想一下…", 0.03],
  ["那个", "呃不对", 0.04],
  ["大概", "哦等一下，确切说是", 0.03],
];

export const IDLE_INSERTIONS = [
  "说起来…你今天过得怎么样呀？",
  "诶，我突然想到一个事…",
  "噗",
  "😂",
  "(´･ω･`)",
  "✨",
  "对了对了！",
  "啊对了～",
];

const SENTENCE_ENDINGS = ["。", "！", "～", "…", "呢", "哦", "呐", "啦"];
void SENTENCE_ENDINGS; // 保留词表（Kotlin 中用于未来扩展），当前版本未参与分支

const rand = () => Math.random();
const pick = <T,>(list: T[]): T => list[Math.floor(rand() * list.length)];

/** 分句（对齐 splitSentences：句末标点附着切分 + 长逗号兜底合并） */
export function splitSentences(text: string): string[] {
  const result: string[] = [];
  const matches = [...text.matchAll(/(.+?[。！？\n～…])(?=\s*[^\s]|\s*$)/g)];
  let lastEnd = 0;
  for (const m of matches) {
    const s = m[1].trim();
    if (s) result.push(s);
    lastEnd = (m.index ?? 0) + m[1].length;
  }
  // 修复：句尾无标点的最后一段必须保留（原实现会被正则整段丢弃）
  const tail = text.slice(lastEnd).trim();
  if (tail) result.push(tail);
  // 兜底：超长无句末标点时按逗号切并合并为 ~15 字段
  if (result.length === 0 && text.length > 20) {
    const segments = text.split(/(?<=，)|(?<=,)/);
    const merged: string[] = [];
    let current = "";
    for (const seg of segments) {
      if (current.length + seg.length < 15) {
        current += seg;
      } else {
        if (current.trim()) merged.push(current.trim());
        current = seg;
      }
    }
    if (current.trim()) merged.push(current.trim());
    return merged;
  }
  if (result.length === 0) result.push(text);
  return result;
}

/** 解析 <pause=Xms> 标签（对齐 parsePauseTags：按出现顺序映射到句索引） */
function parsePauseTags(text: string): { processed: string; pauses: Map<number, number> } {
  const pausePattern = /<pause=(\d+)ms>/g;
  const pauses = new Map<number, number>();
  let sentenceIndex = 0;
  let cleaned = text;
  for (const m of [...text.matchAll(pausePattern)]) {
    pauses.set(sentenceIndex, Number(m[1]) || 500);
    sentenceIndex++;
    cleaned = cleaned.replace(m[0], "");
  }
  return { processed: cleaned, pauses };
}

/** 错别字自我纠正注入（对齐 maybeInjectTypo：在前两句之一随机替换为「原词+纠正话术对吧？」） */
function maybeInjectTypo(sentences: string[]): string[] {
  const index = Math.floor(rand() * Math.min(sentences.length, 2));
  const sentence = sentences[index];
  const [wrong, correct] = pick(TYPO_PATTERNS);
  const modified = sentence.replace(wrong, `${wrong}${correct}对吧？`);
  const out = [...sentences];
  out[index] = modified;
  return out;
}

/** 复杂问题判定（关键词命中≥1 且长度>10——对齐 isComplexQuestion） */
export function isComplexQuestion(text: string): boolean {
  const complexKeywords = [
    "为什么", "怎么", "如何", "能不能", "可以吗",
    "是什么", "区别", "比较", "推荐", "建议",
    "分析", "解释", "原理", "方法", "步骤",
  ];
  const lower = text.toLowerCase();
  const matchCount = complexKeywords.filter((k) => lower.includes(k)).length;
  return matchCount >= 1 && text.length > 10;
}

/** 结构化分块输出（对齐 Humanizer.humanize 的返回形态，供需要逐段延迟的 UI 使用） */
export interface HumanizedChunk {
  text: string;
  delayMs: number;
  isThinking: boolean;
}

/**
 * 完整分块拟人化（逐行对齐 Kotlin humanize(rawText, isComplexQuestion)）。
 * 需要精细打字机效果时可直接消费本函数输出。
 */
export function humanizeChunks(rawText: string, complexQuestion: boolean): HumanizedChunk[] {
  const chunks: HumanizedChunk[] = [];
  if (!rawText.trim()) return chunks;

  const { processedText, pauseSegments } = (() => {
    const r = parsePauseTags(rawText);
    return { processedText: r.processed, pauseSegments: r.pauses };
  })();

  // 复杂问题 → 60% 概率思考前缀
  if (complexQuestion && rand() < 0.6) {
    chunks.push({ text: pick(THINKING_PREFIXES), delayMs: 800 + Math.floor(rand() * 600), isThinking: true });
  }

  const sentences = splitSentences(processedText);
  const typoChunks = sentences.length >= 2 && rand() < 0.06 ? maybeInjectTypo(sentences) : sentences;

  for (let i = 0; i < typoChunks.length; i++) {
    const sentence = typoChunks[i];
    const isLast = i === typoChunks.length - 1;
    const isShort = sentence.length < 8;
    const delayMs =
      pauseSegments.has(i)
        ? pauseSegments.get(i)!
        : isShort
          ? 400 + Math.floor(rand() * 300)
          : isLast
            ? 300 + Math.floor(rand() * 200)
            : sentence.endsWith("…")
              ? 900 + Math.floor(rand() * 400)
              : sentences.length === 1
                ? 0
                : 600 + Math.floor(rand() * 500);
    const actualDelay = chunks.length === 0 || chunks[chunks.length - 1].isThinking ? 200 + Math.floor(rand() * 200) : delayMs;
    chunks.push({ text: sentence, delayMs: actualDelay, isThinking: false });
  }

  // 3% 概率尾部闲聊插入
  if (rand() < 0.03) {
    chunks.push({ text: pick(IDLE_INSERTIONS), delayMs: 1500 + Math.floor(rand() * 800), isThinking: false });
  }
  return chunks;
}

/**
 * 拟人化文本（同步纯函数）。关闭 humanizerEnabled 时原样返回。
 * @param personaId 预留参数（Kotlin 无角色差异，Web 版未来可按角色调整风格）
 */
export function humanize(text: string, personaId?: string): string {
  void personaId;
  const enabled = useSettingsStore.getState().settings.aiFeatures?.humanizerEnabled ?? true;
  if (!enabled || !text.trim()) return text;
  const chunks = humanizeChunks(text, isComplexQuestion(text));
  return chunks.map((c) => c.text).join("");
}
