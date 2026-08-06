import { useState, useEffect } from "react";
import { Minus, Square, X, Maximize2, Fullscreen, Minimize2 } from "lucide-react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { isTauri } from "@/lib/tauri";

export function TitleBar() {
  const [isMaximized, setIsMaximized] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const { settings } = useSettingsStore();
  const collapsed = settings.appearance.sidebarCollapsed;

  /** Tauri 2 窗口拖拽 — 使用 startDragging() API（Windows 兼容） */
  const handleDragStart = async (e: React.MouseEvent) => {
    // 如果点击的是按钮区域，不触发拖拽
    if ((e.target as HTMLElement).closest("button")) return;
    if (!isTauri()) return;
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      await getCurrentWindow().startDragging();
    } catch (err) {
      // startDragging 失败时静默（非关键功能）
    }
  };

  const handleMinimize = async () => {
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      await getCurrentWindow().minimize();
    } catch {}
  };

  const handleMaximize = async () => {
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
    } catch {}
  };

  const handleFullscreen = async () => {
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
    } catch {}
  };

  useEffect(() => {
    let unlisten: (() => void) | null = null;
    import("@tauri-apps/api/window").then(({ getCurrentWindow }) => {
      getCurrentWindow().onResized(() => {
        getCurrentWindow().isFullscreen().then((fs) => setIsFullscreen(fs));
        getCurrentWindow().isMaximized().then((m) => setIsMaximized(m));
      }).then((fn) => { unlisten = fn; });
    });
    return () => { unlisten?.(); };
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "F11") { e.preventDefault(); handleFullscreen(); }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  });

  const handleClose = async () => {
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      await getCurrentWindow().close();
    } catch {}
  };

  const sidebarWidth = collapsed ? "var(--sidebar-collapsed-width)" : "var(--sidebar-width)";

  return (
    <div
      className="flex h-[var(--titlebar-height)] items-center titlebar-surface select-none"
      onMouseDown={handleDragStart}
    >
      <div
        className="flex h-full shrink-0 items-center justify-center border-r border-white/[0.04]"
        style={{ width: sidebarWidth }}
      >
        <span className="text-xs font-semibold" style={{ color: "var(--color-primary)" }}>
          星尘
        </span>
      </div>

      <div className="flex h-full flex-1 items-center justify-between px-4">
        <span className="text-xs" style={{ color: "var(--color-muted-foreground)" }}>
          AI 伴侣
        </span>

        <div className="flex items-center gap-0.5">
          <button onMouseDown={(e) => e.stopPropagation()} onClick={handleMinimize} className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-muted-foreground)] hover:bg-white/10 hover:text-[var(--color-card-foreground)] transition-all duration-200 rounded">
            <Minus className="h-3.5 w-3.5" />
          </button>
          <button onMouseDown={(e) => e.stopPropagation()} onClick={handleMaximize} className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-muted-foreground)] hover:bg-white/10 hover:text-[var(--color-card-foreground)] transition-all duration-200 rounded">
            {isMaximized ? <Square className="h-2.5 w-2.5" /> : <Maximize2 className="h-3 w-3" />}
          </button>
          <button onMouseDown={(e) => e.stopPropagation()} onClick={handleFullscreen} className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-primary)]/70 hover:bg-[var(--color-primary)]/15 hover:text-[var(--color-primary)] transition-all duration-200 rounded" title={isFullscreen ? "退出全屏 (F11)" : "全屏 (F11)"}>
            {isFullscreen ? <Minimize2 className="h-3 w-3" /> : <Fullscreen className="h-3 w-3" />}
          </button>
          <button onMouseDown={(e) => e.stopPropagation()} onClick={handleClose} className="inline-flex h-7 w-7 items-center justify-center text-[var(--color-muted-foreground)] hover:bg-red-500/80 hover:text-white transition-all duration-200 rounded">
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
