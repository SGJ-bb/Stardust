import { useState, useEffect } from "react";
import {
  BubbleSkin,
  loadSkins,
  saveSkins,
  DEFAULT_SKINS,
} from "../utils/api";

interface SkinShopPageProps {
  onBack: () => void;
}

export default function SkinShopPage({ onBack }: SkinShopPageProps) {
  const [skins, setSkins] = useState<BubbleSkin[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [previewSkin, setPreviewSkin] = useState<BubbleSkin | null>(null);

  useEffect(() => {
    async function init() {
      try {
        const saved = await loadSkins();
        if (saved && saved.length > 0) {
          setSkins(saved);
          const active = saved.find((s) => s.is_active);
          if (active) setPreviewSkin(active);
        } else {
          const initialized = DEFAULT_SKINS.map((s, i) => ({
            ...s,
            is_active: i === 0,
          }));
          setSkins(initialized);
          setPreviewSkin(initialized[0]);
          await saveSkins(initialized);
        }
      } catch {
        const initialized = DEFAULT_SKINS.map((s, i) => ({
          ...s,
          is_active: i === 0,
        }));
        setSkins(initialized);
        setPreviewSkin(initialized[0]);
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  const handleActivate = async (skinId: string) => {
    const updated = skins.map((s) => ({
      ...s,
      is_active: s.id === skinId,
    }));
    setSkins(updated);
    const active = updated.find((s) => s.id === skinId);
    if (active) setPreviewSkin(active);

    setSaving(true);
    try {
      await saveSkins(updated);
    } catch (e) {
      console.error("保存皮肤失败:", e);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={styles.container}>
        <div style={styles.header}>
          <button style={styles.backBtn} onClick={onBack}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
          <span style={styles.headerTitle}>皮肤商店</span>
          <div style={{ width: 40 }} />
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
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>皮肤商店</span>
        <div style={{ width: 40 }} />
      </div>

      <div style={styles.content}>
        {/* 预览区域 */}
        {previewSkin && (
          <div style={styles.previewSection}>
            <div style={styles.sectionTitle}>预览效果</div>
            <div style={styles.previewCard}>
              <div style={styles.previewChat}>
                {/* 用户气泡 */}
                <div style={styles.previewRow}>
                  <div style={styles.previewLabel}>你</div>
                  <div
                    style={{
                      ...styles.previewBubble,
                      backgroundColor: previewSkin.user_bg_color,
                      color: previewSkin.user_text_color,
                      borderRadius: previewSkin.corner_radius,
                    }}
                  >
                    今天天气真好呀~
                  </div>
                </div>
                {/* AI 气泡 */}
                <div style={{ ...styles.previewRow, justifyContent: "flex-start" }}>
                  <div
                    style={{
                      ...styles.previewBubble,
                      backgroundColor: previewSkin.ai_bg_color,
                      color: previewSkin.ai_text_color,
                      borderRadius: previewSkin.corner_radius,
                    }}
                  >
                    哼，才不是因为你在才觉得好的...
                  </div>
                  <div style={styles.previewLabel}>星尘</div>
                </div>
              </div>
              <div style={styles.previewSkinName}>
                当前：{previewSkin.name}
              </div>
            </div>
          </div>
        )}

        {/* 皮肤网格 */}
        <div style={styles.sectionTitle}>选择皮肤</div>
        <div style={styles.skinGrid}>
          {skins.map((skin) => (
            <div
              key={skin.id}
              style={{
                ...styles.skinCard,
                borderColor: skin.is_active
                  ? "var(--accent-primary)"
                  : "var(--border-color)",
                boxShadow: skin.is_active
                  ? "0 0 12px rgba(139, 108, 255, 0.3)"
                  : "none",
              }}
              onClick={() => handleActivate(skin.id)}
            >
              {/* 激活标记 */}
              {skin.is_active && (
                <div style={styles.activeBadge}>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3">
                    <path d="M20 6L9 17l-5-5" />
                  </svg>
                </div>
              )}

              {/* 颜色预览 */}
              <div style={styles.colorPreviewRow}>
                <div style={styles.colorPreviewItem}>
                  <div
                    style={{
                      ...styles.colorBlock,
                      backgroundColor: skin.user_bg_color,
                      borderRadius: skin.corner_radius / 2,
                    }}
                  />
                  <span style={styles.colorLabel}>你</span>
                </div>
                <div style={styles.colorPreviewItem}>
                  <div
                    style={{
                      ...styles.colorBlock,
                      backgroundColor: skin.ai_bg_color,
                      borderRadius: skin.corner_radius / 2,
                    }}
                  />
                  <span style={styles.colorLabel}>AI</span>
                </div>
              </div>

              {/* 皮肤名称 */}
              <div style={styles.skinName}>{skin.name}</div>

              {/* 圆角指示 */}
              <div style={styles.skinMeta}>
                圆角 {skin.corner_radius}px
              </div>
            </div>
          ))}
        </div>

        {/* 保存状态提示 */}
        {saving && (
          <div style={styles.savingHint}>保存中...</div>
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
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
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
  },
  headerTitle: {
    fontSize: "var(--text-lg)",
    fontWeight: "var(--weight-bold)",
    letterSpacing: "var(--tracking-tight)",
    color: "var(--text-primary)",
  },
  content: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-4)",
    paddingBottom: "calc(var(--space-4) + var(--safe-bottom))",
    WebkitOverflowScrolling: "touch",
  },

  // 加载状态
  loadingWrap: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    height: "60vh",
  },
  loadingSpinner: {
    width: 36,
    height: 36,
    border: "3px solid rgba(255,255,255,0.1)",
    borderTopColor: "var(--accent-primary)",
    borderRadius: "50%",
    animation: "spin 0.8s linear infinite",
  },
  loadingText: {
    marginTop: "var(--space-3)",
    color: "var(--text-secondary)",
    fontSize: "var(--text-sm)",
  },

  // 预览区域
  previewSection: {
    marginBottom: "var(--space-6)",
  },
  sectionTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    letterSpacing: "var(--tracking-wide)",
    color: "var(--text-secondary)",
    textTransform: "uppercase",
    marginBottom: "var(--space-3)",
  },
  previewCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
    padding: "var(--space-5)",
    boxShadow: "var(--shadow-sm)",
  },
  previewChat: {
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-3)",
  },
  previewRow: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    justifyContent: "flex-end",
  },
  previewLabel: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    flexShrink: 0,
    minWidth: 32,
  },
  previewBubble: {
    padding: "10px 14px",
    fontSize: "var(--text-sm)",
    lineHeight: 1.5,
    maxWidth: "70%",
  },
  previewSkinName: {
    textAlign: "center",
    marginTop: "var(--space-4)",
    fontSize: "var(--text-sm)",
    color: "var(--accent-primary)",
    fontWeight: "var(--weight-medium)",
  },

  // 皮肤网格
  skinGrid: {
    display: "grid",
    gridTemplateColumns: "repeat(2, 1fr)",
    gap: "var(--space-3)",
  },
  skinCard: {
    position: "relative",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    border: "2px solid var(--border-color)",
    padding: "var(--space-4) var(--space-3)",
    cursor: "pointer",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    boxShadow: "var(--shadow-sm)",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: 10,
  },
  activeBadge: {
    position: "absolute",
    top: "var(--space-2)",
    right: "var(--space-2)",
    width: 20,
    height: 20,
    borderRadius: "50%",
    background: "var(--accent-primary)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  colorPreviewRow: {
    display: "flex",
    gap: "var(--space-3)",
    alignItems: "center",
  },
  colorPreviewItem: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: "var(--space-1)",
  },
  colorBlock: {
    width: 44,
    height: 44,
  },
  colorLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  skinName: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  skinMeta: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },

  // 保存提示
  savingHint: {
    textAlign: "center",
    marginTop: "var(--space-4)",
    fontSize: "var(--text-sm)",
    color: "var(--accent-secondary)",
  },
};
