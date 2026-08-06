import { useNavigate } from "react-router";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, ShoppingBag, Lock, Check } from "lucide-react";
import { useState, useEffect } from "react";
import { getSkinList, purchaseSkin } from "@/lib/tauri";

/** 皮肤数据类型 */
interface SkinItem {
  id: string;
  name: string;
  preview: string;
  price: number;
  owned: boolean;
  category: string;
}

/**
 * 皮肤商店页面
 * 对应Android SkinShopActivity
 */
export function SkinShopPage() {
  const navigate = useNavigate();
  const [skins, setSkins] = useState<SkinItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  /** 从后端加载皮肤列表 */
  useEffect(() => {
    getSkinList()
      .then((list) => {
        setSkins((list ?? []).map((s) => ({
          id: s.id,
          name: s.name,
          preview: s.preview,
          price: s.price,
          owned: s.owned,
          category: "皮肤",
        })));
      })
      .catch((error) => {
        console.error("加载皮肤列表失败:", error);
      })
      .finally(() => setIsLoading(false));
  }, []);

  const handlePurchase = async (skinId: string) => {
    try {
      const result = await purchaseSkin(skinId);
      if (result.success) {
        setSkins((prev) =>
          prev.map((s) => (s.id === skinId ? { ...s, owned: true } : s))
        );
      }
    } catch (error) {
      console.error("购买皮肤失败:", error);
    }
  };

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">皮肤商店</h1>
      </div>

      <ScrollArea className="flex-1">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {skins.map((skin) => (
            <Card key={skin.id}>
              <CardContent className="p-4 text-center">
                <div className="text-5xl mb-3">{skin.preview}</div>
                <h3 className="font-medium text-[var(--color-card-foreground)]">{skin.name}</h3>
                <Badge variant="outline" className="text-[10px] mt-1">{skin.category}</Badge>
                <div className="mt-3">
                  {skin.owned ? (
                    <Button variant="outline" size="sm" disabled>
                      <Check className="h-3.5 w-3.5 mr-1" />
                      已拥有
                    </Button>
                  ) : (
                    <Button size="sm" onClick={() => handlePurchase(skin.id)}>
                      <Lock className="h-3.5 w-3.5 mr-1" />
                      {skin.price} 星尘币
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </ScrollArea>
    </PageContainer>
  );
}
