import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreHorizontal, Star, Copy, Trash2, RefreshCw } from "lucide-react";
import { useState } from "react";
import { useChatStore } from "@/stores/useChatStore";
import { toggleFavoriteMessage, deleteMessage as deleteMessagePersisted } from "@/lib/tauri";
import type { MessageRole } from "@/types/chat";

interface MessageActionsProps {
  messageId: string;
  favorited: boolean;
  role: MessageRole;
  /** 消息文本内容，用于复制 */
  content?: string;
  /** 所属角色（重新生成需要） */
  personaId?: string;
}

/**
 * 消息操作菜单组件
 * 支持收藏/复制/删除
 */
export function MessageActions({ messageId, favorited, role, content, personaId }: MessageActionsProps) {
  const { toggleFavorite, deleteMessage, regenerateMessage } = useChatStore();
  const [regenerating, setRegenerating] = useState(false);

  const handleRegenerate = async () => {
    if (!personaId) return;
    setRegenerating(true);
    try {
      await regenerateMessage(personaId, messageId);
    } finally {
      setRegenerating(false);
    }
  };

  const handleCopy = () => {
    // 复制消息内容到剪贴板
    const textToCopy = content ?? messageId;
    navigator.clipboard.writeText(textToCopy);
  };

  const handleFavorite = () => {
    toggleFavorite(messageId);
    // 同步持久化到引擎收藏（收藏页/统计可用）
    toggleFavoriteMessage(messageId).catch(() => undefined);
  };

  const handleDelete = () => {
    deleteMessage(messageId);
    deleteMessagePersisted(messageId).catch(() => undefined);
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className="inline-flex h-6 w-6 items-center justify-center rounded-sm text-[var(--color-muted-foreground)] hover:bg-[var(--color-muted)]">
          <MoreHorizontal className="h-3.5 w-3.5" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-36">
        <DropdownMenuItem onClick={handleFavorite}>
          <Star className={`mr-2 h-3.5 w-3.5 ${favorited ? "fill-yellow-400 text-yellow-400" : ""}`} />
          {favorited ? "取消收藏" : "收藏"}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={handleCopy}>
          <Copy className="mr-2 h-3.5 w-3.5" />
          复制
        </DropdownMenuItem>
        {role === "assistant" && personaId && (
          <DropdownMenuItem onClick={handleRegenerate} disabled={regenerating}>
            <RefreshCw className={`mr-2 h-3.5 w-3.5 ${regenerating ? "animate-spin" : ""}`} />
            {regenerating ? "重新生成中…" : "重新生成"}
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={handleDelete} className="text-red-500 focus:text-red-500">
          <Trash2 className="mr-2 h-3.5 w-3.5" />
          删除
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
