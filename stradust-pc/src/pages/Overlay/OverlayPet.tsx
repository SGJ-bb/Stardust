import { useState, useCallback, useEffect, useMemo, useRef } from "react";
import { Live2DCanvas } from "@/components/live2d/Live2DCanvas";
import PixelPetCanvas, {
  type PixelPetCanvasHandle,
} from "@/components/pixelpet/PixelPetCanvas";
import { ChatInput } from "@/components/chat/ChatInput";
import { useChatStore } from "@/stores/useChatStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { usePixelPetStore } from "@/stores/usePixelPetStore";
import { isTauri } from "@/lib/tauri";
import { streamChat } from "@/lib/tauri";
import { useTauriEvent } from "@/hooks/useTauriEvent";
import { X, Minus, MessageCircle, Monitor, Box } from "lucide-react";
import { getRenderConfig } from "@/lib/pixelpet/types";
import type { PetMode } from "@/lib/pixelpet/types";

/** 默认 Live2D 模型路径（原始文件路径） */
const DEFAULT_MODEL_PATH = "F:\\stradust\\vtuber\\小恶魔.model3.json";

/**
 * 将本地文件路径转换为 WebView 可访问的 URL
 *
 * - Tauri 环境：使用 convertFileSrc 转换为 tauri://localhost/... 形式，让 WebView 能读取本地文件
 * - 浏览器环境：返回原始路径（用于后续加载失败提示，浏览器无法直接访问本地文件）
 */
async function toModelUrl(rawPath: string): Promise<string> {
  if (!isTauri()) return rawPath;
  try {
    const { convertFileSrc } = await import("@tauri-apps/api/core");
    return convertFileSrc(rawPath);
  } catch (err) {
    console.warn("[OverlayPet] convertFileSrc 失败，回退到原始路径:", err);
    return rawPath;
  }
}

/**
 * 桌宠模式 — 独立透明悬浮窗（Desktop Pet）
 *
 * 支持两种渲染模式:
 * - Live2D: 基于Cubism SDK的模型渲染
 * - Pixel Pet: 基于Canvas 2D的像素动画精灵
 */
