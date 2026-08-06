import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { TimeCapsule } from "@/types/capsule";
import { generateId } from "@/lib/utils";

interface CapsuleState {
  /** 时光胶囊列表 */
  capsules: TimeCapsule[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置胶囊列表 */
  setCapsules: (capsules: TimeCapsule[]) => void;
  /** 创建胶囊 */
  createCapsule: (personaId: string, title: string, content: string, openAt: number) => void;
  /** 开启胶囊 */
  openCapsule: (capsuleId: string) => void;
  /** 删除胶囊 */
  deleteCapsule: (capsuleId: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useCapsuleStore = create<CapsuleState>()(
  persist(
    (set) => ({
      capsules: [],
      isLoading: false,

      setCapsules: (capsules) => set({ capsules }),

      createCapsule: (personaId, title, content, openAt) => {
        const capsule: TimeCapsule = {
          id: generateId(),
          personaId,
          title,
          content,
          attachments: [],
          sealedAt: Date.now(),
          openAt,
          opened: false,
          createdAt: Date.now(),
        };
        set((state) => ({ capsules: [...state.capsules, capsule] }));
      },

      openCapsule: (capsuleId) =>
        set((state) => ({
          capsules: state.capsules.map((c) =>
            c.id === capsuleId ? { ...c, opened: true, openMessage: "时光胶囊已开启！" } : c
          ),
        })),

      deleteCapsule: (capsuleId) =>
        set((state) => ({
          capsules: state.capsules.filter((c) => c.id !== capsuleId),
        })),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-capsule",
      partialize: (state) => ({ capsules: state.capsules }),
    }
  )
);
