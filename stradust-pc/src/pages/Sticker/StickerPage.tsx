import { useNavigate } from "react-router";
import { useStickerStore } from "@/stores/useStickerStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ArrowLeft, Sticker as StickerIcon, Star, Download } from "lucide-react";

/**
 * 表情包页面
 * 对应Android StickerActivity
 */
export function StickerPage() {
  const navigate = useNavigate();
  const { packs, recentStickers, toggleFavorite } = useStickerStore();

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">表情包</h1>
      </div>

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
