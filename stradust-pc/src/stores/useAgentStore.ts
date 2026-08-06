// Agent 智能体 Zustand Store

import { create } from "zustand";
import { persist } from "zustand/middleware";
import type {
  SkillMeta,
  SkillCategory,
  AgentSession,
  AgentMessage,
  AgentToolCall,
} from "@/lib/agent/types";
import { isTauri } from "@/lib/tauri";

interface AgentState {
  // 技能列表
  skills: SkillMeta[];
  skillsLoading: boolean;

  // 当前激活的分类
  activeCategory: SkillCategory | "all";

  // 会话
  sessions: AgentSession[];
  currentSessionId: string | null;

  // 聊天状态
  isStreaming: boolean;
  streamingContent: string;
  currentToolCalls: AgentToolCall[];

  // Actions
  fetchSkills: () => Promise<void>;
  setActiveCategory: (cat: SkillCategory | "all") => void;
  toggleSkill: (skillId: string) => Promise<void>;

  // 会话操作
  createSession: (
    title: string,
    category: SkillCategory,
  ) => Promise<AgentSession>;
  fetchSessions: () => Promise<void>;

  // 聊天操作
  sendMessage: (message: string) => Promise<void>;
  clearCurrentSession: () => void;

  // 内部
  addMessage: (message: AgentMessage) => void;
  setStreaming: (streaming: boolean) => void;
  appendStreamContent: (content: string) => void;
  finishStream: () => void;
  updateToolCall: (toolCall: AgentToolCall) => void;
}

const DEFAULT_SESSION: AgentSession = {
  id: "",
  title: "新对话",
  messages: [],
  createdAt: Date.now(),
  updatedAt: Date.now(),
  activeCategory: "ai_assistant",
};

