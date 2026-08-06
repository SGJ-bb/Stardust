import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Persona, PersonaCreateParams, PersonaUpdateParams } from "@/types/persona";
import { generateId } from "@/lib/utils";

interface PersonaState {
  /** 角色列表 */
  personas: Persona[];
  /** 当前活跃角色 */
  activePersona: Persona | null;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置角色列表 */
  setPersonas: (personas: Persona[]) => void;
  /** 设置活跃角色 */
  setActivePersona: (persona: Persona | null) => void;
  /** 添加角色 */
  addPersona: (params: PersonaCreateParams) => Persona;
  /** 更新角色 */
  updatePersona: (id: string, params: PersonaUpdateParams) => void;
  /** 删除角色 */
  deletePersona: (id: string) => void;
  /** 置顶角色 */
  togglePin: (id: string) => void;
  /** 收藏角色 */
  toggleFavorite: (id: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
  /** 根据ID获取角色 */
  getPersonaById: (id: string) => Persona | undefined;
}

export const usePersonaStore = create<PersonaState>()(
  persist(
    (set, get) => ({
      personas: [],
      activePersona: null,
      isLoading: false,

      setPersonas: (personas) => set({ personas }),
      setActivePersona: (persona) => set({ activePersona: persona }),

      addPersona: (params) => {
        const persona: Persona = {
          id: generateId(),
          ...params,
          favorability: 0,
          favorabilityLevel: 1,
          favorabilityTitle: "陌生人",
          createdAt: Date.now(),
          updatedAt: Date.now(),
          pinned: false,
          favorited: false,
          lastChatTime: 0,
          chatCount: 0,
          avatarFrame: "",
          bubbleSkin: "",
          modelId: params.modelId ?? "",
          voiceId: params.voiceId ?? "",
          live2dModelPath: params.live2dModelPath ?? "",
        };
        set((state) => ({ personas: [...state.personas, persona] }));
        return persona;
      },

      updatePersona: (id, params) =>
        set((state) => ({
          personas: state.personas.map((p) =>
            p.id === id ? { ...p, ...params, updatedAt: Date.now() } : p
          ),
        })),

      deletePersona: (id) =>
        set((state) => ({
          personas: state.personas.filter((p) => p.id !== id),
          activePersona: state.activePersona?.id === id ? null : state.activePersona,
        })),

      togglePin: (id) =>
        set((state) => ({
          personas: state.personas.map((p) =>
            p.id === id ? { ...p, pinned: !p.pinned } : p
          ),
        })),

      toggleFavorite: (id) =>
        set((state) => ({
          personas: state.personas.map((p) =>
            p.id === id ? { ...p, favorited: !p.favorited } : p
          ),
        })),

      setLoading: (loading) => set({ isLoading: loading }),

      getPersonaById: (id) => get().personas.find((p) => p.id === id),
    }),
    {
      name: "stradust-personas",
      partialize: (state) => ({ personas: state.personas }),
      version: 1,
      migrate: (persistedState: unknown, version: number) => {
        if (version === 0) {
          const ps = persistedState as { personas?: Record<string, unknown>[] };
          if (ps.personas) {
            ps.personas = ps.personas.map((p) => ({
              modelId: "",
              voiceId: "",
              live2dModelPath: "",
              favorability: 0,
              favorabilityLevel: 1,
              favorabilityTitle: "陌生人",
              pinned: false,
              favorited: false,
              lastChatTime: 0,
              chatCount: 0,
              avatarFrame: "",
              bubbleSkin: "",
              ...p,
              tags: (p as Record<string, unknown>).tags ?? [],
            }));
          }
        }
        return persistedState;
      },
    }
  )
);
