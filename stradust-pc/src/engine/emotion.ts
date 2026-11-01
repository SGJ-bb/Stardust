/**
 * 情绪分析与情绪守护 —— 移植自 Android emotion/EmotionAnalyzer.kt、emotion/EmotionGuardian.kt
 * LLM 调用统一走 llmClient.simpleCompletion（对应 Kotlin EmotionAnalyzer 的直连 API 调用）。
 * 提示词逐字对齐 buildEmotionAnalysisPrompt；参数裁剪范围对齐 EmotionParams。
 */

import { getByPersona, kvGet, kvSet, newId, put, remove } from "@/lib/db";
import type { StoredRecord } from "@/lib/db";
import { simpleCompletion } from "./llmClient";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { Emotion, ISO } from "./kinds";

/** 记录条目扩展：除 kinds.EmotionLogEntry 外附带原始情绪名与强度（guardian 趋势计算用） */
interface EmotionLogRecord extends StoredRecord {
  id: string;
  personaId: string;
  emotion: Emotion;
  negativity: number;
  timestamp: ISO;
  trigger?: string;
  /** 原始（未映射）情绪名，如 tsundere/fearful —— 对齐 EmotionGuardian.EmotionRecord.emotion */
  rawEmotion?: string;
  /** 情绪强度 0~1 —— 对齐 EmotionGuardian.EmotionRecord.intensity */
  intensity?: number;
}

// ─── 情绪映射：Kotlin Emotion / tts_emotion 值域 → kinds.ts Emotion ───
const EMOTION_MAP: Record<string, Emotion> = {
  neutral: "neutral",
  happy: "happy",
  sad: "sad",
  angry: "angry",
  surprised: "surprised",
  tsundere: "shy", // Kotlin TSUNDERE/tts"傲娇" → Web 无傲娇位，就近映射害羞
  shy: "shy",
  excited: "excited",
  comfort: "comfort",
  proud: "proud",
  confused: "confused",
  sleepy: "sleepy",
  cry: "cry",
  fearful: "sad", // TTS 九分类中恐惧/厌恶归入低落情绪
  disgusted: "angry",
};

/** 把 Kotlin Emotion 枚举名或 tts_emotion 原始串映射为 kinds.ts 的 Emotion */
export function mapEmotion(raw: string): Emotion {
  return EMOTION_MAP[raw.trim().toLowerCase()] ?? "neutral";
}

// ─── 分析结果（对齐 EmotionParams + 附加映射后的 emotion） ───
export interface EmotionAnalysisResult {
  /** 映射到 kinds.ts Emotion 的结果 */
  emotion: Emotion;
  /** 建议温度（base + temperatureOffset 裁剪到 0~2；未启用时缺省） */
  temperature?: number;
  /** 建议 Top-P（base + topPOffset 裁剪到 0~1） */
  topP?: number;
  /** 以下为 Kotlin EmotionParams 原始偏移量 */
  temperatureOffset: number;
  topPOffset: number;
  ttsPitchOffset: number;
  ttsRateOffset: number;
  emotionIntensity: number;
  ttsEmotion: string;
  ttsEmotionConfidence: number;
}

const VALID_TTS_EMOTIONS = new Set(["happy", "sad", "angry", "surprised", "fearful", "disgusted", "tsundere", "shy", "neutral"]);

function defaultParams(): EmotionAnalysisResult {
  return {
    emotion: "neutral",
    temperatureOffset: 0,
    topPOffset: 0,
    ttsPitchOffset: 0,
    ttsRateOffset: 0,
    emotionIntensity: 0,
    ttsEmotion: "neutral",
    ttsEmotionConfidence: 0.5,
  };
}

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** 情绪分析提示词（逐字对齐 EmotionAnalyzer.buildEmotionAnalysisPrompt） */
function buildEmotionAnalysisPrompt(personaName: string, personaPrompt: string, currentEmotion: string): string {
  return (
    "你是情绪参数分析器。根据角色设定、当前情绪和对话内容，分析角色此刻的情绪强度，并返回LLM和TTS参数偏移值。\n\n" +
    `角色名：${personaName}\n` +
    `角色设定摘要：${personaPrompt.slice(0, 300)}\n` +
    `当前情绪：${currentEmotion}\n\n` +
    "参数说明：\n" +
    "- temperature_offset：温度偏移（-0.5~1.0）。情绪越激烈/不稳定，值越大。生气/激动时>0.3，平静时≈0\n" +
    "- top_p_offset：Top-P偏移（-0.2~0.1）。情绪激烈时略微降低使输出更集中\n" +
    "- tts_pitch_offset：语音声调偏移（-0.3~0.5）。开心/激动时升高，悲伤时降低\n" +
    "- tts_rate_offset：语速偏移（-0.2~0.3）。急切/激动时加快，悲伤时减慢\n" +
    "- emotion_intensity：情绪强度（0~1）。0=平静，1=极度激烈\n" +
    "- tts_emotion：TTS情绪标签。可选值：happy(开心/快乐)、sad(悲伤/失落)、angry(生气/愤怒)、surprised(惊讶/震惊)、fearful(恐惧/害怕)、disgusted(厌恶/反感)、tsundere(傲娇/口是心非)、shy(害羞/腼腆)、neutral(平静/中性)\n" +
    "- tts_emotion_confidence：情绪标签置信度（0~1）\n\n" +
    "严格返回JSON格式，不要其他内容：\n" +
    '{"temperature_offset":0.0,"top_p_offset":0.0,"tts_pitch_offset":0.0,"tts_rate_offset":0.0,"emotion_intensity":0.0,"tts_emotion":"neutral","tts_emotion_confidence":0.5}'
  );
}

