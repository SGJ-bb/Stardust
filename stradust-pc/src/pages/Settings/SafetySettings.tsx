import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";

/**
 * 安全设置组件
 */
export function SafetySettings() {
  const { settings, updateSettings } = useSettingsStore();
  const safety = settings.safety;

  const updateSafety = (partial: Partial<typeof safety>) => {
    updateSettings({ safety: { ...safety, ...partial } });
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">安全设置</h2>

      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 内容过滤等级 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">内容过滤等级</label>
            <div className="flex gap-2">
              {(["none", "low", "medium", "high"] as const).map((level) => (
                <button
                  key={level}
                  onClick={() => updateSafety({ contentFilterLevel: level })}
                  className={`px-3 py-1.5 rounded-[var(--app-radius)] text-xs transition-colors ${
                    safety.contentFilterLevel === level
                      ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                      : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)]"
                  }`}
                >
                  {level === "none" ? "无" : level === "low" ? "低" : level === "medium" ? "中" : "高"}
                </button>
              ))}
            </div>
          </div>

          <Separator />

          {/* NSFW过滤 */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">NSFW过滤</label>
            <Switch checked={safety.nsfwFilter} onCheckedChange={(checked) => updateSafety({ nsfwFilter: checked })} />
          </div>

          <Separator />

          {/* 自定义敏感词 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">自定义敏感词</label>
            <Input
              placeholder="用逗号分隔敏感词"
              value={safety.blockedWords.join(", ")}
              onChange={(e) =>
                updateSafety({
                  blockedWords: e.target.value.split(",").map((w) => w.trim()).filter(Boolean),
                })
              }
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
