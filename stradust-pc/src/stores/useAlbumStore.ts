import { create } from "zustand";
import type { AlbumEntry } from "@/types/album";
import { getAlbumEntries, addAlbumEntry, deleteAlbumEntry } from "@/lib/tauri";
import { on } from "@/lib/events";

interface AlbumState {
  /** 相册条目列表 */
  entries: AlbumEntry[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 从后端加载相册 */
  load: (personaId: string) => Promise<void>;
  /** 设置条目列表 */
  setEntries: (entries: AlbumEntry[]) => void;
  /** 添加条目（落库） */
  addEntry: (personaId: string, title: string, imagePath: string, description: string) => Promise<void>;
  /** 删除条目 */
  deleteEntry: (entryId: string) => Promise<void>;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

/** 当前列表所属角色（事件刷新用） */
let scopedPersonaId = "";

export const useAlbumStore = create<AlbumState>()((set, get) => ({
  entries: [],
  isLoading: false,

  load: async (personaId) => {
    if (!personaId) return;
    scopedPersonaId = personaId;
    set({ isLoading: true });
    try {
      const list = await getAlbumEntries(personaId);
      set({ entries: list.sort((a, b) => b.date - a.date), isLoading: false });
    } catch {
      set({ isLoading: false });
    }
  },

  setEntries: (entries) => set({ entries }),

  addEntry: async (personaId, title, imagePath, description) => {
    await addAlbumEntry(personaId, title, imagePath, description);
    await get().load(personaId);
  },

  deleteEntry: async (entryId) => {
    await deleteAlbumEntry(entryId);
    set((state) => ({ entries: state.entries.filter((e) => e.id !== entryId) }));
  },

  setLoading: (loading) => set({ isLoading: loading }),
}));

// 数据变化事件 → 重拉
on<{ collection?: string }>("data-changed", (detail) => {
  if (detail?.collection === "albumEntries" && scopedPersonaId) {
    void useAlbumStore.getState().load(scopedPersonaId);
  }
});
