import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { useMemoryStore } from "@/stores/useMemoryStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { ArrowLeft, Search, Brain, Archive, Star, Filter, TrendingUp, Clock, Heart } from "lucide-react";
import { useState, useMemo, useEffect } from "react";
import { formatTime } from "@/lib/utils";
import type { MemoryType, MemoryImportance } from "@/types/memory";

/** 分类选项 */
const CATEGORIES = [
  { key: "all", label: "全部" },
  { key: "important", label: "重要" },
  { key: "daily", label: "日常" },
  { key: "emotion", label: "情感" },
] as const;

type CategoryKey = (typeof CATEGORIES)[number]["key"];

/**
 * 记忆页面
 * 对应Android MemoryActivity
 */
export function MemoryPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();
  const { currentMemory, searchQuery, setSearchQuery, setCurrentMemory } = useMemoryStore();
  const [activeCategory, setActiveCategory] = useState<CategoryKey>("all");

  /** 进入页面时初始化记忆数据 */
  useEffect(() => {
    if (personaId) {
      // 如果当前 memory 不属于这个 personaId，创建新的空记忆
      if (!currentMemory || currentMemory.personaId !== personaId) {
        setCurrentMemory({
          id: `mem-${personaId}`,
          personaId,
          shortTerm: [],
          longTerm: [],
          core: [],
          pools: [],
          totalCount: 0,
          updatedAt: Date.now(),
        });
      }
    }
  }, [personaId, currentMemory?.personaId, setCurrentMemory]);

  /** 合并所有记忆条目并附加来源信息 */
  const allEntries = useMemo(() => {
    const entries: Array<{
      id: string;
      content: string;
      type: MemoryType;
      importance: MemoryImportance;
      createdAt: number | string;
      source: "short" | "long" | "core";
    }> = [];

    (currentMemory?.shortTerm ?? []).forEach((e) => entries.push({ ...e, source: "short" }));
    (currentMemory?.longTerm ?? []).forEach((e) => entries.push({ ...e, source: "long" }));
    (currentMemory?.core ?? []).forEach((e) => entries.push({ ...e, source: "core" }));

    return entries.sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );
  }, [currentMemory]);

  /** 按分类过滤 */
  const filteredEntries = useMemo(() => {
    let result = allEntries;

    // 搜索过滤
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter((e) => e.content.toLowerCase().includes(q));
    }

    // 分类过滤
    if (activeCategory === "important") {
      result = result.filter((e) => e.importance === "high" || e.importance === "critical");
    } else if (activeCategory === "daily") {
      result = result.filter((e) => e.type === "fact" || e.type === "event");
    } else if (activeCategory === "emotion") {
      result = result.filter((e) => e.type === "emotion" || e.type === "preference");
    }

    return result;
  }, [allEntries, searchQuery, activeCategory]);

  /** 统计数据 */
  const stats = useMemo(() => ({
    total: allEntries.length,
    important: allEntries.filter((e) => e.importance === "high" || e.importance === "critical").length,
    today: allEntries.filter((e) => {
      const d = new Date(e.createdAt);
      const now = new Date();
      return d.toDateString() === now.toDateString();
    }).length,
    sources: {
      short: (currentMemory?.shortTerm ?? []).length,
      long: (currentMemory?.longTerm ?? []).length,
      core: (currentMemory?.core ?? []).length,
    },
  }), [allEntries, currentMemory]);

  const typeLabels: Record<MemoryType, string> = {
    fact: "事实",
    event: "事件",
    preference: "偏好",
    emotion: "情感",
    relationship: "关系",
  };

  const isImportant = (importance: MemoryImportance) =>
    importance === "high" || importance === "critical";

  return (
    <PageContainer>
      <div className="page-content">
        {/* ====== 顶部区域：标题 + 搜索 ====== */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-primary)]">
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-2xl font-bold bg-[var(--theme-gradient)] bg-clip-text text-transparent">
              记忆库
            </h1>
          </div>
        </div>

        {/* 搜索框 - input-glow 样式 */}
        <div className="relative mb-6">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--color-muted-foreground)]" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="搜索记忆..."
            className="input-glow pl-11 h-11 rounded-xl border-[var(--color-border)] bg-[var(--color-card)]/60 backdrop-blur-sm text-[var(--color-card-foreground)] placeholder:text-[var(--color-muted-foreground)] focus:border-[var(--color-primary)] transition-all duration-300"
          />
        </div>

        {/* 分类筛选 - category-pill */}
        <div className="flex items-center gap-2 mb-6 overflow-x-auto pb-2">
          <Filter className="h-4 w-4 text-[var(--color-muted-foreground)] mr-1 shrink-0" />
          {CATEGORIES.map((cat) => (
            <button
              key={cat.key}
              onClick={() => setActiveCategory(cat.key)}
              className={`category-pill px-4 py-1.5 rounded-full text-sm font-medium whitespace-nowrap transition-all duration-300 ${
                activeCategory === cat.key
                  ? "bg-[var(--color-primary)] text-white shadow-lg shadow-[var(--color-primary)]/25"
                  : "bg-[var(--color-muted)]/50 text-[var(--color-muted-foreground)] hover:bg-[var(--color-muted)] hover:text-[var(--color-card-foreground)]"
              }`}
            >
              {cat.label}
            </button>
          ))}
        </div>

        {/* ====== 主内容区：左侧卡片列表 + 右侧统计面板 ====== */}
        <div className="flex gap-6">
          {/* 左侧：记忆卡片列表 */}
          <div className="flex-1 min-w-0">
            <ScrollArea className="h-[calc(100vh-320px)]">
              {filteredEntries.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-20">
                  <Brain className="h-16 w-16 text-[var(--color-muted-foreground)]/40 mb-4 animate-pulse" />
                  <p className="text-[var(--color-muted-foreground)] text-sm">暂无记忆</p>
                  <p className="text-[var(--color-muted-foreground)]/60 text-xs mt-1">与 AI 对话后会自动记录</p>
                </div>
              ) : (
                <div className="space-y-3 pr-2">
                  {filteredEntries.map((entry) => (
                    <div
                      key={entry.id}
                      className={`glass-card group p-5 rounded-2xl cursor-pointer transition-all duration-300 hover:-translate-y-1 hover:shadow-[var(--theme-glow)] ${
                        isImportant(entry.importance)
                          ? "chinese-border border-2 border-double border-[var(--color-primary)]/30"
                          : "border border-[var(--color-border)]/50"
                      }`}
                    >
                      {/* 时间戳 + 来源标签 */}
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-xs text-[var(--color-muted-foreground)] flex items-center gap-1.5">
                          <Clock className="h-3 w-3" />
                          {formatTime(typeof entry.createdAt === 'number' ? entry.createdAt : new Date(entry.createdAt).getTime())}
                        </span>
                        <div className="flex items-center gap-1.5">
                          {/* 类型标签 pill */}
                          <span className="px-2.5 py-0.5 rounded-full text-[10px] font-medium bg-[var(--color-primary)]/15 text-[var(--color-primary)]">
                            {typeLabels[entry.type]}
                          </span>
                          {/* 重要度标签 */}
                          {isImportant(entry.importance) && (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-red-500/15 text-red-400 flex items-center gap-0.5">
                              <Star className="h-2.5 w-2.5 fill-red-400" />
                              重要
                            </span>
                          )}
                        </div>
                      </div>

                      {/* 内容预览 */}
                      <p className="text-sm text-[var(--color-card-foreground)] leading-relaxed line-clamp-3 group-hover:line-clamp-none transition-all duration-300">
                        {entry.content}
                      </p>

                      {/* 底部元信息 */}
                      <div className="flex items-center gap-2 mt-3 pt-3 border-t border-[var(--color-border)]/20">
                        <span className={`text-[10px] px-2 py-0.5 rounded-md ${
                          entry.source === "core"
                            ? "bg-amber-500/15 text-amber-400"
                            : entry.source === "long"
                            ? "bg-blue-500/15 text-blue-400"
                            : "bg-emerald-500/15 text-emerald-400"
                        }`}>
                          {entry.source === "core" ? "核心" : entry.source === "long" ? "长期" : "短期"}
                        </span>
                        <span className="text-[10px] text-[var(--color-muted-foreground)]">
                          重要性: {entry.importance}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </ScrollArea>
          </div>

          {/* 右侧统计面板 */}
          <div className="w-64 shrink-0 hidden lg:block">
            <div className="glass-panel sticky top-6 rounded-2xl p-5 space-y-5 border border-[var(--color-border)]/30 bg-gradient-to-b from-[var(--color-card)]/80 to-[var(--color-card)]/40 backdrop-blur-xl">
              <h3 className="text-sm font-semibold text-[var(--color-card-foreground)] flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-[var(--color-primary)]" />
                记忆概览
              </h3>

              {/* 总数 */}
              <div className="text-center py-4 rounded-xl bg-[var(--color-muted)]/30">
                <p className="text-3xl font-bold bg-[var(--theme-gradient)] bg-clip-text text-transparent">
                  {stats.total}
                </p>
                <p className="text-xs text-[var(--color-muted-foreground)] mt-1">条记忆</p>
              </div>

              {/* 统计项 */}
              <div className="space-y-3">
                <div className="flex items-center justify-between p-3 rounded-xl bg-[var(--color-muted)]/20 hover:bg-[var(--color-muted)]/30 transition-colors">
                  <div className="flex items-center gap-2">
                    <Star className="h-3.5 w-3.5 text-amber-400" />
                    <span className="text-xs text-[var(--color-muted-foreground)]">重要记忆</span>
                  </div>
                  <span className="text-sm font-semibold text-[var(--color-card-foreground)]">{stats.important}</span>
                </div>

                <div className="flex items-center justify-between p-3 rounded-xl bg-[var(--color-muted)]/20 hover:bg-[var(--color-muted)]/30 transition-colors">
                  <div className="flex items-center gap-2">
                    <Clock className="h-3.5 w-3.5 text-emerald-400" />
                    <span className="text-xs text-[var(--color-muted-foreground)]">今日新增</span>
                  </div>
                  <span className="text-sm font-semibold text-[var(--color-card-foreground)]">{stats.today}</span>
                </div>

                <div className="flex items-center justify-between p-3 rounded-xl bg-[var(--color-muted)]/20 hover:bg-[var(--color-muted)]/30 transition-colors">
                  <div className="flex items-center gap-2">
                    <Heart className="h-3.5 w-3.5 text-rose-400" />
                    <span className="text-xs text-[var(--color-muted-foreground)]">情感类</span>
                  </div>
                  <span className="text-sm font-semibold text-[var(--color-card-foreground)]">
                    {allEntries.filter((e) => e.type === "emotion").length}
                  </span>
                </div>
              </div>

              {/* 来源分布 */}
              <div className="pt-3 border-t border-[var(--color-border)]/20">
                <p className="text-xs text-[var(--color-muted-foreground)] mb-2">来源分布</p>
                <div className="space-y-2">
                  {[
                    { label: "核心", count: stats.sources.core, color: "bg-amber-400" },
                    { label: "长期", count: stats.sources.long, color: "bg-blue-400" },
                    { label: "短期", count: stats.sources.short, color: "bg-emerald-400" },
                  ].map((src) => (
                    <div key={src.label} className="flex items-center gap-2">
                      <span className="text-[10px] text-[var(--color-muted-foreground)] w-8">{src.label}</span>
                      <div className="flex-1 h-1.5 rounded-full bg-[var(--color-muted)]/30 overflow-hidden">
                        <div
                          className={`h-full ${src.color} rounded-full transition-all duration-500`}
                          style={{ width: `${stats.total > 0 ? (src.count / stats.total) * 100 : 0}%` }}
                        />
                      </div>
                      <span className="text-[10px] text-[var(--color-muted-foreground)] w-6 text-right">{src.count}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* 记忆池入口 */}
              <Button
                variant="outline"
                className="w-full mt-2 rounded-xl border-[var(--color-border)]/50 text-[var(--color-muted-foreground)] hover:text-[var(--color-primary)] hover:border-[var(--color-primary)]/50 transition-all"
                onClick={() => navigate(`/memory/${personaId}/pool`)}
              >
                <Archive className="h-4 w-4 mr-2" />
                记忆池管理
              </Button>
            </div>
          </div>
        </div>
      </div>
    </PageContainer>
  );
}
