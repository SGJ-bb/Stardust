import { create } from "zustand";
import type { Moment, Comment } from "@/types/moments";
import { useSettingsStore } from "@/stores/useSettingsStore";
import {
  listMoments, publishUserMoment, toggleLike, userCommentOnMoment, generateAiMoment, deleteMoment as engineDeleteMoment,
} from "@/engine/momentsFeed";
import { getActiveProvider } from "@/engine/llmClient";
import { on } from "@/lib/events";
import type { MomentRecord } from "@/engine/kinds";

function userName(): string {
  return useSettingsStore.getState().settings.userProfile?.nickname?.trim() || "我";
}

/** 引擎动态记录 → 页面 Moment（与门面 engineToPageMoment 一致） */
function recordToMoment(m: MomentRecord): Moment {
  const me = userName();
  return {
    id: m.id, personaId: m.personaId, content: m.content, images: m.images,
    mood: m.source === "ai" ? "ai" : "user",
    likes: m.likes.length, liked: m.likes.includes(me) || m.likes.includes(m.author),
    comments: m.comments.map((c) => ({
      id: c.id, momentId: m.id, authorId: c.isUser ? "user" : m.personaId,
      authorName: c.author, content: c.content, createdAt: c.createdAt,
    })),
    createdAt: m.createdAt,
  };
}

interface MomentsState {
  /** 朋友圈列表 */
  moments: Moment[];
  /** 是否正在加载 */
  isLoading: boolean;
  /** 最近一次操作错误 */
  error: string | null;

  /** 从引擎加载动态列表（省略 personaId 时沿用上次角色） */
  load: (personaId?: string) => Promise<void>;
  /** 设置朋友圈列表 */
  setMoments: (moments: Moment[]) => void;
  /** 发布朋友圈（AI 会自动点赞评论） */
  publishMoment: (personaId: string, content: string, images: string[]) => Promise<void>;
  /** 点赞朋友圈 */
  likeMoment: (momentId: string) => Promise<void>;
  /** 评论朋友圈（AI 动态会得到 AI 回复） */
  commentMoment: (momentId: string, authorId: string, authorName: string, content: string, replyTo?: string) => Promise<void>;
  /** 删除朋友圈 */
  deleteMoment: (momentId: string) => Promise<void>;
  /** AI 生成一条动态；未配置 LLM 时返回 false */
  generateAi: (personaId: string) => Promise<boolean>;
  /** 清除错误提示 */
  clearError: () => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

/** 当前列表所属角色（load 无参调用时沿用），模块级保存 */
let scopedPersonaId = "";

export const useMomentsStore = create<MomentsState>()((set, get) => ({
  moments: [],
  isLoading: false,
  error: null,

  load: async (personaId) => {
    if (personaId !== undefined) scopedPersonaId = personaId;
    set({ isLoading: true });
    try {
      const list = await listMoments(scopedPersonaId || undefined);
      set({ moments: list.map(recordToMoment), isLoading: false });
    } catch {
      set({ isLoading: false });
    }
  },

  setMoments: (moments) => set({ moments }),

  publishMoment: async (personaId, content, images) => {
    try {
      await publishUserMoment(personaId, content, images);
      await get().load(personaId);
      // AI 点赞/评论有 1.5~4.5s 延迟，稍后再拉一次
      setTimeout(() => void get().load(), 5000);
    } catch (e) {
      set({ error: `发布失败：${(e as Error).message}` });
    }
  },

  likeMoment: async (momentId) => {
    await toggleLike(momentId, userName());
    await get().load();
  },

  commentMoment: async (momentId, _authorId, _authorName, content) => {
    try {
      await userCommentOnMoment(momentId, content);
      await get().load();
      // AI 回复延迟到达，稍后刷新
      setTimeout(() => void get().load(), 4000);
    } catch (e) {
      set({ error: `评论失败：${(e as Error).message}` });
    }
  },

  deleteMoment: async (momentId) => {
    await engineDeleteMoment(momentId);
    set((state) => ({ moments: state.moments.filter((m) => m.id !== momentId) }));
  },

  generateAi: async (personaId) => {
    if (!getActiveProvider()) {
      set({ error: "尚未配置 AI 模型，无法生成动态。请先到「设置」中添加模型提供商。" });
      return false;
    }
    const m = await generateAiMoment(personaId);
    if (!m) {
      set({ error: "AI 动态生成失败，请稍后再试。" });
      return false;
    }
    await get().load(personaId);
    return true;
  },

  clearError: () => set({ error: null }),
  setLoading: (loading) => set({ isLoading: loading }),
}));

// 引擎动态事件 / moments 数据变化 → 重拉列表（AI 心跳、自动点赞评论等后台写入也能反映到 UI）
on("moment-generated", () => {
  void useMomentsStore.getState().load();
});
on<{ collection?: string }>("data-changed", (detail) => {
  if (detail?.collection === "moments") void useMomentsStore.getState().load();
});
