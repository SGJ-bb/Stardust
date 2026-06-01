import { useState, useEffect, useCallback } from "react";
import {
  WakeUpTask,
  loadWakeupTasks,
  saveWakeupTasks,
  generateId,
} from "../utils/api";

export default function WakeUpPage({ onBack }: { onBack: () => void }) {
  const [tasks, setTasks] = useState<WakeUpTask[]>([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newTime, setNewTime] = useState("07:00");
  const [newMessage, setNewMessage] = useState("");
  const [newRepeatDaily, setNewRepeatDaily] = useState(true);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);

  useEffect(() => {
    loadWakeupTasks().then((data) => {
      if (data && data.length > 0) {
        setTasks(data.sort((a, b) => a.time.localeCompare(b.time)));
      }
    });
  }, []);

  const persistTasks = useCallback(async (updated: WakeUpTask[]) => {
    const sorted = [...updated].sort((a, b) => a.time.localeCompare(b.time));
    setTasks(sorted);
    await saveWakeupTasks(sorted);
  }, []);

  const handleCreate = async () => {
    const message = newMessage.trim();
    if (!newTime || !message) return;

    const task: WakeUpTask = {
      id: generateId(),
      time: newTime,
      message,
      enabled: true,
      repeat_daily: newRepeatDaily,
    };

    const updated = [...tasks, task];
    await persistTasks(updated);
    setNewTime("07:00");
    setNewMessage("");
    setNewRepeatDaily(true);
    setShowCreateModal(false);
  };

  const handleToggleEnabled = async (id: string) => {
    const updated = tasks.map((t) =>
      t.id === id ? { ...t, enabled: !t.enabled } : t
    );
    await persistTasks(updated);
  };

  const handleDelete = async (id: string) => {
    const updated = tasks.filter((t) => t.id !== id);
    await persistTasks(updated);
    setDeleteConfirmId(null);
  };

  const enabledCount = tasks.filter((t) => t.enabled).length;

  return (
    <div style={styles.container}>
      {/* 顶部导航栏 */}
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={onBack}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>定时唤醒</span>
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
      {tasks.length > 0 && (
        <div style={styles.statsBar}>
          <div style={styles.statItem}>
            <span style={styles.statEmoji}>⏰</span>
            <span style={styles.statCount}>{tasks.length}</span>
            <span style={styles.statLabel}>全部任务</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={styles.statEmoji}>✅</span>
            <span style={styles.statCount}>{enabledCount}</span>
            <span style={styles.statLabel}>已启用</span>
          </div>
          <div style={styles.statDivider} />
          <div style={styles.statItem}>
            <span style={styles.statEmoji}>🌙</span>
            <span style={styles.statCount}>{tasks.length - enabledCount}</span>
            <span style={styles.statLabel}>已关闭</span>
          </div>
        </div>
      )}

      {/* 任务列表 */}
      <div style={styles.taskList}>
        {tasks.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={styles.emptyEmoji}>⏰</span>
            <span style={styles.emptyText}>还没有唤醒任务</span>
            <span style={styles.emptyHint}>点击右上角「+」，创建一个定时唤醒任务吧</span>
          </div>
        ) : (
          tasks.map((task) => (
            <div
              key={task.id}
              style={{
                ...styles.taskCard,
                opacity: task.enabled ? 1 : 0.6,
              }}
            >
              <div style={styles.taskHeader}>
                <div style={styles.taskHeaderLeft}>
                  <span style={styles.taskTime}>{task.time}</span>
                  <div style={styles.taskInfo}>
                    <span style={styles.taskMessage}>{task.message}</span>
                    <div style={styles.taskTags}>
                      {task.repeat_daily && (
                        <span style={styles.repeatTag}>每日重复</span>
                      )}
                      {!task.enabled && (
                        <span style={styles.disabledTag}>已关闭</span>
                      )}
                    </div>
                  </div>
                </div>
                <div style={styles.taskHeaderRight}>
                  <button
                    style={{
                      ...styles.switch,
                      background: task.enabled
                        ? "var(--accent-primary)"
                        : "var(--bg-input)",
                    }}
                    onClick={() => handleToggleEnabled(task.id)}
                  >
                    <div
                      style={{
                        ...styles.switchThumb,
                        transform: task.enabled
                          ? "translateX(20px)"
                          : "translateX(0)",
                      }}
                    />
                  </button>
                  <button
                    style={styles.deleteBtn}
                    onClick={() => setDeleteConfirmId(task.id)}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="3 6 5 6 21 6" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {/* 删除确认弹窗 */}
      {deleteConfirmId && (
        <div style={styles.modalOverlay} onClick={() => setDeleteConfirmId(null)}>
          <div style={styles.deleteConfirmModal} onClick={(e) => e.stopPropagation()}>
            <span style={styles.deleteConfirmEmoji}>🗑️</span>
            <span style={styles.deleteConfirmTitle}>确定删除这个任务？</span>
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

      {/* 创建任务底部弹窗 */}
      {showCreateModal && (
        <div style={styles.modalOverlay} onClick={() => setShowCreateModal(false)}>
          <div style={styles.createModal} onClick={(e) => e.stopPropagation()}>
            {/* 拖拽指示条 */}
            <div style={styles.modalHandle} />

            <h3 style={styles.modalTitle}>创建唤醒任务</h3>

            <div style={styles.field}>
              <label style={styles.label}>唤醒时间</label>
              <input
                style={styles.timeInput}
                type="time"
                value={newTime}
                onChange={(e) => setNewTime(e.target.value)}
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>唤醒消息</label>
              <input
                style={styles.input}
                type="text"
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                placeholder="输入唤醒时显示的消息..."
                maxLength={50}
              />
            </div>

            <div style={styles.switchRow}>
              <div style={styles.switchLabelArea}>
                <span style={styles.switchLabel}>每日重复</span>
                <span style={styles.switchHint}>开启后每天同一时间唤醒</span>
              </div>
              <button
                style={{
                  ...styles.switch,
                  background: newRepeatDaily
                    ? "var(--accent-primary)"
                    : "var(--bg-input)",
                }}
                onClick={() => setNewRepeatDaily(!newRepeatDaily)}
              >
                <div
                  style={{
                    ...styles.switchThumb,
                    transform: newRepeatDaily
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
                  opacity: newTime && newMessage.trim() ? 1 : 0.5,
                }}
                onClick={handleCreate}
                disabled={!newTime || !newMessage.trim()}
              >
                创建任务
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
    padding: "14px var(--space-4)",
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
    color: "var(--text-primary)",
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

  // 任务列表
  taskList: {
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

  // 任务卡片
  taskCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    padding: "var(--space-4) var(--space-5)",
    border: "1px solid var(--border-color)",
    borderLeft: "3px solid var(--accent-primary)",
    boxShadow: "var(--shadow-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  taskHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  taskHeaderLeft: {
    display: "flex",
    alignItems: "center",
    gap: 14,
    flex: 1,
    minWidth: 0,
  },
  taskTime: {
    fontSize: 28,
    fontWeight: "var(--weight-bold)",
    color: "var(--accent-primary)",
    flexShrink: 0,
    fontVariantNumeric: "tabular-nums",
  },
  taskInfo: {
    display: "flex",
    flexDirection: "column" as const,
    gap: "var(--space-1)",
    minWidth: 0,
  },
  taskMessage: {
    fontSize: "15px",
    color: "var(--text-primary)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap" as const,
  },
  taskTags: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-1)",
  },
  repeatTag: {
    fontSize: "var(--text-xs)",
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-sm)",
    background: "rgba(139, 108, 255, 0.12)",
    color: "var(--accent-primary)",
    fontWeight: "var(--weight-medium)",
  },
  disabledTag: {
    fontSize: "var(--text-xs)",
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-sm)",
    background: "rgba(255, 92, 124, 0.12)",
    color: "var(--accent-red)",
    fontWeight: "var(--weight-medium)",
  },
  taskHeaderRight: {
    display: "flex",
    alignItems: "center",
    gap: 10,
    flexShrink: 0,
  },

  // 开关
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
    background: "white",
    transition: "all var(--duration-normal) var(--ease-out-expo)",
  },

  // 删除按钮
  deleteBtn: {
    background: "rgba(255, 92, 124, 0.15)",
    color: "var(--accent-red)",
    width: 32,
    height: 32,
    borderRadius: "var(--radius-sm)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    padding: 0,
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

  // 创建任务弹窗
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
  timeInput: {
    width: "100%",
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-md)",
    color: "var(--text-primary)",
    padding: "var(--space-3)",
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    outline: "none",
    boxSizing: "border-box" as const,
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
    fontSize: "15px",
    color: "var(--text-primary)",
    fontWeight: "var(--weight-medium)",
  },
  switchHint: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
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
