import { useParams, useNavigate } from "react-router";
import { useVirtualWorldStore } from "@/stores/useVirtualWorldStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ArrowLeft, Globe, BookOpen, Clock, Users, Play, ImagePlus } from "lucide-react";
import { WorldLoreEditor } from "./WorldLoreEditor";
import { formatDate } from "@/lib/utils";
import { isTauri } from "@/lib/tauri";

/**
 * 虚拟世界页面
 * 对应Android VirtualWorldActivity
 */
export function VirtualWorldPage() {
  const { worldId } = useParams<{ worldId: string }>();
  const navigate = useNavigate();
  const { currentWorldConfig, currentWorldState } = useVirtualWorldStore();

  /** 手动触发世界推演 */
  const handleTriggerEvolution = async () => {
    if (!worldId || !isTauri()) return;
    try {
      const { invoke } = await import("@tauri-apps/api/core");
      await invoke("trigger_world_evolution", { worldId });
    } catch (error) {
      console.error("触发推演失败:", error);
    }
  };

  /** 上传图片到虚拟世界 */
  const handleUploadImage = async () => {
    if (!isTauri()) { console.warn("浏览器环境不支持上传图片"); return; }
    try {
      const { open } = await import("@tauri-apps/plugin-dialog");
      const { invoke } = await import("@tauri-apps/api/core");
      const selected = await open({
        multiple: false,
        filters: [{ name: "图片", extensions: ["png", "jpg", "jpeg", "gif", "webp"] }],
      });
      if (selected) {
        const path = typeof selected === "string" ? selected : selected;
        await invoke("upload_world_image", { worldId, imagePath: path });
      }
    } catch (error) {
      console.error("上传图片失败:", error);
    }
  };

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">
          {currentWorldConfig?.name ?? "虚拟世界"}
        </h1>
        <div className="flex gap-2 ml-auto">
          <Button variant="outline" size="sm" onClick={handleTriggerEvolution}>
            <Play className="h-4 w-4 mr-1" />
            推演
          </Button>
          <Button variant="outline" size="sm" onClick={handleUploadImage}>
            <ImagePlus className="h-4 w-4 mr-1" />
            上传图片
          </Button>
        </div>
      </div>

      <Tabs defaultValue="overview">
        <TabsList className="mb-4">
          <TabsTrigger value="overview">
            <Globe className="h-3.5 w-3.5 mr-1" />
            概览
          </TabsTrigger>
          <TabsTrigger value="lore">
            <BookOpen className="h-3.5 w-3.5 mr-1" />
            世界观
          </TabsTrigger>
          <TabsTrigger value="timeline">
            <Clock className="h-3.5 w-3.5 mr-1" />
            时间线
          </TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <ScrollArea className="h-[calc(100vh-250px)]">
            {currentWorldConfig ? (
              <div className="space-y-4">
                <Card>
                  <CardContent className="p-4 space-y-3">
                    <div>
                      <h3 className="text-sm font-medium">描述</h3>
                      <p className="text-sm text-[var(--color-muted-foreground)] mt-1">{currentWorldConfig.description}</p>
                    </div>
                    <div>
                      <h3 className="text-sm font-medium">时代背景</h3>
                      <p className="text-sm text-[var(--color-muted-foreground)] mt-1">{currentWorldConfig.era}</p>
                    </div>
                    <div>
                      <h3 className="text-sm font-medium">地理环境</h3>
                      <p className="text-sm text-[var(--color-muted-foreground)] mt-1">{currentWorldConfig.geography}</p>
                    </div>
                    <div>
                      <h3 className="text-sm font-medium">社会结构</h3>
                      <p className="text-sm text-[var(--color-muted-foreground)] mt-1">{currentWorldConfig.society}</p>
                    </div>
                    <div>
                      <h3 className="text-sm font-medium">体系</h3>
                      <p className="text-sm text-[var(--color-muted-foreground)] mt-1">{currentWorldConfig.system}</p>
                    </div>
                  </CardContent>
                </Card>

                {/* 参与角色 */}
                <Card>
                  <CardContent className="p-4">
                    <h3 className="text-sm font-medium mb-2 flex items-center gap-1">
                      <Users className="h-3.5 w-3.5" />
                      参与角色
                    </h3>
                    <p className="text-sm text-[var(--color-muted-foreground)]">
                      {currentWorldConfig.personaIds.length} 位角色
                    </p>
                  </CardContent>
                </Card>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-20">
                <Globe className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4" />
                <p className="text-[var(--color-muted-foreground)]">世界不存在</p>
              </div>
            )}
          </ScrollArea>
        </TabsContent>

        <TabsContent value="lore">
          {currentWorldConfig && (
            <WorldLoreEditor worldId={currentWorldConfig.id} initialLore={currentWorldConfig.lore} />
          )}
        </TabsContent>

        <TabsContent value="timeline">
          <ScrollArea className="h-[calc(100vh-250px)]">
            {currentWorldState ? (
              <div className="space-y-3">
                <Card>
                  <CardContent className="p-4">
                    <p className="text-sm text-[var(--color-muted-foreground)]">
                      当前时间线: {currentWorldState.timeline}
                    </p>
                    <p className="text-sm text-[var(--color-muted-foreground)]">
                      当前地点: {currentWorldState.location}
                    </p>
                    <p className="text-sm text-[var(--color-muted-foreground)]">
                      天气: {currentWorldState.weather}
                    </p>
                  </CardContent>
                </Card>

                {currentWorldState.eventHistory.map((event) => (
                  <Card key={event.id}>
                    <CardContent className="p-4">
                      <h4 className="font-medium text-sm text-[var(--color-card-foreground)]">{event.title}</h4>
                      <p className="text-xs text-[var(--color-muted-foreground)] mt-1">{event.description}</p>
                      <span className="text-[10px] text-[var(--color-muted-foreground)]">{formatDate(event.timestamp)}</span>
                    </CardContent>
                  </Card>
                ))}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-20">
                <Clock className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
                <p className="text-[var(--color-muted-foreground)]">暂无时间线数据</p>
              </div>
            )}
          </ScrollArea>
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
