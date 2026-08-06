import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Settings, ThemeName, ProviderProfile } from "@/types/settings";

/** 默认设置 */
const DEFAULT_SETTINGS: Settings = {
  providers: [],
  activeProviderId: "",
  voice: {
    engine: "edge-tts",
    voiceId: "zh-CN-XiaoxiaoNeural",
    speed: 1.0,
    pitch: 1.0,
    volume: 1.0,
    autoPlay: true,
  },
  appearance: {
    theme: "sakura",
    darkMode: false,
    fontSize: 14,
    bubbleRadius: 12,
    sidebarCollapsed: false,
    live2dVisible: true,
    live2dOpacity: 1.0,
    live2dScale: 0.25,
    weatherEffect: "auto",
    rainDensity: 1.0,
  },
  memory: {
    enabled: true,
    shortTermCapacity: 20,
    longTermCapacity: 100,
    coreCapacity: 50,
    autoExtract: true,
    retrievalCount: 5,
  },
  safety: {
    contentFilterLevel: "medium",
    nsfwFilter: true,
    blockedWords: [],
  },
  plugins: [],
  autoStart: false,
  minimizeToTray: true,
  globalShortcut: "Ctrl+Shift+S",
  notificationEnabled: true,
  language: "zh-CN",
};

interface SettingsState {
  settings: Settings;
  isLoading: boolean;

  /** 设置全部配置 */
  setSettings: (settings: Settings) => void;
  /** 更新部分配置 */
  updateSettings: (partial: Partial<Settings>) => void;
  /** 设置主题 */
  setTheme: (theme: ThemeName) => void;
  /** 设置暗色模式 */
  setDarkMode: (darkMode: boolean) => void;
  /** 设置天气动效模式 */
  setWeatherEffect: (weatherEffect: "auto" | "always" | "off") => void;
  /** 设置雨滴密集度 */
  setRainDensity: (rainDensity: number) => void;
  /** 添加提供商 */
  addProvider: (provider: ProviderProfile) => void;
  /** 更新提供商 */
  updateProvider: (id: string, partial: Partial<ProviderProfile>) => void;
  /** 删除提供商 */
  deleteProvider: (id: string) => void;
  /** 设置活跃提供商 */
  setActiveProvider: (id: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
  /** 重置为默认设置 */
  resetToDefault: () => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      settings: DEFAULT_SETTINGS,
      isLoading: false,

      setSettings: (settings) => set({ settings }),

      updateSettings: (partial) =>
        set((state) => ({
          settings: { ...state.settings, ...partial },
        })),

      setTheme: (theme) =>
        set((state) => ({
          settings: {
            ...state.settings,
            appearance: { ...state.settings.appearance, theme },
          },
        })),

      setDarkMode: (darkMode) =>
        set((state) => ({
          settings: {
            ...state.settings,
            appearance: { ...state.settings.appearance, darkMode },
          },
        })),

      setWeatherEffect: (weatherEffect) =>
        set((state) => ({
          settings: {
            ...state.settings,
            appearance: { ...state.settings.appearance, weatherEffect },
          },
        })),

      setRainDensity: (rainDensity) =>
        set((state) => ({
          settings: {
            ...state.settings,
            appearance: { ...state.settings.appearance, rainDensity },
          },
        })),

      addProvider: (provider) =>
        set((state) => ({
          settings: {
            ...state.settings,
            providers: [...state.settings.providers, provider],
          },
        })),

      updateProvider: (id, partial) =>
        set((state) => ({
          settings: {
            ...state.settings,
            providers: state.settings.providers.map((p) =>
              p.id === id ? { ...p, ...partial } : p
            ),
          },
        })),

      deleteProvider: (id) =>
        set((state) => ({
          settings: {
            ...state.settings,
            providers: state.settings.providers.filter((p) => p.id !== id),
          },
        })),

      setActiveProvider: (id) =>
        set((state) => ({
          settings: { ...state.settings, activeProviderId: id },
        })),

      setLoading: (loading) => set({ isLoading: loading }),

      resetToDefault: () => set({ settings: DEFAULT_SETTINGS }),
    }),
    {
      name: "stradust-settings",
      partialize: (state) => ({ settings: state.settings }),
    }
  )
);
