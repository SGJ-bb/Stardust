/**
 * 收藏消息管理页 —— 对应 Android FavoriteManager/收藏入口
 */

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Star, Trash2, Copy, MessageCircle, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PageContainer } from "@/components/layout/PageContainer";
import { listFavoriteMessages, deleteMessage } from "@/lib/tauri";
import { usePersonaStore } from "@/stores/usePersonaStore";
import type { ChatMessage } from "@/types/chat";

export function FavoritesPage() {
  const personas = usePersonaStore((s) => s.personas);
  const [filter, setFilter] = useState<string>("all");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);

  const reload = () => {
    setLoading(true);
    listFavoriteMessages(filter === "all" ? undefined : filter)
      .then((list) => setMessages(list.sort((a, b) => b.timestamp - a.timestamp)))
      .finally(() => setLoading(false));
  };

  useEffect(reload, [filter]);

  const handleDelete = async (id: string) => {
    await deleteMessage(id);
    reload();
  };

  const handleCopy = (content: string) => {
    navigator.clipboard?.writeText(content).catch(() => undefined);
  };

  const personaName = (pid: string) => personas.find((p) => p.id === pid)?.name ?? pid;

  return (
    <PageContainer title="我的收藏" description="聊天中收藏的精选消息">
      <div className="flex flex-wrap gap-2 mb-4">
        <Button size="sm" variant={filter === "all" ? "default" : "outline"} onClick={() => setFilter("all")}>
          全部角色
        </Button>
        {personas.map((p) => (
          <Button key={p.id} size="sm" variant={filter === p.id ? "default" : "outline"} onClick={() => setFilter(p.id)}>
            {p.name}
          </Button>
        ))}
      </div>

      {loading ? (
        <p className="text-sm text-[var(--color-muted-foreground)] py-10 text-center">加载中…</p>
      ) : messages.length === 0 ? (
        <div className="text-center py-16 text-[var(--color-muted-foreground)]">
          <Star className="h-10 w-10 mx-auto mb-3 opacity-40" />
          <p className="text-sm">还没有收藏的消息</p>
          <p className="text-xs mt-1">在聊天中长按/右键 AI 消息选择"收藏"</p>
        </div>
      ) : (
        <div className="space-y-3">
          {messages.map((m) => (
            <motion.div
              key={m.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              className="surface-card p-4"
            >
              <div className="flex items-center gap-2 mb-2 text-xs text-[var(--color-muted-foreground)]">
                {m.role === "user" ? <User className="h-3.5 w-3.5" /> : <MessageCircle className="h-3.5 w-3.5" />}
                <span>{m.role === "user" ? "我" : personaName(m.personaId)}</span>
                <span>·</span>
                <span>{new Date(m.timestamp).toLocaleString("zh-CN")}</span>
                <div className="ml-auto flex gap-1">
                  <Button size="sm" variant="ghost" onClick={() => handleCopy(m.content)}>
                    <Copy className="h-3.5 w-3.5" />
                  </Button>
                  <Button size="sm" variant="ghost" className="text-red-400" onClick={() => handleDelete(m.id)}>
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
              <p className="text-sm whitespace-pre-wrap">{m.content}</p>
            </motion.div>
          ))}
        </div>
      )}
    </PageContainer>
  );
}
