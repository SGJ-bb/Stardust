/**
 * 闹钟 / 唤醒任务 / 日程 —— 移植自 Android wakeup/WakeUpScheduler.kt +
 * wakeup/WakeUpTaskManager.kt + action/AIActionManager.kt（闹钟/日程部分）。
 *
 * Web 等价替代说明：
 * - Android 用 AlarmManager.setExactAndAllowWhileIdle + BroadcastReceiver 在系统级精确唤醒；
 *   Web 无该能力，改为**页面内 tick 调度**（setInterval 每分钟检查一次触发条件），
 *   页面关闭期间不会触发（与 WakeUpScheduler 的差异点，注释于 startWakeScheduler）。
 * - 通知：Tauri 下动态 import @tauri-apps/plugin-notification；浏览器下用 Notification API，
 *   需要权限时 requestPermission，全部失败静默。
 * - AI 语音提醒回调（对应 WakeUpReceiver 里 sendProactiveChat 生成唤醒语再通知）通过
 *   setAlarmHandler 由上层注入。
 */

import * as db from "@/lib/db";
import { isTauri } from "@/lib/tauri";
import { emit } from "@/lib/events";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { AlarmRecord, WakeTaskRecord, ScheduleItem } from "./kinds";

// ═══════════════════════ 通知 ═══════════════════════

/** 发送系统通知（Tauri plugin / 浏览器 Notification 双路，失败静默） */
export async function notify(title: string, body: string): Promise<void> {
  if (!useSettingsStore.getState().settings.notificationEnabled) return;
  try {
    if (isTauri()) {
      const mod = await import("@tauri-apps/plugin-notification");
      let granted = await mod.isPermissionGranted();
      if (!granted) {
        granted = (await mod.requestPermission()) === "granted";
      }
      if (granted) mod.sendNotification({ title, body });
      return;
    }
  } catch {
    // Tauri API 不可用时继续尝试浏览器路径
  }
  try {
    if (typeof Notification === "undefined") return;
    let perm = Notification.permission;
    if (perm === "default") perm = await Notification.requestPermission();
    if (perm === "granted") {
      void new Notification(title, { body });
    }
  } catch {
    /* 静默 */
  }
}

// ═══════════════════════ 闹钟（AIActionManager.setAlarm + AlarmActivity 管理面） ═══════════════════════

export async function listAlarms(): Promise<AlarmRecord[]> {
  const all = await db.getAll<AlarmRecord>("alarms");
  return all.sort((a, b) => a.hour * 60 + a.minute - (b.hour * 60 + b.minute));
}

/** 新增闹钟（repeatDays: 0-6，空数组 = 每天，对齐 Kotlin setAlarm 的每日语义） */
export async function addAlarm(input: { hour: number; minute: number; label?: string; repeatDays?: number[]; personaId?: string }): Promise<AlarmRecord> {
  const rec: AlarmRecord = {
    id: db.newId("alarm"),
    personaId: input.personaId,
    hour: Math.min(23, Math.max(0, input.hour)),
    minute: Math.min(59, Math.max(0, input.minute)),
    label: input.label?.trim() || "闹钟",
    repeatDays: input.repeatDays ?? [],
    enabled: true,
    createdAt: Date.now(),
  };
  await db.put("alarms", rec);
  emit("data-changed");
  return rec;
}

export async function deleteAlarm(id: string): Promise<void> {
  await db.remove("alarms", id);
  emit("data-changed");
}

export async function toggleAlarm(id: string, enabled: boolean): Promise<void> {
  const rec = await db.getOne<AlarmRecord>("alarms", id);
  if (!rec) return;
  await db.put("alarms", { ...rec, enabled });
  emit("data-changed");
}

/** 供 AlarmPlugin（AI 工具 setAlarm）使用的能力：设置一个闹钟并返回确认文案（照搬 Kotlin 返回值风格） */
export async function setAlarmCapability(hour: number, minute: number, label = "闹钟"): Promise<string> {
  await addAlarm({ hour, minute, label });
  const timeStr = `${hour}:${String(minute).padStart(2, "0")}`;
  return `闹钟已设置：${label}将在 ${timeStr} 触发`;
}

// ─── 闹钟调度器 ───

let alarmTimer: ReturnType<typeof setInterval> | null = null;
/** 已触发分钟去重：key = alarmId + yyyy-MM-dd-HH-mm */
const firedAlarmKeys = new Set<string>();

type AlarmHandler = (alarm: AlarmRecord) => void | Promise<void>;
let alarmHandler: AlarmHandler | null = null;

/** 注入闹钟触发回调（上层做 AI 语音提醒/全屏闹铃页，对应 AlarmActivity 全屏 Intent） */
export function setAlarmHandler(fn: AlarmHandler | null): void {
  alarmHandler = fn;
}

function pad2(n: number): string { return String(n).padStart(2, "0"); }

