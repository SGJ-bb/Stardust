import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "@/styles/globals.css";

// 开发模式下暴露 stores 到 window，方便自动化测试
if (import.meta.env.DEV) {
  const stores = [
    ["useChatStore", () => import("@/stores/useChatStore")],
    ["usePersonaStore", () => import("@/stores/usePersonaStore")],
    ["useThemeStore", () => import("@/stores/useThemeStore")],
    ["useSettingsStore", () => import("@/stores/useSettingsStore")],
    ["useMemoryStore", () => import("@/stores/useMemoryStore")],
    ["useDiaryStore", () => import("@/stores/useDiaryStore")],
    ["useMomentsStore", () => import("@/stores/useMomentsStore")],
    ["useCalendarStore", () => import("@/stores/useCalendarStore")],
    ["useAchievementStore", () => import("@/stores/useAchievementStore")],
    ["useGroupChatStore", () => import("@/stores/useGroupChatStore")],
    ["useStickerStore", () => import("@/stores/useStickerStore")],
    ["useCapsuleStore", () => import("@/stores/useCapsuleStore")],
    ["useAlbumStore", () => import("@/stores/useAlbumStore")],
    ["useVirtualWorldStore", () => import("@/stores/useVirtualWorldStore")],
    ["usePluginStore", () => import("@/stores/usePluginStore")],
  ] as const;
  for (const [name, loader] of stores) {
    loader().then((m) => {
      (window as unknown as Record<string, unknown>)[`__${name.replace("use", "").replace("Store", "").toLowerCase()}_store`] = (m as Record<string, unknown>)[name];
    });
  }
  // 常用别名
  import("@/stores/useChatStore").then((m) => { (window as unknown as Record<string, unknown>).__chatStore = m.useChatStore; });
  import("@/stores/usePersonaStore").then((m) => { (window as unknown as Record<string, unknown>).__personaStore = m.usePersonaStore; });
  import("@/stores/useMemoryStore").then((m) => { (window as unknown as Record<string, unknown>).__memoryStore = m.useMemoryStore; });
  import("@/stores/useThemeStore").then((m) => { (window as unknown as Record<string, unknown>).__themeStore = m.useThemeStore; });
  import("@/stores/useSettingsStore").then((m) => { (window as unknown as Record<string, unknown>).__settingsStore = m.useSettingsStore; });
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
