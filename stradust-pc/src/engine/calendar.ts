/**
 * 日历引擎 —— 移植自 Android calendar/CalendarManager.kt（公历节日/农历节日表/节假日表/
 * 节气近似算法/生日纪念日）+ CalendarEventManager.kt（用户自定义事件，存 db "kv" 集合）。
 *
 * 农历说明（偏差点）：任务描述提到"1900-2100 表格式照抄"，但 Kotlin 源码中**并无**
 * 1900-2100 农历数据表——它用的是按年份硬编码的 festival→公历日期映射
 * （lunarDates: 仅 2025/2026/2027 三年），本文件忠实照搬该表；超出年份范围时农历节日
 * 无数据即不显示（与 Android 行为一致）。如需真正农历换算需另引数据表，此处不做臆造。
 */

import * as db from "@/lib/db";
import { emit } from "@/lib/events";
import { useSettingsStore } from "@/stores/useSettingsStore";
import type { CalendarRecord } from "./kinds";

// ═══════════════════════ 静态节日数据（照搬 CalendarManager.kt） ═══════════════════════

interface FestivalEvent {
  name: string;
  month: number;
  day: number;
  type: string;
  greeting: string;
  lunar?: boolean;
}

const SOLAR_FESTIVALS: FestivalEvent[] = [
  { name: "元旦", month: 1, day: 1, type: "公历节日", greeting: "新年快乐！新的一年，新的开始~" },
  { name: "情人节", month: 2, day: 14, type: "公历节日", greeting: "情人节快乐！今天有没有想对谁说点什么呀~" },
  { name: "妇女节", month: 3, day: 8, type: "公历节日", greeting: "妇女节快乐！向所有了不起的女性致敬~" },
  { name: "植树节", month: 3, day: 12, type: "公历节日", greeting: "植树节到了，一起为地球添点绿吧~" },
  { name: "愚人节", month: 4, day: 1, type: "公历节日", greeting: "愚人节快乐！今天可要小心别被骗了哦~" },
  { name: "劳动节", month: 5, day: 1, type: "公历节日", greeting: "劳动节快乐！辛苦了，今天好好休息吧~" },
  { name: "青年节", month: 5, day: 4, type: "公历节日", greeting: "青年节快乐！青春就是最好的礼物~" },
  { name: "儿童节", month: 6, day: 1, type: "公历节日", greeting: "儿童节快乐！愿你永远保持一颗童心~" },
  { name: "建党节", month: 7, day: 1, type: "公历节日", greeting: "七一建党节，铭记历史~" },
  { name: "建军节", month: 8, day: 1, type: "公历节日", greeting: "八一建军节，向最可爱的人致敬~" },
  { name: "教师节", month: 9, day: 10, type: "公历节日", greeting: "教师节快乐！感恩每一位辛勤付出的老师~" },
  { name: "国庆节", month: 10, day: 1, type: "公历节日", greeting: "国庆节快乐！祝祖国繁荣昌盛~" },
  { name: "万圣节", month: 10, day: 31, type: "公历节日", greeting: "万圣节快乐！不给糖就捣蛋~" },
  { name: "光棍节", month: 11, day: 11, type: "公历节日", greeting: "双十一快乐！是买买买还是享受单身呢~" },
  { name: "圣诞节", month: 12, day: 25, type: "公历节日", greeting: "圣诞快乐！铃儿响叮当~" },
];

const LUNAR_FESTIVAL_NAMES: FestivalEvent[] = [
  { name: "春节", month: 0, day: 0, type: "农历节日", greeting: "新年快乐！恭喜发财，万事如意~", lunar: true },
  { name: "元宵节", month: 0, day: 0, type: "农历节日", greeting: "元宵节快乐！吃汤圆，团团圆圆~", lunar: true },
  { name: "龙抬头", month: 0, day: 0, type: "农历节日", greeting: "龙抬头好日子，鸿运当头~", lunar: true },
  { name: "端午节", month: 0, day: 0, type: "农历节日", greeting: "端午安康！吃粽子了吗~", lunar: true },
  { name: "七夕节", month: 0, day: 0, type: "农历节日", greeting: "七夕快乐！愿有情人终成眷属~", lunar: true },
  { name: "中秋节", month: 0, day: 0, type: "农历节日", greeting: "中秋快乐！月圆人团圆~", lunar: true },
  { name: "重阳节", month: 0, day: 0, type: "农历节日", greeting: "重阳节快乐！登高望远，敬老爱老~", lunar: true },
  { name: "腊八节", month: 0, day: 0, type: "农历节日", greeting: "腊八节快乐！喝了腊八粥就是年~", lunar: true },
  { name: "除夕", month: 0, day: 0, type: "农历节日", greeting: "除夕快乐！辞旧迎新，阖家团圆~", lunar: true },
];

