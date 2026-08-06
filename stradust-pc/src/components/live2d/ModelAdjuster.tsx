import { useState } from "react";
import { Slider } from "@/components/ui/slider";
import { Button } from "@/components/ui/button";
import { useLive2D } from "@/hooks/useLive2D";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { ZoomIn, ZoomOut, Move } from "lucide-react";

/**
 * 模型调整组件
 * 支持缩放/拖动调整
 */
export function ModelAdjuster() {
  const { setModelScale } = useLive2D();
  const { settings, updateSettings } = useSettingsStore();
  const [scale, setScale] = useState(settings.appearance.live2dScale);

  const handleScaleChange = (value: number[]) => {
    const newScale = value[0];
    setScale(newScale);
    setModelScale(newScale);
    updateSettings({
      appearance: { ...settings.appearance, live2dScale: newScale },
    });
  };

  return (
    <div className="space-y-4 p-4">
      <h4 className="text-sm font-medium text-[var(--color-card-foreground)]">模型调整</h4>

      {/* 缩放调整 */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-xs text-[var(--color-muted-foreground)]">缩放</span>
          <span className="text-xs text-[var(--color-muted-foreground)]">{Math.round(scale * 100)}%</span>
        </div>
        <div className="flex items-center gap-2">
          <ZoomOut className="h-4 w-4 text-[var(--color-muted-foreground)]" />
          <Slider
            value={[scale]}
            onValueChange={handleScaleChange}
            min={0.1}
            max={1.0}
            step={0.05}
            className="flex-1"
          />
          <ZoomIn className="h-4 w-4 text-[var(--color-muted-foreground)]" />
        </div>
      </div>

      {/* 快捷缩放 */}
      <div className="flex gap-2">
        <Button variant="outline" size="sm" onClick={() => handleScaleChange([0.15])}>
          小
        </Button>
        <Button variant="outline" size="sm" onClick={() => handleScaleChange([0.25])}>
          中
        </Button>
        <Button variant="outline" size="sm" onClick={() => handleScaleChange([0.4])}>
          大
        </Button>
      </div>
    </div>
  );
}
