import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { X, Heart, Brain, BookOpen, Trophy } from "lucide-react";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { useNavigate } from "react-router";

interface ChatSidebarProps {
  personaId: string;
  onClose: () => void;
}

/**
 * 聊天右侧信息面板
 */
export function ChatSidebar({ personaId, onClose }: ChatSidebarProps) {
  const { getPersonaById } = usePersonaStore();
  const navigate = useNavigate();
  const persona = getPersonaById(personaId);

  if (!persona) return null;

  return (
    <div className="w-72 border-l border-[var(--color-border)] bg-[var(--color-card)] flex flex-col">
      {/* 头部 */}
      <div className="flex items-center justify-between p-4">
        <h3 className="text-sm font-medium">角色信息</h3>
        <Button variant="ghost" size="icon" onClick={onClose}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      <ScrollArea className="flex-1">
        <div className="p-4 space-y-4">
          {/* 头像和名称 */}
          <div className="flex flex-col items-center">
            <Avatar className="h-20 w-20 mb-3">
              <AvatarImage src={persona.avatar} />
              <AvatarFallback className="text-2xl bg-[var(--color-primary)] text-white">
                {persona.name[0]}
              </AvatarFallback>
            </Avatar>
            <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">{persona.name}</h2>
            <p className="text-xs text-[var(--color-muted-foreground)] mt-1">{persona.favorabilityTitle}</p>
          </div>

          <Separator />

          {/* 好感度 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-sm flex items-center gap-1">
                <Heart className="h-3.5 w-3.5 text-red-400" />
                好感度
              </span>
              <Badge variant="secondary">Lv.{persona.favorabilityLevel}</Badge>
            </div>
            <Progress value={persona.favorability % 100} />
          </div>

          <Separator />

          {/* 描述 */}
          <div>
            <h4 className="text-sm font-medium mb-1">描述</h4>
            <p className="text-xs text-[var(--color-muted-foreground)]">{persona.description}</p>
          </div>

          {/* 性格 */}
          <div>
            <h4 className="text-sm font-medium mb-1">性格</h4>
            <p className="text-xs text-[var(--color-muted-foreground)]">{persona.personality}</p>
          </div>

          {/* 标签 */}
          <div>
            <h4 className="text-sm font-medium mb-2">标签</h4>
            <div className="flex flex-wrap gap-1">
              {(persona.tags ?? []).map((tag) => (
                <Badge key={tag} variant="outline" className="text-xs">
                  {tag}
                </Badge>
              ))}
            </div>
          </div>

          <Separator />

          {/* 快捷操作 */}
          <div className="space-y-2">
            <Button
              variant="outline"
              className="w-full justify-start"
              size="sm"
              onClick={() => navigate(`/memory/${personaId}`)}
            >
              <Brain className="h-4 w-4 mr-2" />
              记忆
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              size="sm"
              onClick={() => navigate(`/diary/${personaId}`)}
            >
              <BookOpen className="h-4 w-4 mr-2" />
              日记
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              size="sm"
              onClick={() => navigate(`/achievement/${personaId}`)}
            >
              <Trophy className="h-4 w-4 mr-2" />
              成就
            </Button>
          </div>
        </div>
      </ScrollArea>
    </div>
  );
}
