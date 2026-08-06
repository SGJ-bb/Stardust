import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { ThemeName } from "@/types/settings";
import { useSettingsStore } from "@/stores/useSettingsStore";

interface ThemeState {
  /** 当前主题 */
  currentTheme: ThemeName;
  /** 是否暗色模式 */
  darkMode: boolean;
  /** 当前气泡皮肤 */
  bubbleSkin: string;
  /** 当前头像边框 */
  avatarFrame: string;

  /** 设置主题（同步到useSettingsStore） */
  setTheme: (theme: ThemeName) => void;
  /** 设置暗色模式（同步到useSettingsStore） */
  setDarkMode: (darkMode: boolean) => void;
  /** 设置气泡皮肤 */
  setBubbleSkin: (skin: string) => void;
  /** 设置头像边框 */
  setAvatarFrame: (frame: string) => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      currentTheme: "sakura",
      darkMode: false,
      bubbleSkin: "default",
      avatarFrame: "default",

      setTheme: (theme) => {
        document.documentElement.setAttribute("data-theme", theme);
        // 同步到useSettingsStore
        useSettingsStore.getState().setTheme(theme);
        set({ currentTheme: theme });
      },

      setDarkMode: (darkMode) => {
        document.documentElement.classList.toggle("dark", darkMode);
        // 同步到useSettingsStore
        useSettingsStore.getState().setDarkMode(darkMode);
        set({ darkMode });
      },

      setBubbleSkin: (skin) => set({ bubbleSkin: skin }),
      setAvatarFrame: (frame) => set({ avatarFrame: frame }),
    }),
    {
      name: "stradust-theme",
      partialize: (state) => ({
        currentTheme: state.currentTheme,
        darkMode: state.darkMode,
        bubbleSkin: state.bubbleSkin,
        avatarFrame: state.avatarFrame,
      }),
    }
  )
);
