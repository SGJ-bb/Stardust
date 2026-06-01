import { useState, useEffect } from "react";
import {
  Sticker,
  loadStickers,
  saveStickers,
  generateId,
} from "../utils/api";

interface StickerPageProps {
  onBack: () => void;
}

const EMOTION_EMOJI_MAP: Record<string, string> = {
  happy: "😊",
  sad: "😢",
  angry: "😠",
  surprised: "😲",
  tsundere: "😤",
  neutral: "😐",
  love: "❤️",
};

const EMOTION_LABELS: Record<string, string> = {
  happy: "开心",
  sad: "难过",
  angry: "生气",
  surprised: "惊讶",
  tsundere: "傲娇",
  neutral: "平静",
  love: "喜欢",
};

const EMOTION_COLORS: Record<string, string> = {
  happy: "var(--accent-green)",
  sad: "var(--accent-secondary)",
  angry: "var(--accent-red)",
  surprised: "var(--accent-orange)",
  tsundere: "var(--accent-pink)",
  neutral: "var(--text-secondary)",
  love: "var(--accent-pink)",
};

const EMOTION_OPTIONS = [
  { key: "happy", label: "开心", emoji: "😊" },
  { key: "sad", label: "难过", emoji: "😢" },
  { key: "angry", label: "生气", emoji: "😠" },
  { key: "surprised", label: "惊讶", emoji: "😲" },
  { key: "tsundere", label: "傲娇", emoji: "😤" },
  { key: "neutral", label: "平静", emoji: "😐" },
  { key: "love", label: "喜欢", emoji: "❤️" },
];

const DEFAULT_STICKERS: Sticker[] = [
  {
    id: "sticker_default_1",
    file_path: "",
    description: "哼，才不是为你开心呢",
    emotion: "tsundere",
    tags: ["傲娇", "日常"],
  },
  {
    id: "sticker_default_2",
    file_path: "",
    description: "今天心情超好！",
    emotion: "happy",
    tags: ["开心", "日常"],
  },
  {
    id: "sticker_default_3",
    file_path: "",
    description: "呜呜...好难过",
    emotion: "sad",
    tags: ["难过", "安慰"],
  },
  {
    id: "sticker_default_4",
    file_path: "",
    description: "气死我了！",
    emotion: "angry",
    tags: ["生气", "发泄"],
  },
  {
    id: "sticker_default_5",
    file_path: "",
    description: "诶？！真的吗！",
    emotion: "surprised",
    tags: ["惊讶", "日常"],
  },
  {
    id: "sticker_default_6",
    file_path: "",
    description: "嗯...随便吧",
    emotion: "neutral",
    tags: ["平静", "日常"],
  },
  {
    id: "sticker_default_7",
    file_path: "",
    description: "最喜欢你了~",
    emotion: "love",
    tags: ["喜欢", "甜蜜"],
  },
  {
    id: "sticker_default_8",
    file_path: "",
    description: "才没有想你呢！",
    emotion: "tsundere",
    tags: ["傲娇", "想念"],
  },
  {
    id: "sticker_default_9",
    file_path: "",
    description: "嘿嘿，好开心呀",
    emotion: "happy",
    tags: ["开心", "日常"],
  },
];

