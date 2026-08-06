import { create } from "zustand";
import { persist } from "zustand/middleware";

interface ActivationState {
  /** 是否已完成首次激活 */
  isFirstActivated: boolean;
  /** 是否已解锁高级功能 */
  isPremiumUnlocked: boolean;

  /** 首次激活（输入口令） */
  activateFirst: () => void;
  /** 解锁高级功能（输入密钥） */
  unlockPremium: (key: string) => boolean;
}

const PREMIUM_KEY = "1314520";

export const useActivationStore = create<ActivationState>()(
  persist(
    (set) => ({
      isFirstActivated: false,
      isPremiumUnlocked: false,

      activateFirst: () => set({ isFirstActivated: true }),

      unlockPremium: (key: string) => {
        if (key === PREMIUM_KEY) {
          set({ isPremiumUnlocked: true });
          return true;
        }
        return false;
      },
    }),
    { name: "stradust-activation" }
  )
);
