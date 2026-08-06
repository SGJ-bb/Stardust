import type {
  ActivationResult,
  PurchaseResult,
  SendMessageParams,
  StreamChatParams,
  CreatePersonaParams,
  UpdatePersonaParams,
  GetMemoriesParams,
  CreateMemoryParams,
  TtsParams,
  SttParams,
  Live2DControlParams,
  FileDialogParams,
} from "@/types/tauri";
import type { Persona, PersonaGender } from "@/types/persona";
import type { ChatMessage, ChatSession } from "@/types/chat";
import type { Memory } from "@/types/memory";
import type { Settings } from "@/types/settings";
import type { GroupChat } from "@/types/group-chat";
import type { Achievement, CheckIn, Growth } from "@/types/achievement";
import type { Diary } from "@/types/diary";
import type { Moment } from "@/types/moments";
import type { CalendarEvent } from "@/types/calendar";
import type { TimeCapsule } from "@/types/capsule";
import type { AlbumEntry } from "@/types/album";
import type { Sticker, StickerPack } from "@/types/sticker";
import type { WorldConfig, WorldState } from "@/types/virtual-world";
// Web 模式下直接读取 Zustand Store（无循环依赖）
import { useSettingsStore } from "@/stores/useSettingsStore";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { useCalendarStore } from "@/stores/useCalendarStore";

/** 检测是否在 Tauri 环境中运行 */
export function isTauri(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

/** 安全调用 Tauri 命令，非 Tauri 环境返回 mock 数据 */
async function tauriCommand<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  if (!isTauri()) {
    console.warn(`[Mock] Tauri命令 "${command}" 在浏览器环境中被调用，返回 mock 数据`);
    return getMockData<T>(command, args);
  }
  const { invoke } = await import("@tauri-apps/api/core");
  return invoke<T>(command, args);
}

/** Mock 数据存储，用于 web 模式下模拟数据持久化 */
const mockStore: {
  personas: Persona[];
  messages: ChatMessage[];
  memories: Memory[];
  settings: Settings | null;
} = {
  personas: [],
  messages: [],
  memories: [],
  settings: null,
};

