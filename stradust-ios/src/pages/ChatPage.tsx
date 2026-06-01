import { useState, useEffect, useRef, useCallback } from "react";
import {
  ChatMessage,
  sendChat,
  loadChatHistory,
  saveChatHistory,
  loadSettings,
  loadPersonas,
  DEFAULT_CHARACTER,
  generateId,
  formatTime,
  getEmotionEmoji,
  getEmotionColor,
  humanizeResponse,
  filterContent,
  getSafetyRefusal,
  predictChat,
  recordEmotionEvent,
  getEmotionTrend,
  getCareMessage,
  type ApiConfig,
  type CharacterCard,
  type HumanizedSegment,
} from "../utils/api";
import {
  messageBubbleIn,
  typingDots,
  bottomSheetIn,
  buttonPress,
  fadeInScale,
} from "../utils/animations";

interface ChatPageProps {
  onOpenSettings: () => void;
  onOpenProfile: () => void;
  onOpenChatHistory?: () => void;
}

export default function ChatPage({ onOpenSettings, onOpenProfile, onOpenChatHistory }: ChatPageProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputText, setInputText] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const [apiConfig, setApiConfig] = useState<ApiConfig | null>(null);
  const [activePersonaId, setActivePersonaId] = useState("default_stardust");
  const [activePersona, setActivePersona] = useState<CharacterCard>(DEFAULT_CHARACTER);
  const [contextTurns, setContextTurns] = useState(10);
  const [reactionMsgId, setReactionMsgId] = useState<string | null>(null);
  const [predictions, setPredictions] = useState<string[]>([]);
  const [showPredictions, setShowPredictions] = useState(false);
  const [careMessage, setCareMessage] = useState<string | null>(null);
  const [inputFocused, setInputFocused] = useState(false);
  const [sendBtnActive, setSendBtnActive] = useState(false);
  const [predictBtnActive, setPredictBtnActive] = useState(false);
  const [activeEmoji, setActiveEmoji] = useState<string | null>(null);
  const [activePrediction, setActivePrediction] = useState<number | null>(null);
  const [avatarPressed, setAvatarPressed] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const messagesAreaRef = useRef<HTMLDivElement>(null);
  const typingDotsRef = useRef<HTMLDivElement>(null);
  const emojiPanelRef = useRef<HTMLDivElement>(null);
  const predictionPanelRef = useRef<HTMLDivElement>(null);
  const sendBtnRef = useRef<HTMLButtonElement>(null);
  const prevMsgCountRef = useRef(0);

  const REACTION_EMOJIS = ["❤️", "😂", "😮", "😢", "😡", "👍", "👏", "🎉"];

  const makeMsg = (partial: Omit<ChatMessage, "image_urls" | "user_mood" | "feedback" | "sticker_path" | "generated_image_path" | "audio_path" | "audio_url">): ChatMessage => ({
    image_urls: [],
    user_mood: "",
    feedback: 0,
    ...partial,
  });

  // 情绪颜色映射到设计系统 accent 色
  const getEmotionAccentColor = (emotion: string): string => {
    const map: Record<string, string> = {
      Happy: "var(--accent-green)", happy: "var(--accent-green)",
      Sad: "var(--accent-secondary)", sad: "var(--accent-secondary)",
      Angry: "var(--accent-red)", angry: "var(--accent-red)",
      Surprised: "var(--accent-orange)", surprised: "var(--accent-orange)",
      Tsundere: "var(--accent-pink)", tsundere: "var(--accent-pink)",
      Neutral: "var(--text-secondary)", neutral: "var(--text-secondary)",
    };
    return map[emotion] || "var(--text-secondary)";
  };

  // 加载设置和聊天记录
  useEffect(() => {
    async function init() {
      const settings = await loadSettings();
      if (settings?.api_config) {
        setApiConfig(settings.api_config);
      }
      const personaId = settings?.active_persona_id || "default_stardust";
      setActivePersonaId(personaId);
      if (settings?.context_turns) {
        setContextTurns(settings.context_turns);
      }
      // 加载活跃角色信息
      const personas = await loadPersonas();
      const found = personas.find((p) => p.id === personaId);
      if (found) {
        setActivePersona(found);
      }
      const history = await loadChatHistory(personaId);
      if (history.length > 0) {
        setMessages(history);
      } else {
        // 显示欢迎消息
        const welcomeMsg = makeMsg({
          id: generateId(),
          text: found ? found.first_mes : DEFAULT_CHARACTER.first_mes,
          time: formatTime(new Date()),
          is_user: false,
          emotion: "Tsundere",
          timestamp: Date.now(),
          is_favorited: false,
          reaction_emoji: "",
        });
        setMessages([welcomeMsg]);
      }
      // 情感守护检查
      try {
        const trend = await getEmotionTrend(4);
        if (trend === "VERY_NEGATIVE" || trend === "NEGATIVE") {
          const care = await getCareMessage(trend);
          if (care) {
            setCareMessage(care);
          }
        }
      } catch {}
    }
    init();
  }, []);

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // 消息气泡入场动画
  useEffect(() => {
    const currentCount = messages.length;
    if (currentCount > prevMsgCountRef.current && messagesAreaRef.current) {
      const rows = messagesAreaRef.current.querySelectorAll("[data-msg-row]");
      const lastRow = rows[rows.length - 1];
      if (lastRow) {
        const lastMsg = messages[messages.length - 1];
        messageBubbleIn(lastRow, lastMsg.is_user);
      }
    }
    prevMsgCountRef.current = currentCount;
  }, [messages]);

  // 打字指示器动画
  useEffect(() => {
    let anim: ReturnType<typeof typingDots> | null = null;
    if (isTyping && typingDotsRef.current) {
      anim = typingDots(typingDotsRef.current.children);
    }
    return () => { if (anim) anim.revert(); };
  }, [isTyping]);

  // Emoji 面板入场动画
  useEffect(() => {
    if (reactionMsgId && emojiPanelRef.current) {
      fadeInScale(emojiPanelRef.current);
    }
  }, [reactionMsgId]);

  // 预测面板入场动画
  useEffect(() => {
    if (showPredictions && predictionPanelRef.current) {
      bottomSheetIn(predictionPanelRef.current);
    }
  }, [showPredictions]);

  // 保存聊天记录
  const saveHistory = useCallback(
    async (msgs: ChatMessage[]) => {
      await saveChatHistory(activePersonaId, msgs);
    },
    [activePersonaId]
  );

  // 发送消息
  const handleSend = async () => {
    const text = inputText.trim();
    if (!text || isLoading) return;

    // 发送按钮按压动画
    if (sendBtnRef.current) {
      buttonPress(sendBtnRef.current);
    }

    // 内容安全过滤
    const settings = await loadSettings();
    if (settings?.content_safety_enabled) {
      const filtered = await filterContent(text, true);
      if (!filtered) {
        const refusal = await getSafetyRefusal();
        const safetyMsg = makeMsg({
          id: generateId(),
          text: refusal,
          time: formatTime(new Date()),
          is_user: false,
          emotion: "Neutral",
          timestamp: Date.now(),
          is_favorited: false,
          reaction_emoji: "",
        });
        setMessages((prev) => [...prev, safetyMsg]);
        return;
      }
    }

    if (!apiConfig?.chat_api_url) {
      const errorMsg = makeMsg({
        id: generateId(),
        text: "请先在设置中配置 API 地址哦~",
        time: formatTime(new Date()),
        is_user: false,
        emotion: "Neutral",
        timestamp: Date.now(),
        is_favorited: false,
        reaction_emoji: "",
      });
      setMessages((prev) => [...prev, errorMsg]);
      return;
    }

    const userMsg = makeMsg({
      id: generateId(),
      text,
      time: formatTime(new Date()),
      is_user: true,
      emotion: null,
      timestamp: Date.now(),
      is_favorited: false,
      reaction_emoji: "",
    });

    const newMessages = [...messages, userMsg];
    setMessages(newMessages);
    setInputText("");
    setIsLoading(true);
    setIsTyping(true);

    try {
      // 构建消息历史
      const chatHistory = newMessages
        .slice(-contextTurns * 2)
        .map((msg) => ({
          role: msg.is_user ? "user" : "assistant",
          content: msg.text,
        }));

      // 添加系统提示
      const allMessages = [
        {
          role: "system" as const,
          content: activePersona.system_prompt,
        },
        ...chatHistory,
      ];

      const response = await sendChat(
        apiConfig.chat_api_url,
        apiConfig.api_key,
        apiConfig.model_name,
        allMessages,
        apiConfig.temperature,
        apiConfig.max_tokens,
        apiConfig.top_p,
        apiConfig.frequency_penalty,
        apiConfig.presence_penalty,
      );

      setIsTyping(false);

      if (response.error_message) {
        const errorMsg = makeMsg({
          id: generateId(),
          text: `呜...${response.error_message}`,
          time: formatTime(new Date()),
          is_user: false,
          emotion: "Sad",
          timestamp: Date.now(),
          is_favorited: false,
          reaction_emoji: "",
        });
        const updated = [...newMessages, errorMsg];
        setMessages(updated);
        await saveHistory(updated);
      } else {
        // 人性化处理
        let displayText = response.text;
        try {
          const segments = await humanizeResponse(response.text);
          if (segments.length > 1) {
            // 逐段显示
            let accumulated = "";
            for (let i = 0; i < segments.length; i++) {
              accumulated += (i > 0 ? "" : "") + segments[i].text;
              const partialText = accumulated;
              const partialMsg = makeMsg({
                id: generateId(),
                text: partialText,
                time: formatTime(new Date()),
                is_user: false,
                emotion: response.emotion,
                timestamp: Date.now(),
                is_favorited: false,
                reaction_emoji: "",
              });
              if (i === 0) {
                const updated = [...newMessages, partialMsg];
                setMessages(updated);
              } else {
                setMessages((prev) => {
                  const newPrev = [...prev];
                  newPrev[newPrev.length - 1] = partialMsg;
                  return newPrev;
                });
              }
              if (i < segments.length - 1) {
                await new Promise((r) => setTimeout(r, segments[i].delay_ms));
              }
            }
            // 保存最终消息
            const finalMsg = makeMsg({
              id: generateId(),
              text: accumulated,
              time: formatTime(new Date()),
              is_user: false,
              emotion: response.emotion,
              timestamp: Date.now(),
              is_favorited: false,
              reaction_emoji: "",
            });
            const updated = [...newMessages, finalMsg];
            await saveHistory(updated);
          } else {
            const aiMsg = makeMsg({
              id: generateId(),
              text: response.text,
              time: formatTime(new Date()),
              is_user: false,
              emotion: response.emotion,
              timestamp: Date.now(),
              is_favorited: false,
              reaction_emoji: "",
            });
            const updated = [...newMessages, aiMsg];
            setMessages(updated);
            await saveHistory(updated);
          }
        } catch {
          const aiMsg = makeMsg({
            id: generateId(),
            text: response.text,
            time: formatTime(new Date()),
            is_user: false,
            emotion: response.emotion,
            timestamp: Date.now(),
            is_favorited: false,
            reaction_emoji: "",
          });
          const updated = [...newMessages, aiMsg];
          setMessages(updated);
          await saveHistory(updated);
        }
        // 记录情绪事件
        if (response.emotion) {
          try { await recordEmotionEvent(response.emotion, 0.5); } catch {}
        }
      }
    } catch (error) {
      setIsTyping(false);
      const errorMsg = makeMsg({
        id: generateId(),
        text: `出错了: ${error}`,
        time: formatTime(new Date()),
        is_user: false,
        emotion: "Sad",
        timestamp: Date.now(),
        is_favorited: false,
        reaction_emoji: "",
      });
      const updated = [...newMessages, errorMsg];
      setMessages(updated);
      await saveHistory(updated);
    } finally {
      setIsLoading(false);
    }
  };

  // 处理键盘事件
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // 处理消息反应
  const handleReaction = async (msgId: string, emoji: string) => {
    const updated = messages.map((m) =>
      m.id === msgId ? { ...m, reaction_emoji: m.reaction_emoji === emoji ? "" : emoji } : m
    );
    setMessages(updated);
    await saveHistory(updated);
    setReactionMsgId(null);
  };

  // 生成聊天预测
  const handlePredict = async () => {
    if (!apiConfig?.chat_api_url || predictions.length > 0) return;
    try {
      const recentTexts = messages.slice(-10).map((m) => m.text);
      const preds = await predictChat(
        apiConfig.chat_api_url,
        apiConfig.api_key,
        apiConfig.model_name,
        recentTexts,
      );
      setPredictions(preds);
      setShowPredictions(true);
    } catch {}
  };

  // 使用预测回复
  const handleUsePrediction = (pred: string) => {
    setInputText(pred);
    setShowPredictions(false);
    setPredictions([]);
  };

  // 自动调整输入框高度
  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputText(e.target.value);
    const textarea = e.target;
    textarea.style.height = "auto";
    textarea.style.height = Math.min(textarea.scrollHeight, 120) + "px";
  };

  return (
    <div style={styles.container}>
      {/* 顶部导航栏 */}
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <div
            style={{
              ...styles.avatarContainer,
              ...(avatarPressed ? { transform: "scale(0.95)" } : {}),
            }}
            onClick={onOpenProfile}
            onMouseDown={() => setAvatarPressed(true)}
            onMouseUp={() => setAvatarPressed(false)}
            onMouseLeave={() => setAvatarPressed(false)}
          >
            <span style={{ fontSize: "var(--text-xl)" }}>🐱</span>
          </div>
          <div style={styles.headerInfo}>
            <span style={styles.headerName}>{activePersona.name}</span>
            <span style={styles.headerStatus}>
              <span style={{
                display: "inline-block",
                width: 6,
                height: 6,
                borderRadius: "var(--radius-full)",
                background: isLoading ? "var(--text-muted)" : "var(--accent-green)",
                marginRight: "var(--space-1)",
                verticalAlign: "middle",
                boxShadow: isLoading ? "none" : "0 0 6px rgba(76, 217, 100, 0.5)",
              }} />
              {isLoading ? "正在输入..." : "在线"}
            </span>
          </div>
        </div>
        <button style={styles.settingsBtn} onClick={onOpenSettings}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="3" />
            <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
          </svg>
        </button>
        {onOpenChatHistory && (
          <button style={styles.historyBtn} onClick={onOpenChatHistory}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
            </svg>
          </button>
        )}
      </div>

      {/* 聊天消息区域 */}
      <div ref={messagesAreaRef} style={styles.messagesArea}>
        {/* 情感守护关怀消息 */}
        {careMessage && (
          <div className="animate-fade-in-down" style={styles.careBanner}>
            <span style={styles.careText}>{careMessage}</span>
            <button style={styles.careClose} onClick={() => setCareMessage(null)}>✕</button>
          </div>
        )}
        {messages.map((msg) => (
          <div
            key={msg.id}
            data-msg-row
            style={{
              ...styles.messageRow,
              justifyContent: msg.is_user ? "flex-end" : "flex-start",
            }}
          >
            {!msg.is_user && (
              <div style={styles.petAvatar}>
                <span style={{ fontSize: "var(--text-lg)" }}>
                  {getEmotionEmoji(msg.emotion || "Neutral")}
                </span>
              </div>
            )}
            <div
              style={{
                ...styles.messageBubble,
                ...(msg.is_user ? styles.userBubble : styles.petBubble),
                borderLeft: msg.is_user
                  ? "none"
                  : `3px solid ${getEmotionAccentColor(msg.emotion || "Neutral")}`,
              }}
              onContextMenu={(e) => {
                e.preventDefault();
                setReactionMsgId(msg.id);
              }}
              onClick={() => {
                if (reactionMsgId) setReactionMsgId(null);
              }}
            >
              <p style={styles.messageText}>{msg.text}</p>
              <div style={styles.messageMeta}>
                <span style={styles.messageTime}>{msg.time}</span>
                {!msg.is_user && msg.emotion && (
                  <span
                    style={{
                      ...styles.emotionTag,
                      color: getEmotionAccentColor(msg.emotion),
                    }}
                  >
                    {msg.emotion}
                  </span>
                )}
              </div>
              {!msg.is_user && msg.reaction_emoji && (
                <span style={styles.reactionBadge}>{msg.reaction_emoji}</span>
              )}
              {msg.is_user && msg.reaction_emoji && (
                <span style={styles.reactionBadge}>{msg.reaction_emoji}</span>
              )}
            </div>
          </div>
        ))}

        {/* 打字指示器 */}
        {isTyping && (
          <div style={{ ...styles.messageRow, justifyContent: "flex-start" }}>
            <div style={styles.petAvatar}>
              <span style={{ fontSize: "var(--text-lg)" }}>🐱</span>
            </div>
            <div style={{ ...styles.messageBubble, ...styles.petBubble }}>
              <div ref={typingDotsRef} style={styles.typingIndicator}>
                <span style={styles.typingDot} />
                <span style={styles.typingDot} />
                <span style={styles.typingDot} />
              </div>
            </div>
          </div>
        )}

        {/* Emoji 反应面板 */}
        {reactionMsgId && (
          <div ref={emojiPanelRef} style={styles.emojiPanel}>
            {REACTION_EMOJIS.map((emoji) => (
              <button
                key={emoji}
                style={{
                  ...styles.emojiBtn,
                  transform: activeEmoji === emoji ? "scale(1.2)" : "scale(1)",
                }}
                onClick={() => handleReaction(reactionMsgId, emoji)}
                onMouseDown={() => setActiveEmoji(emoji)}
                onMouseUp={() => setActiveEmoji(null)}
                onMouseLeave={() => setActiveEmoji(null)}
              >
                {emoji}
              </button>
            ))}
            <button
              style={styles.emojiRemoveBtn}
              onClick={() => handleReaction(reactionMsgId, "")}
            >
              ✕
            </button>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* 输入区域 */}
      <div style={styles.inputArea}>
        <div
          style={{
            ...styles.inputContainer,
            ...(inputFocused ? styles.inputContainerFocused : {}),
          }}
        >
          {/* 聊天预测 */}
          {apiConfig?.chat_api_url && (
            <button
              style={{
                ...styles.predictBtn,
                color: predictBtnActive ? "var(--accent-primary)" : "var(--text-secondary)",
              }}
              onClick={handlePredict}
              onMouseDown={() => setPredictBtnActive(true)}
              onMouseUp={() => setPredictBtnActive(false)}
              onMouseLeave={() => setPredictBtnActive(false)}
              disabled={isLoading}
              title="AI预测回复"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
            </button>
          )}
          <textarea
            ref={inputRef}
            style={styles.textInput}
            value={inputText}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            onFocus={() => setInputFocused(true)}
            onBlur={() => setInputFocused(false)}
            placeholder="和星尘说点什么..."
            rows={1}
          />
          <button
            ref={sendBtnRef}
            style={{
              ...styles.sendBtn,
              opacity: inputText.trim() && !isLoading ? 1 : 0.4,
              transform: sendBtnActive ? "scale(0.92)" : "scale(1)",
            }}
            onClick={handleSend}
            onMouseDown={() => setSendBtnActive(true)}
            onMouseUp={() => setSendBtnActive(false)}
            onMouseLeave={() => setSendBtnActive(false)}
            disabled={isLoading || !inputText.trim()}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
            </svg>
          </button>
        </div>
      </div>

      {/* 聊天预测面板 */}
      {showPredictions && predictions.length > 0 && (
        <div ref={predictionPanelRef} style={styles.predictionPanel}>
          <div style={styles.predictionHeader}>
            <span style={styles.predictionTitle}>AI 预测回复</span>
            <button style={styles.predictionClose} onClick={() => { setShowPredictions(false); setPredictions([]); }}>✕</button>
          </div>
          <div style={styles.predictionList}>
            {predictions.map((pred, i) => (
              <button
                key={i}
                style={{
                  ...styles.predictionItem,
                  ...(activePrediction === i ? {
                    transform: "scale(0.98)",
                    background: "var(--bg-card-hover)",
                  } : {}),
                }}
                onClick={() => handleUsePrediction(pred)}
                onMouseDown={() => setActivePrediction(i)}
                onMouseUp={() => setActivePrediction(null)}
                onMouseLeave={() => setActivePrediction(null)}
              >
                {pred}
              </button>
            ))}
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
    backdropFilter: "blur(20px)",
    WebkitBackdropFilter: "blur(20px)",
    boxShadow: "var(--shadow-sm)",
    position: "relative" as const,
    zIndex: 10,
  },
  headerLeft: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
  },
  avatarContainer: {
    width: 44,
    height: 44,
    borderRadius: "var(--radius-xl)",
    background: "var(--bg-card)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    border: "2px solid var(--accent-primary)",
    boxShadow: "0 0 12px rgba(139, 108, 255, 0.2)",
    transition: "transform var(--duration-fast) var(--ease-out-quart)",
  },
  headerInfo: {
    display: "flex",
    flexDirection: "column",
  },
  headerName: {
    fontSize: "var(--text-md)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-primary)",
    letterSpacing: "var(--tracking-tight)",
  },
  headerStatus: {
    fontSize: "var(--text-xs)",
    color: "var(--text-secondary)",
  },
  settingsBtn: {
    background: "transparent",
    color: "var(--text-secondary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-sm)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
  historyBtn: {
    background: "transparent",
    color: "var(--text-secondary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-sm)",
    marginLeft: "var(--space-1)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
  },
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
    borderRadius: "var(--radius-lg)",
    background: "var(--bg-card)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  messageBubble: {
    maxWidth: "75%",
    padding: "var(--space-3) var(--space-4)",
    borderRadius: "var(--radius-lg)",
    position: "relative",
    transition: "transform var(--duration-fast) var(--ease-out-quart)",
  },
  petBubble: {
    background: "var(--chat-bubble-pet)",
    borderBottomLeftRadius: "var(--radius-sm)",
    borderTop: "1px solid rgba(139, 108, 255, 0.1)",
    boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
  },
  userBubble: {
    background: "var(--chat-bubble-user)",
    borderBottomRightRadius: "var(--radius-sm)",
    borderTop: "1px solid rgba(94, 162, 255, 0.1)",
    boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
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
  typingIndicator: {
    display: "flex",
    gap: "var(--space-1)",
    padding: "var(--space-1) 0",
  },
  typingDot: {
    width: 8,
    height: 8,
    borderRadius: "var(--radius-full)",
    background: "var(--accent-primary)",
    opacity: 0.3,
  },
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
    borderRadius: "var(--radius-xl)",
    padding: "var(--space-2) var(--space-2) var(--space-2) var(--space-4)",
    border: "1px solid var(--border-color)",
    transition: "border-color var(--duration-fast) var(--ease-out-quart), box-shadow var(--duration-fast) var(--ease-out-quart)",
  },
  inputContainerFocused: {
    borderColor: "var(--accent-primary)",
    boxShadow: "0 0 0 3px rgba(139, 108, 255, 0.15), var(--shadow-glow)",
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
    minHeight: "auto",
  },
  sendBtn: {
    width: 36,
    height: 36,
    borderRadius: "var(--radius-full)",
    background: "var(--gradient-primary)",
    color: "white",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    boxShadow: "var(--shadow-sm)",
    transition: "transform var(--duration-fast) var(--ease-out-quart), opacity var(--duration-fast) var(--ease-out-quart)",
  },
  reactionBadge: {
    fontSize: "var(--text-lg)",
    marginLeft: "var(--space-1)",
    verticalAlign: "middle" as const,
  },
  emojiPanel: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-1)",
    padding: "var(--space-2) var(--space-3)",
    background: "var(--bg-tertiary)",
    borderTop: "1px solid var(--border-color)",
    justifyContent: "center",
    flexWrap: "wrap" as const,
  },
  emojiBtn: {
    fontSize: 24,
    padding: "var(--space-1) var(--space-2)",
    background: "transparent",
    borderRadius: "var(--radius-sm)",
    border: "none",
    cursor: "pointer",
    transition: "transform var(--duration-fast) var(--ease-out-quart)",
  },
  emojiRemoveBtn: {
    fontSize: "var(--text-sm)",
    padding: "var(--space-1) var(--space-3)",
    background: "rgba(255, 92, 124, 0.12)",
    color: "var(--accent-red)",
    borderRadius: "var(--radius-sm)",
    border: "none",
    cursor: "pointer",
    fontWeight: "var(--weight-semibold)",
  },
  careBanner: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-3) var(--space-4)",
    background: "rgba(139, 108, 255, 0.1)",
    borderBottom: "1px solid var(--border-color)",
    borderLeft: "3px solid var(--accent-primary)",
    margin: `0 var(--space-4)`,
    borderRadius: "var(--radius-md) var(--radius-md) 0 0",
  },
  careText: {
    fontSize: "var(--text-sm)",
    color: "var(--accent-primary)",
    flex: 1,
  },
  careClose: {
    background: "transparent",
    color: "var(--text-muted)",
    fontSize: "var(--text-sm)",
    padding: "var(--space-1) var(--space-2)",
  },
  predictBtn: {
    background: "transparent",
    color: "var(--text-secondary)",
    padding: "var(--space-2)",
    borderRadius: "var(--radius-sm)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    transition: "color var(--duration-fast) var(--ease-out-quart)",
  },
  predictionPanel: {
    background: "var(--bg-secondary)",
    borderTop: "1px solid var(--border-color)",
    padding: "var(--space-3) var(--space-4)",
    paddingBottom: "calc(var(--space-3) + var(--safe-bottom))",
  },
  predictionHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: "var(--space-2)",
  },
  predictionTitle: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-semibold)",
    color: "var(--text-secondary)",
  },
  predictionClose: {
    background: "transparent",
    color: "var(--text-muted)",
    fontSize: "var(--text-sm)",
    padding: "var(--space-1) var(--space-2)",
  },
  predictionList: {
    display: "flex",
    flexDirection: "column" as const,
    gap: "var(--space-2)",
  },
  predictionItem: {
    textAlign: "left" as const,
    padding: "var(--space-3) var(--space-4)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-md)",
    color: "var(--text-primary)",
    fontSize: "var(--text-sm)",
    border: "1px solid var(--border-color)",
    cursor: "pointer",
    transition: "transform var(--duration-fast) var(--ease-out-quart), background var(--duration-fast) var(--ease-out-quart)",
  },
};
