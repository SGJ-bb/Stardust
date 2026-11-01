/**
 * 服务预设与厂商参数档案 —— 移植自 Android settings/ServicePreset.kt + ProviderProfile.kt
 */

export interface LlmPreset {
  id: string;
  displayName: string;
  url: string;
  defaultModel: string;
}

export interface ServicePreset {
  id: string;
  displayName: string;
  url: string;
  defaultModel: string;
  defaultVoice?: string;
  formatType?: "openai" | "siliconflow" | "aliyun_async" | "fish_audio";
}

// ─── LLM ───
export const LLM_PRESETS: LlmPreset[] = [
  { id: "custom", displayName: "自定义", url: "", defaultModel: "" },
  { id: "openai", displayName: "OpenAI", url: "https://api.openai.com/v1/chat/completions", defaultModel: "gpt-4o-mini" },
  { id: "aliyun", displayName: "阿里云百炼", url: "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", defaultModel: "qwen-plus" },
  { id: "zhipu", displayName: "智谱AI", url: "https://open.bigmodel.cn/api/paas/v4/chat/completions", defaultModel: "glm-4-flash" },
  { id: "minimax", displayName: "MiniMax", url: "https://api.minimax.chat/v1/text/chatcompletion_v2", defaultModel: "MiniMax-Text-01" },
  { id: "moonshot", displayName: "月之暗面", url: "https://api.moonshot.cn/v1/chat/completions", defaultModel: "moonshot-v1-8k" },
  { id: "deepseek", displayName: "DeepSeek", url: "https://api.deepseek.com/v1/chat/completions", defaultModel: "deepseek-chat" },
  { id: "siliconflow", displayName: "硅基流动", url: "https://api.siliconflow.cn/v1/chat/completions", defaultModel: "Qwen/Qwen2.5-7B-Instruct" },
  { id: "openrouter", displayName: "OpenRouter", url: "https://openrouter.ai/api/v1/chat/completions", defaultModel: "google/gemini-2.0-flash-001" },
  { id: "qwen", displayName: "通义千问", url: "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", defaultModel: "qwen-max" },
  { id: "n1n", displayName: "n1n", url: "https://api.n1n.ai/v1/chat/completions", defaultModel: "gpt-4o-mini" },
  { id: "nvidia", displayName: "NVIDIA NIM", url: "https://integrate.api.nvidia.com/v1/chat/completions", defaultModel: "meta/llama-3.3-70b-instruct" },
];

// ─── TTS ───
export const TTS_PRESETS: ServicePreset[] = [
  { id: "openai", displayName: "OpenAI", url: "https://api.openai.com/v1/audio/speech", defaultModel: "tts-1", defaultVoice: "alloy" },
  { id: "siliconflow", displayName: "硅基流动", url: "https://api.siliconflow.cn/v1/audio/speech", defaultModel: "FunAudioLLM/CosyVoice2-0.5B", defaultVoice: "alex" },
  { id: "fish_audio", displayName: "Fish Audio", url: "https://api.fish.audio/v1/tts", defaultModel: "s2-pro", defaultVoice: "default", formatType: "fish_audio" },
  { id: "aliyun", displayName: "阿里云百炼", url: "https://dashscope.aliyuncs.com/compatible-mode/v1/audio/speech", defaultModel: "cosyvoice-v1", defaultVoice: "longxiaochun" },
];

// ─── ASR ───
export const ASR_PRESETS: ServicePreset[] = [
  { id: "openai", displayName: "OpenAI Whisper", url: "https://api.openai.com/v1/audio/transcriptions", defaultModel: "whisper-1" },
  { id: "siliconflow", displayName: "硅基流动", url: "https://api.siliconflow.cn/v1/audio/transcriptions", defaultModel: "FunAudioLLM/SenseVoiceSmall" },
  { id: "aliyun", displayName: "阿里云百炼", url: "https://dashscope.aliyuncs.com/compatible-mode/v1/audio/transcriptions", defaultModel: "sensevoice-v1" },
];

