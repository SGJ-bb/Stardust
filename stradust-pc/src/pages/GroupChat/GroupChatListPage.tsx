import { useNavigate } from "react-router";
import { useGroupChatStore } from "@/stores/useGroupChatStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Plus, Users } from "lucide-react";
import { CreateGroupDialog } from "./CreateGroupDialog";
import { formatTime } from "@/lib/utils";
import { useState } from "react";

/**
 * 群聊列表页面
 * 对应Android GroupChatListActivity
 */
export function GroupChatListPage() {
  const navigate = useNavigate();
  const { groups } = useGroupChatStore();
  const [showCreateDialog, setShowCreateDialog] = useState(false);

  return (
    <PageContainer>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">群聊</h1>
          <p className="text-sm text-[var(--color-muted-foreground)] mt-1">
            多角色一起聊天
          </p>
        </div>
        <Button onClick={() => setShowCreateDialog(true)}>
          <Plus className="h-4 w-4 mr-2" />
          创建群聊
        </Button>
      </div>

      <ScrollArea className="flex-1">
        {groups.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <Users className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
            <p className="text-[var(--color-muted-foreground)]">还没有群聊，创建一个吧</p>
          </div>
        ) : (
          <div className="space-y-3">
            {groups.map((group) => (
              <Card
                key={group.id}
                className="cursor-pointer hover:shadow-md transition-shadow"
                onClick={() => navigate(`/group-chat/${group.id}`)}
              >
                <CardContent className="flex items-center gap-3 p-4">
                  <Avatar className="h-10 w-10">
                    <AvatarFallback className="bg-[var(--color-secondary)] text-[var(--color-secondary-foreground)]">
                      <Users className="h-5 w-5" />
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex-1 min-w-0">
                    <h3 className="font-medium text-[var(--color-card-foreground)] truncate">
                      {group.name}
                    </h3>
                    <p className="text-xs text-[var(--color-muted-foreground)]">
                      {group.members?.length ?? 0} 位成员 · {group.speakMode === "free" ? "自由发言" : group.speakMode === "turn" ? "轮流发言" : "主持模式"}
                    </p>
                  </div>
                  {group.lastMessageTime > 0 && (
                    <span className="text-[10px] text-[var(--color-muted-foreground)]">
                      {formatTime(group.lastMessageTime)}
                    </span>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </ScrollArea>

      <CreateGroupDialog
        open={showCreateDialog}
        onOpenChange={setShowCreateDialog}
      />
    </PageContainer>
  );
}
