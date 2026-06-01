import { useState, useEffect, useCallback, useRef } from "react";
import {
  loadMoments,
  saveMoments,
  loadSettings,
  generateMoment,
  loadAffection,
  DEFAULT_CHARACTER,
  generateId,
  formatTimestamp,
  type Moment as ApiMoment,
  type Comment as ApiComment,
} from "../utils/api";
import {
  listStaggerIn,
  fadeInUp,
  glowPulse,
  buttonPress,
} from "../utils/animations";

interface MomentsPageProps {
  onBack: () => void;
}

export default function MomentsPage({ onBack }: MomentsPageProps) {
  const [moments, setMoments] = useState<ApiMoment[]>([]);
  const [commentInput, setCommentInput] = useState<Record<string, string>>({});
  const [generating, setGenerating] = useState(false);
  const [loading, setLoading] = useState(true);
  const [focusedInput, setFocusedInput] = useState<string | null>(null);
  const [pressedId, setPressedId] = useState<string | null>(null);

  const listRef = useRef<HTMLDivElement>(null);
  const aiBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    async function init() {
      try {
        const saved = await loadMoments();
        if (Array.isArray(saved)) {
          setMoments(saved);
        }
      } catch (e) {
        console.error("Failed to load moments:", e);
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  // 列表入场动画
  useEffect(() => {
    if (!loading && moments.length > 0 && listRef.current) {
      const cards = listRef.current.querySelectorAll(".moment-card");
      if (cards.length > 0) {
        listStaggerIn(cards as unknown as Element[]);
      }
    }
  }, [loading, moments.length]);

  // AI 按钮发光动画
  useEffect(() => {
    let anim: ReturnType<typeof glowPulse> | null = null;
    if (aiBtnRef.current && !generating) {
      anim = glowPulse(aiBtnRef.current);
    }
    return () => { if (anim) anim.revert(); };
  }, [generating]);

  const persistMoments = useCallback(async (updated: ApiMoment[]) => {
    try {
      await saveMoments(updated);
    } catch (e) {
      console.error("Failed to save moments:", e);
    }
  }, []);

  const handleGenerateMoment = async () => {
    if (aiBtnRef.current) buttonPress(aiBtnRef.current);
    setGenerating(true);
    try {
      const settings = await loadSettings();
      const affection = await loadAffection();
      const content = await generateMoment(
        settings.api_config.chat_api_url,
        settings.api_config.api_key,
        settings.api_config.model_name,
        DEFAULT_CHARACTER.name,
        DEFAULT_CHARACTER.system_prompt,
        affection.level,
      );
      const newMoment: ApiMoment = {
        id: generateId(),
        author: DEFAULT_CHARACTER.name,
        content,
        image_path: "",
        created_at: Date.now(),
        comments: [],
      };
      const updated = [newMoment, ...moments];
      setMoments(updated);
      await persistMoments(updated);
    } catch (e) {
      console.error("Failed to generate moment:", e);
    } finally {
      setGenerating(false);
    }
  };

  const handleSendComment = async (momentId: string) => {
    const text = (commentInput[momentId] || "").trim();
    if (!text) return;

    const newComment: ApiComment = {
      id: generateId(),
      author: "我",
      content: text,
      created_at: Date.now(),
    };

    const updated = moments.map((m) =>
      m.id === momentId
        ? { ...m, comments: [...m.comments, newComment] }
        : m
    );
    setMoments(updated);
    setCommentInput((prev) => ({ ...prev, [momentId]: "" }));
    await persistMoments(updated);

    // 评论入场动画
    requestAnimationFrame(() => {
      const commentEl = document.querySelector(`[data-comment-id="${newComment.id}"]`);
      if (commentEl) fadeInUp(commentEl);
    });
  };

  const getInitial = (name: string) => {
    return name ? name.charAt(0).toUpperCase() : "?";
  };

  return (
    <div style={styles.container}>
      <div style={styles.navBar}>
        <button style={styles.backButton} onClick={onBack}>
          ← 返回
        </button>
        <span style={styles.navTitle}>朋友圈</span>
        <div style={styles.navRight}>
          <button
            ref={aiBtnRef}
            style={{
              ...styles.aiButton,
              opacity: generating ? 0.6 : 1,
            }}
            onClick={handleGenerateMoment}
            disabled={generating}
          >
            {generating ? "生成中..." : "✨ AI发动态"}
          </button>
        </div>
      </div>

      <div style={styles.content} ref={listRef}>
        {loading ? (
          <div style={styles.emptyState}>加载中...</div>
        ) : moments.length === 0 ? (
          <div style={styles.emptyState}>
            <div style={styles.emptyEmoji}>📭</div>
            <div>还没有动态，点击右上角「AI发动态」开始吧</div>
          </div>
        ) : (
          moments.map((moment) => (
            <div
              key={moment.id}
              className="moment-card"
              style={{
                ...styles.momentCard,
                transform: pressedId === moment.id ? "scale(0.97)" : "scale(1)",
              }}
              onMouseDown={() => setPressedId(moment.id)}
              onMouseUp={() => setPressedId(null)}
              onMouseLeave={() => setPressedId(null)}
            >
              <div style={styles.momentHeader}>
                <div style={styles.avatar}>{getInitial(moment.author)}</div>
                <div style={styles.authorInfo}>
                  <div style={styles.authorName}>{moment.author}</div>
                  <div style={styles.momentTime}>
                    {formatTimestamp(moment.created_at)}
                  </div>
                </div>
              </div>

              <div style={styles.momentContent}>{moment.content}</div>

              <div style={styles.commentSection}>
                {moment.comments.map((comment) => (
                  <div
                    key={comment.id}
                    data-comment-id={comment.id}
                    style={styles.commentItem}
                  >
                    <span style={styles.commentAuthor}>{comment.author}</span>
                    <span style={styles.commentContent}>{comment.content}</span>
                    <span style={styles.commentTime}>
                      {formatTimestamp(comment.created_at)}
                    </span>
                  </div>
                ))}

                <div style={styles.commentInputRow}>
                  <input
                    style={{
                      ...styles.commentInput,
                      boxShadow: focusedInput === moment.id
                        ? "0 0 0 2px var(--accent-primary), 0 0 12px rgba(139, 108, 255, 0.3)"
                        : "none",
                    }}
                    type="text"
                    placeholder="写评论..."
                    value={commentInput[moment.id] || ""}
                    onChange={(e) =>
                      setCommentInput((prev) => ({
                        ...prev,
                        [moment.id]: e.target.value,
                      }))
                    }
                    onFocus={() => setFocusedInput(moment.id)}
                    onBlur={() => setFocusedInput(null)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") handleSendComment(moment.id);
                    }}
                  />
                  <button
                    style={styles.sendButton}
                    onClick={() => handleSendComment(moment.id)}
                  >
                    发送
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: "flex",
    flexDirection: "column",
    height: "100%",
    background: "var(--bg-primary)",
    color: "var(--text-primary)",
    overflow: "hidden",
  },
  navBar: {
    display: "flex",
    alignItems: "center",
    padding: "var(--space-3) var(--space-4)",
    paddingTop: "calc(var(--space-3) + var(--safe-top))",
    background: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
    flexShrink: 0,
  },
  backButton: {
    background: "none",
    border: "none",
    color: "var(--accent-primary)",
    fontSize: "var(--text-base)",
    cursor: "pointer",
    padding: "var(--space-1) var(--space-2)",
    marginRight: "var(--space-2)",
  },
  navTitle: {
    fontSize: "var(--text-lg)",
    fontWeight: "var(--weight-bold)",
    letterSpacing: "var(--tracking-tight)",
    color: "var(--text-primary)",
  },
  navRight: {
    marginLeft: "auto",
  },
  aiButton: {
    background: "var(--gradient-primary)",
    border: "none",
    color: "var(--text-primary)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    letterSpacing: "var(--tracking-wide)",
    padding: "var(--space-2) var(--space-4)",
    borderRadius: "var(--radius-full)",
    cursor: "pointer",
    boxShadow: "var(--shadow-glow)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  content: {
    flex: 1,
    overflowY: "auto",
    padding: "0 0 var(--space-5) 0",
  },
  momentCard: {
    background: "var(--bg-card)",
    margin: "var(--space-3) var(--space-4)",
    borderRadius: "var(--radius-lg)",
    padding: "var(--space-4) var(--space-5)",
    border: "1px solid var(--border-color)",
    boxShadow: "var(--shadow-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  momentHeader: {
    display: "flex",
    alignItems: "center",
    marginBottom: "var(--space-3)",
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: "var(--radius-full)",
    background: "var(--gradient-primary)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: "var(--text-base)",
    color: "var(--text-primary)",
    flexShrink: 0,
  },
  authorInfo: {
    marginLeft: "var(--space-3)",
    flex: 1,
  },
  authorName: {
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  momentTime: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    marginTop: 2,
  },
  momentContent: {
    fontSize: "var(--text-base)",
    lineHeight: "var(--leading-relaxed)",
    color: "var(--text-primary)",
    marginBottom: "var(--space-3)",
    whiteSpace: "pre-wrap",
    wordBreak: "break-word",
  },
  commentSection: {
    borderTop: "1px solid var(--border-color)",
    paddingTop: "var(--space-3)",
  },
  commentItem: {
    display: "flex",
    marginBottom: "var(--space-2)",
    fontSize: "var(--text-sm)",
    lineHeight: "var(--leading-normal)",
  },
  commentAuthor: {
    color: "var(--accent-primary)",
    fontWeight: "var(--weight-medium)",
    marginRight: "var(--space-2)",
    flexShrink: 0,
  },
  commentContent: {
    color: "var(--text-secondary)",
    wordBreak: "break-word",
  },
  commentTime: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    marginLeft: "var(--space-2)",
    flexShrink: 0,
  },
  commentInputRow: {
    display: "flex",
    alignItems: "center",
    marginTop: "var(--space-3)",
    gap: "var(--space-2)",
  },
  commentInput: {
    flex: 1,
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-full)",
    padding: "var(--space-2) var(--space-4)",
    fontSize: "var(--text-sm)",
    color: "var(--text-primary)",
    outline: "none",
  },
  sendButton: {
    background: "var(--accent-primary)",
    border: "none",
    color: "var(--text-primary)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    padding: "var(--space-2) var(--space-4)",
    borderRadius: "var(--radius-full)",
    cursor: "pointer",
    flexShrink: 0,
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  emptyState: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    padding: "var(--space-16) var(--space-5)",
    color: "var(--text-secondary)",
  },
  emptyEmoji: {
    fontSize: 48,
    marginBottom: "var(--space-4)",
  },
};
