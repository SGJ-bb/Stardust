import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreHorizontal, Star, Copy, Trash2 } from "lucide-react";
import { useChatStore } from "@/stores/useChatStore";
import type { MessageRole } from "@/types/chat";

interface MessageActionsProps {
  messageId: string;
  favorited: boolean;
  role: MessageRole;
  /** 消息文本内容，用于复制 */
  content?: string;
}

/**
 * 消息操作菜单组件
 * 支持收藏/复制/删除
 */
export function MessageActions({ messageId, favorited, role, content }: MessageActionsProps) {
  const { toggleFavorite, deleteMessage } = useChatStore();

  const handleCopy = () => {
    // 复制消息内容到剪贴板
    const textToCopy = content ?? messageId;
    navigator.clipboard.writeText(textToCopy);
  };

  const handleFavorite = () => {
    toggleFavorite(messageId);
  };

  const handleDelete = () => {
    deleteMessage(messageId);
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
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={handleDelete} className="text-red-500 focus:text-red-500">
          <Trash2 className="mr-2 h-3.5 w-3.5" />
          删除
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
