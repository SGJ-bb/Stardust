import { useState, useEffect } from "react";
import {
  loadSettings,
  saveSettings,
  testConnection,
  type AppSettings,
  type ApiConfig,
} from "../utils/api";

interface SettingsPageProps {
  onBack: () => void;
}

export default function SettingsPage({ onBack }: SettingsPageProps) {
  const [settings, setSettings] = useState<AppSettings>({
    api_config: {
      chat_api_url: "",
      api_key: "",
      model_name: "gpt-4o-mini",
      temperature: 1.05,
      top_p: 0.92,
      frequency_penalty: 0.35,
      presence_penalty: 0.5,
      max_tokens: 500,
      provider_id: "custom",
    },
    active_persona_id: "default_stardust",
    tts_enabled: false,
    tts_engine_mode: "auto",
    emotion_analysis_enabled: false,
    user_nickname: "",
    theme: "dark",
    search_enabled: true,
    diary_trigger_mode: "auto",
    content_safety_enabled: true,
    context_turns: 10,
    user_call_name: "",
    proactive_interaction_enabled: true,
    proactive_interaction_frequency: "normal",
  });

  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    async function load() {
      try {
        const s = await loadSettings();
        setSettings(s);
      } catch {
        // 使用默认值
      }
    }
    load();
  }, []);

  const handleSave = async () => {
    await saveSettings(settings);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const handleTestConnection = async () => {
    if (!settings.api_config.chat_api_url) {
      setTestResult("请先填写API地址");
      return;
    }
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testConnection(
        settings.api_config.chat_api_url,
        settings.api_config.api_key,
        settings.api_config.model_name
      );
      setTestResult(result);
    } catch (e) {
      setTestResult(`连接失败: ${e}`);
    } finally {
      setTesting(false);
    }
  };

  const updateApiConfig = (key: keyof ApiConfig, value: string | number) => {
    setSettings((prev) => ({
      ...prev,
      api_config: { ...prev.api_config, [key]: value },
    }));
  };

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={onBack}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </button>
        <span style={styles.headerTitle}>设置</span>
        <div style={{ width: 40 }} />
      </div>

      <div style={styles.content}>
        {/* API 配置 */}
        <div style={styles.section}>
          <h3 style={styles.sectionTitle}>API 配置</h3>

          <div style={styles.field}>
            <label style={styles.label}>API 地址</label>
            <input
              style={styles.input}
              value={settings.api_config.chat_api_url}
              onChange={(e) => updateApiConfig("chat_api_url", e.target.value)}
              placeholder="https://api.openai.com/v1/chat/completions"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>API Key</label>
            <input
              style={styles.input}
              type="password"
              value={settings.api_config.api_key}
              onChange={(e) => updateApiConfig("api_key", e.target.value)}
              placeholder="sk-..."
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>模型名称</label>
            <input
              style={styles.input}
              value={settings.api_config.model_name}
              onChange={(e) => updateApiConfig("model_name", e.target.value)}
              placeholder="gpt-4o-mini"
            />
          </div>

          <div style={styles.row}>
            <div style={{ ...styles.field, flex: 1 }}>
              <label style={styles.label}>Temperature</label>
              <input
                style={styles.input}
                type="number"
                step="0.05"
                value={settings.api_config.temperature}
                onChange={(e) =>
                  updateApiConfig("temperature", parseFloat(e.target.value) || 0)
                }
              />
            </div>
            <div style={{ ...styles.field, flex: 1 }}>
              <label style={styles.label}>Top P</label>
              <input
                style={styles.input}
                type="number"
                step="0.01"
                value={settings.api_config.top_p}
                onChange={(e) =>
                  updateApiConfig("top_p", parseFloat(e.target.value) || 0)
                }
              />
            </div>
          </div>

          <div style={styles.row}>
            <div style={{ ...styles.field, flex: 1 }}>
              <label style={styles.label}>Freq Penalty</label>
              <input
                style={styles.input}
                type="number"
                step="0.05"
                value={settings.api_config.frequency_penalty}
                onChange={(e) =>
                  updateApiConfig("frequency_penalty", parseFloat(e.target.value) || 0)
                }
              />
            </div>
            <div style={{ ...styles.field, flex: 1 }}>
              <label style={styles.label}>Pres Penalty</label>
              <input
                style={styles.input}
                type="number"
                step="0.05"
                value={settings.api_config.presence_penalty}
                onChange={(e) =>
                  updateApiConfig("presence_penalty", parseFloat(e.target.value) || 0)
                }
              />
            </div>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Max Tokens</label>
            <input
              style={styles.input}
              type="number"
              value={settings.api_config.max_tokens}
              onChange={(e) =>
                updateApiConfig("max_tokens", parseInt(e.target.value) || 500)
              }
            />
          </div>

          <div style={styles.testRow}>
            <button
              style={{ ...styles.testBtn, opacity: testing ? 0.6 : 1 }}
              onClick={handleTestConnection}
              disabled={testing}
            >
              {testing ? "测试中..." : "测试连接"}
            </button>
            {testResult && (
              <span
                style={{
                  ...styles.testResult,
                  color: testResult.includes("成功") ? "var(--accent-green)" : "var(--accent-red)",
                }}
              >
                {testResult}
              </span>
            )}
          </div>
        </div>

        {/* 用户设置 */}
        <div style={styles.section}>
          <h3 style={styles.sectionTitle}>用户设置</h3>

          <div style={styles.field}>
            <label style={styles.label}>你的昵称</label>
            <input
              style={styles.input}
              value={settings.user_nickname}
              onChange={(e) =>
                setSettings((prev) => ({ ...prev, user_nickname: e.target.value }))
              }
              placeholder="主人"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>AI对你的称呼</label>
            <input
              style={styles.input}
              value={settings.user_call_name}
              onChange={(e) =>
                setSettings((prev) => ({ ...prev, user_call_name: e.target.value }))
              }
              placeholder="主人"
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>上下文轮数</label>
            <input
              style={styles.input}
              type="number"
              value={settings.context_turns}
              onChange={(e) =>
                setSettings((prev) => ({ ...prev, context_turns: parseInt(e.target.value) || 10 }))
              }
            />
          </div>

          <SwitchRow
            label="语音朗读 (TTS)"
            value={settings.tts_enabled}
            onChange={(v) => setSettings((prev) => ({ ...prev, tts_enabled: v }))}
          />
          <SwitchRow
            label="情绪分析"
            value={settings.emotion_analysis_enabled}
            onChange={(v) => setSettings((prev) => ({ ...prev, emotion_analysis_enabled: v }))}
          />
          <SwitchRow
            label="网络搜索"
            value={settings.search_enabled}
            onChange={(v) => setSettings((prev) => ({ ...prev, search_enabled: v }))}
          />
          <SwitchRow
            label="内容安全过滤"
            value={settings.content_safety_enabled}
            onChange={(v) => setSettings((prev) => ({ ...prev, content_safety_enabled: v }))}
          />
          <SwitchRow
            label="AI主动互动"
            value={settings.proactive_interaction_enabled}
            onChange={(v) => setSettings((prev) => ({ ...prev, proactive_interaction_enabled: v }))}
          />
        </div>

        {/* 日记设置 */}
        <div style={styles.section}>
          <h3 style={styles.sectionTitle}>日记设置</h3>
          <div style={styles.field}>
            <label style={styles.label}>日记触发模式</label>
            <select
              style={styles.select}
              value={settings.diary_trigger_mode}
              onChange={(e) =>
                setSettings((prev) => ({ ...prev, diary_trigger_mode: e.target.value }))
              }
            >
              <option value="auto">自动</option>
              <option value="manual">手动</option>
              <option value="scheduled">定时</option>
            </select>
          </div>
        </div>

        {/* 关于 */}
        <div style={styles.section}>
          <h3 style={styles.sectionTitle}>关于</h3>
          <div style={styles.aboutCard}>
            <span style={{ fontSize: 40 }}>🐱</span>
            <span style={styles.appName}>星尘</span>
            <span style={styles.appVersion}>iOS v0.1.0</span>
            <span style={styles.appDesc}>
              一只异色瞳黑猫AI桌宠，傲娇毒舌但内心关心主人
            </span>
          </div>
        </div>

        <button style={styles.saveBtn} onClick={handleSave}>
          {saved ? "已保存" : "保存设置"}
        </button>
      </div>
    </div>
  );
}

