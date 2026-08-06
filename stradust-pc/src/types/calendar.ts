/** 日历事件 */
export interface CalendarEvent {
  id: string;
  personaId: string;
  title: string;
  description: string;
  /** 开始时间 */
  startTime: number;
  /** 结束时间 */
  endTime: number;
  /** 是否全天 */
  allDay: boolean;
  /** 重复规则 */
  recurrence?: "daily" | "weekly" | "monthly" | "yearly";
  /** 提醒时间(分钟前) */
  reminderMinutes: number;
  /** 颜色标记 */
  color: string;
  /** 事件类型 */
  type: "anniversary" | "birthday" | "reminder" | "alarm" | "custom";
  createdAt: number;
  updatedAt: number;
}