/** 根据 command 返回 mock 数据 */
function getMockData<T>(command: string, _args?: Record<string, unknown>): T {
  switch (command) {
    // 聊天相关
    case "send_chat": {
      const msg: ChatMessage = { id: `msg-${Date.now()}`, personaId: "", role: "assistant", content: "这是一条 mock 回复消息", timestamp: Date.now(), status: "sent", favorited: false, attachments: [], toolCalls: [], reactions: [] };
      mockStore.messages.push(msg);
      return msg as T;
    }
    case "send_chat_stream": {
      // Web 模式：动态导入 webApi 并执行真实流式调用
      import("./webApi").then(({ webStreamChat }) => {
        const p = _args as unknown as { personaId?: string; content?: string; attachments?: string[] };
        webStreamChat(
          { personaId: p?.personaId ?? "", content: p?.content ?? "", attachments: p?.attachments },
          {
            onToken: (token) => {
              window.dispatchEvent(new CustomEvent("mock-chat-stream", {
                detail: { type: "content", content: token }
              }));
            },
            onDone: () => {
              window.dispatchEvent(new CustomEvent("mock-chat-stream", {
                detail: { type: "done" }
              }));
            },
            onError: (errMsg) => {
              window.dispatchEvent(new CustomEvent("mock-chat-stream", {
                detail: { type: "error", content: errMsg }
              }));
            },
          }
        );
      }).catch((err) => {
        console.error("[Web] Failed to load webApi:", err);
        window.dispatchEvent(new CustomEvent("mock-chat-stream", {
          detail: { type: "error", content: "Web API 加载失败: " + (err as Error).message }
        }));
      });
      return undefined as T;
    }
    case "list_personas": {
      return usePersonaStore.getState().personas as T;
    }
    case "get_persona": {
      const persona = usePersonaStore.getState().personas.find(p => p.id === _args?.["personaId"]);
      return (persona ?? null) as T;
    }
    case "create_persona": {
      const store = usePersonaStore.getState();
      const persona = store.addPersona(_args as unknown as Parameters<typeof store.addPersona>[0]);
      return persona as T;
    }
    case "update_persona": {
      const args = _args as { id?: string; [key: string]: unknown };
      const id = args?.id;
      if (id) {
        const { id: _id, ...rest } = args;
        usePersonaStore.getState().updatePersona(id, rest);
        return usePersonaStore.getState().personas.find(p => p.id === id) as T;
      }
      return null as T;
    }
    case "delete_persona": {
      usePersonaStore.getState().deletePersona(_args?.["personaId"] as string);
      return undefined as T;
    }

    // 记忆相关 — 使用 localStorage 持久化
    case "list_memories": {
      try {
        const raw = localStorage.getItem("stradust-memories");
        if (raw) {
          return JSON.parse(raw) as T;
        }
      } catch {}
      return [] as T;
    }
    case "add_memory": {
      try {
        const raw = localStorage.getItem("stradust-memories");
        const memories: Memory[] = raw ? JSON.parse(raw) : [];
        const newMemory: Memory = {
          id: `mem-${Date.now()}`,
          personaId: (_args?.["personaId"] as string) || "",
          shortTerm: [],
          longTerm: [],
          core: [],
          pools: [],
          totalCount: 0,
          updatedAt: Date.now(),
          ...(_args as Partial<Memory>),
        };
        memories.push(newMemory);
        localStorage.setItem("stradust-memories", JSON.stringify(memories));
        return newMemory as T;
      } catch {
        return null as T;
      }
    }
    case "delete_memory": {
      try {
        const raw = localStorage.getItem("stradust-memories");
        if (raw) {
          const memories: Memory[] = JSON.parse(raw);
          const filtered = memories.filter(m => m.id !== _args?.["memoryId"]);
          localStorage.setItem("stradust-memories", JSON.stringify(filtered));
        }
      } catch {}
      return undefined as T;
    }
    case "search_memories": {
      try {
        const raw = localStorage.getItem("stradust-memories");
        if (raw) {
          const memories: Memory[] = JSON.parse(raw);
          const query = (_args?.["query"] as string || "").toLowerCase();
          if (!query) return memories as T;
          // 简单过滤：匹配 content 或 tags
          return memories.filter(m =>
            m.shortTerm.some(e => e.content.toLowerCase().includes(query)) ||
            m.longTerm.some(e => e.content.toLowerCase().includes(query)) ||
            m.core.some(e => e.content.toLowerCase().includes(query))
          ) as T;
        }
      } catch {}
      return [] as T;
    }

    // 语音相关
    case "speak":
      return "" as T;
    case "start_voice_record":
    case "stop_voice_record":
      return undefined as T;

    // Live2D相关
    case "control_live2d":
      return undefined as T;

    // 设置相关
    case "get_settings": {
      return useSettingsStore.getState().settings as T;
    }
    case "update_settings": {
      const newSettings = _args?.["settings"] as Partial<Settings>;
      if (newSettings) {
        useSettingsStore.getState().setSettings(newSettings as Settings);
      }
      return undefined as T;
    }

    // 群聊相关
    case "list_group_chats":
      return [] as T;
    case "create_group_chat":
      return null as T;
    case "send_group_message":
      return undefined as T;

    // 成就相关
    case "list_achievements":
      return [] as T;
    case "check_in":
      return { streak: 1, totalDays: 1, lastCheckIn: Date.now(), todayCheckedIn: true } as T;

    // 日记相关
    case "list_diaries":
      return [] as T;
    case "generate_diary":
      return null as T;
    case "delete_diary":
      return undefined as T;

    // 朋友圈相关
    case "list_moments":
      return [] as T;
    case "create_moment":
      return null as T;
    case "toggle_like":
    case "add_comment":
      return undefined as T;

    // 日历相关 — 使用 CalendarStore
    case "list_events": {
      return useCalendarStore.getState().events as T;
    }
    case "add_event": {
      const calStore = useCalendarStore.getState();
      const eventData = _args as Omit<CalendarEvent, "id" | "createdAt" | "updatedAt">;
      calStore.addEvent(eventData);
      const events = useCalendarStore.getState().events;
      return (events[events.length - 1] ?? null) as unknown as T;
    }
    case "delete_event": {
      useCalendarStore.getState().deleteEvent(_args?.["eventId"] as string);
      return undefined as T;
    }

    // 表情包相关
    case "list_stickers":
      return [] as T;
    case "add_sticker":
      return null as T;

    // 时光胶囊相关
    case "list_capsules":
      return [] as T;
    case "create_capsule":
      return null as T;
    case "open_capsule":
      return null as T;

    // 纪念相册相关
    case "get_album_entries":
      return [] as T;
    case "add_album_entry":
      return null as T;

    // 虚拟世界相关
    case "get_virtual_world":
      return null as T;

    // 模型管理相关
    case "list_models":
      return [] as T;
    case "import_model":
    case "delete_model":
      return undefined as T;

    case "get_local_models":
      return [] as T;
    case "download_local_model":
    case "delete_local_model":
      return undefined as T;

    case "get_alarms":
      return [] as T;
    case "create_alarm":
    case "delete_alarm":
      return undefined as T;
    case "get_radio_list":
      return [] as T;
    case "play_radio":
      return undefined as T;
    case "get_skin_list":
      return [] as T;
    case "purchase_skin":
      return { success: false } as T;
    case "activate_code":
      return { success: false, error: "Mock环境不支持激活" } as T;

    default:
      console.warn(`[Mock] 未知的 Tauri 命令: ${command}`);
      return null as T;
  }
}

