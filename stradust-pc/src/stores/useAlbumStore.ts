import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AlbumEntry } from "@/types/album";
import { generateId } from "@/lib/utils";

interface AlbumState {
  /** 相册条目列表 */
  entries: AlbumEntry[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置条目列表 */
  setEntries: (entries: AlbumEntry[]) => void;
  /** 添加条目 */
  addEntry: (personaId: string, title: string, imagePath: string, description: string) => void;
  /** 删除条目 */
  deleteEntry: (entryId: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useAlbumStore = create<AlbumState>()(
  persist(
    (set) => ({
      entries: [],
      isLoading: false,

      setEntries: (entries) => set({ entries }),

      addEntry: (personaId, title, imagePath, description) => {
        const entry: AlbumEntry = {
          id: generateId(),
          personaId,
          title,
          description,
          imagePath,
          date: Date.now(),
          tags: [],
          createdAt: Date.now(),
        };
        set((state) => ({ entries: [entry, ...state.entries] }));
      },

      deleteEntry: (entryId) =>
        set((state) => ({
          entries: state.entries.filter((e) => e.id !== entryId),
        })),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-album",
      partialize: (state) => ({ entries: state.entries }),
    }
  )
);
