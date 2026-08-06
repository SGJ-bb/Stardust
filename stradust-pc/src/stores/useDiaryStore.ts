import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Diary } from "@/types/diary";
import { generateId } from "@/lib/utils";

interface DiaryState {
  /** 日记列表 */
  diaries: Diary[];
  /** 当前日记 */
  currentDiary: Diary | null;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置日记列表 */
  setDiaries: (diaries: Diary[]) => void;
  /** 设置当前日记 */
  setCurrentDiary: (diary: Diary | null) => void;
  /** 添加日记 */
  addDiary: (personaId: string, title: string, content: string, mood: string) => void;
  /** 删除日记 */
  deleteDiary: (diaryId: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useDiaryStore = create<DiaryState>()(
  persist(
    (set) => ({
      diaries: [],
      currentDiary: null,
      isLoading: false,

      setDiaries: (diaries) => set({ diaries }),
      setCurrentDiary: (diary) => set({ currentDiary: diary }),

      addDiary: (personaId, title, content, mood) => {
        const diary: Diary = {
          id: generateId(),
          personaId,
          title,
          content,
          mood,
          weather: "",
          attachments: [],
          tags: [],
          createdAt: Date.now(),
          updatedAt: Date.now(),
        };
        set((state) => ({ diaries: [diary, ...state.diaries] }));
      },

      deleteDiary: (diaryId) =>
        set((state) => ({
          diaries: state.diaries.filter((d) => d.id !== diaryId),
          currentDiary: state.currentDiary?.id === diaryId ? null : state.currentDiary,
        })),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-diary",
      partialize: (state) => ({
        diaries: state.diaries,
        currentDiary: state.currentDiary,
      }),
    }
  )
);
