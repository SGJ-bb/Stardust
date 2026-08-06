import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { WorldConfig, WorldState, StoryEvent } from "@/types/virtual-world";

interface VirtualWorldState {
  /** 世界列表 */
  worlds: WorldConfig[];
  /** 当前世界状态 */
  currentWorldState: WorldState | null;
  /** 当前世界配置 */
  currentWorldConfig: WorldConfig | null;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置世界列表 */
  setWorlds: (worlds: WorldConfig[]) => void;
  /** 设置当前世界状态 */
  setCurrentWorldState: (state: WorldState | null) => void;
  /** 设置当前世界配置 */
  setCurrentWorldConfig: (config: WorldConfig | null) => void;
  /** 添加世界 */
  addWorld: (world: WorldConfig) => void;
  /** 删除世界 */
  deleteWorld: (worldId: string) => void;
  /** 更新世界观设定 */
  updateWorldLore: (worldId: string, lore: string) => void;
  /** 添加故事事件 */
  addStoryEvent: (event: StoryEvent) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useVirtualWorldStore = create<VirtualWorldState>()(
  persist(
    (set) => ({
      worlds: [],
      currentWorldState: null,
      currentWorldConfig: null,
      isLoading: false,

      setWorlds: (worlds) => set({ worlds }),
      setCurrentWorldState: (state) => set({ currentWorldState: state }),
      setCurrentWorldConfig: (config) => set({ currentWorldConfig: config }),

      addWorld: (world) => set((state) => ({ worlds: [...state.worlds, world] })),

      deleteWorld: (worldId) =>
        set((state) => ({
          worlds: state.worlds.filter((w) => w.id !== worldId),
          currentWorldConfig: state.currentWorldConfig?.id === worldId ? null : state.currentWorldConfig,
        })),

      updateWorldLore: (worldId, lore) =>
        set((state) => ({
          worlds: state.worlds.map((w) =>
            w.id === worldId ? { ...w, lore, updatedAt: Date.now() } : w
          ),
          currentWorldConfig:
            state.currentWorldConfig?.id === worldId
              ? { ...state.currentWorldConfig, lore, updatedAt: Date.now() }
              : state.currentWorldConfig,
        })),

      addStoryEvent: (event) =>
        set((state) => {
          if (!state.currentWorldState) return state;
          return {
            currentWorldState: {
              ...state.currentWorldState,
              eventHistory: [...state.currentWorldState.eventHistory, event],
              updatedAt: Date.now(),
            },
          };
        }),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-virtual-world",
      partialize: (state) => ({
        worlds: state.worlds,
        currentWorldConfig: state.currentWorldConfig,
        currentWorldState: state.currentWorldState,
      }),
    }
  )
);
