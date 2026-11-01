import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { Plus, Trash2, Check, Eye, EyeOff, Sparkles, Zap, Globe, Server, Brain, Cpu, Cloud, Search, Play, Moon, Maximize2, Hexagon } from "lucide-react";
import { generateId } from "@/lib/utils";
import type { ProviderProfile, LlmProvider, ChatParamsConfig } from "@/types/settings";

/** 服务预设 id 附加在 provider.params 上（LlmParams 类型未含该键，统一经下列辅助函数读写） */
type ParamsWithPreset = ProviderProfile["params"] & { presetId?: string };
const getPresetId = (p: ProviderProfile): string => (p.params as ParamsWithPreset | undefined)?.presetId ?? "";
const withPresetId = (params: ProviderProfile["params"], presetId: string): ProviderProfile["params"] =>
  ({ ...params, presetId }) as ProviderProfile["params"];
import { useState, useCallback } from "react";
import { LLM_PRESETS, PROVIDER_PROFILES, getProfile, clampToProfile, findPreset } from "@/engine/presets";
import type { ApiParamProfile } from "@/engine/presets";
import { Select, SectionTitle, ParamSlider } from "./common";

/** 内置厂商模板 — 用户无需自己找 URL */
const BUILTIN_PROVIDERS: Array<{
  key: LlmProvider;
  name: string;
  description: string;
  icon: typeof Sparkles;
  baseUrl: string;
  baseUrlPlaceholder: string;
  defaultModels: string[];
  color: string;
  apiKeyHint: string;   // API Key 获取提示
  /** 对应的服务预设 id（用于参数档案映射，见 presetIdToProfile） */
  presetId?: string;
  free?: boolean;       // 是否免费
}> = [
  // ═══════════ 海外厂商 ═══════════
  {
    key: "openai",
    name: "OpenAI",
    description: "GPT-4o / GPT-4o-mini / o1 / o3",
    icon: Sparkles,
    baseUrl: "https://api.openai.com/v1",
    baseUrlPlaceholder: "https://api.openai.com/v1",
    defaultModels: ["gpt-4o", "gpt-4o-mini", "o1", "o3", "gpt-4-turbo"],
    color: "#10a37f",
    apiKeyHint: "在 platform.openai.com/api-keys 获取",
    presetId: "openai",
  },
  {
    key: "anthropic",
    name: "Anthropic Claude",
    description: "Claude 4 Opus / Sonnet / Haiku",
    icon: Zap,
    baseUrl: "https://api.anthropic.com",
    baseUrlPlaceholder: "https://api.anthropic.com",
    defaultModels: ["claude-opus-4-20250514", "claude-sonnet-4-20250514", "claude-haiku-4-20250514"],
    color: "#d97706",
    apiKeyHint: "在 console.anthropic.com/settings/keys 获取",
  },
  {
    key: "google",
    name: "Google Gemini",
    description: "Gemini 2.5 Pro / Flash / Flash-Lite",
    icon: Globe,
    baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai",
    baseUrlPlaceholder: "https://generativelanguage.googleapis.com/v1beta/openai",
    defaultModels: ["gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"],
    color: "#4285f4",
    apiKeyHint: "在 aistudio.google.com/apikey 获取",
  },

  // ═══════════ 国内厂商（按热度排序） ═══════════
  {
    key: "deepseek",
    name: "DeepSeek (深度求索)",
    description: "DeepSeek V3 / R1 推理模型 / Coder",
    icon: Brain,
    baseUrl: "https://api.deepseek.com/v1",
    baseUrlPlaceholder: "https://api.deepseek.com/v1",
    defaultModels: ["deepseek-chat", "deepseek-reasoner", "deepseek-coder"],
    color: "#4d6bfe",
    apiKeyHint: "在 platform.deepseek.com/api_keys 获取，注册即送免费额度",
    presetId: "deepseek",
  },
  {
    key: "moonshot",
    name: "Moonshot (Kimi 智能助手)",
    description: "Kimi K2 / Moonshot v1 系列",
    icon: Moon,
    baseUrl: "https://api.moonshot.cn/v1",
    baseUrlPlaceholder: "https://api.moonshot.cn/v1",
    defaultModels: ["kimi-latest", "moonshot-v1-128k", "moonshot-v1-32k", "moonshot-v1-8k"],
    color: "#7c3aed",
    apiKeyHint: "在 platform.moonshot.cn/console/api-keys 获取",
    presetId: "moonshot",
  },
  {
    key: "zhipu",
    name: "智谱 AI (GLM)",
    description: "GLM-4-Plus / Flash / Air / CodeGeeX",
    icon: Cpu,
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
    baseUrlPlaceholder: "https://open.bigmodel.cn/api/paas/v4",
    defaultModels: ["glm-4-plus", "glm-4-flash", "glm-4-air", "codegeex-4"],
    color: "#0ea5e9",
    apiKeyHint: "在 open.bigmodel.cn/usercenter/apikeys 获取",
    presetId: "zhipu",
  },
  {
    key: "tongyi",
    name: "阿里 通义千问 (Qwen)",
    description: "Qwen-Max / Plus / Turbo / VL 视觉",
    icon: Cloud,
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    baseUrlPlaceholder: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    defaultModels: ["qwen-max", "qwen-plus", "qwen-turbo", "qwen-vl-max", "qwen-long"],
    color: "#ff6a00",
    apiKeyHint: "在 dashscope.console.aliyun.com/apiKey 获取",
    presetId: "aliyun",
  },
  {
    key: "doubao",
    name: "字节 豆包 (Doubao)",
    description: "Doubao-Pro / Lite / 大模型",
    icon: Play,
    baseUrl: "https://ark.cn-beijing.volces.com/api/v3",
    baseUrlPlaceholder: "https://ark.cn-beijing.volces.com/api/v3",
    defaultModels: ["doubao-pro-32k", "doubao-lite-32k", "doubao-pro-128k", "doubao-1.5-pro-32k"],
    color: "#0066ff",
    apiKeyHint: "在 console.volcengine.com/ark/center 获取 API Key + Endpoint ID（填入 URL）",
  },
  {
    key: "baidu",
    name: "百度 文心一言 (ERNIE)",
    description: "ERNIE 4.0 / 3.5 / Speed / Lite",
    icon: Search,
    baseUrl: "https://qianfan.baidubce.com/v2",
    baseUrlPlaceholder: "https://qianfan.baidubce.com/v2",
    defaultModels: ["ernie-4.0-turbo-8k", "ernie-3.5-8k", "ernie-speed-8k", "ernie-lite-8k"],
    color: "#2932e1",
    apiKeyHint: "在 console.bce.baidu.com/qianfan/ais/console/applicationConsole/application 获取 API Key + Secret Key",
  },
  {
    key: "yiwanyiwu",
    name: "零一万物 (Yi)",
    description: "Yi-Large / Medium / Spark / Vision",
    icon: Hexagon,
    baseUrl: "https://api.lingyiwanwu.com/v1",
    baseUrlPlaceholder: "https://api.lingyiwanwu.com/v1",
    defaultModels: ["yi-large", "yi-medium", "yi-spark", "yi-vision"],
    color: "#e8523d",
    apiKeyHint: "在 platform.lingyiwanwu.com/apikeys 获取",
  },
  {
    key: "minimax",
    name: "MiniMax (海螺 AI)",
    description: "abab 6.5s / 6.5t / abab 6",
    icon: Maximize2,
    baseUrl: "https://api.minimax.chat/v1",
    baseUrlPlaceholder: "https://api.minimax.chat/v1",
    defaultModels: ["abab6.5s-chat", "abab6.5t-chat", "abab6-chat"],
    color: "#f97316",
    apiKeyHint: "在 platform.minimaxi.com/document/Account%20Guide 获取 API Key + Group ID",
    presetId: "minimax",
  },

  // ═══════════ 本地部署 ═══════════
  {
    key: "local",
    name: "Ollama (本地)",
    description: "本地运行开源模型，免费无限用",
    icon: Server,
    baseUrl: "http://localhost:11434/v1",
    baseUrlPlaceholder: "http://localhost:11434/v1",
    defaultModels: ["qwen3:32b", "deepseek-r1:14b", "llama3.3:70b", "phi4:14b", "gemma3:27b"],
    color: "#6366f1",
    apiKeyHint: "",
    free: true,
  },
];

