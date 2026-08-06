import { useNavigate, useSearchParams } from "react-router";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Slider } from "@/components/ui/slider";
import {
  ArrowLeft,
  Brain,
  Mic,
  Palette,
  Database,
  Puzzle,
  Shield,
  Monitor,
  Sun,
  Moon,
  Check,
  Sparkles,
} from "lucide-react";
import { LLMSettings } from "./LLMSettings";
import { VoiceSettings } from "./VoiceSettings";
import { AppearanceSettings } from "./AppearanceSettings";
import { MemorySettings } from "./MemorySettings";
import { PluginSettings } from "./PluginSettings";
import { SafetySettings } from "./SafetySettings";
import { SystemSettings } from "./SystemSettings";
import { PixelPetManagePanel } from "@/components/pixelpet";
import { useState } from "react";
import { useTheme } from "@/hooks/useTheme";

/** 设置分组 */
const SETTING_GROUPS = [
  {
    key: "appearance",
    label: "外观",
    icon: Palette,
    component: AppearanceSettings,
  },
  { key: "voice", label: "语音", icon: Mic, component: VoiceSettings },
  { key: "safety", label: "安全", icon: Shield, component: SafetySettings },
  { key: "plugin", label: "插件", icon: Puzzle, component: PluginSettings },
  { key: "memory", label: "记忆", icon: Database, component: MemorySettings },
  { key: "llm", label: "LLM模型", icon: Brain, component: LLMSettings },
  {
    key: "pixelpet",
    label: "像素宠物",
    icon: Sparkles,
    component: PixelPetManagePanel,
  },
  { key: "system", label: "系统", icon: Monitor, component: SystemSettings },
];

/** 12个主题配置 */
const THEME_OPTIONS = [
  { key: "sakura", label: "樱粉", color: "#ec4899" },
  { key: "peach", label: "桃粉", color: "#f97316" },
  { key: "violet", label: "紫罗兰", color: "#8b5cf6" },
  { key: "ocean", label: "海蓝", color: "#3b82f6" },
  { key: "emerald", label: "翡翠", color: "#10b981" },
  { key: "sunset", label: "日落", color: "#f59e0b" },
  { key: "rosegold", label: "玫瑰金", color: "#e11d48" },
  { key: "mint", label: "薄荷", color: "#14b8a6" },
  { key: "midnight", label: "暗夜", color: "#6366f1" },
  { key: "tea", label: "茶香", color: "#6b8e5a" },
  { key: "cyberpunk", label: "赛博朋克", color: "#00f0ff" },
  { key: "chinese", label: "中式美学", color: "#c53d43" },
];

/**
 * 设置页面
 * 对应Android SettingsActivity
 */
