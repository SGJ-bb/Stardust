import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Sticker, StickerPack } from "@/types/sticker";

interface StickerState {
  /** 表情包集列表 */
  packs: StickerPack[];
  /** 最近使用的表情 */
  recentStickers: Sticker[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置表情包集列表 */
  setPacks: (packs: StickerPack[]) => void;
  /** 添加表情包集 */
  addPack: (pack: StickerPack) => void;
  /** 收藏表情 */
  toggleFavorite: (stickerId: string) => void;
  /** 记录使用 */
  recordUsage: (stickerId: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useStickerStore = create<StickerState>()(
  persist(
    (set) => ({
      packs: [],
      recentStickers: [],
      isLoading: false,

      setPacks: (packs) => set({ packs }),
      addPack: (pack) => set((state) => ({ packs: [...state.packs, pack] })),

      toggleFavorite: (stickerId) =>
        set((state) => ({
          packs: state.packs.map((pack) => ({
            ...pack,
            stickers: pack.stickers.map((s) =>
              s.id === stickerId ? { ...s, favorited: !s.favorited } : s
            ),
          })),
        })),

      recordUsage: (stickerId) =>
        set((state) => {
          let usedSticker: Sticker | null = null;
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
        }),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-sticker",
      partialize: (state) => ({
        packs: state.packs,
        recentStickers: state.recentStickers,
      }),
    }
  )
);
