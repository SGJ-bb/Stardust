import { useState, useEffect, useCallback } from "react";
import { generateId, formatTimestamp, saveMemorial, loadMemorial, type MemorialEntry as ApiMemorialEntry } from "../utils/api";

type MemorialEntry = ApiMemorialEntry;

interface MemorialAlbumPageProps {
  onBack: () => void;
}

type CategoryFilter = "all" | "milestone" | "first" | "special" | "daily";

const CATEGORY_LABELS: Record<string, string> = {
  milestone: "里程碑",
  first: "第一次",
  special: "特别时刻",
  daily: "日常温馨",
};

const CATEGORY_COLORS: Record<string, string> = {
  milestone: "var(--accent-primary)",
  first: "var(--accent-pink)",
  special: "var(--accent-yellow)",
  daily: "var(--accent-green)",
};

const EMOJI_OPTIONS = [
  "⭐", "🌟", "💫", "🎉", "🎊", "💝",
  "💌", "🏆", "🎯", "💐", "🌹", "🎁",
];

const FILTER_OPTIONS: { key: CategoryFilter; label: string }[] = [
  { key: "all", label: "全部" },
  { key: "milestone", label: "里程碑" },
  { key: "first", label: "第一次" },
  { key: "special", label: "特别时刻" },
  { key: "daily", label: "日常温馨" },
];

const STORAGE_KEY = "memorial_album";

async function loadFromStorage(): Promise<MemorialEntry[]> {
  try {
    return await loadMemorial();
  } catch {
    // fallback to localStorage
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) return JSON.parse(raw);
    } catch {
      // ignore
    }
    return [];
  }
}

async function saveToStorage(entries: MemorialEntry[]): Promise<void> {
  try {
    await saveMemorial(entries);
  } catch {
    // fallback to localStorage
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
    } catch {
      // ignore
    }
  }
}

