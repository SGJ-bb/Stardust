import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Moment, Comment } from "@/types/moments";
import { generateId } from "@/lib/utils";

interface MomentsState {
  /** 朋友圈列表 */
  moments: Moment[];
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置朋友圈列表 */
  setMoments: (moments: Moment[]) => void;
  /** 发布朋友圈 */
  publishMoment: (personaId: string, content: string, images: string[]) => void;
  /** 点赞朋友圈 */
  likeMoment: (momentId: string) => void;
  /** 评论朋友圈 */
  commentMoment: (momentId: string, authorId: string, authorName: string, content: string, replyTo?: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useMomentsStore = create<MomentsState>()(
  persist(
    (set) => ({
      moments: [],
      isLoading: false,

      setMoments: (moments) => set({ moments }),

      publishMoment: (personaId, content, images) => {
        const moment: Moment = {
          id: generateId(),
          personaId,
          content,
          images,
          mood: "",
          likes: 0,
          liked: false,
          comments: [],
          createdAt: Date.now(),
        };
        set((state) => ({ moments: [moment, ...state.moments] }));
      },

      likeMoment: (momentId) =>
        set((state) => ({
          moments: state.moments.map((m) =>
            m.id === momentId ? { ...m, liked: !m.liked, likes: m.liked ? m.likes - 1 : m.likes + 1 } : m
          ),
        })),

      commentMoment: (momentId, authorId, authorName, content, replyTo) => {
        const comment: Comment = {
          id: generateId(),
          momentId,
          authorId,
          authorName,
          content,
          replyTo,
          createdAt: Date.now(),
        };
        set((state) => ({
          moments: state.moments.map((m) =>
            m.id === momentId ? { ...m, comments: [...m.comments, comment] } : m
          ),
        }));
      },

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-moments",
      partialize: (state) => ({ moments: state.moments }),
    }
  )
);
