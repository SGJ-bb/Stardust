/**
 * 语音引擎（TTS + ASR）—— 移植自 Android voice/TtsManager.kt、voice/VoiceManager.kt、
 * voice/LocalAsrManager.kt(callCloudAsr) 与 network/ProviderAdapter.kt 的 TTS/ASR 部分。
 *
 * Web 等价替代说明：
 * - "web" 引擎 = 浏览器 speechSynthesis（对应 Android 系统 TextToSpeech）。
 * - "cloud" 引擎 = OpenAI 兼容 / siliconflow / aliyun / fish_audio 云端合成，
 *   请求体/头/响应解析忠实照搬 ProviderAdapter.buildTtsRequest/buildTtsHeaders 与
 *   TtsManager.cloudSynthesize/parseResponse（含 422 时 voice→voice_name 重试、
 *   siliconflow CosyVoice 音色拼接规则）。
 * - "auto" = cloud 已配置则 cloud，否则 web（对齐 ENGINE_AUTO 语义）。
 * - **edge 模式在 Web 不可行 → 回退链 edge→web**：Android EdgeTtsEngine 走
 *   wss://speech.platform.bing.com WebSocket 且依赖 TrustedClientToken 签名，浏览器受
 *   CORS/私有协议限制无法直连，故 settings.voice.engine==="edge-tts"（及 vits/gpt-sovits/
 *   local 等本地模型引擎）统一回退为 web speechSynthesis。
 * - 情绪变调：照搬 VoiceManager.speakInternal 的 emotion→pitch/rate delta 表；
 *   web 引擎作用于 utterance.pitch/rate，cloud 引擎作用于 audio.playbackRate
 *   （Kotlin MediaPlayer 不支持变速，此为 Web 侧等价扩展）。
 */

import { useSettingsStore } from "@/stores/useSettingsStore";
import type { ServiceEndpointConfig } from "@/types/settings";
import { TTS_PRESETS, ASR_PRESETS, EDGE_VOICES, findPreset, type ServicePreset } from "./presets";
import type { Emotion } from "./kinds";

// ─── SpeechRecognition 类型声明（TS DOM lib 未内置；Web 等价于 Android SpeechRecognizer） ───
interface SRAlternativeLike { transcript: string; confidence?: number }
interface SRResultLike { isFinal: boolean; length: number; [index: number]: SRAlternativeLike }
interface SREventLike { resultIndex: number; results: { length: number; [index: number]: SRResultLike } }
interface SRSpeechError extends Error { error?: string }
interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((e: SREventLike) => void) | null;
  onerror: ((e: SRSpeechError) => void) | null;
  onend: (() => void) | null;
}
type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  }
}

// ═══════════════════════ TTS：设置读取（全部兜底） ═══════════════════════

/** 生效的语音配置（engine 归一化后为 "web"|"cloud"|"auto"） */
export interface VoiceOptions {
  engine?: "web" | "cloud" | "auto" | "edge-tts";
  voiceId?: string;
  speed?: number;
  pitch?: number;
  volume?: number;
  autoPlay?: boolean;
  /** 覆盖云端 TTS 端点（默认 settings.voice.cloud，缺失时按 voiceId 猜测 preset） */
  cloud?: ServiceEndpointConfig;
  /** 覆盖云端音色名 / fish_audio reference_id */
  voice?: string;
  /** 情绪（用于 web/cloud 变调参数） */
  emotion?: Emotion;
}

interface ResolvedVoice {
  engine: "web" | "cloud" | "auto" | "edge-tts";
  voiceId: string;
  speed: number;
  pitch: number;
  volume: number;
  autoPlay: boolean;
  cloud: ServiceEndpointConfig | null;
}

/** voiceId 不是端点配置时，按 id 猜测一个云端 TTS 预设（edge 神经音色名除外） */
function defaultCloudFromVoiceId(voiceId: string): ServiceEndpointConfig | null {
  // edge 神经音色名（如 zh-CN-XiaoxiaoNeural）→ 浏览器无法直连 edge，返回 null 走 web
  if (/Neural$/.test(voiceId)) return null;
  const preset = findPreset(TTS_PRESETS, voiceId);
  if (preset) {
    return { presetId: preset.id, baseUrl: preset.url, apiKey: "", model: preset.defaultModel };
  }
  // 非已知 preset 的 voiceId → 视为 fish_audio 的 reference_id（Fish Audio 用音色 id 作 voice）
  return { presetId: "fish_audio", baseUrl: "https://api.fish.audio/v1/tts", apiKey: "", model: "s2-pro" };
}

