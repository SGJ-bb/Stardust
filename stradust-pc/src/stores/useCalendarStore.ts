import { create } from "zustand";
import type { CalendarEvent } from "@/types/calendar";
import { listEvents, addEvent as engineAddEvent, deleteEvent as engineDeleteEvent, getTodayEventsFull, getSolarTerm } from "@/engine/calendar";
import { on } from "@/lib/events";
import type { CalendarRecord } from "@/engine/kinds";

/** 引擎 CalendarRecord → 页面 CalendarEvent（date 映射 startTime，复制自门面 engineToPageEvent 逻辑） */
function engineToPageEvent(e: CalendarRecord): CalendarEvent {
  const start = new Date(`${e.date}T09:00:00`).getTime();
  return {
    id: e.id, personaId: e.personaId ?? "", title: e.title, description: "",
    startTime: start, endTime: start + 3600_000, allDay: true,
    recurrence: e.recurring ? "yearly" : undefined, reminderMinutes: 0, color: "",
    type: e.type === "birthday" ? "birthday" : e.type === "anniversary" ? "anniversary" : "custom",
    createdAt: e.createdAt, updatedAt: e.createdAt,
  };
}

/** 今日节日/节气信息 */
export interface TodayInfo {
  /** 今日事件文案（用户事件 + 系统节日/节气） */
  items: Array<{ title: string; detail?: string; kind: "user" | "system" }>;
  /** 今日节气名 */
  solarTerm: string | null;
}

/** 当前列表所属角色（load 无参调用时沿用），模块级保存 */
let scopedPersonaId = "";

interface CalendarState {
  /** 日历事件列表 */
  events: CalendarEvent[];
  /** 当前选中日期 */
  selectedDate: string;
  /** 今日节日/节气信息 */
  todayInfo: TodayInfo;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 从引擎加载事件与今日信息 */
  load: (personaId?: string) => Promise<void>;
  /** 设置事件列表 */
  setEvents: (events: CalendarEvent[]) => void;
  /** 设置选中日期 */
  setSelectedDate: (date: string) => void;
  /** 添加事件（落库引擎） */
  addEvent: (event: Omit<CalendarEvent, "id" | "createdAt" | "updatedAt">) => Promise<void>;
  /** 删除事件 */
  deleteEvent: (eventId: string) => Promise<void>;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useCalendarStore = create<CalendarState>()((set, get) => ({
  events: [],
  selectedDate: new Date().toISOString().split("T")[0],
  todayInfo: { items: [], solarTerm: null },
  isLoading: false,

  load: async (personaId) => {
    if (personaId !== undefined) scopedPersonaId = personaId;
    set({ isLoading: true });
    try {
      const pid = personaId ?? (scopedPersonaId || undefined);
      const [records, todayItems] = await Promise.all([
        listEvents(pid),
        getTodayEventsFull(),
      ]);
      set({
        events: records.map(engineToPageEvent),
        todayInfo: { items: todayItems, solarTerm: getSolarTerm(new Date()) },
        isLoading: false,
      });
    } catch {
      set({ isLoading: false });
    }
  },

  setEvents: (events) => set({ events }),
  setSelectedDate: (date) => set({ selectedDate: date }),

  addEvent: async (eventData) => {
    await engineAddEvent({
      date: new Date(eventData.startTime).toISOString().slice(0, 10),
      title: eventData.title,
      type: eventData.type === "birthday" ? "birthday" : eventData.type === "anniversary" ? "anniversary" : "user",
      recurring: !!eventData.recurrence,
      personaId: eventData.personaId || undefined,
    });
    await get().load(eventData.personaId);
  },

  deleteEvent: async (eventId) => {
    await engineDeleteEvent(eventId);
    set((state) => ({
      events: state.events.filter((e) => e.id !== eventId),
    }));
  },

  setLoading: (loading) => set({ isLoading: loading }),
}));

// 引擎数据变化 → 节流重拉（calendar.addEvent 等只发通用 data-changed）
let reloadTimer: number | null = null;
on("data-changed", () => {
  if (reloadTimer !== null) return;
  reloadTimer = window.setTimeout(() => {
    reloadTimer = null;
    void useCalendarStore.getState().load();
  }, 300);
});
