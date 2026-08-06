import { useCallback, useEffect } from "react";
import { useThemeStore } from "@/stores/useThemeStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { ThemeName } from "@/types/settings";

/**
 * 主题切换钩子
 * 提供主题切换、暗色模式切换等功能
 */
export function useTheme() {
  const { currentTheme, darkMode, setTheme, setDarkMode } = useThemeStore();
  const { settings, updateSettings } = useSettingsStore();

  /** 切换主题 */
  const changeTheme = useCallback((theme: ThemeName) => {
    setTheme(theme);
    updateSettings({
      appearance: { ...settings.appearance, theme },
    });
  }, [setTheme, settings.appearance, updateSettings]);

  /** 切换暗色模式 */
  const toggleDarkMode = useCallback(() => {
    const newDarkMode = !darkMode;
    setDarkMode(newDarkMode);
    updateSettings({
      appearance: { ...settings.appearance, darkMode: newDarkMode },
    });
  }, [darkMode, setDarkMode, settings.appearance, updateSettings]);

  /** 初始化主题 */
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", currentTheme);
    document.documentElement.classList.toggle("dark", darkMode);
  }, [currentTheme, darkMode]);

  return {
    currentTheme,
    darkMode,
    changeTheme,
    toggleDarkMode,
  };
}