function resolveVoice(opts?: VoiceOptions): ResolvedVoice {
  const v = useSettingsStore.getState().settings.voice;
  let engine = (opts?.engine ?? v?.engine ?? "auto") as ResolvedVoice["engine"];
  // edge→web 回退链：edge-tts 在 Web 环境不可行（见文件头注释），vits/gpt-sovits/local 同理
  if (engine === "edge-tts" || (engine as string) === "vits" || (engine as string) === "gpt-sovits" || (engine as string) === "local") {
    engine = "web";
  }
  const voiceId = opts?.voiceId ?? v?.voiceId ?? "zh-CN-XiaoxiaoNeural";
  const cloud =
    opts?.cloud ??
    v?.cloud ??
    (engine === "cloud" || engine === "auto" ? defaultCloudFromVoiceId(voiceId) : null);
  return {
    engine,
    voiceId,
    speed: opts?.speed ?? v?.speed ?? 1.0,
    pitch: opts?.pitch ?? v?.pitch ?? 1.0,
    volume: opts?.volume ?? v?.volume ?? 1.0,
    autoPlay: opts?.autoPlay ?? v?.autoPlay ?? true,
    cloud,
  };
}

/** cloud 是否真正可用（有 URL；URL 指向本机服务时浏览器可免 Key 直连） */
function cloudUsable(cloud: ServiceEndpointConfig | null): boolean {
  if (!cloud?.baseUrl) return false;
  if (cloud.apiKey) return true;
  try {
    const host = new URL(cloud.baseUrl).hostname;
    return host === "localhost" || host === "127.0.0.1" || host === "::1";
  } catch {
    return false;
  }
}

function ttsFormat(cloud: ServiceEndpointConfig): string | undefined {
  const preset = findPreset(TTS_PRESETS, cloud.presetId);
  return preset?.formatType ?? guessFormatFromUrl(cloud.baseUrl);
}

function guessFormatFromUrl(url: string): "fish_audio" | undefined {
  if (/fish\.audio/i.test(url)) return "fish_audio";
  return undefined;
}

// ═══════════════════════ 情绪 → 音调/语速映射（照搬 VoiceManager.speakInternal） ═══════════════════════

const EMOTION_PITCH_DELTA: Partial<Record<Emotion, number>> = {
  happy: 0.2,
  sad: -0.2,
  angry: 0.1,
  surprised: 0.3,
};
const EMOTION_RATE_DELTA: Partial<Record<Emotion, number>> = {
  happy: 0.1,
  sad: -0.15,
  angry: 0.15,
  surprised: 0.1,
};
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** 过滤括号动作描述，避免读出（微笑）*叹气*【思考】等内容（照搬 VoiceManager.filterActionText） */
export function filterActionText(text: string): string {
  return text
    .replace(/（[^）]*）/g, "")
    .replace(/\([^)]*\)/g, "")
    .replace(/\*[^*]*\*/g, "")
    .replace(/\[[^\]]*\]/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

// ═══════════════════════ 播放器（module 级单例，对齐 MediaPlayer 行为） ═══════════════════════

let currentAudio: HTMLAudioElement | null = null;
/** 最近创建的 objectURL，播放结束/停止时回收 */
let lastObjectUrl: string | null = null;
let playbackListener: (() => void) | null = null;
/** 下一次播放应用的变速（云端合成的情绪变调），播放后清零 */
let pendingPlaybackRate = 0;

/** 注册播放完成回调（对齐 setOnPlaybackCompleteListener） */
export function setPlaybackListener(fn: (() => void) | null): void {
  playbackListener = fn;
}

function revokeLastUrl(): void {
  if (lastObjectUrl) {
    try { URL.revokeObjectURL(lastObjectUrl); } catch { /* ignore */ }
    lastObjectUrl = null;
  }
}

function fireListener(): void {
  const l = playbackListener;
  playbackListener = null;
  l?.();
}

