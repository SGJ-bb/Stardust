/**
 * 系统设置 —— 常规（自启/托盘/快捷键/通知/语言）+ 生活节奏 + 定时唤醒 + 数据管理。
 * 旧持久化 settings 可能缺 rhythm/wake 节，读取一律 `settings.xxx?.yyy ?? 默认值`。
 */
import { useEffect, useRef, useState } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { isTauri, wakeTasksApi, migrationApi } from "@/lib/tauri";
import type { WakeTaskRecord } from "@/engine/kinds";
import type { RhythmConfig, WakeConfig } from "@/types/settings";
import { BellRing, Download, Upload, Smartphone, Trash2, Plus } from "lucide-react";
import { Select, SectionTitle, SettingRow } from "./common";

const RHYTHM_DEFAULTS: RhythmConfig = {
  nagFrequency: "medium",
  diaryTriggerMode: "daily22",
  momentsEnabled: true,
  checkInReminder: true,
};
const WAKE_DEFAULTS: WakeConfig = { enabled: false, hour: 8, minute: 0, notifyDesktop: true };

const NAG_OPTIONS = [
  { value: "off", label: "关闭" },
  { value: "low", label: "低频（偶尔搭话）" },
  { value: "medium", label: "中频（默认）" },
  { value: "high", label: "高频（经常粘人）" },
];

const DIARY_OPTIONS = [
  { value: "manual", label: "仅手动写日记" },
  { value: "msg50", label: "每约 50 条消息触发" },
  { value: "hourly", label: "每小时触发" },
  { value: "every2h", label: "每 2 小时触发" },
  { value: "daily22", label: "每天 22 点（推荐）" },
];

const REPEAT_LABELS: Record<WakeTaskRecord["repeat"], string> = {
  daily: "每天",
  weekday: "工作日",
  once: "仅一次",
};

/**
 * 系统设置组件
 */
