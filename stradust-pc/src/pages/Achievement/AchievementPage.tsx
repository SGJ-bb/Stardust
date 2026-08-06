import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { useAchievementStore } from "@/stores/useAchievementStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  ArrowLeft,
  Trophy,
  Calendar,
  Star,
  Lock,
  Sparkles,
  Target,
  Award,
} from "lucide-react";
import { formatDate } from "@/lib/utils";
import { useState, useMemo } from "react";

/** 分类筛选类型 */
type CategoryFilter = "all" | "unlocked" | "locked";

/**
 * 成就页面
 * 对应Android AchievementActivity
 */
export function AchievementPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();
  const { achievements, checkIn, growth, doCheckIn } = useAchievementStore();
  const [activeTab, setActiveTab] = useState("achievements");
  const [categoryFilter, setCategoryFilter] = useState<CategoryFilter>("all");

  const unlockedCount = achievements.filter((a) => a.unlocked).length;
  const totalCount = achievements.length;

  /** 稀有度配置 */
  const rarityConfig: Record<string, { label: string; colorClass: string }> = {
    common: { label: "普通", colorClass: "text-[var(--color-muted-foreground)]" },
    rare: { label: "稀有", colorClass: "text-blue-400" },
    epic: { label: "史诗", colorClass: "text-purple-400" },
    legendary: { label: "传说", colorClass: "text-[var(--theme-accent-gold,#fbbf24)]" },
  };

  /** 按分类筛选成就 */
  const filteredAchievements = useMemo(() => {
    switch (categoryFilter) {
      case "unlocked":
        return achievements.filter((a) => a.unlocked);
      case "locked":
        return achievements.filter((a) => !a.unlocked);
      default:
        return achievements;
    }
  }, [achievements, categoryFilter]);

  /** 稀有度分布统计 */
  const rarityDistribution = useMemo(() => {
    const dist: Record<string, number> = { common: 0, rare: 0, epic: 0, legendary: 0 };
    achievements.forEach((a) => {
      dist[a.rarity] = (dist[a.rarity] || 0) + 1;
    });
    return dist;
  }, [achievements]);

  /** 总经验值估算 */
  const totalExp = useMemo(() => {
    const expByRarity: Record<string, number> = {
      common: 50,
      rare: 100,
      epic: 200,
      legendary: 500,
    };
    return achievements.reduce((sum, a) => sum + (a.unlocked ? (expByRarity[a.rarity] || 50) : 0), 0);
  }, [achievements]);

  /** 渲染空状态 */
  const renderEmptyState = () => (
    <div className="flex flex-col items-center justify-center py-20">
      <Trophy className="h-20 w-20 text-[var(--color-muted-foreground)] empty-state-icon mb-6" />
      <p className="text-lg font-medium text-[var(--color-card-foreground)] mb-2">暂无成就</p>
      <p className="text-sm text-[var(--color-muted-foreground)]">继续探索，解锁更多成就吧</p>
    </div>
  );

  /** 渲染单个成就卡片 */
  const renderAchievementCard = (achievement: (typeof achievements)[number]) => {
    const isUnlocked = achievement.unlocked;
    const progressPercent = achievement.target > 0 ? Math.min((achievement.progress / achievement.target) * 100, 100) : 0;
    const rarityInfo = rarityConfig[achievement.rarity] || rarityConfig.common;

    return (
      <div
        key={achievement.id}
        className={`
          glass-card p-5 flex flex-col items-center text-center relative overflow-hidden
          ${isUnlocked ? "" : "opacity-50"}
        `}
        style={
          isUnlocked
            ? {
                borderColor: `color-mix(in srgb, var(--theme-accent-gold, #fbbf24) 35%, transparent)`,
                boxShadow: `0 0 20px color-mix(in srgb, var(--theme-accent-gold, #fbbf24) 15%, transparent), var(--theme-glow-strong, none)`,
              }
            : undefined
        }
      >
        {/* 解锁状态角标 */}
        {isUnlocked && (
          <div className="absolute top-3 right-3">
            <Sparkles className="h-4 w-4 text-[var(--theme-accent-gold,#fbbf24)]" />
          </div>
        )}

        {/* 图标区域 */}
        <div className={`text-4xl mb-3 ${isUnlocked ? "" : "grayscale opacity-40"}`}>
          {isUnlocked ? achievement.icon : <Lock className="h-10 w-10 text-[var(--color-muted-foreground)]" />}
        </div>

        {/* 成就名称 */}
        <h3 className={`font-semibold text-sm mb-1.5 ${isUnlocked ? "text-[var(--color-card-foreground)]" : "text-[var(--color-muted-foreground)]"}`}>
          {achievement.title}
        </h3>

        {/* 稀有度标签 */}
        <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full mb-2 ${rarityInfo.colorClass} bg-[var(--color-muted)]`}>
          {rarityInfo.label}
        </span>

        {/* 描述文字 */}
        <p className="text-xs text-[var(--color-muted-foreground)] leading-relaxed mb-3 line-clamp-2">
          {achievement.description}
        </p>

        {/* 进度条区域 */}
        {!isUnlocked && (
          <div className="w-full space-y-1">
            <div className="w-full h-1.5 rounded-full bg-[var(--color-muted)] overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-500"
                style={{
                  width: `${progressPercent}%`,
                  background: "var(--theme-gradient)",
                }}
              />
            </div>
            <p className="text-[10px] text-[var(--color-muted-foreground)]">
              {achievement.progress} / {achievement.target}
            </p>
          </div>
        )}

        {/* 已解锁时间 */}
        {isUnlocked && achievement.unlockedAt && (
          <p className="text-[10px] text-[var(--theme-accent-gold,#fbbf24)] mt-auto pt-2">
            {formatDate(achievement.unlockedAt)} 解锁
          </p>
        )}
      </div>
    );
  };

  return (
    <PageContainer>
      <div className="page-content space-y-6">
        {/* ====== 页面头部 ====== */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
              <ArrowLeft className="h-4 w-4 mr-2" />
              返回
            </Button>
            <div>
              <h1 className="text-2xl font-bold text-[var(--color-card-foreground)] flex items-center gap-2">
                <Trophy className="h-6 w-6 text-[var(--theme-accent-gold,#fbbf24)]" />
                成就系统
              </h1>
              <p className="text-sm text-[var(--color-muted-foreground)] mt-0.5">
                已解锁 <span className="text-[var(--color-primary)] font-semibold">{unlockedCount}</span> / {totalCount}
              </p>
            </div>
          </div>
        </div>

        {/* ====== 统计面板 ====== */}
        <div className="glass-panel rounded-xl p-5">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {/* 总经验值 */}
            <div className="text-center">
              <div className="flex items-center justify-center gap-1.5 mb-1">
                <Sparkles className="h-4 w-4 text-[var(--theme-accent-gold,#fbbf24)]" />
                <span className="text-xs text-[var(--color-muted-foreground)]">总经验值</span>
              </div>
              <p className="text-xl font-bold text-[var(--color-card-foreground)]">{totalExp.toLocaleString()}</p>
            </div>
            {/* 总成就数 */}
            <div className="text-center">
              <div className="flex items-center justify-center gap-1.5 mb-1">
                <Award className="h-4 w-4 text-[var(--color-primary)]" />
                <span className="text-xs text-[var(--color-muted-foreground)]">总成就数</span>
              </div>
              <p className="text-xl font-bold text-[var(--color-card-foreground)]">{totalCount}</p>
            </div>
            {/* 解锁进度 */}
            <div className="text-center">
              <div className="flex items-center justify-center gap-1.5 mb-1">
                <Target className="h-4 w-4 text-green-400" />
                <span className="text-xs text-[var(--color-muted-foreground)]">解锁进度</span>
              </div>
              <p className="text-xl font-bold text-[var(--color-card-foreground)]">
                {totalCount > 0 ? Math.round((unlockedCount / totalCount) * 100) : 0}%
              </p>
            </div>
            {/* 稀有度概览 */}
            <div className="text-center">
              <div className="flex items-center justify-center gap-1.5 mb-1">
                <Star className="h-4 w-4 text-purple-400" />
                <span className="text-xs text-[var(--color-muted-foreground)]">稀有成就</span>
              </div>
              <p className="text-xl font-bold text-[var(--color-card-foreground)]">
                {(rarityDistribution.rare || 0) + (rarityDistribution.epic || 0) + (rarityDistribution.legendary || 0)}
              </p>
            </div>
          </div>

          {/* 稀有度分布条 */}
          <div className="mt-4 pt-4 border-t border-[var(--color-border)]">
            <p className="text-xs text-[var(--color-muted-foreground)] mb-2">稀有度分布</p>
            <div className="flex gap-2 flex-wrap">
              {Object.entries(rarityDistribution).map(([rarity, count]) => (
                <div key={rarity} className="flex items-center gap-1.5 text-xs">
                  <span
                    className="w-2.5 h-2.5 rounded-full"
                    style={{
                      background:
                        rarity === "common"
                          ? "var(--color-muted-foreground)"
                          : rarity === "rare"
                          ? "#60a5fa"
                          : rarity === "epic"
                          ? "#c084fc"
                          : "var(--theme-accent-gold,#fbbf24)",
                    }}
                  />
                  <span className="text-[var(--color-muted-foreground)]">{rarityConfig[rarity]?.label ?? rarity}</span>
                  <span className="font-medium text-[var(--color-card-foreground)]">{count}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ====== 主内容区 Tabs ====== */}
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="mb-5">
            <TabsTrigger value="achievements">
              <Trophy className="h-3.5 w-3.5 mr-1" />
              成就
            </TabsTrigger>
            <TabsTrigger value="checkin">
              <Calendar className="h-3.5 w-3.5 mr-1" />
              签到
            </TabsTrigger>
            <TabsTrigger value="growth">
              <Star className="h-3.5 w-3.5 mr-1" />
              成长
            </TabsTrigger>
          </TabsList>

          {/* ========== 成就 Tab ========== */}
          <TabsContent value="achievements">
            {/* 分类标签栏 */}
            <div className="flex gap-2 mb-5">
              {([
                { key: "all", label: "全部" },
                { key: "unlocked", label: "已解锁" },
                { key: "locked", label: "未解锁" },
              ] as { key: CategoryFilter; label: string }[]).map(({ key, label }) => (
                <button
                  key={key}
                  className={`category-pill ${categoryFilter === key ? "active" : ""}`}
                  onClick={() => setCategoryFilter(key)}
                >
                  {label}
                </button>
              ))}
            </div>

            {/* 成就卡片网格 */}
            {filteredAchievements.length > 0 ? (
              <ScrollArea className="h-[calc(100vh-420px)]">
                <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 pr-3">
                  {filteredAchievements.map(renderAchievementCard)}
                </div>
              </ScrollArea>
            ) : (
              renderEmptyState()
            )}
          </TabsContent>

          {/* ========== 签到 Tab ========== */}
          <TabsContent value="checkin">
            <div className="glass-panel rounded-xl p-8 text-center max-w-md mx-auto">
              <Calendar className="h-16 w-16 mx-auto text-[var(--color-primary)] mb-4" />
              <h3 className="text-xl font-semibold text-[var(--color-card-foreground)] mb-2">
                {checkIn?.todayCheckedIn ? "今日已签到 ✅" : "今日未签到"}
              </h3>
              <p className="text-sm text-[var(--color-muted-foreground)] mb-6">
                连续签到 <span className="text-[var(--color-primary)] font-semibold">{checkIn?.streak ?? 0}</span> 天 · 累计{" "}
                <span className="text-[var(--color-primary)] font-semibold">{checkIn?.totalDays ?? 0}</span> 天
              </p>
              {!checkIn?.todayCheckedIn && (
                <Button
                  onClick={() => personaId && doCheckIn(personaId)}
                  disabled={!personaId}
                  className="min-w-[120px]"
                >
                  立即签到
                </Button>
              )}
            </div>
          </TabsContent>

          {/* ========== 成长 Tab ========== */}
          <TabsContent value="growth">
            {growth ? (
              <div className="glass-panel rounded-xl p-6">
                <h3 className="text-lg font-semibold text-[var(--color-card-foreground)] mb-5 flex items-center gap-2">
                  <Star className="h-5 w-5 text-[var(--color-primary)]" />
                  成长数据
                </h3>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-5">
                  {[
                    { icon: "❤️", label: "好感度", value: `Lv.${growth.favorabilityLevel}` },
                    { icon: "💬", label: "聊天次数", value: growth.totalChats.toLocaleString() },
                    { icon: "📝", label: "总字数", value: growth.totalWords.toLocaleString() },
                    { icon: "🧠", label: "记忆数", value: growth.totalMemories.toLocaleString() },
                    { icon: "🏆", label: "成就解锁", value: growth.achievementsUnlocked },
                    { icon: "📅", label: "签到天数", value: growth.checkInDays },
                  ].map((item) => (
                    <div key={item.label} className="text-center p-4 rounded-lg bg-[var(--color-muted)]/30">
                      <span className="text-2xl mb-2 block">{item.icon}</span>
                      <p className="text-xs text-[var(--color-muted-foreground)] mb-1">{item.label}</p>
                      <p className="text-lg font-bold text-[var(--color-card-foreground)]">{item.value}</p>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              renderEmptyState()
            )}
          </TabsContent>
        </Tabs>
      </div>
    </PageContainer>
  );
}