export function SettingsPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialTab = searchParams.get("tab");
  const validKeys = SETTING_GROUPS.map((g) => g.key);
  const [activeGroup, setActiveGroup] = useState(
    initialTab && validKeys.includes(initialTab) ? initialTab : "appearance",
  );
  const { settings, updateSettings } = useSettingsStore();
  const { changeTheme, toggleDarkMode } = useTheme();

  const ActiveComponent =
    SETTING_GROUPS.find((g) => g.key === activeGroup)?.component ??
    AppearanceSettings;
  const appearance = settings.appearance;

  return (
    <PageContainer>
      {/* 页面标题 */}
      <div className="flex items-center gap-3 mb-6">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => navigate(-1)}
          className="h-9 w-9"
        >
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">
          设置
        </h1>
      </div>

      <div className="flex gap-6 h-[calc(100vh-120px)]">
        {/* ========== 左侧设置分类导航 (glass-panel sidebar风格) ========== */}
        <div className="w-56 shrink-0 glass-panel rounded-[var(--radius-lg)] p-3 flex flex-col gap-1">
          {SETTING_GROUPS.map((group) => {
            const Icon = group.icon;
            const isActive = activeGroup === group.key;
            return (
              <button
                key={group.key}
                onClick={() => setActiveGroup(group.key)}
                className={`sidebar-item ${isActive ? "active" : ""}`}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span>{group.label}</span>
              </button>
            );
          })}
        </div>

        {/* ========== 右侧设置内容区 ========== */}
        <div className="flex-1 overflow-hidden flex flex-col">
          <ScrollArea className="flex-1 pr-2">
            <div className="space-y-6 pb-20">
              {/* ===== 外观设置特殊处理：主题选择器 + 暗色模式 ===== */}
              {activeGroup === "appearance" && (
                <>
                  {/* 分组标题 */}
                  <p className="sidebar-section-label">主题与外观</p>

                  {/* 主题选择器 - 12个主题色块网格 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between mb-4">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          主题配色
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          选择你喜欢的界面配色方案
                        </p>
                      </div>
                      <span className="text-xs text-[var(--color-primary)] font-medium px-2 py-1 rounded-full bg-[var(--color-primary)]/10">
                        {THEME_OPTIONS.find((t) => t.key === appearance.theme)
                          ?.label ?? ""}
                      </span>
                    </div>
                    <div className="grid grid-cols-4 sm:grid-cols-6 gap-3">
                      {THEME_OPTIONS.map((theme) => {
                        const isActive = appearance.theme === theme.key;
                        return (
                          <button
                            key={theme.key}
                            onClick={() => changeTheme(theme.key as any)}
                            className={`relative group aspect-square rounded-xl transition-all duration-300 ${
                              isActive
                                ? "ring-2 ring-offset-2 ring-offset-[var(--color-card)] scale-105 shadow-lg"
                                : "hover:scale-105 hover:shadow-md"
                            }`}
                            style={{
                              backgroundColor: theme.color,
                              ...(isActive ? { ringColor: theme.color } : {}),
                            }}
                            title={theme.label}
                          >
                            {/* 选中标记 */}
                            {isActive && (
                              <div className="absolute inset-0 flex items-center justify-center">
                                <div className="w-5 h-5 rounded-full bg-white/90 flex items-center justify-center shadow-sm">
                                  <Check
                                    className="h-3 w-3 text-gray-800"
                                    strokeWidth={3}
                                  />
                                </div>
                              </div>
                            )}
                            {/* hover提示 */}
                            <div className="absolute -bottom-7 left-1/2 -translate-x-1/2 opacity-0 group-hover:opacity-100 transition-opacity text-[10px] text-[var(--color-muted-foreground)] whitespace-nowrap pointer-events-none">
                              {theme.label}
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* 亮/暗模式切换开关 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-[var(--color-muted)] flex items-center justify-center">
                          {appearance.darkMode ? (
                            <Moon className="h-5 w-5 text-[var(--color-primary)]" />
                          ) : (
                            <Sun className="h-5 w-5 text-[var(--color-primary)]" />
                          )}
                        </div>
                        <div>
                          <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                            {appearance.darkMode ? "暗色模式" : "亮色模式"}
                          </h3>
                          <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                            切换界面明暗显示
                          </p>
                        </div>
                      </div>
                      <Switch
                        checked={appearance.darkMode}
                        onCheckedChange={toggleDarkMode}
                      />
                    </div>
                  </div>

                  {/* 字体大小 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          字体大小
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          调整界面文字大小
                        </p>
                      </div>
                      <span className="text-xs font-medium text-[var(--color-primary)] tabular-nums px-2 py-1 rounded-full bg-[var(--color-primary)]/10">
                        {appearance.fontSize}px
                      </span>
                    </div>
                    <Slider
                      value={[appearance.fontSize]}
                      onValueChange={([v]) =>
                        updateSettings({
                          appearance: { ...appearance, fontSize: v },
                        })
                      }
                      min={12}
                      max={20}
                      step={1}
                    />
                  </div>

                  {/* 气泡圆角 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          气泡圆角
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          聊天气泡的圆角半径
                        </p>
                      </div>
                      <span className="text-xs font-medium text-[var(--color-primary)] tabular-nums px-2 py-1 rounded-full bg-[var(--color-primary)]/10">
                        {appearance.bubbleRadius}px
                      </span>
                    </div>
                    <Slider
                      value={[appearance.bubbleRadius]}
                      onValueChange={([v]) =>
                        updateSettings({
                          appearance: { ...appearance, bubbleRadius: v },
                        })
                      }
                      min={0}
                      max={24}
                      step={2}
                    />
                  </div>

                  {/* 侧边栏折叠 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          折叠侧边栏
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          收起侧边栏以获得更多空间
                        </p>
                      </div>
                      <Switch
                        checked={appearance.sidebarCollapsed}
                        onCheckedChange={(checked) =>
                          updateSettings({
                            appearance: {
                              ...appearance,
                              sidebarCollapsed: checked,
                            },
                          })
                        }
                      />
                    </div>
                  </div>

                  {/* Live2D 显示 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          显示 Live2D 角色
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          在聊天界面展示 Live2D 角色
                        </p>
                      </div>
                      <Switch
                        checked={appearance.live2dVisible}
                        onCheckedChange={(checked) =>
                          updateSettings({
                            appearance: {
                              ...appearance,
                              live2dVisible: checked,
                            },
                          })
                        }
                      />
                    </div>
                  </div>

                  {/* Live2D 透明度 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          Live2D 透明度
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          调整角色透明度
                        </p>
                      </div>
                      <span className="text-xs font-medium text-[var(--color-primary)] tabular-nums px-2 py-1 rounded-full bg-[var(--color-primary)]/10">
                        {Math.round(appearance.live2dOpacity * 100)}%
                      </span>
                    </div>
                    <Slider
                      value={[appearance.live2dOpacity]}
                      onValueChange={([v]) =>
                        updateSettings({
                          appearance: { ...appearance, live2dOpacity: v },
                        })
                      }
                      min={0.1}
                      max={1.0}
                      step={0.05}
                    />
                  </div>

                  {/* Live2D 缩放 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          Live2D 缩放
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          调整角色显示大小
                        </p>
                      </div>
                      <span className="text-xs font-medium text-[var(--color-primary)] tabular-nums px-2 py-1 rounded-full bg-[var(--color-primary)]/10">
                        {Math.round(appearance.live2dScale * 100)}%
                      </span>
                    </div>
                    <Slider
                      value={[appearance.live2dScale]}
                      onValueChange={([v]) =>
                        updateSettings({
                          appearance: { ...appearance, live2dScale: v },
                        })
                      }
                      min={0.1}
                      max={1.0}
                      step={0.05}
                    />
                  </div>

                  {/* 天气动效开关 */}
                  <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                    <div className="flex items-center justify-between mb-4">
                      <div>
                        <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                          雨滴玻璃效果
                        </h3>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                          下雨天屏幕显示雨滴打湿玻璃效果
                        </p>
                      </div>
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      {[
                        { key: "auto", label: "自动", desc: "根据天气" },
                        { key: "always", label: "始终开启", desc: "常驻效果" },
                        { key: "off", label: "关闭", desc: "不显示" },
                      ].map((opt) => {
                        const isActive =
                          (appearance.weatherEffect ?? "auto") === opt.key;
                        return (
                          <button
                            key={opt.key}
                            onClick={() =>
                              updateSettings({
                                appearance: {
                                  ...appearance,
                                  weatherEffect: opt.key as
                                    "auto" | "always" | "off",
                                },
                              })
                            }
                            className={`relative flex flex-col items-center gap-1 px-3 py-2.5 rounded-lg border transition-all duration-200 ${
                              isActive
                                ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
                                : "border-[var(--color-border)] text-[var(--color-muted-foreground)] hover:border-[var(--color-muted-foreground)]"
                            }`}
                          >
                            <span className="text-xs font-semibold">
                              {opt.label}
                            </span>
                            <span className="text-[10px] opacity-70">
                              {opt.desc}
                            </span>
                            {isActive && (
                              <div
                                className="absolute bottom-1 left-1/2 -translate-x-1/2 w-5 h-0.5 rounded-full"
                                style={{ background: "var(--color-primary)" }}
                              />
                            )}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* 雨滴密集度滑块（仅在天气效果开启时显示） */}
                  {(appearance.weatherEffect ?? "auto") !== "off" && (
                    <div className="glass-card p-5 rounded-[var(--radius-lg)]">
                      <div className="flex items-center justify-between mb-3">
                        <div>
                          <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                            雨滴密集度
                          </h3>
                          <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                            调整雨滴数量和溅射频率
                          </p>
                        </div>
                        <span className="text-sm font-mono font-medium px-2.5 py-1 rounded-md bg-[var(--color-muted)]/30 text-[var(--color-primary)]">
                          {((appearance.rainDensity ?? 1) * 100).toFixed(0)}%
                        </span>
                      </div>
                      <Slider
                        value={[appearance.rainDensity ?? 1]}
                        min={0.2}
                        max={2.0}
                        step={0.1}
                        onValueChange={([v]) =>
                          updateSettings({
                            appearance: {
                              ...appearance,
                              rainDensity: v,
                            },
                          })
                        }
                      />
                      <div className="flex justify-between mt-1.5 text-[10px] text-[var(--color-muted-foreground)]">
                        <span>稀疏</span>
                        <span>标准</span>
                        <span>密集</span>
                      </div>
                    </div>
                  )}
                </>
              )}

              {/* 其他设置组件 */}
              {activeGroup !== "appearance" && <ActiveComponent />}
            </div>
          </ScrollArea>
        </div>
      </div>

      {/* 打赏区域 */}
      <div className="mt-8 mb-4">
        <div className="rounded-2xl overflow-hidden border border-[var(--color-border)] bg-gradient-to-br from-green-500/5 to-emerald-500/5 p-6 text-center">
          <p className="text-sm font-medium text-[var(--color-card-foreground)] mb-3">
            💚 支持一下作者吧，作者要没米吃饭了
          </p>
          <div className="inline-block rounded-xl overflow-hidden shadow-lg max-w-[280px]">
            <img
              src="/donate-qrcode.png"
              alt="微信支付赞赏码"
              className="w-full h-auto"
              draggable={false}
            />
          </div>
          <p className="text-[10px] text-[var(--color-muted-foreground)] mt-3">
            扫码支持 · 感谢你的每一份心意 ✨
          </p>
        </div>
      </div>
    </PageContainer>
  );
}
