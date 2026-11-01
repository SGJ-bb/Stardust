/** LLM提供商 */
export type LlmProvider =
  | "openai"
  | "anthropic"
  | "google"
  | "local"
  | "deepseek"
  | "zhipu"
  | "tongyi"
  | "baidu"
  | "doubao"
  | "moonshot"
  | "minimax"
  | "yiwanyiwu"
  | "custom";

/** LLM参数 */
export interface LlmParams {
  temperature: number;
  topP: number;
  maxTokens: number;
  presencePenalty: number;
  frequencyPenalty: number;
  stopSequences: string[];
}

/** 提供商配置 */
export interface ProviderProfile {
  id: string;
  name: string;
  provider: LlmProvider;
  apiKey: string;
  baseUrl: string;
  modelId: string;
  params: LlmParams;
  /** 是否默认 */
  isDefault: boolean;
}

/** 语音引擎（web=浏览器原生 speechSynthesis，cloud=OpenAI兼容TTS API） */
export type VoiceEngine = "edge-tts" | "vits" | "gpt-sovits" | "local" | "web" | "cloud";

/** 语音配置 */
export interface VoiceConfig {
  engine: VoiceEngine;
  voiceId: string;
  speed: number;
  pitch: number;
  volume: number;
  /** 自动播放 */
  autoPlay: boolean;
  /** 云端TTS服务（engine=cloud 时使用） */
  cloud?: ServiceEndpointConfig;
}

/** ASR 配置 */
export interface AsrConfig {
  /** web = SpeechRecognition；cloud = OpenAI whisper 兼容接口 */
  mode: "web" | "cloud";
  cloud?: ServiceEndpointConfig;
  language: string;
}

/** 通用服务端点配置（TTS/ASR/图片生成/图片识别） */
export interface ServiceEndpointConfig {
  presetId: string;
  baseUrl: string;
  apiKey: string;
  model: string;
}

/** AI 功能开关（对齐 Android SettingsManager） */
export interface AiFeaturesConfig {
  emotionAnalysisEnabled: boolean;
  llmEmotionAnalysisEnabled: boolean;
  searchEnabled: boolean;
  searchProvider: "duckduckgo" | "bing" | "baidu";
  live2dEnabled: boolean;
  offlineModeEnabled: boolean;
  screenAwarenessEnabled: boolean;
  safetyFilterEnabled: boolean;
  humanizerEnabled: boolean;
  replyPredictEnabled: boolean;
  proactiveEnabled: boolean;
}

/** 聊天参数（对齐 Android 全局 LLM 采样参数） */
export interface ChatParamsConfig {
  temperature: number;
  topP: number;
  frequencyPenalty: number;
  presencePenalty: number;
  maxTokens: number;
  contextTurns: number;
  languageStyle: "normal" | "tsundere" | "cute";
}

/** 日记与主动搭话 */
export interface RhythmConfig {
  nagFrequency: "low" | "medium" | "high" | "off";
  diaryTriggerMode: "manual" | "msg50" | "hourly" | "every2h" | "daily22";
  momentsEnabled: boolean;
  checkInReminder: boolean;
}

/** 定时唤醒（Web 替代：页面内调度 + 系统通知） */
export interface WakeConfig {
  enabled: boolean;
  hour: number;
  minute: number;
  notifyDesktop: boolean;
}

/** 用户画像 */
export interface UserProfileConfig {
  nickname: string;
  gender: "" | "male" | "female" | "other";
  birthday: string;
  appearance: string;
  description: string;
}

/** 主题名称（12套） */
export type ThemeName =
  | "sakura"
  | "peach"
  | "violet"
  | "ocean"
  | "emerald"
  | "sunset"
  | "rosegold"
  | "mint"
  | "midnight"
  | "tea"
  | "cyberpunk"
  | "chinese";

/** 外观配置 */
export interface AppearanceConfig {
  theme: ThemeName;
  /** 是否暗色模式 */
  darkMode: boolean;
  /** 字体大小 */
  fontSize: number;
  /** 气泡圆角 */
  bubbleRadius: number;
  /** 侧边栏是否折叠 */
  sidebarCollapsed: boolean;
  /** Live2D是否显示 */
  live2dVisible: boolean;
  /** Live2D透明度 */
  live2dOpacity: number;
  /** Live2D缩放 */
  live2dScale: number;
  /** 天气动效模式: auto=根据天气自动, always=始终开启, off=关闭 */
  weatherEffect: "auto" | "always" | "off";
  /** 雨滴密集度: 0.2~2.0（1.0 为标准密度） */
  rainDensity: number;
  /** 界面显示密度：compact 紧凑 / standard 标准 / cozy 舒适 */
  density?: "compact" | "standard" | "cozy";
  /** 背景遮罩明暗（0=背景全亮, 1=标准遮罩） */
  bgMask?: number;
}

/** 记忆配置 */
export interface MemoryConfig {
  /** 是否启用记忆 */
  enabled: boolean;
  /** 短期记忆容量 */
  shortTermCapacity: number;
  /** 长期记忆容量 */
  longTermCapacity: number;
  /** 核心记忆容量 */
  coreCapacity: number;
  /** 自动提取记忆 */
  autoExtract: boolean;
  /** 记忆检索数量 */
  retrievalCount: number;
}

/** 安全配置 */
export interface SafetyConfig {
  /** 内容过滤等级 */
  contentFilterLevel: "none" | "low" | "medium" | "high";
  /** 是否启用NSFW过滤 */
  nsfwFilter: boolean;
  /** 自定义敏感词 */
  blockedWords: string[];
}

/** 插件配置 */
export interface PluginConfig {
  id: string;
  name: string;
  enabled: boolean;
  settings: Record<string, unknown>;
}

/** 全局设置 */
export interface Settings {
  /** 提供商列表 */
  providers: ProviderProfile[];
  /** 当前活跃提供商ID */
  activeProviderId: string;
  /** 语音配置 */
  voice: VoiceConfig;
  /** 外观配置 */
  appearance: AppearanceConfig;
  /** 记忆配置 */
  memory: MemoryConfig;
  /** 安全配置 */
  safety: SafetyConfig;
  /** 插件列表 */
  plugins: PluginConfig[];
  /** 是否开机自启 */
  autoStart: boolean;
  /** 是否最小化到托盘 */
  minimizeToTray: boolean;
  /** 全局快捷键 */
  globalShortcut: string;
  /** 是否启用通知 */
  notificationEnabled: boolean;
  /** 语言 */
  language: string;
  /** ===== 以下为对齐 Android 版的扩展配置（可选，引擎有默认值） ===== */
  /** 全局聊天采样参数 */
  chatParams?: ChatParamsConfig;
  /** AI 功能开关 */
  aiFeatures?: AiFeaturesConfig;
  /** 日记/搭话节奏 */
  rhythm?: RhythmConfig;
  /** 定时唤醒 */
  wake?: WakeConfig;
  /** 用户画像 */
  userProfile?: UserProfileConfig;
  /** 语音识别 */
  asr?: AsrConfig;
  /** 图片生成服务 */
  imageGen?: ServiceEndpointConfig;
  /** 图片识别服务 */
  imageRecog?: ServiceEndpointConfig;
  /** 星尘币余额（皮肤商店） */
  coins?: number;
  /** 工具权限三档（借鉴 Operit）：auto=自动允许 ask=每次询问 deny=拒绝；缺省 auto */
  toolPermissions?: Record<string, "auto" | "ask" | "deny">;
}