export const useAgentStore = create<AgentState>()(
  persist(
    (set, get) => ({
      skills: [],
      skillsLoading: false,
      activeCategory: "all",
      sessions: [],
      currentSessionId: null,
      isStreaming: false,
      streamingContent: "",
      currentToolCalls: [],

      fetchSkills: async () => {
        if (!isTauri()) {
          // 浏览器环境：返回空列表，不报错
          set({ skills: [], skillsLoading: false });
          return;
        }
        set({ skillsLoading: true });
        try {
          const { invoke } = await import("@tauri-apps/api/core");
          const skills = await invoke<SkillMeta[]>("list_agent_skills");
          set({ skills, skillsLoading: false });
        } catch (e) {
          console.error("[Agent] 获取技能列表失败:", e);
          set({ skillsLoading: false });
        }
      },

      setActiveCategory: (category) => set({ activeCategory: category }),

      toggleSkill: async (skillId) => {
        if (!isTauri()) return;
        try {
          // 先找到当前状态
          const skill = get().skills.find((s) => s.id === skillId);
          if (!skill) return;

          const { invoke } = await import("@tauri-apps/api/core");
          await invoke("toggle_agent_skill", {
            skillId,
            enabled: !skill.enabled,
          });

          // 更新本地状态
          set({
            skills: get().skills.map((s) =>
              s.id === skillId ? { ...s, enabled: !s.enabled } : s,
            ),
          });
        } catch (e) {
          console.error("[Agent] 切换技能失败:", e);
        }
      },

      createSession: async (title, category) => {
        // 浏览器环境：直接创建本地会话
        if (!isTauri()) {
          const session: AgentSession = {
            ...DEFAULT_SESSION,
            id: `local_${Date.now()}`,
            title,
            activeCategory: category,
            createdAt: Date.now(),
            updatedAt: Date.now(),
          };
          set((state) => ({
            sessions: [...state.sessions, session],
            currentSessionId: session.id,
          }));
          return session;
        }
        try {
          const { invoke } = await import("@tauri-apps/api/core");
          const session = await invoke<AgentSession>("create_agent_session", {
            title,
            category,
          });
          set((state) => ({
            sessions: [...state.sessions, session],
            currentSessionId: session.id,
          }));
          return session;
        } catch (e) {
          console.error("[Agent] 创建会话失败:", e);
          // 创建本地会话作为 fallback
          const session: AgentSession = {
            ...DEFAULT_SESSION,
            id: `local_${Date.now()}`,
            title,
            activeCategory: category,
            createdAt: Date.now(),
            updatedAt: Date.now(),
          };
          set((state) => ({
            sessions: [...state.sessions, session],
            currentSessionId: session.id,
          }));
          return session;
        }
      },

      fetchSessions: async () => {
        if (!isTauri()) return;
        try {
          const { invoke } = await import("@tauri-apps/api/core");
          const sessions = await invoke<AgentSession[]>("list_agent_sessions");
          set({ sessions });
        } catch (e) {
          console.error("[Agent] 获取会话列表失败:", e);
        }
      },

      sendMessage: async (message) => {
        const state = get();
        if (state.isStreaming || !message.trim()) return;

        set({ isStreaming: true, streamingContent: "", currentToolCalls: [] });

        // 添加用户消息
        const userMsg: AgentMessage = {
          id: `msg_${Date.now()}_u`,
          role: "user",
          content: message,
          timestamp: Date.now(),
        };
        get().addMessage(userMsg);

        // 浏览器环境：直接返回提示，不调用 Tauri
        if (!isTauri()) {
          const errorMsg: AgentMessage = {
            id: `msg_${Date.now()}_a`,
            role: "assistant",
            content: "Agent 功能仅在 Tauri 桌面环境下可用。",
            timestamp: Date.now(),
          };
          get().addMessage(errorMsg);
          get().finishStream();
          return;
        }

        try {
          // 监听流式事件
          const { listen } = await import("@tauri-apps/api/event");
          const unlisten = await listen<unknown>("agent-stream", (event) => {
            const payload = event.payload as Record<string, unknown>;
            const eventType = payload.type as string;

            switch (eventType) {
              case "content":
                get().appendStreamContent(payload.data as string);
                break;
              case "tool_start": {
                const tcData = payload.data as {
                  name: string;
                  args: Record<string, unknown>;
                };
                const tc: AgentToolCall = {
                  id: `tc_${Date.now()}`,
                  name: tcData.name,
                  arguments: tcData.args,
                  status: "running",
                  startedAt: Date.now(),
                };
                get().updateToolCall(tc);
                break;
              }
              case "tool_result": {
                const trData = payload.data as {
                  name: string;
                  result: string;
                  success: boolean;
                };
                set((s) => ({
                  currentToolCalls: s.currentToolCalls.map((tc) =>
                    tc.name === trData.name
                      ? {
                          ...tc,
                          status: trData.success
                            ? ("success" as const)
                            : ("failed" as const),
                          result: trData.result,
                          finishedAt: Date.now(),
                        }
                      : tc,
                  ),
                }));
                break;
              }
              case "done":
                break;
              case "error":
                console.error("[Agent] 流式错误:", payload.data);
                break;
            }
          });

          // 发送请求
          const { invoke } = await import("@tauri-apps/api/core");
          const response = await invoke<string>("agent_chat", {
            sessionId: state.currentSessionId,
            message,
            category: state.activeCategory,
            history:
              state.sessions.find((s) => s.id === state.currentSessionId)
                ?.messages ?? [],
          });

          // 如果有流式内容，使用它；否则使用最终响应
          const finalContent = get().streamingContent || response;

          const assistantMsg: AgentMessage = {
            id: `msg_${Date.now()}_a`,
            role: "assistant",
            content: finalContent,
            toolCalls:
              get().currentToolCalls.length > 0
                ? get().currentToolCalls
                : undefined,
            timestamp: Date.now(),
          };
          get().addMessage(assistantMsg);

          unlisten();
        } catch (e) {
          console.error("[Agent] 发送消息失败:", e);
          const errorMsg: AgentMessage = {
            id: `msg_${Date.now()}_err`,
            role: "assistant",
            content: `出错了: ${e instanceof Error ? e.message : String(e)}`,
            timestamp: Date.now(),
          };
          get().addMessage(errorMsg);
        } finally {
          get().finishStream();
        }
      },

      clearCurrentSession: () => {
        const state = get();
        if (!state.currentSessionId) return;
        set({
          sessions: state.sessions.map((s) =>
            s.id === state.currentSessionId ? { ...s, messages: [] } : s,
          ),
        });
      },

      addMessage: (message) => {
        const state = get();
        if (!state.currentSessionId) return;
        set({
          sessions: state.sessions.map((s) =>
            s.id === state.currentSessionId
              ? {
                  ...s,
                  messages: [...s.messages, message],
                  updatedAt: Date.now(),
                }
              : s,
          ),
        });
      },

      setStreaming: (streaming) => set({ isStreaming: streaming }),
      appendStreamContent: (content) =>
        set((s) => ({ streamingContent: s.streamingContent + content })),
      finishStream: () => set({ isStreaming: false, streamingContent: "" }),

      updateToolCall: (toolCall) =>
        set((s) => ({
          currentToolCalls: [
            ...s.currentToolCalls.filter((tc) => tc.name !== toolCall.name),
            toolCall,
          ],
        })),
    }),
    {
      name: "stradust-agent",
      partialize: (state) => ({
        activeCategory: state.activeCategory,
        currentSessionId: state.currentSessionId,
        sessions: state.sessions.map((s) => ({
          ...s,
          messages: s.messages.slice(-20), // 只保留最近20条到localStorage
        })),
      }),
    },
  ),
);
