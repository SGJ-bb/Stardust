/** 纪念相册条目 */
export interface AlbumEntry {
  id: string;
  personaId: string;
  title: string;
  description: string;
  /** 图片路径 */
  imagePath: string;
  /** 日期 */
  date: number;
  /** 标签 */
  tags: string[];
  /** 关联记忆ID */
  memoryId?: string;
  createdAt: number;
}
