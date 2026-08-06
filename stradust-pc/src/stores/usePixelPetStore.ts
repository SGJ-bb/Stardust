import { create } from "zustand";
import type {
  PixelPet,
  PetAction,
  ImageGenConfig,
  GenerationProgressEvent,
} from "@/lib/pixelpet/types";
import { DEFAULT_GEN_CONFIG } from "@/lib/pixelpet/types";
import { isTauri } from "@/lib/tauri";

// ─── Store State ───
interface PixelPetState {
  // 宠物列表与当前选中
  pets: PixelPet[];
  activePet: PixelPet | null;
  currentPetActions: PetAction[];

  // 图片生成配置
  genConfig: ImageGenConfig;

  // UI 状态
  isLoading: boolean;
  isGenerating: boolean;
  generationProgress: GenerationProgressEvent | null;

  // 宠物模式 (live2d | pixel)
  petMode: "live2d" | "pixel";

  // ─── Actions ───

  // 加载
  loadPets: () => Promise<void>;
  loadActivePet: () => Promise<void>;
  loadPetActions: (petId: string) => Promise<void>;
  loadGenConfig: () => Promise<void>;

  // 宠物 CRUD
  createPet: (petData: {
    name: string;
    description?: string;
    referenceImagePath?: string;
    basePrompt: string;
    negativePrompt?: string;
    spriteWidth?: number;
    spriteHeight?: number;
    fps?: number;
    scale?: number;
    renderMode?: string;
  }) => Promise<PixelPet>;
  updatePet: (pet: PixelPet) => Promise<void>;
  setActivePet: (id: string) => Promise<void>;
  deletePet: (id: string) => Promise<void>;

  // 动作 CRUD
  createAction: (
    action: Omit<PetAction, "id" | "frames" | "createdAt">,
  ) => Promise<PetAction>;
  updateAction: (action: PetAction) => Promise<void>;
  deleteAction: (id: string) => Promise<void>;

  // 配置
  updateGenConfig: (config: Partial<ImageGenConfig>) => Promise<void>;

  // 模式切换
  setPetMode: (mode: "live2d" | "pixel") => void;

  // 生成进度
  setGenerating: (val: boolean) => void;
  setGenerationProgress: (event: GenerationProgressEvent | null) => void;
}

// ─── Tauri invoke helper ─__
async function invoke<T>(
  cmd: string,
  args?: Record<string, unknown>,
): Promise<T> {
  // 浏览器环境：直接抛出友好错误，由调用方 catch 处理
  if (!isTauri()) {
    throw new Error(`[PixelPetStore] 命令 "${cmd}" 仅在 Tauri 桌面环境下可用`);
  }
  const { invoke: tauriInvoke } = await import("@tauri-apps/api/core");
  return tauriInvoke<T>(cmd, args);
}

export const usePixelPetStore = create<PixelPetState>((set, get) => ({
  pets: [],
  activePet: null,
  currentPetActions: [],
  genConfig: { ...DEFAULT_GEN_CONFIG },
  isLoading: false,
  isGenerating: false,
  generationProgress: null,
  petMode: "live2d",

  // ── 加载 ──
  loadPets: async () => {
    set({ isLoading: true });
    try {
      const pets = await invoke<Array<unknown>>("list_pixel_pets");
      set({ pets: pets as PixelPet[], isLoading: false });
      // 同时加载活跃宠物
      get().loadActivePet();
    } catch (e) {
      console.error("[PixelPetStore] loadPets error:", e);
      set({ isLoading: false });
    }
  },

  loadActivePet: async () => {
    try {
      const pet = await invoke<unknown | null>("get_active_pixel_pet");
      set({ activePet: pet as PixelPet | null });
      if (pet) {
        get().loadPetActions((pet as PixelPet).id);
      }
    } catch (e) {
      console.error("[PixelPetStore] loadActivePet error:", e);
    }
  },

  loadPetActions: async (petId: string) => {
    try {
      const actions = await invoke<Array<unknown>>("list_pet_actions", {
        petId,
      });
      set({ currentPetActions: actions as PetAction[] });
    } catch (e) {
      console.error("[PixelPetStore] loadPetActions error:", e);
    }
  },

  loadGenConfig: async () => {
    try {
      const config = await invoke<unknown>("get_pixel_gen_config");
      if (config) {
        set({ genConfig: config as ImageGenConfig });
      }
    } catch (e) {
      console.error("[PixelPetStore] loadGenConfig error:", e);
    }
  },

  // ── 宠物 CRUD ──
  createPet: async (petData) => {
    const pet = await invoke<PixelPet>("create_pixel_pet", {
      req: {
        name: petData.name,
        description: petData.description,
        referenceImagePath: petData.referenceImagePath,
        basePrompt: petData.basePrompt,
        negativePrompt: petData.negativePrompt,
        spriteWidth: petData.spriteWidth,
        spriteHeight: petData.spriteHeight,
        fps: petData.fps,
        scale: petData.scale,
        renderMode: petData.renderMode,
      },
    });
    set((s) => ({ pets: [pet, ...s.pets] }));
    return pet;
  },

  updatePet: async (pet) => {
    await invoke("update_pixel_pet", { pet });
    set((s) => ({
      pets: s.pets.map((p) => (p.id === pet.id ? pet : p)),
      activePet: s.activePet?.id === pet.id ? pet : s.activePet,
    }));
  },

  setActivePet: async (id: string) => {
    await invoke("set_active_pixel_pet", { id });
    const pet = get().pets.find((p) => p.id === id) || null;
    set({ activePet: pet });
    if (pet) get().loadPetActions(pet.id);
  },

  deletePet: async (id: string) => {
    await invoke("delete_pixel_pet", { id });
    set((s) => ({
      pets: s.pets.filter((p) => p.id !== id),
      activePet: s.activePet?.id === id ? null : s.activePet,
      currentPetActions: s.activePet?.id === id ? [] : s.currentPetActions,
    }));
  },

  // ── 动作 CRUD ──
  createAction: async (actionData) => {
    const action = await invoke<PetAction>("create_pet_action", {
      req: {
        petId: actionData.petId,
        name: actionData.name,
        displayName: actionData.displayName,
        description: actionData.description,
        prompt: actionData.prompt,
        frameCount: actionData.frameCount,
        frameDuration: actionData.frameDuration,
        loopMode: actionData.loopMode,
        triggerEvents: actionData.triggerEvents,
      },
    });
    set((s) => ({ currentPetActions: [...s.currentPetActions, action] }));
    return action;
  },

  updateAction: async (action) => {
    await invoke("update_pet_action", { action });
    set((s) => ({
      currentPetActions: s.currentPetActions.map((a) =>
        a.id === action.id ? action : a,
      ),
    }));
  },

  deleteAction: async (id: string) => {
    await invoke("delete_pet_action", { id });
    set((s) => ({
      currentPetActions: s.currentPetActions.filter((a) => a.id !== id),
    }));
  },

  // ── 配置 ──
  updateGenConfig: async (partial) => {
    const config = await invoke<ImageGenConfig>("update_pixel_gen_config", {
      req: partial,
    });
    set({ genConfig: config });
  },

  // ── 模式切换 ──
  setPetMode: (mode) => set({ petMode: mode }),

  // ── 生成状态 ──
  setGenerating: (val) => set({ isGenerating: val }),
  setGenerationProgress: (event) => set({ generationProgress: event }),
}));
