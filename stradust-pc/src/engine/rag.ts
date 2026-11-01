/**
 * 角色 RAG —— 移植自 Android rag/：
 *   RagConfig.kt（常量）、VectorMath.kt、TextChunker.kt、RagEmbedder.kt(TfidfEmbedder)、
 *   PersonaRagManager.kt、VectorStore.kt
 * 向量索引持久化到 IndexedDB kv 集合，key = `rag:{personaId}`。
 * 纯 TS，无 React 依赖；仅实现 tfidf 嵌入模式（Web 版无 ONNX/云端嵌入器），
 * reranker / cloud / local 模式在 Kotlin 中依赖端侧模型，此处省略并在注释说明。
 */

import { kvGet, kvSet } from "@/lib/db";

// ─── RagConfig 常量（对齐 rag/RagConfig.kt 默认值） ───
export const RAG_DEFAULTS = {
  personaRagEnabled: true,
  personaTopK: 3,
  chunkMaxChars: 300,
  chunkOverlapChars: 60,
  minSimilarity: 0.12,
  embeddingMode: "tfidf" as const,
  ragMode: "auto" as const,
  treeRagEnabled: true,
  treeRagMaxDepth: 2,
};

// ─── VectorMath（对齐 rag/VectorMath.kt） ───
export function cosineSimilarity(a: number[], b: number[]): number {
  if (a.length !== b.length || a.length === 0) return 0;
  let dot = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  const denom = Math.sqrt(normA) * Math.sqrt(normB);
  return denom > 0 ? dot / denom : 0;
}

// ─── TextChunker（对齐 rag/TextChunker.kt，含 [[链接]] 提取） ───
export interface Chunk {
  index: number;
  text: string;
  sourceField: string;
  links: string[];
}

const LINK_PATTERN = /\[\[([^\]|]+)(?:\|[^\]]+)?\]\]/g;

/** 提取 [[链接]] 目标文本（去重、保持顺序） */
function extractLinks(text: string): string[] {
  const out: string[] = [];
  for (const m of text.matchAll(LINK_PATTERN)) {
    const target = m[1].trim();
    if (target && !out.includes(target)) out.push(target);
  }
  return out;
}

function buildOverlap(text: string, chars: number): string {
  if (text.length <= chars) return "";
  const start = text.length - chars;
  const boundaries = ["。", "！", "？", "\n", "；"];
  let boundary = -1;
  for (let i = start; i < text.length; i++) {
    if (boundaries.includes(text[i])) {
      boundary = i;
      break;
    }
  }
  return boundary >= 0 && boundary < text.length - 5 ? text.slice(boundary + 1) : text.slice(start);
}

class TextChunker {
  constructor(private maxChars = RAG_DEFAULTS.chunkMaxChars, private overlapChars = RAG_DEFAULTS.chunkOverlapChars) {}

  /** 按角色字段分块（对齐 chunkPersona(fields)） */
  chunkPersona(fields: Record<string, string>): Chunk[] {
    const chunks: Chunk[] = [];
    let globalIndex = 0;
    for (const [fieldName, text] of Object.entries(fields)) {
      if (!text || !text.trim()) continue;
      if (text.length <= this.maxChars) {
        const trimmed = text.trim();
        chunks.push({ index: globalIndex++, text: trimmed, sourceField: fieldName, links: extractLinks(trimmed) });
      } else {
        const sub = this.splitLongText(text.trim(), fieldName, globalIndex);
        chunks.push(...sub);
        globalIndex += sub.length;
      }
    }
    return chunks;
  }

  chunkText(text: string, sourceLabel = ""): Chunk[] {
    if (!text || !text.trim()) return [];
    if (text.length <= this.maxChars) {
      const trimmed = text.trim();
      return [{ index: 0, text: trimmed, sourceField: sourceLabel, links: extractLinks(trimmed) }];
    }
    return this.splitLongText(text.trim(), sourceLabel, 0);
  }

