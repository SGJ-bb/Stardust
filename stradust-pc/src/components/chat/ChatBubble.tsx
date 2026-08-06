import { cn } from "@/lib/utils";
import { formatTime } from "@/lib/utils";
import type { ChatMessage, ToolCall, Attachment } from "@/types/chat";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { MessageActions } from "./MessageActions";
import { EmojiReaction } from "./EmojiReaction";
import { Wrench, CheckCircle, XCircle, Loader2, ChevronDown, ChevronRight } from "lucide-react";
import { useState } from "react";
import { motion } from "framer-motion";

interface ChatBubbleProps {
  message: ChatMessage;
  personaName?: string;
  personaAvatar?: string;
  showTime?: boolean;
  isStreaming?: boolean;
}

export function ChatBubble({
  message,
  personaName,
  personaAvatar,
  showTime = true,
  isStreaming = false,
}: ChatBubbleProps) {
  const isUser = message.role === "user";
  const isSystem = message.role === "system";

  if (isSystem) {
    return (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
        className="flex justify-center py-2"
      >
        <div className="bubble-system">{message.content}</div>
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
      className={cn(
        "flex gap-3 px-5 py-2.5 group reveal-up",
        isUser ? "flex-row-reverse" : "flex-row"
      )}
    >
      {/* Avatar - 用户头像：纯色 var(--color-primary)，AI 头像：var(--color-secondary) */}
      <div className="shrink-0 pt-1">
        <Avatar className="h-9 w-9">
          {isUser ? (
            <AvatarFallback className="bg-[var(--color-primary)] text-white text-xs font-medium">
              我
            </AvatarFallback>
          ) : (
            <>
              <AvatarImage src={personaAvatar} />
              <AvatarFallback className="bg-[var(--color-secondary)] text-white text-xs font-medium">
                {personaName?.[0] ?? "AI"}
              </AvatarFallback>
            </>
          )}
        </Avatar>
      </div>

      {/* Message content */}
      <div className={cn("max-w-[72%] space-y-1.5", isUser ? "items-end" : "items-start")}>
        {/* Name */}
        {!isUser && personaName && (
          <span className="text-label px-1">
            {personaName}
          </span>
        )}

        {/* Attachments - 使用 surface-card */}
        {message.attachments.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {message.attachments.map((attachment) => (
              <AttachmentRenderer key={attachment.id} attachment={attachment} />
            ))}
          </div>
        )}

        {/* Bubble - 用户气泡用 bubble-user（纯色 + shadow），AI 气泡用 bubble-assistant（实心 + border） */}
        {(message.content || isStreaming) && (
          <div className="relative">
            <div
              className={cn(
                "inline-block px-4 py-2.5 text-[14px] leading-relaxed",
                isUser ? "bubble-user" : "bubble-assistant"
              )}
            >
              {message.content}
              {isStreaming && (
                <span className="inline-flex gap-1 ml-1.5 align-middle">
                  <span className="typing-dot w-1 h-1 rounded-full bg-current opacity-40" />
                  <span className="typing-dot w-1 h-1 rounded-full bg-current opacity-40" style={{ animationDelay: "0.2s" }} />
                  <span className="typing-dot w-1 h-1 rounded-full bg-current opacity-40" style={{ animationDelay: "0.4s" }} />
                </span>
              )}
            </div>

            {/* Message actions */}
            <div className="invisible group-hover:visible transition-opacity">
              <MessageActions
                messageId={message.id}
                favorited={message.favorited}
                role={message.role}
                content={message.content}
              />
            </div>
          </div>
        )}

        {/* Tool calls - 使用 surface-card 而不是 glass-card */}
        {message.toolCalls.length > 0 && (
          <div className="space-y-1.5">
            {message.toolCalls.map((toolCall) => (
              <ToolCallRenderer key={toolCall.id} toolCall={toolCall} />
            ))}
          </div>
        )}

        {/* Reactions */}
        {message.reactions.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {message.reactions.map((reaction, index) => (
              <EmojiReaction
                key={index}
                emoji={reaction.emoji}
                count={reaction.count}
                reacted={reaction.reacted}
              />
            ))}
          </div>
        )}

        {/* Time - 使用 text-caption 类 */}
        {showTime && (
          <span className="text-caption px-1">
            {formatTime(message.timestamp)}
          </span>
        )}
      </div>
    </motion.div>
  );
}

function AttachmentRenderer({ attachment }: { attachment: Attachment }) {
  if (attachment.type === "image") {
    return (
      <div className="surface-card overflow-hidden max-w-[200px] max-h-[200px]">
        <img
          src={attachment.url}
          alt={attachment.name}
          className="w-full h-full object-cover"
          loading="lazy"
        />
      </div>
    );
  }

  /* 非图片附件使用 surface-card */
  return (
    <div className="surface-card flex items-center gap-2 px-3 py-2 text-sm">
      <span className="text-white/30">📎</span>
      <span className="truncate text-white/70">{attachment.name}</span>
    </div>
  );
}

/* 工具调用卡片使用 surface-card */
function ToolCallRenderer({ toolCall }: { toolCall: ToolCall }) {
  const [expanded, setExpanded] = useState(false);

  const StatusIcon = () => {
    switch (toolCall.status) {
      case "pending":
        return <Loader2 className="h-3.5 w-3.5 text-white/30 animate-spin" />;
      case "running":
        return <Loader2 className="h-3.5 w-3.5 text-blue-400 animate-spin" />;
      case "completed":
        return <CheckCircle className="h-3.5 w-3.5 text-emerald-400" />;
      case "error":
        return <XCircle className="h-3.5 w-3.5 text-red-400" />;
    }
  };

  const statusText = {
    pending: "等待中",
    running: "运行中",
    completed: "完成",
    error: "错误",
  }[toolCall.status];

  return (
    <div className="surface-card overflow-hidden text-xs">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-1.5 w-full px-3 py-2 text-left hover:bg-white/[0.04] transition-colors"
      >
        <Wrench className="h-3 w-3 text-white/30" />
        <span className="font-medium text-white/70 flex-1">{toolCall.name}</span>
        <StatusIcon />
        <span className="text-white/25">{statusText}</span>
        {expanded ? <ChevronDown className="h-3 w-3 text-white/25" /> : <ChevronRight className="h-3 w-3 text-white/25" />}
      </button>

      {expanded && (
        <div className="px-3 py-2 border-t border-white/[0.04] space-y-1.5">
          <div>
            <span className="text-white/30">参数:</span>
            <pre className="mt-0.5 text-white/60 whitespace-pre-wrap break-all font-mono text-[10px]">
              {formatJsonString(toolCall.arguments)}
            </pre>
          </div>
          {toolCall.result && (
            <div>
              <span className="text-white/30">结果:</span>
              <pre className="mt-0.5 text-white/60 whitespace-pre-wrap break-all font-mono text-[10px]">
                {formatJsonString(toolCall.result)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function formatJsonString(str: string): string {
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch {
    return str;
  }
}