export default function MemorialAlbumPage({ onBack }: MemorialAlbumPageProps) {
  const [entries, setEntries] = useState<MemorialEntry[]>([]);
  const [filter, setFilter] = useState<CategoryFilter>("all");
  const [showAddModal, setShowAddModal] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newEmoji, setNewEmoji] = useState("⭐");
  const [newCategory, setNewCategory] = useState<string>("milestone");
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const data = await loadFromStorage();
      setEntries(data.sort((a, b) => b.timestamp - a.timestamp));
    })();
  }, []);

  const persistEntries = useCallback((updated: MemorialEntry[]) => {
    const sorted = [...updated].sort((a, b) => b.timestamp - a.timestamp);
    setEntries(sorted);
    saveToStorage(sorted);
  }, []);

  const handleAdd = () => {
    const title = newTitle.trim();
    const description = newDescription.trim();
    if (!title) return;

    const entry: MemorialEntry = {
      id: generateId(),
      title,
      description,
      emoji: newEmoji,
      timestamp: Date.now(),
      category: newCategory,
    };

    persistEntries([...entries, entry]);
    setNewTitle("");
    setNewDescription("");
    setNewEmoji("⭐");
    setNewCategory("milestone");
    setShowAddModal(false);
  };

  const handleDelete = (id: string) => {
    persistEntries(entries.filter((e) => e.id !== id));
    setDeleteConfirmId(null);
  };

  const filteredEntries =
    filter === "all"
      ? entries
      : entries.filter((e) => e.category === filter);

  // 统计
  const totalCount = entries.length;
  const categoryBreakdown = {
    milestone: entries.filter((e) => e.category === "milestone").length,
    first: entries.filter((e) => e.category === "first").length,
    special: entries.filter((e) => e.category === "special").length,
    daily: entries.filter((e) => e.category === "daily").length,
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
        <span style={styles.headerTitle}>纪念相册</span>
        <button
          style={styles.addBtn}
          onClick={() => setShowAddModal(true)}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>

      {/* 统计栏 */}
      {entries.length > 0 && (
        <div style={styles.statsBar}>
          <div style={styles.statItem}>
            <span style={styles.statCount}>{totalCount}</span>
            <span style={styles.statLabel}>全部回忆</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={{ ...styles.statCount, color: CATEGORY_COLORS.milestone }}>{categoryBreakdown.milestone}</span>
            <span style={styles.statLabel}>里程碑</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={{ ...styles.statCount, color: CATEGORY_COLORS.first }}>{categoryBreakdown.first}</span>
            <span style={styles.statLabel}>第一次</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={{ ...styles.statCount, color: CATEGORY_COLORS.special }}>{categoryBreakdown.special}</span>
            <span style={styles.statLabel}>特别时刻</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={{ ...styles.statCount, color: CATEGORY_COLORS.daily }}>{categoryBreakdown.daily}</span>
            <span style={styles.statLabel}>日常温馨</span>
          </div>
        </div>
      )}

      {/* 分类筛选 */}
      <div style={styles.filterBar}>
        {FILTER_OPTIONS.map((opt) => (
          <button
            key={opt.key}
            style={{
              ...styles.filterBtn,
              background:
                filter === opt.key
                  ? opt.key === "all"
                    ? "var(--accent-primary)"
                    : CATEGORY_COLORS[opt.key]
                  : "var(--bg-card)",
              color: filter === opt.key ? "white" : "var(--text-secondary)",
              boxShadow: filter === opt.key
                ? `0 0 10px ${opt.key === "all" ? "rgba(139, 108, 255, 0.4)" : CATEGORY_COLORS[opt.key] + "40"}`
                : "none",
            }}
            onClick={() => setFilter(opt.key)}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* 纪念卡片网格 */}
      <div style={styles.gridContainer}>
        {filteredEntries.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={styles.emptyEmoji}>📸</span>
            <span style={styles.emptyText}>
              {filter === "all" ? "还没有纪念回忆" : `没有"${CATEGORY_LABELS[filter] || filter}"类别的回忆`}
            </span>
            <span style={styles.emptyHint}>点击右上角「+」，记录与星尘的重要时刻</span>
          </div>
        ) : (
          <div style={styles.grid}>
            {filteredEntries.map((entry) => (
              <div
                key={entry.id}
                style={{
                  ...styles.card,
                  borderLeft: `3px solid ${CATEGORY_COLORS[entry.category] || "var(--border-color)"}`,
                }}
                onClick={() => setDeleteConfirmId(entry.id)}
              >
                <div style={styles.cardEmoji}>{entry.emoji}</div>
                <div style={styles.cardBody}>
                  <span style={styles.cardTitle}>{entry.title}</span>
                  {entry.description && (
                    <span style={styles.cardDesc}>
                      {entry.description.length > 40
                        ? entry.description.slice(0, 40) + "..."
                        : entry.description}
                    </span>
                  )}
                  <div style={styles.cardFooter}>
                    <span
                      style={{
                        ...styles.categoryTag,
                        background: CATEGORY_COLORS[entry.category] || "var(--text-muted)",
                      }}
                    >
                      {CATEGORY_LABELS[entry.category] || entry.category}
                    </span>
                    <span style={styles.cardDate}>
                      {formatTimestamp(entry.timestamp)}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 删除确认弹窗 */}
      {deleteConfirmId && (
        <div style={styles.modalOverlay} onClick={() => setDeleteConfirmId(null)}>
          <div style={styles.deleteConfirmModal} onClick={(e) => e.stopPropagation()}>
            <span style={styles.deleteConfirmEmoji}>🗑️</span>
            <span style={styles.deleteConfirmTitle}>确定删除这条回忆？</span>
            <span style={styles.deleteConfirmHint}>删除后将无法恢复</span>
            <div style={styles.deleteConfirmBtns}>
              <button
                style={styles.cancelDeleteBtn}
                onClick={() => setDeleteConfirmId(null)}
              >
                取消
              </button>
              <button
                style={styles.confirmDeleteBtn}
                onClick={() => handleDelete(deleteConfirmId)}
              >
                删除
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 添加回忆底部弹窗 */}
      {showAddModal && (
        <div style={styles.modalOverlay} onClick={() => setShowAddModal(false)}>
          <div style={styles.createModal} onClick={(e) => e.stopPropagation()}>
            {/* 拖拽指示条 */}
            <div style={styles.modalHandle} />

            <h3 style={styles.modalTitle}>记录回忆</h3>

            <div style={styles.field}>
              <label style={styles.label}>Emoji</label>
              <div style={styles.emojiPicker}>
                {EMOJI_OPTIONS.map((em) => (
                  <button
                    key={em}
                    style={{
                      ...styles.emojiBtn,
                      background: newEmoji === em
                        ? "rgba(139, 108, 255, 0.2)"
                        : "var(--bg-input)",
                      border: newEmoji === em
                        ? "2px solid var(--accent-primary)"
                        : "1px solid var(--border-color)",
                    }}
                    onClick={() => setNewEmoji(em)}
                  >
                    {em}
                  </button>
                ))}
              </div>
            </div>

            <div style={styles.field}>
              <label style={styles.label}>标题</label>
              <input
                style={styles.input}
                type="text"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="给这个回忆起个名字..."
                maxLength={30}
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>描述</label>
              <textarea
                style={styles.textarea}
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                placeholder="记录这个特别时刻的细节..."
                rows={3}
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>类别</label>
              <div style={styles.categoryPicker}>
                {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
                  <button
                    key={key}
                    style={{
                      ...styles.categoryPickBtn,
                      background: newCategory === key
                        ? CATEGORY_COLORS[key]
                        : "var(--bg-input)",
                      color: newCategory === key ? "white" : "var(--text-secondary)",
                    }}
                    onClick={() => setNewCategory(key)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            <div style={styles.modalBtns}>
              <button
                style={styles.cancelBtn}
                onClick={() => setShowAddModal(false)}
              >
                取消
              </button>
              <button
                style={{
                  ...styles.confirmBtn,
                  opacity: newTitle.trim() ? 1 : 0.5,
                }}
                onClick={handleAdd}
                disabled={!newTitle.trim()}
              >
                记录
              </button>
            </div>
          </div>
        </div>
      )}
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
  addBtn: {
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    color: "white",
    width: 36,
    height: 36,
    borderRadius: 18,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    padding: 0,
  },

  // 统计栏
  statsBar: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-around",
    padding: "14px var(--space-3)",
    background: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
  },
  statItem: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: 2,
  },
  statCount: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-primary)",
  },
  statLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  statDivider: {
    width: 1,
    height: 30,
    background: "var(--border-color)",
  },

  // 筛选栏
  filterBar: {
    display: "flex",
    gap: "var(--space-2)",
    padding: "var(--space-3) var(--space-4)",
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

  // 网格容器
  gridContainer: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-3) var(--space-4)",
    WebkitOverflowScrolling: "touch",
  },
  grid: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
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
    lineHeight: 1.6,
  },

  // 卡片
  card: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    padding: "var(--space-4) var(--space-4)",
    display: "flex",
    flexDirection: "column" as const,
    gap: "var(--space-2)",
    cursor: "pointer",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    boxShadow: "var(--shadow-sm)",
  },
  cardEmoji: {
    fontSize: 28,
  },
  cardBody: {
    display: "flex",
    flexDirection: "column" as const,
    gap: "var(--space-1)",
    minWidth: 0,
  },
  cardTitle: {
    fontSize: "15px",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap" as const,
  },
  cardDesc: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    lineHeight: 1.5,
    overflow: "hidden",
    textOverflow: "ellipsis",
    display: "-webkit-box",
    WebkitLineClamp: 2,
    WebkitBoxOrient: "vertical" as const,
  },
  cardFooter: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    gap: "var(--space-1)",
    marginTop: "var(--space-1)",
  },
  categoryTag: {
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-semibold)",
    color: "white",
    whiteSpace: "nowrap" as const,
  },
  cardDate: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    whiteSpace: "nowrap" as const,
  },

  // 删除确认弹窗
  modalOverlay: {
    position: "fixed" as const,
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
  deleteConfirmModal: {
    background: "var(--bg-secondary)",
    borderRadius: "var(--radius-xl)",
    padding: 28,
    margin: "var(--space-5)",
    border: "1px solid var(--border-color)",
    width: "calc(100% - 40px)",
    maxWidth: 320,
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: 10,
  },
  deleteConfirmEmoji: {
    fontSize: 36,
  },
  deleteConfirmTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  deleteConfirmHint: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    marginBottom: "var(--space-2)",
  },
  deleteConfirmBtns: {
    display: "flex",
    gap: "var(--space-3)",
    width: "100%",
  },
  cancelDeleteBtn: {
    flex: 1,
    background: "var(--bg-card)",
    color: "var(--text-primary)",
    padding: "12px",
    borderRadius: "var(--radius-md)",
    fontSize: "15px",
    border: "1px solid var(--border-color)",
  },
  confirmDeleteBtn: {
    flex: 1,
    background: "rgba(255, 92, 124, 0.9)",
    color: "white",
    padding: "12px",
    borderRadius: "var(--radius-md)",
    fontSize: "15px",
    fontWeight: "var(--weight-semibold)",
    border: "none",
  },

  // 添加回忆弹窗
  createModal: {
    width: "100%",
    maxWidth: 500,
    background: "var(--bg-secondary)",
    borderRadius: "20px 20px 0 0",
    padding: "var(--space-5) var(--space-5)",
    paddingBottom: "calc(var(--space-6) + var(--safe-bottom))",
  },
  modalHandle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    background: "var(--border-color)",
    margin: "0 auto var(--space-4)",
  },
  modalTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-primary)",
    marginBottom: "var(--space-5)",
    margin: 0,
  },
  field: {
    marginBottom: "var(--space-4)",
  },
  label: {
    display: "block",
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    marginBottom: "var(--space-2)",
    fontWeight: "var(--weight-medium)",
  },
  input: {
    width: "100%",
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-md)",
    color: "var(--text-primary)",
    padding: "var(--space-3)",
    fontSize: "15px",
    outline: "none",
    boxSizing: "border-box" as const,
  },
  textarea: {
    width: "100%",
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-md)",
    color: "var(--text-primary)",
    padding: "var(--space-3)",
    fontSize: "15px",
    outline: "none",
    resize: "none" as const,
    lineHeight: 1.5,
    boxSizing: "border-box" as const,
  },

  // Emoji 选择器
  emojiPicker: {
    display: "flex",
    gap: "var(--space-2)",
    flexWrap: "wrap" as const,
  },
  emojiBtn: {
    width: 40,
    height: 40,
    borderRadius: "var(--radius-sm)",
    fontSize: 20,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    cursor: "pointer",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    padding: 0,
  },

  // 类别选择器
  categoryPicker: {
    display: "flex",
    gap: "var(--space-2)",
    flexWrap: "wrap" as const,
  },
  categoryPickBtn: {
    padding: "6px var(--space-4)",
    borderRadius: "var(--radius-full)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    border: "none",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },

  // 弹窗按钮
  modalBtns: {
    display: "flex",
    gap: "var(--space-3)",
  },
  cancelBtn: {
    flex: 1,
    background: "var(--bg-card)",
    color: "var(--text-primary)",
    padding: "14px",
    borderRadius: "var(--radius-md)",
    fontSize: "var(--text-base)",
    border: "1px solid var(--border-color)",
  },
  confirmBtn: {
    flex: 1,
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    color: "white",
    padding: "14px",
    borderRadius: "var(--radius-md)",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
  },
};
