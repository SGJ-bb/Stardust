import { useParams, useNavigate } from "react-router";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Separator } from "@/components/ui/separator";
import { ArrowLeft, Edit, MessageCircle, Heart, Brain, BookOpen, Trophy, Camera, Timer, Download, Upload } from "lucide-react";
import { isTauri } from "@/lib/tauri";

/**
 * 角色详情页面
 * 对应Android ProfileActivity
 */
export function PersonaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { getPersonaById, addPersona } = usePersonaStore();
  const persona = id ? getPersonaById(id) : null;

  if (!persona) {
    return (
      <PageContainer>
        <div className="flex items-center justify-center h-full">
          <p className="text-[var(--color-muted-foreground)]">角色不存在</p>
        </div>
      </PageContainer>
    );
  }

  /** 导出角色数据 */
  const handleExport = async () => {
    if (!isTauri()) { console.warn("浏览器环境不支持导出"); return; }
    try {
      const { save } = await import("@tauri-apps/plugin-dialog");
      const { writeTextFile } = await import("@tauri-apps/plugin-fs");
      const filePath = await save({
        defaultPath: `${persona.name}_export.json`,
        filters: [{ name: "JSON", extensions: ["json"] }],
      });
      if (filePath) {
        const data = JSON.stringify(persona, null, 2);
        await writeTextFile(filePath, data);
      }
    } catch (error) {
      console.error("导出角色失败:", error);
    }
  };

  /** 导入角色数据 */
  const handleImport = async () => {
    if (!isTauri()) { console.warn("浏览器环境不支持导入"); return; }
    try {
      const { open } = await import("@tauri-apps/plugin-dialog");
      const { readTextFile } = await import("@tauri-apps/plugin-fs");
      const filePath = await open({
        multiple: false,
        filters: [{ name: "JSON", extensions: ["json"] }],
      });
      if (filePath) {
        const path = typeof filePath === "string" ? filePath : filePath;
        const data = await readTextFile(path);
        const imported = JSON.parse(data);
        addPersona(imported);
        navigate(`/persona/${imported.id}`);
      }
    } catch (error) {
      console.error("导入角色失败:", error);
    }
  };

  return (
    <PageContainer>
      {/* 返回按钮 */}
      <Button variant="ghost" onClick={() => navigate(-1)} className="mb-4">
        <ArrowLeft className="h-4 w-4 mr-2" />
        返回
      </Button>

      {/* 角色信息头部 */}
      <div className="flex items-start gap-6 mb-6">
        <Avatar className="h-24 w-24">
          <AvatarImage src={persona.avatar} />
          <AvatarFallback className="text-3xl bg-[var(--color-primary)] text-white">
            {persona.name[0]}
          </AvatarFallback>
        </Avatar>
        <div className="flex-1">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">{persona.name}</h1>
            <Button variant="outline" size="sm" onClick={() => navigate(`/persona/${persona.id}/edit`)}>
              <Edit className="h-3.5 w-3.5 mr-1" />
              编辑
            </Button>
          </div>
          <p className="text-sm text-[var(--color-muted-foreground)] mt-1">{persona.favorabilityTitle}</p>
          <div className="flex flex-wrap gap-1 mt-2">
            {(persona.tags ?? []).map((tag) => (
              <Badge key={tag} variant="secondary" className="text-xs">{tag}</Badge>
            ))}
          </div>
        </div>
      </div>

      {/* 好感度 */}
      <Card className="mb-4">
        <CardContent className="p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm font-medium flex items-center gap-1">
              <Heart className="h-4 w-4 text-red-400" />
              好感度
            </span>
            <Badge>Lv.{persona.favorabilityLevel}</Badge>
          </div>
          <Progress value={persona.favorability % 100} />
        </CardContent>
      </Card>

      {/* 描述 */}
      <Card className="mb-4">
        <CardContent className="p-4 space-y-3">
          <div>
            <h3 className="text-sm font-medium mb-1">描述</h3>
            <p className="text-sm text-[var(--color-muted-foreground)]">{persona.description}</p>
          </div>
          <Separator />
          <div>
            <h3 className="text-sm font-medium mb-1">性格</h3>
            <p className="text-sm text-[var(--color-muted-foreground)]">{persona.personality}</p>
          </div>
          <Separator />
          <div>
            <h3 className="text-sm font-medium mb-1">场景</h3>
            <p className="text-sm text-[var(--color-muted-foreground)]">{persona.scenario}</p>
          </div>
        </CardContent>
      </Card>

      {/* 快捷操作 */}
      <div className="grid grid-cols-2 gap-3">
        <Button variant="outline" className="justify-start" onClick={() => navigate(`/chat/${persona.id}`)}>
          <MessageCircle className="h-4 w-4 mr-2" />
          开始聊天
        </Button>
        <Button variant="outline" className="justify-start" onClick={() => navigate(`/memory/${persona.id}`)}>
          <Brain className="h-4 w-4 mr-2" />
          记忆
        </Button>
        <Button variant="outline" className="justify-start" onClick={() => navigate(`/diary/${persona.id}`)}>
          <BookOpen className="h-4 w-4 mr-2" />
          日记
        </Button>
        <Button variant="outline" className="justify-start" onClick={() => navigate(`/achievement/${persona.id}`)}>
          <Trophy className="h-4 w-4 mr-2" />
          成就
        </Button>
        <Button variant="outline" className="justify-start" onClick={() => navigate(`/album/${persona.id}`)}>
          <Camera className="h-4 w-4 mr-2" />
          纪念相册
        </Button>
        <Button variant="outline" className="justify-start" onClick={() => navigate(`/capsule/${persona.id}`)}>
          <Timer className="h-4 w-4 mr-2" />
          时光胶囊
        </Button>
        <Button variant="outline" className="justify-start" onClick={handleExport}>
          <Download className="h-4 w-4 mr-2" />
          导出角色
        </Button>
        <Button variant="outline" className="justify-start" onClick={handleImport}>
          <Upload className="h-4 w-4 mr-2" />
          导入角色
        </Button>
      </div>
    </PageContainer>
  );
}
