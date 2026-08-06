import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { isTauri } from "@/lib/tauri";
import { useEffect, useState } from "react";

/**
 * 系统设置组件
 * 包含开机自启、最小化到托盘、全局快捷键、通知设置
 */
export function SystemSettings() {
  const { settings, updateSettings } = useSettingsStore();
  const [shortcutInput, setShortcutInput] = useState(settings.globalShortcut);
  const [isRecordingShortcut, setIsRecordingShortcut] = useState(false);

  /** 注册/注销开机自启 */
  const toggleAutoStart = async (enabled: boolean) => {
    updateSettings({ autoStart: enabled });
    try {
      if (enabled) {
        // Tauri autostart 插件启用
        const { enable } = await import("@tauri-apps/plugin-autostart");
        await enable();
      } else {
        const { disable } = await import("@tauri-apps/plugin-autostart");
        await disable();
      }
    } catch (error) {
      console.error("设置开机自启失败:", error);
    }
  };

  /** 注册全局快捷键 */
  const handleRegisterShortcut = async () => {
    if (!shortcutInput.trim() || !isTauri()) return;
    try {
      const { register, unregister } = await import("@tauri-apps/plugin-global-shortcut");
      // 先注销旧快捷键
      try { await unregister(settings.globalShortcut); } catch { /* 忽略 */ }
      // 注册新快捷键
      await register(shortcutInput.trim(), () => {
        // 全局快捷键触发：显示/隐藏窗口
        console.log("全局快捷键触发:", shortcutInput);
      });
      updateSettings({ globalShortcut: shortcutInput.trim() });
    } catch (error) {
      console.error("注册全局快捷键失败:", error);
    }
  };

  /** 录制快捷键 */
  const startRecordingShortcut = () => {
    setIsRecordingShortcut(true);
    setShortcutInput("");
    const handleKeyDown = (e: KeyboardEvent) => {
      e.preventDefault();
      const parts: string[] = [];
      if (e.ctrlKey || e.metaKey) parts.push("Ctrl");
      if (e.altKey) parts.push("Alt");
      if (e.shiftKey) parts.push("Shift");
      if (e.key !== "Control" && e.key !== "Alt" && e.key !== "Shift" && e.key !== "Meta") {
        parts.push(e.key.length === 1 ? e.key.toUpperCase() : e.key);
      }
      if (parts.length > 1 || (parts.length === 1 && !["Ctrl", "Alt", "Shift"].includes(parts[0]))) {
        setShortcutInput(parts.join("+"));
        setIsRecordingShortcut(false);
        window.removeEventListener("keydown", handleKeyDown);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
  };

  /** 切换通知 */
  const toggleNotification = async (enabled: boolean) => {
    updateSettings({ notificationEnabled: enabled });
    if (enabled) {
      try {
        const { requestPermission } = await import("@tauri-apps/plugin-notification");
        await requestPermission();
      } catch (error) {
        console.error("请求通知权限失败:", error);
      }
    }
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">系统设置</h2>

      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 开机自启 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium">开机自启动</label>
              <p className="text-xs text-[var(--color-muted-foreground)]">系统启动时自动运行应用</p>
            </div>
            <Switch
              checked={settings.autoStart}
              onCheckedChange={toggleAutoStart}
            />
          </div>

          <Separator />

          {/* 最小化到托盘 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium">最小化到托盘</label>
              <p className="text-xs text-[var(--color-muted-foreground)]">关闭窗口时最小化到系统托盘</p>
            </div>
            <Switch
              checked={settings.minimizeToTray}
              onCheckedChange={(checked) => updateSettings({ minimizeToTray: checked })}
            />
          </div>

          <Separator />

          {/* 全局快捷键 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">全局快捷键</label>
            <p className="text-xs text-[var(--color-muted-foreground)]">用于快速显示/隐藏窗口</p>
            <div className="flex gap-2">
              <Input
                value={isRecordingShortcut ? "请按下快捷键..." : shortcutInput}
                readOnly
                placeholder="如: Ctrl+Shift+S"
                className="flex-1"
                onClick={startRecordingShortcut}
              />
              <Button
                variant="outline"
                size="sm"
                onClick={handleRegisterShortcut}
                disabled={shortcutInput === settings.globalShortcut || !shortcutInput.trim()}
              >
                应用
              </Button>
            </div>
          </div>

          <Separator />

          {/* 通知 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium">启用通知</label>
              <p className="text-xs text-[var(--color-muted-foreground)]">接收闹钟、消息等系统通知</p>
            </div>
            <Switch
              checked={settings.notificationEnabled}
              onCheckedChange={toggleNotification}
            />
          </div>

          <Separator />

          {/* 语言 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">语言</label>
            <div className="flex gap-2">
              {(["zh-CN", "en-US", "ja-JP"] as const).map((lang) => (
                <button
                  key={lang}
                  onClick={() => updateSettings({ language: lang })}
                  className={`px-3 py-1.5 rounded-[var(--app-radius)] text-xs transition-colors ${
                    settings.language === lang
                      ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                      : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)]"
                  }`}
                >
                  {lang === "zh-CN" ? "中文" : lang === "en-US" ? "English" : "日本語"}
                </button>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
