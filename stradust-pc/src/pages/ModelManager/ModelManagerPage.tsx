import { useNavigate } from "react-router";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, Box, Plus, Trash2, Settings, Move } from "lucide-react";
import { useState, useEffect } from "react";
import { getModelList, importModel, deleteModel as deleteModelApi } from "@/lib/tauri";
import { isTauri } from "@/lib/tauri";

/** 模型数据类型 */
interface ModelItem {
  id: string;
  name: string;
  path: string;
  thumbnail: string;
}

/**
 * 模型管理页面
 * 对应Android ModelManagerActivity+ModelSettingsActivity+ModelAdjustActivity
 */
export function ModelManagerPage() {
  const navigate = useNavigate();
  const [models, setModels] = useState<ModelItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  /** 从后端加载模型列表 */
  useEffect(() => {
    getModelList()
      .then((list) => setModels(list ?? []))
      .catch((error) => console.error("加载模型列表失败:", error))
      .finally(() => setIsLoading(false));
  }, []);

  /** 导入模型：打开文件选择对话框 */
  const handleImport = async () => {
    if (!isTauri()) { console.warn("浏览器环境不支持导入模型"); return; }
    try {
      const { open } = await import("@tauri-apps/plugin-dialog");
      const selected = await open({
        multiple: false,
        filters: [{ name: "Live2D模型", extensions: ["zip", "model3.json"] }],
      });
      if (selected) {
        const path = typeof selected === "string" ? selected : selected;
        await importModel(path);
        // 重新加载模型列表
        const list = await getModelList();
        setModels(list ?? []);
      }
    } catch (error) {
      console.error("导入模型失败:", error);
    }
  };

  const handleDelete = async (modelId: string) => {
    try {
      await deleteModelApi(modelId);
      setModels((prev) => prev.filter((m) => m.id !== modelId));
    } catch (error) {
      console.error("删除模型失败:", error);
    }
  };

  return (
    <PageContainer>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={() => navigate(-1)}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            返回
          </Button>
          <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">模型管理</h1>
        </div>
        <Button onClick={handleImport}>
          <Plus className="h-4 w-4 mr-2" />
          导入模型
        </Button>
      </div>

      <ScrollArea className="flex-1">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {models.map((model) => (
            <Card key={model.id}>
              <CardContent className="p-4">
                <div className="text-center mb-3">
                  <div className="text-5xl">{model.thumbnail}</div>
                </div>
                <h3 className="font-medium text-center text-[var(--color-card-foreground)]">{model.name}</h3>
                <div className="flex justify-center gap-2 mt-3">
                  <Button variant="outline" size="sm">
                    <Settings className="h-3.5 w-3.5 mr-1" />
                    设置
                  </Button>
                  <Button variant="outline" size="sm">
                    <Move className="h-3.5 w-3.5 mr-1" />
                    调整
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => handleDelete(model.id)}>
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </ScrollArea>
    </PageContainer>
  );
}
