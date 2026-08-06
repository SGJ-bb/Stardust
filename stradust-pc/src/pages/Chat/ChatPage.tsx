import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { ChatBubble } from "@/components/chat/ChatBubble";
import { ChatInput } from "@/components/chat/ChatInput";
import { TypingIndicator } from "@/components/chat/TypingIndicator";
import { ChatSidebar } from "./ChatSidebar";
import { ChatToolbar } from "./ChatToolbar";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useState, useRef, useEffect, useCallback } from "react";
import { streamChat } from "@/lib/tauri";
import { useTauriEvent } from "@/hooks/useTauriEvent";
import { motion, AnimatePresence } from "framer-motion";
import { UserPlus, ArrowRight, AlertCircle, X, Settings } from "lucide-react";

const SUGGESTED_REPLIES = ["你好呀", "今天心情怎么样", "聊聊天吧"];

export function ChatPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const navigate = useNavigate();
  const {
    currentPersonaId,
    messages,
    isSending,
    isStreaming,
    streamingContent,
    setCurrentPersona,
    addUserMessage,
    setSending,
    setStreaming,
    appendStreamContent,
    finishStream,
  } = useChatStore();
  const { personas, getPersonaById } = usePersonaStore();

  // 优先使用 URL 参数 → 当前选中角色 → 自动选第一个角色
  let resolvedPersonaId = urlPersonaId ?? currentPersonaId ?? "";
  // 如果仍然为空，自动选择第一个可用角色
  if (!resolvedPersonaId && personas.length > 0) {
    resolvedPersonaId = personas[0].id;
  }
  const personaId = resolvedPersonaId;

  const [showSidebar, setShowSidebar] = useState(false);
  const [streamError, setStreamError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const rafRef = useRef<number>(0);

  // 自动选择第一个角色时同步到 store（无条件的 effect）
  useEffect(() => {
    if (!urlPersonaId && !currentPersonaId && personas.length > 0) {
      setCurrentPersona(personas[0].id);
    }
  }, [urlPersonaId, currentPersonaId, personas.length]);

  const persona = personaId ? getPersonaById(personaId) : null;

  useEffect(() => {
    if (personaId) {
      setCurrentPersona(personaId);
    }
  }, [personaId, setCurrentPersona]);

  // 监听后端 "chat-stream" 事件
  useTauriEvent<{
    type: string;
    content?: string;
    finish_reason?: string;
    tool_name?: string;
    result?: string;
  }>("chat-stream", (event) => {
    switch (event.type) {
      case "content":
        if (event.content) appendStreamContent(event.content);
        break;
      case "done":
        finishStream();
        break;
      case "error":
        console.error("Stream error:", event.content);
        setStreamError(event.content || "发送失败，请稍后重试");
        finishStream();
        setSending(false);
        break;
      case "tool_result":
        console.log("Tool result:", event.tool_name, event.result);
        break;
    }
  });

  useEffect(() => {
    if (rafRef.current) {
      cancelAnimationFrame(rafRef.current);
    }
    rafRef.current = requestAnimationFrame(() => {
      if (scrollRef.current) {
        scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
      }
    });
    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [messages, streamingContent]);

  const handleSend = useCallback(
    async (content: string, attachments?: string[]) => {
      if (!personaId) {
        console.warn("[Chat] 无法发送消息：未选择角色");
        return;
      }

      setStreamError(null);
      addUserMessage(personaId, content);
      setSending(true);

      try {
        setStreaming(true);
        await streamChat({ personaId, content, attachments });
      } catch (error) {
        console.error("Send failed:", error);
        setStreamError(
          error instanceof Error ? error.message : "发送失败，请稍后重试",
        );
        finishStream();
      } finally {
        setSending(false);
      }
    },
    [personaId, addUserMessage, setSending, setStreaming, finishStream],
  );

  const handleSuggestedReply = useCallback(
    (text: string) => {
      handleSend(text);
    },
    [handleSend],
  );

  // ═══════════ 无角色时的引导界面 ═══════════
  if (!personaId || !persona) {
    return (
      <div className="flex h-full w-full items-center justify-center">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="text-center max-w-sm px-6"
        >
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-primary)]/10 mb-4">
            <UserPlus className="h-8 w-8 text-[var(--color-primary)]" />
          </div>
          <h2 className="text-lg font-bold mb-2 text-[var(--color-card-foreground)]">
            还没有角色
          </h2>
          <p className="text-sm text-[var(--color-muted-foreground)] mb-6">
            创建或选择一个角色开始聊天吧
          </p>
          <div className="flex gap-3 justify-center">
            <button
              onClick={() => navigate("/personas/new")}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[var(--color-primary)] text-white text-sm font-medium hover:brightness-110 transition-all"
            >
              <UserPlus className="h-4 w-4" />
              创建角色
            </button>
            {personas.length > 0 && (
              <button
                onClick={() => navigate("/")}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl border border-[var(--color-border)] text-sm font-medium text-[var(--color-card-foreground)] hover:bg-[var(--color-muted)] transition-colors"
              >
                选择角色
                <ArrowRight className="h-4 w-4" />
              </button>
            )}
          </div>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="flex h-full w-full relative">
      {/* 主聊天区域 */}
      <div className="flex flex-1 flex-col min-w-0 min-h-0">
        {/* 工具栏 */}
        <div className="shrink-0 surface-panel">
          <ChatToolbar
            personaName={persona.name}
            personaAvatar={persona.avatar}
            favorabilityLevel={persona.favorabilityLevel ?? 1}
            onBack={() => navigate("/")}
            onToggleSidebar={() => setShowSidebar(!showSidebar)}
          />
        </div>

        {/* 错误提示条（如未配置 LLM、网络错误等） */}
        <AnimatePresence>
          {streamError && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
              className="shrink-0 overflow-hidden"
            >
              <div className="flex items-start gap-3 px-4 py-2.5 mx-3 mt-2 rounded-xl border border-red-500/30 bg-red-500/10 backdrop-blur-sm">
                <AlertCircle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
                <div className="flex-1 min-w-0 text-sm text-red-600 dark:text-red-400 break-words">
                  {streamError}
                  {streamError.includes("未配置") ||
                  streamError.includes("LLM") ||
                  streamError.includes("提供商") ? (
                    <button
                      onClick={() => navigate("/settings?tab=llm")}
                      className="ml-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-red-500/20 hover:bg-red-500/30 text-red-600 dark:text-red-400 font-medium transition-colors"
                    >
                      <Settings className="h-3 w-3" />
                      去设置
                    </button>
                  ) : null}
                </div>
                <button
                  onClick={() => setStreamError(null)}
                  className="shrink-0 p-1 rounded-md hover:bg-red-500/20 text-red-500 transition-colors"
                  aria-label="关闭错误提示"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* 消息区域 */}
        <div className="flex-1 min-h-0 overflow-hidden">
          <ScrollArea className="h-full w-full">
            <div
              ref={scrollRef}
              className="py-6 px-4 max-w-3xl mx-auto stagger-list"
            >
              {/* 空状态 */}
              {messages.length === 0 && (
                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{
                    duration: 0.5,
                    ease: [0.22, 1, 0.36, 1] as [
                      number,
                      number,
                      number,
                      number,
                    ],
                  }}
                  className="flex flex-col items-center justify-center py-16"
                >
                  {/* 角色头像 */}
                  <div className="relative mb-5 empty-state-icon">
                    <div
                      className="h-24 w-24 rounded-full bg-[var(--color-card)] border border-[var(--color-border)] flex items-center justify-center"
                      style={{ borderRadius: "var(--radius-lg)" }}
                    >
                      {persona.avatar ? (
                        <img
                          src={persona.avatar}
                          alt={persona.name}
                          className="h-20 w-20 rounded-full object-cover"
                          style={{ borderRadius: "var(--radius-md)" }}
                        />
                      ) : (
                        <span className="text-3xl font-bold text-[var(--color-primary)]">
                          {persona.name[0]}
                        </span>
                      )}
                    </div>
                  </div>

                  <h2 className="text-h2 mb-2">{persona.name}</h2>

                  <p className="text-body-sm text-center max-w-sm mb-6">
                    {persona.greeting || "开始和角色聊天吧"}
                  </p>

                  {/* 建议回复 */}
                  <div className="flex flex-wrap items-center justify-center gap-2">
                    {SUGGESTED_REPLIES.map((reply) => (
                      <button
                        key={reply}
                        onClick={() => handleSuggestedReply(reply)}
                        className="suggested-reply"
                      >
                        {reply}
                      </button>
                    ))}
                  </div>
                </motion.div>
              )}

              {/* 消息列表 */}
              <AnimatePresence mode="popLayout">
                {messages.map((message) => (
                  <ChatBubble
                    key={message.id}
                    message={message}
                    personaName={persona.name}
                    personaAvatar={persona.avatar}
                  />
                ))}
              </AnimatePresence>

              {/* 流式输出 */}
              {isStreaming && streamingContent && (
                <ChatBubble
                  message={{
                    id: "streaming",
                    personaId,
                    role: "assistant",
                    content: streamingContent,
                    timestamp: Date.now(),
                    status: "streaming",
                    favorited: false,
                    attachments: [],
                    toolCalls: [],
                    reactions: [],
                  }}
                  personaName={persona.name}
                  personaAvatar={persona.avatar}
                  isStreaming
                />
              )}

              {/* 打字指示器 */}
              {isSending && !isStreaming && <TypingIndicator />}
            </div>
          </ScrollArea>
        </div>

        {/* 输入区域 */}
        <div className="shrink-0">
          <ChatInput
            onSend={handleSend}
            disabled={isSending}
            placeholder={`给 ${persona.name} 发消息...`}
          />
        </div>
      </div>

      {/* 右侧面板 */}
      <AnimatePresence>
        {showSidebar && (
          <motion.div
            initial={{ width: 0, opacity: 0 }}
            animate={{ width: 320, opacity: 1 }}
            exit={{ width: 0, opacity: 0 }}
            transition={{
              duration: 0.3,
              ease: [0.22, 1, 0.36, 1] as [number, number, number, number],
            }}
            className="overflow-hidden border-l border-white/[0.04]"
          >
            <ChatSidebar
              personaId={personaId}
              onClose={() => setShowSidebar(false)}
            />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
