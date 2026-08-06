/** Tauri Command 类型声明 */

/** 激活码验证结果 */
export interface ActivationResult {
  success: boolean;
  error?: string;
}

/** 皮肤购买结果 */
export interface PurchaseResult {
  success: boolean;
  error?: string;
}

/** 通用命令响应 */
export interface CommandResult<T = unknown> {
  success: boolean;
  data?: T;
  error?: string;
}

/** 问候命令参数 */
export interface GreetParams {
  name: string;
}

/** 发送聊天消息参数 */
export interface SendMessageParams {
  personaId: string;
  content: string;
  attachments?: string[];
}

/** 流式聊天参数 */
export interface StreamChatParams {
  personaId: string;
  content: string;
  sessionId?: string;
  /** 附件路径列表（图片等） */
  attachments?: string[];
}

/** 创建角色参数（与PersonaCreateParams统一） */
export type CreatePersonaParams = import("./persona").PersonaCreateParams;

/** 更新角色参数（与PersonaUpdateParams统一） */
export type UpdatePersonaParams = import("./persona").PersonaUpdateParams;

/** 获取记忆参数 */
export interface GetMemoriesParams {
  personaId: string;
  query?: string;
  limit?: number;
}

/** 创建记忆参数 */
export interface CreateMemoryParams {
  personaId: string;
  type: string;
  content: string;
  importance: string;
  tags?: string[];
}

/** TTS参数 */
export interface TtsParams {
  text: string;
  voiceId: string;
  engine: string;
  speed?: number;
  pitch?: number;
}

/** 语音识别参数 */
export interface SttParams {
  audioPath: string;
  language?: string;
}

/** Live2D控制参数 */
export interface Live2DControlParams {
  action: "load" | "expression" | "motion" | "tap" | "scale";
  modelPath?: string;
  expressionName?: string;
  motionGroup?: string;
  motionIndex?: number;
  scale?: number;
}

/** 文件对话框参数 */
export interface FileDialogParams {
  title?: string;
  filters?: Array<{ name: string; extensions: string[] }>;
  multiple?: boolean;
}

/** Tauri事件名称 */
export type TauriEventType =
  | "chat:stream-token"
  | "chat:stream-done"
  | "chat:stream-error"
  | "chat:tool-call"
  | "chat:tool-result"
  | "voice:recording-started"
  | "voice:recording-stopped"
  | "voice:recognition-result"
  | "live2d:model-loaded"
  | "live2d:action-complete"
  | "notification:received"
  | "alarm:triggered"
  | "capsule:opened"
  | "achievement:unlocked"
  | "moment:new-comment"
  | "shortcut:triggered";
