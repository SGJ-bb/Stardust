import { useState, useEffect, useRef, useCallback } from "react";
import {
  GroupChat,
  GroupMessage,
  CharacterCard,
  ApiConfig,
  loadGroupChats,
  saveGroupChats,
  loadGroupMessages,
  saveGroupMessages,
  loadPersonas,
  loadSettings,
  sendChat,
  generateId,
  formatTime,
  getEmotionEmoji,
  getEmotionColor,
} from "../utils/api";
import {
  listStaggerIn,
  messageBubbleIn,
  bottomSheetIn,
} from "../utils/animations";

// 成员头像颜色映射
const AVATAR_COLORS = [
  "var(--accent-primary)",
  "var(--accent-secondary)",
  "var(--accent-pink)",
  "var(--accent-orange)",
  "var(--accent-green)",
  "var(--accent-yellow)",
];

function getAvatarColor(index: number): string {
  return AVATAR_COLORS[index % AVATAR_COLORS.length];
}

export default function GroupChatPage({ onBack }: { onBack: () => void }) {
  // ===== 群聊列表视图状态 =====
  const [groupChats, setGroupChats] = useState<GroupChat[]>([]);
  const [personas, setPersonas] = useState<CharacterCard[]>([]);
  const [apiConfig, setApiConfig] = useState<ApiConfig | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  // ===== 创建群聊表单状态 =====
  const [newGroupName, setNewGroupName] = useState("");
  const [newGroupMembers, setNewGroupMembers] = useState<string[]>([]);
  const [newGroupSpeakMode, setNewGroupSpeakMode] = useState("free");
  const [newGroupRelationship, setNewGroupRelationship] = useState("friends");

  // ===== 群聊对话视图状态 =====
  const [activeGroup, setActiveGroup] = useState<GroupChat | null>(null);
  const [groupMessages, setGroupMessages] = useState<GroupMessage[]>([]);
  const [inputText, setInputText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [typingPersonas, setTypingPersonas] = useState<string[]>([]);
  const [pressedGroupId, setPressedGroupId] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const listAreaRef = useRef<HTMLDivElement>(null);
  const modalRef = useRef<HTMLDivElement>(null);
  const prevMsgCountRef = useRef(0);

  // ===== 初始化加载 =====
  useEffect(() => {
    async function init() {
      const [groups, personaList, settings] = await Promise.all([
        loadGroupChats(),
        loadPersonas(),
        loadSettings(),
      ]);
      setGroupChats(groups || []);
      setPersonas(personaList || []);
      if (settings?.api_config) {
        setApiConfig(settings.api_config);
      }
    }
    init();
  }, []);

  // ===== 群聊列表入场动画 =====
  useEffect(() => {
    if (groupChats.length > 0 && listAreaRef.current) {
      const cards = listAreaRef.current.querySelectorAll(".group-card");
      if (cards.length > 0) {
        listStaggerIn(cards as unknown as Element[]);
      }
    }
  }, [groupChats.length]);

  // ===== 创建弹窗动画 =====
  useEffect(() => {
    if (showCreateModal && modalRef.current) {
      bottomSheetIn(modalRef.current);
    }
  }, [showCreateModal]);

  // ===== 消息气泡入场动画 =====
  useEffect(() => {
    if (groupMessages.length > prevMsgCountRef.current && groupMessages.length > 0) {
      const lastMsg = groupMessages[groupMessages.length - 1];
      requestAnimationFrame(() => {
        const el = document.querySelector(`[data-msg-id="${lastMsg.id}"]`);
        if (el) {
          messageBubbleIn(el, lastMsg.sender_persona_id === "user");
        }
      });
    }
    prevMsgCountRef.current = groupMessages.length;
  }, [groupMessages]);

  // ===== 自动滚动到底部 =====
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [groupMessages]);

  // ===== 加载群聊消息 =====
  const loadMessages = useCallback(async (groupId: string) => {
    const msgs = await loadGroupMessages(groupId);
    setGroupMessages(msgs || []);
    prevMsgCountRef.current = (msgs || []).length;
  }, []);

  // ===== 保存群聊消息 =====
  const saveMessages = useCallback(
    async (groupId: string, msgs: GroupMessage[]) => {
      await saveGroupMessages(groupId, msgs);
    },
    []
  );

  // ===== 获取角色信息 =====
  const getPersonaById = useCallback(
    (id: string): CharacterCard | undefined => {
      return personas.find((p) => p.id === id);
    },
    [personas]
  );

  // ===== 创建群聊 =====
  const handleCreateGroup = async () => {
    if (!newGroupName.trim()) return;
    if (newGroupMembers.length === 0) return;

    const newGroup: GroupChat = {
      id: generateId(),
      name: newGroupName.trim(),
      member_persona_ids: newGroupMembers,
      speak_mode: newGroupSpeakMode,
      relationship_setting: newGroupRelationship,
    };

    const updated = [...groupChats, newGroup];
    setGroupChats(updated);
    await saveGroupChats(updated);

    // 重置表单
    setNewGroupName("");
    setNewGroupMembers([]);
    setNewGroupSpeakMode("free");
    setNewGroupRelationship("friends");
    setShowCreateModal(false);
  };

  // ===== 删除群聊 =====
  const handleDeleteGroup = async (groupId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = groupChats.filter((g) => g.id !== groupId);
    setGroupChats(updated);
    await saveGroupChats(updated);
    // 清除该群聊的消息
    await saveGroupMessages(groupId, []);
  };

  // ===== 进入群聊对话 =====
  const handleOpenGroup = async (group: GroupChat) => {
    setActiveGroup(group);
    await loadMessages(group.id);
  };

  // ===== 返回群聊列表 =====
  const handleBackToList = () => {
    setActiveGroup(null);
    setGroupMessages([]);
    setInputText("");
    setIsLoading(false);
    setTypingPersonas([]);
  };

  // ===== 切换成员选择 =====
  const toggleMember = (personaId: string) => {
    setNewGroupMembers((prev) =>
      prev.includes(personaId)
        ? prev.filter((id) => id !== personaId)
        : [...prev, personaId]
    );
  };

  // ===== 发送消息并让AI角色回复 =====
  const handleSend = async () => {
    const text = inputText.trim();
    if (!text || isLoading || !activeGroup) return;

    if (!apiConfig?.chat_api_url) {
      const errorMsg: GroupMessage = {
        id: generateId(),
        sender_persona_id: "system",
        sender_name: "系统",
        text: "请先在设置中配置 API 地址哦~",
        time: formatTime(new Date()),
        emotion: "Neutral",
      };
      setGroupMessages((prev) => [...prev, errorMsg]);
      return;
    }

    // 添加用户消息
    const userMsg: GroupMessage = {
      id: generateId(),
      sender_persona_id: "user",
      sender_name: "我",
      text,
      time: formatTime(new Date()),
      emotion: "",
    };

    const currentMessages = [...groupMessages, userMsg];
    setGroupMessages(currentMessages);
    setInputText("");
    setIsLoading(true);

    // 保存用户消息
    await saveMessages(activeGroup.id, currentMessages);

    // 构建对话历史文本（用于上下文）
    const contextMessages = currentMessages.slice(-20).map((msg) => ({
      role: msg.sender_persona_id === "user" ? "user" as const : "assistant" as const,
      content: msg.sender_persona_id === "user"
        ? msg.text
        : `${msg.sender_name}: ${msg.text}`,
    }));

    // 遍历每个群成员，分别调用 sendChat 生成回复
    const memberIds = activeGroup.member_persona_ids;
    const speakOrder =
      activeGroup.speak_mode === "sequential" ? memberIds : [...memberIds].sort(() => Math.random() - 0.5);

    for (const personaId of speakOrder) {
      const persona = getPersonaById(personaId);
      if (!persona) continue;

      setTypingPersonas((prev) => [...prev, persona.name]);

      try {
        // 构建该角色的消息上下文
        const systemPrompt = `${persona.system_prompt}\n\n你正在一个名为「${activeGroup.name}」的群聊中。群聊关系设定：${activeGroup.relationship_setting}。群聊中的其他成员：${memberIds
          .filter((id) => id !== personaId)
          .map((id) => getPersonaById(id)?.name || id)
          .join("、")}。请以${persona.name}的身份回复，保持角色一致性。在回复末尾 [[emotion:xxx]] 处标注你的当前情绪（从 happy/sad/angry/surprised/tsundere/neutral 中选一个）。`;

        const messagesForApi = [
          { role: "system" as const, content: systemPrompt },
          ...contextMessages,
        ];

        const response = await sendChat(
          apiConfig.chat_api_url,
          apiConfig.api_key,
          apiConfig.model_name,
          messagesForApi,
          apiConfig.temperature,
          apiConfig.max_tokens,
          apiConfig.top_p,
          apiConfig.frequency_penalty,
          apiConfig.presence_penalty
        );

        setTypingPersonas((prev) => prev.filter((n) => n !== persona.name));

        if (response.error_message) {
          const errorMsg: GroupMessage = {
            id: generateId(),
            sender_persona_id: personaId,
            sender_name: persona.name,
            text: `（出错了：${response.error_message}）`,
            time: formatTime(new Date()),
            emotion: "Sad",
          };
          const updated = [...currentMessages, errorMsg];
          // 更新 contextMessages 以便下一个角色能看到之前的回复
          currentMessages.push(errorMsg);
          contextMessages.push({
            role: "assistant",
            content: `${persona.name}: ${errorMsg.text}`,
          });
          setGroupMessages(updated);
          await saveMessages(activeGroup.id, updated);
        } else {
          // 解析情绪标签
          let replyText = response.text;
          let emotion = response.emotion || "Neutral";
          const emotionMatch = replyText.match(/\[\[emotion:(\w+)\]\]/);
          if (emotionMatch) {
            emotion = emotionMatch[1];
            replyText = replyText.replace(/\[\[emotion:\w+\]\]/, "").trim();
          }

          const aiMsg: GroupMessage = {
            id: generateId(),
            sender_persona_id: personaId,
            sender_name: persona.name,
            text: replyText,
            time: formatTime(new Date()),
            emotion,
          };
          const updated = [...currentMessages, aiMsg];
          currentMessages.push(aiMsg);
          contextMessages.push({
            role: "assistant",
            content: `${persona.name}: ${replyText}`,
          });
          setGroupMessages(updated);
          await saveMessages(activeGroup.id, updated);
        }
      } catch (error) {
        setTypingPersonas((prev) => prev.filter((n) => n !== persona.name));
        const errorMsg: GroupMessage = {
          id: generateId(),
          sender_persona_id: personaId,
          sender_name: persona.name,
          text: `（出错了：${error}）`,
          time: formatTime(new Date()),
          emotion: "Sad",
        };
        const updated = [...currentMessages, errorMsg];
        currentMessages.push(errorMsg);
        contextMessages.push({
          role: "assistant",
          content: `${persona.name}: ${errorMsg.text}`,
        });
        setGroupMessages(updated);
        await saveMessages(activeGroup.id, updated);
      }
    }

    setIsLoading(false);
  };

  // ===== 键盘事件 =====
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // ===== 输入框自适应高度 =====
  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputText(e.target.value);
    const textarea = e.target;
    textarea.style.height = "auto";
    textarea.style.height = Math.min(textarea.scrollHeight, 120) + "px";
  };

  // ===== 发言模式选项 =====
  const speakModeOptions = [
    { value: "free", label: "自由发言", desc: "角色随机顺序回复" },
    { value: "sequential", label: "顺序发言", desc: "角色按加入顺序回复" },
  ];

  // ===== 关系设定选项 =====
  const relationshipOptions = [
    { value: "friends", label: "朋友" },
    { value: "family", label: "家人" },
    { value: "colleagues", label: "同事" },
    { value: "classmates", label: "同学" },
    { value: "rivals", label: "竞争对手" },
    { value: "lovers", label: "恋人" },
  ];

  // ============================================================
  // 群聊对话视图
  // ============================================================
  if (activeGroup) {
    return (
      <div style={styles.container}>
        {/* 对话顶部导航 */}
        <div style={styles.header}>
          <button style={styles.backBtn} onClick={handleBackToList}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
          <div style={styles.headerInfo}>
            <span style={styles.headerName}>{activeGroup.name}</span>
            <span style={styles.headerStatus}>
              {activeGroup.member_persona_ids
                .map((id) => getPersonaById(id)?.name || id)
                .join("、")}
            </span>
          </div>
          <div style={styles.memberAvatars}>
            {activeGroup.member_persona_ids.slice(0, 3).map((id, idx) => {
              const p = getPersonaById(id);
              return (
                <div key={id} style={{
                  ...styles.memberAvatarSmall,
                  borderColor: getAvatarColor(idx),
                }}>
                  <span style={{ fontSize: 14 }}>
                    {p ? getEmotionEmoji("Neutral") : "?"}
                  </span>
                </div>
              );
            })}
            {activeGroup.member_persona_ids.length > 3 && (
              <span style={styles.moreCount}>
                +{activeGroup.member_persona_ids.length - 3}
              </span>
            )}
          </div>
        </div>

        {/* 消息区域 */}
        <div style={styles.messagesArea}>
          {groupMessages.map((msg) => {
            const isUser = msg.sender_persona_id === "user";
            const isSystem = msg.sender_persona_id === "system";
            const persona = !isUser && !isSystem ? getPersonaById(msg.sender_persona_id) : null;
            const memberIdx = activeGroup.member_persona_ids.indexOf(msg.sender_persona_id);

            return (
              <div
                key={msg.id}
                data-msg-id={msg.id}
                style={{
                  ...styles.messageRow,
                  justifyContent: isUser ? "flex-end" : "flex-start",
                }}
              >
                {!isUser && (
                  <div
                    style={{
                      ...styles.petAvatar,
                      borderColor: isSystem
                        ? "var(--text-muted)"
                        : getAvatarColor(memberIdx >= 0 ? memberIdx : 0),
                    }}
                  >
                    <span style={{ fontSize: 16 }}>
                      {isSystem
                        ? "⚙️"
                        : persona
                        ? getEmotionEmoji(msg.emotion || "Neutral")
                        : "❓"}
                    </span>
                  </div>
                )}
                <div
                  style={{
                    ...styles.messageBubble,
                    ...(isUser ? styles.userBubble : styles.petBubble),
                    borderLeft: isUser
                      ? "none"
                      : `3px solid ${isSystem ? "var(--text-muted)" : getAvatarColor(memberIdx >= 0 ? memberIdx : 0)}`,
                  }}
                >
                  {!isUser && (
                    <span
                      style={{
                        ...styles.senderName,
                        color: isSystem
                          ? "var(--text-muted)"
                          : getAvatarColor(memberIdx >= 0 ? memberIdx : 0),
                      }}
                    >
                      {msg.sender_name}
                    </span>
                  )}
                  <p style={styles.messageText}>{msg.text}</p>
                  <div style={styles.messageMeta}>
                    <span style={styles.messageTime}>{msg.time}</span>
                    {!isUser && !isSystem && msg.emotion && (
                      <span
                        style={{
                          ...styles.emotionTag,
                          color: getEmotionColor(msg.emotion),
                        }}
                      >
                        {msg.emotion}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}

          {/* 打字指示器 */}
          {typingPersonas.length > 0 && (
            <div style={{ ...styles.messageRow, justifyContent: "flex-start" }}>
              <div style={styles.petAvatar}>
                <span style={{ fontSize: 16 }}>💬</span>
              </div>
              <div style={{ ...styles.messageBubble, ...styles.petBubble }}>
                <span style={styles.senderName} />
                <div style={styles.typingRow}>
                  <span style={styles.typingName}>
                    {typingPersonas.join("、")}
                  </span>
                  <span style={styles.typingText}>正在输入</span>
                  <div style={styles.typingIndicator}>
                    <span style={{ ...styles.typingDot, animationDelay: "0s" }} />
                    <span style={{ ...styles.typingDot, animationDelay: "0.2s" }} />
                    <span style={{ ...styles.typingDot, animationDelay: "0.4s" }} />
                  </div>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* 输入区域 */}
        <div style={styles.inputArea}>
          <div style={styles.inputContainer}>
            <textarea
              ref={inputRef}
              style={styles.textInput}
              value={inputText}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              placeholder="发送群消息..."
              rows={1}
            />
            <button
              style={{
                ...styles.sendBtn,
                opacity: inputText.trim() && !isLoading ? 1 : 0.4,
              }}
              onClick={handleSend}
              disabled={isLoading || !inputText.trim()}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ============================================================
  // 群聊列表视图
  // ============================================================
  return (
    <div style={styles.container}>
      {/* 列表顶部导航 */}
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={onBack}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>群聊</span>
        <button style={styles.addBtn} onClick={() => setShowCreateModal(true)}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>

      {/* 群聊列表 */}
      <div style={styles.listArea} ref={listAreaRef}>
        {groupChats.length === 0 ? (
          <div style={styles.emptyState}>
            <span style={{ fontSize: 48 }}>👥</span>
            <p style={styles.emptyTitle}>还没有群聊</p>
            <p style={styles.emptyDesc}>点击右上角 + 创建一个群聊吧</p>
          </div>
        ) : (
          groupChats.map((group) => {
            const memberNames = group.member_persona_ids
              .map((id) => getPersonaById(id)?.name || id)
              .join("、");
            return (
              <div
                key={group.id}
                className="group-card"
                style={{
                  ...styles.groupCard,
                  transform: pressedGroupId === group.id ? "scale(0.97)" : "scale(1)",
                }}
                onClick={() => handleOpenGroup(group)}
                onMouseDown={() => setPressedGroupId(group.id)}
                onMouseUp={() => setPressedGroupId(null)}
                onMouseLeave={() => setPressedGroupId(null)}
              >
                <div style={styles.groupAvatar}>
                  <span style={{ fontSize: 24 }}>👥</span>
                </div>
                <div style={styles.groupInfo}>
                  <span style={styles.groupName}>{group.name}</span>
                  <span style={styles.groupMembers}>{memberNames}</span>
                  <div style={styles.groupTags}>
                    <span style={styles.groupTag}>
                      {speakModeOptions.find((o) => o.value === group.speak_mode)?.label || group.speak_mode}
                    </span>
                    <span style={styles.groupTag}>
                      {relationshipOptions.find((o) => o.value === group.relationship_setting)?.label || group.relationship_setting}
                    </span>
                    <span style={styles.groupTag}>
                      {group.member_persona_ids.length}人
                    </span>
                  </div>
                </div>
                <button
                  style={styles.deleteBtn}
                  onClick={(e) => handleDeleteGroup(group.id, e)}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
                  </svg>
                </button>
              </div>
            );
          })
        )}
      </div>

      {/* 创建群聊弹窗 */}
      {showCreateModal && (
        <div style={styles.modalOverlay} onClick={() => setShowCreateModal(false)}>
          <div
            ref={modalRef}
            style={styles.modalContent}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={styles.modalHeader}>
              <span style={styles.modalTitle}>创建群聊</span>
              <button style={styles.modalCloseBtn} onClick={() => setShowCreateModal(false)}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* 群聊名称 */}
            <div style={styles.formGroup}>
              <label style={styles.formLabel}>群聊名称</label>
              <input
                style={styles.formInput}
                type="text"
                value={newGroupName}
                onChange={(e) => setNewGroupName(e.target.value)}
                placeholder="输入群聊名称"
                maxLength={20}
              />
            </div>

            {/* 选择成员 */}
            <div style={styles.formGroup}>
              <label style={styles.formLabel}>
                选择成员
                {newGroupMembers.length > 0 && (
                  <span style={styles.selectedCount}> 已选 {newGroupMembers.length} 人</span>
                )}
              </label>
              <div style={styles.memberList}>
                {personas.length === 0 ? (
                  <p style={styles.noPersonas}>暂无可用角色，请先创建角色</p>
                ) : (
                  personas.map((persona, idx) => {
                    const isSelected = newGroupMembers.includes(persona.id);
                    return (
                      <div
                        key={persona.id}
                        style={{
                          ...styles.memberItem,
                          ...(isSelected ? styles.memberItemSelected : {}),
                        }}
                        onClick={() => toggleMember(persona.id)}
                      >
                        <div style={{
                          ...styles.memberItemAvatar,
                          borderColor: getAvatarColor(idx),
                        }}>
                          <span style={{ fontSize: 18 }}>
                            {getEmotionEmoji("Neutral")}
                          </span>
                        </div>
                        <div style={styles.memberItemInfo}>
                          <span style={styles.memberItemName}>{persona.name}</span>
                          <span style={styles.memberItemDesc}>
                            {persona.description.slice(0, 30)}
                          </span>
                        </div>
                        <div
                          style={{
                            ...styles.memberCheckbox,
                            ...(isSelected ? styles.memberCheckboxSelected : {}),
                          }}
                        >
                          {isSelected && (
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3">
                              <path d="M20 6L9 17l-5-5" />
                            </svg>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            {/* 发言模式 */}
            <div style={styles.formGroup}>
              <label style={styles.formLabel}>发言模式</label>
              <div style={styles.radioGroup}>
                {speakModeOptions.map((opt) => (
                  <div
                    key={opt.value}
                    style={{
                      ...styles.radioItem,
                      ...(newGroupSpeakMode === opt.value ? styles.radioItemSelected : {}),
                    }}
                    onClick={() => setNewGroupSpeakMode(opt.value)}
                  >
                    <div
                      style={{
                        ...styles.radioCircle,
                        ...(newGroupSpeakMode === opt.value ? styles.radioCircleSelected : {}),
                      }}
                    >
                      {newGroupSpeakMode === opt.value && <div style={styles.radioDot} />}
                    </div>
                    <div style={styles.radioText}>
                      <span style={styles.radioLabel}>{opt.label}</span>
                      <span style={styles.radioDesc}>{opt.desc}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 关系设定 */}
            <div style={styles.formGroup}>
              <label style={styles.formLabel}>关系设定</label>
              <div style={styles.chipGroup}>
                {relationshipOptions.map((opt) => (
                  <div
                    key={opt.value}
                    style={{
                      ...styles.chip,
                      ...(newGroupRelationship === opt.value ? styles.chipSelected : {}),
                    }}
                    onClick={() => setNewGroupRelationship(opt.value)}
                  >
                    {opt.label}
                  </div>
                ))}
              </div>
            </div>

            {/* 创建按钮 */}
            <button
              style={{
                ...styles.createBtn,
                opacity: newGroupName.trim() && newGroupMembers.length > 0 ? 1 : 0.4,
              }}
              onClick={handleCreateGroup}
              disabled={!newGroupName.trim() || newGroupMembers.length === 0}
            >
              创建群聊
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ============================================================
// 样式定义
// ============================================================

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: "flex",
    flexDirection: "column",
    height: "100%",
    background: "var(--bg-primary)",
  },

  // ===== 顶部导航 =====
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
  headerInfo: {
    display: "flex",
    flexDirection: "column",
    flex: 1,
    marginLeft: "var(--space-2)",
  },
  headerName: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  headerStatus: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
  },
  addBtn: {
    background: "var(--gradient-primary)",
    color: "var(--text-primary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-full)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    boxShadow: "var(--shadow-sm)",
  },
  memberAvatars: {
    display: "flex",
    alignItems: "center",
    gap: 2,
  },
  memberAvatarSmall: {
    width: 24,
    height: 24,
    borderRadius: "var(--radius-full)",
    background: "var(--bg-card)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    border: "1.5px solid var(--accent-primary)",
    marginLeft: -4,
  },
  moreCount: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
    marginLeft: "var(--space-1)",
  },

  // ===== 群聊列表 =====
  listArea: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-3) var(--space-4)",
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-3)",
  },
  emptyState: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    flex: 1,
    gap: "var(--space-2)",
    opacity: 0.6,
  },
  emptyTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  emptyDesc: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
  },
  groupCard: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-4) var(--space-4)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
    borderLeft: "3px solid var(--accent-primary)",
    cursor: "pointer",
    boxShadow: "var(--shadow-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  groupAvatar: {
    width: 48,
    height: 48,
    borderRadius: "var(--radius-full)",
    background: "var(--gradient-primary)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  groupInfo: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-1)",
    overflow: "hidden",
  },
  groupName: {
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  groupMembers: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
  },
  groupTags: {
    display: "flex",
    gap: "var(--space-2)",
    flexWrap: "wrap",
  },
  groupTag: {
    fontSize: "var(--text-xs)",
    padding: "2px var(--space-2)",
    borderRadius: "var(--radius-full)",
    background: "var(--bg-secondary)",
    color: "var(--text-secondary)",
    border: "1px solid var(--border-color)",
  },
  deleteBtn: {
    background: "transparent",
    color: "var(--text-muted)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-sm)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },

  // ===== 弹窗 =====
  modalOverlay: {
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
  modalContent: {
    width: "100%",
    maxWidth: 500,
    maxHeight: "85vh",
    background: "var(--bg-secondary)",
    borderRadius: "var(--radius-xl) var(--radius-xl) 0 0",
    padding: "var(--space-5) var(--space-5) calc(var(--space-5) + var(--safe-bottom))",
    overflow: "auto",
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-4)",
  },
  modalHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  modalTitle: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-primary)",
  },
  modalCloseBtn: {
    background: "transparent",
    color: "var(--text-secondary)",
    padding: "var(--space-1)",
    borderRadius: "var(--radius-sm)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },

  // ===== 表单 =====
  formGroup: {
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-2)",
  },
  formLabel: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  selectedCount: {
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-regular)",
    color: "var(--accent-primary)",
  },
  formInput: {
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-md)",
    padding: "var(--space-3) var(--space-4)",
    color: "var(--text-primary)",
    fontSize: "var(--text-base)",
    outline: "none",
  },
  memberList: {
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-2)",
    maxHeight: 200,
    overflow: "auto",
  },
  noPersonas: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
    textAlign: "center" as const,
    padding: "var(--space-4)",
  },
  memberItem: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-3) var(--space-3)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    border: "1px solid var(--border-color)",
    cursor: "pointer",
    transition: "all 0.15s ease",
  },
  memberItemSelected: {
    borderColor: "var(--accent-primary)",
    background: "var(--bg-card-hover)",
  },
  memberItemAvatar: {
    width: 36,
    height: 36,
    borderRadius: "var(--radius-full)",
    background: "var(--bg-secondary)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    border: "2px solid var(--accent-primary)",
  },
  memberItemInfo: {
    flex: 1,
    display: "flex",
    flexDirection: "column",
    overflow: "hidden",
  },
  memberItemName: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  memberItemDesc: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
  },
  memberCheckbox: {
    width: 22,
    height: 22,
    borderRadius: "var(--radius-full)",
    border: "2px solid var(--border-color)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    transition: "all 0.15s ease",
  },
  memberCheckboxSelected: {
    border: "none",
    background: "var(--accent-primary)",
  },

  // ===== 单选按钮组 =====
  radioGroup: {
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-2)",
  },
  radioItem: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    padding: "var(--space-3) var(--space-3)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    border: "1px solid var(--border-color)",
    cursor: "pointer",
  },
  radioItemSelected: {
    borderColor: "var(--accent-primary)",
  },
  radioCircle: {
    width: 20,
    height: 20,
    borderRadius: "var(--radius-full)",
    border: "2px solid var(--border-color)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  radioCircleSelected: {
    borderColor: "var(--accent-primary)",
  },
  radioDot: {
    width: 10,
    height: 10,
    borderRadius: "var(--radius-full)",
    background: "var(--accent-primary)",
  },
  radioText: {
    display: "flex",
    flexDirection: "column",
  },
  radioLabel: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
  },
  radioDesc: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
  },

  // ===== 标签选择 =====
  chipGroup: {
    display: "flex",
    flexWrap: "wrap",
    gap: "var(--space-2)",
  },
  chip: {
    padding: "var(--space-2) var(--space-4)",
    borderRadius: "var(--radius-full)",
    background: "var(--bg-card)",
    border: "1px solid var(--border-color)",
    color: "var(--text-primary)",
    fontSize: "var(--text-sm)",
    cursor: "pointer",
    transition: "all 0.15s ease",
  },
  chipSelected: {
    background: "var(--accent-primary)",
    borderColor: "var(--accent-primary)",
    color: "var(--text-primary)",
  },

  // ===== 创建按钮 =====
  createBtn: {
    padding: "var(--space-4) 0",
    borderRadius: "var(--radius-lg)",
    background: "var(--gradient-primary)",
    color: "var(--text-primary)",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    border: "none",
    cursor: "pointer",
    marginTop: "var(--space-1)",
    boxShadow: "var(--shadow-md)",
  },

  // ===== 对话消息区域 =====
  messagesArea: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-3) var(--space-4)",
    display: "flex",
    flexDirection: "column",
    gap: "var(--space-3)",
  },
  messageRow: {
    display: "flex",
    alignItems: "flex-end",
    gap: "var(--space-2)",
  },
  petAvatar: {
    width: 32,
    height: 32,
    borderRadius: "var(--radius-full)",
    background: "var(--bg-card)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    border: "2px solid var(--accent-primary)",
  },
  messageBubble: {
    maxWidth: "75%",
    padding: "var(--space-3) var(--space-4)",
    borderRadius: "var(--radius-lg)",
    position: "relative",
    boxShadow: "0 2px 8px rgba(0, 0, 0, 0.1)",
  },
  petBubble: {
    background: "var(--chat-bubble-pet)",
    borderBottomLeftRadius: 4,
  },
  userBubble: {
    background: "var(--chat-bubble-user)",
    borderBottomRightRadius: 4,
  },
  senderName: {
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-semibold)",
    display: "block",
    marginBottom: "var(--space-1)",
  },
  messageText: {
    fontSize: "var(--text-base)",
    lineHeight: "var(--leading-relaxed)",
    color: "var(--text-primary)",
    wordBreak: "break-word",
    margin: 0,
  },
  messageMeta: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
    marginTop: "var(--space-1)",
  },
  messageTime: {
    fontSize: "var(--text-xs)",
    color: "var(--text-muted)",
  },
  emotionTag: {
    fontSize: "var(--text-xs)",
    fontWeight: "var(--weight-medium)",
  },

  // ===== 打字指示器 =====
  typingRow: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-2)",
  },
  typingName: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--accent-primary)",
  },
  typingText: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
  },
  typingIndicator: {
    display: "flex",
    gap: 3,
    padding: "2px 0",
  },
  typingDot: {
    width: 6,
    height: 6,
    borderRadius: "var(--radius-full)",
    background: "var(--text-secondary)",
    animation: "typing 1.4s infinite",
  },

  // ===== 输入区域 =====
  inputArea: {
    padding: "var(--space-2) var(--space-4)",
    paddingBottom: "calc(var(--space-2) + var(--safe-bottom))",
    background: "var(--bg-secondary)",
    borderTop: "1px solid var(--border-color)",
  },
  inputContainer: {
    display: "flex",
    alignItems: "flex-end",
    gap: "var(--space-2)",
    background: "var(--bg-input)",
    borderRadius: "var(--radius-full)",
    padding: "var(--space-2) var(--space-2) var(--space-2) var(--space-4)",
    border: "1px solid var(--border-color)",
  },
  textInput: {
    flex: 1,
    background: "transparent",
    border: "none",
    color: "var(--text-primary)",
    fontSize: "var(--text-base)",
    padding: "var(--space-2) 0",
    resize: "none",
    outline: "none",
    maxHeight: 120,
    lineHeight: "var(--leading-normal)",
  },
  sendBtn: {
    width: 36,
    height: 36,
    borderRadius: "var(--radius-full)",
    background: "var(--gradient-primary)",
    color: "var(--text-primary)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    border: "none",
    cursor: "pointer",
  },
};