/** 宽容解析 JSON 对象（对齐 parseEmotionParams 的杂质剥离 + 各字段 coerceIn 范围） */
function parseEmotionParams(text: string): EmotionAnalysisResult {
  try {
    let cleaned = text.trim().replace(/^```\s*json\s*/i, "").replace(/```$/, "").trim();
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start < 0 || end < 0) return defaultParams();
    cleaned = cleaned.slice(start, end + 1);
    const json = JSON.parse(cleaned) as Record<string, unknown>;
    const num = (key: string, dflt: number) => {
      const v = Number(json[key]);
      return Number.isFinite(v) ? v : dflt;
    };
    const rawEmotion = String(json.tts_emotion ?? "neutral").toLowerCase();
    const ttsEmotion = VALID_TTS_EMOTIONS.has(rawEmotion) ? rawEmotion : "neutral";
    return {
      emotion: mapEmotion(ttsEmotion),
      temperatureOffset: clamp(num("temperature_offset", 0), -0.5, 1.0),
      topPOffset: clamp(num("top_p_offset", 0), -0.2, 0.1),
      ttsPitchOffset: clamp(num("tts_pitch_offset", 0), -0.3, 0.5),
      ttsRateOffset: clamp(num("tts_rate_offset", 0), -0.2, 0.3),
      emotionIntensity: clamp(num("emotion_intensity", 0), 0, 1),
      ttsEmotion,
      ttsEmotionConfidence: clamp(num("tts_emotion_confidence", 0.5), 0, 1),
    };
  } catch {
    return defaultParams();
  }
}

/**
 * LLM 情绪分析（对齐 ChatViewModel.prepareRequest 中的调用方式 + EmotionAnalyzer.analyzeEmotion）。
 * 尊重 settings.aiFeatures.emotionAnalysisEnabled 开关；关闭或失败时返回默认零偏移（同 Kotlin）。
 * chatHistory 项：[isUser, text]，取最近 6 条拼进用户消息（对齐 Kotlin）。
 */
export async function analyzeEmotion(
  userText: string,
  opts?: {
    personaName?: string;
    personaPrompt?: string;
    chatHistory?: [boolean, string][];
    currentEmotion?: string;
  },
): Promise<EmotionAnalysisResult> {
  const settings = useSettingsStore.getState().settings;
  const enabled = settings.aiFeatures?.emotionAnalysisEnabled ?? false;
  const baseTemp = settings.chatParams?.temperature ?? 1.05;
  const baseTopP = settings.chatParams?.topP ?? 0.92;
  if (!enabled) return defaultParams();

  const personaName = opts?.personaName ?? "星尘";
  try {
    const system = buildEmotionAnalysisPrompt(personaName, opts?.personaPrompt ?? "", opts?.currentEmotion ?? "neutral");
    let userContent: string;
    if (opts?.chatHistory?.length) {
      const historyText = opts.chatHistory
        .slice(-6)
        .map(([isUser, text]) => (isUser ? `用户：${text}` : `${personaName}：${text}`))
        .join("\n");
      userContent = `最近的对话：\n${historyText}\n\n用户最新消息：${userText}`;
    } else {
      userContent = `用户说：${userText}`;
    }
    const text = await simpleCompletion(userContent, { system, temperature: 0.3, maxTokens: 200 });
    const result = parseEmotionParams(text);
    // 应用偏移到基础采样参数（对齐 applyToTemperature / applyToTopP）
    result.temperature = clamp(baseTemp + result.temperatureOffset, 0, 2);
    result.topP = clamp(baseTopP + result.topPOffset, 0, 1);
    return result;
  } catch {
    return defaultParams();
  }
}

// ═══════════════════════ EmotionGuardian（情绪守护，per-persona 持久化到 kv） ═══════════════════════

