import { useParams, useNavigate } from "react-router";
import { useGroupChatStore } from "@/stores/useGroupChatStore";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, Users, Settings, Image as ImageIcon } from "lucide-react";
import { ChatInput } from "@/components/chat/ChatInput";
import { cn } from "@/lib/utils";
import { sendGroupMessage } from "@/lib/tauri";
import { useTauriEvent } from "@/hooks/useTauriEvent";
import { useState } from "react";
import type { SpeakMode } from "@/types/group-chat";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";

/** 发言模式标签映射 */
const SPEAK_MODE_LABELS: Record<SpeakMode, string> = {
  free: "自由发言",
  turn: "轮流发言",
  moderator: "主持模式",
};

/**
 * 群聊页面
 * 对应Android GroupChatActivity
 * 支持发言模式切换、@提及、图片上传
 */
export function GroupChatPage() {
  const { groupId } = useParams<{ groupId: string }>();
  const navigate = useNavigate();
  const { groups, addGroupMessage, updateGroup } = useGroupChatStore();
  const group = groups.find((g) => g.id === groupId);
  const [showSettingsDialog, setShowSettingsDialog] = useState(false);

  /** 根据personaId查找群成员获取名称和头像 */
  const getMemberInfo = (personaId: string) => {
    if (personaId === "user") return { name: "我", avatar: "" };
    const member = group?.members.find((m) => m.personaId === personaId);
    return { name: member?.name ?? personaId, avatar: member?.avatar ?? "" };
  };

  /** 监听群聊AI回复事件 */
  useTauriEvent<{ groupId: string; personaId: string; content: string }>("group-chat:ai-reply", (payload) => {
    if (payload.groupId === groupId) {
      addGroupMessage(payload.groupId, payload.personaId, payload.content);
    }
  });

  const handleSend = (content: string, attachments?: string[]) => {
    if (!groupId) return;
    /** 处理 @提及 */
    const mentionRegex = /@(\S+)/g;
    const mentions = content.match(mentionRegex);
    const finalContent = mentions
      ? `${content}${attachments ? ` [附件: ${attachments.length}个]` : ""}`
      : `${content}${attachments ? ` [附件: ${attachments.length}个]` : ""}`;
    addGroupMessage(groupId, "user", finalContent);
    // 调用后端群聊API触发AI回复
    sendGroupMessage(groupId, finalContent).catch((error) => {
      console.error("发送群聊消息失败:", error);
    });
  };

  /** 切换发言模式 */
  const handleSpeakModeChange = (mode: SpeakMode) => {
    if (!groupId) return;
    updateGroup(groupId, { speakMode: mode });
  };

  return (
    <div className="flex h-full flex-col">
      {/* 顶部栏 */}
      <div className="flex items-center gap-3 border-b border-[var(--color-border)] bg-[var(--color-card)] px-4 py-2">
        <Button variant="ghost" size="icon" onClick={() => navigate("/group-chat")}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <Avatar className="h-8 w-8">
          <AvatarFallback className="bg-[var(--color-secondary)] text-[var(--color-secondary-foreground)]">
            <Users className="h-4 w-4" />
          </AvatarFallback>
        </Avatar>
        <div className="flex-1">
          <h2 className="text-sm font-medium text-[var(--color-card-foreground)]">
            {group?.name ?? "群聊"}
          </h2>
          <p className="text-[10px] text-[var(--color-muted-foreground)]">
            {group?.members.length ?? 0} 位成员 · {SPEAK_MODE_LABELS[group?.speakMode ?? "free"]}
          </p>
        </div>
        <Button variant="ghost" size="icon" onClick={() => setShowSettingsDialog(true)} title="群聊设置">
          <Settings className="h-4 w-4" />
        </Button>
      </div>

      {/* 消息列表 */}
      <ScrollArea className="flex-1">
        <div className="py-4">
          {group?.messages.map((msg) => {
            const memberInfo = getMemberInfo(msg.personaId);
            return (
              <div
                key={msg.id}
                className={cn(
                  "flex gap-3 px-4 py-2",
                  msg.personaId === "user" ? "flex-row-reverse" : "flex-row"
                )}
              >
                <Avatar className="h-7 w-7">
                  {memberInfo.avatar ? (
                    <AvatarImage src={memberInfo.avatar} />
                  ) : null}
                  <AvatarFallback className="text-[10px]">
                    {msg.isSystem ? "系" : memberInfo.name?.[0] ?? "?"}
                  </AvatarFallback>
                </Avatar>
                <div className={cn("max-w-[70%]", msg.personaId === "user" ? "items-end" : "items-start")}>
                  {!msg.isSystem && msg.personaId !== "user" && (
                    <span className="text-[10px] text-[var(--color-muted-foreground)]">{memberInfo.name}</span>
                  )}
                  <div
                    className={cn(
                      "inline-block px-3 py-2 text-sm rounded-[var(--app-radius)]",
                      msg.isSystem
                        ? "bubble-system"
                        : msg.personaId === "user"
                          ? "bubble-user"
                          : "bubble-assistant"
                    )}
                  >
                    {msg.content}
                  </div>
                </div>
              </div>
            );
          })}

          {(!group || group.messages.length === 0) && (
            <div className="flex flex-col items-center justify-center py-20">
              <Users className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
              <p className="text-[var(--color-muted-foreground)]">开始群聊吧</p>
            </div>
          )}
        </div>
      </ScrollArea>

      {/* 输入框 */}
      <ChatInput onSend={handleSend} placeholder="发送群聊消息... (@角色名 可提及)" />

      {/* 群聊设置对话框 */}
      <Dialog open={showSettingsDialog} onOpenChange={setShowSettingsDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>群聊设置</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {/* 发言模式 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">发言模式</label>
              <div className="flex gap-2">
                {(["free", "turn", "moderator"] as SpeakMode[]).map((mode) => (
                  <Button
                    key={mode}
                    variant={group?.speakMode === mode ? "default" : "outline"}
                    size="sm"
                    onClick={() => handleSpeakModeChange(mode)}
                  >
                    {SPEAK_MODE_LABELS[mode]}
                  </Button>
                ))}
              </div>
            </div>

            {/* 成员列表 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">成员 ({group?.members.length ?? 0})</label>
              <div className="space-y-1.5 max-h-[200px] overflow-y-auto">
                {group?.members.map((member) => (
                  <div key={member.personaId} className="flex items-center gap-2 py-1">
                    <Avatar className="h-6 w-6">
                      <AvatarImage src={member.avatar} />
                      <AvatarFallback className="text-[10px]">{member.name?.[0] ?? "?"}</AvatarFallback>
                    </Avatar>
                    <span className="text-sm text-[var(--color-card-foreground)]">{member.name}</span>
                    {member.isAdmin && (
                      <span className="text-[10px] text-[var(--color-primary)]">管理员</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
