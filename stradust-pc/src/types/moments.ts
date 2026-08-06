/** 朋友圈动态 */
export interface Moment {
  id: string;
  personaId: string;
  content: string;
  /** 图片列表 */
  images: string[];
  /** 心情 */
  mood: string;
  /** 点赞数 */
  likes: number;
  /** 是否已点赞 */
  liked: boolean;
  /** 评论列表 */
  comments: Comment[];
  createdAt: number;
}

/** 评论 */
export interface Comment {
  id: string;
  momentId: string;
  /** 评论者ID（角色ID或"用户"） */
  authorId: string;
  authorName: string;
  content: string;
  /** 回复的评论ID */
  replyTo?: string;
  createdAt: number;
}
