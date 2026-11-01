import { create } from "zustand";
import type { Achievement, CheckIn, Growth } from "@/types/achievement";
import { getAllAchievements, ACHIEVEMENT_CATALOG, unlockAchievement as engineUnlock } from "@/engine/achievements";
import { checkIn as engineCheckIn, getCheckInStatus } from "@/engine/checkin";
import { listMilestones } from "@/engine/milestones";
import * as tauri from "@/lib/tauri";
import { on } from "@/lib/events";
import type { AchievementRecord, MilestoneRecord } from "@/engine/kinds";

/** 引擎成就 → 页面 Achievement（category/rarity 按目录分类映射，target 取目录值；与门面一致） */
function recordToAchievement(r: AchievementRecord): Achievement {
  const def = ACHIEVEMENT_CATALOG.find((c) => c.key === r.key);
  const category = def?.category ?? "chat";
  const rarityMap: Record<string, Achievement["rarity"]> = {
    chat: "common", checkin: "rare", affection: "rare", memory: "epic", diary: "epic",
    hidden: "legendary", feedback: "rare", pomodoro: "epic",
  };
  const mappedCategory: Achievement["category"] =
    ["chat", "memory", "emotion", "social", "special"].includes(category)
      ? (category as Achievement["category"])
      : category === "checkin" || category === "affection" || category === "feedback" ? "social"
      : category === "diary" || category === "hidden" || category === "pomodoro" ? "special"
      : "memory";
  return {
    id: r.id, personaId: r.personaId, title: r.title, description: r.description, icon: r.icon,
    unlocked: r.unlocked, unlockedAt: r.unlockedAt,
    progress: Math.round(r.progress * (def?.target ?? 1)), // 引擎存 0~1，页面按 progress/target 显示
    target: def?.target ?? 1,
    category: mappedCategory,
    rarity: rarityMap[category] ?? "common",
  };
}

interface AchievementState {
  /** 成就列表 */
  achievements: Achievement[];
  /** 签到状态 */
  checkIn: CheckIn | null;
  /** 成长数据 */
  growth: Growth | null;
  /** 里程碑列表 */
  milestones: MilestoneRecord[];
  /** 是否正在加载 */
  isLoading: boolean;
  /** 最近一次解锁提示（页面弹 toast 后 clearNotice） */
  notice: string | null;
  /** 错误提示 */
  error: string | null;

  /** 从引擎加载成就/签到/成长/里程碑 */
  load: (personaId: string) => Promise<void>;
  /** 设置成就列表 */
  setAchievements: (achievements: Achievement[]) => void;
  /** 设置签到状态 */
  setCheckIn: (checkIn: CheckIn) => void;
  /** 设置成长数据 */
  setGrowth: (growth: Growth) => void;
  /** 解锁成就（走引擎落库） */
  unlockAchievement: (achievementId: string) => Promise<void>;
  /** 执行签到（真实落库：checkins + growth + achievements 联动） */
  doCheckIn: (personaId: string) => Promise<void>;
  /** 清除提示 */
  clearNotice: () => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useAchievementStore = create<AchievementState>()((set, get) => ({
  achievements: [],
  checkIn: null,
  growth: null,
  milestones: [],
  isLoading: false,
  notice: null,
  error: null,

  load: async (personaId) => {
    if (!personaId) return;
    set({ isLoading: true });
    try {
      const [records, ci, growth, milestones] = await Promise.all([
        getAllAchievements(personaId),
        getCheckInStatus(personaId),
        tauri.getGrowth(personaId),
        listMilestones(personaId),
      ]);
      set({
        achievements: records.map(recordToAchievement),
        checkIn: {
          personaId, streak: ci.streak, totalDays: ci.totalDays,
          lastCheckIn: ci.lastCheckIn ? new Date(`${ci.lastCheckIn}T12:00:00`).getTime() : 0,
          todayCheckedIn: ci.checkedInToday,
        },
        growth,
        milestones,
        isLoading: false,
      });
    } catch {
      set({ isLoading: false });
    }
  },

  setAchievements: (achievements) => set({ achievements }),
  setCheckIn: (checkIn) => set({ checkIn }),
  setGrowth: (growth) => set({ growth }),

  unlockAchievement: async (achievementId) => {
    // id 形如 `${personaId}:${key}`
    const idx = achievementId.indexOf(":");
    if (idx > 0) {
      const personaId = achievementId.slice(0, idx);
      const key = achievementId.slice(idx + 1);
      await engineUnlock(personaId, key);
      await get().load(personaId);
    }
  },

  doCheckIn: async (personaId) => {
    set({ error: null });
    try {
      const s = await engineCheckIn(personaId);
      set((state) => ({
        checkIn: state.checkIn
          ? { ...state.checkIn, streak: s.streak, totalDays: s.totalDays, lastCheckIn: Date.now(), todayCheckedIn: true }
          : { personaId, streak: s.streak, totalDays: s.totalDays, lastCheckIn: Date.now(), todayCheckedIn: true },
      }));
      // 签到会级联解锁成就与成长，重拉全部数据
      await get().load(personaId);
      set({ notice: `签到成功！连续 ${s.streak} 天，累计 ${s.totalDays} 天` });
    } catch (e) {
      set({ error: `签到失败：${(e as Error).message}` });
    }
  },

  clearNotice: () => set({ notice: null, error: null }),
  setLoading: (loading) => set({ isLoading: loading }),
}));

// 成就解锁事件 → 刷新列表并弹出提示
on<AchievementRecord>("achievement-unlocked", (rec) => {
  const pid = rec?.personaId;
  if (pid) void useAchievementStore.getState().load(pid);
  if (rec) {
    useAchievementStore.setState({ notice: `🏆 解锁成就「${rec.title}」${rec.icon}` });
  }
});