export function SystemSettings() {
  const { settings, updateSettings } = useSettingsStore();
  const [shortcutInput, setShortcutInput] = useState(settings.globalShortcut);
  const [isRecordingShortcut, setIsRecordingShortcut] = useState(false);

  const rhythm = settings.rhythm ?? RHYTHM_DEFAULTS;
  const wake = settings.wake ?? WAKE_DEFAULTS;
  const patchRhythm = (partial: Partial<RhythmConfig>) => updateSettings({ rhythm: { ...rhythm, ...partial } });
  const patchWake = (partial: Partial<WakeConfig>) => updateSettings({ wake: { ...wake, ...partial } });

  // ─── 通知权限状态 ───
  const [notifPerm, setNotifPerm] = useState<string>(
    typeof Notification !== "undefined" ? Notification.permission : "unsupported",
  );
  const requestNotifPermission = async () => {
    if (typeof Notification === "undefined") { setNotifPerm("unsupported"); return; }
    const p = await Notification.requestPermission();
    setNotifPerm(p);
    try {
      if (p === "granted") new Notification("星尘", { body: "通知权限已开启，到点会在这里提醒你～" });
    } catch { /* ignore */ }
  };

  // ─── 唤醒任务列表 ───
  const personas = usePersonaStore((s) => s.personas);
  const activePersona = usePersonaStore((s) => s.activePersona);
  const personaId = activePersona?.id ?? personas[0]?.id ?? "default";
  const [tasks, setTasks] = useState<WakeTaskRecord[]>([]);
  const [showTaskForm, setShowTaskForm] = useState(false);
  const [taskForm, setTaskForm] = useState({ name: "", time: "09:00", repeat: "daily" as WakeTaskRecord["repeat"], promptHint: "" });
  useEffect(() => {
    let cancelled = false;
    void wakeTasksApi.list(personaId).then((t) => { if (!cancelled) setTasks(t); }).catch(() => {});
    return () => { cancelled = true; };
  }, [personaId]);

  const addTask = async () => {
    if (!taskForm.name.trim() || !/^\d{1,2}:\d{2}$/.test(taskForm.time)) return;
    await wakeTasksApi.add({ personaId, ...taskForm });
    setTasks(await wakeTasksApi.list(personaId));
    setTaskForm({ name: "", time: "09:00", repeat: "daily", promptHint: "" });
    setShowTaskForm(false);
  };
  const removeTask = async (id: string) => {
    await wakeTasksApi.remove(id);
    setTasks(await wakeTasksApi.list(personaId));
  };
  const toggleTask = async (id: string, enabled: boolean) => {
    await wakeTasksApi.toggle(id, enabled);
    setTasks((prev) => prev.map((t) => (t.id === id ? { ...t, enabled } : t)));
  };

  // ─── 数据管理 ───
  const [dataMsg, setDataMsg] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const [importMode, setImportMode] = useState<"merge" | "replace">("merge");
  const snapshotInputRef = useRef<HTMLInputElement>(null);
  const androidInputRef = useRef<HTMLInputElement>(null);

  const handleExport = async () => {
    try {
      const blob = await migrationApi.exportSnapshot();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "stradust-backup.json";
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
      setDataMsg({ kind: "ok", text: "快照已导出为 stradust-backup.json" });
    } catch (e) {
      setDataMsg({ kind: "err", text: `导出失败：${(e as Error).message}` });
    }
  };

  const handleImportSnapshot = async (file: File) => {
    try {
      const r = await migrationApi.importSnapshot(file, importMode);
      setDataMsg({
        kind: "ok",
        text: `导入完成（${importMode === "merge" ? "合并" : "覆盖"}）：消息 ${r.messages} 条、日记 ${r.diaries} 篇，共写入 ${r.collections} 个集合`,
      });
    } catch (e) {
      setDataMsg({ kind: "err", text: `导入失败：${(e as Error).message}` });
    }
  };

  const handleImportAndroid = async (file: File) => {
    try {
      const text = await file.text();
      const r = await migrationApi.importAndroidJson(text, personaId);
      setDataMsg({ kind: "ok", text: `Android 备份导入完成：成功导入 ${r.count} 条记录（无法识别的字段已跳过）` });
    } catch (e) {
      setDataMsg({ kind: "err", text: `导入失败：${(e as Error).message}` });
    }
  };

  /** 注册/注销开机自启 */
  const toggleAutoStart = async (enabled: boolean) => {
    updateSettings({ autoStart: enabled });
    try {
      if (enabled) {
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
      } catch {
        void requestNotifPermission();
      }
    }
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">系统设置</h2>

      {/* ═══════ 常规 ═══════ */}
      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 开机自启 */}
          <div className="flex items-center justify-between">
            <div>
              <label className="text-sm font-medium">开机自启动</label>
              <p className="text-xs text-[var(--color-muted-foreground)]">系统启动时自动运行应用</p>
            </div>
            <Switch checked={settings.autoStart} onCheckedChange={toggleAutoStart} />
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

      {/* ═══════ 生活节奏 ═══════ */}
      <SectionTitle title="生活节奏" desc="AI 主动搭话的频率与写日记、发朋友圈的习惯" />
      <Card>
        <CardContent className="p-4 space-y-4">
          <SettingRow title="主动搭话频率" desc="AI 隔多久忍不住先找你说话一次">
            <div className="w-44">
              <Select
                value={rhythm.nagFrequency ?? "medium"}
                onChange={(v) => patchRhythm({ nagFrequency: v as RhythmConfig["nagFrequency"] })}
                options={NAG_OPTIONS}
              />
            </div>
          </SettingRow>
          <SettingRow title="日记触发方式" desc="AI 什么时候动笔记录你们的一天">
            <div className="w-44">
              <Select
                value={rhythm.diaryTriggerMode ?? "daily22"}
                onChange={(v) => patchRhythm({ diaryTriggerMode: v as RhythmConfig["diaryTriggerMode"] })}
                options={DIARY_OPTIONS}
              />
            </div>
          </SettingRow>
          <SettingRow title="AI 朋友圈" desc="允许 AI 自发动态、评论你们的共同回忆">
            <Switch checked={rhythm.momentsEnabled !== false} onCheckedChange={(checked) => patchRhythm({ momentsEnabled: checked })} />
          </SettingRow>
          <SettingRow title="签到提醒" desc="每天提醒你来打个卡，积累羁绊">
            <Switch checked={rhythm.checkInReminder !== false} onCheckedChange={(checked) => patchRhythm({ checkInReminder: checked })} />
          </SettingRow>
        </CardContent>
      </Card>

      {/* ═══════ 定时唤醒 ═══════ */}
      <SectionTitle
        title="定时唤醒"
        desc="Web 等价实现：调度在页面内进行，需保持本页面打开才会准时触发并弹桌面通知"
      />
      <Card>
        <CardContent className="p-4 space-y-4">
          <SettingRow title="每日定时唤醒" desc="到点后 AI 会主动来问候你一次">
            <Switch checked={wake.enabled ?? false} onCheckedChange={(checked) => patchWake({ enabled: checked })} />
          </SettingRow>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-label block mb-1.5">时（0-23）</label>
              <Input
                type="number"
                min={0}
                max={23}
                value={wake.hour ?? 8}
                onChange={(e) => patchWake({ hour: Math.min(23, Math.max(0, Number(e.target.value) || 0)) })}
                className="input-field"
              />
            </div>
            <div>
              <label className="text-label block mb-1.5">分（0-59）</label>
              <Input
                type="number"
                min={0}
                max={59}
                value={wake.minute ?? 0}
                onChange={(e) => patchWake({ minute: Math.min(59, Math.max(0, Number(e.target.value) || 0)) })}
                className="input-field"
              />
            </div>
          </div>
          <SettingRow title="桌面通知" desc="唤醒时同时弹一条系统通知（浏览器通知权限）">
            <Switch checked={wake.notifyDesktop !== false} onCheckedChange={(checked) => patchWake({ notifyDesktop: checked })} />
          </SettingRow>
          <div className="flex items-center gap-2">
            <Button size="sm" variant="outline" onClick={requestNotifPermission}>
              <BellRing className="h-3.5 w-3.5 mr-1" /> 请求通知权限
            </Button>
            <span className={`text-xs ${notifPerm === "granted" ? "text-green-500" : notifPerm === "denied" ? "text-red-400" : "text-[var(--color-muted-foreground)]"}`}>
              {notifPerm === "granted" ? "已授权" : notifPerm === "denied" ? "已被拒绝（请在浏览器地址栏站点设置中允许）" : notifPerm === "unsupported" ? "当前环境不支持通知" : "未授权，点击左侧按钮申请"}
            </span>
          </div>

          <Separator />

          {/* 唤醒任务列表 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">唤醒任务</label>
              <Button size="sm" variant="ghost" onClick={() => setShowTaskForm((s) => !s)}>
                <Plus className="h-3.5 w-3.5 mr-1" /> 新增
              </Button>
            </div>
            <p className="text-[11px] text-[var(--color-muted-foreground)] opacity-70">
              多个定点唤醒计划（属于当前角色「{personas.find((p) => p.id === personaId)?.name ?? personaId}」），同样需页面保持打开。
            </p>
            {showTaskForm && (
              <div className="rounded-lg border border-[var(--color-border)] p-3 space-y-2">
                <Input
                  value={taskForm.name}
                  onChange={(e) => setTaskForm({ ...taskForm, name: e.target.value })}
                  placeholder="任务名称，如「早安问候」"
                  className="input-field"
                />
                <div className="grid grid-cols-2 gap-2">
                  <Input
                    type="time"
                    value={taskForm.time}
                    onChange={(e) => setTaskForm({ ...taskForm, time: e.target.value })}
                    className="input-field"
                  />
                  <Select
                    value={taskForm.repeat}
                    onChange={(v) => setTaskForm({ ...taskForm, repeat: v as WakeTaskRecord["repeat"] })}
                    options={[
                      { value: "daily", label: "每天" },
                      { value: "weekday", label: "仅工作日" },
                      { value: "once", label: "仅一次" },
                    ]}
                  />
                </div>
                <Input
                  value={taskForm.promptHint}
                  onChange={(e) => setTaskForm({ ...taskForm, promptHint: e.target.value })}
                  placeholder="唤醒引导语（可选），如「提醒我喝水」"
                  className="input-field"
                />
                <div className="flex justify-end">
                  <Button size="sm" className="btn-primary" onClick={addTask} disabled={!taskForm.name.trim()}>
                    保存任务
                  </Button>
                </div>
              </div>
            )}
            {tasks.length === 0 && !showTaskForm && (
              <p className="text-xs text-[var(--color-muted-foreground)] py-2">暂无唤醒任务</p>
            )}
            <div className="space-y-1.5">
              {tasks.map((t) => (
                <div key={t.id} className="flex items-center gap-2 rounded-lg bg-[var(--color-muted)]/20 px-3 py-2">
                  <span className="text-xs font-mono tabular-nums w-12 shrink-0">{t.time}</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-medium truncate">{t.name}</p>
                    <p className="text-[10px] text-[var(--color-muted-foreground)] truncate">
                      {REPEAT_LABELS[t.repeat]}{t.promptHint ? ` · ${t.promptHint}` : ""}
                    </p>
                  </div>
                  <Switch checked={t.enabled} onCheckedChange={(c) => void toggleTask(t.id, c)} />
                  <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => void removeTask(t.id)}>
                    <Trash2 className="h-3.5 w-3.5 text-[var(--color-muted-foreground)]" />
                  </Button>
                </div>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* ═══════ 数据管理 ═══════ */}
      <SectionTitle title="数据管理" desc="全量备份与迁移：聊天记录、日记、设定一站式打包" />
      <Card>
        <CardContent className="p-4 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium">导出快照</p>
              <p className="text-xs text-[var(--color-muted-foreground)]">下载 stradust-backup.json（含全部数据 + 设置）</p>
            </div>
            <Button size="sm" variant="outline" onClick={handleExport}>
              <Download className="h-3.5 w-3.5 mr-1" /> 导出
            </Button>
          </div>

          <Separator />

          <div className="space-y-2">
            <p className="text-sm font-medium">导入快照</p>
            <div className="flex gap-2">
              {([
                { value: "merge", label: "合并（按 id 去重更新）" },
                { value: "replace", label: "覆盖（先清空再写入）" },
              ] as const).map((m) => (
                <button
                  key={m.value}
                  onClick={() => setImportMode(m.value)}
                  className={`px-3 py-1.5 rounded-[var(--app-radius)] text-xs transition-colors ${
                    importMode === m.value
                      ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                      : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)]"
                  }`}
                >
                  {m.label}
                </button>
              ))}
            </div>
            <input
              ref={snapshotInputRef}
              type="file"
              accept="application/json,.json"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void handleImportSnapshot(f);
                e.target.value = "";
              }}
            />
            <Button size="sm" variant="outline" onClick={() => snapshotInputRef.current?.click()}>
              <Upload className="h-3.5 w-3.5 mr-1" /> 选择本应用导出的 JSON
            </Button>
          </div>

          <Separator />

          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium">导入 Android 备份</p>
              <p className="text-xs text-[var(--color-muted-foreground)]">
                解析安卓版导出的聊天记录 / 日记 JSON，归入当前角色；无法识别的字段自动跳过
              </p>
            </div>
            <input
              ref={androidInputRef}
              type="file"
              accept="application/json,.json"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void handleImportAndroid(f);
                e.target.value = "";
              }}
            />
            <Button size="sm" variant="outline" onClick={() => androidInputRef.current?.click()}>
              <Smartphone className="h-3.5 w-3.5 mr-1" /> 选择文件
            </Button>
          </div>

          {dataMsg && (
            <div
              className={`text-xs px-3 py-2 rounded-md ${
                dataMsg.kind === "ok" ? "bg-green-500/10 text-green-500" : "bg-red-500/10 text-red-400"
              }`}
            >
              {dataMsg.text}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