  private splitLongText(text: string, fieldName: string, startIndex: number): Chunk[] {
    const chunks: Chunk[] = [];
    const paragraphs = text.split(/\n+/).filter((p) => p.trim() !== "");
    let current = "";
    let localIndex = startIndex;

    for (const para of paragraphs) {
      if (current.length + para.length > this.maxChars && current.length > 0) {
        const chunkText = current.trim();
        chunks.push({ index: localIndex++, text: chunkText, sourceField: fieldName, links: extractLinks(chunkText) });
        current = buildOverlap(current, this.overlapChars);
      }
      if (para.length > this.maxChars) {
        if (current.length > 0) {
          const chunkText = current.trim();
          chunks.push({ index: localIndex++, text: chunkText, sourceField: fieldName, links: extractLinks(chunkText) });
          current = "";
        }
        const sub = this.splitLongSentence(para, fieldName, localIndex);
        chunks.push(...sub);
        localIndex += sub.length;
        current = buildOverlap(sub[sub.length - 1].text, this.overlapChars);
      } else {
        if (current.length > 0) current += "\n";
        current += para;
      }
    }
    if (current.trim()) {
      const chunkText = current.trim();
      chunks.push({ index: localIndex++, text: chunkText, sourceField: fieldName, links: extractLinks(chunkText) });
    }
    return chunks;
  }

  private splitLongSentence(text: string, fieldName: string, startIndex: number): Chunk[] {
    const chunks: Chunk[] = [];
    // Kotlin: Regex("(?<=[。！？；])|(?<=[，,])") —— JS lookbehind 在 ES2018+ 支持
    const sentences = text.split(/(?<=[。！？；])|(?<=[，,])/).filter((s) => s.trim() !== "");
    let current = "";
    let idx = startIndex;
    for (const sentence of sentences) {
      if (current.length + sentence.length > this.maxChars && current.length > 0) {
        const chunkText = current.trim();
        chunks.push({ index: idx++, text: chunkText, sourceField: fieldName, links: extractLinks(chunkText) });
        current = buildOverlap(current, Math.floor(this.overlapChars / 2));
      }
      current += sentence;
    }
    if (current.trim()) {
      const chunkText = current.trim();
      chunks.push({ index: idx++, text: chunkText, sourceField: fieldName, links: extractLinks(chunkText) });
    }
    if (chunks.length === 0) {
      const step = Math.max(this.maxChars - this.overlapChars, 1);
      let pos = 0;
      while (pos < text.length) {
        const end = Math.min(pos + this.maxChars, text.length);
        const chunkText = text.slice(pos, end);
        chunks.push({ index: idx++, text: chunkText, sourceField: fieldName, links: extractLinks(chunkText) });
        pos += step;
      }
    }
    return chunks;
  }
}

// ─── TfidfEmbedder（对齐 rag/RagEmbedder.kt TfidfEmbedder：单字+双字 token，取 top2000 词表） ───
class TfidfEmbedder {
  private vocabulary = new Map<string, number>();
  private idf: number[] = [];
  private built = false;

  isReady(): boolean {
    return this.built;
  }

  dimension(): number {
    return this.built ? this.vocabulary.size : 0;
  }

  buildVocabulary(corpus: string[]): void {
    this.vocabulary.clear();
    const df = new Map<string, number>();
    const tokenizedCorpus = corpus.map((c) => this.tokenize(c));
    for (const tokens of tokenizedCorpus) {
      for (const token of new Set(tokens)) {
        df.set(token, (df.get(token) ?? 0) + 1);
      }
    }
    const sortedTokens = [...df.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 2000)
      .map(([k]) => k);
    sortedTokens.forEach((token, i) => this.vocabulary.set(token, i));
    const N = corpus.length;
    this.idf = new Array(this.vocabulary.size).fill(0);
    for (const [token, idx] of this.vocabulary) {
      const docFreq = df.get(token) ?? 1;
      this.idf[idx] = Math.log((N + 1) / (docFreq + 1)) + 1;
    }
    this.built = true;
  }

