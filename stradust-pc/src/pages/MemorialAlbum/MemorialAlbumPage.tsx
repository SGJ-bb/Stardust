import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { useAlbumStore } from "@/stores/useAlbumStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ArrowLeft, Camera, Plus, Trash2 } from "lucide-react";
import { formatDate } from "@/lib/utils";
import { useState } from "react";

/**
 * 纪念相册页面
 * 对应Android MemorialAlbumActivity
 */
export function MemorialAlbumPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();
  const { entries, addEntry, deleteEntry } = useAlbumStore();
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");

  const handleAdd = () => {
    if (!personaId || !title.trim()) return;
    addEntry(personaId, title.trim(), "", description.trim());
    setTitle("");
    setDescription("");
    setShowAddDialog(false);
  };

  return (
    <PageContainer>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={() => navigate(-1)}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            返回
          </Button>
          <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">纪念相册</h1>
        </div>
        <Button onClick={() => setShowAddDialog(true)}>
          <Plus className="h-4 w-4 mr-2" />
          添加
        </Button>
      </div>

      <ScrollArea className="flex-1">
        {entries.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <Camera className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
            <p className="text-[var(--color-muted-foreground)]">还没有纪念照片</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
            {entries.map((entry) => (
              <Card key={entry.id} className="group cursor-pointer relative">
                <CardContent className="p-2">
                  <div className="aspect-square rounded bg-[var(--color-muted)] mb-2 flex items-center justify-center text-3xl">
                    📷
                  </div>
                  <h4 className="text-sm font-medium text-[var(--color-card-foreground)] truncate">{entry.title}</h4>
                  <p className="text-[10px] text-[var(--color-muted-foreground)]">{formatDate(entry.date)}</p>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="absolute top-1 right-1 opacity-0 group-hover:opacity-100 h-6 w-6"
                    onClick={() => deleteEntry(entry.id)}
                  >
                    <Trash2 className="h-3 w-3" />
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </ScrollArea>

      <Dialog open={showAddDialog} onOpenChange={setShowAddDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>添加纪念</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="标题" />
            <Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="描述" rows={3} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowAddDialog(false)}>取消</Button>
            <Button onClick={handleAdd} disabled={!title.trim()}>添加</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
