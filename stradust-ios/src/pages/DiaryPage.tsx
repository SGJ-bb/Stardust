import { useState, useEffect, useCallback, useRef } from "react";
import {
  DiaryEntry,
  loadDiaries,
  saveDiaries,
  loadChatHistory,
  loadSettings,
  loadAffection,
  generateDiary,
  DEFAULT_CHARACTER,
  formatDate,
  todayStr,
} from "../utils/api";
import {
  listStaggerIn,
  fadeInScale,
  glowPulse,
} from "../utils/animations";

interface DiaryPageProps {
  onBack: () => void;
}

const MOOD_OPTIONS = [
  { key: "happy", label: "开心", emoji: "😊", accent: "var(--accent-green)" },
  { key: "sad", label: "难过", emoji: "😢", accent: "var(--accent-secondary)" },
  { key: "excited", label: "兴奋", emoji: "🤩", accent: "var(--accent-orange)" },
  { key: "calm", label: "平静", emoji: "😌", accent: "var(--accent-secondary)" },
  { key: "sentimental", label: "感性", emoji: "🥺", accent: "var(--accent-pink)" },
];

export default function DiaryPage({ onBack }: DiaryPageProps) {
  const [diaries, setDiaries] = useState<DiaryEntry[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [selectedMood, setSelectedMood] = useState<string>("happy");
  const [showMoodPicker, setShowMoodPicker] = useState(false);

  // 动画引用
  const diaryListRef = useRef<HTMLDivElement>(null);
  const generateBtnRef = useRef<HTMLButtonElement>(null);
  const moodPickerRef = useRef<HTMLDivElement>(null);
  const expandedContentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    loadDiaries().then((data) => {
      if (data && data.length > 0) {
        setDiaries(data);
      }
    });
  }, []);

  // 日记列表入场动画
  useEffect(() => {
    if (diaryListRef.current && diaries.length > 0) {
      const cards = diaryListRef.current.querySelectorAll("[data-diary-card]");
      if (cards.length > 0) {
        listStaggerIn(Array.from(cards));
      }
    }
  }, [diaries]);

  // AI 生成按钮发光动画
  useEffect(() => {
    let anim: ReturnType<typeof glowPulse> | null = null;
    if (generateBtnRef.current && !isGenerating) {
      anim = glowPulse(generateBtnRef.current);
    }
    return () => { if (anim) anim.revert(); };
  }, [isGenerating]);

  // 展开内容动画
  useEffect(() => {
    if (expandedId && expandedContentRef.current) {
      fadeInScale(expandedContentRef.current);
    }
  }, [expandedId]);

  // 心情选择器入场动画
  useEffect(() => {
    if (showMoodPicker && moodPickerRef.current) {
      fadeInScale(moodPickerRef.current);
    }
  }, [showMoodPicker]);

  const sortedDiaries = [...diaries].sort((a, b) => b.date.localeCompare(a.date));

  const handleToggleExpand = (date: string) => {
    setExpandedId(expandedId === date ? null : date);
  };

  const handleGenerateDiary = useCallback(async () => {
    if (isGenerating) return;
    setIsGenerating(true);
    setShowMoodPicker(false);

    try {
      const settings = await loadSettings();
      if (!settings?.api_config?.chat_api_url) {
        alert("请先在设置中配置 API 地址");
        setIsGenerating(false);
        return;
      }

      const apiConfig = settings.api_config;
      const personaId = settings.active_persona_id || "default_stardust";

      const chatHistory = await loadChatHistory(personaId);
      const chatTexts = chatHistory.map((msg) =>
        `${msg.is_user ? "用户" : DEFAULT_CHARACTER.name}: ${msg.text}`
      );

      if (chatTexts.length === 0) {
        alert("暂无聊天记录，无法生成日记");
        setIsGenerating(false);
        return;
      }

      const affection = await loadAffection();
      const moodOption = MOOD_OPTIONS.find((m) => m.key === selectedMood) || MOOD_OPTIONS[0];

      const result = await generateDiary(
        apiConfig.chat_api_url,
        apiConfig.api_key,
        apiConfig.model_name,
        chatTexts,
        DEFAULT_CHARACTER.name,
        DEFAULT_CHARACTER.system_prompt,
        moodOption.key,
        moodOption.emoji,
        affection.level,
      );

      const today = todayStr();
      const existingToday = diaries.find((d) => d.date === today);

      let title = "星尘的日记";
      const titleMatch = result.match(/^(.{1,30})/);
      if (titleMatch) {
        title = titleMatch[1].replace(/^#\s*/, "").trim();
      }

      const newEntry: DiaryEntry = {
        date: today,
        title,
        content: result,
        mood: moodOption.label,
        mood_emoji: moodOption.emoji,
        affection_level: affection.level,
        message_count: chatHistory.length,
        is_auto: true,
      };

      let updatedDiaries: DiaryEntry[];
      if (existingToday) {
        updatedDiaries = diaries.map((d) =>
          d.date === today ? newEntry : d
        );
      } else {
        updatedDiaries = [...diaries, newEntry];
      }

      await saveDiaries(updatedDiaries);
      setDiaries(updatedDiaries);
      setExpandedId(today);
    } catch (error) {
      alert(`生成日记失败: ${error}`);
    } finally {
      setIsGenerating(false);
    }
  }, [isGenerating, selectedMood, diaries]);

  const handleDeleteDiary = async (date: string) => {
    const updatedDiaries = diaries.filter((d) => d.date !== date);
    await saveDiaries(updatedDiaries);
    setDiaries(updatedDiaries);
    if (expandedId === date) {
      setExpandedId(null);
    }
  };

  const truncateContent = (content: string, maxLen: number = 60) => {
    const plain = content.replace(/[#*_~`]/g, "").replace(/\n/g, " ").trim();
    return plain.length > maxLen ? plain.slice(0, maxLen) + "..." : plain;
  };

  const formatDateDisplay = (dateStr: string) => {
    try {
      const d = new Date(dateStr + "T00:00:00");
      const today = new Date();
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);

      if (dateStr === todayStr()) return "今天";
      if (formatDate(yesterday) === formatDate(d)) return "昨天";

      const month = d.getMonth() + 1;
      const day = d.getDate();
      const weekDays = ["日", "一", "二", "三", "四", "五", "六"];
      const weekDay = weekDays[d.getDay()];
      return `${month}月${day}日 周${weekDay}`;
    } catch {
      return dateStr;
    }
  };

  return (
    <div style={styles.container}>
      {/* 顶部导航栏 */}
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={onBack}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>星尘的日记</span>
        <button
          ref={generateBtnRef}
          style={{
            ...styles.generateBtn,
            opacity: isGenerating ? 0.7 : 1,
            pointerEvents: isGenerating ? ("none" as const) : ("auto" as const),
          }}
          onClick={() => {
            if (isGenerating) return;
            setShowMoodPicker(!showMoodPicker);
          }}
          disabled={isGenerating}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
          </svg>
          <span style={styles.generateBtnText}>AI写日记</span>
        </button>
      </div>

      {/* 心情选择器 */}
      {showMoodPicker && (
        <div style={styles.moodPickerOverlay} onClick={() => setShowMoodPicker(false)}>
          <div ref={moodPickerRef} style={styles.moodPicker} onClick={(e) => e.stopPropagation()}>
            <span style={styles.moodPickerTitle}>选择今天的心情</span>
            <div style={styles.moodOptions}>
              {MOOD_OPTIONS.map((mood) => (
                <button
                  key={mood.key}
                  style={{
                    ...styles.moodOption,
                    background: selectedMood === mood.key
                      ? "rgba(139, 108, 255, 0.3)"
                      : "var(--bg-input)",
                    borderColor: selectedMood === mood.key
                      ? "var(--accent-primary)"
                      : "var(--border-color)",
                    transform: selectedMood === mood.key ? "scale(1.08)" : "scale(1)",
                    boxShadow: selectedMood === mood.key
                      ? "0 0 0 2px var(--accent-primary), var(--shadow-glow)"
                      : "none",
                    opacity: selectedMood === mood.key ? 1 : 0.6,
                  }}
                  onClick={() => setSelectedMood(mood.key)}
                >
                  <span style={styles.moodEmoji}>{mood.emoji}</span>
                  <span style={{
                    ...styles.moodLabel,
                    color: selectedMood === mood.key ? "var(--accent-primary)" : "var(--text-secondary)",
                  }}>{mood.label}</span>
                </button>
              ))}
            </div>
            <button
              style={styles.moodConfirmBtn}
              onClick={handleGenerateDiary}
              disabled={isGenerating}
            >
              {isGenerating ? "正在生成..." : "开始生成"}
            </button>
          </div>
        </div>
      )}

      {/* 日记列表 */}
      <div ref={diaryListRef} style={styles.diaryList}>
        {sortedDiaries.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={styles.emptyEmoji}>📖</span>
            <span style={styles.emptyText}>还没有日记</span>
            <span style={styles.emptyHint}>点击右上角「AI写日记」，让星尘帮你记录今天的故事吧</span>
          </div>
        ) : (
          sortedDiaries.map((diary) => {
            const isExpanded = expandedId === diary.date;
            return (
              <div
                key={diary.date}
                data-diary-card
                style={{
                  ...styles.diaryCard,
                  borderLeft: isExpanded ? "3px solid var(--accent-secondary)" : "1px solid var(--border-color)",
                }}
                onClick={() => handleToggleExpand(diary.date)}
              >
                <div style={styles.diaryHeader}>
                  <div style={styles.diaryHeaderLeft}>
                    <span style={styles.diaryEmoji}>{diary.mood_emoji}</span>
                    <div style={styles.diaryTitleArea}>
                      <span style={styles.diaryTitle}>{diary.title}</span>
                      <span style={styles.diaryDate}>{formatDateDisplay(diary.date)}</span>
                    </div>
                  </div>
                  <div style={styles.diaryHeaderRight}>
                    {diary.is_auto && (
                      <span style={styles.autoTag}>AI生成</span>
                    )}
                    <svg
                      width="16"
                      height="16"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="var(--text-muted)"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      style={{
                        transform: isExpanded ? "rotate(180deg)" : "rotate(0deg)",
                        transition: "transform var(--duration-fast) var(--ease-out-quart)",
                      }}
                    >
                      <polyline points="6 9 12 15 18 9" />
                    </svg>
                  </div>
                </div>

                {/* 心情标签 */}
                <div style={styles.moodTagRow}>
                  <span
                    style={{
                      ...styles.moodTag,
                      background: getMoodTagBg(diary.mood),
                      color: getMoodTagColor(diary.mood),
                    }}
                  >
                    {diary.mood_emoji} {diary.mood}
                  </span>
                  {diary.message_count > 0 && (
                    <span style={styles.msgCountTag}>
                      {diary.message_count}条对话
                    </span>
                  )}
                </div>

                {/* 内容区域 */}
                {isExpanded ? (
                  <div ref={expandedContentRef} style={styles.diaryContentExpanded}>
                    <p style={styles.diaryContentText}>{diary.content}</p>
                    <div style={styles.diaryActions}>
                      <button
                        style={styles.deleteBtn}
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteDiary(diary.date);
                        }}
                      >
                        删除日记
                      </button>
                    </div>
                  </div>
                ) : (
                  <p style={styles.diarySummary}>{truncateContent(diary.content)}</p>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* 生成中遮罩 */}
      {isGenerating && (
        <div style={styles.generatingOverlay}>
          <div style={styles.generatingCard}>
            <div style={styles.generatingSpinner} />
            <span style={styles.generatingText}>星尘正在认真写日记...</span>
            <span style={styles.generatingHint}>根据聊天记录生成中</span>
          </div>
        </div>
      )}
    </div>
  );
}

function getMoodTagBg(mood: string): string {
  const map: Record<string, string> = {
    "开心": "rgba(92, 255, 180, 0.15)",
    "难过": "rgba(94, 162, 255, 0.15)",
    "兴奋": "rgba(255, 154, 92, 0.15)",
    "平静": "rgba(94, 162, 255, 0.15)",
    "感性": "rgba(255, 110, 180, 0.15)",
  };
  return map[mood] || "rgba(139, 108, 255, 0.15)";
}

function getMoodTagColor(mood: string): string {
  const map: Record<string, string> = {
    "开心": "var(--accent-green)",
    "难过": "var(--accent-secondary)",
    "兴奋": "var(--accent-orange)",
    "平静": "var(--accent-secondary)",
    "感性": "var(--accent-pink)",
  };
  return map[mood] || "var(--accent-primary)";
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: "flex",
    flexDirection: "column",
    height: "100%",
    background: "var(--bg-primary)",
    position: "relative",
  },
  header: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-3) var(--space-4)",
    paddingTop: "calc(var(--space-3) + var(--safe-top))",
    background: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
    flexShrink: 0,
  },
  backBtn: {
    background: "transparent",
    color: "var(--text-primary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-md)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  headerTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  generateBtn: {
    display: "flex",
    alignItems: "center",
    gap: 4,
    background: "var(--gradient-primary)",
    color: "white",
    padding: "var(--space-2) var(--space-3)",
    borderRadius: "var(--radius-full)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    boxShadow: "var(--shadow-glow)",
  },
  generateBtnText: {
    fontSize: "var(--text-sm)",
    letterSpacing: "var(--tracking-wide)",
  },
  moodPickerOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: "var(--bg-overlay)",
    backdropFilter: "blur(4px)",
    WebkitBackdropFilter: "blur(4px)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 100,
  },
  moodPicker: {
    background: "var(--bg-secondary)",
    borderRadius: "var(--radius-xl)",
    padding: "var(--space-6)",
    margin: "var(--space-5)",
    border: "1px solid var(--border-color)",
    width: "calc(100% - 40px)",
    maxWidth: 360,
    boxShadow: "var(--shadow-lg)",
  },
  moodPickerTitle: {
    display: "block",
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    marginBottom: "var(--space-4)",
    textAlign: "center" as const,
  },
  moodOptions: {
    display: "flex",
    justifyContent: "space-between",
    gap: "var(--space-2)",
    marginBottom: "var(--space-5)",
  },
  moodOption: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: "var(--space-1)",
    padding: "var(--space-3) var(--space-2)",
    borderRadius: "var(--radius-lg)",
    border: "2px solid var(--border-color)",
    background: "var(--bg-input)",
    flex: 1,
    minWidth: 0,
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  moodEmoji: {
    fontSize: 24,
  },
  moodLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
    fontWeight: "var(--weight-medium)",
  },
  moodConfirmBtn: {
    width: "100%",
    padding: "var(--space-3) 0",
    background: "var(--gradient-primary)",
    color: "white",
    borderRadius: "var(--radius-lg)",
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    boxShadow: "var(--shadow-sm)",
  },
  diaryList: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-3) var(--space-4)",
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-3)",
  },
  emptyState: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    flex: 1,
    gap: "var(--space-2)",
    padding: "60px var(--space-5)",
  },
  emptyEmoji: {
    fontSize: 48,
    marginBottom: "var(--space-2)",
  },
  emptyText: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-medium)",
    color: "var(--text-secondary)",
  },
  emptyHint: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    textAlign: "center" as const,
    lineHeight: "var(--leading-relaxed)",
  },
  diaryCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
    padding: "var(--space-4)",
    cursor: "pointer",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    boxShadow: "var(--shadow-sm)",
  },
  diaryHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  diaryHeaderLeft: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    flex: 1,
    minWidth: 0,
  },
  diaryEmoji: {
    fontSize: 28,
    flexShrink: 0,
  },
  diaryTitleArea: {
    display: "flex",
    flexDirection: "column" as const,
    gap: 2,
    minWidth: 0,
  },
  diaryTitle: {
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap" as const,
  },
  diaryDate: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    fontWeight: "var(--weight-medium)",
    letterSpacing: "var(--tracking-wide)",
  },
  diaryHeaderRight: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    flexShrink: 0,
  },
  autoTag: {
    fontSize: "var(--text-xs)",
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-full)",
    background: "rgba(139, 108, 255, 0.2)",
    color: "var(--accent-primary)",
    fontWeight: "var(--weight-medium)",
  },
  moodTagRow: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    marginTop: "var(--space-2)",
  },
  moodTag: {
    display: "inline-flex",
    alignItems: "center",
    gap: 4,
    padding: "3px 10px",
    borderRadius: "var(--radius-full)",
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-medium)",
  },
  msgCountTag: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  diarySummary: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    lineHeight: "var(--leading-normal)",
    marginTop: "var(--space-2)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap" as const,
  },
  diaryContentExpanded: {
    marginTop: "var(--space-3)",
    paddingTop: "var(--space-3)",
    borderTop: "1px solid var(--border-color)",
  },
  diaryContentText: {
    fontSize: "var(--text-md)",
    color: "var(--text-primary)",
    lineHeight: "var(--leading-relaxed)",
    whiteSpace: "pre-wrap" as const,
    wordBreak: "break-word" as const,
  },
  diaryActions: {
    display: "flex",
    justifyContent: "flex-end",
    marginTop: "var(--space-4)",
  },
  deleteBtn: {
    padding: "var(--space-1) var(--space-4)",
    background: "rgba(255, 92, 124, 0.15)",
    color: "var(--accent-red)",
    borderRadius: "var(--radius-md)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
  },
  generatingOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: "var(--bg-overlay)",
    backdropFilter: "blur(4px)",
    WebkitBackdropFilter: "blur(4px)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 200,
  },
  generatingCard: {
    background: "var(--bg-secondary)",
    borderRadius: "var(--radius-xl)",
    padding: "var(--space-8)",
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: "var(--space-3)",
    border: "1px solid var(--border-color)",
    boxShadow: "var(--shadow-lg)",
  },
  generatingSpinner: {
    width: 40,
    height: 40,
    borderRadius: 20,
    border: "3px solid var(--border-color)",
    borderTopColor: "var(--accent-primary)",
    animation: "spin 1s linear infinite",
  },
  generatingText: {
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-medium)",
    color: "var(--text-primary)",
  },
  generatingHint: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
  },
};
