/** 日记 */
export interface Diary {
  id: string;
  personaId: string;
  title: string;
  content: string;
  /** 心情标签 */
  mood: string;
  /** 天气 */
  weather: string;
  /** 附件 */
  attachments: string[];
  /** 标签 */
  tags: string[];
  createdAt: number;
  updatedAt: number;
}

/** 日记条目（简化版，用于列表展示） */
export interface DiaryEntry {
  id: string;
  personaId: string;
  title: string;
  mood: string;
  createdAt: number;
  excerpt: string;
}