/** 播放音频 URL（objectURL/http 均可），替代 Android playAudio(path,url) */
export function playAudioUrl(url: string, onComplete?: () => void): void {
  stopPlayback();
  playbackListener = onComplete ?? null;
  try {
    const audio = new Audio(url);
    if (pendingPlaybackRate) {
      audio.playbackRate = pendingPlaybackRate;
      pendingPlaybackRate = 0;
    }
    audio.onended = () => {
      currentAudio = null;
      revokeLastUrl();
      fireListener();
    };
    audio.onerror = () => {
      currentAudio = null;
      revokeLastUrl();
      fireListener();
    };
    void audio.play().catch(() => {
      // 自动播放策略拦截时静默失败（浏览器要求用户手势）
      currentAudio = null;
      revokeLastUrl();
    });
    currentAudio = audio;
  } catch {
    playbackListener = null;
  }
}

export function stopPlayback(): void {
  try {
    if (currentAudio) {
      currentAudio.pause();
      currentAudio.onended = null;
      currentAudio.onerror = null;
      currentAudio.src = "";
    }
  } catch { /* ignore */ }
  currentAudio = null;
  revokeLastUrl();
  fireListener();
}

export function isPlaying(): boolean {
  return !!currentAudio && !currentAudio.paused && !currentAudio.ended;
}

// ═══════════════════════ web 引擎：speechSynthesis ═══════════════════════

/** voiceId → 本地 voice 名称片段映射（edge 神经音色名 → Chrome 本地近似中文 voice） */
const VOICE_NAME_HINTS: Record<string, string[]> = {
  "zh-CN-XiaoxiaoNeural": ["xiaoxiao", "晓晓", "xiaohui", "yunxi"],
  "zh-CN-XiaoyiNeural": ["xiaoyi", "晓伊", "xiaoxue"],
  "zh-CN-YunxiNeural": ["yunxi", "云希"],
  "zh-CN-YunjianNeural": ["yunjian", "云健"],
  "zh-CN-YunxiaNeural": ["yunxia", "云霞"],
  "zh-CN-YunyangNeural": ["yunyang", "云扬"],
};

function pickWebVoice(voiceId: string): SpeechSynthesisVoice | null {
  if (typeof speechSynthesis === "undefined") return null;
  const voices = speechSynthesis.getVoices();
  if (!voices.length) return null;
  const lower = voiceId.toLowerCase();
  // 1) 直接同名匹配（浏览器装有 edge 语音包时）
  const exact = voices.find((vo) => vo.name.toLowerCase() === lower);
  if (exact) return exact;
  // 2) edge 音色名映射到近似本地 voice
  if (EDGE_VOICES.includes(voiceId)) {
    const hints = VOICE_NAME_HINTS[voiceId] ?? [];
    for (const h of hints) {
      const v = voices.find((vo) => vo.name.toLowerCase().includes(h));
      if (v) return v;
    }
  }
  // 3) 语言前缀匹配（zh-CN → 任意 zh voice）
  const langPrefix = voiceId.split("-")[0].toLowerCase();
  return (
    voices.find((vo) => vo.lang.toLowerCase().startsWith(langPrefix)) ??
    voices.find((vo) => vo.default) ??
    voices[0] ??
    null
  );
}

function speakViaWebUtterance(text: string, rv: ResolvedVoice, emotion?: Emotion): void {
  try {
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    const voice = pickWebVoice(rv.voiceId);
    if (voice) {
      u.voice = voice;
      u.lang = voice.lang;
    } else {
      u.lang = rv.voiceId.includes("-") ? rv.voiceId : "zh-CN";
    }
    // Kotlin: pitch=(basePitch+delta).coerceIn(0.5,2.0)，speechSynthesis 同域 [0.5,2]
    u.pitch = clamp(rv.pitch + (emotion ? (EMOTION_PITCH_DELTA[emotion] ?? 0) : 0), 0.5, 2.0);
    // Kotlin: rate=(baseRate+delta).coerceIn(0.5,2.0)
    u.rate = clamp(rv.speed + (emotion ? (EMOTION_RATE_DELTA[emotion] ?? 0) : 0), 0.5, 2.0);
    u.volume = clamp(rv.volume, 0, 1);
    speechSynthesis.speak(u);
  } catch {
    /* speechSynthesis 不可用时静默 */
  }
}

