import { create } from "zustand";
import type { TimeCapsule } from "@/types/capsule";
import { getTimeCapsules, createTimeCapsule, openTimeCapsule, deleteTimeCapsule } from "@/lib/tauri";
import { on } from "@/lib/events";

interface CapsuleState {
  /** 时光胶囊列表 */
  capsules: TimeCapsule[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 从后端加载胶囊 */
  load: (personaId: string) => Promise<void>;
  /** 设置胶囊列表 */
  setCapsules: (capsules: TimeCapsule[]) => void;
  /** 创建胶囊（落库） */
  createCapsule: (personaId: string, title: string, content: string, openAt: number) => Promise<void>;
  /** 开启胶囊（到期才允许，真实更新落库状态） */
  openCapsule: (capsuleId: string) => Promise<void>;
  /** 删除胶囊 */
  deleteCapsule: (capsuleId: string) => Promise<void>;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

/** 当前列表所属角色（事件刷新用） */
let scopedPersonaId = "";

export const useCapsuleStore = create<CapsuleState>()((set, get) => ({
  capsules: [],
  isLoading: false,

  load: async (personaId) => {
    if (!personaId) return;
    scopedPersonaId = personaId;
    set({ isLoading: true });
    try {
      const list = await getTimeCapsules(personaId);
      set({ capsules: list.sort((a, b) => a.openAt - b.openAt), isLoading: false });
    } catch {
      set({ isLoading: false });
    }
  },

  setCapsules: (capsules) => set({ capsules }),

  createCapsule: async (personaId, title, content, openAt) => {
    await createTimeCapsule(personaId, title, content, openAt);
    await get().load(personaId);
  },

  openCapsule: async (capsuleId) => {
    const updated = await openTimeCapsule(capsuleId);
    if (updated) {
      // 未到期时门面返回 null，不改变状态
      set((state) => ({ capsules: state.capsules.map((c) => (c.id === capsuleId ? updated : c)) }));
    }
  },

  deleteCapsule: async (capsuleId) => {
    await deleteTimeCapsule(capsuleId);
    set((state) => ({ capsules: state.capsules.filter((c) => c.id !== capsuleId) }));
  },

  setLoading: (loading) => set({ isLoading: loading }),
}));

// 数据变化事件 → 重拉（多端写入保持一致）
on<{ collection?: string }>("data-changed", (detail) => {
  if (detail?.collection === "capsules" && scopedPersonaId) {
    void useCapsuleStore.getState().load(scopedPersonaId);
  }
});