  embedSingle(text: string): number[] {
    if (!this.built) return [];
    const vec = new Array(this.vocabulary.size).fill(0);
    const tokens = this.tokenize(text);
    const counts = new Map<string, number>();
    for (const t of tokens) counts.set(t, (counts.get(t) ?? 0) + 1);
    const total = tokens.length || 1;
    for (const [token, count] of counts) {
      const idx = this.vocabulary.get(token);
      if (idx === undefined) continue;
      vec[idx] = (count / total) * this.idf[idx];
    }
    const norm = Math.sqrt(vec.reduce((s, v) => s + v * v, 0));
    if (norm > 0) for (let i = 0; i < vec.length; i++) vec[i] /= norm;
    return vec;
  }

  embed(texts: string[]): number[][] {
    return texts.map((t) => this.embedSingle(t));
  }

  /** 去空白与标点（保留 。，！？…～），切单字 + 双字 token，去重（同 Kotlin tokenize） */
  private tokenize(text: string): string[] {
    const tokens: string[] = [];
    const cleaned = text.replace(/[^\S\u3000。，！？…～\p{L}\p{N}]/gu, "");
    if (!cleaned) return tokens;
    for (const ch of cleaned) tokens.push(ch);
    const chars = [...cleaned];
    for (let i = 0; i < chars.length - 1; i++) tokens.push(chars[i] + chars[i + 1]);
    return [...new Set(tokens)];
  }
}

// ─── VectorStore（对齐 rag/VectorStore.kt，持久化改存 kv 集合） ───
interface VectorEntryJson {
  id: number;
  text: string;
  vector: number[];
  source: string;
  links: string[];
}

interface StoredIndex {
  hash: string;
  entries: VectorEntryJson[];
}

function indexKey(personaId: string): string {
  return `rag:${personaId}`;
}

/** 内存态：per-persona 索引缓存（内部可重载，避免每次检索读 IDB） */
interface LoadedIndex {
  store: VectorEntryJson[];
  embedder: TfidfEmbedder;
  hash: string;
}
const indexCache = new Map<string, LoadedIndex>();

// ─── 角色字段提取（镜像 MainActivity.buildPersonaFields） ───
/** 任意携带设定字段的 persona 对象（Web Persona 或 CharacterCard 均可） */
export interface RagPersonaLike {
  id: string;
  name?: string;
  description?: string;
  personality?: string;
  scenario?: string;
  greeting?: string;
  tags?: string[];
  /** CharacterCard 风格的补充字段 */
  speechStyle?: string;
  appearance?: string;
  catchphrases?: string;
  preferences?: string;
  worldSetting?: string;
  worldRelationship?: string;
  worldRules?: string;
  systemPrompt?: string;
  mesExample?: string;
  creatorNotes?: string;
  updatedAt?: number;
  createdAt?: number;
  [key: string]: unknown;
}

/**
 * 从 persona 提取 RAG 分块字段（镜像 MainActivity.buildPersonaFields：
 * name/desc/appearance/personality/speechStyle/catchphrases/preferences/worldSetting/worldRelationship/worldRules）。
 * Web Persona 缺少的字段自动跳过；额外补充 systemPrompt/scenario/greeting/tags 等可用字段。
 */
export function personaToFields(persona: RagPersonaLike): Record<string, string> {
  const fields: Record<string, string> = {};
  const put = (key: string, label: string, value?: string | null) => {
    const v = (value ?? "").trim();
    if (v) fields[key] = `${label}：${v}`;
  };
  if (persona.name?.trim()) fields["name"] = `你是「${persona.name.trim()}」。`;
  put("desc", "简介", persona.description);
  put("appearance", "外貌", persona.appearance);
  put("personality", "性格", persona.personality);
  put("speechStyle", "说话风格", persona.speechStyle ?? persona.tags?.join("、"));
  put("catchphrases", "常用口头禅", persona.catchphrases);
  put("preferences", "喜好", persona.preferences);
  put("worldSetting", "世界观设定", persona.worldSetting ?? persona.scenario);
  put("worldRelationship", "你和用户的关系", persona.worldRelationship);
  put("worldRules", "规则", persona.worldRules);
  put("systemPrompt", "系统设定", persona.systemPrompt);
  put("example", "对话示例", persona.mesExample);
  put("notes", "作者备注", persona.creatorNotes);
  return fields;
}

