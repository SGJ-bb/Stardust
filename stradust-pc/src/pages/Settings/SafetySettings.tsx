/**
 * 安全设置 —— 内容过滤 + AI 功能开关清单（对齐 Android SettingsManager 各项 enabled 开关）。
 * 旧持久化 settings 可能缺 aiFeatures 节，读取一律 `?? 默认值`。
 */
import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import type { AiFeaturesConfig } from "@/types/settings";
import { Select, SectionTitle, SettingRow } from "./common";

/** 与引擎侧兜底一致的默认开关（useSettingsStore.DEFAULT_SETTINGS.aiFeatures） */
const FEATURES_DEFAULTS: AiFeaturesConfig = {
  emotionAnalysisEnabled: false,
  llmEmotionAnalysisEnabled: true,
  searchEnabled: true,
  searchProvider: "duckduckgo",
  live2dEnabled: true,
  offlineModeEnabled: false,
  screenAwarenessEnabled: false,
  safetyFilterEnabled: true,
  humanizerEnabled: true,
  replyPredictEnabled: true,
  proactiveEnabled: true,
};

/** 功能开关清单：key → 中文名 + 用途说明 */
const FEATURE_ITEMS: Array<{
  key: keyof Omit<AiFeaturesConfig, "searchProvider">;
  label: string;
  desc: string;
}> = [
  { key: "emotionAnalysisEnabled", label: "情绪分析（本地模型）", desc: "用轻量方式分析每句话的情绪波动，驱动表情与好感度变化" },
  { key: "llmEmotionAnalysisEnabled", label: "情绪分析（LLM 判定）", desc: "让大模型参与判断你话语里的情绪，更细腻但多耗一点 token" },
  { key: "searchEnabled", label: "联网搜索", desc: "允许 AI 调用 search_web 工具查询新闻、天气等实时信息" },
  { key: "live2dEnabled", label: "Live2D 角色", desc: "总开关：关闭后不再加载 Live2D 桌宠渲染" },
  { key: "offlineModeEnabled", label: "离线模式", desc: "仅使用本地能力，暂停一切云端请求" },
  { key: "screenAwarenessEnabled", label: "屏幕感知", desc: "允许 AI 读取当前屏幕内容作为对话上下文（Web 等价实现，谨慎开启）" },
  { key: "safetyFilterEnabled", label: "安全过滤器", desc: "拦截不当输入输出的总闸门，建议保持开启" },
  { key: "humanizerEnabled", label: "拟人化改写", desc: "把 AI 腔的长回复拆句、加口语词，更像真人在打字" },
  { key: "replyPredictEnabled", label: "回复预测", desc: "生成「你可能会这样回」的候选句，方便一键接话" },
  { key: "proactiveEnabled", label: "主动消息", desc: "允许 AI 按节奏主动找你搭话、发日记和朋友圈" },
];

const FILTER_LEVELS = [
  { value: "none", label: "关闭" },
  { value: "low", label: "轻度" },
  { value: "medium", label: "中度" },
  { value: "high", label: "严格" },
];

/**
 * 安全设置组件
 */
export function SafetySettings() {
  const { settings, updateSettings } = useSettingsStore();
  const safety = settings.safety;
  const features = settings.aiFeatures ?? FEATURES_DEFAULTS;

  const updateSafety = (partial: Partial<typeof safety>) => {
    updateSettings({ safety: { ...safety, ...partial } });
  };
  const patchFeatures = (partial: Partial<AiFeaturesConfig>) => {
    updateSettings({ aiFeatures: { ...FEATURES_DEFAULTS, ...features, ...partial } });
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">安全与功能</h2>

      {/* ═══════ 内容过滤 ═══════ */}
      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 内容过滤等级 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">内容过滤等级</label>
            <div className="flex gap-2">
              {FILTER_LEVELS.map((level) => (
                <button
                  key={level.value}
                  onClick={() => updateSafety({ contentFilterLevel: level.value as typeof safety.contentFilterLevel })}
                  className={`px-3 py-1.5 rounded-[var(--app-radius)] text-xs transition-colors ${
                    safety.contentFilterLevel === level.value
                      ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                      : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)]"
                  }`}
                >
                  {level.label}
                </button>
              ))}
            </div>
            <p className="text-[11px] text-[var(--color-muted-foreground)] opacity-70">
              等级越高，对暴力/成人等敏感内容的拦截越严格。
            </p>
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
            <p className="text-[11px] text-[var(--color-muted-foreground)] opacity-70">
              命中敏感词的聊天内容会被替换处理；AI 回复中出现时同样会被过滤。
            </p>
          </div>
        </CardContent>
      </Card>

      {/* ═══════ AI 功能开关 ═══════ */}
      <SectionTitle title="功能开关" desc="逐项控制 AI 的增强能力，全部对应引擎运行时的实际行为" />
      <Card>
        <CardContent className="p-4 space-y-4">
          {FEATURE_ITEMS.map((item) => (
            <SettingRow key={item.key} title={item.label} desc={item.desc}>
              <Switch
                checked={features[item.key] ?? FEATURES_DEFAULTS[item.key]}
                onCheckedChange={(checked) => patchFeatures({ [item.key]: checked } as Partial<AiFeaturesConfig>)}
              />
            </SettingRow>
          ))}
          <Separator />
          <SettingRow title="搜索引擎" desc="联网搜索工具使用的引擎渠道">
            <div className="w-36">
              <Select
                value={features.searchProvider ?? "duckduckgo"}
                onChange={(v) => patchFeatures({ searchProvider: v as AiFeaturesConfig["searchProvider"] })}
                options={[
                  { value: "duckduckgo", label: "DuckDuckGo" },
                  { value: "bing", label: "Bing" },
                  { value: "baidu", label: "百度" },
                ]}
              />
            </div>
          </SettingRow>
        </CardContent>
      </Card>
    </div>
  );
}
