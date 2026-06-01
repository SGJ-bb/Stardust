import { useState, useEffect, useMemo } from "react";
import {
  loadDiaries,
  loadCheckinRecords,
  loadMilestones,
  loadAffection,
  todayStr,
  type DiaryEntry,
  type CheckInRecord,
  type Milestone,
} from "../utils/api";

interface CalendarPageProps {
  onBack: () => void;
}

interface DayDetail {
  dateStr: string;
  diary: DiaryEntry | undefined;
  checkin: CheckInRecord | undefined;
  milestones: Milestone[];
  affectionLevel: number | null;
}

const WEEKDAY_HEADERS = ["日", "一", "二", "三", "四", "五", "六"];

export default function CalendarPage({ onBack }: CalendarPageProps) {
  const now = new Date();
  const [viewYear, setViewYear] = useState(now.getFullYear());
  const [viewMonth, setViewMonth] = useState(now.getMonth());
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [diaries, setDiaries] = useState<DiaryEntry[]>([]);
  const [checkinRecords, setCheckinRecords] = useState<CheckInRecord[]>([]);
  const [milestones, setMilestones] = useState<Milestone[]>([]);
  const [affectionLevel, setAffectionLevel] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [hoveredDate, setHoveredDate] = useState<string | null>(null);

  useEffect(() => {
    async function init() {
      try {
        const [d, c, m, a] = await Promise.all([
          loadDiaries(),
          loadCheckinRecords(),
          loadMilestones(),
          loadAffection(),
        ]);
        if (d && d.length > 0) setDiaries(d);
        if (c && c.length > 0) setCheckinRecords(c);
        if (m && m.length > 0) setMilestones(m);
        if (a) setAffectionLevel(a.level ?? 0);
      } catch (e) {
        console.error("加载日历数据失败:", e);
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  // 按日期索引数据
  const diaryMap = useMemo(() => {
    const map = new Map<string, DiaryEntry>();
    diaries.forEach((d) => map.set(d.date, d));
    return map;
  }, [diaries]);

  const checkinMap = useMemo(() => {
    const map = new Map<string, CheckInRecord>();
    checkinRecords.forEach((c) => map.set(c.date, c));
    return map;
  }, [checkinRecords]);

  const milestoneMap = useMemo(() => {
    const map = new Map<string, Milestone[]>();
    milestones.forEach((m) => {
      const dateStr = new Date(m.timestamp).toISOString().split("T")[0];
      const arr = map.get(dateStr) || [];
      arr.push(m);
      map.set(dateStr, arr);
    });
    return map;
  }, [milestones]);

  // 日历网格计算
  const calendarDays = useMemo(() => {
    const firstDay = new Date(viewYear, viewMonth, 1);
    const startWeekday = firstDay.getDay();
    const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
    const prevMonthDays = new Date(viewYear, viewMonth, 0).getDate();

    const days: Array<{
      day: number;
      dateStr: string;
      isCurrentMonth: boolean;
    }> = [];

    // 上月填充
    for (let i = startWeekday - 1; i >= 0; i--) {
      const day = prevMonthDays - i;
      const d = new Date(viewYear, viewMonth - 1, day);
      days.push({
        day,
        dateStr: d.toISOString().split("T")[0],
        isCurrentMonth: false,
      });
    }

    // 当月
    for (let day = 1; day <= daysInMonth; day++) {
      const d = new Date(viewYear, viewMonth, day);
      days.push({
        day,
        dateStr: d.toISOString().split("T")[0],
        isCurrentMonth: true,
      });
    }

    // 下月填充
    const remaining = 42 - days.length;
    for (let day = 1; day <= remaining; day++) {
      const d = new Date(viewYear, viewMonth + 1, day);
      days.push({
        day,
        dateStr: d.toISOString().split("T")[0],
        isCurrentMonth: false,
      });
    }

    return days;
  }, [viewYear, viewMonth]);

  const todayDateStr = todayStr();

  const handlePrevMonth = () => {
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear(viewYear - 1);
    } else {
      setViewMonth(viewMonth - 1);
    }
    setSelectedDate(null);
  };

  const handleNextMonth = () => {
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear(viewYear + 1);
    } else {
      setViewMonth(viewMonth + 1);
    }
    setSelectedDate(null);
  };

  const handleToday = () => {
    const n = new Date();
    setViewYear(n.getFullYear());
    setViewMonth(n.getMonth());
    setSelectedDate(todayDateStr);
  };

  const handleDayClick = (dateStr: string) => {
    setSelectedDate(selectedDate === dateStr ? null : dateStr);
  };

  // 选中日期的详情
  const selectedDetail: DayDetail | null = useMemo(() => {
    if (!selectedDate) return null;
    return {
      dateStr: selectedDate,
      diary: diaryMap.get(selectedDate),
      checkin: checkinMap.get(selectedDate),
      milestones: milestoneMap.get(selectedDate) || [],
      affectionLevel: affectionLevel > 0 ? affectionLevel : null,
    };
  }, [selectedDate, diaryMap, checkinMap, milestoneMap, affectionLevel]);

  const monthLabel = `${viewYear}年${viewMonth + 1}月`;

  if (loading) {
    return (
      <div style={styles.container}>
        <div style={styles.header}>
          <button style={styles.backBtn} onClick={onBack}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
          <span style={styles.headerTitle}>日历</span>
          <span style={styles.headerPlaceholder} />
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
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={onBack}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>日历</span>
        <button style={styles.todayBtn} onClick={handleToday}>
          今天
        </button>
      </div>

      {/* 月份导航 */}
      <div style={styles.monthNav}>
        <button style={styles.monthArrowBtn} onClick={handlePrevMonth}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <span style={styles.monthLabel}>{monthLabel}</span>
        <button style={styles.monthArrowBtn} onClick={handleNextMonth}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>

      {/* 星期头部 */}
      <div style={styles.weekdayRow}>
        {WEEKDAY_HEADERS.map((wd) => (
          <div key={wd} style={styles.weekdayCell}>
            {wd}
          </div>
        ))}
      </div>

      {/* 日历网格 */}
      <div style={styles.calendarGrid}>
        {calendarDays.map((item, idx) => {
          const isToday = item.dateStr === todayDateStr;
          const isSelected = item.dateStr === selectedDate;
          const hasDiary = diaryMap.has(item.dateStr);
          const hasCheckin = checkinMap.has(item.dateStr);
          const dayMilestones = milestoneMap.get(item.dateStr);
          const hasMilestone = dayMilestones && dayMilestones.length > 0;

          return (
            <div
              key={idx}
              style={{
                ...styles.dayCell,
                opacity: item.isCurrentMonth ? 1 : 0.3,
                background: hoveredDate === item.dateStr && item.isCurrentMonth
                  ? "rgba(139, 108, 255, 0.08)"
                  : "transparent",
              }}
              onClick={() => item.isCurrentMonth && handleDayClick(item.dateStr)}
              onMouseEnter={() => item.isCurrentMonth && setHoveredDate(item.dateStr)}
              onMouseLeave={() => setHoveredDate(null)}
            >
              <div
                style={{
                  ...styles.dayNumber,
                  ...(isToday ? styles.dayNumberToday : {}),
                  ...(isSelected ? styles.dayNumberSelected : {}),
                }}
              >
                {item.day}
              </div>
              {/* 指示点行 */}
              {(hasDiary || hasCheckin || hasMilestone) && (
                <div style={styles.dotRow}>
                  {hasDiary && <div style={{ ...styles.dot, backgroundColor: "var(--accent-secondary)" }} />}
                  {hasCheckin && <div style={{ ...styles.dot, backgroundColor: "var(--accent-green)" }} />}
                  {hasMilestone && <div style={{ ...styles.dot, backgroundColor: "var(--accent-primary)" }} />}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* 图例栏 */}
      <div style={styles.legendBar}>
        <div style={styles.legendItem}>
          <div style={{ ...styles.legendDot, backgroundColor: "var(--accent-secondary)" }} />
          <span style={styles.legendText}>日记</span>
        </div>
        <div style={styles.legendItem}>
          <div style={{ ...styles.legendDot, backgroundColor: "var(--accent-green)" }} />
          <span style={styles.legendText}>签到</span>
        </div>
        <div style={styles.legendItem}>
          <div style={{ ...styles.legendDot, backgroundColor: "var(--accent-primary)" }} />
          <span style={styles.legendText}>里程碑</span>
        </div>
      </div>

      {/* 选中日期的底部详情 */}
      {selectedDetail && (
        <div style={styles.bottomSheetOverlay} onClick={() => setSelectedDate(null)}>
          <div style={styles.bottomSheet} onClick={(e) => e.stopPropagation()}>
            <div style={styles.bottomSheetHandle} />
            <div style={styles.bottomSheetHeader}>
              <span style={styles.bottomSheetDate}>
                {formatDetailDate(selectedDetail.dateStr)}
              </span>
              <button style={styles.bottomSheetClose} onClick={() => setSelectedDate(null)}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
            <div style={styles.bottomSheetContent}>
              {/* 日记 */}
              {selectedDetail.diary ? (
                <div style={styles.detailSection}>
                  <div style={styles.detailSectionHeader}>
                    <span style={styles.detailSectionIcon}>📝</span>
                    <span style={styles.detailSectionTitle}>日记</span>
                  </div>
                  <div style={styles.diaryCard}>
                    <div style={styles.diaryCardHeader}>
                      <span style={styles.diaryCardEmoji}>{selectedDetail.diary.mood_emoji}</span>
                      <span style={styles.diaryCardTitle}>{selectedDetail.diary.title}</span>
                    </div>
                    <p style={styles.diaryCardPreview}>
                      {truncateContent(selectedDetail.diary.content, 120)}
                    </p>
                  </div>
                </div>
              ) : (
                <div style={styles.detailSection}>
                  <div style={styles.detailSectionHeader}>
                    <span style={styles.detailSectionIcon}>📝</span>
                    <span style={styles.detailSectionTitle}>日记</span>
                  </div>
                  <span style={styles.noDataText}>这一天没有日记</span>
                </div>
              )}

              {/* 签到 */}
              <div style={styles.detailSection}>
                <div style={styles.detailSectionHeader}>
                  <span style={styles.detailSectionIcon}>📅</span>
                  <span style={styles.detailSectionTitle}>签到</span>
                </div>
                {selectedDetail.checkin ? (
                  <div style={styles.checkinCard}>
                    <span style={styles.checkinBadge}>✓ 已签到</span>
                    <span style={styles.checkinStreak}>
                      连续 {selectedDetail.checkin.streak} 天
                    </span>
                  </div>
                ) : (
                  <span style={styles.noDataText}>这一天未签到</span>
                )}
              </div>

              {/* 里程碑 */}
              <div style={styles.detailSection}>
                <div style={styles.detailSectionHeader}>
                  <span style={styles.detailSectionIcon}>⭐</span>
                  <span style={styles.detailSectionTitle}>里程碑</span>
                </div>
                {selectedDetail.milestones.length > 0 ? (
                  selectedDetail.milestones.map((ms) => (
                    <div key={ms.id} style={styles.milestoneCard}>
                      <span style={styles.milestoneIcon}>{ms.icon}</span>
                      <div style={styles.milestoneInfo}>
                        <span style={styles.milestoneTitle}>{ms.title}</span>
                        <span style={styles.milestoneDesc}>{ms.description}</span>
                      </div>
                    </div>
                  ))
                ) : (
                  <span style={styles.noDataText}>这一天没有里程碑</span>
                )}
              </div>

              {/* 好感度 */}
              {selectedDetail.affectionLevel !== null && (
                <div style={styles.detailSection}>
                  <div style={styles.detailSectionHeader}>
                    <span style={styles.detailSectionIcon}>❤️</span>
                    <span style={styles.detailSectionTitle}>好感度</span>
                  </div>
                  <div style={styles.affectionCard}>
                    <span style={styles.affectionLevel}>{selectedDetail.affectionLevel}</span>
                    <span style={styles.affectionLabel}>当前等级</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function formatDetailDate(dateStr: string): string {
  try {
    const d = new Date(dateStr + "T00:00:00");
    const month = d.getMonth() + 1;
    const day = d.getDate();
    const weekDays = ["日", "一", "二", "三", "四", "五", "六"];
    const weekDay = weekDays[d.getDay()];
    return `${month}月${day}日 周${weekDay}`;
  } catch {
    return dateStr;
  }
}

function truncateContent(content: string, maxLen: number = 120): string {
  const plain = content.replace(/[#*_~`]/g, "").replace(/\n/g, " ").trim();
  return plain.length > maxLen ? plain.slice(0, maxLen) + "..." : plain;
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: "flex",
    flexDirection: "column",
    height: "100%",
    background: "var(--bg-primary)",
    position: "relative",
    animation: "fadeIn var(--duration-normal) var(--ease-out-expo)",
  },
  header: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-3) var(--space-4)",
    paddingTop: "calc(var(--space-3) + var(--safe-top))",
    background: "rgba(20, 18, 31, 0.85)",
    backdropFilter: "blur(20px)",
    borderBottom: "1px solid var(--border-color)",
    flexShrink: 0,
  },
  backBtn: {
    background: "transparent",
    color: "var(--text-primary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-sm)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  headerTitle: {
    fontSize: "var(--text-lg)",
    fontWeight: "var(--weight-bold)",
    letterSpacing: "var(--tracking-tight)",
    color: "var(--text-primary)",
  },
  headerPlaceholder: {
    width: 48,
  },
  todayBtn: {
    padding: "6px 14px",
    borderRadius: 14,
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    color: "white",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
  },

  // 月份导航
  monthNav: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: "var(--space-5)",
    padding: "14px var(--space-4)",
    background: "var(--bg-secondary)",
  },
  monthArrowBtn: {
    background: "rgba(255, 255, 255, 0.06)",
    border: "1px solid var(--border-color)",
    color: "var(--text-primary)",
    width: 36,
    height: 36,
    borderRadius: "var(--radius-md)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  monthLabel: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    minWidth: 120,
    textAlign: "center" as const,
  },

  // 星期头部
  weekdayRow: {
    display: "grid",
    gridTemplateColumns: "repeat(7, 1fr)",
    padding: "var(--space-2) var(--space-2) 4px",
    background: "var(--bg-secondary)",
  },
  weekdayCell: {
    textAlign: "center" as const,
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    color: "var(--text-muted)",
    padding: "4px 0",
  },

  // 日历网格
  calendarGrid: {
    display: "grid",
    gridTemplateColumns: "repeat(7, 1fr)",
    padding: "4px var(--space-2) var(--space-2)",
    background: "var(--bg-secondary)",
    flexShrink: 0,
  },
  dayCell: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    padding: "6px 2px",
    minHeight: 48,
    cursor: "pointer",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    borderRadius: "var(--radius-sm)",
  },
  dayNumber: {
    width: 32,
    height: 32,
    borderRadius: 16,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    color: "var(--text-primary)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  dayNumberToday: {
    border: "2px solid var(--accent-primary)",
    fontWeight: "var(--weight-bold)",
  },
  dayNumberSelected: {
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    color: "white",
    fontWeight: "var(--weight-bold)",
  },
  dotRow: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 3,
    marginTop: 2,
    height: 6,
  },
  dot: {
    width: 5,
    height: 5,
    borderRadius: 3,
    flexShrink: 0,
  },

  // 图例栏
  legendBar: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: "var(--space-5)",
    padding: "10px var(--space-4)",
    background: "var(--bg-secondary)",
    borderTop: "1px solid var(--border-color)",
    flexShrink: 0,
  },
  legendItem: {
    display: "flex",
    alignItems: "center",
    gap: 5,
  },
  legendDot: {
    width: "var(--space-2)",
    height: "var(--space-2)",
    borderRadius: 4,
  },
  legendText: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
  },

  // 加载状态
  loadingWrap: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    flex: 1,
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

  // 底部详情面板
  bottomSheetOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: "var(--bg-overlay)",
    backdropFilter: "blur(4px)",
    WebkitBackdropFilter: "blur(4px)",
    display: "flex",
    alignItems: "flex-end",
    zIndex: 100,
    animation: "fadeIn var(--duration-fast) var(--ease-out-quart)",
  },
  bottomSheet: {
    background: "var(--bg-secondary)",
    borderRadius: "20px 20px 0 0",
    width: "100%",
    maxHeight: "65%",
    display: "flex",
    flexDirection: "column" as const,
    animation: "slideUp var(--duration-normal) var(--ease-out-expo)",
  },
  bottomSheetHandle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    background: "var(--border-color)",
    margin: "10px auto 0",
    flexShrink: 0,
  },
  bottomSheetHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "14px var(--space-5) var(--space-2)",
    flexShrink: 0,
  },
  bottomSheetDate: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  bottomSheetClose: {
    background: "rgba(255, 255, 255, 0.06)",
    border: "none",
    color: "var(--text-secondary)",
    width: 32,
    height: 32,
    borderRadius: 16,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  bottomSheetContent: {
    flex: 1,
    overflow: "auto",
    padding: "0 var(--space-5) var(--space-6)",
    display: "flex",
    flexDirection: "column" as const,
    gap: "var(--space-4)",
  },

  // 详情区块
  detailSection: {
    display: "flex",
    flexDirection: "column" as const,
    gap: "var(--space-2)",
  },
  detailSectionHeader: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-1)",
  },
  detailSectionIcon: {
    fontSize: 16,
  },
  detailSectionTitle: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-secondary)",
  },
  noDataText: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    paddingLeft: 22,
  },

  // 日记卡片
  diaryCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    padding: "var(--space-3)",
    border: "1px solid var(--border-color)",
  },
  diaryCardHeader: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    marginBottom: 6,
  },
  diaryCardEmoji: {
    fontSize: 20,
  },
  diaryCardTitle: {
    fontSize: "15px",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  diaryCardPreview: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    lineHeight: 1.5,
  },

  // 签到卡片
  checkinCard: {
    display: "flex",
    alignItems: "center",
    gap: 10,
    background: "rgba(92, 255, 180, 0.1)",
    borderRadius: "var(--radius-md)",
    padding: "10px 14px",
    border: "1px solid rgba(92, 255, 180, 0.2)",
  },
  checkinBadge: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--accent-green)",
  },
  checkinStreak: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
  },

  // 里程碑卡片
  milestoneCard: {
    display: "flex",
    alignItems: "center",
    gap: 10,
    background: "rgba(139, 108, 255, 0.1)",
    borderRadius: "var(--radius-md)",
    padding: "10px 14px",
    border: "1px solid rgba(139, 108, 255, 0.2)",
  },
  milestoneIcon: {
    fontSize: 20,
  },
  milestoneInfo: {
    display: "flex",
    flexDirection: "column" as const,
    gap: 2,
  },
  milestoneTitle: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--accent-primary)",
  },
  milestoneDesc: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
  },

  // 好感度卡片
  affectionCard: {
    display: "flex",
    alignItems: "center",
    gap: 10,
    background: "rgba(255, 110, 180, 0.1)",
    borderRadius: "var(--radius-md)",
    padding: "10px 14px",
    border: "1px solid rgba(255, 110, 180, 0.2)",
  },
  affectionLevel: {
    fontSize: 20,
    fontWeight: "var(--weight-bold)",
    color: "var(--accent-pink)",
  },
  affectionLabel: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
  },
};
