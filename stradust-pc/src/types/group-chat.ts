/** 群聊发言模式：自由发言、轮流发言、主持模式 */
export type SpeakMode = "free" | "turn" | "moderator";

/** 群聊成员 */
export interface GroupMember {
  personaId: string;
  name: string;
  avatar: string;
  /** 是否为管理员 */
  isAdmin: boolean;
  /** 发言权重 */
  speakWeight: number;
}

/** 群聊消息 */
export interface GroupMessage {
  id: string;
  groupId: string;
  personaId: string;
  content: string;
  timestamp: number;
  /** 是否为系统消息 */
  isSystem: boolean;
}

/** 群聊 */
export interface GroupChat {
  id: string;
  name: string;
  avatar: string;
  description: string;
  members: GroupMember[];
  messages: GroupMessage[];
  speakMode: SpeakMode;
  /** 话题 */
  topic: string;
  /** 世界观ID */
  worldId?: string;
  createdAt: number;
  updatedAt: number;
  lastMessageTime: number;
}
