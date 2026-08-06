// Agent 对话区 — 消息流 + 输入框 + 工具调用展示

import { useState, useRef, useEffect } from "react";
import { Send, Loader2, Sparkles, Trash2 } from "lucide-react";
import { useAgentStore } from "@/stores/useAgentStore";
import { ToolCallDisplay } from "./ToolCallDisplay";
import type { SkillCategory, AgentMessage } from "@/lib/agent/types";

interface AgentChatProps {
  activeCategory: SkillCategory | "all";
}

// 稳定引用的空数组，避免 useSyncExternalStore 因每次返回新 [] 而无限循环
const EMPTY_MESSAGES: AgentMessage[] = [];

export function AgentChat({ activeCategory }: AgentChatProps) {
  const messages = useAgentStore(
    (s) =>
      s.sessions.find((sess) => sess.id === s.currentSessionId)?.messages ??
      EMPTY_MESSAGES,
  );
  const isStreaming = useAgentStore((s) => s.isStreaming);
  const streamingContent = useAgentStore((s) => s.streamingContent);
  const sendMessage = useAgentStore((s) => s.sendMessage);
  const clearCurrentSession = useAgentStore((s) => s.clearCurrentSession);

  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streamingContent]);

  const handleSend = async () => {
    if (!input.trim() || isStreaming) return;
    const msg = input;
    setInput("");
    await sendMessage(msg);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-full">
      {/* 头部 */}
      <div className="px-6 py-4 border-b border-border/50 flex items-center justify-between flex-shrink-0">
        <div>
          <h1 className="text-base font-semibold flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-primary" />
            Agent 智能体
          </h1>
          <p className="text-xs text-muted-foreground mt-0.5">
            当前模式：{activeCategory === "all" ? "全部技能" : activeCategory}
            {" · "} {messages.length} 条消息
          </p>
        </div>
        <button
          onClick={clearCurrentSession}
          className="p-2 rounded-lg hover:bg-destructive/10 text-muted-foreground hover:text-destructive transition-colors"
          title="清空对话"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>

      {/* 消息区域 */}
      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
        {messages.length === 0 && !isStreaming && (
          <div className="flex flex-col items-center justify-center h-full text-center">
            <div className="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center mb-4">
              <Sparkles className="w-8 h-8 text-primary" />
            </div>
            <h3 className="text-sm font-medium mb-1">Agent 智能体已就绪</h3>
            <p className="text-xs text-muted-foreground max-w-[280px] leading-relaxed">
              选择左侧技能后，告诉我你想做什么。我会自动调用合适的工具来完成任务。
            </p>
            <div className="mt-4 grid grid-cols-2 gap-2 max-w-[320px] w-full text-[11px]">
              {[
                "帮我把这个PDF转成Word",
                "裁剪视频前30秒",
                "运行这段Python代码",
                "总结这篇文章的要点",
              ].map((example) => (
                <button
                  key={example}
                  onClick={() => {
                    setInput(example);
                  }}
                  className="px-3 py-2 rounded-lg bg-secondary/50 hover:bg-secondary text-left
                    text-secondary-foreground transition-colors"
                >
                  {example}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg: AgentMessage) => (
          <MessageBubble key={msg.id} message={msg} />
        ))}

        {/* 流式输出中的内容 */}
        {isStreaming && streamingContent && (
          <div className="flex gap-3">
            <div className="w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center flex-shrink-0 mt-0.5">
              <Sparkles className="w-3.5 h-3.5 text-primary" />
            </div>
            <div className="bg-card rounded-2xl rounded-tl-sm px-4 py-3 max-w-[80%] shadow-sm border border-border/30">
              <p className="text-sm leading-relaxed whitespace-pre-wrap">
                {streamingContent}
              </p>
              <Loader2 className="w-3.5 h-3.5 animate-spin mt-2 text-muted-foreground" />
            </div>
          </div>
        )}

        {/* 工具调用过程展示 */}
        <ToolCallDisplay />

        <div ref={messagesEndRef} />
      </div>

      {/* 输入区 */}
      <div className="px-6 py-4 border-t border-border/50 flex-shrink-0">
        <div className="flex items-end gap-3 bg-card rounded-2xl border border-border/50 p-2 focus-within:ring-1 focus-within:ring-primary/50 focus-within:border-primary/50">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="告诉 Agent 你想做什么..."
            rows={1}
            className="flex-1 bg-transparent px-3 py-2 text-sm resize-none outline-none
              placeholder:text-muted-foreground max-h-32"
            style={{ minHeight: "40px" }}
          />
          <button
            onClick={handleSend}
            disabled={!input.trim() || isStreaming}
            className="p-2.5 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90
              disabled:opacity-50 disabled:cursor-not-allowed transition-all flex-shrink-0"
          >
            {isStreaming ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Send className="w-4 h-4" />
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

/** 单条消息气泡 */
function MessageBubble({
  message,
}: {
  message: import("@/lib/agent/types").AgentMessage;
}) {
  const isUser = message.role === "user";

  if (message.role === "tool") {
    return null; // tool消息不单独显示，整合到assistant中
  }

  return (
    <div className={`flex gap-3 ${isUser ? "flex-row-reverse" : ""}`}>
      {/* 头像 */}
      <div
        className={`w-7 h-7 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 ${
          isUser ? "bg-secondary" : "bg-primary/10"
        }`}
      >
        {isUser ? (
          <span className="text-xs font-medium text-secondary-foreground">
            你
          </span>
        ) : (
          <Sparkles className="w-3.5 h-3.5 text-primary" />
        )}
      </div>

      {/* 内容 */}
      <div className={`max-w-[80%] ${isUser ? "" : ""}`}>
        <div
          className={`rounded-2xl px-4 py-3 shadow-sm border border-border/30 ${
            isUser
              ? "bg-secondary text-secondary-foreground rounded-tr-sm"
              : "bg-card rounded-tl-sm"
          }`}
        >
          <p className="text-sm leading-relaxed whitespace-pre-wrap">
            {message.content}
          </p>

          {/* 工具调用标签 */}
          {message.toolCalls && message.toolCalls.length > 0 && (
            <div className="mt-2 pt-2 border-t border-border/30 space-y-1">
              {message.toolCalls.map((tc) => (
                <div
                  key={tc.id}
                  className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-[11px]
                    ${
                      tc.status === "success"
                        ? "bg-emerald-500/10 text-emerald-600"
                        : tc.status === "failed"
                          ? "bg-red-500/10 text-red-500"
                          : "bg-blue-500/10 text-blue-600"
                    }`}
                >
                  <span>⚡</span>
                  {tc.name}
                  <span
                    className={
                      tc.status === "success"
                        ? "✓"
                        : tc.status === "failed"
                          ? "✗"
                          : "..."
                    }
                  />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 时间戳 */}
        <p
          className={`text-[10px] text-muted-foreground mt-1 ${isUser ? "text-right" : ""}`}
        >
          {new Date(message.timestamp).toLocaleTimeString("zh-CN", {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </p>
      </div>
    </div>
  );
}