// ═══════════════════════ cloud 引擎（忠实移植 TtsManager.cloudSynthesize） ═══════════════════════

/** 照搬 ProviderAdapter.buildTtsRequest（openai 通用格式 + fish_audio 分支；mimo 不移植） */
function buildTtsRequest(formatType: string | undefined, model: string, text: string, voice: string): Record<string, unknown> {
  if (formatType === "fish_audio") {
    const body: Record<string, unknown> = { text, format: "mp3" };
    if (voice.trim()) body.reference_id = voice;
    return body;
  }
  return { model, input: text, voice };
}

/** 照搬 ProviderAdapter.buildTtsHeaders */
function buildTtsHeaders(cloud: ServiceEndpointConfig): Record<string, string> {
  return { Authorization: `Bearer ${cloud.apiKey}`, "Content-Type": "application/json" };
}

/** siliconflow CosyVoice 音色拼接规则（照搬 TtsManager.cloudSynthesize） */
function normalizeSiliconFlowVoice(url: string, model: string, voice: string): string {
  const isSiliconFlow = /siliconflow/i.test(url);
  const isCosyVoice = /cosyvoice/i.test(model);
  if (isSiliconFlow && isCosyVoice && !voice.includes(":")) {
    const cosyVoiceNames = new Set(["alex", "anna", "bella", "benjamin", "charles", "claire", "david", "diana"]);
    const name = voice.toLowerCase();
    return cosyVoiceNames.has(name) ? `${model}:${name}` : `${model}:alex`;
  }
  return voice;
}

