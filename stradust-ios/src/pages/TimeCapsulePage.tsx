import { useState, useEffect, useCallback, useRef } from "react";
import {
  TimeCapsule,
  loadTimeCapsules,
  saveTimeCapsules,
  generateId,
  formatTimestamp,
} from "../utils/api";
import {
  listStaggerIn,
  bottomSheetIn,
  glowPulse,
} from "../utils/animations";

interface TimeCapsulePageProps {
  onBack: () => void;
}

type CapsuleStatus = "unopened" | "expired" | "opened";

function getCapsuleStatus(capsule: TimeCapsule): CapsuleStatus {
  if (capsule.is_opened) return "opened";
  if (Date.now() >= capsule.open_date) return "expired";
  return "unopened";
}

function getStatusLabel(status: CapsuleStatus): string {
  const map: Record<CapsuleStatus, string> = {
    unopened: "封存中",
    expired: "可开启",
    opened: "已开启",
  };
  return map[status];
}

function getStatusEmoji(status: CapsuleStatus): string {
  const map: Record<CapsuleStatus, string> = {
    unopened: "🔒",
    expired: "✨",
    opened: "💌",
  };
  return map[status];
}

function getStatusBg(status: CapsuleStatus): string {
  const map: Record<CapsuleStatus, string> = {
    unopened: "rgba(139, 108, 255, 0.15)",
    expired: "rgba(255, 213, 92, 0.15)",
    opened: "rgba(92, 255, 180, 0.15)",
  };
  return map[status];
}

function getStatusColor(status: CapsuleStatus): string {
  const map: Record<CapsuleStatus, string> = {
    unopened: "var(--accent-primary)",
    expired: "var(--accent-yellow)",
    opened: "var(--accent-green)",
  };
  return map[status];
}

function getCardBorderLeft(status: CapsuleStatus): string {
  const map: Record<CapsuleStatus, string> = {
    unopened: "3px solid var(--accent-secondary)",
    expired: "3px solid var(--accent-green)",
    opened: "3px solid var(--accent-yellow)",
  };
  return map[status];
}

function getCardBorder(status: CapsuleStatus): string {
  const map: Record<CapsuleStatus, string> = {
    unopened: "1px solid var(--border-color)",
    expired: "1px solid rgba(255, 213, 92, 0.4)",
    opened: "1px solid rgba(92, 255, 180, 0.3)",
  };
  return map[status];
}

function formatOpenDate(ts: number): string {
  const d = new Date(ts);
  const year = d.getFullYear();
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const weekDays = ["日", "一", "二", "三", "四", "五", "六"];
  const weekDay = weekDays[d.getDay()];
  return `${year}年${month}月${day}日 周${weekDay}`;
}

function getTimeRemaining(ts: number): string {
  const diff = ts - Date.now();
  if (diff <= 0) return "已到开启时间";
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
  if (days > 0) return `还有${days}天${hours}小时`;
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  if (hours > 0) return `还有${hours}小时${minutes}分钟`;
  return `还有${minutes}分钟`;
}

