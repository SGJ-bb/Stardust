import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";

/**
 * 记忆设置组件
 */
export function MemorySettings() {
  const { settings, updateSettings } = useSettingsStore();
  const memory = settings.memory;

  const updateMemory = (partial: Partial<typeof memory>) => {
    updateSettings({ memory: { ...memory, ...partial } });
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">记忆设置</h2>

      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 启用记忆 */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">启用记忆</label>
            <Switch checked={memory.enabled} onCheckedChange={(checked) => updateMemory({ enabled: checked })} />
          </div>

          <Separator />

          {/* 短期记忆容量 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">短期记忆容量</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{memory.shortTermCapacity}</span>
            </div>
            <Slider value={[memory.shortTermCapacity]} onValueChange={([v]) => updateMemory({ shortTermCapacity: v })} min={5} max={50} step={5} />
          </div>

          {/* 长期记忆容量 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">长期记忆容量</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{memory.longTermCapacity}</span>
            </div>
            <Slider value={[memory.longTermCapacity]} onValueChange={([v]) => updateMemory({ longTermCapacity: v })} min={20} max={500} step={20} />
          </div>

          {/* 核心记忆容量 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">核心记忆容量</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{memory.coreCapacity}</span>
            </div>
            <Slider value={[memory.coreCapacity]} onValueChange={([v]) => updateMemory({ coreCapacity: v })} min={10} max={200} step={10} />
          </div>

          <Separator />

          {/* 自动提取 */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">自动提取记忆</label>
            <Switch checked={memory.autoExtract} onCheckedChange={(checked) => updateMemory({ autoExtract: checked })} />
          </div>

          {/* 检索数量 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">检索数量</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{memory.retrievalCount}</span>
            </div>
            <Slider value={[memory.retrievalCount]} onValueChange={([v]) => updateMemory({ retrievalCount: v })} min={1} max={20} step={1} />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