function SwitchRow({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <div style={styles.switchRow}>
      <span style={styles.switchLabel}>{label}</span>
      <button
        style={{
          ...styles.switch,
          background: value ? "var(--accent-primary)" : "var(--bg-input)",
        }}
        onClick={() => onChange(!value)}
      >
        <div
          style={{
            ...styles.switchThumb,
            transform: value ? "translateX(20px)" : "translateX(0)",
          }}
        />
      </button>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: "flex",
    flexDirection: "column",
    height: "100%",
    background: "var(--bg-primary)",
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
  content: {
    flex: 1,
    overflow: "auto",
    padding: "var(--space-4)",
    paddingBottom: "calc(var(--space-4) + var(--safe-bottom))",
  },
  section: {
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
  field: {
    marginBottom: "var(--space-3)",
  },
  label: {
    display: "block",
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    marginBottom: "var(--space-1)",
  },
  input: {
    width: "100%",
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-sm)",
    color: "var(--text-primary)",
    padding: "10px var(--space-3)",
    fontSize: "15px",
    outline: "none",
  },
  select: {
    width: "100%",
    background: "var(--bg-input)",
    border: "1px solid var(--border-color)",
    borderRadius: "var(--radius-sm)",
    color: "var(--text-primary)",
    padding: "10px var(--space-3)",
    fontSize: "15px",
    outline: "none",
  },
  row: {
    display: "flex",
    gap: "var(--space-3)",
  },
  testRow: {
    display: "flex",
    alignItems: "center",
    gap: "var(--space-3)",
    marginTop: "var(--space-2)",
  },
  testBtn: {
    background: "var(--bg-card)",
    color: "var(--accent-primary)",
    padding: "var(--space-2) var(--space-4)",
    borderRadius: "var(--radius-sm)",
    fontSize: "var(--text-sm)",
    border: "1px solid var(--accent-primary)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    boxShadow: "0 0 8px rgba(139, 108, 255, 0.15)",
  },
  testResult: {
    fontSize: "var(--text-sm)",
    fontWeight: "var(--weight-medium)",
  },
  switchRow: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "var(--space-3) 0",
    borderBottom: "1px solid var(--border-color)",
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
  },
  switchThumb: {
    width: 20,
    height: 20,
    borderRadius: 10,
    background: "white",
    transition: "all var(--duration-normal) var(--ease-out-expo)",
  },
  aboutCard: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    gap: "var(--space-2)",
    padding: "var(--space-6)",
    background: "var(--bg-card)",
    borderRadius: "var(--radius-lg)",
    border: "1px solid var(--border-color)",
  },
  appName: {
    fontSize: "var(--text-lg)",
    fontWeight: "var(--weight-bold)",
    color: "var(--text-primary)",
  },
  appVersion: {
    fontSize: "var(--text-sm)",
    color: "var(--text-muted)",
  },
  appDesc: {
    fontSize: "var(--text-sm)",
    color: "var(--text-secondary)",
    textAlign: "center",
    lineHeight: 1.5,
  },
  saveBtn: {
    width: "100%",
    background: "linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))",
    color: "white",
    padding: "14px",
    borderRadius: "var(--radius-md)",
    fontSize: "var(--text-base)",
    fontWeight: "var(--weight-semibold)",
    transition: "all var(--duration-fast) var(--ease-out-quart)",
    boxShadow: "var(--shadow-md)",
  },
};