export default function StickerPage({ onBack }: StickerPageProps) {
  const [stickers, setStickers] = useState<Sticker[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterEmotion, setFilterEmotion] = useState<string>("all");
  const [showAddSheet, setShowAddSheet] = useState(false);
  const [newDescription, setNewDescription] = useState("");
  const [newEmotion, setNewEmotion] = useState("happy");
  const [newTags, setNewTags] = useState("");
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [pressedStickerId, setPressedStickerId] = useState<string | null>(null);

  useEffect(() => {
    async function init() {
      try {
        const saved = await loadStickers();
        if (saved && saved.length > 0) {
          setStickers(saved);
        } else {
          setStickers(DEFAULT_STICKERS);
          await saveStickers(DEFAULT_STICKERS);
        }
      } catch (e) {
        console.error("加载贴纸数据失败:", e);
        setStickers(DEFAULT_STICKERS);
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  const filteredStickers =
    filterEmotion === "all"
      ? stickers
      : stickers.filter((s) => s.emotion === filterEmotion);

  const handleAddSticker = async () => {
    if (!newDescription.trim()) return;

    const tags = newTags
      .split(/[,，\s]+/)
      .map((t) => t.trim())
      .filter((t) => t.length > 0);

    const newSticker: Sticker = {
      id: generateId(),
      file_path: "",
      description: newDescription.trim(),
      emotion: newEmotion,
      tags,
    };

    const updated = [...stickers, newSticker];
    await saveStickers(updated);
    setStickers(updated);
    setNewDescription("");
    setNewEmotion("happy");
    setNewTags("");
    setShowAddSheet(false);
  };

  const handleDeleteSticker = async (id: string) => {
    const updated = stickers.filter((s) => s.id !== id);
    await saveStickers(updated);
    setStickers(updated);
    setDeletingId(null);
  };

  if (loading) {
    return (
      <div style={styles.container}>
        <div style={styles.header}>
          <button style={styles.backBtn} onClick={onBack}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
          <span style={styles.headerTitle}>贴纸收藏</span>
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
        <span style={styles.headerTitle}>贴纸收藏</span>
        <button style={styles.addBtn} onClick={() => setShowAddSheet(true)}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
        </button>
      </div>

      {/* 情绪筛选标签栏 */}
      <div style={styles.filterBar}>
        <div style={styles.filterScroll}>
          <button
            style={{
              ...styles.filterChip,
              background: filterEmotion === "all"
                ? "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))"
                : "var(--bg-input)",
              color: filterEmotion === "all" ? "white" : "var(--text-secondary)",
              borderColor: filterEmotion === "all" ? "transparent" : "var(--border-color)",
              boxShadow: filterEmotion === "all"
                ? "0 0 12px rgba(139, 108, 255, 0.4)"
                : "none",
            }}
            onClick={() => setFilterEmotion("all")}
          >
            全部
          </button>
          {EMOTION_OPTIONS.map((opt) => (
            <button
              key={opt.key}
              style={{
                ...styles.filterChip,
                background: filterEmotion === opt.key
                  ? `${EMOTION_COLORS[opt.key]}22`
                  : "var(--bg-input)",
                color: filterEmotion === opt.key
                  ? EMOTION_COLORS[opt.key]
                  : "var(--text-secondary)",
                borderColor: filterEmotion === opt.key
                  ? EMOTION_COLORS[opt.key]
                  : "var(--border-color)",
                boxShadow: filterEmotion === opt.key
                  ? `0 0 10px ${EMOTION_COLORS[opt.key]}40`
                  : "none",
              }}
              onClick={() => setFilterEmotion(opt.key)}
            >
              {opt.emoji} {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* 贴纸网格 */}
      <div style={styles.gridWrap}>
        {filteredStickers.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={styles.emptyEmoji}>🎭</span>
            <span style={styles.emptyText}>
              {filterEmotion === "all" ? "还没有贴纸" : "没有该情绪的贴纸"}
            </span>
            <span style={styles.emptyHint}>
              {filterEmotion === "all"
                ? "点击右上角 + 添加你的第一个贴纸吧"
                : "试试选择其他情绪标签"}
            </span>
          </div>
        ) : (
          <div style={styles.grid}>
            {filteredStickers.map((sticker) => (
              <div
                key={sticker.id}
                style={{
                  ...styles.stickerCard,
                  transform: pressedStickerId === sticker.id ? "scale(0.96)" : "scale(1)",
                }}
                onClick={() => setDeletingId(deletingId === sticker.id ? null : sticker.id)}
                onMouseDown={() => setPressedStickerId(sticker.id)}
                onMouseUp={() => setPressedStickerId(null)}
                onMouseLeave={() => setPressedStickerId(null)}
              >
                <div style={styles.stickerEmojiWrap}>
                  <span style={styles.stickerEmoji}>
                    {EMOTION_EMOJI_MAP[sticker.emotion] || "😐"}
                  </span>
                </div>
                <span style={styles.stickerDesc}>
                  {sticker.description.length > 8
                    ? sticker.description.slice(0, 8) + "..."
                    : sticker.description}
                </span>
                <div style={styles.stickerTags}>
                  <span
                    style={{
                      ...styles.emotionTag,
                      color: EMOTION_COLORS[sticker.emotion] || "var(--text-muted)",
                      background: `${EMOTION_COLORS[sticker.emotion] || "#a0a0c0"}18`,
                    }}
                  >
                    {EMOTION_LABELS[sticker.emotion] || sticker.emotion}
                  </span>
                  {sticker.tags.slice(0, 1).map((tag, i) => (
                    <span key={i} style={styles.customTag}>
                      {tag}
                    </span>
                  ))}
                </div>

                {/* 删除确认 */}
                {deletingId === sticker.id && (
                  <div style={styles.deleteOverlay} onClick={(e) => e.stopPropagation()}>
                    <button
                      style={styles.deleteConfirmBtn}
                      onClick={() => handleDeleteSticker(sticker.id)}
                    >
                      删除
                    </button>
                    <button
                      style={styles.deleteCancelBtn}
                      onClick={() => setDeletingId(null)}
                    >
                      取消
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 底部安全区 */}
      <div style={{ height: 40 }} />

      {/* 添加贴纸弹窗 */}
      {showAddSheet && (
        <div style={styles.sheetOverlay} onClick={() => setShowAddSheet(false)}>
          <div style={styles.sheetContent} onClick={(e) => e.stopPropagation()}>
            <div style={styles.sheetHandle} />
            <span style={styles.sheetTitle}>添加新贴纸</span>

            {/* 描述输入 */}
            <div style={styles.fieldGroup}>
              <label style={styles.fieldLabel}>描述</label>
              <input
                style={styles.fieldInput}
                type="text"
                placeholder="输入贴纸描述..."
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                maxLength={50}
              />
            </div>

            {/* 情绪选择 */}
            <div style={styles.fieldGroup}>
              <label style={styles.fieldLabel}>情绪标签</label>
              <div style={styles.emotionPicker}>
                {EMOTION_OPTIONS.map((opt) => (
                  <button
                    key={opt.key}
                    style={{
                      ...styles.emotionOption,
                      background: newEmotion === opt.key
                        ? `${EMOTION_COLORS[opt.key]}22`
                        : "var(--bg-input)",
                      borderColor: newEmotion === opt.key
                        ? EMOTION_COLORS[opt.key]
                        : "var(--border-color)",
                      color: newEmotion === opt.key
                        ? EMOTION_COLORS[opt.key]
                        : "var(--text-secondary)",
                    }}
                    onClick={() => setNewEmotion(opt.key)}
                  >
                    <span style={styles.emotionOptionEmoji}>{opt.emoji}</span>
                    <span style={styles.emotionOptionLabel}>{opt.label}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* 自定义标签 */}
            <div style={styles.fieldGroup}>
              <label style={styles.fieldLabel}>自定义标签</label>
              <input
                style={styles.fieldInput}
                type="text"
                placeholder="用逗号分隔，如：日常,搞笑"
                value={newTags}
                onChange={(e) => setNewTags(e.target.value)}
              />
            </div>

            {/* 提交按钮 */}
            <button
              style={{
                ...styles.submitBtn,
                opacity: newDescription.trim() ? 1 : 0.5,
              }}
              onClick={handleAddSticker}
              disabled={!newDescription.trim()}
            >
              添加贴纸
            </button>
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

  // 顶部导航栏
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
    padding: "var(--space-2)",
    borderRadius: "var(--radius-md)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    border: "none",
  },
  headerPlaceholder: {
    width: 36,
  },

  // 加载状态
  loadingWrap: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    flex: 1,
    gap: "var(--space-3)",
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
    color: "var(--text-secondary)",
    fontSize: "var(--text-sm)",
  },

  // 情绪筛选栏
  filterBar: {
    padding: "10px 0",
    background: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
    flexShrink: 0,
  },
  filterScroll: {
    display: "flex",
    gap: "var(--space-2)",
    overflowX: "auto",
    padding: "0 var(--space-4)",
    WebkitOverflowScrolling: "touch",
    scrollbarWidth: "none",
  },
  filterChip: {
    display: "inline-flex",
    alignItems: "center",
    gap: "var(--space-1)",
    padding: "6px 14px",
    borderRadius: "var(--radius-full)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    whiteSpace: "nowrap" as const,
    border: "1px solid var(--border-color)",
    background: "var(--bg-input)",
    color: "var(--text-secondary)",
    flexShrink: 0,
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    boxShadow: "none",
  },

  // 贴纸网格
  gridWrap: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-3) var(--space-3) 0",
    WebkitOverflowScrolling: "touch",
  },
  grid: {
    display: "grid",
    gridTemplateColumns: "repeat(3, 1fr)",
    gap: 10,
  },
  stickerCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
    padding: "var(--space-3) var(--space-2)",
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: 6,
    position: "relative" as const,
    cursor: "pointer",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    boxShadow: "var(--shadow-sm)",
  },
  stickerEmojiWrap: {
    width: 52,
    height: 52,
    borderRadius: "var(--radius-md)",
    background: "rgba(255, 255, 255, 0.04)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  stickerEmoji: {
    fontSize: 30,
  },
  stickerDesc: {
    fontSize: "var(--text-sm)",
    color: "var(--text-primary)",
    textAlign: "center" as const,
    lineHeight: 1.4,
    width: "100%",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap" as const,
  },
  stickerTags: {
    display: "flex",
    flexWrap: "wrap" as const,
    gap: "var(--space-1)",
    justifyContent: "center",
  },
  emotionTag: {
    fontSize: "var(--text-xs)",
    padding: "1px 6px",
    borderRadius: "var(--radius-sm)",
    fontWeight: "var(--weight-medium)",
  },
  customTag: {
    fontSize: "var(--text-xs)",
    padding: "1px 6px",
    borderRadius: "var(--radius-sm)",
    background: "rgba(255, 255, 255, 0.06)",
    color: "var(--text-muted)",
  },

  // 删除确认浮层
  deleteOverlay: {
    position: "absolute" as const,
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: "rgba(0, 0, 0, 0.7)",
    borderRadius: "var(--radius-lg)",
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    gap: "var(--space-2)",
    zIndex: 10,
    animation: "fadeIn var(--duration-instant) var(--ease-out-quart)",
  },
  deleteConfirmBtn: {
    padding: "6px 20px",
    background: "rgba(255, 92, 124, 0.9)",
    color: "white",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
  },
  deleteCancelBtn: {
    padding: "4px 16px",
    background: "rgba(255, 255, 255, 0.12)",
    color: "var(--text-secondary)",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    border: "none",
  },

  // 空状态
  emptyState: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    justifyContent: "center",
    padding: "80px var(--space-5)",
    gap: "var(--space-2)",
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

  // 底部弹窗
  sheetOverlay: {
    position: "absolute" as const,
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
    zIndex: 200,
    animation: "fadeIn var(--duration-fast) var(--ease-out-quart)",
  },
  sheetContent: {
    background: "var(--bg-secondary)",
    borderRadius: "20px 20px 0 0",
    padding: "var(--space-3) var(--space-5) 32px",
    width: "100%",
    maxWidth: 500,
    border: "1px solid var(--border-color)",
    borderBottom: "none",
    animation: "slideUp var(--duration-normal) var(--ease-out-expo)",
  },
  sheetHandle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    background: "var(--border-color)",
    margin: "0 auto var(--space-4)",
  },
  sheetTitle: {
    display: "block",
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    marginBottom: "var(--space-5)",
    textAlign: "center" as const,
  },

  // 表单字段
  fieldGroup: {
    marginBottom: "var(--space-4)",
  },
  fieldLabel: {
    display: "block",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    color: "var(--text-secondary)",
    marginBottom: "var(--space-1)",
  },
  fieldInput: {
    width: "100%",
    padding: "10px 14px",
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-md)",
    color: "var(--text-primary)",
    fontSize: "15px",
    outline: "none",
    boxSizing: "border-box" as const,
    transition: "border-color var(--duration-fast) var(--ease-out-quart)",
  },

  // 情绪选择器
  emotionPicker: {
    display: "flex",
    flexWrap: "wrap" as const,
    gap: "var(--space-2)",
  },
  emotionOption: {
    display: "flex",
    flexDirection: "column" as const,
    alignItems: "center",
    gap: "var(--space-1)",
    padding: "10px var(--space-3)",
    borderRadius: "var(--radius-md)",
    border: "2px solid var(--border-color)",
    background: "var(--bg-input)",
    minWidth: 56,
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  emotionOptionEmoji: {
    fontSize: 22,
  },
  emotionOptionLabel: {
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-medium)",
  },

  // 提交按钮
  submitBtn: {
    width: "100%",
    padding: "14px 0",
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    color: "white",
    borderRadius: "var(--radius-md)",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
    marginTop: "var(--space-2)",
  },
};