function b64ToBlob(b64: string, mime: string): Blob {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

async function blobToObjectUrl(blob: Blob): Promise<string | null> {
  try {
    const url = URL.createObjectURL(blob);
    lastObjectUrl = url;
    return url;
  } catch {
    return null;
  }
}

/** 照搬 TtsManager.parseResponse：二进制 audio/octet-stream 优先，再 JSON url/audio/data[] */
async function parseTtsResponse(res: Response): Promise<string | null> {
  const contentType = res.headers.get("Content-Type") ?? "";
  if (contentType.includes("audio") || contentType.includes("octet-stream")) {
    const blob = await res.blob();
    return blobToObjectUrl(blob);
  }
  const bodyStr = await res.text();
  try {
    const json = JSON.parse(bodyStr) as Record<string, unknown>;
    const audioUrl = typeof json.url === "string" ? json.url : "";
    if (audioUrl.trim()) return audioUrl;
    const audioB64 = typeof json.audio === "string" ? json.audio : "";
    if (audioB64.trim()) return blobToObjectUrl(b64ToBlob(audioB64, "audio/mpeg"));
    const dataArr = Array.isArray(json.data) ? (json.data as Record<string, unknown>[]) : null;
    const first = dataArr?.[0];
    if (first) {
      const dataUrl = typeof first.url === "string" ? first.url : "";
      if (dataUrl.trim()) return dataUrl;
      const b64 = typeof first.b64_json === "string" ? first.b64_json : "";
      if (b64.trim()) return blobToObjectUrl(b64ToBlob(b64, "audio/mpeg"));
    }
  } catch { /* 非 JSON 视为无音频 */ }
  return null;
}

/** 解析本次合成使用的音色名（openai 系：voice 字段；fish_audio：reference_id） */
function resolveTtsVoice(cloud: ServiceEndpointConfig, formatType: string | undefined, overrideVoice?: string): string {
  const rv = resolveVoice({});
  if (formatType === "fish_audio") {
    // fish_audio 的 voice 是音色 reference_id：显式覆盖 > 非端点型 voiceId（edge 神经名除外）
    if (overrideVoice?.trim()) return overrideVoice.trim();
    if (rv.voiceId && !/Neural$/.test(rv.voiceId) && !findPreset(TTS_PRESETS, rv.voiceId)) return rv.voiceId;
    return "";
  }
  // openai/siliconflow/aliyun：Android 用 ttsVoiceName.ifBlank{ttsVoice}，Web 用 voiceId
  const raw = overrideVoice?.trim() || rv.voiceId || cloud.model || "alloy";
  return normalizeSiliconFlowVoice(cloud.baseUrl, cloud.model, raw);
}

async function cloudSynthesize(text: string, cloud: ServiceEndpointConfig, overrideVoice?: string, emotion?: Emotion): Promise<string | null> {
  const formatType = ttsFormat(cloud);
  const voice = resolveTtsVoice(cloud, formatType, overrideVoice);
  const headers = buildTtsHeaders(cloud);
  let body = buildTtsRequest(formatType, cloud.model, text, voice);
  let res = await fetchWithTimeout(cloud.baseUrl, { method: "POST", headers, body: JSON.stringify(body) });

  // 422 重试：voice → voice_name（照搬 TtsManager）
  if (res.status === 422 && formatType !== "fish_audio") {
    body = { ...body };
    if ("voice" in body) {
      body.voice_name = body.voice;
      delete body.voice;
    }
    res = await fetchWithTimeout(cloud.baseUrl, { method: "POST", headers, body: JSON.stringify(body) });
  }
  if (!res.ok) {
    const errBody = await res.text().catch(() => "");
    throw new Error(`HTTP ${res.status}: ${errBody.slice(0, 300)}`);
  }
  const url = await parseTtsResponse(res);
  if (url && emotion) {
    const delta = EMOTION_RATE_DELTA[emotion] ?? 0;
    if (delta) pendingPlaybackRate = clamp(1.0 + delta, 0.5, 2.0);
  }
  return url;
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs = 60000): Promise<Response> {
  return fetch(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
}

// ═══════════════════════ 对外 API ═══════════════════════

/**
 * 合成语音，返回可播放 URL：
 * - cloud 成功 → objectURL(blob) 或远程 URL
 * - web 引擎 → 直接用 speechSynthesis 播放并返回 null（对齐 Android LOCAL/AUTO 回退行为）
 * - 失败 → null
 */
export async function synthesize(text: string, opts?: VoiceOptions): Promise<string | null> {
  if (!text.trim()) return null;
  const clean = filterActionText(text);
  if (!clean) return null;
  const emotion = opts?.emotion;
  const rv = resolveVoice(opts);

  // 决定实际引擎（auto: cloud 已配置则 cloud，否则 web —— 对齐 ENGINE_AUTO）
  let engine = rv.engine;
  if (engine === "auto") engine = cloudUsable(rv.cloud) ? "cloud" : "web";

  if (engine === "web") {
    speakViaWebUtterance(clean, rv, emotion);
    return null;
  }

  try {
    const url = await cloudSynthesize(clean, rv.cloud!, opts?.voice, emotion);
    if (url) return url;
    // 云端无音频产出：auto 语义下回退 web
    speakViaWebUtterance(clean, rv, emotion);
    return null;
  } catch {
    // 云端失败回退 web（对齐 TtsManager ENGINE_AUTO 回退；显式 cloud 模式也回退以保可用性）
    speakViaWebUtterance(clean, rv, emotion);
    return null;
  }
}

/**
 * 一站式朗读：合成 + 播放。遵循 settings.voice.autoPlay
 * （settings.aiFeatures 无独立 TTS 总开关字段，以 voice.autoPlay 为准，缺省视为开）。
 */
export async function speak(text: string, opts?: VoiceOptions): Promise<void> {
  const rv = resolveVoice(opts);
  const clean = filterActionText(text);
  if (!clean) return;
  if (!rv.autoPlay) return;
  const url = await synthesize(clean, opts);
  if (url) playAudioUrl(url);
  // url===null 时 synthesize 已通过 speechSynthesis 直接播放
}

/** 停止所有播放（speechSynthesis + 音频元素） */
export function stopSpeaking(): void {
  try { speechSynthesis?.cancel(); } catch { /* ignore */ }
  stopPlayback();
}

/** TTS 是否启用（settings.voice.autoPlay 作为开关，undefined 兜底为 true） */
export function isTTSEnabled(): boolean {
  return resolveVoice().autoPlay !== false;
}

// ═══════════════════════ ASR ═══════════════════════

export interface AsrHandlers {
  onPartial: (text: string) => void;
  onFinal: (text: string) => void;
  onError: (err: string) => void;
  onEnd: () => void;
}

export interface AsrSession {
  stop(): void;
  abort(): void;
}

/** 浏览器原生语音识别（window.SpeechRecognition/webkitSpeechRecognition） */
export function startWebAsr(handlers: AsrHandlers, lang?: string): AsrSession {
  const Ctor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  if (!Ctor) {
    handlers.onError("当前浏览器不支持语音识别");
    handlers.onEnd();
    return { stop() {}, abort() {} };
  }
  const rec = new Ctor();
  const fallbackLang = useSettingsStore.getState().settings.asr?.language ?? "zh-CN";
  rec.lang = lang ?? fallbackLang;
  rec.continuous = false;
  rec.interimResults = true;
  rec.maxAlternatives = 1;
  let finalText = "";
  let ended = false;

  rec.onresult = (e: SREventLike) => {
    let interim = "";
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const r = e.results[i];
      const t = r[0]?.transcript ?? "";
      if (r.isFinal) finalText += t;
      else interim += t;
    }
    if (interim) handlers.onPartial(interim);
  };
  rec.onerror = (e: SRSpeechError) => {
    const code = e.error ?? "unknown";
    handlers.onError(
      code === "no-speech" ? "未检测到语音"
        : code === "not-allowed" ? "麦克风权限被拒绝"
        : `识别错误: ${code}`,
    );
  };
  rec.onend = () => {
    if (ended) return;
    ended = true;
    if (finalText.trim()) handlers.onFinal(finalText.trim());
    handlers.onEnd();
  };

  try {
    rec.start();
  } catch (e) {
    handlers.onError((e as Error).message || "启动识别失败");
  }
  return {
    stop() { try { rec.stop(); } catch { /* ignore */ } },
    abort() { try { rec.abort(); } catch { /* ignore */ } },
  };
}

function getAsrCloud(): ServiceEndpointConfig | null {
  const a = useSettingsStore.getState().settings.asr;
  if (a?.cloud?.baseUrl) return a.cloud;
  return null;
}

/** 照搬 ProviderAdapter.buildAsrRequestFields */
function buildAsrFields(model: string): Record<string, string> {
  const fields: Record<string, string> = { model };
  if (!/sense/i.test(model)) fields.language = "zh";
  return fields;
}

/** 照搬 ProviderAdapter.parseAsrResponse：{text} 或 choices[0].message.content */
function parseAsrResponse(body: string): string {
  try {
    const json = JSON.parse(body) as Record<string, unknown>;
    const text = typeof json.text === "string" ? json.text : "";
    if (text.trim()) return text;
    const choices = Array.isArray(json.choices) ? (json.choices as Record<string, unknown>[]) : null;
    const content = (choices?.[0]?.message as Record<string, unknown> | undefined)?.content;
    return typeof content === "string" ? content : "";
  } catch {
    return "";
  }
}

/**
 * 云端 ASR（multipart 上传，忠实移植 LocalAsrManager.callCloudAsr）。
 * 浏览器 FormData 自动生成 multipart boundary，无需手写分隔符。
 * 注：Android 的 aliyun 异步轮询 pollAliyunTask 仅存在于图片生成路径，ASR 无轮询，故不适用。
 */
export async function cloudAsr(blob: Blob, filename?: string): Promise<string> {
  const cloud = getAsrCloud();
  if (!cloud) throw new Error("未配置云端 ASR 服务");
  const model = cloud.model || "whisper-1";
  const form = new FormData();
  form.append("file", blob, filename ?? "audio.wav");
  const fields = buildAsrFields(model);
  for (const [k, v] of Object.entries(fields)) form.append(k, v);
  const res = await fetchWithTimeout(cloud.baseUrl, {
    method: "POST",
    headers: { Authorization: `Bearer ${cloud.apiKey}` },
    body: form,
  }, 30000);
  if (!res.ok) {
    const errBody = await res.text().catch(() => "");
    throw new Error(`ASR 请求失败 HTTP ${res.status}: ${errBody.slice(0, 200)}`);
  }
  return parseAsrResponse(await res.text());
}

/** 列出云端 TTS 服务预设（含各厂商默认音色；浏览器无法枚举厂商实时音色列表） */
export function listCloudVoices(): ServicePreset[] {
  return TTS_PRESETS;
}
