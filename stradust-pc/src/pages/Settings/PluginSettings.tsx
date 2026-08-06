import { usePluginStore } from "@/stores/usePluginStore";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";

/**
 * 插件设置组件
 */
export function PluginSettings() {
  const { plugins, togglePlugin } = usePluginStore();

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">插件管理</h2>

      {plugins.length === 0 ? (
        <p className="text-sm text-[var(--color-muted-foreground)] text-center py-8">暂无已安装插件</p>
      ) : (
        <div className="space-y-3">
          {plugins.map((plugin) => (
            <Card key={plugin.id}>
              <CardContent className="p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-medium text-sm text-[var(--color-card-foreground)]">{plugin.name}</h3>
                  </div>
                  <Switch
                    checked={plugin.enabled}
                    onCheckedChange={() => togglePlugin(plugin.id)}
                  />
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