async function alarmTick(): Promise<void> {
  const now = new Date();
  const hmKey = `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}-${pad2(now.getHours())}-${pad2(now.getMinutes())}`;
  const alarms = await db.getAll<AlarmRecord>("alarms");
  for (const a of alarms) {
    if (!a.enabled) continue;
    if (a.hour !== now.getHours() || a.minute !== now.getMinutes()) continue;
    if (a.repeatDays.length && !a.repeatDays.includes(now.getDay())) continue;
    const key = `${a.id}@${hmKey}`;
    if (firedAlarmKeys.has(key)) continue;
    firedAlarmKeys.add(key);
    // 照搬 ReminderReceiver：标题带 ⏰、正文为"HH:mm 的闹钟响了！"
    await notify(`⏰ ${a.label}`, `${a.hour}：${pad2(a.minute)} 的闹钟响了！`);
    emit("alarm-fired", a);
    try {
      await alarmHandler?.(a);
    } catch { /* handler 异常不影响调度 */ }
  }
  // 集合防泄漏：只保留当天的 key
  if (firedAlarmKeys.size > 200) {
    const keep = hmKey.slice(0, 10);
    for (const k of Array.from(firedAlarmKeys)) {
      if (!k.includes(keep)) firedAlarmKeys.delete(k);
    }
  }
}

/** 启动闹钟调度（每 20s tick，覆盖分钟边界；页面内调度，Web 等价实现） */
export function startAlarmScheduler(): void {
  if (alarmTimer !== null) return; // module 级去重，防 HMR 泄漏
  alarmTimer = setInterval(() => { void alarmTick(); }, 20000);
  void alarmTick();
}

export function stopAlarmScheduler(): void {
  if (alarmTimer !== null) {
    clearInterval(alarmTimer);
    alarmTimer = null;
  }
}

// ═══════════════════════ 唤醒任务（WakeUpTaskManager + WakeUpScheduler） ═══════════════════════

/**
 * 与 Android 的差异点（注释说明）：
 * - WakeUpTaskManager.scheduleTask 用 AlarmManager 精确闹钟 + 开机重排（BootReceiver），
 *   杀后台/锁屏仍可触发；Web 只能页面存活时轮询，错过时刻在当天内不补触发。
 * - WakeUpScheduler 的全局唤醒时间来自 settings.wake{enabled,hour,minute}，此处并入 tick。
 */

export async function listWakeTasks(personaId: string): Promise<WakeTaskRecord[]> {
  return db.getByPersona<WakeTaskRecord>("wakeTasks", personaId);
}

export async function addWakeTask(input: {
  personaId: string;
  name: string;
  time: string; // HH:mm
  repeat?: WakeTaskRecord["repeat"];
  promptHint?: string;
}): Promise<WakeTaskRecord> {
  const [h, m] = input.time.split(":").map((x) => parseInt(x, 10));
  const rec: WakeTaskRecord = {
    id: db.newId("wake"),
    personaId: input.personaId,
    name: input.name.trim() || "唤醒任务",
    time: /^\d{1,2}:\d{2}$/.test(input.time) ? input.time : "09:00",
    repeat: input.repeat ?? "daily",
    promptHint: input.promptHint ?? "",
    enabled: true,
    nextRunAt: computeNextRun(h, m, input.repeat ?? "daily"),
  };
  await db.put("wakeTasks", rec);
  emit("data-changed");
  return rec;
}

export async function deleteWakeTask(id: string): Promise<void> {
  await db.remove("wakeTasks", id);
  emit("data-changed");
}

export async function toggleWakeTask(id: string, enabled: boolean): Promise<void> {
  const rec = await db.getOne<WakeTaskRecord>("wakeTasks", id);
  if (!rec) return;
  await db.put("wakeTasks", { ...rec, enabled });
  emit("data-changed");
}

/** 下一次触发时间戳（照搬 Calendar 逻辑：今天过了就 +1 天；weekday 跳过周末） */
function computeNextRun(hour: number, minute: number, repeat: WakeTaskRecord["repeat"]): number {
  const now = new Date();
  const d = new Date();
  d.setHours(hour, minute, 0, 0);
  if (d.getTime() <= now.getTime()) d.setDate(d.getDate() + 1);
  if (repeat === "weekday") {
    while (d.getDay() === 0 || d.getDay() === 6) d.setDate(d.getDate() + 1);
  }
  return d.getTime();
}

let wakeTimer: ReturnType<typeof setInterval> | null = null;
const firedWakeKeys = new Set<string>();

/** 分时段问候兜底（照搬 WakeUpReceiver.showWakeUpNotification 的时间分支） */
function defaultGreeting(hour: number): string {
  if (hour < 6) return "夜深了，星尘还在等你...";
  if (hour < 12) return "早上好！星尘想和你说说话~";
  if (hour < 18) return "下午好呀！今天过得怎么样？";
  return "晚上好，今天辛苦啦~";
}

