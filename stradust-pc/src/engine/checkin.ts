/**
 * 签到引擎 —— 移植自 gamify/CheckInManager.kt
 * 复刻规则：
 * - 重复签到防护：lastCheckIn === today 直接返回当前状态（Kotlin AlreadyCheckedIn）；
 * - 连击 streak：首次=1；昨天签过则 +1；断签重置为 1；
 * - totalDays 累计 +1；历史 history 最多保留 100 条、按日期去重倒序（Kotlin addRecord）；
 * - 连续签到每满 15 天触发 AI 发动态（shouldTriggerAiMoment，streak % 15 === 0）。
 *
 * 偏差点：
 * 1. Kotlin CheckInManager 本身不发放经验奖励（MainActivity 只做成就联动），
 *    此处按任务要求联动 growth.ts：每次签到 +5 exp，连签每满 7 天额外 +10 exp；
 * 2. Android 的签到是全局单例，PC 端按 personaId 独立存储。
 */

import { getOne, put } from "@/lib/db";
import type { CheckInState } from "./kinds";
import { emit } from "@/lib/events";
import { addExp } from "./growth";
import { checkAndUnlock } from "./achievements";

/** 每次签到基础经验（偏差 1，自定数值） */
const BASE_CHECKIN_EXP = 5;
/** 连签整周额外经验 */
const WEEKLY_BONUS_EXP = 10;

function todayStr(d: Date = new Date()): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function shiftDate(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  return todayStr(new Date(y, m - 1, d + days));
}

/** 读取签到状态（不落库） */
async function loadState(personaId: string): Promise<CheckInState> {
  const existing = await getOne<CheckInState>("checkins", personaId);
  return existing ?? {
    id: personaId,
    personaId,
    streak: 0,
    totalDays: 0,
    lastCheckIn: "",
    history: [],
  };
}

export interface CheckInStatus extends CheckInState {
  /** 今日是否已签到 */
  checkedInToday: boolean;
  /** 距离下次触发 AI 动态还差几天（Kotlin UI: 15 - streak % 15） */
  daysToNextAiMoment: number;
  /** 今天是否为 AI 动态触发日 */
  isTriggerDay: boolean;
}

/** 查询签到状态 */
export async function getCheckInStatus(personaId: string): Promise<CheckInStatus> {
  const s = await loadState(personaId);
  const today = todayStr();
  // 断签检测只影响展示中的 streak 连续性判断，不改写存储值（与 Kotlin 一致：checkIn 时才归 1）
  const activeStreak = s.lastCheckIn && s.lastCheckIn !== today && s.lastCheckIn !== shiftDate(today, -1) ? 0 : s.streak;
  return {
    ...s,
    streak: activeStreak,
    checkedInToday: s.lastCheckIn === today,
    daysToNextAiMoment: 15 - (activeStreak % 15),
    isTriggerDay: activeStreak > 0 && activeStreak % 15 === 0,
  };
}

/**
 * 执行签到。重复调用当日直接返回现有状态（不报错、不加次数）。
 * 成功时联动 growth.addExp 与 achievements.checkAndUnlock。
 */
export async function checkIn(personaId: string): Promise<CheckInState> {
  const today = todayStr();
  const s = await loadState(personaId);

  // 重复签到防护（Kotlin AlreadyCheckedIn）
  if (s.lastCheckIn === today) return s;

  // 连击计算（Kotlin when 分支照抄：昨天签过 → +1，否则重置 1）
  s.streak = !s.lastCheckIn ? 1 : s.lastCheckIn === shiftDate(today, -1) ? s.streak + 1 : 1;
  s.lastCheckIn = today;
  s.totalDays += 1;

  // 历史记录：去重同日、最新在前、封顶 100（Kotlin addRecord）
  const expGained = BASE_CHECKIN_EXP + (s.streak % 7 === 0 ? WEEKLY_BONUS_EXP : 0);
  s.history = [{ date: today, expGained }, ...s.history.filter((h) => h.date !== today)].slice(0, 100);

  await put("checkins", s);

  // 经验奖励联动 growth.ts（偏差 1）
  await addExp(personaId, expGained);
  // 成就联动（对齐 MainActivity: updateProgress("checkin", streak)）
  await checkAndUnlock(personaId, { streak: s.streak });

  emit("data-changed", { personaId, checkIn: s });
  // 连续签到 15 天触发 AI 发动态（Kotlin shouldTriggerAiMoment → UI 层监听此事件）
  if (s.streak > 0 && s.streak % 15 === 0) {
    emit("moment-generated", { personaId, reason: `checkin-streak-${s.streak}` });
  }
  return s;
}
