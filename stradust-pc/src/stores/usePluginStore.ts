import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { PluginConfig } from "@/types/settings";

interface PluginState {
  /** 插件列表 */
  plugins: PluginConfig[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置插件列表 */
  setPlugins: (plugins: PluginConfig[]) => void;
  /** 切换插件启用状态 */
  togglePlugin: (pluginId: string) => void;
  /** 更新插件设置 */
  updatePluginSettings: (pluginId: string, settings: Record<string, unknown>) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const usePluginStore = create<PluginState>()(
  persist(
    (set) => ({
      plugins: [],
      isLoading: false,

      setPlugins: (plugins) => set({ plugins }),

      togglePlugin: (pluginId) =>
        set((state) => ({
          plugins: state.plugins.map((p) =>
            p.id === pluginId ? { ...p, enabled: !p.enabled } : p
          ),
        })),

      updatePluginSettings: (pluginId, settings) =>
        set((state) => ({
          plugins: state.plugins.map((p) =>
            p.id === pluginId ? { ...p, settings: { ...p.settings, ...settings } } : p
          ),
        })),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-plugin",
      partialize: (state) => ({ plugins: state.plugins }),
    }
  )
);
