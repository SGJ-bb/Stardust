import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { useCapsuleStore } from "@/stores/useCapsuleStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ArrowLeft, Timer, Plus, Lock, Unlock, Trash2 } from "lucide-react";
import { formatDate } from "@/lib/utils";
import { useState } from "react";

/**
 * 时光胶囊页面
 * 对应Android TimeCapsuleActivity
 */
export function TimeCapsulePage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();
  const { capsules, createCapsule, openCapsule, deleteCapsule } = useCapsuleStore();
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [openDate, setOpenDate] = useState("");

  const handleCreate = () => {
    if (!personaId || !title.trim() || !openDate) return;
    createCapsule(personaId, title.trim(), content.trim(), new Date(openDate).getTime());
    setTitle("");
    setContent("");
    setOpenDate("");
    setShowCreateDialog(false);
  };

  return (
    <PageContainer>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={() => navigate(-1)}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            返回
          </Button>
          <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">时光胶囊</h1>
        </div>
        <Button onClick={() => setShowCreateDialog(true)}>
          <Plus className="h-4 w-4 mr-2" />
          创建胶囊
        </Button>
      </div>

      <ScrollArea className="flex-1">
        {capsules.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <Timer className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
            <p className="text-[var(--color-muted-foreground)]">还没有时光胶囊</p>
          </div>
        ) : (
          <div className="space-y-3">
            {capsules.map((capsule) => {
              const canOpen = Date.now() >= capsule.openAt;
              return (
                <Card key={capsule.id} className={!capsule.opened && !canOpen ? "opacity-70" : ""}>
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between">
                      <div className="flex items-start gap-3">
                        {capsule.opened ? (
                          <Unlock className="h-5 w-5 text-green-500 mt-0.5" />
                        ) : (
                          <Lock className="h-5 w-5 text-[var(--color-muted-foreground)] mt-0.5" />
                        )}
                        <div>
                          <h3 className="font-medium text-[var(--color-card-foreground)]">{capsule.title}</h3>
                          {capsule.opened ? (
                            <p className="text-sm text-[var(--color-muted-foreground)] mt-1">{capsule.content}</p>
                          ) : (
                            <p className="text-sm text-[var(--color-muted-foreground)] mt-1">封存中...</p>
                          )}
                          <div className="flex items-center gap-2 mt-2">
                            <Badge variant={capsule.opened ? "default" : "secondary"} className="text-[10px]">
                              {capsule.opened ? "已开启" : "封存中"}
                            </Badge>
                            <span className="text-[10px] text-[var(--color-muted-foreground)]">
                              开启时间: {formatDate(capsule.openAt)}
                            </span>
                          </div>
                        </div>
                      </div>
                      <div className="flex gap-1">
                        {!capsule.opened && canOpen && (
                          <Button size="sm" onClick={() => openCapsule(capsule.id)}>
                            开启
                          </Button>
                        )}
                        <Button variant="ghost" size="icon" onClick={() => deleteCapsule(capsule.id)}>
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </ScrollArea>

      <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>创建时光胶囊</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="胶囊标题" />
            <Textarea value={content} onChange={(e) => setContent(e.target.value)} placeholder="写给未来的话..." rows={4} />
            <div className="space-y-2">
              <label className="text-sm font-medium">开启日期</label>
              <Input type="date" value={openDate} onChange={(e) => setOpenDate(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreateDialog(false)}>取消</Button>
            <Button onClick={handleCreate} disabled={!title.trim() || !openDate}>创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
