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

/** 语音引擎 */
export type VoiceEngine = "edge-tts" | "vits" | "gpt-sovits" | "local";

/** 语音配置 */
export interface VoiceConfig {
  engine: VoiceEngine;
  voiceId: string;
  speed: number;
  pitch: number;
  volume: number;
  /** 自动播放 */
  autoPlay: boolean;
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
}
