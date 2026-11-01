import { create } from "zustand";
import type { Sticker, StickerPack } from "@/types/sticker";
import { listStickers, addSticker, deleteSticker, incrementUse, BUILTIN_STICKERS } from "@/engine/stickers";
import type { StickerRecord } from "@/engine/kinds";

/** 引擎 StickerRecord → 页面 Sticker（与门面 engineToPageSticker 一致） */
function recordToSticker(s: { id: string; name: string; dataUrl: string; tags: string[]; createdAt: number; useCount: number }): Sticker {
  return {
    id: s.id, name: s.name, path: s.dataUrl, thumbnail: s.dataUrl,
    packId: "all", tags: s.tags, favorited: false, useCount: s.useCount, createdAt: s.createdAt,
  };
}

function buildPack(stickers: Sticker[]): StickerPack {
  return {
    id: "all", name: "全部表情", cover: stickers[0]?.thumbnail ?? "",
    stickers, builtIn: true, createdAt: Date.now(),
  };
}

interface StickerState {
  /** 表情包集列表 */
  packs: StickerPack[];
  /** 最近使用的表情 */
  recentStickers: Sticker[];
  /** 收藏的表情 id 集合（本地偏好，引擎无此字段） */
  favoriteIds: string[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 从引擎加载表情（内置 + 自定义） */
  load: (personaId?: string) => Promise<void>;
  /** 添加表情（dataURL 图片） */
  addStickerItem: (input: { name: string; tags: string[]; dataUrl: string; personaId?: string }) => Promise<void>;
  /** 删除表情（builtin_ 拒删由引擎保证） */
  removeSticker: (stickerId: string) => Promise<void>;
  /** 设置表情包集列表 */
  setPacks: (packs: StickerPack[]) => void;
  /** 添加表情包集（转调引擎逐个导入其表情） */
  addPack: (pack: StickerPack) => Promise<void>;
  /** 收藏表情 */
  toggleFavorite: (stickerId: string) => void;
  /** 记录使用 */
  recordUsage: (stickerId: string) => Promise<void>;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

const FAV_KEY = "sticker-favorites";

function loadFavorites(): string[] {
  try {
    return JSON.parse(localStorage.getItem(FAV_KEY) ?? "[]") as string[];
  } catch {
    return [];
  }
}

export const useStickerStore = create<StickerState>()((set, get) => ({
  packs: [],
  recentStickers: [],
  favoriteIds: loadFavorites(),
  isLoading: false,

  load: async (personaId) => {
    set({ isLoading: true });
    try {
      const records = await listStickers(personaId);
      const favSet = new Set(get().favoriteIds);
      const stickers = records.map((r) => ({ ...recordToSticker(r), favorited: favSet.has(r.id) }));
      set({ packs: [buildPack(stickers)], isLoading: false });
    } catch {
      set({ isLoading: false });
    }
  },

  addStickerItem: async (input) => {
    await addSticker(input);
    await get().load();
  },

  removeSticker: async (stickerId) => {
    if (stickerId.startsWith("builtin_")) return; // 内置拒删（对齐引擎）
    await deleteSticker(stickerId);
    await get().load();
  },

  setPacks: (packs) => set({ packs }),

  addPack: async (pack) => {
    for (const s of pack.stickers ?? []) {
      if (!BUILTIN_STICKERS.some((b) => b.id === s.id)) {
        await addSticker({ name: s.name, tags: s.tags, dataUrl: s.path });
      }
    }
    await get().load();
  },

  toggleFavorite: (stickerId) => {
    const favs = new Set(get().favoriteIds);
    if (favs.has(stickerId)) favs.delete(stickerId);
    else favs.add(stickerId);
    const ids = [...favs];
    localStorage.setItem(FAV_KEY, JSON.stringify(ids));
    set((state) => ({
      favoriteIds: ids,
      packs: state.packs.map((p) => ({
        ...p,
        stickers: p.stickers.map((s) => (s.id === stickerId ? { ...s, favorited: !s.favorited } : s)),
      })),
    }));
  },

  recordUsage: async (stickerId) => {
    let usedSticker: Sticker | null = null;
    set((state) => {
      const packs = state.packs.map((pack) => ({
        ...pack,
        stickers: pack.stickers.map((s) => {
          if (s.id === stickerId) {
            usedSticker = { ...s, useCount: s.useCount + 1 };
            return usedSticker;
          }
          return s;
        }),
      }));
      const recent = usedSticker
        ? [usedSticker, ...state.recentStickers.filter((s) => s.id !== stickerId)].slice(0, 20)
        : state.recentStickers;
      return { packs, recentStickers: recent };
    });
    await incrementUse(stickerId).catch(() => undefined);
  },

  setLoading: (loading) => set({ isLoading: loading }),
}));
