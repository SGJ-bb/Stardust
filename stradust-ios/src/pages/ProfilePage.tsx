import { useState, useEffect, useRef } from "react";
import {
  loadAffection,
  saveAffection,
  loadPersonas,
  savePersonas,
  loadCheckinRecords,
  saveCheckinRecords,
  loadMilestones,
  loadSettings,
  saveSettings,
  getAffectionLevelLabel,
  getGrowthStage,
  getGrowthStageIcon,
  todayStr,
  generateId,
  DEFAULT_CHARACTER,
  type CharacterCard,
  type AffectionData,
  type CheckInRecord,
  type Milestone,
} from "../utils/api";
import {
  progressFill,
  staggerFadeIn,
  breathe,
  fadeInScale,
  bottomSheetIn,
} from "../utils/animations";

interface ProfilePageProps {
  onBack: () => void;
}

export default function ProfilePage({ onBack }: ProfilePageProps) {
  const [personas, setPersonas] = useState<CharacterCard[]>([DEFAULT_CHARACTER]);
  const [activePersonaId, setActivePersonaId] = useState(DEFAULT_CHARACTER.id);
  const [affection, setAffection] = useState<AffectionData>({
    level: 0,
    total: 0,
    messages_today: 0,
    affection_change_today: 0,
    last_update_date: "",
    personality_evolution_count: 0,
  });
  const [checkinRecords, setCheckinRecords] = useState<CheckInRecord[]>([]);
  const [milestones, setMilestones] = useState<Milestone[]>([]);
  const [showPersonaPicker, setShowPersonaPicker] = useState(false);
  const [editingPersona, setEditingPersona] = useState<CharacterCard | null>(null);
  const [editForm, setEditForm] = useState<Partial<CharacterCard>>({});
  const [checkedInToday, setCheckedInToday] = useState(false);
  const [streak, setStreak] = useState(0);

  // 动画引用
  const progressRef = useRef<HTMLDivElement>(null);
  const milestoneListRef = useRef<HTMLDivElement>(null);
  const checkinBtnRef = useRef<HTMLButtonElement>(null);
  const profileCardRef = useRef<HTMLDivElement>(null);
  const growthBadgeRef = useRef<HTMLDivElement>(null);
  const pickerRef = useRef<HTMLDivElement>(null);
  const editModalRef = useRef<HTMLDivElement>(null);
  const prevAffectionLevel = useRef(0);

  // 获取当前角色
  const activePersona = personas.find((p) => p.id === activePersonaId) || DEFAULT_CHARACTER;

  // 加载数据
  useEffect(() => {
    async function loadData() {
      const [loadedPersonas, loadedAffection, loadedCheckin, loadedMilestones, loadedSettings] =
        await Promise.all([
          loadPersonas(),
          loadAffection(),
          loadCheckinRecords(),
          loadMilestones(),
          loadSettings(),
        ]);

      if (loadedPersonas && loadedPersonas.length > 0) {
        setPersonas(loadedPersonas);
      }
      if (loadedSettings?.active_persona_id) {
        setActivePersonaId(loadedSettings.active_persona_id);
      }
      if (loadedAffection) {
        setAffection(loadedAffection);
        prevAffectionLevel.current = loadedAffection.level;
      }
      if (loadedCheckin) {
        setCheckinRecords(loadedCheckin);
        const today = todayStr();
        const todayRecord = loadedCheckin.find((r) => r.date === today);
        if (todayRecord) {
          setCheckedInToday(true);
          setStreak(todayRecord.streak);
        } else if (loadedCheckin.length > 0) {
          // 检查昨天是否签到来计算当前连续天数
          const yesterday = new Date();
          yesterday.setDate(yesterday.getDate() - 1);
          const yesterdayStr = yesterday.toISOString().split("T")[0];
          const yesterdayRecord = loadedCheckin.find((r) => r.date === yesterdayStr);
          setStreak(yesterdayRecord ? yesterdayRecord.streak : 0);
        }
      }
      if (loadedMilestones) {
        setMilestones(loadedMilestones);
      }
    }
    loadData();
  }, []);

  // 入场动画
  useEffect(() => {
    const anims: ReturnType<typeof breathe>[] = [];
    const timer = setTimeout(() => {
      if (profileCardRef.current) {
        fadeInScale(profileCardRef.current);
      }
      if (progressRef.current) {
        progressFill(progressRef.current, 0, affection.level);
      }
      if (checkinBtnRef.current && !checkedInToday) {
        anims.push(breathe(checkinBtnRef.current));
      }
      if (growthBadgeRef.current) {
        anims.push(breathe(growthBadgeRef.current));
      }
    }, 100);
    return () => {
      clearTimeout(timer);
      anims.forEach((a) => a.revert());
    };
  }, []);

  // 好感度变化动画
  useEffect(() => {
    if (prevAffectionLevel.current !== affection.level && progressRef.current) {
      progressFill(progressRef.current, prevAffectionLevel.current, affection.level);
      prevAffectionLevel.current = affection.level;
    }
  }, [affection.level]);

  // 里程碑入场动画
  useEffect(() => {
    if (milestoneListRef.current && milestones.length > 0) {
      const items = milestoneListRef.current.querySelectorAll("[data-milestone]");
      if (items.length > 0) {
        staggerFadeIn(Array.from(items));
      }
    }
  }, [milestones]);

  // 角色选择弹窗动画
  useEffect(() => {
    if (showPersonaPicker && pickerRef.current) {
      bottomSheetIn(pickerRef.current);
    }
  }, [showPersonaPicker]);

  // 编辑弹窗动画
  useEffect(() => {
    if (editingPersona && editModalRef.current) {
      bottomSheetIn(editModalRef.current);
    }
  }, [editingPersona]);

  // 签到
  const handleCheckin = async () => {
    if (checkedInToday) return;

    const today = todayStr();
    const newStreak = streak + 1;
    const newRecord: CheckInRecord = { date: today, streak: newStreak };

    const newRecords = [...checkinRecords.filter((r) => r.date !== today), newRecord];
    setCheckinRecords(newRecords);
    setCheckedInToday(true);
    setStreak(newStreak);

    // 好感度 +2
    const newAffection = {
      ...affection,
      level: Math.min(100, affection.level + 2),
      total: affection.total + 2,
      affection_change_today: affection.affection_change_today + 2,
      last_update_date: today,
    };
    setAffection(newAffection);

    await Promise.all([
      saveCheckinRecords(newRecords),
      saveAffection(newAffection),
    ]);
  };

  // 切换角色
  const handleSwitchPersona = async (persona: CharacterCard) => {
    setActivePersonaId(persona.id);
    setShowPersonaPicker(false);

    const settings = await loadSettings();
    await saveSettings({ ...settings, active_persona_id: persona.id });

    // 更新 is_active 状态
    const updatedPersonas = personas.map((p) => ({
      ...p,
      is_active: p.id === persona.id,
    }));
    setPersonas(updatedPersonas);
    await savePersonas(updatedPersonas);
  };

  // 编辑角色
  const handleStartEdit = (persona: CharacterCard) => {
    setEditingPersona(persona);
    setEditForm({
      name: persona.name,
      description: persona.description,
      personality: persona.personality,
      speech_style: persona.speech_style,
    });
  };

  const handleSaveEdit = async () => {
    if (!editingPersona) return;

    const updatedPersonas = personas.map((p) =>
      p.id === editingPersona.id
        ? {
            ...p,
            name: editForm.name || p.name,
            description: editForm.description || p.description,
            personality: editForm.personality || p.personality,
            speech_style: editForm.speech_style || p.speech_style,
          }
        : p
    );
    setPersonas(updatedPersonas);
    await savePersonas(updatedPersonas);
    setEditingPersona(null);
    setEditForm({});
  };

  // 创建新角色
  const handleCreatePersona = async () => {
    const newPersona: CharacterCard = {
      ...DEFAULT_CHARACTER,
      id: generateId(),
      name: "新角色",
      description: "一个全新的AI伙伴",
      personality: "友善、温柔",
      speech_style: "自然亲切",
      first_mes: "你好呀！很高兴认识你~",
      is_active: false,
      created_at: Date.now(),
    };
    const updatedPersonas = [...personas, newPersona];
    setPersonas(updatedPersonas);
    await savePersonas(updatedPersonas);
    handleStartEdit(newPersona);
  };

  // 删除角色
  const handleDeletePersona = async (personaId: string) => {
    if (personas.length <= 1) return;
    if (personaId === DEFAULT_CHARACTER.id) return;

    const updatedPersonas = personas.filter((p) => p.id !== personaId);
    setPersonas(updatedPersonas);
    await savePersonas(updatedPersonas);

    if (activePersonaId === personaId) {
      const firstRemaining = updatedPersonas[0];
      await handleSwitchPersona(firstRemaining);
    }
  };

  // 成长阶段
  const growthStage = getGrowthStage(affection.level, streak);
  const growthIcon = getGrowthStageIcon(growthStage);
  const affectionLabel = getAffectionLevelLabel(affection.level);

  // 好感度进度条颜色 - 使用设计系统渐变
  const getAffectionBarGradient = (level: number): string => {
    if (level >= 90) return "var(--gradient-warm)";
    if (level >= 70) return "var(--gradient-primary)";
    if (level >= 50) return "var(--gradient-cool)";
    if (level >= 30) return "linear-gradient(90deg, var(--accent-green), var(--accent-yellow))";
    if (level >= 10) return "linear-gradient(90deg, var(--accent-orange), var(--accent-yellow))";
    return "linear-gradient(90deg, var(--text-muted), var(--text-secondary))";
  };

  return (
    <div style={styles.container}>
      {/* 顶部导航栏 */}
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={onBack}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>角色档案</span>
        <button style={styles.switchBtn} onClick={() => setShowPersonaPicker(true)}>
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M17 1l4 4-4 4" />
            <path d="M3 11V9a4 4 0 014-4h14" />
            <path d="M7 23l-4-4 4-4" />
            <path d="M21 13v2a4 4 0 01-4 4H3" />
          </svg>
        </button>
      </div>

      {/* 主内容 */}
      <div style={styles.content}>
        {/* 角色头像卡片 */}
        <div ref={profileCardRef} style={styles.profileCard}>
          <div style={styles.avatarWrapper}>
            <div style={styles.avatarCircle}>
              <span style={{ fontSize: 48 }}>{activePersona.avatar_path ? "🖼️" : "🐱"}</span>
            </div>
            <div ref={growthBadgeRef} style={styles.growthBadge}>
              <span style={{ fontSize: 14 }}>{growthIcon}</span>
            </div>
          </div>
          <span style={styles.characterName}>{activePersona.name}🐱</span>
          <span style={styles.growthStageText}>
            {growthIcon} {growthStage}
          </span>
        </div>

        {/* 好感度区域 */}
        <div style={styles.section}>
          <div style={styles.sectionHeader}>
            <span style={styles.sectionTitle}>好感度</span>
            <span style={styles.affectionLevelTag}>{affectionLabel}</span>
          </div>
          <div style={styles.progressBarBg}>
            <div
              ref={progressRef}
              style={{
                ...styles.progressBarFill,
                width: `${affection.level}%`,
                background: getAffectionBarGradient(affection.level),
                boxShadow: "var(--shadow-glow)",
              }}
            />
          </div>
          <div style={styles.progressInfo}>
            <span style={styles.progressLabel}>Lv.{affection.level}</span>
            <span style={styles.progressSubLabel}>{affection.level}/100</span>
          </div>
        </div>

        {/* 签到区域 */}
        <div style={styles.section}>
          <div style={styles.sectionHeader}>
            <span style={styles.sectionTitle}>每日签到</span>
            <span style={styles.streakBadge}>🔥 连续 {streak} 天</span>
          </div>
          <button
            ref={checkinBtnRef}
            style={{
              ...styles.checkinBtn,
              ...(checkedInToday ? styles.checkinBtnDisabled : styles.checkinBtnActive),
            }}
            onClick={handleCheckin}
            disabled={checkedInToday}
          >
            {checkedInToday ? "✅ 今日已签到" : "📝 签到"}
          </button>
        </div>

        {/* 角色信息 */}
        <div style={styles.section}>
          <div style={styles.sectionHeader}>
            <span style={styles.sectionTitle}>角色信息</span>
            <button style={styles.editBtn} onClick={() => handleStartEdit(activePersona)}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
              <span>编辑</span>
            </button>
          </div>
          <div style={styles.infoCard}>
            <div style={styles.infoRow}>
              <span style={styles.infoLabel}>描述</span>
              <span style={styles.infoValue}>{activePersona.description}</span>
            </div>
            <div style={styles.infoDivider} />
            <div style={styles.infoRow}>
              <span style={styles.infoLabel}>性格</span>
              <span style={styles.infoValue}>{activePersona.personality}</span>
            </div>
            <div style={styles.infoDivider} />
            <div style={styles.infoRow}>
              <span style={styles.infoLabel}>说话风格</span>
              <span style={styles.infoValue}>{activePersona.speech_style}</span>
            </div>
          </div>
        </div>

        {/* 里程碑 */}
        <div style={styles.section}>
          <span style={styles.sectionTitle}>里程碑</span>
          {milestones.length === 0 ? (
            <div style={styles.emptyState}>
              <span style={{ fontSize: 32 }}>🏔️</span>
              <span style={styles.emptyText}>还没有里程碑，继续和角色互动吧~</span>
            </div>
          ) : (
            <div ref={milestoneListRef} style={styles.milestoneList}>
              {milestones.map((m) => (
                <div key={m.id} data-milestone style={{
                  ...styles.milestoneItem,
                  borderLeft: (m as any).unlocked
                    ? "3px solid var(--accent-green)"
                    : "3px solid var(--accent-primary)",
                }}>
                  <span style={{ fontSize: 20 }}>{m.icon || "⭐"}</span>
                  <div style={styles.milestoneInfo}>
                    <span style={styles.milestoneTitle}>{m.title}</span>
                    <span style={styles.milestoneDesc}>{m.description}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 角色选择弹窗 */}
      {showPersonaPicker && (
        <div style={styles.overlay} onClick={() => setShowPersonaPicker(false)}>
          <div ref={pickerRef} style={styles.pickerModal} onClick={(e) => e.stopPropagation()}>
            <div style={styles.pickerHeader}>
              <span style={styles.pickerTitle}>选择角色</span>
              <button style={styles.pickerClose} onClick={() => setShowPersonaPicker(false)}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div style={styles.pickerList}>
              {personas.map((p) => (
                <div
                  key={p.id}
                  style={{
                    ...styles.pickerItem,
                    ...(p.id === activePersonaId ? styles.pickerItemActive : {}),
                  }}
                  onClick={() => handleSwitchPersona(p)}
                >
                  <div style={styles.pickerAvatar}>
                    <span style={{ fontSize: 24 }}>{p.avatar_path ? "🖼️" : "🐱"}</span>
                  </div>
                  <div style={styles.pickerInfo}>
                    <span style={styles.pickerName}>{p.name}</span>
                    <span style={styles.pickerDesc}>{p.description}</span>
                  </div>
                  {p.id === activePersonaId && (
                    <span style={styles.pickerCheck}>✓</span>
                  )}
                </div>
              ))}
            </div>
            <button style={styles.createPersonaBtn} onClick={handleCreatePersona}>
              ➕ 创建新角色
            </button>
          </div>
        </div>
      )}

      {/* 编辑角色弹窗 */}
      {editingPersona && (
        <div style={styles.overlay} onClick={() => setEditingPersona(null)}>
          <div ref={editModalRef} style={styles.editModal} onClick={(e) => e.stopPropagation()}>
            <div style={styles.pickerHeader}>
              <span style={styles.pickerTitle}>编辑角色</span>
              <button style={styles.pickerClose} onClick={() => setEditingPersona(null)}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div style={styles.editForm}>
              <div style={styles.editField}>
                <label style={styles.editLabel}>名字</label>
                <input
                  style={styles.editInput}
                  value={editForm.name || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
                  placeholder="角色名字"
                />
              </div>
              <div style={styles.editField}>
                <label style={styles.editLabel}>描述</label>
                <textarea
                  style={{ ...styles.editInput, minHeight: 60, resize: "vertical" }}
                  value={editForm.description || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, description: e.target.value }))}
                  placeholder="角色描述"
                  rows={3}
                />
              </div>
              <div style={styles.editField}>
                <label style={styles.editLabel}>性格</label>
                <textarea
                  style={{ ...styles.editInput, minHeight: 60, resize: "vertical" }}
                  value={editForm.personality || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, personality: e.target.value }))}
                  placeholder="角色性格"
                  rows={3}
                />
              </div>
              <div style={styles.editField}>
                <label style={styles.editLabel}>说话风格</label>
                <input
                  style={styles.editInput}
                  value={editForm.speech_style || ""}
                  onChange={(e) => setEditForm((prev) => ({ ...prev, speech_style: e.target.value }))}
                  placeholder="说话风格"
                />
              </div>
              <div style={styles.editActions}>
                {editingPersona.id !== DEFAULT_CHARACTER.id && personas.length > 1 && (
                  <button
                    style={styles.deleteBtn}
                    onClick={() => {
                      handleDeletePersona(editingPersona.id);
                      setEditingPersona(null);
                    }}
                  >
                    删除角色
                  </button>
                )}
                <div style={{ flex: 1 }} />
                <button style={styles.cancelBtn} onClick={() => setEditingPersona(null)}>
                  取消
                </button>
                <button style={styles.saveEditBtn} onClick={handleSaveEdit}>
                  保存
                </button>
              </div>
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
  },
  header: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-3) var(--space-4)",
    paddingTop: "calc(var(--space-3) + var(--safe-top))",
    background: "var(--bg-secondary)",
    borderBottom: "1px solid var(--border-color)",
  },
  backBtn: {
    background: "transparent",
    color: "var(--text-primary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-md)",
  },
  headerTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  switchBtn: {
    background: "transparent",
    color: "var(--text-secondary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-md)",
  },
  content: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-4)",
    paddingBottom: "calc(var(--space-4) + var(--safe-bottom))",
  },
  // 角色头像卡片
  profileCard: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    padding: "var(--space-6) var(--space-4)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-xl)",
    border: "1px solid var(--border-color)",
    marginBottom: "var(--space-4)",
    boxShadow: "var(--shadow-md)",
  },
  avatarWrapper: {
    position: "relative",
    marginBottom: "var(--space-3)",
  },
  avatarCircle: {
    width: 96,
    height: 96,
    borderRadius: 48,
    background: "var(--gradient-primary)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    boxShadow: "var(--shadow-glow)",
  },
  growthBadge: {
    position: "absolute",
    bottom: -2,
    right: -2,
    width: 32,
    height: 32,
    borderRadius: 16,
    background: "var(--bg-card)",
    border: "2px solid var(--accent-green)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  characterName: {
    fontSize: "var(--text-xl)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-primary)",
    marginBottom: "var(--space-1)",
  },
  growthStageText: {
    fontSize: "var(--text-sm)",
    color: "var(--accent-green)",
    fontWeight: "var(--weight-medium)",
  },
  // 区块
  section: {
    marginBottom: "var(--space-5)",
  },
  sectionHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: "var(--space-2)",
  },
  sectionTitle: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-secondary)",
    textTransform: "uppercase",
    letterSpacing: "var(--tracking-wide)",
  },
  // 好感度
  affectionLevelTag: {
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--accent-primary)",
    background: "rgba(139, 108, 255, 0.15)",
    padding: "3px 10px",
    borderRadius: "var(--radius-full)",
  },
  progressBarBg: {
    width: "100%",
    height: 12,
    borderRadius: 6,
    background: "var(--bg-input)",
    overflow: "hidden",
    boxShadow: "inset 0 2px 4px rgba(0, 0, 0, 0.2)",
  },
  progressBarFill: {
    height: "100%",
    borderRadius: 6,
    transition: "width 0.5s var(--ease-out-expo), background 0.5s var(--ease-out-expo)",
  },
  progressInfo: {
    display: "flex",
    justifyContent: "space-between",
    marginTop: "var(--space-1)",
  },
  progressLabel: {
    fontSize: "var(--text-2xl)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-primary)",
  },
  progressSubLabel: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
  },
  // 签到
  streakBadge: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--accent-orange)",
  },
  checkinBtn: {
    width: "100%",
    padding: "var(--space-3)",
    borderRadius: "var(--radius-lg)",
    color: "white",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
  },
  checkinBtnActive: {
    background: "var(--gradient-warm)",
    boxShadow: "var(--shadow-glow)",
  },
  checkinBtnDisabled: {
    background: "var(--bg-input)",
    color: "var(--text-muted)",
    boxShadow: "none",
    opacity: 0.6,
    pointerEvents: "none" as const,
  },
  // 角色信息
  editBtn: {
    display: "flex",
    alignItems: "center",
    gap: 4,
    background: "transparent",
    color: "var(--accent-primary)",
    fontSize: "var(--text-sm)",
    padding: "var(--space-1) var(--space-2)",
    borderRadius: "var(--radius-sm)",
  },
  infoCard: {
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
    padding: "var(--space-3) var(--space-4)",
    boxShadow: "var(--shadow-sm)",
  },
  infoRow: {
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-1)",
    padding: "var(--space-1) 0",
  },
  infoLabel: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
    fontWeight: "var(--weight-medium)",
  },
  infoValue: {
    fontSize: "var(--text-sm)",
    color: "var(--text-primary)",
    lineHeight: "var(--leading-normal)",
  },
  infoDivider: {
    height: 1,
    background: "var(--border-color)",
    margin: "var(--space-1) 0",
  },
  // 里程碑
  emptyState: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: "var(--space-2)",
    padding: "var(--space-6) var(--space-4)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
  },
  emptyText: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    textAlign: "center",
  },
  milestoneList: {
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-2)",
  },
  milestoneItem: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-3) var(--space-3)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    border: "1px solid var(--border-color)",
    boxShadow: "var(--shadow-sm)",
    transition: "background var(--duration-fast) var(--ease-out-quart)",
  },
  milestoneInfo: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    gap: 2,
  },
  milestoneTitle: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  milestoneDesc: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
  },
  // 弹窗遮罩
  overlay: {
    position: "fixed",
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
    zIndex: 1000,
  },
  // 角色选择弹窗
  pickerModal: {
    width: "100%",
    maxHeight: "70vh",
    background: "var(--bg-secondary)",
    borderRadius: "var(--radius-2xl) var(--radius-2xl) 0 0",
    display: "flex",
    flexDirection: "column",
    paddingBottom: "env(safe-area-inset-bottom, 0px)",
  },
  pickerHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-4) var(--space-5)",
    borderBottom: "1px solid var(--border-color)",
  },
  pickerTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  pickerClose: {
    background: "transparent",
    color: "var(--text-secondary)",
    padding: "var(--space-1)",
    borderRadius: "var(--radius-sm)",
  },
  pickerList: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-2) var(--space-4)",
  },
  pickerItem: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-3) var(--space-3)",
    borderRadius: "var(--radius-lg)",
    marginBottom: "var(--space-1)",
    border: "1px solid var(--border-color)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  pickerItemActive: {
    background: "rgba(139, 108, 255, 0.1)",
    border: "2px solid var(--accent-primary)",
    boxShadow: "0 0 16px rgba(139, 108, 255, 0.15)",
  },
  pickerAvatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    background: "var(--bg-card)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  pickerInfo: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    gap: 2,
    overflow: "hidden",
  },
  pickerName: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  pickerDesc: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
  },
  pickerCheck: {
    fontSize: 18,
    fontWeight: "var(--weight-bold)",
    color: "var(--accent-primary)",
  },
  createPersonaBtn: {
    margin: "var(--space-2) var(--space-4) var(--space-4)",
    padding: "var(--space-3)",
    borderRadius: "var(--radius-lg)",
    background: "var(--bg-card)",
    color: "var(--accent-primary)",
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-medium)",
    border: "1px dashed var(--accent-primary)",
  },
  // 编辑弹窗
  editModal: {
    width: "100%",
    maxHeight: "85vh",
    background: "var(--bg-secondary)",
    borderRadius: "var(--radius-2xl) var(--radius-2xl) 0 0",
    display: "flex",
    flexDirection: "column",
    paddingBottom: "env(safe-area-inset-bottom, 0px)",
  },
  editForm: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-4) var(--space-5)",
  },
  editField: {
    marginBottom: "var(--space-3)",
  },
  editLabel: {
    display: "block",
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    marginBottom: "var(--space-1)",
    fontWeight: "var(--weight-medium)",
  },
  editInput: {
    width: "100%",
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-md)",
    color: "var(--text-primary)",
    padding: "var(--space-2) var(--space-3)",
    fontSize: "var(--text-md)",
    outline: "none",
    lineHeight: "var(--leading-normal)",
  },
  editActions: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    marginTop: "var(--space-2)",
    marginBottom: "var(--space-4)",
  },
  deleteBtn: {
    padding: "var(--space-2) var(--space-4)",
    borderRadius: "var(--radius-md)",
    background: "rgba(255, 92, 124, 0.15)",
    color: "var(--accent-red)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
  },
  cancelBtn: {
    padding: "var(--space-2) var(--space-5)",
    borderRadius: "var(--radius-md)",
    background: "var(--bg-card)",
    color: "var(--text-secondary)",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
    border: "1px solid var(--border-color)",
  },
  saveEditBtn: {
    padding: "var(--space-2) var(--space-6)",
    borderRadius: "var(--radius-md)",
    background: "var(--gradient-primary)",
    color: "white",
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    boxShadow: "var(--shadow-sm)",
  },
};