// ==================== 聊天相关 ====================

/** 发送消息 */
export async function sendMessage(params: SendMessageParams): Promise<ChatMessage> {
  return tauriCommand("send_chat", params as unknown as Record<string, unknown>);
}

/** 流式聊天 */
export async function streamChat(params: StreamChatParams): Promise<void> {
  return tauriCommand("send_chat_stream", params as unknown as Record<string, unknown>);
}

/** 获取聊天历史 */
export async function getChatHistory(personaId: string, limit?: number, offset?: number): Promise<ChatMessage[]> {
  return tauriCommand("get_chat_history", { personaId, limit, offset });
}

/** 获取会话列表 */
export async function getChatSessions(personaId: string): Promise<ChatSession[]> {
  return tauriCommand("get_chat_sessions", { personaId });
}

/** 删除消息 */
export async function deleteMessage(messageId: string): Promise<void> {
  return tauriCommand("delete_message", { messageId });
}

/** 收藏/取消收藏消息 */
export async function toggleFavoriteMessage(messageId: string): Promise<void> {
  return tauriCommand("toggle_favorite", { messageId });
}

/** 清空聊天记录 */
export async function clearChatHistory(personaId: string): Promise<void> {
  return tauriCommand("clear_chat_history", { personaId });
}

// ==================== 角色相关 ====================

/** 获取角色列表 */
export async function getPersonaList(): Promise<Persona[]> {
  return tauriCommand("list_personas");
}

/** 获取角色详情 */
export async function getPersona(personaId: string): Promise<Persona> {
  return tauriCommand("get_persona", { personaId });
}

/** 创建角色 */
export async function createPersona(params: CreatePersonaParams): Promise<Persona> {
  return tauriCommand("create_persona", params as unknown as Record<string, unknown>);
}

/** 更新角色 */
export async function updatePersona(params: UpdatePersonaParams): Promise<Persona> {
  return tauriCommand("update_persona", params as unknown as Record<string, unknown>);
}

/** 删除角色 */
export async function deletePersona(personaId: string): Promise<void> {
  return tauriCommand("delete_persona", { personaId });
}

// ==================== 记忆相关 ====================

/** 获取角色记忆 */
export async function getMemories(params: GetMemoriesParams): Promise<Memory[]> {
  return tauriCommand("list_memories", params as unknown as Record<string, unknown>);
}

/** 创建记忆 */
export async function createMemory(params: CreateMemoryParams): Promise<void> {
  return tauriCommand("add_memory", params as unknown as Record<string, unknown>);
}

/** 删除记忆 */
export async function deleteMemory(memoryId: string): Promise<void> {
  return tauriCommand("delete_memory", { memoryId });
}

/** 搜索记忆 */
export async function searchMemories(personaId: string, query: string): Promise<Memory[]> {
  return tauriCommand("search_memories", { personaId, query });
}

// ==================== 语音相关 ====================

/** 文字转语音 */
export async function textToSpeech(params: TtsParams): Promise<string> {
  return tauriCommand("speak", params as unknown as Record<string, unknown>);
}

/** 语音识别 */
export async function speechToText(params: SttParams): Promise<string> {
  return tauriCommand("speech_to_text", params as unknown as Record<string, unknown>);
}

/** 开始录音 */
export async function startRecording(): Promise<void> {
  return tauriCommand("start_voice_record");
}