/** 农历节日 → 公历日期表（year → name → [month, day]），逐值照搬 lunarDates */
const LUNAR_DATES: Record<number, Record<string, [number, number]>> = {
  2025: {
    春节: [1, 29], 元宵节: [2, 12], 龙抬头: [2, 28],
    端午节: [5, 31], 七夕节: [8, 29], 中秋节: [10, 6],
    重阳节: [10, 29], 腊八节: [12, 28], 除夕: [1, 28],
  },
  2026: {
    春节: [2, 17], 元宵节: [3, 3], 龙抬头: [3, 19],
    端午节: [6, 19], 七夕节: [8, 27], 中秋节: [10, 4],
    重阳节: [10, 27], 腊八节: [12, 17], 除夕: [2, 16],
  },
  2027: {
    春节: [2, 6], 元宵节: [2, 20], 龙抬头: [3, 8],
    端午节: [6, 9], 七夕节: [8, 16], 中秋节: [9, 23],
    重阳节: [10, 16], 腊八节: [12, 7], 除夕: [2, 5],
  },
};

interface HolidayDay { month: number; day: number; label: string; isWorkday?: boolean }

/** 法定节假日/调休表（逐值照搬 holidays） */
const HOLIDAYS: Record<number, HolidayDay[]> = {
  2025: [
    { month: 1, day: 1, label: "元旦假期" }, { month: 1, day: 28, label: "春节假期" }, { month: 1, day: 29, label: "春节假期" },
    { month: 1, day: 30, label: "春节假期" }, { month: 1, day: 31, label: "春节假期" }, { month: 2, day: 1, label: "春节假期" },
    { month: 2, day: 2, label: "春节假期" }, { month: 2, day: 3, label: "春节假期" }, { month: 2, day: 4, label: "春节假期" },
    { month: 1, day: 26, label: "调休上班", isWorkday: true }, { month: 2, day: 8, label: "调休上班", isWorkday: true },
    { month: 4, day: 4, label: "清明假期" }, { month: 4, day: 5, label: "清明假期" }, { month: 4, day: 6, label: "清明假期" },
    { month: 5, day: 1, label: "劳动节假期" }, { month: 5, day: 2, label: "劳动节假期" }, { month: 5, day: 3, label: "劳动节假期" },
    { month: 5, day: 4, label: "劳动节假期" }, { month: 5, day: 5, label: "劳动节假期" },
    { month: 4, day: 27, label: "调休上班", isWorkday: true },
    { month: 5, day: 31, label: "端午假期" }, { month: 6, day: 1, label: "端午假期" }, { month: 6, day: 2, label: "端午假期" },
    { month: 10, day: 1, label: "国庆假期" }, { month: 10, day: 2, label: "国庆假期" }, { month: 10, day: 3, label: "国庆假期" },
    { month: 10, day: 4, label: "国庆假期" }, { month: 10, day: 5, label: "国庆假期" }, { month: 10, day: 6, label: "中秋假期" },
    { month: 10, day: 7, label: "国庆假期" }, { month: 10, day: 8, label: "国庆假期" },
    { month: 9, day: 28, label: "调休上班", isWorkday: true }, { month: 10, day: 11, label: "调休上班", isWorkday: true },
  ],
  2026: [
    { month: 1, day: 1, label: "元旦假期" }, { month: 1, day: 2, label: "元旦假期" }, { month: 1, day: 3, label: "元旦假期" },
    { month: 2, day: 16, label: "除夕" }, { month: 2, day: 17, label: "春节假期" }, { month: 2, day: 18, label: "春节假期" },
    { month: 2, day: 19, label: "春节假期" }, { month: 2, day: 20, label: "春节假期" }, { month: 2, day: 21, label: "春节假期" },
    { month: 2, day: 22, label: "春节假期" }, { month: 2, day: 23, label: "春节假期" },
    { month: 6, day: 19, label: "端午假期" }, { month: 6, day: 20, label: "端午假期" }, { month: 6, day: 21, label: "端午假期" },
    { month: 10, day: 1, label: "国庆假期" }, { month: 10, day: 2, label: "国庆假期" }, { month: 10, day: 3, label: "国庆假期" },
    { month: 10, day: 4, label: "中秋假期" }, { month: 10, day: 5, label: "国庆假期" }, { month: 10, day: 6, label: "国庆假期" },
    { month: 10, day: 7, label: "国庆假期" }, { month: 10, day: 8, label: "国庆假期" },
  ],
};

export function getHolidayForDate(year: number, month: number, day: number): HolidayDay | null {
  return HOLIDAYS[year]?.find((h) => h.month === month && h.day === day) ?? null;
}

