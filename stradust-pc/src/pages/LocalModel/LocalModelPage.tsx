import { useNavigate } from "react-router";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, Cpu, Download, Trash2, HardDrive } from "lucide-react";
import { formatFileSize } from "@/lib/utils";
import { useState, useEffect } from "react";
import { getLocalModels, downloadLocalModel, deleteLocalModel as deleteLocalModelApi } from "@/lib/tauri";

/** 本地模型数据类型 */
interface LocalModelItem {
  id: string;
  name: string;
  path: string;
  size: number;
}

/**
 * 本地模型页面
 * 对应Android LocalModelActivity
 */
export function LocalModelPage() {
  const navigate = useNavigate();
  const [models, setModels] = useState<LocalModelItem[]>([]);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  /** 从后端加载本地模型列表 */
  useEffect(() => {
    getLocalModels()
      .then((list) => setModels(list ?? []))
      .catch((error) => console.error("加载本地模型列表失败:", error))
      .finally(() => setIsLoading(false));
  }, []);

  const handleDownload = async (modelId: string) => {
    setDownloading(modelId);
    try {
      await downloadLocalModel(modelId);
      // 重新加载模型列表
      const list = await getLocalModels();
      setModels(list ?? []);
    } catch (error) {
      console.error("下载模型失败:", error);
    } finally {
      setDownloading(null);
    }
  };

  const handleDelete = async (modelId: string) => {
    try {
      await deleteLocalModelApi(modelId);
      setModels((prev) => prev.filter((m) => m.id !== modelId));
    } catch (error) {
      console.error("删除模型失败:", error);
    }
  };

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">本地模型</h1>
      </div>

      <ScrollArea className="flex-1">
        {models.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <Cpu className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
            <p className="text-[var(--color-muted-foreground)]">暂无本地模型</p>
          </div>
        ) : (
          <div className="space-y-3">
            {models.map((model) => (
              <Card key={model.id}>
                <CardContent className="p-4">
                  <div className="flex items-start justify-between">
                    <div className="flex items-start gap-3">
                      <Cpu className="h-5 w-5 text-[var(--color-primary)] mt-0.5" />
                      <div>
                        <h3 className="font-medium text-sm text-[var(--color-card-foreground)]">{model.name}</h3>
                        <div className="flex items-center gap-2 mt-1">
                          <Badge variant="outline" className="text-[10px]">
                            <HardDrive className="h-3 w-3 mr-1" />
                            {formatFileSize(model.size)}
                          </Badge>
                        </div>
                        {downloading === model.id && (
                          <Progress value={45} className="mt-2 h-1.5 w-40" />
                        )}
                      </div>
                    </div>
                    <div className="flex gap-1">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleDownload(model.id)}
                        disabled={downloading === model.id}
                      >
                        <Download className="h-3.5 w-3.5 mr-1" />
                        {downloading === model.id ? "下载中" : "下载"}
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => handleDelete(model.id)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
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
