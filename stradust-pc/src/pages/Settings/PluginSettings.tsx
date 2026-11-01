import { useState } from "react";
/**
 * 插件设置 —— 用户安装的插件开关 + 内置 AI 工具清单（只读展示）。
 */
import { usePluginStore } from "@/stores/usePluginStore";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { TOOL_DEFINITIONS, TOOL_LABELS, getToolPermission, setToolPermission, type ToolPermission } from "@/engine/plugins";
import { SectionTitle } from "./common";

/** 工具中文名注释 */

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

      {/* ═══════ 内置工具清单（引擎自带，AI 自动调用） ═══════ */}
      <SectionTitle
        title="内置 AI 工具"
        desc="以下工具由引擎内置、随对话自动提供给 AI。可为每个工具设置权限：自动允许 / 每次询问 / 拒绝（借鉴 Operit 的工具授权模型）"
      />
      <div className="space-y-2.5">
        {TOOL_DEFINITIONS.map((tool) => (
          <Card key={tool.name}>
            <CardContent className="p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-medium text-sm text-[var(--color-card-foreground)]">
                      {TOOL_LABELS[tool.name] ?? tool.name}
                    </h3>
                    <code className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[var(--color-muted)]/30 text-[var(--color-muted-foreground)]">
                      {tool.name}
                    </code>
                  </div>
                  <p className="text-xs text-[var(--color-muted-foreground)] mt-1 leading-relaxed">
                    {tool.description}
                  </p>
                  {Object.keys(tool.parameters?.properties ?? {}).length > 0 && (
                    <p className="text-[10px] text-[var(--color-muted-foreground)] opacity-60 mt-1.5">
                      参数：{Object.keys(tool.parameters.properties as Record<string, unknown>).join("、")}
                    </p>
                  )}
                </div>
                <PermSelector toolName={tool.name} />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

/** 单工具权限三档选择器 */
function PermSelector({ toolName }: { toolName: string }) {
  const [perm, setPerm] = useState<ToolPermission>(() => getToolPermission(toolName));
  const options: { value: ToolPermission; label: string }[] = [
    { value: "auto", label: "自动" },
    { value: "ask", label: "询问" },
    { value: "deny", label: "拒绝" },
  ];
  return (
    <div className="shrink-0 flex rounded-lg border border-[var(--color-border)] overflow-hidden">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => {
            setPerm(o.value);
            setToolPermission(toolName, o.value);
          }}
          className={`px-2.5 py-1 text-[11px] font-medium transition-colors ${
            perm === o.value
              ? o.value === "deny"
                ? "bg-red-500/15 text-red-500"
                : o.value === "ask"
                  ? "bg-amber-500/15 text-amber-600"
                  : "bg-[var(--color-primary)]/15 text-[var(--color-primary)]"
              : "text-[var(--color-muted-foreground)] hover:bg-[var(--color-muted)]/40"
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