export default function TimeCapsulePage({ onBack }: TimeCapsulePageProps) {
  const [capsules, setCapsules] = useState<TimeCapsule[]>([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newContent, setNewContent] = useState("");
  const [newOpenDate, setNewOpenDate] = useState("");
  const [newFromSelf, setNewFromSelf] = useState(true);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);

  const listRef = useRef<HTMLDivElement>(null);
  const createModalRef = useRef<HTMLDivElement>(null);
  const openBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    loadTimeCapsules().then((data) => {
      if (data && data.length > 0) {
        setCapsules(data.sort((a, b) => a.open_date - b.open_date));
      }
    });
  }, []);

  // 列表入场动画
  useEffect(() => {
    if (capsules.length > 0 && listRef.current) {
      const cards = listRef.current.querySelectorAll(".capsule-card");
      if (cards.length > 0) {
        listStaggerIn(cards as unknown as Element[]);
      }
    }
  }, [capsules.length]);

  // 创建弹窗入场动画
  useEffect(() => {
    if (showCreateModal && createModalRef.current) {
      bottomSheetIn(createModalRef.current);
    }
  }, [showCreateModal]);

  // 可开启按钮发光动画
  useEffect(() => {
    let anim: ReturnType<typeof glowPulse> | null = null;
    if (openBtnRef.current) {
      anim = glowPulse(openBtnRef.current);
    }
    return () => { if (anim) anim.revert(); };
  }, [expandedId]);

  const persistCapsules = useCallback(async (updated: TimeCapsule[]) => {
    const sorted = [...updated].sort((a, b) => a.open_date - b.open_date);
    setCapsules(sorted);
    await saveTimeCapsules(sorted);
  }, []);

  const handleCreate = async () => {
    const title = newTitle.trim();
    const content = newContent.trim();
    if (!title || !content || !newOpenDate) return;

    const openDate = new Date(newOpenDate).getTime();
    if (openDate <= Date.now()) {
      alert("开启日期必须晚于当前时间");
      return;
    }

    const capsule: TimeCapsule = {
      id: generateId(),
      title,
      content,
      created_at: Date.now(),
      open_date: openDate,
      is_opened: false,
      from_self: newFromSelf,
    };

    const updated = [...capsules, capsule];
    await persistCapsules(updated);
    setNewTitle("");
    setNewContent("");
    setNewOpenDate("");
    setNewFromSelf(true);
    setShowCreateModal(false);
  };

  const handleOpen = async (id: string) => {
    const updated = capsules.map((c) =>
      c.id === id ? { ...c, is_opened: true } : c
    );
    await persistCapsules(updated);
    setExpandedId(id);
  };

  const handleDelete = async (id: string) => {
    const updated = capsules.filter((c) => c.id !== id);
    await persistCapsules(updated);
    setDeleteConfirmId(null);
    if (expandedId === id) setExpandedId(null);
  };

  const handleToggleExpand = (id: string) => {
    setExpandedId(expandedId === id ? null : id);
  };

  // 统计
  const unopenedCount = capsules.filter((c) => getCapsuleStatus(c) === "unopened").length;
  const expiredCount = capsules.filter((c) => getCapsuleStatus(c) === "expired").length;
  const openedCount = capsules.filter((c) => getCapsuleStatus(c) === "opened").length;

  // 获取最小可选日期（明天）
  const getMinDate = () => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().split("T")[0];
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
        <span style={styles.headerTitle}>时光胶囊</span>
        <button
          style={styles.addBtn}
          onClick={() => setShowCreateModal(true)}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>

      {/* 统计栏 */}
      {capsules.length > 0 && (
        <div style={styles.statsBar}>
          <div style={styles.statItem}>
            <span style={styles.statEmoji}>🔒</span>
            <span style={{ ...styles.statCount, color: "var(--accent-primary)" }}>{unopenedCount}</span>
            <span style={styles.statLabel}>封存中</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={styles.statEmoji}>✨</span>
            <span style={{ ...styles.statCount, color: "var(--accent-yellow)" }}>{expiredCount}</span>
            <span style={styles.statLabel}>可开启</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={styles.statEmoji}>💌</span>
            <span style={{ ...styles.statCount, color: "var(--accent-green)" }}>{openedCount}</span>
            <span style={styles.statLabel}>已开启</span>
          </div>
        </div>
      )}

      {/* 胶囊列表 */}
      <div style={styles.capsuleList} ref={listRef}>
        {capsules.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={styles.emptyEmoji}>⏳</span>
            <span style={styles.emptyText}>还没有时光胶囊</span>
            <span style={styles.emptyHint}>点击右上角「+」，给未来的自己或星尘写一封信吧</span>
          </div>
        ) : (
          capsules.map((capsule) => {
            const status = getCapsuleStatus(capsule);
            const isExpanded = expandedId === capsule.id;
            return (
              <div
                key={capsule.id}
                className="capsule-card"
                style={{
                  ...styles.capsuleCard,
                  border: getCardBorder(status),
                  borderLeft: getCardBorderLeft(status),
                }}
                onClick={() => handleToggleExpand(capsule.id)}
              >
                <div style={styles.capsuleHeader}>
                  <div style={styles.capsuleHeaderLeft}>
                    <span style={styles.capsuleEmoji}>{getStatusEmoji(status)}</span>
                    <div style={styles.capsuleTitleArea}>
                      <span style={styles.capsuleTitle}>{capsule.title}</span>
                      <span style={styles.capsuleOpenDate}>
                        开启日期：{formatOpenDate(capsule.open_date)}
                      </span>
                    </div>
                  </div>
                  <div style={styles.capsuleHeaderRight}>
                    <span
                      style={{
                        ...styles.statusTag,
                        background: getStatusBg(status),
                        color: getStatusColor(status),
                      }}
                    >
                      {getStatusLabel(status)}
                    </span>
                    {capsule.from_self && (
                      <span style={styles.fromSelfTag}>来自自己</span>
                    )}
                  </div>
                </div>

                {/* 状态信息 */}
                {status === "unopened" && !isExpanded && (
                  <div style={styles.timeRemaining}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--text-muted)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <circle cx="12" cy="12" r="10" />
                      <polyline points="12 6 12 12 16 14" />
                    </svg>
                    <span className="animate-breathe" style={styles.timeRemainingText}>{getTimeRemaining(capsule.open_date)}</span>
                  </div>
                )}

                {status === "expired" && !isExpanded && (
                  <div style={styles.expiredHint}>
                    <span style={styles.expiredHintText}>时光已到，点击开启胶囊</span>
                  </div>
                )}

                {/* 展开区域 */}
                {isExpanded && (
                  <div style={styles.capsuleExpanded}>
                    {status === "opened" ? (
                      <>
                        <div style={styles.openedContent}>
                          <p style={styles.openedContentText}>{capsule.content}</p>
                        </div>
                        <div style={styles.openedMeta}>
                          <span style={styles.openedMetaItem}>
                            创建于 {formatTimestamp(capsule.created_at)}
                          </span>
                          <span style={styles.openedMetaItem}>
                            开启于 {formatTimestamp(capsule.open_date)}
                          </span>
                        </div>
                      </>
                    ) : status === "expired" ? (
                      <div style={styles.openPrompt}>
                        <span style={styles.openPromptEmoji}>🎁</span>
                        <span style={styles.openPromptText}>时光已到！点击下方按钮开启胶囊</span>
                        <button
                          ref={openBtnRef}
                          style={styles.openBtn}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleOpen(capsule.id);
                          }}
                        >
                          开启胶囊
                        </button>
                      </div>
                    ) : (
                      <div style={styles.sealedContent}>
                        <span className="animate-breathe" style={styles.sealedEmoji}>🔒</span>
                        <span style={styles.sealedText}>
                          胶囊尚未到开启时间，还需等待
                        </span>
                        <span style={styles.sealedTime}>
                          {getTimeRemaining(capsule.open_date)}
                        </span>
                      </div>
                    )}

                    {/* 删除按钮 */}
                    <div style={styles.capsuleActions}>
                      <button
                        style={styles.deleteBtn}
                        onClick={(e) => {
                          e.stopPropagation();
                          setDeleteConfirmId(capsule.id);
                        }}
                      >
                        删除胶囊
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* 删除确认弹窗 */}
      {deleteConfirmId && (
        <div style={styles.modalOverlay} onClick={() => setDeleteConfirmId(null)}>
          <div style={styles.deleteConfirmModal} onClick={(e) => e.stopPropagation()}>
            <span style={styles.deleteConfirmEmoji}>🗑️</span>
            <span style={styles.deleteConfirmTitle}>确定删除这个胶囊？</span>
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

      {/* 创建胶囊底部弹窗 */}
      {showCreateModal && (
        <div style={styles.modalOverlay} onClick={() => setShowCreateModal(false)}>
          <div
            ref={createModalRef}
            style={styles.createModal}
            onClick={(e) => e.stopPropagation()}
          >
            {/* 拖拽指示条 */}
            <div style={styles.modalHandle} />

            <h3 style={styles.modalTitle}>创建时光胶囊</h3>

            <div style={styles.field}>
              <label style={styles.label}>标题</label>
              <input
                style={styles.input}
                type="text"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="给胶囊起个名字..."
                maxLength={30}
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>内容</label>
              <textarea
                style={styles.textarea}
                value={newContent}
                onChange={(e) => setNewContent(e.target.value)}
                placeholder="写下你想对未来的自己或星尘说的话..."
                rows={4}
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>开启日期</label>
              <input
                style={styles.input}
                type="date"
                value={newOpenDate}
                onChange={(e) => setNewOpenDate(e.target.value)}
                min={getMinDate()}
              />
            </div>

            <div style={styles.switchRow}>
              <div style={styles.switchLabelArea}>
                <span style={styles.switchLabel}>来自自己</span>
                <span style={styles.switchHint}>标记为写给自己的信</span>
              </div>
              <button
                style={{
                  ...styles.switch,
                  background: newFromSelf
                    ? "var(--accent-primary)"
                    : "var(--bg-input)",
                }}
                onClick={() => setNewFromSelf(!newFromSelf)}
              >
                <div
                  style={{
                    ...styles.switchThumb,
                    transform: newFromSelf
                      ? "translateX(20px)"
                      : "translateX(0)",
                  }}
                />
              </button>
            </div>

            <div style={styles.modalBtns}>
              <button
                style={styles.cancelBtn}
                onClick={() => setShowCreateModal(false)}
              >
                取消
              </button>
              <button
                style={{
                  ...styles.confirmBtn,
                  opacity: newTitle.trim() && newContent.trim() && newOpenDate ? 1 : 0.5,
                }}
                onClick={handleCreate}
                disabled={!newTitle.trim() || !newContent.trim() || !newOpenDate}
              >
                封存胶囊
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
    background: "var(--gradient-primary)",
    color: "var(--text-primary)",
    width: 36,
    height: 36,
    borderRadius: "var(--radius-full)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    padding: 0,
    boxShadow: "var(--shadow-sm)",
  },

  // 统计栏
  statsBar: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-around",
    padding: "var(--space-4) var(--space-4)",
    background: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
  },
  statItem: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: "var(--space-1)",
  },
  statEmoji: {
    fontSize: 20,
  },
  statCount: {
    fontSize: 20,
    fontWeight: "var(--weight-bold)",
  },
  statLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  statDivider: {
    width: 1,
    height: 36,
    background: "var(--border-color)",
  },

  // 胶囊列表
  capsuleList: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-3) var(--space-4)",
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-3)",
    WebkitOverflowScrolling: "touch",
  },
  emptyState: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    flex: 1,
    gap: "var(--space-2)",
    padding: "var(--space-16) var(--space-5)",
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

  // 胶囊卡片
  capsuleCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    padding: "var(--space-4) var(--space-5)",
    cursor: "pointer",
    boxShadow: "var(--shadow-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  capsuleHeader: {
    display: "flex",
    alignItems: "flex-start",
    justifyContent: "space-between",
  },
  capsuleHeaderLeft: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    flex: 1,
    minWidth: 0,
  },
  capsuleEmoji: {
    fontSize: 28,
    flexShrink: 0,
  },
  capsuleTitleArea: {
    display: "flex",
    flexDirection: "column" as const,
    gap: 2,
    minWidth: 0,
  },
  capsuleTitle: {
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap" as const,
  },
  capsuleOpenDate: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  capsuleHeaderRight: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    flexShrink: 0,
  },
  statusTag: {
    fontSize: "var(--text-xs)",
    padding: "3px var(--space-3)",
    borderRadius: "var(--radius-full)",
    fontWeight: "var(--weight-semibold)",
    whiteSpace: "nowrap" as const,
  },
  fromSelfTag: {
    fontSize: "var(--text-xs)",
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-full)",
    background: "rgba(139, 108, 255, 0.12)",
    color: "var(--accent-primary)",
    fontWeight: "var(--weight-medium)",
  },

  // 时间剩余
  timeRemaining: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    marginTop: "var(--space-3)",
  },
  timeRemainingText: {
    fontSize: "var(--text-sm)",
    color: "var(--accent-primary)",
    fontWeight: "var(--weight-medium)",
  },

  // 过期提示
  expiredHint: {
    marginTop: "var(--space-3)",
  },
  expiredHintText: {
    fontSize: "var(--text-sm)",
    color: "var(--accent-yellow)",
    fontWeight: "var(--weight-medium)",
  },

  // 展开区域
  capsuleExpanded: {
    marginTop: "var(--space-3)",
    paddingTop: "var(--space-3)",
    borderTop: "1px solid var(--border-color)",
  },

  // 已开启内容
  openedContent: {
    background: "rgba(92, 255, 180, 0.06)",
    borderRadius: "var(--radius-md)",
    padding: "var(--space-4)",
    marginBottom: "var(--space-3)",
  },
  openedContentText: {
    fontSize: "var(--text-base)",
    color: "var(--text-primary)",
    lineHeight: "var(--leading-relaxed)",
    whiteSpace: "pre-wrap" as const,
    wordBreak: "break-word" as const,
    margin: 0,
  },
  openedMeta: {
    display: "flex",
    flexDirection: "column" as const,
    gap: "var(--space-1)",
  },
  openedMetaItem: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },

  // 可开启提示
  openPrompt: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-5) 0",
  },
  openPromptEmoji: {
    fontSize: 40,
  },
  openPromptText: {
    fontSize: "var(--text-base)",
    color: "var(--text-secondary)",
    textAlign: "center" as const,
  },
  openBtn: {
    background: "var(--gradient-primary)",
    color: "var(--text-primary)",
    padding: "var(--space-3) var(--space-8)",
    borderRadius: "var(--radius-full)",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
    boxShadow: "var(--shadow-glow)",
  },

  // 封存中内容
  sealedContent: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: "var(--space-2)",
    padding: "var(--space-5) 0",
  },
  sealedEmoji: {
    fontSize: 36,
  },
  sealedText: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
  },
  sealedTime: {
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--accent-primary)",
  },

  // 操作按钮
  capsuleActions: {
    display: "flex",
    justifyContent: "flex-end",
    marginTop: "var(--space-4)",
  },
  deleteBtn: {
    padding: "var(--space-2) var(--space-4)",
    background: "rgba(255, 92, 124, 0.15)",
    color: "var(--accent-red)",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    border: "none",
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
    gap: "var(--space-3)",
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
    padding: "var(--space-3)",
    borderRadius: "var(--radius-lg)",
    fontSize: "var(--text-base)",
    border: "1px solid var(--border-color)",
  },
  confirmDeleteBtn: {
    flex: 1,
    background: "rgba(255, 92, 124, 0.9)",
    color: "var(--text-primary)",
    padding: "var(--space-3)",
    borderRadius: "var(--radius-lg)",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
  },

  // 创建胶囊弹窗
  createModal: {
    width: "100%",
    maxWidth: 500,
    background: "var(--bg-secondary)",
    borderRadius: "var(--radius-xl) var(--radius-xl) 0 0",
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
    fontSize: "var(--text-base)",
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
    fontSize: "var(--text-base)",
    outline: "none",
    resize: "none" as const,
    lineHeight: "var(--leading-normal)",
    boxSizing: "border-box" as const,
  },
  switchRow: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-2) 0",
    marginBottom: "var(--space-5)",
  },
  switchLabelArea: {
    display: "flex",
    flexDirection: "column" as const,
    gap: 2,
  },
  switchLabel: {
    fontSize: "var(--text-base)",
    color: "var(--text-primary)",
    fontWeight: "var(--weight-medium)",
  },
  switchHint: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  switch: {
    width: 48,
    height: 28,
    borderRadius: 14,
    padding: 4,
    transition: "all var(--duration-normal) var(--ease-out-expo)",
    border: "none",
    flexShrink: 0,
  },
  switchThumb: {
    width: 20,
    height: 20,
    borderRadius: 10,
    background: "var(--text-primary)",
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
    padding: "var(--space-4)",
    borderRadius: "var(--radius-lg)",
    fontSize: "var(--text-base)",
    border: "1px solid var(--border-color)",
  },
  confirmBtn: {
    flex: 1,
    background: "var(--gradient-primary)",
    color: "var(--text-primary)",
    padding: "var(--space-4)",
    borderRadius: "var(--radius-lg)",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
    boxShadow: "var(--shadow-md)",
  },
};
