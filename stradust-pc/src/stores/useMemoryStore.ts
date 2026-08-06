import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Memory, MemoryEntry, MemoryPool } from "@/types/memory";
import { searchMemories } from "@/lib/tauri";

interface MemoryState {
  /** 当前角色的记忆 */
  currentMemory: Memory | null;
  /** 搜索关键词 */
  searchQuery: string;
  /** 搜索结果 */
  searchResults: MemoryEntry[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置当前记忆 */
  setCurrentMemory: (memory: Memory | null) => void;
  /** 设置搜索关键词并触发搜索 */
  setSearchQuery: (query: string) => void;
  /** 设置搜索结果 */
  setSearchResults: (results: MemoryEntry[]) => void;
  /** 添加记忆条目 */
  addMemoryEntry: (entry: MemoryEntry) => void;
  /** 删除记忆条目 */
  deleteMemoryEntry: (entryId: string) => void;
  /** 归档记忆条目 */
  archiveMemoryEntry: (entryId: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useMemoryStore = create<MemoryState>()(
  persist(
    (set, get) => ({
  currentMemory: null,
  searchQuery: "",
  searchResults: [],
  isLoading: false,

  setCurrentMemory: (memory) => set({ currentMemory: memory }),
  setSearchQuery: (query) => {
    set({ searchQuery: query });
    // 搜索关键词变化时触发后端搜索
    const { currentMemory } = get();
    if (currentMemory && query.trim()) {
      searchMemories(currentMemory.personaId, query)
        .then((results) => {
          // searchMemories返回Memory[]，需要提取其中的条目
          const entries: MemoryEntry[] = results.flatMap((m) => [
            ...m.shortTerm,
            ...m.longTerm,
            ...m.core,
          ]);
          set({ searchResults: entries });
        })
        .catch((error) => {
          console.error("搜索记忆失败:", error);
          set({ searchResults: [] });
        });
    } else {
      set({ searchResults: [] });
    }
  },
  setSearchResults: (results) => set({ searchResults: results }),

  addMemoryEntry: (entry) =>
    set((state) => {
      if (!state.currentMemory) return state;
      const memory = { ...state.currentMemory };
      if (entry.importance === "critical") {
        memory.core = [...memory.core, entry];
      } else if (entry.importance === "high" || entry.importance === "medium") {
        memory.longTerm = [...memory.longTerm, entry];
      } else {
        memory.shortTerm = [...memory.shortTerm, entry];
      }
      memory.totalCount += 1;
      return { currentMemory: memory };
    }),

  deleteMemoryEntry: (entryId) =>
    set((state) => {
      if (!state.currentMemory) return state;
      const memory = { ...state.currentMemory };
      memory.shortTerm = memory.shortTerm.filter((e) => e.id !== entryId);
      memory.longTerm = memory.longTerm.filter((e) => e.id !== entryId);
      memory.core = memory.core.filter((e) => e.id !== entryId);
      memory.totalCount -= 1;
      return { currentMemory: memory };
    }),

  archiveMemoryEntry: (entryId) =>
    set((state) => {
      if (!state.currentMemory) return state;
      const memory = { ...state.currentMemory };
      const archive = (entries: MemoryEntry[]) =>
        entries.map((e) => (e.id === entryId ? { ...e, archived: true } : e));
      memory.shortTerm = archive(memory.shortTerm);
      memory.longTerm = archive(memory.longTerm);
      memory.core = archive(memory.core);
      return { currentMemory: memory };
    }),

  setLoading: (loading) => set({ isLoading: loading }),
}),
{
  name: "stradust-memory",
  partialize: (state) => ({
    currentMemory: state.currentMemory,
  }),
}
));