async function wakeTick(): Promise<void> {
  const now = new Date();
  const dayKey = `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
  const hmKey = `${dayKey}-${pad2(now.getHours())}-${pad2(now.getMinutes())}`;

  // 1) settings.wake 全局唤醒（WakeUpScheduler 等价）
  const wake = useSettingsStore.getState().settings.wake;
  if (wake?.enabled && wake.hour === now.getHours() && wake.minute === now.getMinutes()) {
    const key = `global@${hmKey}`;
    if (!firedWakeKeys.has(key)) {
      firedWakeKeys.add(key);
      emit("wake-fired", { id: "global-wake", name: "定时唤醒", time: `${pad2(wake.hour)}:${pad2(wake.minute)}`, promptHint: defaultGreeting(now.getHours()) } as unknown as WakeTaskRecord);
      if (wake.notifyDesktop !== false) {
        await notify("星尘 唤醒", defaultGreeting(now.getHours()));
      }
    }
  }

  // 2) 唤醒任务表
  const tasks = await db.getAll<WakeTaskRecord>("wakeTasks");
  for (const t of tasks) {
    if (!t.enabled) continue;
    const [h, m] = t.time.split(":").map((x) => parseInt(x, 10));
    if (Number.isNaN(h) || Number.isNaN(m)) continue;
    if (h !== now.getHours() || m !== now.getMinutes()) continue;
    if (t.repeat === "weekday" && (now.getDay() === 0 || now.getDay() === 6)) continue;
    const key = `${t.id}@${hmKey}`;
    if (firedWakeKeys.has(key)) continue;
    firedWakeKeys.add(key);
    const updated: WakeTaskRecord = { ...t, lastRunAt: Date.now() };
    if (t.repeat !== "once") {
      updated.nextRunAt = computeNextRun(h, m, t.repeat);
    } else {
      updated.enabled = false;
    }
    await db.put("wakeTasks", updated);
    emit("wake-fired", updated);
    // 照搬 showTaskNotification：标题"AI名：任务名"，正文描述
    await notify(`${t.name}`, t.promptHint || defaultGreeting(now.getHours()));
  }

  if (firedWakeKeys.size > 200) {
    for (const k of Array.from(firedWakeKeys)) {
      if (!k.includes(dayKey)) firedWakeKeys.delete(k);
    }
  }
}

/** 启动唤醒任务调度（20s tick；页面内调度为 Web 等价实现，见上方差异说明） */
export function startWakeScheduler(): void {
  if (wakeTimer !== null) return;
  wakeTimer = setInterval(() => { void wakeTick(); }, 20000);
  void wakeTick();
}

export function stopWakeScheduler(): void {
  if (wakeTimer !== null) {
    clearInterval(wakeTimer);
    wakeTimer = null;
  }
}

// ═══════════════════════ 日程（AIActionManager.addSchedule/getAllSchedules） ═══════════════════════

/** 日程存 kv 集合 key="schedules"（任务要求） */
const SCHEDULES_KEY = "schedules";

async function loadSchedules(): Promise<ScheduleItem[]> {
  return (await db.kvGet<ScheduleItem[]>(SCHEDULES_KEY)) ?? [];
}

async function saveSchedules(items: ScheduleItem[]): Promise<void> {
  await db.kvSet(SCHEDULES_KEY, items);
  emit("data-changed");
}

export async function addSchedule(personaId: string, timeIso: string, content: string): Promise<ScheduleItem> {
  const item: ScheduleItem = {
    id: db.newId("sched"),
    personaId,
    time: new Date(timeIso).getTime() || Date.now(),
    content,
    done: false,
    createdAt: Date.now(),
  };
  const items = await loadSchedules();
  items.push(item);
  await saveSchedules(items);
  return item;
}

export async function listSchedules(personaId: string, includeDone = false): Promise<ScheduleItem[]> {
  const items = await loadSchedules();
  return items
    .filter((s) => s.personaId === personaId && (includeDone || !s.done))
    .sort((a, b) => a.time - b.time);
}

export async function completeSchedule(id: string): Promise<void> {
  const items = await loadSchedules();
  const it = items.find((s) => s.id === id);
  if (!it) return;
  it.done = true;
  await saveSchedules(items);
}

// ─── 日程到点提醒调度（对应 ACTION_SCHEDULE_TRIGGER → 通知） ───

let scheduleTimer: ReturnType<typeof setInterval> | null = null;
const notifiedSchedules = new Set<string>();

async function scheduleTick(): Promise<void> {
  const now = Date.now();
  const items = await loadSchedules();
  for (const s of items) {
    if (s.done || notifiedSchedules.has(s.id)) continue;
    if (s.time <= now && now - s.time < 10 * 60 * 1000) {
      notifiedSchedules.add(s.id);
      await notify("📅 日程提醒", s.content);
      emit("wake-fired", { id: s.id, name: "日程", promptHint: s.content } as unknown as WakeTaskRecord);
    }
  }
}

export function startScheduleScheduler(): void {
  if (scheduleTimer !== null) return;
  scheduleTimer = setInterval(() => { void scheduleTick(); }, 30000);
}

export function stopScheduleScheduler(): void {
  if (scheduleTimer !== null) {
    clearInterval(scheduleTimer);
    scheduleTimer = null;
  }
}
