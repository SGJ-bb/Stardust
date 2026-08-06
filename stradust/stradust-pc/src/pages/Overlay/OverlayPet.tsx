import { useState, useCallback, useEffect } from "react";
import { Live2DCanvas } from "@/components/live2d/Live2DCanvas";
import { ChatInput } from "@/components/chat/ChatInput";
import { useChatStore } from "@/stores/useChatStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { isTauri } from "@/lib/tauri";
import { X, Minus, MessageCircle } from "lucide-react";

/**
 * 桌宠模式 — 独立透明悬浮窗（Desktop Pet）
 *
 * 特性：
 * - 完全透明背景，浮在桌面上
 * - 只有 Live2D 角色和聊天输入栏
 * - 可拖拽移动到桌面任意位置
 * - 始终置顶
 */
export function OverlayPet() {
  const { settings } = useSettingsStore();
  const { addUserMessage, currentPersonaId } = useChatStore();
  const { activePersona } = usePersonaStore();
  const [showChat, setShowChat] = useState(false);
  const [showControls, setShowControls] = useState(false);

  const personaId = currentPersonaId ?? activePersona?.id;
  const personaName = activePersona?.name ?? "星尘";

  /** 发送消息 */
  const handleSend = useCallback((content: string) => {
    if (!personaId) return;
    addUserMessage(personaId, content);
    setShowChat(false);
  }, [personaId, addUserMessage]);

  /** 关闭桌宠窗口 → 回主界面 */
  const handleClose = async () => {
    if (!isTauri()) return;
    try {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("show_overlay", { visible: false });
      // 显示并聚焦主窗口
      const { Window } = await import("@tauri-apps/api/window");
      const mainWin = await Window.getByLabel("main");
      if (mainWin) {
        mainWin.show();
        mainWin.setFocus();
      }
    } catch {}
  };

  /** 最小化（隐藏桌宠） */
  const handleMinimize = async () => {
    if (!isTauri()) return;
    try {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("show_overlay", { visible: false });
    } catch {}
  };

  /** 窗口拖拽（Tauri 2 startDragging API） */
  const handleDragStart = async (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest("button")) return;
    if (!isTauri()) return;
    try {
      const { getCurrentWindow } = await import("@tauri-apps/api/window");
      await getCurrentWindow().startDragging();
    } catch {}
  };

  // 鼠标进入窗口时显示控制按钮
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const show = () => { clearTimeout(timer); setShowControls(true); };
    const hide = () => { timer = setTimeout(() => setShowControls(false), 3000); };
    document.addEventListener("mouseenter", show);
    document.addEventListener("mouseleave", hide);
    return () => { clearTimeout(timer); document.removeEventListener("mouseenter", show); document.removeEventListener("mouseleave", hide); };
  }, []);

  return (
    <div
      className="relative w-full h-full overflow-hidden select-none"
      style={{ background: "transparent" }}
      onMouseDown={handleDragStart}
    >
      {/* ══════════ Live2D 角色（主体） ══════════ */}
      <div className="absolute inset-0 flex items-end justify-center pb-4">
        <Live2DCanvas
          scale={settings.appearance.live2dScale * 1.6}
          opacity={settings.appearance.live2dOpacity}
        />
      </div>

      {/* ══════════ 控制栏（悬停显示） ══════════ */}
      <div
        className={`absolute top-0 left-0 right-0 z-50 transition-opacity duration-200 ${showControls ? "opacity-100" : "opacity-0"}`}
      >
        {/* 拖拽区域 */}
        <div className="h-6 flex items-center justify-between px-1.5">
          <span className="text-[9px] text-white/50 drop-shadow pointer-events-none">
            {personaName}
          </span>
          <div className="flex items-center gap-0.5 pointer-events-auto">
            <button onMouseDown={(e) => e.stopPropagation()} onClick={() => setShowChat(!showChat)} className={`p-1 rounded transition-colors ${showChat ? "text-[var(--color-primary)]" : "text-white/40 hover:text-white"}`} title="聊天">
              <MessageCircle className="h-3 w-3" />
            </button>
            <button onMouseDown={(e) => e.stopPropagation()} onClick={handleMinimize} className="p-1 rounded text-white/40 hover:text-white transition-colors" title="最小化">
              <Minus className="h-3 w-3" />
            </button>
            <button onMouseDown={(e) => e.stopPropagation()} onClick={handleClose} className="p-1 rounded text-white/40 hover:text-red-400 transition-colors" title="关闭">
              <X className="h-3 w-3" />
            </button>
          </div>
        </div>
      </div>

      {/* ══════════ 聊天输入框（底部弹出） ══════════ */}
      {showChat && (
        <div className="absolute bottom-1 left-1 right-1 z-40 pointer-events-auto animate-in slide-in-from-bottom-2 duration-200">
          <div className="rounded-lg bg-black/60 backdrop-blur-xl p-1.5 border border-white/10 shadow-lg shadow-black/30">
            <ChatInput
              onSend={handleSend}
              placeholder={`跟 ${personaName} 说...`}
              compact
            />
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={() => setShowChat(false)}
              className="block w-full text-center text-[9px] text-white/30 hover:text-white/60 mt-1 transition-colors"
            >
              收起
            </button>
          </div>
        </div>
      )}

      {/* ══════════ 双击交互区 ══════════ */}
      <div
        className="absolute inset-0 z-20"
        onDoubleClick={() => setShowChat((v) => !v)}
        title="双击打开聊天"
      />
    </div>
  );
}
