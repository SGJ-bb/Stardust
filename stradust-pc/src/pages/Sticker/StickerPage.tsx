import { useNavigate } from "react-router";
import { useStickerStore } from "@/stores/useStickerStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ArrowLeft, Sticker as StickerIcon, Star, Trash2, Upload } from "lucide-react";
import { useEffect, useRef, useState } from "react";

/** 文件选择转 dataURL */
function fileToDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

/**
 * 表情包页面
 * 对应Android StickerActivity
 */
export function StickerPage() {
  const navigate = useNavigate();
  const { packs, recentStickers, toggleFavorite, load, addStickerItem, removeSticker } = useStickerStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  /** 进入页面时从引擎加载表情（内置 + 自定义） */
  useEffect(() => {
    void load();
  }, [load]);

  const allStickers = packs.flatMap((p) => p.stickers ?? []);

  const handleFilePicked = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setUploadError("请选择图片文件");
      return;
    }
    try {
      const dataUrl = await fileToDataURL(file);
      const name = file.name.replace(/\.[^.]+$/, "");
      await addStickerItem({ name, tags: [name], dataUrl });
      setUploadError(null);
    } catch {
      setUploadError("读取图片失败，请重试");
    }
  };

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">表情包</h1>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => void handleFilePicked(e)}
        />
        <Button
          variant="outline"
          onClick={() => fileInputRef.current?.click()}
          className="ml-auto gap-2"
          title="选择图片添加为表情"
        >
          <Upload className="h-4 w-4" />
          添加表情
        </Button>
      </div>

      {uploadError && (
        <p className="mb-3 text-sm text-red-400">{uploadError}</p>
      )}

      <Tabs defaultValue="packs">
        <TabsList className="mb-4">
          <TabsTrigger value="packs">表情包集</TabsTrigger>
          <TabsTrigger value="recent">最近使用</TabsTrigger>
          <TabsTrigger value="favorites">收藏</TabsTrigger>
        </TabsList>

        <TabsContent value="packs">
          <ScrollArea className="h-[calc(100vh-250px)]">
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              {packs.map((pack) => (
                <Card key={pack.id} className="cursor-pointer hover:shadow-md transition-shadow">
                  <CardContent className="p-4 text-center">
                    <div className="text-4xl mb-2">📦</div>
                    <h3 className="text-sm font-medium text-[var(--color-card-foreground)]">{pack.name}</h3>
                    <p className="text-xs text-[var(--color-muted-foreground)]">{pack.stickers?.length ?? 0} 个表情</p>
                  </CardContent>
                </Card>
              ))}
            </div>

            {/* 全部表情网格（内置 + 自定义，可收藏/删除） */}
            <div className="mt-6">
              <h3 className="text-sm font-medium text-[var(--color-muted-foreground)] mb-3">全部表情 ({allStickers.length})</h3>
              <div className="grid grid-cols-6 gap-2">
                {allStickers.map((sticker) => (
                  <div key={sticker.id} className="group relative aspect-square rounded bg-[var(--color-muted)] hover:bg-[var(--color-accent)] transition-colors flex items-center justify-center overflow-hidden">
                    <img src={sticker.thumbnail || sticker.path} alt={sticker.name} title={sticker.name} className="w-full h-full object-contain" />
                    <button
                      onClick={() => toggleFavorite(sticker.id)}
                      className={`absolute top-0.5 left-0.5 p-0.5 rounded ${sticker.favorited ? "opacity-100" : "opacity-0 group-hover:opacity-100"}`}
                      title="收藏"
                    >
                      <Star className={`h-3.5 w-3.5 ${sticker.favorited ? "fill-yellow-400 text-yellow-400" : "text-[var(--color-muted-foreground)]"}`} />
                    </button>
                    {!sticker.id.startsWith("builtin_") && (
                      <button
                        onClick={() => void removeSticker(sticker.id)}
                        className="absolute top-0.5 right-0.5 p-0.5 rounded opacity-0 group-hover:opacity-100 hover:text-red-400"
                        title="删除"
                      >
                        <Trash2 className="h-3.5 w-3.5 text-[var(--color-muted-foreground)]" />
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </ScrollArea>
        </TabsContent>

        <TabsContent value="recent">
          <ScrollArea className="h-[calc(100vh-250px)]">
            {recentStickers.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-20">
                <StickerIcon className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
                <p className="text-[var(--color-muted-foreground)]">暂无最近使用的表情</p>
              </div>
            ) : (
              <div className="grid grid-cols-6 gap-2">
                {recentStickers.map((sticker) => (
                  <button
                    key={sticker.id}
                    className="aspect-square rounded bg-[var(--color-muted)] hover:bg-[var(--color-accent)] transition-colors flex items-center justify-center overflow-hidden"
                  >
                    <img src={sticker.thumbnail || sticker.path} alt={sticker.name} className="w-full h-full object-contain" />
                  </button>
                ))}
              </div>
            )}
          </ScrollArea>
        </TabsContent>

        <TabsContent value="favorites">
          <ScrollArea className="h-[calc(100vh-250px)]">
            <div className="grid grid-cols-6 gap-2">
              {packs.flatMap((p) => p.stickers ?? []).filter((s) => s.favorited).map((sticker) => (
                <button
                  key={sticker.id}
                  onClick={() => toggleFavorite(sticker.id)}
                  className="aspect-square rounded bg-[var(--color-muted)] hover:bg-[var(--color-accent)] transition-colors flex items-center justify-center overflow-hidden"
                >
                  <img src={sticker.thumbnail || sticker.path} alt={sticker.name} className="w-full h-full object-contain" />
                </button>
              ))}
            </div>
          </ScrollArea>
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
