import { useState, useEffect, useRef, useCallback } from "react";
import {
  loadChatHistory,
  loadSettings,
  saveChatHistory,
  ChatMessage,
  formatTimestamp,
  getEmotionEmoji,
  getEmotionColor,
  DEFAULT_CHARACTER,
} from "../utils/api";

interface ChatHistoryPageProps {
  onBack: () => void;
}

type SenderFilter = "all" | "user" | "ai";

const EMOTION_OPTIONS = ["Happy", "Sad", "Angry", "Surprised", "Tsundere", "Neutral"] as const;

const EMOTION_LABELS: Record<string, string> = {
  Happy: "开心",
  Sad: "难过",
  Angry: "生气",
  Surprised: "惊讶",
  Tsundere: "傲娇",
  Neutral: "平静",
};

export default function ChatHistoryPage({ onBack }: ChatHistoryPageProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [activePersonaId, setActivePersonaId] = useState("default_stardust");
  const [searchText, setSearchText] = useState("");
  const [senderFilter, setSenderFilter] = useState<SenderFilter>("all");
  const [emotionFilter, setEmotionFilter] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [toastText, setToastText] = useState<string | null>(null);
  const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [longPressId, setLongPressId] = useState<string | null>(null);

  useEffect(() => {
    async function init() {
      const settings = await loadSettings();
      if (settings?.active_persona_id) {
        setActivePersonaId(settings.active_persona_id);
      }
      const history = await loadChatHistory(
        settings?.active_persona_id || "default_stardust"
      );
      setMessages(history);
      setLoading(false);
    }
    init();
  }, []);

  const showToast = useCallback((text: string) => {
    setToastText(text);
    setTimeout(() => setToastText(null), 2000);
  }, []);

  const toggleFavorite = useCallback(
    async (id: string) => {
      const updated = messages.map((m) =>
        m.id === id ? { ...m, is_favorited: !m.is_favorited } : m
      );
      setMessages(updated);
      await saveChatHistory(activePersonaId, updated);
      const msg = updated.find((m) => m.id === id);
      if (msg) {
        showToast(msg.is_favorited ? "已收藏" : "已取消收藏");
      }
      setLongPressId(null);
    },
    [messages, activePersonaId, showToast]
  );

  // 长按触发收藏
  const handleTouchStart = (id: string) => {
    longPressTimer.current = setTimeout(() => {
      setLongPressId(id);
    }, 500);
  };

  const handleTouchEnd = () => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
  };

  // 筛选逻辑
  const filteredMessages = messages.filter((msg) => {
    // 搜索文本
    if (searchText.trim()) {
      if (!msg.text.toLowerCase().includes(searchText.trim().toLowerCase())) {
        return false;
      }
    }
    // 发送者筛选
    if (senderFilter === "user" && !msg.is_user) return false;
    if (senderFilter === "ai" && msg.is_user) return false;
    // 情绪筛选
    if (emotionFilter && msg.emotion !== emotionFilter) return false;
    return true;
  });

  // 统计
  const totalCount = messages.length;
  const userCount = messages.filter((m) => m.is_user).length;
  const aiCount = messages.filter((m) => !m.is_user).length;
  const favCount = messages.filter((m) => m.is_favorited).length;

  // 导出聊天记录
  const handleExport = async () => {
    setExporting(true);
    try {
      const lines = filteredMessages.map((msg) => {
        const sender = msg.is_user ? "我" : DEFAULT_CHARACTER.name;
        const time = formatTimestamp(msg.timestamp);
        const emotion = msg.emotion ? ` [${msg.emotion}]` : "";
        const fav = msg.is_favorited ? " ⭐" : "";
        return `[${time}] ${sender}${emotion}${fav}: ${msg.text}`;
      });
      const header = `=== ${DEFAULT_CHARACTER.name} 聊天记录 ===\n导出时间: ${formatTimestamp(Date.now())}\n共 ${filteredMessages.length} 条消息\n\n`;
      const text = header + lines.join("\n");
      await navigator.clipboard.writeText(text);
      showToast("已复制到剪贴板");
    } catch {
      showToast("复制失败，请重试");
    } finally {
      setExporting(false);
    }
  };

  const truncateText = (text: string, maxLen: number = 80): string => {
    if (text.length <= maxLen) return text;
    return text.substring(0, maxLen) + "...";
  };

  return (
    <div style={styles.container}>
      {/* 顶部导航 */}
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={onBack}>
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>聊天记录</span>
        <button
          style={{ ...styles.exportBtn, opacity: exporting ? 0.5 : 1 }}
          onClick={handleExport}
          disabled={exporting}
        >
          {exporting ? "导出中..." : "导出"}
        </button>
      </div>

      {/* 统计栏 */}
      <div style={styles.statsBar}>
        <div style={styles.statItem}>
          <span style={styles.statNumber}>{totalCount}</span>
          <span style={styles.statLabel}>全部</span>
        </div>
        <div style={styles.statDivider} />
        <div style={styles.statItem}>
          <span style={styles.statNumber}>{userCount}</span>
          <span style={styles.statLabel}>我的</span>
        </div>
        <div style={styles.statDivider} />
        <div style={styles.statItem}>
          <span style={styles.statNumber}>{aiCount}</span>
          <span style={styles.statLabel}>{DEFAULT_CHARACTER.name}</span>
        </div>
        <div style={styles.statDivider} />
        <div style={styles.statItem}>
          <span style={styles.statNumber}>{favCount}</span>
          <span style={styles.statLabel}>收藏</span>
        </div>
      </div>

      {/* 搜索栏 */}
      <div style={styles.searchBar}>
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="var(--text-muted)"
          strokeWidth="2"
          style={{ flexShrink: 0 }}
        >
          <circle cx="11" cy="11" r="8" />
          <path d="M21 21l-4.35-4.35" />
        </svg>
        <input
          style={styles.searchInput}
          type="text"
          placeholder="搜索聊天内容..."
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
        {searchText && (
          <button style={styles.clearBtn} onClick={() => setSearchText("")}>
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="var(--text-muted)"
            >
              <path d="M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z" />
            </svg>
          </button>
        )}
      </div>

      {/* 发送者筛选 */}
      <div style={styles.filterBar}>
        {(
          [
            { key: "all", label: "全部" },
            { key: "user", label: "我的" },
            { key: "ai", label: DEFAULT_CHARACTER.name },
          ] as const
        ).map((item) => (
          <button
            key={item.key}
            style={{
              ...styles.filterBtn,
              background:
                senderFilter === item.key
                  ? "var(--accent-primary)"
                  : "var(--bg-card)",
              color:
                senderFilter === item.key
                  ? "white"
                  : "var(--text-secondary)",
            }}
            onClick={() => setSenderFilter(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {/* 情绪筛选 */}
      <div style={styles.emotionFilterBar}>
        <button
          style={{
            ...styles.emotionFilterBtn,
            background:
              emotionFilter === null
                ? "var(--accent-primary)"
                : "var(--bg-card)",
            color:
              emotionFilter === null ? "white" : "var(--text-secondary)",
          }}
          onClick={() => setEmotionFilter(null)}
        >
          全部情绪
        </button>
        {EMOTION_OPTIONS.map((emo) => (
          <button
            key={emo}
            style={{
              ...styles.emotionFilterBtn,
              background:
                emotionFilter === emo
                  ? getEmotionColor(emo)
                  : "var(--bg-card)",
              color:
                emotionFilter === emo ? "white" : "var(--text-secondary)",
              borderColor:
                emotionFilter === emo
                  ? getEmotionColor(emo)
                  : "var(--border-color)",
            }}
            onClick={() =>
              setEmotionFilter(emotionFilter === emo ? null : emo)
            }
          >
            {getEmotionEmoji(emo)} {EMOTION_LABELS[emo]}
          </button>
        ))}
      </div>

      {/* 消息列表 */}
      <div style={styles.listContainer}>
        {loading ? (
          <div style={styles.emptyState}>
            <span style={{ fontSize: 48 }}>⏳</span>
            <span style={styles.emptyText}>加载中...</span>
          </div>
        ) : filteredMessages.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={{ fontSize: 48 }}>💬</span>
            <span style={styles.emptyText}>
              {messages.length === 0
                ? "还没有聊天记录"
                : "没有匹配的消息"}
            </span>
          </div>
        ) : (
          filteredMessages.map((msg) => (
            <div
              key={msg.id}
              style={styles.messageCard}
              onTouchStart={() => handleTouchStart(msg.id)}
              onTouchEnd={handleTouchEnd}
              onTouchCancel={handleTouchEnd}
              onClick={() => toggleFavorite(msg.id)}
            >
              {/* 长按/点击收藏确认 */}
              {longPressId === msg.id && (
                <div style={styles.favoriteOverlay}>
                  <div style={styles.favoriteConfirm}>
                    <span style={styles.favoriteConfirmText}>
                      {msg.is_favorited ? "取消收藏？" : "收藏这条消息？"}
                    </span>
                    <div style={styles.favoriteConfirmBtns}>
                      <button
                        style={styles.confirmFavBtn}
                        onClick={(e) => {
                          e.stopPropagation();
                          toggleFavorite(msg.id);
                        }}
                      >
                        {msg.is_favorited ? "取消收藏" : "收藏"}
                      </button>
                      <button
                        style={styles.cancelFavBtn}
                        onClick={(e) => {
                          e.stopPropagation();
                          setLongPressId(null);
                        }}
                      >
                        返回
                      </button>
                    </div>
                  </div>
                </div>
              )}

              <div style={styles.messageHeader}>
                <div style={styles.messageSenderRow}>
                  <span style={styles.senderIcon}>
                    {msg.is_user ? "👤" : getEmotionEmoji(msg.emotion || "Neutral")}
                  </span>
                  <span
                    style={{
                      ...styles.senderName,
                      color: msg.is_user
                        ? "var(--accent-secondary)"
                        : "var(--accent-primary)",
                    }}
                  >
                    {msg.is_user ? "我" : DEFAULT_CHARACTER.name}
                  </span>
                  {msg.emotion && !msg.is_user && (
                    <span
                      style={{
                        ...styles.emotionTag,
                        color: getEmotionColor(msg.emotion),
                        borderColor: getEmotionColor(msg.emotion),
                      }}
                    >
                      {getEmotionEmoji(msg.emotion)} {EMOTION_LABELS[msg.emotion] || msg.emotion}
                    </span>
                  )}
                </div>
                <div style={styles.messageMetaRight}>
                  {msg.is_favorited && (
                    <span style={styles.favIndicator}>⭐</span>
                  )}
                  <span style={styles.messageTime}>
                    {formatTimestamp(msg.timestamp)}
                  </span>
                </div>
              </div>
              <div style={styles.messageBody}>
                {truncateText(msg.text)}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Toast 提示 */}
      {toastText && <div style={styles.toast}>{toastText}</div>}
    </div>
  );
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
  },
  backBtn: {
    background: "transparent",
    color: "var(--text-primary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-sm)",
  },
  headerTitle: {
    fontSize: "var(--text-lg)",
    fontWeight: "var(--weight-bold)",
    letterSpacing: "var(--tracking-tight)",
    color: "var(--text-primary)",
  },
  exportBtn: {
    background: "var(--bg-card)",
    color: "var(--accent-primary)",
    padding: "6px 14px",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    border: "1px solid var(--accent-primary)",
  },
  statsBar: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-around",
    padding: "14px var(--space-4)",
    background: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
  },
  statItem: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: 2,
  },
  statNumber: {
    fontSize: 20,
    fontWeight: "var(--weight-bold)",
    color: "var(--accent-primary)",
  },
  statLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  statDivider: {
    width: 1,
    height: 28,
    background: "var(--border-color)",
  },
  searchBar: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    margin: "var(--space-3) var(--space-4) 0",
    padding: "var(--space-2) var(--space-3)",
    background: "var(--bg-input)",
    borderRadius: "var(--radius-md)",
    border: "1px solid var(--border-color)",
  },
  searchInput: {
    flex: 1,
    background: "transparent",
    border: "none",
    color: "var(--text-primary)",
    fontSize: "15px",
    padding: 0,
    outline: "none",
    width: "100%",
  },
  clearBtn: {
    background: "transparent",
    padding: "var(--space-1)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    border: "none",
  },
  filterBar: {
    display: "flex",
    gap: "var(--space-2)",
    padding: "var(--space-3) var(--space-4) 0",
    overflowX: "auto" as const,
    WebkitOverflowScrolling: "touch",
  },
  filterBtn: {
    padding: "6px var(--space-4)",
    borderRadius: "var(--radius-full)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    whiteSpace: "nowrap" as const,
    border: "none",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  emotionFilterBar: {
    display: "flex",
    gap: "var(--space-1)",
    padding: "var(--space-2) var(--space-4) 0",
    overflowX: "auto" as const,
    WebkitOverflowScrolling: "touch",
    flexWrap: "nowrap" as const,
  },
  emotionFilterBtn: {
    padding: "5px var(--space-3)",
    borderRadius: "var(--radius-lg)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    whiteSpace: "nowrap" as const,
    border: "1px solid var(--border-color)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    display: "flex",
    alignItems: "center",
    gap: "var(--space-1)",
  },
  listContainer: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-3) var(--space-4) var(--space-6)",
    WebkitOverflowScrolling: "touch",
  },
  emptyState: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    padding: "60px var(--space-5)",
    gap: "var(--space-4)",
  },
  emptyText: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    textAlign: "center" as const,
  },
  messageCard: {
    position: "relative" as const,
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    marginBottom: "10px",
    padding: "var(--space-3) var(--space-4)",
    border: "1px solid var(--border-color)",
    borderLeft: "3px solid var(--accent-primary)",
    overflow: "hidden",
    boxShadow: "var(--shadow-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  messageHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: "var(--space-1)",
  },
  messageSenderRow: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-1)",
  },
  senderIcon: {
    fontSize: 16,
  },
  senderName: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
  },
  emotionTag: {
    padding: "1px var(--space-2)",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-medium)",
    border: "1px solid",
    background: "rgba(0,0,0,0.2)",
  },
  messageMetaRight: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-1)",
  },
  favIndicator: {
    fontSize: "var(--text-sm)",
    color: "var(--accent-yellow)",
  },
  messageTime: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  messageBody: {
    fontSize: "var(--text-sm)",
    color: "var(--text-primary)",
    lineHeight: 1.6,
    wordBreak: "break-word" as const,
    paddingLeft: 22,
  },
  favoriteOverlay: {
    position: "absolute" as const,
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: "rgba(0,0,0,0.6)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 10,
    borderRadius: "var(--radius-md)",
  },
  favoriteConfirm: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-5)",
  },
  favoriteConfirmText: {
    fontSize: "15px",
    color: "white",
    fontWeight: "var(--weight-medium)",
  },
  favoriteConfirmBtns: {
    display: "flex",
    gap: "var(--space-3)",
  },
  confirmFavBtn: {
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    color: "white",
    padding: "var(--space-2) 24px",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
  },
  cancelFavBtn: {
    background: "var(--bg-card)",
    color: "var(--text-primary)",
    padding: "var(--space-2) 24px",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    border: "1px solid var(--border-color)",
  },
  toast: {
    position: "fixed" as const,
    bottom: "calc(80px + var(--safe-bottom))",
    left: "50%",
    transform: "translateX(-50%)",
    background: "var(--bg-card)",
    color: "var(--text-primary)",
    padding: "10px 24px",
    borderRadius: "var(--radius-md)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    boxShadow: "var(--shadow-md)",
    border: "1px solid var(--border-color)",
    zIndex: 200,
    animation: "fadeIn var(--duration-normal) var(--ease-out-expo)",
  },
};