// ─── 图片生成 ───
export const IMAGE_GEN_PRESETS: ServicePreset[] = [
  { id: "openai", displayName: "OpenAI (DALL-E)", url: "https://api.openai.com/v1/images/generations", defaultModel: "dall-e-3", formatType: "openai" },
  { id: "siliconflow", displayName: "硅基流动", url: "https://api.siliconflow.cn/v1/images/generations", defaultModel: "Kwai-Kolors/Kolors", formatType: "siliconflow" },
  { id: "aliyun_kling", displayName: "阿里云百炼(可灵)", url: "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation", defaultModel: "kling-v3-image-generation", formatType: "aliyun_async" },
  { id: "zhipu", displayName: "智谱AI (CogView)", url: "https://open.bigmodel.cn/api/paas/v4/images/generations", defaultModel: "cogview-4-250304", formatType: "openai" },
];

// ─── 图片识别 ───
export const IMAGE_RECOG_PRESETS: ServicePreset[] = [
  { id: "openai", displayName: "OpenAI (GPT-4o)", url: "https://api.openai.com/v1/chat/completions", defaultModel: "gpt-4o" },
  { id: "aliyun", displayName: "阿里云百炼 (Qwen-VL)", url: "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", defaultModel: "qwen-vl-max" },
  { id: "zhipu", displayName: "智谱AI (GLM-4V)", url: "https://open.bigmodel.cn/api/paas/v4/chat/completions", defaultModel: "glm-4v-flash" },
  { id: "siliconflow", displayName: "硅基流动 (Qwen-VL)", url: "https://api.siliconflow.cn/v1/chat/completions", defaultModel: "Qwen/Qwen2-VL-7B-Instruct" },
];

// ─── Edge TTS 语音列表（Web Speech 不可用时的云端 edge 声音名） ───
export const EDGE_VOICES = [
  "zh-CN-XiaoxiaoNeural", "zh-CN-XiaoyiNeural", "zh-CN-YunxiNeural", "zh-CN-YunjianNeural",
  "zh-CN-YunxiaNeural", "zh-CN-YunyangNeural", "zh-CN-liaoning-XiaobeiNeural", "zh-CN-shaanxi-XiaoniNeural",
  "zh-HK-HiuGaaiNeural", "zh-TW-HsiaoChenNeural", "ja-JP-NanamiNeural", "en-US-AnaNeural",
  "en-US-AriaNeural", "en-US-GuyNeural", "en-GB-RyanNeural", "ko-KR-SunHiNeural",
];

// ─── 厂商参数档案 ───
export interface ApiParamProfile {
  id: string;
  displayName: string;
  tempMin: number;
  tempMax: number;
  topPMin: number;
  topPMax: number;
  freqPenaltyMin: number | null;
  freqPenaltyMax: number | null;
  presPenaltyMin: number | null;
  presPenaltyMax: number | null;
  maxTokensLimit: number;
  defaultTemp: number;
  defaultTopP: number;
  defaultFreqPenalty: number;
  defaultPresPenalty: number;
  defaultMaxTokens: number;
  supportsVision: boolean;
  paramHints: Record<string, string>;
}

const P = (id: string, displayName: string, over: Partial<ApiParamProfile> = {}): ApiParamProfile => ({
  id, displayName,
  tempMin: 0, tempMax: 2, topPMin: 0, topPMax: 1,
  freqPenaltyMin: -2, freqPenaltyMax: 2, presPenaltyMin: -2, presPenaltyMax: 2,
  maxTokensLimit: 16384,
  defaultTemp: 1.05, defaultTopP: 0.92, defaultFreqPenalty: 0.35, defaultPresPenalty: 0.5, defaultMaxTokens: 500,
  supportsVision: false, paramHints: {},
  ...over,
});

