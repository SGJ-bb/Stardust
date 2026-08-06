/** 世界配置 */
export interface WorldConfig {
  id: string;
  name: string;
  description: string;
  /** 世界观设定 */
  lore: string;
  /** 时代背景 */
  era: string;
  /** 地理环境 */
  geography: string;
  /** 社会结构 */
  society: string;
  /** 魔法/科技体系 */
  system: string;
  /** 规则 */
  rules: string[];
  /** 关联角色ID列表 */
  personaIds: string[];
  createdAt: number;
  updatedAt: number;
}

/** 世界状态 */
export interface WorldState {
  worldId: string;
  /** 当前时间线 */
  timeline: string;
  /** 当前地点 */
  location: string;
  /** 天气 */
  weather: string;
  /** 事件历史 */
  eventHistory: StoryEvent[];
  updatedAt: number;
}

/** 故事事件 */
export interface StoryEvent {
  id: string;
  worldId: string;
  title: string;
  description: string;
  /** 参与角色 */
  participants: string[];
  /** 事件类型 */
  type: "plot" | "dialogue" | "action" | "discovery" | "conflict" | "resolution";
  timestamp: number;
  /** 影响 */
  consequences: string[];
}
