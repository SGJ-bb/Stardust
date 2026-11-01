import { Outlet } from "react-router";
import { TitleBar } from "./TitleBar";
import { Sidebar } from "./Sidebar";
import { Live2DCanvas } from "@/components/live2d/Live2DCanvas";
import { AmbientBackground } from "@/components/effects/AmbientEffects";
import { RainOnGlass } from "@/components/effects/RainOnGlass";
import { useWeather } from "@/hooks/useWeather";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { ErrorBoundary } from "@/components/common/ErrorBoundary";
import { isTauri } from "@/lib/tauri";
import { useEmotionDriver } from "@/hooks/useEmotionDriver";
import { ToolConfirmDialog } from "@/components/chat/ToolConfirmDialog";

const DEFAULT_LIVE2D_MODEL = "F:\\stradust\\vtuber\\小恶魔.model3.json";

/** 将本地模型路径转为 live2d:// 协议 URL（仅 Tauri 环境） */
function toLive2dUrl(raw: string | undefined): string {
  if (!raw || raw.trim().length === 0) raw = DEFAULT_LIVE2D_MODEL;
  if (!isTauri()) return raw;
  const name = raw.replace(/\\/g, "/").split("/").pop() || raw;
  return `live2d://${name}`;
}

/**
 * 命中区路径：按模型文件名推导（public/models/zones_<key>.json）
 * whale2.model3.json → /models/zones_whale2.json
 * claudia_expr.model3.json → /models/zones_claudia.json
 * 文件不存在时 loadZones 会静默返回 null（点击交互自动关闭，不影响渲染）
 */
function zonesUrlFor(raw: string | undefined): string {
  const name = (raw || DEFAULT_LIVE2D_MODEL).replace(/\\/g, "/").split("/").pop() || "";
  const key = name
    .replace(/\.model3\.json$/i, "")
    .replace(/[_-](expr|v\d+|pkg).*$/i, "")
    .toLowerCase();
  return `/models/zones_${key}.json`;
}

export function AppLayout() {
  const { settings } = useSettingsStore();
  const { activePersona } = usePersonaStore();
  const { isRaining, rainIntensity } = useWeather(true);

  // 聊天情绪 → Live2D 表情/动作 自动驱动
  useEmotionDriver();

  // 天气动效开关逻辑（兼容老用户：weatherEffect 可能为 undefined）
  const weatherMode = settings.appearance.weatherEffect ?? "auto";
  const showRainEffect =
    weatherMode === "always" ||
    (weatherMode === "auto" && isRaining);

  // always 模式下用默认强度 0.55（中等雨量），auto 模式跟随真实天气
  const baseIntensity =
    weatherMode === "always" && !isRaining
      ? 0.55
      : rainIntensity;

  // 用户设置的密集度（0.2~2.0）作为乘数
  const rainDensity = settings.appearance.rainDensity ?? 1;
  const effectiveIntensity = baseIntensity * rainDensity;

  return (
    <div className="flex h-screen w-screen flex-col overflow-hidden relative">
      {/* 背景图层 */}
      <div className="app-bg" aria-hidden="true" />
      <AmbientBackground />

      {/* 雨滴打湿玻璃效果 */}
      <RainOnGlass active={showRainEffect} intensity={effectiveIntensity} />

      {/* 标题栏 — 可拖拽移动窗口 */}
      <TitleBar />

      {/* 主体：侧边栏 + 内容区 */}
      <div className="flex flex-1 overflow-hidden relative z-10">
        {/* 左侧边栏 */}
        <Sidebar />

        {/* 右侧内容区 */}
        <main className="flex-1 overflow-hidden relative min-w-0">
          <Outlet />
        </main>
      </div>

      {/* 工具执行确认弹窗（ask 权限） */}
      <ToolConfirmDialog />

      {/* Live2D 悬浮层 */}
      {settings.appearance.live2dVisible && (
        <ErrorBoundary>
          <Live2DCanvas
            scale={settings.appearance.live2dScale}
            opacity={settings.appearance.live2dOpacity}
            modelPath={toLive2dUrl(activePersona?.live2dModelPath)}
            zonesPath={zonesUrlFor(activePersona?.live2dModelPath)}
            gaze
            dock
          />
        </ErrorBoundary>
      )}
    </div>
  );
}
