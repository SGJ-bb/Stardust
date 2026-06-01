import { useState, useEffect } from "react";
import {
  loadAffection,
  DEFAULT_CHARACTER,
  getAffectionLevelLabel,
  getGrowthStage,
  getGrowthStageIcon,
} from "../utils/api";

interface MorePageProps {
  onBack: () => void;
  onNavigate?: (page: string) => void;
}

interface MenuItem {
  id: string;
  name: string;
  icon: string;
  available: boolean;
}

const menuItems: MenuItem[] = [
  { id: "moments", name: "朋友圈", icon: "📷", available: true },
  { id: "capsule", name: "时光胶囊", icon: "⏳", available: true },
  { id: "groupchat", name: "群聊", icon: "👥", available: true },
  { id: "calendar", name: "日历", icon: "📅", available: true },
  { id: "memorial", name: "纪念相册", icon: "🖼️", available: true },
  { id: "chathistory", name: "聊天记录", icon: "📋", available: true },
  { id: "skinshop", name: "皮肤商店", icon: "👗", available: true },
  { id: "sticker", name: "贴纸", icon: "🎨", available: true },
  { id: "alarm", name: "定时唤醒", icon: "⏰", available: true },
  { id: "settings", name: "设置", icon: "⚙️", available: true },
];

export default function MorePage({ onBack, onNavigate }: MorePageProps) {
  const characterName = DEFAULT_CHARACTER.name;
  const [affectionLabel, setAffectionLabel] = useState("");
  const [growthStage, setGrowthStage] = useState("");
  const [growthIcon, setGrowthIcon] = useState("🌱");
  const [profilePressed, setProfilePressed] = useState(false);
  const [activeGridItem, setActiveGridItem] = useState<string | null>(null);

  useEffect(() => {
    async function loadInfo() {
      try {
        const affection = await loadAffection();
        setAffectionLabel(getAffectionLevelLabel(affection.level));
        const stage = getGrowthStage(affection.level, 0);
        setGrowthStage(stage);
        setGrowthIcon(getGrowthStageIcon(stage));
      } catch {
        // 使用默认值
      }
    }
    loadInfo();
  }, []);

  const handleItemClick = (item: MenuItem) => {
    if (!item.available) {
      alert(`「${item.name}」即将开放，敬请期待！`);
      return;
    }
    if (onNavigate) {
      onNavigate(item.id);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.navBar}>
        <button style={styles.backButton} onClick={onBack}>
          ← 返回
        </button>
        <span style={styles.navTitle}>更多</span>
      </div>

      <div style={styles.content}>
        <div
          style={{
            ...styles.profileCard,
            ...(profilePressed ? { transform: "scale(0.98)" } : {}),
          }}
          onMouseDown={() => setProfilePressed(true)}
          onMouseUp={() => setProfilePressed(false)}
          onMouseLeave={() => setProfilePressed(false)}
        >
          <div style={styles.profileAvatar}>{growthIcon}</div>
          <div style={styles.profileInfo}>
            <div style={styles.profileName}>{characterName}</div>
            <div style={styles.profileMeta}>
              <span style={styles.growthBadge}>
                {growthIcon} {growthStage}
              </span>
              <span>{affectionLabel}</span>
            </div>
          </div>
        </div>

        <div style={styles.grid}>
          {menuItems.map((item) => (
            <div
              key={item.id}
              style={{
                ...styles.gridItem,
                ...(activeGridItem === item.id ? {
                  transform: "scale(0.95)",
                  background: "var(--bg-card-hover)",
                } : {}),
                ...(!item.available ? { opacity: 0.5 } : {}),
              }}
              onClick={() => handleItemClick(item)}
              onMouseDown={() => setActiveGridItem(item.id)}
              onMouseUp={() => setActiveGridItem(null)}
              onMouseLeave={() => setActiveGridItem(null)}
            >
              <div style={styles.gridItemIcon}>{item.icon}</div>
              <div style={styles.gridItemName}>{item.name}</div>
              {!item.available && (
                <div style={styles.comingSoonTag}>即将开放</div>
              )}
            </div>
          ))}
        </div>
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
    animation: "fadeIn var(--duration-normal) var(--ease-out-expo)",
  },
  navBar: {
    display: "flex",
    alignItems: "center",
    padding: "var(--space-3) var(--space-4)",
    paddingTop: "calc(var(--space-3) + var(--safe-top))",
    background: "rgba(20, 18, 31, 0.85)",
    backdropFilter: "blur(20px)",
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
    color: "var(--text-primary)",
  },
  content: {
    flex: 1,
    overflowY: "auto",
    padding: "var(--space-4)",
  },
  profileCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    padding: "var(--space-5)",
    marginBottom: "var(--space-5)",
    border: "1px solid var(--border-color)",
    display: "flex",
    alignItems: "center",
    boxShadow: "var(--shadow-md)",
    transition: "transform var(--duration-fast) var(--ease-out-quart)",
  },
  profileAvatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: 28,
    flexShrink: 0,
    border: "2px solid var(--accent-primary)",
    boxShadow: "0 0 16px rgba(139, 108, 255, 0.2)",
  },
  profileInfo: {
    marginLeft: 14,
    flex: 1,
  },
  profileName: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    marginBottom: "var(--space-1)",
  },
  profileMeta: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
  },
  growthBadge: {
    display: "inline-flex",
    alignItems: "center",
    gap: "var(--space-1)",
    background: "rgba(139, 108, 255, 0.15)",
    color: "var(--accent-primary)",
    fontSize: "var(--text-sm)",
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-sm)",
  },
  grid: {
    display: "grid",
    gridTemplateColumns: "repeat(3, 1fr)",
    gap: "var(--space-3)",
  },
  gridItem: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    padding: "20px var(--space-2)",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    cursor: "pointer",
    border: "1px solid var(--border-color)",
    minHeight: 100,
    boxShadow: "var(--shadow-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  gridItemIcon: {
    fontSize: 36,
    marginBottom: "var(--space-2)",
  },
  gridItemName: {
    fontSize: "var(--text-sm)",
    color: "var(--text-primary)",
    textAlign: "center",
    fontWeight: "var(--weight-medium)",
  },
  comingSoonTag: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    marginTop: "var(--space-1)",
  },
};