const MAX_RECORDS = 100; // Guardian.MAX_RECORDS
const CARE_COOLDOWN_MS = 4 * 3600 * 1000; // CARE_COOLDOWN_MS

export type EmotionTrend = "VERY_NEGATIVE" | "NEGATIVE" | "NEUTRAL" | "POSITIVE";

/** 负向/正向情绪集合（对齐 getRecentTrend 的 when 分支，含 Web 扩展情绪） */
const NEGATIVE = new Set(["sad", "angry", "fearful", "disgusted", "cry", "comfort", "sleepy"]);
const POSITIVE = new Set(["happy", "excited", "tsundere", "shy", "proud"]);

/**
 * 记录一次情绪：写 emotionLog 集合（含 rawEmotion/intensity 扩展字段，趋势计算直接读该集合）。
 * 超过 100 条裁剪最旧（对齐 recordEmotion 的 MAX_RECORDS）。
 */
export async function recordEmotion(personaId: string, emotion: Emotion | string, intensity = 0.5, source?: string): Promise<void> {
  const raw = String(emotion).trim().toLowerCase();
  const entry: EmotionLogRecord = {
    id: newId("emo"),
    personaId,
    emotion: mapEmotion(raw),
    negativity: NEGATIVE.has(raw) ? clamp(intensity, 0, 1) : 0,
    timestamp: Date.now(),
    trigger: source,
    rawEmotion: raw,
    intensity: clamp(intensity, 0, 1),
  };
  await put("emotionLog", entry);
  const logs = await getByPersona<EmotionLogRecord>("emotionLog", personaId);
  if (logs.length > MAX_RECORDS) {
    const sorted = logs.sort((a, b) => a.timestamp - b.timestamp);
    for (const stale of sorted.slice(0, logs.length - MAX_RECORDS)) await remove("emotionLog", stale.id);
  }
}

/** 近期情绪趋势（对齐 getRecentTrend：负面占比/平均强度判定四档；优先用 rawEmotion，回退映射值） */
export async function getRecentTrend(personaId: string, hours = 24): Promise<EmotionTrend> {
  const cutoff = Date.now() - hours * 3600 * 1000;
  const recent = (await getByPersona<EmotionLogRecord>("emotionLog", personaId)).filter((r) => r.timestamp > cutoff);
  if (recent.length === 0) return "NEUTRAL";
  let negativeCount = 0;
  let positiveCount = 0;
  let totalIntensity = 0;
  for (const r of recent) {
    totalIntensity += typeof r.intensity === "number" ? r.intensity : r.negativity || 0.5;
    const e = (r.rawEmotion ?? r.emotion).toLowerCase();
    if (["sad", "angry", "fearful", "disgusted"].includes(e)) negativeCount++;
    else if (["happy", "excited", "tsundere", "shy"].includes(e)) positiveCount++;
  }
  const negativeRatio = negativeCount / recent.length;
  const avgIntensity = totalIntensity / recent.length;
  const posRatio = positiveCount / recent.length;
  if (negativeRatio > 0.6 && avgIntensity > 0.5) return "VERY_NEGATIVE";
  if (negativeRatio > 0.4) return "NEGATIVE";
  if (posRatio > 0.6) return "POSITIVE";
  return "NEUTRAL";
}

/** 是否应发送关怀消息（4小时冷却 + 趋势为负向——对齐 shouldSendCare） */
export async function shouldShowCare(personaId: string): Promise<boolean> {
  const lastCare = (await kvGet<number>(`care:${personaId}`)) ?? 0;
  if (Date.now() - lastCare < CARE_COOLDOWN_MS) return false;
  const trend = await getRecentTrend(personaId);
  return trend === "NEGATIVE" || trend === "VERY_NEGATIVE";
}

/** 标记已发送关怀（重置冷却时间戳——对齐 markCareSent） */
export async function markCareSent(personaId: string): Promise<void> {
  await kvSet(`care:${personaId}`, Date.now());
}

/** 关怀文案（三组文案逐字对齐 getCareMessage，随机取一条） */
export async function getCareMessage(personaId: string): Promise<string> {
  const trend = await getRecentTrend(personaId);
  const messages =
    trend === "VERY_NEGATIVE"
      ? [
          "你最近好像不太开心...要不要和我聊聊？我一直在的。",
          "我注意到你心情不太好，要不要听首歌放松一下？",
          "不管发生什么，我都会陪着你。想说话的时候随时找我。",
        ]
      : trend === "NEGATIVE"
        ? ["今天还好吗？如果累了就休息一下吧~", "感觉你最近有点疲惫，记得照顾好自己哦。", "要不要聊聊天？我随时都在~"]
        : ["嘿~突然想跟你说一声，有你真好！", "今天也要开开心心的哦~"];
  return messages[Math.floor(Math.random() * messages.length)];
}
