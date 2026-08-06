import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Achievement, CheckIn, Growth } from "@/types/achievement";
import { checkIn as checkInApi } from "@/lib/tauri";

interface AchievementState {
  /** 成就列表 */
  achievements: Achievement[];
  /** 签到状态 */
  checkIn: CheckIn | null;
  /** 成长数据 */
  growth: Growth | null;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置成就列表 */
  setAchievements: (achievements: Achievement[]) => void;
  /** 设置签到状态 */
  setCheckIn: (checkIn: CheckIn) => void;
  /** 设置成长数据 */
  setGrowth: (growth: Growth) => void;
  /** 解锁成就 */
  unlockAchievement: (achievementId: string) => void;
  /** 执行签到（需要personaId） */
  doCheckIn: (personaId: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useAchievementStore = create<AchievementState>()(
  persist(
    (set) => ({
      achievements: [],
      checkIn: null,
      growth: null,
      isLoading: false,

      setAchievements: (achievements) => set({ achievements }),
      setCheckIn: (checkIn) => set({ checkIn }),
      setGrowth: (growth) => set({ growth }),

      unlockAchievement: (achievementId) =>
        set((state) => ({
          achievements: state.achievements.map((a) =>
            a.id === achievementId
              ? { ...a, unlocked: true, unlockedAt: Date.now(), progress: a.target }
              : a
          ),
        })),

      doCheckIn: (personaId) =>
        set((state) => {
          if (!state.checkIn) return state;
          // 调用后端签到API
          checkInApi(personaId).catch((error) => {
            console.error("签到失败:", error);
          });
          return {
            checkIn: {
              ...state.checkIn,
              streak: state.checkIn.todayCheckedIn ? state.checkIn.streak : state.checkIn.streak + 1,
              totalDays: state.checkIn.todayCheckedIn ? state.checkIn.totalDays : state.checkIn.totalDays + 1,
              lastCheckIn: Date.now(),
              todayCheckedIn: true,
            },
          };
        }),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-achievement",
      partialize: (state) => ({
        achievements: state.achievements,
        checkIn: state.checkIn,
        growth: state.growth,
      }),
    }
  )
);
