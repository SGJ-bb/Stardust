import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { CalendarEvent } from "@/types/calendar";
import { generateId } from "@/lib/utils";

interface CalendarState {
  /** 日历事件列表 */
  events: CalendarEvent[];
  /** 当前选中日期 */
  selectedDate: string;
  /** 是否正在加载 */
  isLoading: boolean;

  /** 设置事件列表 */
  setEvents: (events: CalendarEvent[]) => void;
  /** 设置选中日期 */
  setSelectedDate: (date: string) => void;
  /** 添加事件 */
  addEvent: (event: Omit<CalendarEvent, "id" | "createdAt" | "updatedAt">) => void;
  /** 删除事件 */
  deleteEvent: (eventId: string) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
}

export const useCalendarStore = create<CalendarState>()(
  persist(
    (set) => ({
      events: [],
      selectedDate: new Date().toISOString().split("T")[0],
      isLoading: false,

      setEvents: (events) => set({ events }),
      setSelectedDate: (date) => set({ selectedDate: date }),

      addEvent: (eventData) => {
        const event: CalendarEvent = {
          ...eventData,
          id: generateId(),
          createdAt: Date.now(),
          updatedAt: Date.now(),
        };
        set((state) => ({ events: [...state.events, event] }));
      },

      deleteEvent: (eventId) =>
        set((state) => ({
          events: state.events.filter((e) => e.id !== eventId),
        })),

      setLoading: (loading) => set({ isLoading: loading }),
    }),
    {
      name: "stradust-calendar",
      partialize: (state) => ({
        events: state.events,
        selectedDate: state.selectedDate,
      }),
    }
  )
);
