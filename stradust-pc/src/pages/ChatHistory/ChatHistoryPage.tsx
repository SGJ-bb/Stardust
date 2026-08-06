import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, History, Search, Star, Trash2 } from "lucide-react";
import { formatTime, truncate } from "@/lib/utils";
import { useState } from "react";

/**
 * 聊天记录页面
 * 对应Android ChatHistoryActivity
 */
export function ChatHistoryPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const navigate = useNavigate();
  const { currentPersonaId, messages } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const [searchQuery, setSearchQuery] = useState("");

  /** 按当前角色过滤消息 */
  const personaMessages = personaId
    ? messages.filter((m) => m.personaId === personaId)
    : messages;

  const filteredMessages = personaMessages.filter((m) =>
    (m.content ?? "").toLowerCase().includes(searchQuery.toLowerCase())
  );

  const favoriteMessages = filteredMessages.filter((m) => m.favorited);

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">聊天记录</h1>
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
                      <p className="text-sm text-[var(--color-card-foreground)]">{truncate(msg.content, 100)}</p>
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

      {/* 全部消息 */}
      <ScrollArea className="flex-1">
        <h3 className="text-sm font-medium text-[var(--color-muted-foreground)] mb-3">
          全部记录 ({filteredMessages.length})
        </h3>
        {filteredMessages.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <History className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
            <p className="text-[var(--color-muted-foreground)]">暂无聊天记录</p>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredMessages.map((msg) => (
              <Card key={msg.id}>
                <CardContent className="p-3">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <Badge variant="outline" className="text-[10px] mb-1">
                        {msg.role === "user" ? "我" : "AI"}
                      </Badge>
                      <p className="text-sm text-[var(--color-card-foreground)]">{truncate(msg.content, 100)}</p>
                    </div>
                    <span className="text-[10px] text-[var(--color-muted-foreground)] ml-2">
                      {formatTime(msg.timestamp)}
                    </span>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </ScrollArea>
    </PageContainer>
  );
}