/** 停止录音 */
export async function stopRecording(): Promise<string> {
  return tauriCommand("stop_voice_record");
}

// ==================== Live2D相关 ====================

/** 控制Live2D模型 */
export async function controlLive2D(params: Live2DControlParams): Promise<void> {
  return tauriCommand("control_live2d", params as unknown as Record<string, unknown>);
}

// ==================== 设置相关 ====================

/** 获取设置 */
export async function getSettings(): Promise<Settings> {
  return tauriCommand("get_settings");
}

/** 保存设置 */
export async function saveSettings(settings: Settings): Promise<void> {
  return tauriCommand("update_settings", { settings });
}

// ==================== 群聊相关 ====================

/** 获取群聊列表 */
export async function getGroupChatList(): Promise<GroupChat[]> {
  return tauriCommand("list_group_chats");
}

/** 获取群聊详情 */
export async function getGroupChat(groupId: string): Promise<GroupChat> {
  return tauriCommand("get_group_chat", { groupId });
}

/** 创建群聊 */
export async function createGroupChat(name: string, memberIds: string[], worldId?: string): Promise<GroupChat> {
  return tauriCommand("create_group_chat", { name, memberIds, worldId });
}

/** 发送群聊消息 */
export async function sendGroupMessage(groupId: string, content: string): Promise<void> {
  return tauriCommand("send_group_message", { groupId, content });
}

// ==================== 成就相关 ====================

/** 获取成就列表 */
export async function getAchievements(personaId: string): Promise<Achievement[]> {
  return tauriCommand("list_achievements", { personaId });
}

/** 签到 */
export async function checkIn(personaId: string): Promise<CheckIn> {
  return tauriCommand("check_in", { personaId });
}

/** 获取签到状态 */
export async function getCheckInStatus(personaId: string): Promise<CheckIn> {
  return tauriCommand("get_check_in_status", { personaId });
}

/** 获取成长数据 */
export async function getGrowth(personaId: string): Promise<Growth> {
  return tauriCommand("get_growth", { personaId });
}

// ==================== 日记相关 ====================

/** 获取日记列表 */
export async function getDiaryList(personaId: string): Promise<Diary[]> {
  return tauriCommand("list_diaries", { personaId });
}

/** 创建日记 */
export async function createDiary(personaId: string, title: string, content: string, mood: string): Promise<Diary> {
  return tauriCommand("generate_diary", { personaId, title, content, mood });
}

/** 删除日记 */
export async function deleteDiary(diaryId: string): Promise<void> {
  return tauriCommand("delete_diary", { diaryId });
}

// ==================== 朋友圈相关 ====================

/** 获取朋友圈列表 */
export async function getMoments(personaId: string): Promise<Moment[]> {
  return tauriCommand("list_moments", { personaId });
}

/** 发布朋友圈 */
export async function publishMoment(personaId: string, content: string, images: string[]): Promise<Moment> {
  return tauriCommand("create_moment", { personaId, content, images });
}

/** 点赞朋友圈 */
export async function likeMoment(momentId: string): Promise<void> {
  return tauriCommand("toggle_like", { momentId });
}

/** 评论朋友圈 */
export async function commentMoment(momentId: string, content: string, replyTo?: string): Promise<void> {
  return tauriCommand("add_comment", { momentId, content, replyTo });
}

// ==================== 日历相关 ====================

/** 获取日历事件 */
export async function getCalendarEvents(personaId: string, startTime: number, endTime: number): Promise<CalendarEvent[]> {
  return tauriCommand("list_events", { personaId, startTime, endTime });
}

/** 创建日历事件 */
export async function createCalendarEvent(event: Omit<CalendarEvent, "id" | "createdAt" | "updatedAt">): Promise<CalendarEvent> {
  return tauriCommand("add_event", event as unknown as Record<string, unknown>);
}

/** 删除日历事件 */
export async function deleteCalendarEvent(eventId: string): Promise<void> {
  return tauriCommand("delete_event", { eventId });
}

// ==================== 表情包相关 ====================

/** 获取表情包集列表 */
export async function getStickerPacks(): Promise<StickerPack[]> {
  return tauriCommand("list_stickers");
}

/** 导入表情包 */
export async function importStickerPack(path: string): Promise<StickerPack> {
  return tauriCommand("add_sticker", { path });
}

// ==================== 时光胶囊相关 ====================

