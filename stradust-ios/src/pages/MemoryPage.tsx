import { useState, useEffect, useRef, useCallback } from "react";
import {
  MemoryEntry,
  loadMemories,
  saveMemories,
  loadSettings,
  evaluateMemories,
  loadChatHistory,
  DEFAULT_CHARACTER,
  generateId,
  formatTimestamp,
  type ApiConfig,
} from "../utils/api";

interface MemoryPageProps {
  onBack: () => void;
}

const CATEGORIES = ["全部", "habit", "preference", "impression", "detail"] as const;
type CategoryFilter = (typeof CATEGORIES)[number];

const CATEGORY_LABELS: Record<string, string> = {
  habit: "习惯",
  preference: "喜好",
  impression: "印象",
  detail: "细节",
};

const CATEGORY_COLORS: Record<string, string> = {
  habit: "var(--accent-primary)",
  preference: "var(--accent-orange)",
  impression: "var(--accent-green)",
  detail: "var(--accent-yellow)",
};

export default function MemoryPage({ onBack }: MemoryPageProps) {
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [filter, setFilter] = useState<CategoryFilter>("全部");
  const [showAddModal, setShowAddModal] = useState(false);
  const [newContent, setNewContent] = useState("");
  const [newCategory, setNewCategory] = useState<string>("habit");
  const [newIsGlobal, setNewIsGlobal] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [evalResult, setEvalResult] = useState<string | null>(null);
  const [longPressId, setLongPressId] = useState<string | null>(null);
  const [swipeX, setSwipeX] = useState<number | null>(null);
  const [swipedId, setSwipedId] = useState<string | null>(null);
  const [apiConfig, setApiConfig] = useState<ApiConfig | null>(null);
  const [activePersonaId, setActivePersonaId] = useState("default_stardust");
  const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    async function init() {
      const settings = await loadSettings();
      if (settings?.api_config) {
        setApiConfig(settings.api_config);
      }
      if (settings?.active_persona_id) {
        setActivePersonaId(settings.active_persona_id);
      }
      const loaded = await loadMemories();
      setMemories(loaded.sort((a, b) => b.timestamp - a.timestamp));
    }
    init();
  }, []);

  const persistMemories = useCallback(async (updated: MemoryEntry[]) => {
    const sorted = [...updated].sort((a, b) => b.timestamp - a.timestamp);
    setMemories(sorted);
    await saveMemories(sorted);
  }, []);

  const handleAddMemory = async () => {
    const text = newContent.trim();
    if (!text) return;
    const entry: MemoryEntry = {
      id: generateId(),
      content: text,
      category: newCategory,
      timestamp: Date.now(),
      is_global: newIsGlobal,
    };
    const updated = [...memories, entry];
    await persistMemories(updated);
    setNewContent("");
    setNewCategory("habit");
    setNewIsGlobal(false);
    setShowAddModal(false);
  };

  const handleDelete = async (id: string) => {
    const updated = memories.filter((m) => m.id !== id);
    await persistMemories(updated);
    setLongPressId(null);
    setSwipedId(null);
  };

  const handleToggleGlobal = async (id: string) => {
    const updated = memories.map((m) =>
      m.id === id ? { ...m, is_global: !m.is_global } : m
    );
    await persistMemories(updated);
  };

  const handleEvaluate = async () => {
    if (!apiConfig) {
      setEvalResult("请先在设置中配置 API");
      return;
    }
    setEvaluating(true);
    setEvalResult(null);
    try {
      const history = await loadChatHistory(activePersonaId);
      const conversationTexts = history.map((msg) => msg.text);
      const newMemories = await evaluateMemories(
        apiConfig.chat_api_url,
        apiConfig.api_key,
        apiConfig.model_name,
        conversationTexts,
        DEFAULT_CHARACTER.name
      );
      if (newMemories.length === 0) {
        setEvalResult("AI 未发现新的记忆");
      } else {
        // 合并新记忆，去重（按内容）
        const existingContents = new Set(memories.map((m) => m.content));
        const uniqueNew = newMemories.filter(
          (m) => !existingContents.has(m.content)
        );
        if (uniqueNew.length === 0) {
          setEvalResult("AI 发现的记忆已全部存在");
        } else {
          const updated = [...memories, ...uniqueNew];
          await persistMemories(updated);
          setEvalResult(`AI 新增了 ${uniqueNew.length} 条记忆`);
        }
      }
    } catch (e) {
      setEvalResult(`评估失败: ${e}`);
    } finally {
      setEvaluating(false);
    }
  };

  // 长按触发删除确认
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

  // 滑动删除
  const handleTouchMoveStart = (id: string, x: number) => {
    setSwipeX(x);
    setSwipedId(id);
  };

  const handleTouchMoveMove = (x: number) => {
    if (swipeX !== null && swipedId) {
      const diff = swipeX - x;
      if (diff > 60) {
        setSwipedId(swipedId);
      }
    }
  };

  const handleTouchMoveEnd = () => {
    setSwipeX(null);
  };

  const filteredMemories =
    filter === "全部"
      ? memories
      : memories.filter((m) => m.category === filter);

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
        <span style={styles.headerTitle}>记忆宫殿</span>
        <button
          style={styles.aiBtn}
          onClick={handleEvaluate}
          disabled={evaluating}
        >
          {evaluating ? "评估中..." : "AI评估"}
        </button>
      </div>

      {/* AI评估结果 */}
      {evalResult && (
        <div style={styles.evalBanner}>
          <span style={styles.evalText}>{evalResult}</span>
          <button
            style={styles.evalClose}
            onClick={() => setEvalResult(null)}
          >
            ✕
          </button>
        </div>
      )}

      {/* 分类筛选 */}
      <div style={styles.filterBar}>
        {CATEGORIES.map((cat) => (
          <button
            key={cat}
            style={{
              ...styles.filterBtn,
              background:
                filter === cat
                  ? "var(--accent-primary)"
                  : "var(--bg-card)",
              color: filter === cat ? "white" : "var(--text-secondary)",
            }}
            onClick={() => setFilter(cat)}
          >
            {cat === "全部" ? "全部" : CATEGORY_LABELS[cat] || cat}
          </button>
        ))}
      </div>

      {/* 记忆列表 */}
      <div style={styles.listContainer}>
        {filteredMemories.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={{ fontSize: 48 }}>🧠</span>
            <span style={styles.emptyText}>
              {filter === "全部" ? "还没有记忆，点击 + 添加" : `没有"${CATEGORY_LABELS[filter] || filter}"类别的记忆`}
            </span>
          </div>
        ) : (
          filteredMemories.map((memory) => (
            <div
              key={memory.id}
              style={styles.memoryCard}
              onTouchStart={() => handleTouchStart(memory.id)}
              onTouchEnd={handleTouchEnd}
              onTouchCancel={handleTouchEnd}
            >
              {/* 滑动删除区域 */}
              <div
                style={{
                  ...styles.memoryContent,
                  transform:
                    swipedId === memory.id ? "translateX(-70px)" : "translateX(0)",
                  transition: "transform 0.2s ease",
                }}
                onTouchStart={(e) =>
                  handleTouchMoveStart(
                    memory.id,
                    e.touches[0].clientX
                  )
                }
                onTouchMove={(e) =>
                  handleTouchMoveMove(e.touches[0].clientX)
                }
                onTouchEnd={handleTouchMoveEnd}
              >
                <div style={styles.memoryTop}>
                  <span
                    style={{
                      ...styles.categoryTag,
                      background:
                        CATEGORY_COLORS[memory.category] || "var(--text-muted)",
                    }}
                  >
                    {CATEGORY_LABELS[memory.category] || memory.category}
                  </span>
                  {memory.is_global && (
                    <span style={styles.globalTag}>全局</span>
                  )}
                  <span style={styles.memoryTime}>
                    {formatTimestamp(memory.timestamp)}
                  </span>
                </div>
                <div style={styles.memoryText}>{memory.content}</div>
                <div style={styles.memoryActions}>
                  <button
                    style={styles.actionBtn}
                    onClick={() => handleToggleGlobal(memory.id)}
                  >
                    {memory.is_global ? "取消全局" : "标记全局"}
                  </button>
                </div>
              </div>

              {/* 删除按钮（滑动露出） */}
              {swipedId === memory.id && (
                <button
                  style={styles.deleteBtn}
                  onClick={() => handleDelete(memory.id)}
                >
                  删除
                </button>
              )}

              {/* 长按删除确认 */}
              {longPressId === memory.id && (
                <div style={styles.deleteOverlay}>
                  <div style={styles.deleteConfirm}>
                    <span style={styles.deleteConfirmText}>
                      确定删除这条记忆？
                    </span>
                    <div style={styles.deleteConfirmBtns}>
                      <button
                        style={styles.confirmDeleteBtn}
                        onClick={() => handleDelete(memory.id)}
                      >
                        删除
                      </button>
                      <button
                        style={styles.cancelDeleteBtn}
                        onClick={() => setLongPressId(null)}
                      >
                        取消
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* 添加记忆按钮 */}
      <button
        style={styles.fabBtn}
        onClick={() => setShowAddModal(true)}
      >
        <svg
          width="28"
          height="28"
          viewBox="0 0 24 24"
          fill="none"
          stroke="white"
          strokeWidth="2.5"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>

      {/* 添加记忆弹窗 */}
      {showAddModal && (
        <div style={styles.modalOverlay} onClick={() => setShowAddModal(false)}>
          <div
            style={styles.modalContent}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={styles.modalTitle}>添加记忆</h3>

            <div style={styles.field}>
              <label style={styles.label}>记忆内容</label>
              <textarea
                style={styles.textarea}
                value={newContent}
                onChange={(e) => setNewContent(e.target.value)}
                placeholder="输入你想记录的内容..."
                rows={4}
                autoFocus
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>分类</label>
              <div style={styles.categoryPicker}>
                {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
                  <button
                    key={key}
                    style={{
                      ...styles.categoryPickBtn,
                      background:
                        newCategory === key
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

            <div style={styles.switchRow}>
              <span style={styles.switchLabel}>全局记忆</span>
              <button
                style={{
                  ...styles.switch,
                  background: newIsGlobal
                    ? "var(--accent-primary)"
                    : "var(--bg-input)",
                }}
                onClick={() => setNewIsGlobal(!newIsGlobal)}
              >
                <div
                  style={{
                    ...styles.switchThumb,
                    transform: newIsGlobal
                      ? "translateX(20px)"
                      : "translateX(0)",
                  }}
                />
              </button>
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
                  opacity: newContent.trim() ? 1 : 0.5,
                }}
                onClick={handleAddMemory}
                disabled={!newContent.trim()}
              >
                添加
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
  aiBtn: {
    background: "var(--bg-card)",
    color: "var(--accent-primary)",
    padding: "6px 14px",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    letterSpacing: "var(--tracking-wide)",
    border: "1px solid var(--accent-primary)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  evalBanner: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "10px var(--space-4)",
    background: "var(--bg-card)",
    borderBottom: "1px solid var(--border-color)",
  },
  evalText: {
    fontSize: "var(--text-sm)",
    color: "var(--accent-primary)",
    flex: 1,
  },
  evalClose: {
    background: "transparent",
    color: "var(--text-muted)",
    fontSize: "var(--text-sm)",
    padding: "var(--space-1) var(--space-2)",
  },
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
  listContainer: {
    flex: 1,
    overflow: "auto",
    padding: "0 var(--space-4) 100px",
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
  memoryCard: {
    position: "relative" as const,
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    marginBottom: "10px",
    border: "1px solid var(--border-color)",
    borderLeft: "3px solid var(--accent-primary)",
    overflow: "hidden",
    boxShadow: "var(--shadow-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  memoryContent: {
    padding: "14px var(--space-4)",
    position: "relative" as const,
    zIndex: 1,
    background: "var(--bg-card)",
  },
  memoryTop: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    marginBottom: "var(--space-2)",
  },
  categoryTag: {
    padding: "2px 10px",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-semibold)",
    color: "white",
  },
  globalTag: {
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--accent-primary)",
    background: "rgba(139, 108, 255, 0.15)",
    border: "1px solid var(--accent-primary)",
  },
  memoryTime: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    marginLeft: "auto",
  },
  memoryText: {
    fontSize: "15px",
    color: "var(--text-primary)",
    lineHeight: 1.6,
    wordBreak: "break-word" as const,
  },
  memoryActions: {
    display: "flex",
    justifyContent: "flex-end",
    marginTop: "var(--space-2)",
    gap: "var(--space-2)",
  },
  actionBtn: {
    background: "transparent",
    color: "var(--text-muted)",
    fontSize: "var(--text-sm)",
    padding: "4px 10px",
    borderRadius: "var(--radius-sm)",
    border: "1px solid var(--border-color)",
  },
  deleteBtn: {
    position: "absolute" as const,
    right: 0,
    top: 0,
    bottom: 0,
    width: 70,
    background: "var(--accent-red)",
    color: "white",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 0,
  },
  deleteOverlay: {
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
  deleteConfirm: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-5)",
  },
  deleteConfirmText: {
    fontSize: "15px",
    color: "white",
    fontWeight: "var(--weight-medium)",
  },
  deleteConfirmBtns: {
    display: "flex",
    gap: "var(--space-3)",
  },
  confirmDeleteBtn: {
    background: "var(--accent-red)",
    color: "white",
    padding: "var(--space-2) 24px",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
  },
  cancelDeleteBtn: {
    background: "var(--bg-card)",
    color: "var(--text-primary)",
    padding: "var(--space-2) 24px",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    border: "1px solid var(--border-color)",
  },
  fabBtn: {
    position: "absolute" as const,
    bottom: "calc(var(--space-6) + var(--safe-bottom))",
    right: "var(--space-5)",
    width: 56,
    height: 56,
    borderRadius: 28,
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    boxShadow: "var(--shadow-md)",
    border: "none",
    zIndex: 5,
  },
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
    alignItems: "flex-end",
    justifyContent: "center",
    zIndex: 100,
  },
  modalContent: {
    width: "100%",
    maxWidth: 500,
    background: "var(--bg-secondary)",
    borderRadius: "20px 20px 0 0",
    padding: "var(--space-6) var(--space-5)",
    paddingBottom: "calc(var(--space-6) + var(--safe-bottom))",
  },
  modalTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-primary)",
    marginBottom: "var(--space-5)",
  },
  field: {
    marginBottom: "var(--space-4)",
  },
  label: {
    display: "block",
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    marginBottom: "var(--space-2)",
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
  switchRow: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-2) 0",
    marginBottom: "var(--space-5)",
  },
  switchLabel: {
    fontSize: "15px",
    color: "var(--text-primary)",
  },
  switch: {
    width: 48,
    height: 28,
    borderRadius: 14,
    padding: 4,
    transition: "all var(--duration-normal) var(--ease-out-expo)",
    border: "none",
  },
  switchThumb: {
    width: 20,
    height: 20,
    borderRadius: 10,
    background: "white",
    transition: "all var(--duration-normal) var(--ease-out-expo)",
  },
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