// ─── SHA-256（对齐 PersonaRagManager.sha256） ───
async function sha256(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  if (globalThis.crypto?.subtle) {
    const buf = await crypto.subtle.digest("SHA-256", data);
    return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
  }
  // 无 SubtleCrypto 环境的简易回退（非安全场景，仅做变更检测）
  let h = 5381;
  for (const b of data) h = ((h << 5) + h + b) >>> 0;
  return `js-${h.toString(16)}`;
}

/** 分块（对齐 TextChunker.Chunk） */
export function chunkPersona(persona: RagPersonaLike): Chunk[] {
  const chunker = new TextChunker(RAG_DEFAULTS.chunkMaxChars, RAG_DEFAULTS.chunkOverlapChars);
  return chunker.chunkPersona(personaToFields(persona));
}

/** 文本通用分块（对齐 TextChunker.chunkText，供日记/记忆等复用） */
export function chunkText(text: string, sourceLabel = ""): Chunk[] {
  const chunker = new TextChunker(RAG_DEFAULTS.chunkMaxChars, RAG_DEFAULTS.chunkOverlapChars);
  return chunker.chunkText(text, sourceLabel);
}

/** 计算当前 persona 字段哈希（用于增量重建判断，对齐 Kotlin checkAndRebuildPersonaIndex） */
export async function currentHash(persona: RagPersonaLike): Promise<string> {
  const fields = personaToFields(persona);
  const joined = Object.entries(fields)
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([k, v]) => `${k}=${v}`)
    .join("\u0000");
  return sha256(joined);
}

/**
 * 为角色构建 RAG 索引（对齐 PersonaRagManager.buildIndex）。
 * 哈希未变且已有索引则直接复用；向量存 kv 集合 key=`rag:{personaId}`。
 */
export async function buildIndex(persona: RagPersonaLike): Promise<void> {
  const pid = persona.id;
  const fields = personaToFields(persona);
  const newHash = await currentHash(persona);

  const cached = indexCache.get(pid);
  if (cached && cached.hash === newHash) return;

  const stored = await kvGet<StoredIndex>(indexKey(pid));
  if (stored && stored.hash === newHash) {
    const embedder = new TfidfEmbedder();
    embedder.buildVocabulary(stored.entries.map((e) => e.text));
    indexCache.set(pid, { store: stored.entries, embedder, hash: newHash });
    return;
  }

  const chunker = new TextChunker(RAG_DEFAULTS.chunkMaxChars, RAG_DEFAULTS.chunkOverlapChars);
  const chunks = chunker.chunkPersona(fields);
  if (chunks.length === 0) {
    indexCache.delete(pid);
    await kvSet(indexKey(pid), { hash: "", entries: [] } satisfies StoredIndex);
    return;
  }
  const embedder = new TfidfEmbedder();
  embedder.buildVocabulary(chunks.map((c) => c.text));
  const vectors = embedder.embed(chunks.map((c) => c.text));
  const entries: VectorEntryJson[] = chunks.map((c, i) => ({
    id: c.index,
    text: c.text,
    vector: i < vectors.length ? vectors[i] : [],
    source: c.sourceField,
    links: c.links,
  }));
  await kvSet(indexKey(pid), { hash: newHash, entries } satisfies StoredIndex);
  indexCache.set(pid, { store: entries, embedder, hash: newHash });
}

/** 树扩展：每层最多引入的分块数 / 总数上限（对齐 PersonaRagManager MAX_EXPANSION_*） */
const MAX_EXPANSION_PER_DEPTH = 3;
const MAX_EXPANSION_TOTAL = 8;

