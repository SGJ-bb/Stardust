import { create } from "zustand";
import type { GroupChat, GroupMessage } from "@/types/group-chat";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { useSettingsStore } from "@/stores/useSettingsStore";
import {
  listGroups, createGroup, deleteGroup as engineDeleteGroup, sendGroupMessage,
} from "@/engine/groupchatEngine";
import type { SpeakMode as EngineSpeakMode } from "@/engine/groupchatEngine";
import { on } from "@/lib/events";
import type { GroupRecord } from "@/engine/kinds";

/** 页面发言模式（free/turn/moderator）→ 引擎模式（auto/manual/ai_judge） */
const MODE_TO_ENGINE: Record<string, EngineSpeakMode> = {
  free: "auto",
  turn: "manual",
  moderator: "ai_judge",
};

function userName(): string {
  return useSettingsStore.getState().settings.userProfile?.nickname?.trim() || "我";
}

/** 群消息发送者 → personaId（引擎 senderId 即 personaId 或 "user"） */
function resolveSenderId(senderId: string): { personaId: string; name: string } {
  if (senderId === "user") return { personaId: "user", name: userName() };
  const p = usePersonaStore.getState().personas.find((x) => x.id === senderId);
  return { personaId: senderId, name: p?.name ?? senderId };
}

/** 引擎 GroupRecord → 页面 GroupChat */
function recordToGroup(g: GroupRecord): GroupChat {
  const personas = usePersonaStore.getState().personas;
  return {
    id: g.id, name: g.name, avatar: "", description: "",
    members: g.memberIds.map((pid, i) => {
      const p = personas.find((x) => x.id === pid);
      return { personaId: pid, name: p?.name ?? "未知角色", avatar: p?.avatar ?? "", isAdmin: i === 0, speakWeight: 1 };
    }),
    messages: g.messages.map((m) => {
      const resolved = resolveSenderId(m.senderId);
      return {
        id: m.id, groupId: g.id, personaId: resolved.personaId, content: m.content,
        timestamp: m.timestamp, isSystem: false,
      };
    }),
    speakMode: "free", topic: "", worldId: g.worldId,
    createdAt: g.createdAt, updatedAt: g.updatedAt,
    lastMessageTime: g.messages[g.messages.length - 1]?.timestamp ?? g.updatedAt,
  };
}

interface GroupChatState {
  /** 群聊列表 */
  groups: GroupChat[];
  /** 当前活跃群聊 */
  activeGroup: GroupChat | null;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 从引擎加载全部群聊 */
  load: () => Promise<void>;
  /** 设置群聊列表 */
  setGroups: (groups: GroupChat[]) => void;
  /** 设置活跃群聊 */
  setActiveGroup: (group: GroupChat | null) => void;
  /** 创建群聊（落库引擎后刷新列表；入参 group 只需 name/memberIds 有效） */
  addGroup: (group: GroupChat) => Promise<void>;
  /** 删除群聊 */
  deleteGroup: (groupId: string) => Promise<void>;
  /** 更新群聊设置（发言模式持久化到本地 kv，其余字段暂存内存） */
  updateGroup: (groupId: string, partial: Partial<GroupChat>) => void;
  /** 发送用户消息（引擎触发 AI 回复，随后经事件刷新） */
  addGroupMessage: (groupId: string, personaId: string, content: string) => Promise<void>;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

/** 各群的发言模式偏好（引擎不存此字段，本地记忆） */
const localSpeakMode = new Map<string, GroupChat["speakMode"]>();

/** 缓存引擎成员 id（页面 GroupChat.members 即来自 memberIds，直接读内存态） */
function groupEngineMemberIds(groupId: string): string[] {
  return useGroupChatStore.getState().groups.find((g) => g.id === groupId)?.members.map((m) => m.personaId) ?? [];
}

export const useGroupChatStore = create<GroupChatState>()((set, get) => ({
  groups: [],
  activeGroup: null,
  isLoading: false,

  load: async () => {
    set({ isLoading: true });
    try {
      const list = await listGroups();
      const groups = list.map(recordToGroup).map((g) => ({
        ...g,
        speakMode: localSpeakMode.get(g.id) ?? g.speakMode,
      }));
      set((state) => ({
        groups,
        activeGroup: state.activeGroup ? groups.find((g) => g.id === state.activeGroup?.id) ?? null : null,
        isLoading: false,
      }));
    } catch {
      set({ isLoading: false });
    }
  },

  setGroups: (groups) => set({ groups }),
  setActiveGroup: (group) => set({ activeGroup: group }),

  addGroup: async (group) => {
    try {
      const created = await createGroup(group.name, group.members.map((m) => m.personaId), group.worldId);
      if (group.speakMode) localSpeakMode.set(created.id, group.speakMode);
      await get().load();
    } catch { /* 建群失败静默，列表不变 */ }
  },

  deleteGroup: async (groupId) => {
    await engineDeleteGroup(groupId);
    localSpeakMode.delete(groupId);
    set((state) => ({
      groups: state.groups.filter((g) => g.id !== groupId),
      activeGroup: state.activeGroup?.id === groupId ? null : state.activeGroup,
    }));
  },

  updateGroup: (groupId, partial) => {
    if (partial.speakMode) localSpeakMode.set(groupId, partial.speakMode);
    set((state) => ({
      groups: state.groups.map((g) =>
        g.id === groupId ? { ...g, ...partial, updatedAt: Date.now() } : g
      ),
      activeGroup:
        state.activeGroup?.id === groupId
          ? { ...state.activeGroup, ...partial, updatedAt: Date.now() }
          : state.activeGroup,
    }));
  },

  addGroupMessage: async (groupId, personaId, content) => {
    if (personaId !== "user") return; // 仅用户发言入口；AI 回复由引擎产生并经事件刷新
    // 先乐观展示用户消息
    const message: GroupMessage = {
      id: `tmp-${Date.now()}`, groupId, personaId: "user", content,
      timestamp: Date.now(), isSystem: false,
    };
    set((state) => ({
      groups: state.groups.map((g) =>
        g.id === groupId
          ? { ...g, messages: [...g.messages, message], lastMessageTime: Date.now(), updatedAt: Date.now() }
          : g
      ),
      activeGroup:
        state.activeGroup?.id === groupId
          ? { ...state.activeGroup, messages: [...state.activeGroup.messages, message], lastMessageTime: Date.now() }
          : state.activeGroup,
    }));
    try {
      const mode = MODE_TO_ENGINE[localSpeakMode.get(groupId) ?? "free"];
      // manual 模式默认点名全部成员（引擎语义：无 @ 时用 manualIds）
      const manualIds = groupEngineMemberIds(groupId);
      await sendGroupMessage(groupId, content, { speakMode: mode, manualIds });
    } catch { /* 发送失败：引擎无回复，等待下次刷新对齐 */ }
    // AI 回复在 sendGroupMessage 内完成后统一重拉（清掉乐观临时消息、拿最终数据）
    await get().load();
  },

  setLoading: (loading) => set({ isLoading: loading }),
}));

// 引擎群聊数据变化 → 节流重拉（AI 链式回复会多次 emit，合并尾部刷新）
let reloadTimer: number | null = null;
function scheduleReload(): void {
  if (reloadTimer !== null) return;
  reloadTimer = window.setTimeout(() => {
    reloadTimer = null;
    void useGroupChatStore.getState().load();
  }, 300);
}

on<{ collection?: string }>("data-changed", (detail) => {
  if (detail?.collection === "groupChats") scheduleReload();
});