export const PROVIDER_PROFILES: ApiParamProfile[] = [
  P("custom", "自定义", { maxTokensLimit: 131072, supportsVision: true, paramHints: { freq_penalty: "部分API不支持此参数", pres_penalty: "部分API不支持此参数" } }),
  P("nvidia", "NVIDIA NIM", { freqPenaltyMin: null, freqPenaltyMax: null, presPenaltyMin: null, presPenaltyMax: null, maxTokensLimit: 131072, defaultTemp: 0.7, defaultTopP: 0.9, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 1024, supportsVision: true }),
  P("openai", "OpenAI", { defaultTemp: 1.0, defaultTopP: 0.9, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 1024, supportsVision: true }),
  P("deepseek", "DeepSeek", { freqPenaltyMin: 0, presPenaltyMin: 0, maxTokensLimit: 393216, defaultTemp: 1.0, defaultTopP: 0.9, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 4096, supportsVision: true }),
  P("aliyun", "阿里云百炼", { freqPenaltyMin: null, freqPenaltyMax: null, presPenaltyMin: null, presPenaltyMax: null, maxTokensLimit: 8192, defaultTemp: 0.85, defaultTopP: 0.8, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 2048, supportsVision: true }),
  P("qwen", "通义千问", { freqPenaltyMin: null, freqPenaltyMax: null, presPenaltyMin: null, presPenaltyMax: null, maxTokensLimit: 8192, defaultTemp: 0.85, defaultTopP: 0.8, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 2048, supportsVision: true }),
  P("zhipu", "智谱AI", { tempMax: 1, freqPenaltyMin: null, freqPenaltyMax: null, presPenaltyMin: null, presPenaltyMax: null, maxTokensLimit: 8192, defaultTemp: 0.7, defaultTopP: 0.7, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 2048, supportsVision: true }),
  P("minimax", "MiniMax", { tempMax: 1, freqPenaltyMin: null, freqPenaltyMax: null, presPenaltyMin: null, presPenaltyMax: null, maxTokensLimit: 8192, defaultTemp: 0.7, defaultTopP: 0.7, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 2048, supportsVision: true }),
  P("moonshot", "月之暗面", { tempMax: 1, freqPenaltyMin: null, freqPenaltyMax: null, presPenaltyMin: null, presPenaltyMax: null, maxTokensLimit: 8192, defaultTemp: 0.7, defaultTopP: 0.7, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 2048, supportsVision: true }),
  P("n1n", "n1n", { maxTokensLimit: 32768, defaultTemp: 1.0, defaultTopP: 0.9, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 1024, supportsVision: true }),
  P("siliconflow", "硅基流动", { freqPenaltyMin: null, freqPenaltyMax: null, presPenaltyMin: null, presPenaltyMax: null, maxTokensLimit: 8192, defaultTemp: 0.7, defaultTopP: 0.7, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 2048, supportsVision: true }),
  P("openrouter", "OpenRouter", { maxTokensLimit: 131072, defaultTemp: 1.0, defaultTopP: 0.9, defaultFreqPenalty: 0, defaultPresPenalty: 0, defaultMaxTokens: 4096, supportsVision: true }),
];

export function getProfile(id: string): ApiParamProfile {
  return PROVIDER_PROFILES.find((p) => p.id === id) ?? PROVIDER_PROFILES[0];
}

export function shouldSendFreqPenalty(providerId: string): boolean {
  const p = getProfile(providerId);
  return p.freqPenaltyMin !== null;
}

export function shouldSendPresPenalty(providerId: string): boolean {
  const p = getProfile(providerId);
  return p.presPenaltyMin !== null;
}

export function supportsVision(providerId: string): boolean {
  return getProfile(providerId).supportsVision;
}

export function clampToProfile(providerId: string, params: { temperature: number; topP: number; maxTokens: number }): { temperature: number; topP: number; maxTokens: number } {
  const p = getProfile(providerId);
  const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
  return {
    temperature: clamp(params.temperature, p.tempMin, p.tempMax),
    topP: clamp(params.topP, p.topPMin, p.topPMax),
    maxTokens: clamp(params.maxTokens, 1, p.maxTokensLimit),
  };
}

export function findPreset<T extends { id: string }>(list: T[], id: string): T | undefined {
  return list.find((x) => x.id === id);
}
