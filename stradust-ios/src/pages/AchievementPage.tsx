import { useState, useEffect, useRef } from "react";
import {
  Achievement,
  loadAchievements,
  saveAchievements,
  loadAffection,
  loadCheckinRecords,
  loadMemories,
  loadDiaries,
  loadChatHistory,
  loadSettings,
  DEFAULT_ACHIEVEMENTS,
} from "../utils/api";
import {
  countUp,
  staggerFadeIn,
  progressFill,
  glowPulse,
  fadeInScale,
} from "../utils/animations";

interface AchievementPageProps {
  onBack: () => void;
}

const CATEGORY_ORDER = ["chat", "checkin", "affection", "memory", "diary", "hidden"] as const;

const CATEGORY_LABELS: Record<string, string> = {
  chat: "💬 聊天",
  checkin: "📅 签到",
  affection: "❤️ 好感度",
  memory: "🧠 记忆",
  diary: "📝 日记",
  hidden: "🔮 隐藏",
};

const CATEGORY_COLORS: Record<string, string> = {
  chat: "var(--accent-secondary)",
  checkin: "var(--accent-orange)",
  affection: "var(--accent-pink)",
  memory: "var(--accent-primary)",
  diary: "var(--accent-green)",
  hidden: "var(--accent-yellow)",
};

