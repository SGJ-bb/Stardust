import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { useGroupChatStore } from "@/stores/useGroupChatStore";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { ScrollArea } from "@/components/ui/scroll-area";
import { generateId } from "@/lib/utils";
import type { SpeakMode } from "@/types/group-chat";

interface CreateGroupDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * 创建群聊对话框
 */
export function CreateGroupDialog({ open, onOpenChange }: CreateGroupDialogProps) {
  const { personas } = usePersonaStore();
  const { addGroup } = useGroupChatStore();
  const [name, setName] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [speakMode, setSpeakMode] = useState<SpeakMode>("free");

  const toggleMember = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id]
    );
  };

  const handleCreate = () => {
    if (!name.trim() || selectedIds.length < 2) return;

    addGroup({
      id: generateId(),
      name: name.trim(),
      avatar: "",
      description: "",
      members: selectedIds.map((id) => ({
        personaId: id,
        name: personas.find((p) => p.id === id)?.name ?? "",
        avatar: personas.find((p) => p.id === id)?.avatar ?? "",
        isAdmin: false,
        speakWeight: 1,
      })),
      messages: [],
      speakMode,
      topic: "",
      createdAt: Date.now(),
      updatedAt: Date.now(),
      lastMessageTime: 0,
    });

    setName("");
    setSelectedIds([]);
    setSpeakMode("free");
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>创建群聊</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium">群聊名称 *</label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="给群聊取个名字"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">发言模式</label>
            <div className="flex gap-2">
              {([
                { value: "free", label: "自由发言" },
                { value: "turn", label: "轮流发言" },
                { value: "moderator", label: "主持模式" },
              ] as const).map((mode) => (
                <Button
                  key={mode.value}
                  variant={speakMode === mode.value ? "default" : "outline"}
                  size="sm"
                  onClick={() => setSpeakMode(mode.value)}
                >
                  {mode.label}
                </Button>
              ))}
            </div>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">选择角色 (至少2个)</label>
            <ScrollArea className="h-[200px]">
              <div className="space-y-2">
                {personas.map((persona) => (
                  <button
                    key={persona.id}
                    onClick={() => toggleMember(persona.id)}
                    className="flex w-full items-center gap-3 rounded-[var(--app-radius)] p-2 hover:bg-[var(--color-muted)] transition-colors"
                  >
                    <Avatar className="h-8 w-8">
                      <AvatarImage src={persona.avatar} />
                      <AvatarFallback className="text-xs bg-[var(--color-primary)] text-white">
                        {persona.name[0]}
                      </AvatarFallback>
                    </Avatar>
                    <span className="text-sm flex-1 text-left">{persona.name}</span>
                    <div
                      className={`h-4 w-4 rounded border ${
                        selectedIds.includes(persona.id)
                          ? "bg-[var(--color-primary)] border-[var(--color-primary)]"
                          : "border-[var(--color-border)]"
                      }`}
                    />
                  </button>
                ))}
              </div>
            </ScrollArea>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleCreate} disabled={!name.trim() || selectedIds.length < 2}>
            创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
