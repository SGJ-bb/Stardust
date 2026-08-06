import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { useCalendarStore } from "@/stores/useCalendarStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  ArrowLeft, Plus, Calendar as CalendarIcon, Trash2,
  ChevronLeft, ChevronRight, CalendarDays
} from "lucide-react";
import { formatDate } from "@/lib/utils";
import { useState, useMemo } from "react";
import type { CalendarEvent } from "@/types/calendar";

/** 获取今日日期字符串 YYYY-MM-DD */
function getTodayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * 日历页面
 * 对应Android CalendarActivity
 */
export function CalendarPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();
  const { events, selectedDate, setSelectedDate, addEvent, deleteEvent } = useCalendarStore();
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [eventType, setEventType] = useState<CalendarEvent["type"]>("custom");

  /** 当前月份的天数 */
  const currentDate = useMemo(() => new Date(selectedDate), [selectedDate]);
  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const firstDayOfWeek = new Date(year, month, 1).getDay();

  /** 今日标记 */
  const todayStr = getTodayStr();

  /** 当月事件 */
  const monthEvents = useMemo(() => {
    return events.filter((e) => {
      const d = new Date(e.startTime);
      return d.getFullYear() === year && d.getMonth() === month;
    });
  }, [events, year, month]);

  /** 当日事件 */
  const dayEvents = useMemo(() => {
    return monthEvents.filter(
      (e) => new Date(e.startTime).toDateString() === new Date(selectedDate).toDateString()
    );
  }, [monthEvents, selectedDate]);

  const handlePrevMonth = () => {
    const d = new Date(year, month - 1, 1);
    setSelectedDate(d.toISOString().split("T")[0]);
  };

  const handleNextMonth = () => {
    const d = new Date(year, month + 1, 1);
    setSelectedDate(d.toISOString().split("T")[0]);
  };

  const handleCreate = () => {
    if (!personaId || !title.trim()) return;
    addEvent({
      personaId,
      title: title.trim(),
      description: description.trim(),
      startTime: new Date(selectedDate).getTime(),
      endTime: new Date(selectedDate).getTime() + 3600000,
      allDay: false,
      reminderMinutes: 15,
      color: "#f472b6",
      type: eventType,
    });
    setTitle("");
    setDescription("");
    setShowCreateDialog(false);
  };

  /** 事件类型中文映射 */
  const eventTypeLabel: Record<CalendarEvent["type"], string> = {
    anniversary: "纪念日",
    birthday: "生日",
    reminder: "提醒",
    alarm: "闹钟",
    custom: "自定义",
  };

  return (
    <PageContainer className="page-content">
      {/* ====== 顶部导航栏 ====== */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium
              text-[var(--color-card-foreground)] hover:bg-[var(--color-muted)]
              border border-[color-mix(in_srgb,var(--color-border)_20%,transparent)]
              transition-all duration-200 hover-lift"
          >
            <ArrowLeft className="h-4 w-4" />
            返回
          </button>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: "var(--color-card-foreground)" }}>
            日历
          </h1>
        </div>
        <Button
          onClick={() => setShowCreateDialog(true)}
          className="gap-2"
          style={{
            background: "var(--theme-gradient)",
            borderColor: "transparent",
          }}
        >
          <Plus className="h-4 w-4" />
          添加事件
        </Button>
      </div>

      {/* ====== 月份切换面板 (glass-panel) ====== */}
      <div
        className="glass-panel flex items-center justify-between rounded-xl px-5 py-3.5 mb-5"
      >
        <button
          onClick={handlePrevMonth}
          className="p-2 rounded-lg transition-all duration-200
            text-[var(--color-card-foreground)] hover:bg-[color-mix(in_srgb,var(--color-primary)_12%,transparent)]
            hover:text-[var(--color-primary)]"
        >
          <ChevronLeft className="h-5 w-5" />
        </button>
        <h2
          className="text-xl font-semibold tracking-wide"
          style={{ color: "var(--color-card-foreground)" }}
        >
          {year}年{month + 1}月
        </h2>
        <button
          onClick={handleNextMonth}
          className="p-2 rounded-lg transition-all duration-200
            text-[var(--color-card-foreground)] hover:bg-[color-mix(in_srgb,var(--color-primary)_12%,transparent)]
            hover:text-[var(--color-primary)]"
        >
          <ChevronRight className="h-5 w-5" />
        </button>
      </div>

      {/* ====== 日历网格 (glass-card) ====== */}
      <div className="glass-card rounded-2xl p-5 mb-6">
        {/* 星期头 */}
        <div className="grid grid-cols-7 gap-1 mb-3">
          {["日", "一", "二", "三", "四", "五", "六"].map((day) => (
            <div
              key={day}
              className="text-center text-xs font-semibold py-2"
              style={{ color: "var(--color-muted-foreground)" }}
            >
              {day}
            </div>
          ))}
        </div>

        {/* 日期格子 */}
        <div className="grid grid-cols-7 gap-1">
          {/* 空白占位 */}
          {Array.from({ length: firstDayOfWeek }).map((_, i) => (
            <div key={`empty-${i}`} className="h-11" />
          ))}

          {/* 日期按钮 */}
          {Array.from({ length: daysInMonth }).map((_, i) => {
            const day = i + 1;
            const dateStr = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
            const isSelected = dateStr === selectedDate;
            const isToday = dateStr === todayStr;
            const hasEvents = monthEvents.some((e) => new Date(e.startTime).getDate() === day);

            return (
              <button
                key={day}
                onClick={() => setSelectedDate(dateStr)}
                className={`
                  relative h-11 rounded-xl text-sm font-medium flex flex-col items-center justify-center
                  transition-all duration-200 cursor-pointer
                  ${isSelected ? "ring-2 ring-offset-0 shadow-md" : ""}
                `}
                style={{
                  // 基础样式
                  color:
                    isSelected
                      ? "var(--color-primary-foreground)"
                      : isToday
                        ? "var(--color-primary)"
                        : "var(--color-card-foreground)",
                  background: isSelected
                    ? "var(--theme-gradient)"
                    : isToday
                      ? "color-mix(in srgb, var(--color-primary) 10%, transparent)"
                      : hasEvents
                        ? "color-mix(in srgb, var(--color-muted) 60%, transparent)"
                        : "transparent",
                  border: isSelected
                    ? "none"
                    : `1px solid color-mix(in srgb, var(--color-border) 20%, transparent)`,
                  boxShadow: isSelected
                    ? "var(--theme-glow-strong)"
                    : "none",
                  borderRadius: "var(--radius-md)",
                }}
              >
                <span>{day}</span>
                {/* 事件小圆点 */}
                {hasEvents && !isSelected && (
                  <span
                    className="absolute bottom-1 left-1/2 -translate-x-1/2 h-1.5 w-1.5 rounded-full"
                    style={{
                      background: "var(--color-primary)",
                      boxShadow: "0 0 4px var(--theme-glow)",
                    }}
                  />
                )}
                {/* 选中状态的事件指示器 */}
                {hasEvents && isSelected && (
                  <span
                    className="absolute bottom-1 left-1/2 -translate-x-1/2 h-1.5 w-1.5 rounded-full"
                    style={{
                      background: "rgba(255,255,255,0.85)",
                    }}
                  />
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* ====== 底部今日事件列表 (chinese-border / glass-card) ====== */}
      <div className="glass-card chinese-border rounded-2xl p-5 flex-1 min-h-0 flex flex-col">
        <div className="flex items-center gap-2 mb-4 pb-3" style={{
          borderBottom: "1px solid color-mix(in srgb, var(--color-border) 20%, transparent)",
        }}>
          <CalendarIcon className="h-4 w-4" style={{ color: "var(--color-primary)" }} />
          <h3 className="text-sm font-semibold" style={{ color: "var(--color-card-foreground)" }}>
            {formatDate(new Date(selectedDate).getTime())} 的日程
          </h3>
          <span
            className="ml-auto text-xs px-2 py-0.5 rounded-full"
            style={{
              background: "color-mix(in srgb, var(--color-primary) 12%, transparent)",
              color: "var(--color-primary)",
            }}
          >
            {dayEvents.length} 个事件
          </span>
        </div>

        <ScrollArea className="flex-1 min-h-0">
          {dayEvents.length === 0 ? (
            /* ====== 空状态：日历图标 + 暂无日程 ====== */
            <div className="flex flex-col items-center justify-center py-12 gap-4">
              <CalendarDays
                className="empty-state-icon h-16 w-16 opacity-30"
                style={{ color: "var(--color-muted-foreground)" }}
              />
              <p className="text-sm font-medium" style={{ color: "var(--color-muted-foreground)" }}>
                暂无日程
              </p>
              <p className="text-xs mt-[-8px]" style={{ color: "var(--color-muted-foreground)", opacity: 0.65 }}>
                点击上方「添加事件」开始规划你的一天
              </p>
            </div>
          ) : (
            <div className="space-y-3 pr-2">
              {dayEvents.map((event) => (
                <div
                  key={event.id}
                  className="group flex items-center justify-between p-3.5 rounded-xl transition-all duration-200 hover-lift"
                  style={{
                    background: "color-mix(in srgb, var(--color-card) 8%, transparent)",
                    border: "1px solid color-mix(in srgb, var(--color-border) 20%, transparent)",
                  }}
                >
                  <div className="flex items-start gap-3 min-w-0 flex-1">
                    {/* 左侧颜色条 */}
                    <div
                      className="w-1 h-10 rounded-full shrink-0 mt-0.5"
                      style={{ background: event.color || "var(--color-primary)" }}
                    />
                    <div className="min-w-0 flex-1">
                      <h4
                        className="text-sm font-medium truncate"
                        style={{ color: "var(--color-card-foreground)" }}
                      >
                        {event.title}
                      </h4>
                      {event.description && (
                        <p
                          className="text-xs mt-1 line-clamp-2"
                          style={{ color: "var(--color-muted-foreground)" }}
                        >
                          {event.description}
                        </p>
                      )}
                      <span
                        className="inline-block text-[10px] font-medium mt-1.5 px-2 py-0.5 rounded-full"
                        style={{
                          background: "color-mix(in srgb, var(--color-muted) 70%, transparent)",
                          color: "var(--color-muted-foreground)",
                          border: "1px solid color-mix(in srgb, var(--color-border) 20%, transparent)",
                        }}
                      >
                        {eventTypeLabel[event.type]}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => deleteEvent(event.id)}
                    className="shrink-0 p-2 rounded-lg opacity-0 group-hover:opacity-100
                      transition-all duration-200
                      text-[var(--color-destructive)] hover:bg-[color-mix(in_srgb,var(--color-destructive)_10%,transparent)]"
                    title="删除事件"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </ScrollArea>
      </div>

      {/* ====== 创建事件对话框 ====== */}
      <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
        <DialogContent
          className="sm:max-w-md glass-card"
          style={{
            border: "1px solid color-mix(in srgb, var(--color-border) 20%, transparent)",
          }}
        >
          <DialogHeader>
            <DialogTitle style={{ color: "var(--color-card-foreground)" }}>
              添加事件
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="事件标题"
              className="input-glow"
              style={{
                background: "color-mix(in srgb, var(--color-card) 50%, transparent)",
                borderColor: "color-mix(in srgb, var(--color-border) 30%, transparent)",
                color: "var(--color-card-foreground)",
              }}
            />
            <Textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="事件描述（可选）"
              rows={2}
              className="input-glow resize-none"
              style={{
                background: "color-mix(in srgb, var(--color-card) 50%, transparent)",
                borderColor: "color-mix(in srgb, var(--color-border) 30%, transparent)",
                color: "var(--color-card-foreground)",
              }}
            />
            <div className="flex gap-2 flex-wrap">
              {(["anniversary", "birthday", "reminder", "alarm", "custom"] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setEventType(t)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-200 ${
                    eventType === t ? "" : ""
                  }`}
                  style={
                    eventType === t
                      ? {
                          background: "var(--theme-gradient)",
                          color: "var(--color-primary-foreground)",
                          boxShadow: "var(--theme-glow-strong)",
                        }
                      : {
                          background: "color-mix(in srgb, var(--color-muted) 70%, transparent)",
                          color: "var(--color-muted-foreground)",
                          border: "1px solid color-mix(in srgb, var(--color-border) 20%, transparent)",
                        }
                  }
                >
                  {eventTypeLabel[t]}
                </button>
              ))}
            </div>
          </div>
          <DialogFooter className="gap-2">
            <button
              onClick={() => setShowCreateDialog(false)}
              className="px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200"
              style={{
                background: "color-mix(in srgb, var(--color-muted) 70%, transparent)",
                color: "var(--color-card-foreground)",
                border: "1px solid color-mix(in srgb, var(--color-border) 20%, transparent)",
              }}
            >
              取消
            </button>
            <button
              onClick={handleCreate}
              disabled={!title.trim()}
              className="px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed"
              style={
                title.trim()
                  ? {
                      background: "var(--theme-gradient)",
                      color: "var(--color-primary-foreground)",
                      boxShadow: "var(--theme-glow-strong)",
                    }
                  : {
                      background: "color-mix(in srgb, var(--color-muted) 50%, transparent)",
                      color: "var(--color-muted-foreground)",
                    }
              }
            >
              添加
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