/** 获取时光胶囊列表 */
export async function getTimeCapsules(personaId: string): Promise<TimeCapsule[]> {
  return tauriCommand("list_capsules", { personaId });
}

/** 创建时光胶囊 */
export async function createTimeCapsule(personaId: string, title: string, content: string, openAt: number): Promise<TimeCapsule> {
  return tauriCommand("create_capsule", { personaId, title, content, openAt });
}

/** 开启时光胶囊 */
export async function openTimeCapsule(capsuleId: string): Promise<TimeCapsule> {
  return tauriCommand("open_capsule", { capsuleId });
}

// ==================== 纪念相册相关 ====================

/** 获取纪念相册 */
export async function getAlbumEntries(personaId: string): Promise<AlbumEntry[]> {
  return tauriCommand("get_album_entries", { personaId });
}

/** 添加相册条目 */
export async function addAlbumEntry(personaId: string, title: string, imagePath: string, description: string): Promise<AlbumEntry> {
  return tauriCommand("add_album_entry", { personaId, title, imagePath, description });
}

// ==================== 虚拟世界相关 ====================

/** 获取世界配置 */
export async function getWorldConfig(worldId: string): Promise<WorldConfig> {
  return tauriCommand("get_virtual_world", { worldId });
}

/** 获取世界状态 */
export async function getWorldState(worldId: string): Promise<WorldState> {
  return tauriCommand("get_world_state", { worldId });
}

/** 创建世界 */
export async function createWorld(config: Omit<WorldConfig, "id" | "createdAt" | "updatedAt">): Promise<WorldConfig> {
  return tauriCommand("create_world", config as unknown as Record<string, unknown>);
}

/** 更新世界设定 */
export async function updateWorldLore(worldId: string, lore: string): Promise<void> {
  return tauriCommand("update_world_config", { worldId, lore });
}

// ==================== 闹钟相关 ====================

/** 获取闹钟列表 */
export async function getAlarms(): Promise<CalendarEvent[]> {
  return tauriCommand("get_alarms");
}

/** 创建闹钟 */
export async function createAlarm(time: string, label: string, repeat?: string): Promise<CalendarEvent> {
  return tauriCommand("create_alarm", { time, label, repeat });
}

/** 删除闹钟 */
export async function deleteAlarm(alarmId: string): Promise<void> {
  return tauriCommand("delete_alarm", { alarmId });
}

// ==================== 电台相关 ====================

/** 获取电台列表 */
export async function getRadioList(): Promise<Array<{ id: string; title: string; url: string; cover: string }>> {
  return tauriCommand("get_radio_list");
}

/** 播放电台 */
export async function playRadio(radioId: string): Promise<void> {
  return tauriCommand("play_radio", { radioId });
}

// ==================== 激活码相关 ====================

/** 激活码验证 */
export async function activateCode(code: string): Promise<ActivationResult> {
  return tauriCommand("activate_code", { code });
}

// ==================== 本地模型相关 ====================

/** 获取本地模型列表 */
export async function getLocalModels(): Promise<Array<{ id: string; name: string; path: string; size: number }>> {
  return tauriCommand("get_local_models");
}

/** 下载本地模型 */
export async function downloadLocalModel(modelId: string): Promise<void> {
  return tauriCommand("download_local_model", { modelId });
}

/** 删除本地模型 */
export async function deleteLocalModel(modelId: string): Promise<void> {
  return tauriCommand("delete_local_model", { modelId });
}

// ==================== 皮肤商店相关 ====================

/** 获取皮肤列表 */
export async function getSkinList(): Promise<Array<{ id: string; name: string; preview: string; price: number; owned: boolean }>> {
  return tauriCommand("get_skin_list");
}

/** 购买皮肤 */
export async function purchaseSkin(skinId: string): Promise<PurchaseResult> {
  return tauriCommand("purchase_skin", { skinId });
}

// ==================== 模型管理相关 ====================

/** 获取Live2D模型列表 */
export async function getModelList(): Promise<Array<{ id: string; name: string; path: string; thumbnail: string }>> {
  return tauriCommand("list_models");
}

/** 导入模型 */
export async function importModel(path: string): Promise<void> {
  return tauriCommand("import_model", { path });
}

/** 删除模型 */
export async function deleteModel(modelId: string): Promise<void> {
  return tauriCommand("delete_model", { modelId });
}
