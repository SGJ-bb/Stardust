import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { useDiaryStore } from "@/stores/useDiaryStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ArrowLeft, Plus, BookOpen, Trash2, Edit3, CalendarDays, Smile, ChevronRight } from "lucide-react";
import { formatDate } from "@/lib/utils";
import { useState } from "react";
import { isTauri } from "@/lib/tauri";

/** 心情映射：emoji + 颜色 */
const MOOD_MAP: Record<string, { emoji: string; color: string }> = {
  "开心": { emoji: "😊", color: "#fbbf24" },
  "快乐": { emoji: "😄", color: "#f59e0b" },
  "平静": { emoji: "😌", color: "#60a5fa" },
  "难过": { emoji: "😢", color: "#60a5fa" },
  "悲伤": { emoji: "😭", color: "#3b82f6" },
  "愤怒": { emoji: "😠", color: "#ef4444" },
  "焦虑": { emoji: "😰", color: "#f97316" },
  "期待": { emoji: "🤩", color: "#a855f7" },
  "感动": { emoji: "🥹", color: "#ec4899" },
  "疲惫": { emoji: "😫", color: "#78716c" },
};

/**
 * 日记页面
 * 对应Android DiaryActivity
 */
export function DiaryPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();
  const { diaries, addDiary, deleteDiary } = useDiaryStore();
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [mood, setMood] = useState("");

  /** 按当前角色过滤日记 */
  const filteredDiaries = personaId
    ? diaries.filter((d) => d.personaId === personaId)
    : diaries;

  /** 按日期倒序排列 */
  const sortedDiaries = [...filteredDiaries].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );

  /** 当前选中的日记 */
  const selectedDiary = sortedDiaries.find((d) => d.id === selectedId) ?? sortedDiaries[0] ?? null;

  /** 默认选中第一篇 */
  if (!selectedId && sortedDiaries.length > 0 && !selectedDiary) {
    // 在首次渲染时设置默认选中
    setTimeout(() => setSelectedId(sortedDiaries[0].id), 0);
  }

  const handleCreate = () => {
    if (!personaId || !title.trim()) return;
    addDiary(personaId, title.trim(), content.trim(), mood.trim());
    setTitle("");
    setContent("");
    setMood("");
    setShowCreateDialog(false);
  };

  return (
    <PageContainer>
      <div className="page-content tea-paper">
        {/* ====== 顶部区域 ====== */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-primary)]">
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-2xl font-bold bg-[var(--theme-gradient)] bg-clip-text text-transparent">
              日记本
            </h1>
          </div>
          {/* 新建日记按钮 */}
          <Button
            onClick={() => setShowCreateDialog(true)}
            className="rounded-xl bg-[var(--color-primary)] hover:bg-[var(--color-primary)]/90 text-white shadow-lg shadow-[var(--color-primary)]/25 transition-all duration-300 hover:scale-105"
          >
            <Plus className="h-4 w-4 mr-2" />
            写日记
          </Button>
        </div>

        {/* ====== 主内容区：左侧列表 + 右侧详情 ====== */}
        <div className="flex gap-6 min-h-[calc(100vh-200px)]">
          {/* 左侧：日记列表 */}
          <div className="w-72 shrink-0 hidden md:block">
            <ScrollArea className="h-[calc(100vh-220px)] pr-2">
              {sortedDiaries.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-center px-4">
                  <BookOpen className="h-12 w-12 text-[var(--color-muted-foreground)]/40 mb-3 animate-pulse" />
                  <p className="text-sm text-[var(--color-muted-foreground)]">还没有日记</p>
                  <p className="text-xs text-[var(--color-muted-foreground)]/60 mt-1">点击右上角开始记录</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {sortedDiaries.map((diary) => (
                    <button
                      key={diary.id}
                      onClick={() => setSelectedId(diary.id)}
                      className={`w-full text-left p-4 rounded-xl transition-all duration-300 group ${
                        selectedId === diary.id
                          ? "bg-[var(--color-primary)]/10 border border-[var(--color-primary)]/30 shadow-md"
                          : "bg-[var(--color-card)]/50 border border-[var(--color-border)]/20 hover:bg-[var(--color-card)]/80 hover:border-[var(--color-border)]/40"
                      }`}
                    >
                      <div className="flex items-start justify-between mb-2">
                        <span className={`text-xs flex items-center gap-1.5 ${
                          selectedId === diary.id ? "text-[var(--color-primary)]" : "text-[var(--color-muted-foreground)]"
                        }`}>
                          <CalendarDays className="h-3 w-3" />
                          {formatDate(diary.createdAt)}
                        </span>
                        <ChevronRight className={`h-3.5 w-3.5 transition-all ${
                          selectedId === diary.id ? "text-[var(--color-primary)] translate-x-0" : "text-transparent group-hover:text-[var(--color-muted-foreground)] group-hover:translate-x-0.5"
                        }`} />
                      </div>
                      <h3 className={`text-sm font-medium line-clamp-1 mb-1.5 ${
                        selectedId === diary.id ? "text-[var(--color-card-foreground)]" : "text-[var(--color-card-foreground)]/80"
                      }`}>
                        {diary.title || "无题"}
                      </h3>
                      <p className="text-xs text-[var(--color-muted-foreground)] line-clamp-2 leading-relaxed">
                        {diary.content || "暂无内容..."}
                      </p>
                      {/* 心情 emoji */}
                      {diary.mood && (
                        <div className="flex items-center gap-1.5 mt-2 pt-2 border-t border-[var(--color-border)]/10">
                          <Smile className="h-3 w-3 text-[var(--color-muted-foreground)]" />
                          <span className="text-base">{MOOD_MAP[diary.mood]?.emoji || diary.mood}</span>
                          <span className="text-[10px] text-[var(--color-muted-foreground)]">{diary.mood}</span>
                        </div>
                      )}
                    </button>
                  ))}
                </div>
              )}
            </ScrollArea>
          </div>

          {/* 右侧：日记详情 */}
          <div className="flex-1 min-w-0">
            {!selectedDiary ? (
              <div className="flex flex-col items-center justify-center h-full py-20">
                <BookOpen className="h-20 w-20 text-[var(--color-muted-foreground)]/25 mb-4" />
                <p className="text-[var(--color-muted-foreground)]">选择一篇日记查看</p>
              </div>
            ) : (
              <div className="tea-warmth rounded-2xl p-8 bg-gradient-to-br from-[var(--color-card)]/70 via-[var(--color-card)]/50 to-[var(--color-card)]/30 backdrop-blur-xl border border-[var(--color-border)]/20 h-full">
                {/* 顶部操作栏 */}
                <div className="flex items-center justify-between mb-6 pb-4 border-b border-[var(--color-border)]/10">
                  <div className="flex items-center gap-3">
                    {/* 心情指示器 */}
                    {selectedDiary.mood && (
                      <>
                        <span className="text-2xl">{MOOD_MAP[selectedDiary.mood]?.emoji || selectedDiary.mood}</span>
                        <div
                          className="w-2.5 h-2.5 rounded-full"
                          style={{ backgroundColor: MOOD_MAP[selectedDiary.mood]?.color || "#94a3b8" }}
                        />
                      </>
                    )}
                    <span className="text-xs text-[var(--color-muted-foreground)] flex items-center gap-1.5">
                      <CalendarDays className="h-3.5 w-3.5" />
                      {formatDate(selectedDiary.createdAt)}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button variant="ghost" size="icon" className="text-[var(--color-muted-foreground)] hover:text-[var(--color-primary)]">
                      <Edit3 className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => deleteDiary(selectedDiary.id)}
                      className="text-[var(--color-muted-foreground)] hover:text-red-400"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>

                {/* 日期标题（大字渐变） */}
                <h2 className="text-3xl font-bold mb-6 bg-[var(--theme-gradient)] bg-clip-text text-transparent leading-tight">
                  {selectedDiary.title || "无题"}
                </h2>

                {/* 正文内容区（玻璃拟态背景） */}
                <div className="glass-card rounded-2xl p-6 bg-white/[0.03] backdrop-blur-sm border border-white/[0.06] shadow-inner">
                  <div className="prose prose-sm max-w-none">
                    {(selectedDiary.content || "暂无内容...").split("\n").map((line, i) => (
                      <p key={i} className="text-[var(--color-card-foreground)]/85 leading-loose text-base mb-3 last:mb-0 indent-8">
                        {line || "\u00A0"}
                      </p>
                    ))}
                  </div>
                </div>

                {/* 底部装饰线 */}
                <div className="mt-6 pt-4 border-t border-dashed border-[var(--color-border)]/15">
                  <p className="text-[10px] text-[var(--color-muted-foreground)]/50 text-center tracking-widest">
                    · · · 记录此刻 · · ·
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 创建日记对话框 */}
      <Dialog open={showCreateDialog} onOpenChange={setShowCreateDialog}>
        <DialogContent className="sm:max-w-lg glass-panel border-[var(--color-border)]/30 bg-[var(--color-card)]/95 backdrop-blur-xl">
          <DialogHeader>
            <DialogTitle className="text-[var(--color-card-foreground)] flex items-center gap-2">
              <Plus className="h-5 w-5 text-[var(--color-primary)]" />
              写日记
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="给今天起个标题..."
              className="rounded-xl border-[var(--color-border)]/50 bg-[var(--color-muted)]/30 focus:border-[var(--color-primary)]"
            />
            <Input
              value={mood}
              onChange={(e) => setMood(e.target.value)}
              placeholder="今天的心情 (如：开心、平静...)"
              className="rounded-xl border-[var(--color-border)]/50 bg-[var(--color-muted)]/30 focus:border-[var(--color-primary)]"
            />
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="写下今天的故事..."
              rows={6}
              className="rounded-xl border-[var(--color-border)]/50 bg-[var(--color-muted)]/30 focus:border-[var(--color-primary)] resize-none leading-relaxed"
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setShowCreateDialog(false)}
              className="rounded-xl border-[var(--color-border)]/50"
            >
              取消
            </Button>
            <Button
              onClick={handleCreate}
              disabled={!title.trim()}
              className="rounded-xl bg-[var(--color-primary)] hover:bg-[var(--color-primary)]/90 text-white"
            >
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
