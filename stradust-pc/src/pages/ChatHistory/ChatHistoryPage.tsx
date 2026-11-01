import { useParams, useNavigate, Link } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, History, Search, Star, Trash2 } from "lucide-react";
import { formatTime, formatDate } from "@/lib/utils";
import { getChatHistory, deleteMessage } from "@/lib/tauri";
import type { ChatMessage } from "@/types/chat";
import { useEffect, useMemo, useState } from "react";

/** 日期分组键（yyyy-MM-dd） */
function dayKey(ts: number): string {
  const d = new Date(ts);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * 聊天记录页面
 * 对应Android ChatHistoryActivity
 */
export function ChatHistoryPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const navigate = useNavigate();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const [searchQuery, setSearchQuery] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  /** 按角色加载真实历史（引擎数据） */
  useEffect(() => {
    if (!personaId) {
      setMessages([]);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    getChatHistory(personaId, 500)
      .then((list) => {
        if (!cancelled) setMessages(list);
      })
      .catch(() => {
        if (!cancelled) setMessages([]);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [personaId]);

  const filteredMessages = useMemo(
    () => messages.filter((m) => (m.content ?? "").toLowerCase().includes(searchQuery.toLowerCase())),
    [messages, searchQuery],
  );

  const favoriteMessages = filteredMessages.filter((m) => m.favorited);

  /** 按日期倒序分组 */
  const groups = useMemo(() => {
    const map = new Map<string, ChatMessage[]>();
    for (const m of [...filteredMessages].sort((a, b) => b.timestamp - a.timestamp)) {
      const k = dayKey(m.timestamp);
      map.set(k, [...(map.get(k) ?? []), m]);
    }
    return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]));
  }, [filteredMessages]);

  const handleDelete = async (messageId: string) => {
    await deleteMessage(messageId);
    setMessages((prev) => prev.filter((m) => m.id !== messageId));
  };

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">聊天记录</h1>
        <Link
          to="/favorites"
          className="ml-auto flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-[var(--color-primary)] hover:bg-[var(--color-muted)] transition-colors"
        >
          <Star className="h-4 w-4" />
          我的收藏
        </Link>
      </div>

      {/* 搜索 */}
      <div className="relative mb-4">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--color-muted-foreground)]" />
        <Input
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="搜索聊天记录..."
          className="pl-10"
        />
      </div>

      {/* 收藏的消息 */}
      {favoriteMessages.length > 0 && (
        <div className="mb-6">
          <h3 className="text-sm font-medium text-[var(--color-muted-foreground)] mb-3 flex items-center gap-1">
            <Star className="h-3.5 w-3.5" />
            收藏 ({favoriteMessages.length})
            <Link to="/favorites" className="ml-2 text-xs text-[var(--color-primary)] hover:underline">
              查看全部 →
            </Link>
          </h3>
          <div className="space-y-2">
            {favoriteMessages.map((msg) => (
              <Card key={msg.id}>
                <CardContent className="p-3">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <Badge variant="outline" className="text-[10px] mb-1">
                        {msg.role === "user" ? "我" : "AI"}
                      </Badge>
                      <p className="text-sm text-[var(--color-card-foreground)]">{msg.content.slice(0, 100)}</p>
                    </div>
                    <span className="text-[10px] text-[var(--color-muted-foreground)] ml-2">
                      {formatTime(msg.timestamp)}
                    </span>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* 全部消息（按日期分组） */}
      <ScrollArea className="flex-1">
        <h3 className="text-sm font-medium text-[var(--color-muted-foreground)] mb-3">
          全部记录 ({filteredMessages.length})
        </h3>
        {filteredMessages.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <History className={`h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon ${isLoading ? "animate-spin" : ""}`} />
            <p className="text-[var(--color-muted-foreground)]">{isLoading ? "加载中..." : "暂无聊天记录"}</p>
          </div>
        ) : (
          <div className="space-y-5">
            {groups.map(([dateStr, list]) => (
              <div key={dateStr}>
                <h4 className="text-xs font-semibold text-[var(--color-muted-foreground)] mb-2 sticky top-0 bg-[var(--color-background)]/80 backdrop-blur py-1">
                  {formatDate(new Date(`${dateStr}T12:00:00`).getTime())} · {list.length} 条
                </h4>
                <div className="space-y-2">
                  {list.map((msg) => (
                    <Card key={msg.id}>
                      <CardContent className="p-3">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex-1 min-w-0">
                            <Badge variant="outline" className="text-[10px] mb-1">
                              {msg.role === "user" ? "我" : "AI"}
                            </Badge>
                            {msg.favorited && <Star className="inline h-3 w-3 ml-1 fill-yellow-400 text-yellow-400" />}
                            <p className="text-sm text-[var(--color-card-foreground)] break-words">{msg.content.slice(0, 100)}</p>
                          </div>
                          <div className="flex items-center gap-1 shrink-0">
                            <span className="text-[10px] text-[var(--color-muted-foreground)]">
                              {formatTime(msg.timestamp)}
                            </span>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-6 w-6 text-[var(--color-muted-foreground)] hover:text-red-400"
                              title="删除这条记录"
                              onClick={() => void handleDelete(msg.id)}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </div>
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </ScrollArea>
    </PageContainer>
  );
}