export default function AchievementPage({ onBack }: AchievementPageProps) {
  const [achievements, setAchievements] = useState<Achievement[]>([]);
  const [loading, setLoading] = useState(true);

  // 动画引用
  const statsCardRef = useRef<HTMLDivElement>(null);
  const unlockedCountRef = useRef<HTMLSpanElement>(null);
  const overallProgressRef = useRef<HTMLDivElement>(null);
  const listWrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    async function init() {
      try {
        const [
          savedAchievements,
          affectionData,
          checkinRecords,
          memories,
          diaries,
          settings,
        ] = await Promise.all([
          loadAchievements(),
          loadAffection(),
          loadCheckinRecords(),
          loadMemories(),
          loadDiaries(),
          loadSettings(),
        ]);

        const chatHistory = await loadChatHistory(
          settings?.active_persona_id || "default_stardust"
        );

        // 以 DEFAULT_ACHIEVEMENTS 为基准，合并已保存的进度
        const base = DEFAULT_ACHIEVEMENTS.map((def) => {
          const saved = savedAchievements?.find((s) => s.id === def.id);
          return saved ? { ...def, ...saved } : { ...def };
        });

        // 计算当前进度
        const chatCount = chatHistory?.length ?? 0;
        const maxStreak = checkinRecords?.length
          ? Math.max(...checkinRecords.map((r) => r.streak), 0)
          : 0;
        const affectionLevel = affectionData?.level ?? 0;
        const memoryCount = memories?.length ?? 0;
        const diaryCount = diaries?.length ?? 0;

        const updated = base.map((a) => {
          let progress = a.progress;
          switch (a.category) {
            case "chat":
              progress = chatCount;
              break;
            case "checkin":
              progress = maxStreak;
              break;
            case "affection":
              progress = affectionLevel;
              break;
            case "memory":
              progress = memoryCount;
              break;
            case "diary":
              progress = diaryCount;
              break;
            case "hidden":
              // hidden 成就保持已保存的状态
              break;
          }

          const unlocked = a.unlocked || progress >= a.unlock_condition;
          const unlockedAt = a.unlocked_at || (unlocked ? Date.now() : 0);

          return { ...a, progress, unlocked, unlocked_at: unlockedAt };
        });

        setAchievements(updated);
        await saveAchievements(updated);
      } catch (e) {
        console.error("加载成就数据失败:", e);
        setAchievements(DEFAULT_ACHIEVEMENTS);
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  const unlockedCount = achievements.filter((a) => a.unlocked).length;
  const totalCount = achievements.length;
  const overallProgress = totalCount > 0 ? (unlockedCount / totalCount) * 100 : 0;

  const grouped = CATEGORY_ORDER.map((cat) => ({
    category: cat,
    label: CATEGORY_LABELS[cat],
    items: achievements.filter((a) => a.category === cat),
  })).filter((g) => g.items.length > 0);

  // 入场动画
  useEffect(() => {
    if (loading) return;
    const timer = setTimeout(() => {
      if (statsCardRef.current) {
        fadeInScale(statsCardRef.current);
      }
      if (unlockedCountRef.current) {
        countUp(unlockedCountRef.current, 0, unlockedCount);
      }
      if (overallProgressRef.current) {
        progressFill(overallProgressRef.current, 0, overallProgress);
      }
    }, 100);
    return () => clearTimeout(timer);
  }, [loading]);

  // 成就列表入场动画
  useEffect(() => {
    if (loading || !listWrapRef.current) return;
    const cards = listWrapRef.current.querySelectorAll("[data-ach-card]");
    if (cards.length > 0) {
      staggerFadeIn(Array.from(cards));
    }
  }, [loading, achievements]);

  // 已解锁成就发光动画
  useEffect(() => {
    if (loading || !listWrapRef.current) return;
    const anims: ReturnType<typeof glowPulse>[] = [];
    const unlockedCards = listWrapRef.current.querySelectorAll("[data-unlocked]");
    unlockedCards.forEach((card) => {
      anims.push(glowPulse(card));
    });
    return () => { anims.forEach((a) => a.revert()); };
  }, [loading, achievements]);

  if (loading) {
    return (
      <div style={styles.container}>
        <div style={styles.navBar}>
          <button style={styles.backBtn} onClick={onBack}>← 返回</button>
          <span style={styles.navTitle}>成就殿堂</span>
          <span style={styles.navPlaceholder} />
        </div>
        <div style={styles.loadingWrap}>
          <div style={styles.loadingSpinner} />
          <p style={styles.loadingText}>加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      {/* 顶部导航栏 */}
      <div style={styles.navBar}>
        <button style={styles.backBtn} onClick={onBack}>← 返回</button>
        <span style={styles.navTitle}>成就殿堂</span>
        <span style={styles.navPlaceholder} />
      </div>

      {/* 成就统计卡片 */}
      <div ref={statsCardRef} style={styles.statsCard}>
        <div style={styles.statsTop}>
          <div style={styles.statsIcon}>🏆</div>
          <div style={styles.statsInfo}>
            <div style={styles.statsCount}>
              <span ref={unlockedCountRef} style={styles.statsUnlocked}>{unlockedCount}</span>
              <span style={styles.statsSeparator}> / </span>
              <span style={styles.statsTotal}>{totalCount}</span>
            </div>
            <div style={styles.statsLabel}>已解锁成就</div>
          </div>
        </div>
        <div style={styles.progressTrack}>
          <div
            ref={overallProgressRef}
            style={{
              ...styles.progressFill,
              width: `${overallProgress}%`,
            }}
          />
        </div>
        <div style={styles.progressLabel}>
          完成度 {Math.round(overallProgress)}%
        </div>
      </div>

      {/* 按类别分组的成就列表 */}
      <div ref={listWrapRef} style={styles.listWrap}>
        {grouped.map((group) => (
          <div key={group.category} style={styles.categorySection}>
            <div style={{
              ...styles.categoryHeader,
              color: CATEGORY_COLORS[group.category] || "var(--text-secondary)",
            }}>{group.label}</div>
            {group.items.map((ach) => {
              const progressPercent = Math.min(
                (ach.progress / ach.unlock_condition) * 100,
                100
              );
              return (
                <div
                  key={ach.id}
                  data-ach-card
                  style={{
                    ...styles.achievementCard,
                    opacity: ach.unlocked ? 1 : 0.5,
                    borderLeft: ach.unlocked
                      ? "3px solid var(--accent-yellow)"
                      : "1px solid var(--border-color)",
                  }}
                >
                  <div style={styles.achLeft}>
                    <div
                      data-unlocked={ach.unlocked ? "" : undefined}
                      style={{
                        ...styles.achIcon,
                        backgroundColor: ach.unlocked
                          ? "rgba(255, 213, 92, 0.15)"
                          : "rgba(128, 128, 128, 0.1)",
                      }}
                    >
                      {ach.icon}
                    </div>
                  </div>
                  <div style={styles.achCenter}>
                    <div style={styles.achTitleRow}>
                      <span
                        style={{
                          ...styles.achTitle,
                          color: ach.unlocked ? "var(--accent-yellow)" : "var(--text-primary)",
                        }}
                      >
                        {ach.title}
                      </span>
                      {ach.unlocked ? (
                        <span style={styles.unlockedBadge}>✓</span>
                      ) : (
                        <span style={styles.lockedBadge}>🔒</span>
                      )}
                    </div>
                    <div style={styles.achDesc}>{ach.description}</div>
                    <div style={styles.achProgressTrack}>
                      <div
                        style={{
                          ...styles.achProgressFill,
                          width: `${progressPercent}%`,
                          background: ach.unlocked
                            ? "var(--gradient-primary)"
                            : "linear-gradient(90deg, var(--accent-primary), var(--accent-secondary))",
                        }}
                      />
                    </div>
                    <div style={styles.achProgressText}>
                      {Math.min(ach.progress, ach.unlock_condition)} / {ach.unlock_condition}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        ))}
      </div>

      {/* 底部安全区 */}
      <div style={{ height: 40 }} />
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    minHeight: "100vh",
    backgroundColor: "var(--bg-primary)",
    color: "var(--text-primary)",
    fontFamily: "var(--font-sans)",
    paddingBottom: "env(safe-area-inset-bottom, 0px)",
    overflowY: "auto",
    WebkitOverflowScrolling: "touch",
  },

  // 导航栏
  navBar: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-3) var(--space-4)",
    paddingTop: "calc(var(--space-3) + env(safe-area-inset-top, 0px))",
    backgroundColor: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
    position: "sticky",
    top: 0,
    zIndex: 100,
  },
  backBtn: {
    background: "none",
    border: "none",
    color: "var(--text-secondary)",
    fontSize: "var(--text-base)",
    cursor: "pointer",
    padding: "var(--space-1) 0",
    minWidth: 60,
  },
  navTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  navPlaceholder: {
    minWidth: 60,
  },

  // 加载状态
  loadingWrap: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    height: "60vh",
  },
  loadingSpinner: {
    width: 36,
    height: 36,
    border: "3px solid var(--border-color)",
    borderTopColor: "var(--accent-primary)",
    borderRadius: "50%",
    animation: "spin 0.8s linear infinite",
  },
  loadingText: {
    marginTop: "var(--space-3)",
    color: "var(--text-secondary)",
    fontSize: "var(--text-sm)",
  },

  // 统计卡片
  statsCard: {
    margin: "var(--space-4)",
    padding: "var(--space-5)",
    background: "var(--gradient-primary)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
    boxShadow: "var(--shadow-lg)",
    position: "relative",
    overflow: "hidden",
  },
  statsTop: {
    display: "flex",
    alignItems: "center",
    marginBottom: "var(--space-4)",
  },
  statsIcon: {
    fontSize: 40,
    marginRight: "var(--space-4)",
  },
  statsInfo: {
    flex: 1,
  },
  statsCount: {
    display: "flex",
    alignItems: "baseline",
  },
  statsUnlocked: {
    fontSize: "var(--text-2xl)",
    fontWeight: "var(--weight-bold)",
    color: "var(--accent-yellow)",
  },
  statsSeparator: {
    fontSize: "var(--text-lg)",
    color: "var(--text-secondary)",
    margin: "0 var(--space-1)",
  },
  statsTotal: {
    fontSize: "var(--text-lg)",
    color: "var(--text-secondary)",
  },
  statsLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    marginTop: 2,
    textTransform: "uppercase" as const,
    letterSpacing: "var(--tracking-wide)",
  },
  progressTrack: {
    height: 8,
    backgroundColor: "rgba(255, 255, 255, 0.15)",
    borderRadius: 4,
    overflow: "hidden",
    boxShadow: "inset 0 1px 2px rgba(0, 0, 0, 0.2)",
  },
  progressFill: {
    height: "100%",
    borderRadius: 4,
    background: "var(--accent-yellow)",
    transition: "width var(--duration-slow) var(--ease-out-expo)",
    boxShadow: "0 0 12px rgba(255, 213, 92, 0.4)",
  },
  progressLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
    textAlign: "right" as const,
    marginTop: "var(--space-1)",
  },

  // 列表
  listWrap: {
    padding: "0 var(--space-4)",
  },
  categorySection: {
    marginBottom: "var(--space-5)",
  },
  categoryHeader: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-secondary)",
    marginBottom: "var(--space-2)",
    paddingLeft: "var(--space-1)",
    letterSpacing: "var(--tracking-wide)",
    borderBottom: "2px solid var(--accent-primary)",
    paddingBottom: "var(--space-2)",
  },

  // 成就卡片
  achievementCard: {
    display: "flex",
    alignItems: "flex-start",
    padding: "var(--space-3)",
    backgroundColor: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    marginBottom: "var(--space-2)",
    border: "1px solid var(--border-color)",
    transition: "opacity var(--duration-normal) var(--ease-out-quart)",
    boxShadow: "var(--shadow-sm)",
  },
  achLeft: {
    marginRight: "var(--space-3)",
    flexShrink: 0,
  },
  achIcon: {
    width: 44,
    height: 44,
    borderRadius: "var(--radius-md)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: 22,
  },
  achCenter: {
    flex: 1,
    minWidth: 0,
  },
  achTitleRow: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 4,
  },
  achTitle: {
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
  },
  unlockedBadge: {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    width: 22,
    height: 22,
    borderRadius: "50%",
    backgroundColor: "rgba(255, 213, 92, 0.2)",
    color: "var(--accent-yellow)",
    fontSize: 13,
    fontWeight: "var(--weight-bold)",
    flexShrink: 0,
  },
  lockedBadge: {
    fontSize: 14,
    opacity: 0.4,
    flexShrink: 0,
  },
  achDesc: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    marginBottom: "var(--space-2)",
  },
  achProgressTrack: {
    height: 6,
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    borderRadius: 3,
    overflow: "hidden",
    boxShadow: "inset 0 1px 2px rgba(0, 0, 0, 0.2)",
  },
  achProgressFill: {
    height: "100%",
    borderRadius: 3,
    transition: "width var(--duration-slow) var(--ease-out-expo)",
  },
  achProgressText: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    textAlign: "right" as const,
    marginTop: "var(--space-1)",
  },
};
