import { useState, useCallback, useEffect, useMemo } from "react";
import { Live2DCanvas } from "@/components/live2d/Live2DCanvas";
import { ChatInput } from "@/components/chat/ChatInput";
import { useChatStore } from "@/stores/useChatStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { isTauri } from "@/lib/tauri";
import { streamChat } from "@/lib/tauri";
import { useTauriEvent } from "@/hooks/useTauriEvent";
import { X, Minus, MessageCircle, Send } from "lucide-react";

/** 默认 Live2D 模型路径 */
const DEFAULT_MODEL_PATH = "F:\\stradust\\vtuber\\小恶魔.model3.json";

/**
 * 桌宠模式 — 独立透明悬浮窗（Desktop Pet）
 *
 * 特性：
 * - 完全透明背景，浮在桌面上
 * - 只有 Live2D 角色和聊天栏
 * - 可拖拽移动到桌面任意位置
 * - 始终置顶
 * - 支持流式 AI 回复显示
 */
export function OverlayPet() {
  const { settings } = useSettingsStore();
  const { addUserMessage, currentPersonaId, setSending, setStreaming, appendStreamContent, finishStream, streamingContent, isStreaming, messages } = useChatStore();
  const { activePersona } = usePersonaStore();
  const [showChat, setShowChat] = useState(false);
  const [showControls, setShowControls] = useState(false);

  const personaId = currentPersonaId ?? activePersona?.id;
  const personaName = activePersona?.name ?? "星尘";

  /** Live2D 模型路径：优先使用角色配置，否则使用默认模型 */
  const modelPath = useMemo(() => {
    const configured = activePersona?.live2dModelPath;
    if (configured && configured.trim().length > 0) return configured;
    return DEFAULT_MODEL_PATH;
  }, [activePersona?.live2dModelPath]);

  /** 当前角色的最近消息（用于在桌宠中显示对话） */
  const recentMessages = useMemo(() => {
    if (!personaId) return [];
    return messages.filter(m => m.personaId === personaId).slice(-6);
  }, [messages, personaId]);

  // ══════════ 监听后端 chat-stream 事件（AI 流式回复） ══════════
  useTauriEvent<{ type: string; content?: string; finish_reason?: string }>("chat-stream", (event) => {
    switch (event.type) {
      case "content":
        if (event.content) appendStreamContent(event.content);
        break;
      case "done":
        finishStream();
        break;
      case "error":
        console.error("[Pet] Stream error:", event.content);
        finishStream();
        setSending(false);
        break;
    }
  });

  /** 发送消息 + 触发 AI 流式回复 */
  const handleSend = useCallback(async (content: string) => {
    if (!personaId) return;
    addUserMessage(personaId, content);
    setSending(true);

    try {
      setStreaming(true);
      await streamChat({ personaId, content });
    } catch (error) {
      console.error("Pet chat failed:", error);
      finishStream();
    } finally {
      setSending(false);
    }
  }, [personaId, addUserMessage, setSending, setStreaming, finishStream]);

  /** 关闭桌宠窗口 → 回主界面 */
  const handleClose = async () => {
    if (!isTauri()) return;
    try {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("show_overlay", { visible: false });
      const { Window } = await import("@tauri-apps/api/window");
      const mainWin = await Window.getByLabel("main");
      if (mainWin) { mainWin.show(); mainWin.setFocus(); }
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

  /** 窗口拖拽 */
  const handleDragStart = async (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest("button, input, textarea")) return;
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
      className="group relative w-full h-full overflow-hidden select-none"
      style={{ background: "transparent" }}
      onMouseDown={handleDragStart}
    >
      {/* ══════════ Live2D 角色（主体） ══════════ */}
      <div className="absolute inset-0 flex items-end justify-center pb-4">
        <Live2DCanvas
          scale={settings.appearance.live2dScale * 1.6}
          opacity={settings.appearance.live2dOpacity}
          modelPath={modelPath}
        />
      </div>

      {/* ══════════ 控制栏（悬停显示） ══════════ */}
      <div className={`absolute top-0 left-0 right-0 z-50 transition-opacity duration-200 ${showControls ? "opacity-100" : "opacity-0"}`}>
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

      {/* ══════════ AI 回复气泡（Live2D 旁边显示） ══════════ */}
      {(isStreaming || streamingContent) && (
        <div className="absolute top-8 left-2 right-2 z-30 max-w-[280px] animate-in fade-in slide-in-from-bottom-2 duration-300">
          <div className="rounded-xl bg-black/70 backdrop-blur-md px-3 py-2 border border-white/10 shadow-lg">
            <p className="text-xs text-white/90 leading-relaxed whitespace-pre-wrap break-words">
              {streamingContent || (
                <span className="inline-flex items-center gap-1 text-white/50">
                  <span className="animate-pulse">思考中...</span>
                </span>
              )}
            </p>
          </div>
        </div>
      )}

      {/* ══════════ 聊天面板（底部弹出） ══════════ */}
      {showChat && (
        <div className="absolute bottom-1 left-1 right-1 z-40 pointer-events-auto animate-in slide-in-from-bottom-2 duration-200">
          <div className="rounded-lg bg-black/60 backdrop-blur-xl p-1.5 border border-white/10 shadow-lg shadow-black/30 flex flex-col gap-1.5 max-h-[260px]">

            {/* 最近对话记录 */}
            {recentMessages.length > 0 && (
              <div className="max-h-[140px] overflow-y-auto space-y-1 px-1 scrollbar-thin scrollbar-thumb-white/20">
                {recentMessages.map((msg) => (
                  <div key={msg.id} className={`text-[10px] max-w-[240px] p-1.5 rounded-lg leading-relaxed ${
                    msg.role === "user"
                      ? "bg-blue-500/30 text-blue-100 ml-auto"
                      : "bg-white/10 text-white/80 mr-auto"
                  }`}>
                    {msg.content}
                  </div>
                ))}
                {/* 流式回复中的内容也显示在这里 */}
                {isStreaming && streamingContent && (
                  <div className="text-[10px] max-w-[240px] p-1.5 rounded-lg bg-white/10 text-white/80 mr-auto leading-relaxed">
                    {streamingContent}
                    <span className="animate-pulse ml-0.5">|</span>
                  </div>
                )}
              </div>
            )}

            {/* 输入框 */}
            <ChatInput onSend={handleSend} placeholder={`跟 ${personaName} 说...`} compact />

            <button onMouseDown={(e) => e.stopPropagation()} onClick={() => setShowChat(false)} className="block w-full text-center text-[9px] text-white/30 hover:text-white/60 mt-0.5 transition-colors pb-0.5">
              收起
            </button>
          </div>
        </div>
      )}

      {/* ══════════ 双击交互区（排除控制栏和聊天框区域） ══════════ */}
      <div
        className="absolute inset-0 z-20"
        onDoubleClick={() => setShowChat((v) => !v)}
        title="双击打开聊天"
      />
    </div>
  );
}
