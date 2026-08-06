import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { THEME_LABELS, THEME_LIST } from "@/components/common/ThemeProvider";
import { useTheme } from "@/hooks/useTheme";
import type { ThemeName } from "@/types/settings";

/**
 * 外观设置组件
 */
export function AppearanceSettings() {
  const { settings, updateSettings } = useSettingsStore();
  const { changeTheme, toggleDarkMode } = useTheme();
  const appearance = settings.appearance;

  const updateAppearance = (partial: Partial<typeof appearance>) => {
    updateSettings({ appearance: { ...appearance, ...partial } });
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">外观设置</h2>

      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 主题选择 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">主题配色</label>
            <div className="grid grid-cols-3 gap-2">
              {THEME_LIST.map((theme) => (
                <button
                  key={theme}
                  onClick={() => changeTheme(theme)}
                  className={`px-3 py-2 rounded-[var(--app-radius)] text-xs transition-colors ${
                    appearance.theme === theme
                      ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                      : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)] hover:bg-[var(--color-accent)]"
                  }`}
                >
                  {THEME_LABELS[theme]}
                </button>
              ))}
            </div>
          </div>

          <Separator />

          {/* 暗色模式 */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">暗色模式</label>
            <Switch checked={appearance.darkMode} onCheckedChange={toggleDarkMode} />
          </div>

          <Separator />

          {/* 字体大小 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">字体大小</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{appearance.fontSize}px</span>
            </div>
            <Slider
              value={[appearance.fontSize]}
              onValueChange={([v]) => updateAppearance({ fontSize: v })}
              min={12}
              max={20}
              step={1}
            />
          </div>

          {/* 气泡圆角 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">气泡圆角</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{appearance.bubbleRadius}px</span>
            </div>
            <Slider
              value={[appearance.bubbleRadius]}
              onValueChange={([v]) => updateAppearance({ bubbleRadius: v })}
              min={0}
              max={24}
              step={2}
            />
          </div>

          <Separator />

          {/* 侧边栏折叠 */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">折叠侧边栏</label>
            <Switch
              checked={appearance.sidebarCollapsed}
              onCheckedChange={(checked) => updateAppearance({ sidebarCollapsed: checked })}
            />
          </div>

          {/* Live2D显示 */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">显示Live2D角色</label>
            <Switch
              checked={appearance.live2dVisible}
              onCheckedChange={(checked) => updateAppearance({ live2dVisible: checked })}
            />
          </div>

          {/* Live2D透明度 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">Live2D透明度</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{Math.round(appearance.live2dOpacity * 100)}%</span>
            </div>
            <Slider
              value={[appearance.live2dOpacity]}
              onValueChange={([v]) => updateAppearance({ live2dOpacity: v })}
              min={0.1}
              max={1.0}
              step={0.05}
            />
          </div>

          {/* Live2D缩放 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">Live2D缩放</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{Math.round(appearance.live2dScale * 100)}%</span>
            </div>
            <Slider
              value={[appearance.live2dScale]}
              onValueChange={([v]) => updateAppearance({ live2dScale: v })}
              min={0.1}
              max={1.0}
              step={0.05}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