export function isHoliday(year: number, month: number, day: number): boolean {
  const h = getHolidayForDate(year, month, day);
  return !!h && !h.isWorkday;
}

export function isWorkdayAdjust(year: number, month: number, day: number): boolean {
  return getHolidayForDate(year, month, day)?.isWorkday === true;
}

// ─── 二十四节气（近似偏移法，逐值照搬 solarTermOffsets/greetings） ───

const SOLAR_TERM_OFFSETS: Array<[string, [number, number]]> = [
  ["小寒", [5, 20]], ["大寒", [20, 20]],
  ["立春", [3, 4]], ["雨水", [18, 19]],
  ["惊蛰", [5, 6]], ["春分", [20, 21]],
  ["清明", [4, 5]], ["谷雨", [20, 20]],
  ["立夏", [5, 6]], ["小满", [21, 21]],
  ["芒种", [5, 6]], ["夏至", [21, 21]],
  ["小暑", [7, 7]], ["大暑", [22, 23]],
  ["立秋", [7, 8]], ["处暑", [23, 23]],
  ["白露", [7, 8]], ["秋分", [23, 23]],
  ["寒露", [8, 8]], ["霜降", [23, 24]],
  ["立冬", [7, 8]], ["小雪", [22, 22]],
  ["大雪", [7, 7]], ["冬至", [21, 22]],
];

const SOLAR_TERM_GREETINGS: Record<string, string> = {
  小寒: "小寒到了，天冷记得加衣~", 大寒: "大寒是一年中最冷的时候，注意保暖~",
  立春: "立春了！春天要来了，万物复苏~", 雨水: "雨水节气，春雨贵如油~",
  惊蛰: "惊蛰到了，春雷响万物长~", 春分: "春分昼夜平分，春意正浓~",
  清明: "清明时节雨纷纷~", 谷雨: "谷雨到了，春播好时节~",
  立夏: "立夏了！夏天正式开始~", 小满: "小满节气，麦粒渐满~",
  芒种: "芒种忙忙种，农事正当时~", 夏至: "夏至日最长，盛夏来了~",
  小暑: "小暑天气热，注意防暑~", 大暑: "大暑是一年中最热的时候~",
  立秋: "立秋了！秋天要来了~", 处暑: "处暑暑渐消，秋凉将至~",
  白露: "白露秋分夜，一夜凉一夜~", 秋分: "秋分昼夜平分，秋意渐浓~",
  寒露: "寒露时节，秋深露重~", 霜降: "霜降到了，天气渐冷~",
  立冬: "立冬了！冬天正式开始~", 小雪: "小雪节气，初雪将至~",
  大雪: "大雪纷飞，银装素裹~", 冬至: "冬至大如年，记得吃饺子/汤圆~",
};

/** 指定日期的节气名（照搬 Kotlin 索引推月 + year%4 选闰/平常偏移的近似算法） */
export function getSolarTerm(date: Date): string | null {
  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  const day = date.getDate();
  for (let i = 0; i < SOLAR_TERM_OFFSETS.length; i++) {
    const [name, offsets] = SOLAR_TERM_OFFSETS[i];
    const d = offsets[year % 4 === 0 ? 1 : 0];
    const m = Math.floor(i / 2) + 1;
    if (m === month && d === day) return name;
  }
  return null;
}

// ═══════════════════════ 今日事件计算 ═══════════════════════

function fmtDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** 今日的节日/节气文案列表（照搬 getTodayEvents） */
export function getTodayEvents(personaBirthday?: string): string[] {
  const now = new Date();
  const month = now.getMonth() + 1;
  const day = now.getDate();
  const year = now.getFullYear();
  const results: string[] = [];

  for (const f of SOLAR_FESTIVALS) {
    if (f.month === month && f.day === day) results.push(`【${f.type}】${f.name}：${f.greeting}`);
  }
  const dates = LUNAR_DATES[year];
  if (dates) {
    for (const f of LUNAR_FESTIVAL_NAMES) {
      const dp = dates[f.name];
      if (dp && dp[0] === month && dp[1] === day) results.push(`【${f.type}】${f.name}：${f.greeting}`);
    }
  }
  const term = getSolarTerm(now);
  if (term) results.push(`【节气】${term}：${SOLAR_TERM_GREETINGS[term]}`);

  // 生日祝福并入今日事件（对应 Kotlin getCalendarContextBlock 的【生日】行）
  if (personaBirthday) {
    const g = getBirthdayGreeting(personaBirthday);
    if (g) results.push(`【生日】${g}`);
  }
  return results;
}

