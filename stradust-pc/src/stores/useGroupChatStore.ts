import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { GroupChat, GroupMessage } from "@/types/group-chat";
import { generateId } from "@/lib/utils";

interface GroupChatState {
  /** 群聊列表 */
  groups: GroupChat[];
  /** 当前活跃群聊 */
  activeGroup: GroupChat | null;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置群聊列表 */
  setGroups: (groups: GroupChat[]) => void;
  /** 设置活跃群聊 */
  setActiveGroup: (group: GroupChat | null) => void;
  /** 添加群聊 */
  addGroup: (group: GroupChat) => void;
  /** 删除群聊 */
  deleteGroup: (groupId: string) => void;
  /** 更新群聊设置 */
  updateGroup: (groupId: string, partial: Partial<GroupChat>) => void;
  /** 发送群聊消息 */
  addGroupMessage: (groupId: string, personaId: string, content: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useGroupChatStore = create<GroupChatState>()(
  persist(
    (set) => ({
      groups: [],
      activeGroup: null,
      isLoading: false,

      setGroups: (groups) => set({ groups }),
      setActiveGroup: (group) => set({ activeGroup: group }),

      addGroup: (group) => set((state) => ({ groups: [...state.groups, group] })),

      deleteGroup: (groupId) =>
        set((state) => ({
          groups: state.groups.filter((g) => g.id !== groupId),
          activeGroup: state.activeGroup?.id === groupId ? null : state.activeGroup,
        })),

      updateGroup: (groupId, partial) =>
        set((state) => ({
          groups: state.groups.map((g) =>
            g.id === groupId ? { ...g, ...partial, updatedAt: Date.now() } : g
          ),
          activeGroup:
            state.activeGroup?.id === groupId
              ? { ...state.activeGroup, ...partial, updatedAt: Date.now() }
              : state.activeGroup,
        })),

      addGroupMessage: (groupId, personaId, content) => {
        const message: GroupMessage = {
          id: generateId(),
          groupId,
          personaId,
          content,
          timestamp: Date.now(),
          isSystem: false,
        };
        set((state) => ({
          groups: state.groups.map((g) =>
            g.id === groupId
              ? { ...g, messages: [...g.messages, message], lastMessageTime: Date.now(), updatedAt: Date.now() }
              : g
          ),
          activeGroup:
            state.activeGroup?.id === groupId
              ? {
                  ...state.activeGroup,
                  messages: [...state.activeGroup.messages, message],
                  lastMessageTime: Date.now(),
                  updatedAt: Date.now(),
                }
              : state.activeGroup,
        }));
      },

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-group-chat",
      partialize: (state) => ({
        groups: state.groups,
        activeGroup: state.activeGroup,
      }),
    }
  )
);
