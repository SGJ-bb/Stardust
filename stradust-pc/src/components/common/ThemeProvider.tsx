import { useEffect } from "react";
import { useThemeStore } from "@/stores/useThemeStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { ThemeName } from "@/types/settings";

/** 12套主题配色名称 */
export const THEME_LABELS: Record<ThemeName, string> = {
  sakura: "樱粉",
  peach: "桃粉",
  violet: "紫罗兰",
  ocean: "海蓝",
  emerald: "翡翠",
  sunset: "日落",
  rosegold: "玫瑰金",
  mint: "薄荷",
  midnight: "暗夜",
  tea: "茶香",
  cyberpunk: "赛博朋克",
  chinese: "华夏风韵",
};

/** 所有主题名列表 */
export const THEME_LIST: ThemeName[] = [
  "sakura", "peach", "violet", "ocean", "emerald",
  "sunset", "rosegold", "mint", "midnight",
  "tea", "cyberpunk", "chinese",
];

interface ThemeProviderProps {
  children: React.ReactNode;
}

/**
 * 主题提供者组件
 * 初始化主题并监听设置变化
 * 确保页面加载时立即设置 data-theme 属性，避免空白闪烁
 */
export function ThemeProvider({ children }: ThemeProviderProps) {
  const { currentTheme, darkMode, setTheme, setDarkMode } = useThemeStore();
  const { settings } = useSettingsStore();

  /** 初始化主题 */
  useEffect(() => {
    setTheme(settings.appearance.theme);
    setDarkMode(settings.appearance.darkMode);
  }, [settings.appearance.theme, settings.appearance.darkMode, setTheme, setDarkMode]);

  /** 应用主题到DOM — 立即设置，确保首次渲染就有颜色 */
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", currentTheme);
    document.documentElement.classList.toggle("dark", darkMode);
  }, [currentTheme, darkMode]);

  return <>{children}</>;
}