/** 是否今天生日（birthday 形如 yyyy-MM-dd，照搬 isBirthdayToday 的 split 比较） */
export function isBirthdayToday(birthday: string, date = new Date()): boolean {
  if (!birthday?.trim()) return false;
  const parts = birthday.split("-");
  if (parts.length < 3) return false;
  return parseInt(parts[1], 10) === date.getMonth() + 1 && parseInt(parts[2], 10) === date.getDate();
}

/** 生日祝福（含周岁，照搬 getBirthdayGreeting） */
export function getBirthdayGreeting(birthday: string): string | null {
  if (!isBirthdayToday(birthday)) return null;
  const birthYear = parseInt(birthday.split("-")[0], 10);
  if (Number.isNaN(birthYear)) return "生日快乐！今天是你特别的日子~";
  const age = new Date().getFullYear() - birthYear;
  return `生日快乐！今天是你${age}岁的生日，愿你新的一岁一切顺利~`;
}

// ═══════════════════════ 用户事件 CRUD（CalendarEventManager 等价，存 kv 集合 key="calendar-events"） ═══════════════════════

const EVENTS_KEY = "calendar-events";

async function loadEvents(): Promise<CalendarRecord[]> {
  return (await db.kvGet<CalendarRecord[]>(EVENTS_KEY)) ?? [];
}

async function saveEvents(items: CalendarRecord[]): Promise<void> {
  await db.kvSet(EVENTS_KEY, items);
  emit("data-changed");
}

export interface AddEventInput {
  date: string; // yyyy-MM-dd
  type?: CalendarRecord["type"];
  title: string;
  recurring?: boolean;
  lunar?: boolean;
  personaId?: string;
}

export async function addEvent(input: AddEventInput): Promise<CalendarRecord> {
  const rec: CalendarRecord = {
    id: db.newId("evt"),
    personaId: input.personaId,
    date: input.date,
    type: input.type ?? "user",
    title: input.title,
    recurring: input.recurring ?? false,
    lunar: input.lunar ?? false,
    createdAt: Date.now(),
  };
  const items = await loadEvents();
  items.push(rec);
  await saveEvents(items);
  return rec;
}

export async function deleteEvent(id: string): Promise<void> {
  const items = await loadEvents();
  await saveEvents(items.filter((e) => e.id !== id));
}

/** 全部事件（可按角色过滤；系统节日通过 getTodayEvents/getUpcomingEvents 合并） */
export async function listEvents(personaId?: string): Promise<CalendarRecord[]> {
  const items = await loadEvents();
  const filtered = personaId ? items.filter((e) => !e.personaId || e.personaId === personaId) : items;
  return filtered.sort((a, b) => a.date.localeCompare(b.date));
}

/** 某日的循环事件展开（生日/纪念日 recurring=true 按 MM-DD 匹配任意年份） */
function recurringOn(e: CalendarRecord, date: Date): boolean {
  if (!e.recurring) return false;
  const [, m, d] = e.date.split("-");
  return parseInt(m, 10) === date.getMonth() + 1 && parseInt(d, 10) === date.getDate();
}

/** 今日事件（用户事件 + 系统节日/节气 + 用户画像生日；返回结构化条目便于 UI） */
export async function getTodayEventsFull(): Promise<Array<{ title: string; detail?: string; kind: "user" | "system" }>> {
  const today = new Date();
  const todayStr = fmtDate(today);
  const out: Array<{ title: string; detail?: string; kind: "user" | "system" }> = [];
  const events = await loadEvents();
  for (const e of events) {
    if (e.date === todayStr || recurringOn(e, today)) {
      out.push({ title: e.title, detail: e.type, kind: "user" });
    }
  }
  const userBirthday = useSettingsStore.getState().settings.userProfile?.birthday;
  for (const line of getTodayEvents(userBirthday)) {
    out.push({ title: line, kind: "system" });
  }
  return out;
}

/** 未来 N 天内的事件（含循环展开，不含系统节日批量枚举——UI 需要时用 getTodayEvents） */
export async function getUpcomingEvents(days: number, personaId?: string): Promise<CalendarRecord[]> {
  const items = await loadEvents();
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start.getTime() + days * 24 * 3600 * 1000);
  const result: Array<CalendarRecord & { _sortDate: string }> = [];
  for (const e of items) {
    if (personaId && e.personaId && e.personaId !== personaId) continue;
    if (e.recurring) {
      // 循环事件：找窗口内下一次发生日期
      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        if (recurringOn(e, d)) {
          result.push({ ...e, _sortDate: fmtDate(d) });
          break;
        }
      }
    } else {
      const t = new Date(e.date).getTime();
      if (t >= start.getTime() && t <= end.getTime()) result.push({ ...e, _sortDate: e.date });
    }
  }
  return result.sort((a, b) => a._sortDate.localeCompare(b._sortDate)).map(({ _sortDate, ...rest }) => rest);
}
