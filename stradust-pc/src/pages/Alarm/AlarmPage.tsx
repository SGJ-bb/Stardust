import { useNavigate } from "react-router";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Switch } from "@/components/ui/switch";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ArrowLeft, AlarmClock, Plus, Trash2 } from "lucide-react";
import { useState, useEffect } from "react";
import { getAlarms, createAlarm, deleteAlarm as deleteAlarmApi } from "@/lib/tauri";
import { useTauriEvent } from "@/hooks/useTauriEvent";
import { isTauri } from "@/lib/tauri";
import type { CalendarEvent } from "@/types/calendar";

/**
 * 闹钟页面
 * 对应Android AlarmActivity
 * 支持系统通知、闹钟触发提醒
 */
export function AlarmPage() {
  const navigate = useNavigate();
  const [alarms, setAlarms] = useState<CalendarEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [newTime, setNewTime] = useState("07:00");
  const [newLabel, setNewLabel] = useState("");
  const [newRepeat, setNewRepeat] = useState("每天");

  /** 从后端加载闹钟列表 */
  useEffect(() => {
    getAlarms()
      .then((list) => setAlarms(list ?? []))
      .catch((error) => console.error("加载闹钟列表失败:", error))
      .finally(() => setIsLoading(false));
  }, []);

  /** 监听闹钟触发事件，发送系统通知 */
  useTauriEvent<{ alarmId: string; label: string; time: string }>("alarm:triggered", async (payload) => {
    if (!isTauri()) return;
    try {
      const { sendNotification, requestPermission } = await import("@tauri-apps/plugin-notification");
      const permission = await requestPermission();
      if (permission === "granted") {
        sendNotification({
          title: "闹钟提醒",
          body: payload.label || `闹钟响了 (${payload.time})`,
        });
      }
    } catch (error) {
      console.error("发送闹钟通知失败:", error);
    }
  });

  /** 创建闹钟 */
  const handleCreate = async () => {
    try {
      const alarm = await createAlarm(newTime, newLabel, newRepeat);
      setAlarms((prev) => [...prev, alarm]);
      setShowCreateDialog(false);
      setNewTime("07:00");
      setNewLabel("");
      setNewRepeat("每天");
    } catch (error) {
      console.error("创建闹钟失败:", error);
    }
  };

  const toggleAlarm = (id: string) => {
    setAlarms((prev) =>
      prev.map((a) => (a.id === id ? { ...a, allDay: !a.allDay } : a))
    );
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteAlarmApi(id);
      setAlarms((prev) => prev.filter((a) => a.id !== id));
    } catch (error) {
      console.error("删除闹钟失败:", error);
    }
  };

  return (
    <PageContainer>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={() => navigate(-1)}>
            <ArrowLeft className="h-4 w-4 mr-2" />
            返回
          </Button>
          <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">闹钟</h1>
        </div>
        <Button onClick={() => setShowCreateDialog(true)}>
          <Plus className="h-4 w-4 mr-2" />
          添加闹钟
        </Button>
      </div>

      <ScrollArea className="flex-1">
        {alarms.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20">
            <AlarmClock className="h-12 w-12 text-[var(--color-muted-foreground)] mb-4 empty-state-icon" />
            <p className="text-[var(--color-muted-foreground)]">暂无闹钟</p>
          </div>
        ) : (
          <div className="space-y-3">
            {alarms.map((alarm) => (
              <Card key={alarm.id} className={!alarm.allDay ? "opacity-60" : ""}>
                <CardContent className="flex items-center justify-between p-4">
                  <div>
                    <div className="text-2xl font-bold text-[var(--color-card-foreground)]">{alarm.title}</div>
                    <p className="text-sm text-[var(--color-muted-foreground)]">{alarm.description}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <Switch checked={alarm.allDay} onCheckedChange={() => toggleAlarm(alarm.id)} />
                    <Button variant="ghost" size="icon" onClick={() => handleDelete(alarm.id)}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </ScrollArea>

      {/* 创建闹钟对话框 */}
      <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>添加闹钟</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <Input type="time" value={newTime} onChange={(e) => setNewTime(e.target.value)} />
            <Input value={newLabel} onChange={(e) => setNewLabel(e.target.value)} placeholder="闹钟标签" />
            <Input value={newRepeat} onChange={(e) => setNewRepeat(e.target.value)} placeholder="重复（如：每天、工作日）" />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCreateDialog(false)}>取消</Button>
            <Button onClick={handleCreate} disabled={!newLabel.trim()}>添加</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
