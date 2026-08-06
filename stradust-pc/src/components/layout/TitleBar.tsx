import { useState, useEffect } from "react";
import {
  Minus,
  Square,
  X,
  Maximize2,
  Fullscreen,
  Minimize2,
} from "lucide-react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { isTauri } from "@/lib/tauri";

export function TitleBar() {
  const [isMaximized, setIsMaximized] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const { settings } = useSettingsStore();
  const collapsed = settings.appearance.sidebarCollapsed;

  const handleMinimize = async () => {
    if (!isTauri()) return;
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      await getCurrentWindow().minimize();
    } catch (error) {
      console.error("Minimize failed:", error);
    }
  };

  const handleMaximize = async () => {
    if (!isTauri()) return;
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      const win = getCurrentWindow();
      if (isMaximized) {
        await win.unmaximize();
        setIsMaximized(false);
      } else {
        await win.maximize();
        setIsMaximized(true);
      }
    } catch (error) {
      console.error("Maximize failed:", error);
    }
  };

  const handleFullscreen = async () => {
    if (!isTauri()) return;
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      const win = getCurrentWindow();
      if (isFullscreen) {
        await win.setFullscreen(false);
        setIsFullscreen(false);
      } else {
        await win.setFullscreen(true);
        setIsFullscreen(true);
      }
    } catch (error) {
      console.error("Fullscreen failed:", error);
    }
  };

  // 监听全屏变化，同步状态
  useEffect(() => {
    if (!isTauri()) return;
    let unlisten: (() => void) | null = null;

    import("@tauri-apps/api/window").then(({ getCurrentWindow }) => {
      getCurrentWindow()
        .onResized(() => {
          getCurrentWindow()
            .isFullscreen()
            .then((fs) => {
              setIsFullscreen(fs);
            });
        })
        .then((fn) => {
          unlisten = fn;
        });
    });

    return () => {
      unlisten?.();
    };
  }, []);

  // F11 快捷键切换全屏
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "F11") {
        e.preventDefault();
        handleFullscreen();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  });

  const handleClose = async () => {
    if (!isTauri()) return;
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      await getCurrentWindow().close();
    } catch (error) {
      console.error("Close failed:", error);
    }
  };

  const sidebarWidth = collapsed
    ? "var(--sidebar-collapsed-width)"
    : "var(--sidebar-width)";

  return (
    <div
      className="flex h-[var(--titlebar-height)] items-center titlebar-surface"
      data-tauri-drag-region
    >
      <div
        className="flex h-full shrink-0 items-center justify-center border-r border-white/[0.04]"
        style={{ width: sidebarWidth }}
        data-tauri-drag-region
      >
        <span
          className="text-xs font-semibold"
          style={{ color: "var(--color-primary)" }}
        >
          星尘
        </span>
      </div>

      <div
        className="flex h-full flex-1 items-center justify-between px-4"
        data-tauri-drag-region
      >
        <span
          className="text-xs"
          style={{ color: "var(--color-muted-foreground)" }}
        >
          AI 伴侣
        </span>

        <div className="titlebar-no-drag flex items-center gap-0.5">
          <button
            onClick={handleMinimize}
            className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-muted-foreground)] hover:bg-white/10 hover:text-[var(--color-card-foreground)] transition-all duration-200 rounded"
          >
            <Minus className="h-3.5 w-3.5" />
          </button>
          <button
            onClick={handleMaximize}
            className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-muted-foreground)] hover:bg-white/10 hover:text-[var(--color-card-foreground)] transition-all duration-200 rounded"
          >
            {isMaximized ? (
              <Square className="h-2.5 w-2.5" />
            ) : (
              <Maximize2 className="h-3 w-3" />
            )}
          </button>
          {/* 全屏按钮 — 稍微突出显示 */}
          <button
            onClick={handleFullscreen}
            className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-primary)]/70 hover:bg-[var(--color-primary)]/15 hover:text-[var(--color-primary)] transition-all duration-200 rounded"
            title={isFullscreen ? "退出全屏 (F11)" : "全屏 (F11)"}
          >
            {isFullscreen ? (
              <Minimize2 className="h-3 w-3" />
            ) : (
              <Fullscreen className="h-3 w-3" />
            )}
          </button>
          <button
            onClick={handleClose}
            className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-muted-foreground)] hover:bg-red-500/80 hover:text-white transition-all duration-200 rounded"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
