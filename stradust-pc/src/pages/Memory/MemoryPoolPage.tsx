import { useParams, useNavigate } from "react-router";
import { useMemoryStore } from "@/stores/useMemoryStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, Database, Trash2 } from "lucide-react";

/**
 * 记忆池页面
 * 对应Android MemoryPoolActivity
 */
export function MemoryPoolPage() {
  const { personaId } = useParams<{ personaId: string }>();
  const navigate = useNavigate();
  const { currentMemory } = useMemoryStore();

  const pools = currentMemory?.pools ?? [];

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">记忆池</h1>
      </div>

      <ScrollArea className="flex-1">
        {pools.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <Database className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
            <p className="text-[var(--color-muted-foreground)]">暂无记忆池</p>
          </div>
        ) : (
          <div className="space-y-3">
            {pools.map((pool) => (
              <Card key={pool.id}>
                <CardContent className="p-4">
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-medium text-[var(--color-card-foreground)]">{pool.name}</h3>
                      <p className="text-xs text-[var(--color-muted-foreground)] mt-1">{pool.description}</p>
                      <p className="text-xs text-[var(--color-muted-foreground)] mt-1">
                        {pool.entries.length} / {pool.maxCapacity} 条记忆
                      </p>
                    </div>
                    <Button variant="ghost" size="icon">
                      <Trash2 className="h-4 w-4" />
                    </Button>
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