/** 服务预设 id → 参数档案 id（PROVIDER_PROFILES 无 moonshot 之外的别名时兜底 custom） */
function profileIdFromPreset(presetId: string): string {
  return getProfile(presetId).id === presetId ? presetId : "custom";
}

/** provider 类型（LlmProvider）→ 参数档案 id */
function profileIdFromProviderType(provider: LlmProvider): string {
  const template = BUILTIN_PROVIDERS.find((t) => t.key === provider);
  if (template?.presetId) return profileIdFromPreset(template.presetId);
  const direct = PROVIDER_PROFILES.find((p) => p.id === provider);
  return direct?.id ?? "custom";
}

/** 解析某提供商当前应使用的参数档案 */
export function resolveProfile(provider: ProviderProfile): ApiParamProfile {
  const pid = getPresetId(provider) ? profileIdFromPreset(getPresetId(provider)) : profileIdFromProviderType(provider.provider);
  return getProfile(pid);
}

/** 全局聊天参数默认值（对齐引擎侧 chatParams 兜底；旧持久化 settings 可能缺该节） */
const CHAT_DEFAULTS: ChatParamsConfig = {
  temperature: 1.05,
  topP: 0.92,
  frequencyPenalty: 0.35,
  presencePenalty: 0.5,
  maxTokens: 500,
  contextTurns: 10,
  languageStyle: "normal",
};

