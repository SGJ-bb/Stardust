import { create } from "zustand";
import type { Diary } from "@/types/diary";
import {
  getDiaryList, saveUserDiary, generateDailyDiary, updateDiaryContent, deleteDiaryById,
} from "@/engine/diary";
import { getActiveProvider } from "@/engine/llmClient";
import { on } from "@/lib/events";
import type { DiaryRecord } from "@/engine/kinds";

/** 引擎日记记录 → 页面 Diary（date 进 tags[0]，与门面 engineToPageDiary 一致） */
function recordToDiary(d: DiaryRecord): Diary {
  return {
    id: d.id, personaId: d.personaId, title: d.title, content: d.content,
    mood: d.mood, weather: "", attachments: [], tags: [d.date],
    createdAt: d.createdAt, updatedAt: d.updatedAt,
  };
}

interface DiaryState {
  /** 日记列表 */
  diaries: Diary[];
  /** 当前日记 */
  currentDiary: Diary | null;
  /** 是否正在加载 */
  isLoading: boolean;
  /** 最近一次操作错误（如 LLM 未配置） */
  error: string | null;

  /** 从引擎加载指定角色日记 */
  load: (personaId: string) => Promise<void>;
  /** 设置日记列表 */
  setDiaries: (diaries: Diary[]) => void;
  /** 设置当前日记 */
  setCurrentDiary: (diary: Diary | null) => void;
  /** 添加日记（用户手写，落库引擎） */
  addDiary: (personaId: string, title: string, content: string, mood: string) => Promise<void>;
  /** LLM 生成今日日记；未配置模型或失败时返回 false 并设置 error */
  generateDiary: (personaId: string) => Promise<boolean>;
  /** 删除日记 */
  deleteDiary: (diaryId: string) => Promise<void>;
  /** 更新日记内容 */
  updateDiary: (diaryId: string, content: string) => Promise<void>;
  /** 清除错误提示 */
  clearError: () => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

function todayStr(): string {
  const n = new Date();
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, "0")}-${String(n.getDate()).padStart(2, "0")}`;
}

export const useDiaryStore = create<DiaryState>()((set, get) => ({
  diaries: [],
  currentDiary: null,
  isLoading: false,
  error: null,

  load: async (personaId) => {
    if (!personaId) return;
    set({ isLoading: true });
    try {
      const list = await getDiaryList(personaId);
      set({ diaries: list.map(recordToDiary), isLoading: false });
    } catch {
      set({ isLoading: false });
    }
  },

  setDiaries: (diaries) => set({ diaries }),
  setCurrentDiary: (diary) => set({ currentDiary: diary }),

  addDiary: async (personaId, title, content, mood) => {
    try {
      // 用户在心情栏填写的内容作为标题兜底（引擎按文本自动分析情绪）
      await saveUserDiary(personaId, todayStr(), title || mood, content || mood || title);
      await get().load(personaId);
    } catch (e) {
      set({ error: `保存日记失败：${(e as Error).message}` });
    }
  },

  generateDiary: async (personaId) => {
    if (!getActiveProvider()) {
      set({ error: "尚未配置 AI 模型，无法生成日记。请先到「设置」中添加并启用模型提供商。" });
      return false;
    }
    set({ isLoading: true, error: null });
    try {
      const result = await generateDailyDiary(personaId, { force: true });
      if (!result) {
        set({ isLoading: false, error: "今天还没有可总结的聊天内容，生成失败。" });
        return false;
      }
      await get().load(personaId);
      set({ isLoading: false });
      return true;
    } catch (e) {
      set({ isLoading: false, error: `AI 生成日记失败：${(e as Error).message}` });
      return false;
    }
  },

  deleteDiary: async (diaryId) => {
    await deleteDiaryById(diaryId);
    set((state) => ({
      diaries: state.diaries.filter((d) => d.id !== diaryId),
      currentDiary: state.currentDiary?.id === diaryId ? null : state.currentDiary,
    }));
  },

  updateDiary: async (diaryId, content) => {
    const updated = await updateDiaryContent(diaryId, content);
    if (updated) {
      set((state) => ({
        diaries: state.diaries.map((d) => (d.id === diaryId ? recordToDiary(updated) : d)),
        currentDiary: state.currentDiary?.id === diaryId ? recordToDiary(updated) : state.currentDiary,
      }));
    }
  },

  clearError: () => set({ error: null }),
  setLoading: (loading) => set({ isLoading: loading }),
}));

// 引擎生成日记事件 → 自动刷新当前列表（页面卸载时也生效）
on<DiaryRecord>("diary-generated", () => {
  const d = useDiaryStore.getState();
  const pid = d.currentDiary?.personaId ?? d.diaries[0]?.personaId;
  if (pid) void d.load(pid);
});
