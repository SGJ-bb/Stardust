import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { ChatMessage, ChatSession, StreamEvent, Favorability } from "@/types/chat";
import { generateId } from "@/lib/utils";

/** 按角色ID分组的消息存储 */
type MessagesByPersona = Record<string, ChatMessage[]>;

interface ChatState {
  /** 当前聊天的角色ID */
  currentPersonaId: string | null;
  /** 当前角色的消息列表（从 messagesByPersona 派生） */
  messages: ChatMessage[];
  /** 按角色ID分组存储的所有消息 */
  messagesByPersona: MessagesByPersona;
  /** 会话列表 */
  sessions: ChatSession[];
  /** 是否正在发送消息 */
  isSending: boolean;
  /** 是否正在流式接收 */
  isStreaming: boolean;
  /** 当前流式内容 */
  streamingContent: string;
  /** 好感度信息 */
  favorability: Favorability | null;
  /** 是否正在加载历史 */
  isLoadingHistory: boolean;

  /** 设置当前角色（切换时保留各角色消息） */
  setCurrentPersona: (personaId: string) => void;
  /** 添加用户消息 */
  addUserMessage: (personaId: string, content: string) => void;
  /** 添加AI消息 */
  addAssistantMessage: (personaId: string, content: string) => void;
  /** 更新流式内容 */
  appendStreamContent: (token: string) => void;
  /** 完成流式响应 */
  finishStream: () => void;
  /** 设置发送状态 */
  setSending: (sending: boolean) => void;
  /** 设置流式状态 */
  setStreaming: (streaming: boolean) => void;
  /** 设置消息列表 */
  setMessages: (messages: ChatMessage[]) => void;
  /** 删除消息 */
  deleteMessage: (messageId: string) => void;
  /** 切换消息收藏 */
  toggleFavorite: (messageId: string) => void;
  /** 清空消息 */
  clearMessages: () => void;
  /** 设置好感度 */
  setFavorability: (favorability: Favorability) => void;
  /** 设置加载状态 */
  setLoadingHistory: (loading: boolean) => void;
}

/** 更新指定角色的消息并返回新的 messagesByPersona */
function updatePersonaMessages(
  messagesByPersona: MessagesByPersona,
  personaId: string,
  updater: (msgs: ChatMessage[]) => ChatMessage[]
): MessagesByPersona {
  const current = messagesByPersona[personaId] ?? [];
  return { ...messagesByPersona, [personaId]: updater(current) };
}

export const useChatStore = create<ChatState>()(
  persist(
    (set, get) => ({
      currentPersonaId: null,
      messages: [],
      messagesByPersona: {},
      sessions: [],
      isSending: false,
      isStreaming: false,
      streamingContent: "",
      favorability: null,
      isLoadingHistory: false,

      setCurrentPersona: (personaId) => {
        const { messagesByPersona } = get();
        const messages = messagesByPersona[personaId] ?? [];
        set({ currentPersonaId: personaId, messages });
      },

      addUserMessage: (personaId, content) => {
        const message: ChatMessage = {
          id: generateId(),
          personaId,
          role: "user",
          content,
          timestamp: Date.now(),
          status: "sent",
          favorited: false,
          attachments: [],
          toolCalls: [],
          reactions: [],
        };
        set((state) => {
          const messagesByPersona = updatePersonaMessages(state.messagesByPersona, personaId, (msgs) => [...msgs, message]);
          const messages = state.currentPersonaId === personaId ? messagesByPersona[personaId] : state.messages;
          return { messagesByPersona, messages };
        });
      },

      addAssistantMessage: (personaId, content) => {
        const message: ChatMessage = {
          id: generateId(),
          personaId,
          role: "assistant",
          content,
          timestamp: Date.now(),
          status: "sent",
          favorited: false,
          attachments: [],
          toolCalls: [],
          reactions: [],
        };
        set((state) => {
          const messagesByPersona = updatePersonaMessages(state.messagesByPersona, personaId, (msgs) => [...msgs, message]);
          const messages = state.currentPersonaId === personaId ? messagesByPersona[personaId] : state.messages;
          return { messagesByPersona, messages };
        });
      },

      appendStreamContent: (token) => {
        set((state) => ({ streamingContent: state.streamingContent + token }));
      },

      finishStream: () => {
        const { streamingContent, currentPersonaId, messages, messagesByPersona } = get();
        if (streamingContent && currentPersonaId) {
          const message: ChatMessage = {
            id: generateId(),
            personaId: currentPersonaId,
            role: "assistant",
            content: streamingContent,
            timestamp: Date.now(),
            status: "sent",
            favorited: false,
            attachments: [],
            toolCalls: [],
            reactions: [],
          };
          const newMessages = [...messages, message];
          const newMessagesByPersona = { ...messagesByPersona, [currentPersonaId]: newMessages };
          set({ messages: newMessages, messagesByPersona: newMessagesByPersona, streamingContent: "", isStreaming: false });
        } else {
          set({ streamingContent: "", isStreaming: false });
        }
      },

      setSending: (sending) => set({ isSending: sending }),
      setStreaming: (streaming) => set({ isStreaming: streaming }),
      setMessages: (messages) => {
        const { currentPersonaId, messagesByPersona } = get();
        if (currentPersonaId) {
          set({ messages, messagesByPersona: { ...messagesByPersona, [currentPersonaId]: messages } });
        } else {
          set({ messages });
        }
      },

      deleteMessage: (messageId) =>
        set((state) => {
          const { currentPersonaId } = state;
          const updateFn = (msgs: ChatMessage[]) => msgs.filter((m) => m.id !== messageId);
          const messagesByPersona = currentPersonaId
            ? updatePersonaMessages(state.messagesByPersona, currentPersonaId, updateFn)
            : state.messagesByPersona;
          const messages = currentPersonaId ? messagesByPersona[currentPersonaId] : state.messages.filter((m) => m.id !== messageId);
          return { messages, messagesByPersona };
        }),

      toggleFavorite: (messageId) =>
        set((state) => {
          const toggleFn = (msgs: ChatMessage[]) =>
            msgs.map((m) => m.id === messageId ? { ...m, favorited: !m.favorited } : m);
          const { currentPersonaId } = state;
          const messagesByPersona = currentPersonaId
            ? updatePersonaMessages(state.messagesByPersona, currentPersonaId, toggleFn)
            : state.messagesByPersona;
          const messages = currentPersonaId ? messagesByPersona[currentPersonaId] : toggleFn(state.messages);
          return { messages, messagesByPersona };
        }),

      clearMessages: () => {
        const { currentPersonaId, messagesByPersona } = get();
        if (currentPersonaId) {
          const newByPersona = { ...messagesByPersona };
          delete newByPersona[currentPersonaId];
          set({ messages: [], messagesByPersona: newByPersona });
        } else {
          set({ messages: [] });
        }
      },
      setFavorability: (favorability) => set({ favorability }),
      setLoadingHistory: (loading) => set({ isLoadingHistory: loading }),
    }),
    {
      name: "stradust-chat",
      // 不持久化临时状态
      partialize: (state) => ({
        currentPersonaId: state.currentPersonaId,
        messagesByPersona: state.messagesByPersona,
        sessions: state.sessions,
        favorability: state.favorability,
      }),
    }
  )
);
