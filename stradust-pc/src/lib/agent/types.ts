// Agent 智能体系统 — TypeScript 类型定义

/** 技能分类 */
export type SkillCategory = 'office' | 'media' | 'dev' | 'ai_assistant';

export const SKILL_CATEGORY_META: Record<SkillCategory, { label: string; icon: string }> = {
  office: { label: '办公效率', icon: 'FileText' },
  media: { label: '媒体创作', icon: 'Film' },
  dev: { label: '开发工具', icon: 'Terminal' },
  ai_assistant: { label: 'AI助手', icon: 'Sparkles' },
};

/** 技能元数据 */
export interface SkillMeta {
  id: string;
  name: string;
  description: string;
  category: SkillCategory;
  cliDeps: string[];
  enabled: boolean;
  isBuiltin: boolean;
  version: string;
}

/** Agent 消息角色 */
export type AgentRole = 'user' | 'assistant' | 'tool' | 'system';

/** Agent 会话消息 */
export interface AgentMessage {
  id: string;
  role: AgentRole;
  content: string;
  toolCalls?: AgentToolCall[];
  toolCallId?: string;
  toolName?: string;
  timestamp: number;
}

/** Agent 工具调用记录 */
export interface AgentToolCall {
  id: string;
  name: string;
  arguments: Record<string, unknown>;
  status: 'pending' | 'running' | 'success' | 'failed';
  result?: string;
  startedAt: number;
  finishedAt?: number;
}

/** 工具调用状态 */
export type ToolCallStatus = AgentToolCall['status'];

/** Agent 会话 */
export interface AgentSession {
  id: string;
  title: string;
  messages: AgentMessage[];
  createdAt: number;
  updatedAt: number;
  activeCategory: SkillCategory;
}

/** Agent 流式事件 */
export type AgentEvent =
  | { type: 'content'; data: string }
  | { type: 'tool_start'; data: { name: string; args: Record<string, unknown> } }
  | { type: 'tool_result'; data: { name: string; result: string; success: boolean } }
  | { type: 'reasoning'; data: string }
  | { type: 'done'; data: { toolCalls: AgentToolCall[] } }
  | { type: 'error'; data: { message: string } };

/** 分类标签（用于Tab） */
export interface CategoryTab {
  key: SkillCategory | 'all';
  label: string;
  icon: string;
  count: number;
}
