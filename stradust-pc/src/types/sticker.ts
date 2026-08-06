/** 表情包 */
export interface Sticker {
  id: string;
  name: string;
  /** 图片路径 */
  path: string;
  /** 缩略图路径 */
  thumbnail: string;
  /** 所属表情包集 */
  packId: string;
  /** 标签 */
  tags: string[];
  /** 是否收藏 */
  favorited: boolean;
  /** 使用次数 */
  useCount: number;
  createdAt: number;
}

/** 表情包集 */
export interface StickerPack {
  id: string;
  name: string;
  cover: string;
  stickers: Sticker[];
  /** 是否为内置 */
  builtIn: boolean;
  createdAt: number;
}
