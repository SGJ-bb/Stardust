/** 角色性别 */
export type PersonaGender = "male" | "female" | "other";

/** 角色创建参数 */
export interface PersonaCreateParams {
  name: string;
  avatar: string;
  description: string;
  personality: string;
  scenario: string;
  greeting: string;
  gender: PersonaGender;
  tags: string[];
  modelId?: string;
  voiceId?: string;
  live2dModelPath?: string;
}

/** 角色更新参数（id 通过函数参数传入，不包含在此类型中） */
export type PersonaUpdateParams = Partial<PersonaCreateParams>;

/** 角色 */
export interface Persona {
  id: string;
  name: string;
  avatar: string;
  description: string;
  personality: string;
  scenario: string;
  greeting: string;
  gender: PersonaGender;
  tags: string[];
  modelId: string;
  voiceId: string;
  live2dModelPath: string;
  /** 好感度 */
  favorability: number;
  /** 好感度等级 */
  favorabilityLevel: number;
  /** 好感度称号 */
  favorabilityTitle: string;
  /** 创建时间 */
  createdAt: number;
  /** 更新时间 */
  updatedAt: number;
  /** 是否置顶 */
  pinned: boolean;
  /** 是否收藏 */
  favorited: boolean;
  /** 最近聊天时间 */
  lastChatTime: number;
  /** 聊天次数 */
  chatCount: number;
  /** 头像边框 */
  avatarFrame: string;
  /** 气泡皮肤 */
  bubbleSkin: string;
}