const LANGUAGE_STYLE_OPTIONS = [
  { value: "normal", label: "自然（默认）" },
  { value: "tsundere", label: "傲娇" },
  { value: "cute", label: "可爱" },
];

/**
 * LLM 设置组件 — 内置厂商模板 + 服务预设 + 厂商参数档案约束 + 全局聊天参数
 */
export function LLMSettings() {
  const { settings, addProvider, deleteProvider, setActiveProvider, updateProvider, updateSettings } = useSettingsStore();
  const [showAddForm, setShowAddForm] = useState(false);
  const [showApiKey, setShowApiKey] = useState<string | null>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<LlmProvider>("openai");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [newProvider, setNewProvider] = useState({
    name: "",
    provider: "openai" as LlmProvider,
    apiKey: "",
    baseUrl: "",
    modelId: "",
    presetId: "custom",
  });

  // 测试状态
  const [testingProviderId, setTestingProviderId] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<Record<string, { ok: boolean; msg: string; latency?: number }>>({});

  /** 选择厂商模板时自动填充 */
  const handleSelectTemplate = useCallback((key: LlmProvider) => {
    const template = BUILTIN_PROVIDERS.find((t) => t.key === key);
    if (!template) return;
    setSelectedTemplate(key);
    setNewProvider((prev) => ({
      ...prev,
      provider: key,
      name: prev.name || template.name,
      baseUrl: template.baseUrl,
      modelId: prev.modelId || template.defaultModels[0],
      presetId: template.presetId ?? "custom",
    }));
  }, []);

  /** 表单里切换服务预设：自动填 baseUrl/modelId（custom 保持自由填写） */
  const handlePresetChange = (presetId: string) => {
    if (presetId === "custom") {
      setNewProvider((prev) => ({ ...prev, presetId }));
      return;
    }
    const preset = findPreset(LLM_PRESETS, presetId);
    if (!preset) return;
    setNewProvider((prev) => ({
      ...prev,
      presetId,
      // 除自定义外一律走 OpenAI 兼容协议
      provider: "openai",
      baseUrl: preset.url,
      modelId: preset.defaultModel,
      name: prev.name || preset.displayName,
    }));
  };

  const handleAdd = () => {
    if (!newProvider.name.trim()) return;
    // local 模式不需要 API Key
    if (newProvider.provider !== "local" && !newProvider.apiKey.trim()) return;

    const baseParams = {
      temperature: 0.7,
      topP: 0.9,
      maxTokens: 2048,
      presencePenalty: 0,
      frequencyPenalty: 0,
      stopSequences: [] as string[],
    };
    const clamped = clampToProfile(profileIdFromPreset(newProvider.presetId), baseParams);
    const provider: ProviderProfile = {
      id: generateId(),
      name: newProvider.name,
      provider: newProvider.provider,
      apiKey: newProvider.apiKey,
      baseUrl: newProvider.baseUrl,
      modelId: newProvider.modelId,
      params: { ...withPresetId({ ...clamped, presencePenalty: 0, frequencyPenalty: 0, stopSequences: [] }, newProvider.presetId) },
      isDefault: settings.providers.length === 0,
    };
    addProvider(provider);
    if (settings.providers.length === 0) {
      setActiveProvider(provider.id);
    }
    setShowAddForm(false);
    setEditingId(null);
    setNewProvider({ name: "", provider: "openai", apiKey: "", baseUrl: "", modelId: "", presetId: "custom" });
    setSelectedTemplate("openai");
  };

  /** 打开编辑表单（回填既有配置） */
  const startEdit = (provider: ProviderProfile) => {
    setEditingId(provider.id);
    setShowAddForm(true);
    setSelectedTemplate(provider.provider);
    setNewProvider({
      name: provider.name,
      provider: provider.provider,
      apiKey: provider.apiKey,
      baseUrl: provider.baseUrl,
      modelId: provider.modelId,
      presetId: getPresetId(provider) || "custom",
    });
  };

  const cancelForm = () => {
    setShowAddForm(false);
    setEditingId(null);
    setNewProvider({ name: "", provider: "openai", apiKey: "", baseUrl: "", modelId: "", presetId: "custom" });
    setSelectedTemplate("openai");
  };

  /** 测试 LLM 连接 */
  const testConnection = async (provider: ProviderProfile) => {
    setTestingProviderId(provider.id);
    setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: "测试中..." } }));

    const startTime = Date.now();

    try {
      if (provider.provider === "local") {
        // Ollama 本地模型检测 — 去除可能的 /v1 后缀
        let ollamaUrl = provider.baseUrl.replace(/\/+$/, "");
        if (ollamaUrl.endsWith("/v1")) ollamaUrl = ollamaUrl.slice(0, -3);
        const res = await fetch(`${ollamaUrl}/api/tags`, {
          method: "GET",
          signal: AbortSignal.timeout(8000),
        });
        const data = await res.json();
        const models = data?.models ?? [];
        const latency = Date.now() - startTime;
        if (res.ok && Array.isArray(models)) {
          setTestResult(prev => ({ ...prev, [provider.id]: {
            ok: true, msg: `连接成功！已安装 ${models.length} 个模型`, latency
          }}));
        } else {
          setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: "Ollama 未运行或地址错误" } }));
        }
      } else {
        // OpenAI 兼容 API 测试 — 发送一个最小请求
        const res = await fetch(`${provider.baseUrl}/chat/completions`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${provider.apiKey}`,
          },
          body: JSON.stringify({
            model: provider.modelId,
            messages: [{ role: "user", content: "hi" }],
            max_tokens: 5,
            stream: false,
          }),
          signal: AbortSignal.timeout(15000),
        });
        const latency = Date.now() - startTime;

        if (res.ok) {
          const data = await res.json();
          const modelInfo = data?.model || provider.modelId;
          setTestResult(prev => ({ ...prev, [provider.id]: {
            ok: true, msg: `连接成功！模型: ${modelInfo}`, latency
          }}));
        } else {
          let errMsg = `HTTP ${res.status}`;
          try {
            const errData = await res.json();
            errMsg = errData?.error?.message || errData?.error || errMsg;
          } catch {}
          setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: errMsg } }));
        }
      }
    } catch (err: any) {
      const errMsg = err.name === "TimeoutError" ? "连接超时" : (err.message || "网络错误");
      setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: errMsg } }));
    } finally {
      setTestingProviderId(null);
    }
  };

  /** 获取当前选中模板的推荐模型列表 */
  const currentTemplate = BUILTIN_PROVIDERS.find((t) => t.key === selectedTemplate);
  const formProfile = getProfile(profileIdFromPreset(newProvider.presetId));

  // ─── 全局聊天参数读写（旧 settings 可能缺 chatParams 节 → 全部 ?? 默认值） ───
  const cp = settings.chatParams;
  const patchChatParams = (partial: Partial<ChatParamsConfig>) => {
    updateSettings({ chatParams: { ...CHAT_DEFAULTS, ...(cp ?? {}), ...partial } });
  };
  const activeProvider = settings.providers.find((p) => p.id === settings.activeProviderId) ?? settings.providers[0];
  const activeProfile = activeProvider ? resolveProfile(activeProvider) : getProfile("custom");
  const supportsFreq = activeProfile.freqPenaltyMin !== null;
  const supportsPres = activeProfile.presPenaltyMin !== null;
  const globalMaxLimit = Math.max(16, activeProfile.maxTokensLimit);

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">AI 模型配置</h2>
          <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
            选择内置厂商或自定义 API 接口
          </p>
        </div>
        <Button onClick={() => { cancelForm(); setShowAddForm(true); }} size="sm" className="btn-primary">
          <Plus className="h-4 w-4 mr-1" />
          添加模型
        </Button>
      </div>

      {/* ====== 已配置的提供商列表 ====== */}
      {settings.providers.length > 0 && (
        <div className="space-y-3">
          {settings.providers.map((provider) => {
            const template = BUILTIN_PROVIDERS.find((t) => t.key === provider.provider);
            const profile = resolveProfile(provider);
            const freqSupported = profile.freqPenaltyMin !== null;
            const presSupported = profile.presPenaltyMin !== null;
            const isEditing = editingId === provider.id && showAddForm;
            const saveParams = (partial: Partial<ProviderProfile["params"]>) => {
              const merged = { ...provider.params, ...partial };
              const clamped = clampToProfile(profile.id, merged);
              updateProvider(provider.id, { params: { ...merged, ...clamped } });
            };
            return (
              <Card key={provider.id} className="surface-card">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                      {/* 厂商图标/色块 */}
                      <div
                        className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
                        style={{ background: `${template?.color ?? "#666"}15` }}
                      >
                        {template ? (
                          <template.icon className="h-5 w-5" style={{ color: template.color }} />
                        ) : (
                          <Server className="h-5 w-5 text-[var(--color-muted-foreground)]" />
                        )}
                      </div>

                      {/* 信息区 */}
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <h3 className="font-medium text-sm text-[var(--color-card-foreground)] truncate">
                            {provider.name}
                          </h3>
                          {settings.activeProviderId === provider.id && (
                            <span className="shrink-0 text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-[var(--color-primary)]/10 text-[var(--color-primary)] flex items-center gap-0.5">
                              <Check className="h-2.5 w-2.5" /> 当前使用
                            </span>
                          )}
                          <span className="shrink-0 text-[10px] px-1.5 py-0.5 rounded-full bg-[var(--color-muted)]/40 text-[var(--color-muted-foreground)]">
                            参数档案: {profile.displayName}
                          </span>
                        </div>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5 truncate">
                          模型: <span className="font-mono">{provider.modelId}</span>
                        </p>
                        {provider.baseUrl && (
                          <p className="text-[11px] text-[var(--color-muted-foreground)] mt-0.5 truncate opacity-60 font-mono">
                            {provider.baseUrl}
                          </p>
                        )}
                      </div>
                    </div>

                    {/* 操作按钮 */}
                    <div className="flex gap-1.5 shrink-0 ml-3">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => testConnection(provider)}
                        disabled={testingProviderId === provider.id}
                        className="text-xs"
                      >
                        {testingProviderId === provider.id ? (
                          <span className="flex items-center gap-1">
                            <span className="h-3 w-3 border border-current border-t-transparent rounded-full animate-spin" />
                            测试中
                          </span>
                        ) : (
                          <>
                            <Play className="h-3 w-3 mr-0.5" />
                            测试
                          </>
                        )}
                      </Button>
                      {settings.activeProviderId !== provider.id && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setActiveProvider(provider.id)}
                          className="text-xs"
                        >
                          使用
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-xs"
                        onClick={() => (isEditing ? cancelForm() : startEdit(provider))}
                      >
                        {isEditing ? "收起" : "编辑"}
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => deleteProvider(provider.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5 text-[var(--color-muted-foreground)]" />
                      </Button>
                    </div>
                  </div>
                  {/* 测试结果 */}
                  {testResult[provider.id] && (
                    <div className={`mt-2 text-xs px-2 py-1.5 rounded-md flex items-center gap-1.5 ${
                      testResult[provider.id].ok
                        ? "bg-green-500/10 text-green-400"
                        : "bg-red-500/10 text-red-400"
                    }`}>
                      {testResult[provider.id].ok ? (
                        <Check className="h-3 w-3 shrink-0" />
                      ) : (
                        <span className="h-3 w-3 shrink-0 rounded-full bg-red-400" />
                      )}
                      <span>{testResult[provider.id].msg}</span>
                      {testResult[provider.id].latency && (
                        <span className="ml-auto opacity-60">{testResult[provider.id].latency}ms</span>
                      )}
                    </div>
                  )}

                  {/* ====== 行内展开：完整参数编辑表单 ====== */}
                  {isEditing && (
                    <div className="mt-4 space-y-4 border-t border-[var(--color-border)] pt-4">
                      {/* 服务预设 */}
                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className="text-label block mb-1.5">显示名称</label>
                          <Input
                            value={newProvider.name}
                            onChange={(e) => setNewProvider({ ...newProvider, name: e.target.value })}
                            className="input-field"
                          />
                        </div>
                        <div>
                          <label className="text-label block mb-1.5">服务预设</label>
                          <Select
                            value={newProvider.presetId}
                            onChange={handlePresetChange}
                            options={LLM_PRESETS.map((p) => ({ value: p.id, label: p.displayName }))}
                          />
                          <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">
                            选择预设将自动填入 API 地址与模型，并按厂商能力限制参数范围
                          </p>
                        </div>
                      </div>
                      <div>
                        <label className="text-label block mb-1.5">API Key</label>
                        <div className="relative">
                          <Input
                            value={newProvider.apiKey}
                            onChange={(e) => setNewProvider({ ...newProvider, apiKey: e.target.value })}
                            type={showApiKey === provider.id ? "text" : "password"}
                            placeholder="输入 API Key"
                            className="input-field pr-10"
                          />
                          <button
                            type="button"
                            onClick={() => setShowApiKey(showApiKey === provider.id ? null : provider.id)}
                            className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-muted-foreground)] hover:text-[var(--color-card-foreground)]"
                          >
                            {showApiKey === provider.id ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                          </button>
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className="text-label block mb-1.5">API 地址</label>
                          <Input
                            value={newProvider.baseUrl}
                            onChange={(e) => setNewProvider({ ...newProvider, baseUrl: e.target.value })}
                            className="input-field font-mono text-xs"
                          />
                        </div>
                        <div>
                          <label className="text-label block mb-1.5">模型 ID</label>
                          <Input
                            value={newProvider.modelId}
                            onChange={(e) => setNewProvider({ ...newProvider, modelId: e.target.value })}
                            className="input-field font-mono text-xs"
                          />
                        </div>
                      </div>
                      <Button size="sm" variant="outline" onClick={handleAdd}>保存基本信息</Button>

                      <Separator />

                      {/* 采样参数（按所选 provider 的参数档案 clamp + paramHints） */}
                      <div className="space-y-3">
                        <p className="sidebar-section-label">采样参数（{profile.displayName} 档案）</p>
                        <ParamSlider
                          label="Temperature"
                          value={provider.params?.temperature ?? profile.defaultTemp}
                          min={profile.tempMin}
                          max={profile.tempMax}
                          step={0.05}
                          onChange={(v) => saveParams({ temperature: v })}
                          hint={profile.paramHints.temperature}
                        />
                        <ParamSlider
                          label="Top P"
                          value={provider.params?.topP ?? profile.defaultTopP}
                          min={profile.topPMin}
                          max={profile.topPMax}
                          step={0.01}
                          onChange={(v) => saveParams({ topP: v })}
                          hint={profile.paramHints.top_p}
                        />
                        <ParamSlider
                          label="最大 Tokens"
                          value={Math.min(provider.params?.maxTokens ?? profile.defaultMaxTokens, profile.maxTokensLimit)}
                          min={16}
                          max={profile.maxTokensLimit}
                          step={16}
                          onChange={(v) => saveParams({ maxTokens: v })}
                          hint={`上限 ${profile.maxTokensLimit}（${profile.displayName}）${profile.paramHints.max_tokens ? ` · ${profile.paramHints.max_tokens}` : ""}`}
                        />
                        <ParamSlider
                          label="Frequency Penalty"
                          value={freqSupported ? (provider.params?.frequencyPenalty ?? profile.defaultFreqPenalty) : 0}
                          min={profile.freqPenaltyMin ?? -2}
                          max={profile.freqPenaltyMax ?? 2}
                          step={0.05}
                          disabled={!freqSupported}
                          onChange={(v) => saveParams({ frequencyPenalty: v })}
                          hint={freqSupported ? profile.paramHints.freq_penalty : `${profile.displayName} 不支持 frequency_penalty`}
                        />
                        <ParamSlider
                          label="Presence Penalty"
                          value={presSupported ? (provider.params?.presencePenalty ?? profile.defaultPresPenalty) : 0}
                          min={profile.presPenaltyMin ?? -2}
                          max={profile.presPenaltyMax ?? 2}
                          step={0.05}
                          disabled={!presSupported}
                          onChange={(v) => saveParams({ presencePenalty: v })}
                          hint={presSupported ? profile.paramHints.pres_penalty : `${profile.displayName} 不支持 presence_penalty`}
                        />
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {settings.providers.length === 0 && (
        <div className="surface-card p-8 text-center">
          <Server className="h-10 w-10 mx-auto mb-3 text-[var(--color-muted-foreground)] opacity-40" />
          <p className="text-sm text-[var(--color-muted-foreground)]">还没有配置 AI 模型</p>
          <p className="text-xs text-[var(--color-muted-foreground)] mt-1 opacity-60">
            选择下方内置厂商快速添加，或点击「添加模型」自定义
          </p>
        </div>
      )}

      {/* ====== 快速添加：内置厂商选择 ====== */}
      {!showAddForm && settings.providers.length === 0 && (
        <div>
          <p className="sidebar-section-label mb-3">快速添加（内置厂商）</p>
          <div className="grid grid-cols-3 gap-2.5">
            {BUILTIN_PROVIDERS.map((template) => {
              const Icon = template.icon;
              return (
                <button
                  key={template.key}
                  onClick={() => {
                    handleSelectTemplate(template.key);
                    setNewProvider({
                      name: template.name,
                      provider: template.key,
                      apiKey: "",
                      baseUrl: template.baseUrl,
                      modelId: template.defaultModels[0],
                      presetId: template.presetId ?? "custom",
                    });
                    setShowAddForm(true);
                  }}
                  className="surface-card p-4 text-left group transition-all duration-200 hover:-translate-y-0.5"
                >
                  <div className="flex items-center gap-2.5 mb-2">
                    <div
                      className="w-8 h-8 rounded-lg flex items-center justify-center"
                      style={{ background: `${template.color}12` }}
                    >
                      <Icon className="h-4 w-4" style={{ color: template.color }} />
                    </div>
                    <span className="font-semibold text-sm text-[var(--color-card-foreground)]">
                      {template.name}
                    </span>
                  </div>
                  <p className="text-[11px] text-[var(--color-muted-foreground)] leading-relaxed">
                    {template.description}
                  </p>
                  {(template.key === "local" || template.key === "deepseek") && (
                    <span className="inline-block mt-1.5 text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-green-500/10 text-green-500">
                      {template.key === "local" ? "免费" : "送额度"}
                    </span>
                  )}
                </button>
              );
            })}
          </div>

          {/* 自定义入口 */}
          <button
            onClick={() => {
              setSelectedTemplate("openai");
              setNewProvider({ name: "", provider: "openai", apiKey: "", baseUrl: "", modelId: "", presetId: "custom" });
              setShowAddForm(true);
            }}
            className="w-full surface-card p-3 text-center text-sm text-[var(--color-muted-foreground)] hover:text-[var(--color-card-foreground)] transition-colors mt-3"
          >
            + 自定义 API 接口（OpenAI 兼容格式）
          </button>
        </div>
      )}

      {/* ====== 添加表单（新提供商） ====== */}
      {showAddForm && !editingId && (
        <Card className="surface-card">
          <CardContent className="p-5 space-y-4">
            <h3 className="font-medium text-sm text-[var(--color-card-foreground)]">
              {settings.providers.length === 0 ? "添加 AI 模型" : "添加新模型"}
            </h3>

            {/* 厂商选择 */}
            <div>
              <label className="text-label block mb-2">选择厂商</label>
              <div className="grid grid-cols-3 gap-2">
                {BUILTIN_PROVIDERS.map((template) => {
                  const Icon = template.icon;
                  const isActive = selectedTemplate === template.key;
                  return (
                    <button
                      key={template.key}
                      onClick={() => handleSelectTemplate(template.key)}
                      className={`relative flex items-center gap-2 px-3 py-2.5 rounded-lg border transition-all duration-200 ${
                        isActive
                          ? "border-[var(--color-primary)] bg-[var(--color-primary)]/8"
                          : "border-[var(--color-border)] hover:border-[var(--color-muted-foreground)]"
                      }`}
                    >
                      <Icon
                        className="h-4 w-4 shrink-0"
                        style={{ color: isActive ? "var(--color-primary)" : template.color }}
                      />
                      <span className={`text-xs font-medium ${isActive ? "text-[var(--color-primary)]" : ""}`}>
                        {template.name}
                      </span>
                      {isActive && (
                        <div
                          className="absolute bottom-0 left-1/2 -translate-x-1/2 w-6 h-0.5 rounded-full"
                          style={{ background: "var(--color-primary)" }}
                        />
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 服务预设（对齐 Android ServicePreset 下拉） */}
            <div>
              <label className="text-label block mb-1.5">服务预设</label>
              <Select
                value={newProvider.presetId}
                onChange={handlePresetChange}
                options={LLM_PRESETS.map((p) => ({ value: p.id, label: p.displayName }))}
              />
              <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">
                选择预设后自动填入 API 地址与默认模型（OpenAI 兼容协议）；选「自定义」可自由填写。当前参数档案：{formProfile.displayName}
              </p>
            </div>

            {/* 名称 */}
            <div>
              <label className="text-label block mb-1.5">显示名称</label>
              <Input
                value={newProvider.name}
                onChange={(e) => setNewProvider({ ...newProvider, name: e.target.value })}
                placeholder="例如：我的 GPT-4o"
                className="input-field"
              />
            </div>

            {/* API Key（local 模式不需要） */}
            {selectedTemplate !== "local" && (
              <div>
                <label className="text-label block mb-1.5">API Key</label>
                <div className="relative">
                  <Input
                    value={newProvider.apiKey}
                    onChange={(e) => setNewProvider({ ...newProvider, apiKey: e.target.value })}
                    placeholder={selectedTemplate === "openai" ? "sk-..." : selectedTemplate === "anthropic" ? "sk-ant-..." : "输入 API Key"}
                    type={showApiKey === "main" ? "text" : "password"}
                    className="input-field pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKey(showApiKey === "main" ? null : "main")}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-muted-foreground)] hover:text-[var(--color-card-foreground)]"
                  >
                    {showApiKey === "main" ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">
                  {currentTemplate?.apiKeyHint || "在对应厂商开放平台获取 API Key"}
                </p>
              </div>
            )}

            {/* Base URL */}
            <div>
              <label className="text-label block mb-1.5">API 地址</label>
              <Input
                value={newProvider.baseUrl}
                onChange={(e) => setNewProvider({ ...newProvider, baseUrl: e.target.value })}
                placeholder={currentTemplate?.baseUrlPlaceholder ?? "https://api.example.com/v1"}
                className="input-field font-mono text-xs"
              />
              {selectedTemplate === "local" && (
                <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">
                  确保已安装并启动 Ollama：ollama serve
                </p>
              )}
            </div>

            {/* 模型 ID */}
            <div>
              <label className="text-label block mb-1.5">模型</label>
              <Input
                value={newProvider.modelId}
                onChange={(e) => setNewProvider({ ...newProvider, modelId: e.target.value })}
                placeholder="输入模型 ID"
                className="input-field font-mono text-xs"
              />
              {/* 推荐模型快捷选择 */}
              {currentTemplate && (
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {currentTemplate.defaultModels.map((model) => (
                    <button
                      key={model}
                      onClick={() => setNewProvider({ ...newProvider, modelId: model })}
                      className={`chip text-[11px] ${newProvider.modelId === model ? "active" : ""}`}
                    >
                      {model}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* 按钮 */}
            <div className="flex gap-2 pt-1">
              <Button
                onClick={handleAdd}
                disabled={!newProvider.name.trim() || (selectedTemplate !== "local" && !newProvider.apiKey.trim())}
                className="btn-primary"
              >
                确认添加
              </Button>
              <Button variant="outline" onClick={cancelForm}>
                取消
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* ====== 全局聊天参数（写 settings.chatParams，引擎优先读取该节） ====== */}
      <SectionTitle
        title="全局聊天参数"
        desc={`作用于所有对话的温度/采样/上下文轮数等。范围按当前使用模型（${activeProfile.displayName}）自动限制。`}
      />
      <Card className="surface-card">
        <CardContent className="p-5 space-y-4">
          <ParamSlider
            label="Temperature"
            display={(cp?.temperature ?? CHAT_DEFAULTS.temperature).toFixed(2)}
            value={cp?.temperature ?? CHAT_DEFAULTS.temperature}
            min={activeProfile.tempMin}
            max={activeProfile.tempMax}
            step={0.05}
            onChange={(v) => patchChatParams({ temperature: v })}
            hint="越高越发散活泼，越低越严谨稳定"
          />
          <ParamSlider
            label="Top P"
            display={(cp?.topP ?? CHAT_DEFAULTS.topP).toFixed(2)}
            value={cp?.topP ?? CHAT_DEFAULTS.topP}
            min={activeProfile.topPMin}
            max={activeProfile.topPMax}
            step={0.01}
            onChange={(v) => patchChatParams({ topP: v })}
            hint="核采样概率阈值，通常与 Temperature 二选一调整"
          />
          <ParamSlider
            label="最大回复长度 (tokens)"
            value={Math.min(cp?.maxTokens ?? CHAT_DEFAULTS.maxTokens, globalMaxLimit)}
            min={64}
            max={globalMaxLimit}
            step={64}
            onChange={(v) => patchChatParams({ maxTokens: v })}
            hint={`上限 ${globalMaxLimit}（${activeProfile.displayName}）`}
          />
          <ParamSlider
            label="Frequency Penalty"
            display={(cp?.frequencyPenalty ?? CHAT_DEFAULTS.frequencyPenalty).toFixed(2)}
            value={supportsFreq ? (cp?.frequencyPenalty ?? CHAT_DEFAULTS.frequencyPenalty) : 0}
            min={activeProfile.freqPenaltyMin ?? -2}
            max={activeProfile.freqPenaltyMax ?? 2}
            step={0.05}
            disabled={!supportsFreq}
            onChange={(v) => patchChatParams({ frequencyPenalty: v })}
            hint={supportsFreq ? (activeProfile.paramHints.freq_penalty ?? "惩罚重复用词，降低复读感") : `${activeProfile.displayName} 不支持 frequency_penalty`}
          />
          <ParamSlider
            label="Presence Penalty"
            display={(cp?.presencePenalty ?? CHAT_DEFAULTS.presencePenalty).toFixed(2)}
            value={supportsPres ? (cp?.presencePenalty ?? CHAT_DEFAULTS.presencePenalty) : 0}
            min={activeProfile.presPenaltyMin ?? -2}
            max={activeProfile.presPenaltyMax ?? 2}
            step={0.05}
            disabled={!supportsPres}
            onChange={(v) => patchChatParams({ presencePenalty: v })}
            hint={supportsPres ? (activeProfile.paramHints.pres_penalty ?? "鼓励引入新话题，提高回复多样性") : `${activeProfile.displayName} 不支持 presence_penalty`}
          />
          <Separator />
          <ParamSlider
            label="上下文轮数"
            value={Math.min(50, Math.max(5, cp?.contextTurns ?? CHAT_DEFAULTS.contextTurns))}
            min={5}
            max={50}
            step={1}
            onChange={(v) => patchChatParams({ contextTurns: v })}
            hint="每次请求携带的历史对话轮数（5~50），越大越连贯、消耗也越大"
          />
          <div className="grid grid-cols-2 items-center gap-3">
            <label className="text-sm font-medium">语言风格</label>
            <Select
              value={cp?.languageStyle ?? CHAT_DEFAULTS.languageStyle}
              onChange={(v) => patchChatParams({ languageStyle: v as ChatParamsConfig["languageStyle"] })}
              options={LANGUAGE_STYLE_OPTIONS}
            />
          </div>
          <p className="text-[11px] text-[var(--color-muted-foreground)] opacity-70">
            语言风格会注入系统提示词，改变 AI 的说话口癖（自然 / 傲娇 / 可爱）。
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