/** 沿 [[超链接]] 广度优先扩展相关分块（对齐 expandByLinks） */
function expandByLinks(store: VectorEntryJson[], initialIds: number[]): VectorEntryJson[] {
  const maxDepth = Math.min(Math.max(RAG_DEFAULTS.treeRagMaxDepth, 1), 5);
  const result = new Map<number, VectorEntryJson>();
  const visited = new Set<number>(initialIds);
  let currentLayer = initialIds.map((id) => store.find((e) => e.id === id)!).filter(Boolean);
  let depth = 0;

  while (depth < maxDepth && result.size < MAX_EXPANSION_TOTAL) {
    const allLinks = [...new Set(currentLayer.flatMap((e) => e.links ?? []))];
    if (allLinks.length === 0) break;
    const linkSet = new Set(allLinks.filter((l) => l.trim()));
    if (linkSet.size === 0) break;
    const candidates = store.filter((e) => !visited.has(e.id) && (e.links ?? []).some((l) => linkSet.has(l)));
    const nextLayer: VectorEntryJson[] = [];
    for (const candidate of candidates) {
      if (result.size >= MAX_EXPANSION_TOTAL) break;
      if (nextLayer.length >= MAX_EXPANSION_PER_DEPTH) break;
      if (visited.has(candidate.id)) continue;
      result.set(candidate.id, candidate);
      visited.add(candidate.id);
      nextLayer.push(candidate);
    }
    if (nextLayer.length === 0) break;
    currentLayer = nextLayer;
    depth++;
  }
  return [...result.values()];
}

async function loadIndex(personaId: string): Promise<LoadedIndex | null> {
  const cached = indexCache.get(personaId);
  if (cached) return cached;
  const stored = await kvGet<StoredIndex>(indexKey(personaId));
  if (!stored || stored.entries.length === 0) return null;
  const embedder = new TfidfEmbedder();
  embedder.buildVocabulary(stored.entries.map((e) => e.text));
  const loaded = { store: stored.entries, embedder, hash: stored.hash };
  indexCache.set(personaId, loaded);
  return loaded;
}

/**
 * 检索角色知识库（对齐 PersonaRagManager.retrieveSync/retrieve）。
 * @param name persona 名称（供日志/UI 展示用，与 Kotlin 参数对齐）
 * @param query 用户查询
 * @param k topK，默认 RagConfig.personaTopK=3
 * @returns 初始命中分块文本 + 树扩展分块文本；未就绪/空查询返回 []
 */
export async function searchRag(
  personaId: string,
  name: string,
  query: string,
  k: number = RAG_DEFAULTS.personaTopK,
): Promise<string[]> {
  void name; // 与 Kotlin 签名对齐，tfidf 模式下无需使用
  if (!query.trim()) return [];
  const indexed = await loadIndex(personaId);
  if (!indexed || !indexed.embedder.isReady()) return [];
  try {
    const queryVec = indexed.embedder.embedSingle(query);
    if (queryVec.length === 0) return [];
    const initial = indexed.store
      .map((entry) => ({ entry, score: cosineSimilarity(queryVec, entry.vector) }))
      .filter((r) => r.score >= RAG_DEFAULTS.minSimilarity)
      .sort((a, b) => b.score - a.score)
      .slice(0, k)
      .map((r) => r.entry);
    if (initial.length === 0) return [];
    if (RAG_DEFAULTS.treeRagEnabled) {
      const expanded = expandByLinks(indexed.store, initial.map((e) => e.id));
      return [...initial.map((e) => e.text), ...expanded.map((e) => e.text)];
    }
    return initial.map((e) => e.text);
  } catch {
    return [];
  }
}

/** 清除某角色的 RAG 缓存与持久索引 */
export async function clearRag(personaId: string): Promise<void> {
  indexCache.delete(personaId);
  await kvSet(indexKey(personaId), { hash: "", entries: [] } satisfies StoredIndex);
}
