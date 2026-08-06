/** 记忆类型 */
export type MemoryType = "fact" | "event" | "preference" | "emotion" | "relationship";

/** 记忆重要度 */
export type MemoryImportance = "low" | "medium" | "high" | "critical";

/** 记忆条目 */
export interface MemoryEntry {
  id: string;
  personaId: string;
  type: MemoryType;
  content: string;
  importance: MemoryImportance;
  /** 关联的会话ID */
  sessionId?: string;
  /** 关联的消息ID */
  messageId?: string;
  /** 标签 */
  tags: string[];
  /** 创建时间 */
  createdAt: number;
  /** 最后访问时间 */
  lastAccessedAt: number;
  /** 访问次数 */
  accessCount: number;
  /** 是否已归档 */
  archived: boolean;
}

/** 记忆池 */
export interface MemoryPool {
  id: string;
  personaId: string;
  name: string;
  description: string;
  entries: MemoryEntry[];
  /** 最大容量 */
  maxCapacity: number;
  createdAt: number;
}

/** 记忆 */
export interface Memory {
  id: string;
  personaId: string;
  /** 短期记忆 */
  shortTerm: MemoryEntry[];
  /** 长期记忆 */
  longTerm: MemoryEntry[];
  /** 核心记忆 */
  core: MemoryEntry[];
  /** 记忆池列表 */
  pools: MemoryPool[];
  /** 总记忆数 */
  totalCount: number;
  /** 最后更新时间 */
  updatedAt: number;
}

/** 会话 */
export interface Session {
  id: string;
  personaId: string;
  startTime: number;
  endTime: number;
  messageCount: number;
  summary: string;
  /** 提取的记忆 */
  extractedMemories: MemoryEntry[];
}