export function OverlayPet() {
  const { settings } = useSettingsStore();
  const {
    addUserMessage,
    currentPersonaId,
    setSending,
    setStreaming,
    appendStreamContent,
    finishStream,
    streamingContent,
    isStreaming,
    messages,
  } = useChatStore();
  const { activePersona } = usePersonaStore();
  const {
    petMode,
    activePet,
    currentPetActions,
    loadActivePet,
    loadPetActions,
    loadGenConfig,
    setPetMode,
  } = usePixelPetStore();
  const [showChat, setShowChat] = useState(false);
  const [showControls, setShowControls] = useState(false);

  // PixelPet Canvas 引用（用于外部触发动作）
  const pixelPetRef = useRef<PixelPetCanvasHandle>(null);
  const [pixelModeReady, setPixelModeReady] = useState(false);

  const personaId = currentPersonaId ?? activePersona?.id;
  const personaName = (activePet?.name || activePersona?.name) ?? "星尘";

  // ══════════ 初始化：加载像素宠物数据 ══════════
  useEffect(() => {
    if (isTauri()) {
      loadGenConfig();
      loadActivePet();
    }
  }, []);

  useEffect(() => {
    if (activePet) {
      loadPetActions(activePet.id);
    }
  }, [activePet?.id]);

  // 当动作数据就绪后标记 pixel mode ready
  useEffect(() => {
    if (petMode === "pixel" && currentPetActions.length > 0) {
      setPixelModeReady(true);
    } else if (petMode === "live2d") {
      setPixelModeReady(false);
    }
  }, [petMode, currentPetActions.length]);

  /** Live2D 模型路径（异步转换：Tauri 下走 convertFileSrc） */
  const configuredModelPath = activePersona?.live2dModelPath;
  const rawModelPath =
    configuredModelPath && configuredModelPath.trim().length > 0
      ? configuredModelPath
      : DEFAULT_MODEL_PATH;
  const [modelPath, setModelPath] = useState<string>(rawModelPath);
  useEffect(() => {
    let cancelled = false;
    toModelUrl(rawModelPath).then((url) => {
      if (!cancelled) setModelPath(url);
    });
    return () => {
      cancelled = true;
    };
  }, [rawModelPath]);

  /** 当前角色的最近消息 */
  const recentMessages = useMemo(() => {
    if (!personaId) return [];
    return messages.filter((m) => m.personaId === personaId).slice(-6);
  }, [messages, personaId]);

  // ══════════ 监听 chat-stream 事件 ══════════
  useTauriEvent<{ type: string; content?: string; finish_reason?: string }>(
    "chat-stream",
    (event) => {
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
    },
  );

  /** 发送消息 */
  const handleSend = useCallback(
    async (content: string) => {
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
    },
    [personaId, addUserMessage, setSending, setStreaming, finishStream],
  );

  /** 关闭桌宠窗口 */
  const handleClose = async () => {
    if (!isTauri()) return;
    try {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("show_overlay", { visible: false });
      const { Window } = await import("@tauri-apps/api/window");
      const mainWin = await Window.getByLabel("main");
      if (mainWin) {
        mainWin.show();
        mainWin.setFocus();
      }
    } catch {}
  };

  /** 最小化 */
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

  /** 切换渲染模式 */
  const handleToggleMode = useCallback((mode: PetMode) => {
    setPetMode(mode);
  }, []);

  /** 像素模式：单击 → 随机互动动作 */
  const handlePixelClick = useCallback(() => {
    if (petMode !== "pixel") return;
    const interactions = ["happy", "wave", "surprised"];
    const randomAction =
      interactions[Math.floor(Math.random() * interactions.length)];
    pixelPetRef.current?.triggerInteraction(randomAction);
  }, [petMode]);

  // 控制栏显示/隐藏
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const show = () => {
      clearTimeout(timer);
      setShowControls(true);
    };
    const hide = () => {
      timer = setTimeout(() => setShowControls(false), 3000);
    };
    document.addEventListener("mouseenter", show);
    document.addEventListener("mouseleave", hide);
    return () => {
      clearTimeout(timer);
      document.removeEventListener("mouseenter", show);
      document.removeEventListener("mouseleave", hide);
    };
  }, []);

  return (
    <div
      className="group relative w-full h-full overflow-hidden select-none"
      style={{ background: "transparent" }}
      onMouseDown={handleDragStart}
    >
      {/* ══════════ 渲染区域：Live2D 或 Pixel Pet ══════════ */}
      <div className="absolute inset-0 flex items-end justify-center pb-4">
        {petMode === "live2d" ? (
          <Live2DCanvas
            scale={settings.appearance.live2dScale * 1.6}
            opacity={settings.appearance.live2dOpacity}
            modelPath={modelPath}
          />
        ) : pixelModeReady ? (
          <PixelPetCanvas
            ref={pixelPetRef}
            actions={currentPetActions}
            petMode={petMode}
            scale={activePet ? getRenderConfig(activePet).scale : 3}
            onClick={handlePixelClick}
            onDoubleClick={() => setShowChat((v) => !v)}
            className="w-[192px] h-[192px]"
          />
        ) : (
          /* 像素模式但尚未加载数据时显示占位 */
          <div className="flex items-center justify-center w-[192px] h-[192px] text-white/30 text-xs">
            加载中...
          </div>
        )}
      </div>

      {/* ══════════ 控制栏（悬停显示） ══════════ */}
      <div
        className={`absolute top-0 left-0 right-0 z-50 transition-opacity duration-200 ${showControls ? "opacity-100" : "opacity-0"}`}
      >
        <div className="h-6 flex items-center justify-between px-1.5">
          <span className="text-[9px] text-white/50 drop-shadow pointer-events-none">
            {personaName} · {petMode === "live2d" ? "Live2D" : "Pixel"}
          </span>
          <div className="flex items-center gap-0.5 pointer-events-auto">
            {/* 模式切换按钮 */}
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={() =>
                handleToggleMode(petMode === "live2d" ? "pixel" : "live2d")
              }
              className={`p-1 rounded transition-colors ${petMode === "pixel" ? "text-green-400" : "text-white/40 hover:text-white"}`}
              title={petMode === "live2d" ? "切换到像素宠物" : "切换到 Live2D"}
            >
              {petMode === "pixel" ? (
                <Box className="h-3 w-3" />
              ) : (
                <Monitor className="h-3 w-3" />
              )}
            </button>
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={() => setShowChat(!showChat)}
              className={`p-1 rounded transition-colors ${showChat ? "text-[var(--color-primary)]" : "text-white/40 hover:text-white"}`}
              title="聊天"
            >
              <MessageCircle className="h-3 w-3" />
            </button>
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={handleMinimize}
              className="p-1 rounded text-white/40 hover:text-white transition-colors"
              title="最小化"
            >
              <Minus className="h-3 w-3" />
            </button>
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={handleClose}
              className="p-1 rounded text-white/40 hover:text-red-400 transition-colors"
              title="关闭"
            >
              <X className="h-3 w-3" />
            </button>
          </div>
        </div>
      </div>

      {/* ══════════ AI 回复气泡 ══════════ */}
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
                  <div
                    key={msg.id}
                    className={`text-[10px] max-w-[240px] p-1.5 rounded-lg leading-relaxed ${
                      msg.role === "user"
                        ? "bg-blue-500/30 text-blue-100 ml-auto"
                        : "bg-white/10 text-white/80 mr-auto"
                    }`}
                  >
                    {msg.content}
                  </div>
                ))}
                {isStreaming && streamingContent && (
                  <div className="text-[10px] max-w-[240px] p-1.5 rounded-lg bg-white/10 text-white/80 mr-auto leading-relaxed">
                    {streamingContent}
                    <span className="animate-pulse ml-0.5">|</span>
                  </div>
                )}
              </div>
            )}

            {/* 输入框 */}
            <ChatInput
              onSend={handleSend}
              placeholder={`跟 ${personaName} 说...`}
              compact
            />

            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={() => setShowChat(false)}
              className="block w-full text-center text-[9px] text-white/30 hover:text-white/60 mt-0.5 transition-colors pb-0.5"
            >
              收起
            </button>
          </div>
        </div>
      )}

      {/* ══════════ 双击交互区（仅 Live2D 模式下生效，Pixel 模式由 canvas 自处理）╪═══════════ */}
      {petMode === "live2d" && (
        <div
          className="absolute inset-0 z-20"
          onDoubleClick={() => setShowChat((v) => !v)}
          title="双击打开聊天"
        />
      )}
    </div>
  );
}
