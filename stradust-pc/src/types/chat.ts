/** 聊天消息类型 */
export type MessageRole = "user" | "assistant" | "system";

/** 消息状态 */
export type MessageStatus = "sending" | "sent" | "error" | "streaming";

/** 工具调用状态 */
export type ToolCallStatus = "pending" | "running" | "completed" | "error";

/** 工具定义 */
export interface ToolDefinition {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

/** 工具调用 */
export interface ToolCall {
  id: string;
  name: string;
  arguments: string;
  status: ToolCallStatus;
  result?: string;
}

/** 聊天消息 */
export interface ChatMessage {
  id: string;
  personaId: string;
  role: MessageRole;
  content: string;
  timestamp: number;
  status: MessageStatus;
  /** 是否已收藏 */
  favorited: boolean;
  /** 附件列表 */
  attachments: Attachment[];
  /** 工具调用列表 */
  toolCalls: ToolCall[];
  /** 表情回应 */
  reactions: EmojiReaction[];
  /** 关联的记忆ID */
  memoryId?: string;
  /** 情感标签 */
  emotion?: string;
}

/** 附件 */
export interface Attachment {
  id: string;
  type: "image" | "audio" | "video" | "file";
  url: string;
  name: string;
  size: number;
  mimeType: string;
}

/** 表情回应 */
export interface EmojiReaction {
  emoji: string;
  count: number;
  reacted: boolean;
}

/** 流式响应事件 */
export type StreamEventType = "token" | "tool_call" | "tool_result" | "emotion" | "action" | "done" | "error";

export interface StreamEvent {
  type: StreamEventType;
  data: string;
  toolCall?: ToolCall;
}

/** 聊天响应 */
export interface ChatResponse {
  message: ChatMessage;
  streamEvents?: StreamEvent[];
}

/** 会话信息 */
export interface ChatSession {
  id: string;
  personaId: string;
  title: string;
  lastMessage: string;
  lastMessageTime: number;
  messageCount: number;
  createdAt: number;
  updatedAt: number;
}

/** 好感度等级 */
export interface Favorability {
  personaId: string;
  level: number;
  experience: number;
  maxExperience: number;
  title: string;
}
